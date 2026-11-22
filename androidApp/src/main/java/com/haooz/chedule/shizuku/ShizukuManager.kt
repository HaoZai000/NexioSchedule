/** Shizuku 管理器 - 管理 Shizuku 服务的绑定和生命周期 */
package com.haooz.chedule.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.platform.PermissionRequestSession
import com.haooz.chedule.i18n.AndroidMiscStr
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import rikka.sui.Sui

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val FIREWALL_CHAIN_OEM_DENY = 9

    @Volatile private var privilegedService: IPrivilegedService? = null
    @Volatile private var serviceConnected = false
    @Volatile private var bindLatch = CountDownLatch(1)
    private val bindLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false
    private var applicationPackage = ""
    private const val PERMISSION_TIMEOUT_MS = 30_000L

    private fun clearPrivilegedService() {
        privilegedService = null
        serviceConnected = false
        bindLatch.countDown()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder() && checkSelfPermission()) {
                privilegedService = IPrivilegedService.Stub.asInterface(binder)
                serviceConnected = true
                bindLatch.countDown()
                NexioLog.d(TAG, "Privileged service connected")
            } else {
                clearPrivilegedService()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clearPrivilegedService()
            NexioLog.d(TAG, "Privileged service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
    }

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        try {
            applicationPackage = context.applicationContext.packageName
            Sui.init(applicationPackage)
            // 进程级监听不持有 Activity；页面监听另行注册/注销。
            Shizuku.addBinderDeadListener { clearPrivilegedService() }
            Shizuku.addBinderReceivedListener { clearPrivilegedService() }
            initialized = true
            NexioLog.d(TAG, "Shizuku initialized, available: ${isShizukuRunning()}")
        } catch (e: Exception) {
            NexioLog.e(TAG, "Failed to initialize Shizuku", e)
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun checkSelfPermission(): Boolean {
        return try {
            isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /** 返回取消函数；所有会话事件在主线程串行，清理先于回调。 */
    fun requestPermission(callback: (Boolean) -> Unit): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!isShizukuRunning() || checkSelfPermission()) {
            callback(checkSelfPermission())
            return {}
        }
        val id = permissionSession.begin(callback)
        if (id == null) {
            callback(false)
            return {}
        }
        // 每轮独立 requestCode，忽略其它请求以及超时后的迟到结果。
        val requestCode = 1000 + id
        permissionListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
            if (code == requestCode) mainHandler.post {
                permissionSession.complete(id, grant == PackageManager.PERMISSION_GRANTED && checkSelfPermission())
            }
        }
        permissionDeadListener = Shizuku.OnBinderDeadListener {
            mainHandler.post { permissionSession.complete(id, false) }
        }
        permissionTimeout = Runnable { permissionSession.complete(id, false) }
        pendingCancel = { permissionSession.cancel(id) }
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener!!)
            Shizuku.addBinderDeadListener(permissionDeadListener!!)
            mainHandler.postDelayed(permissionTimeout!!, PERMISSION_TIMEOUT_MS)
            if (!isShizukuRunning()) permissionSession.complete(id, false)
            else Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            NexioLog.w(TAG, "Shizuku permission request failed: ${e.message}")
            permissionSession.complete(id, false)
        }
        return { permissionSession.cancel(id) }
    }

    /** 取消挂起的授权请求（页面离开时调用）；无挂起会话时静默。 */
    fun cancelPermissionRequest() {
        val cancel = pendingCancel ?: return
        cancel()
    }

    private var pendingCancel: (() -> Unit)? = null

    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null
    private var permissionDeadListener: Shizuku.OnBinderDeadListener? = null
    private var permissionTimeout: Runnable? = null
    private val permissionSession = PermissionRequestSession {
        pendingCancel = null
        permissionListener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
        permissionDeadListener?.let { runCatching { Shizuku.removeBinderDeadListener(it) } }
        permissionTimeout?.let { mainHandler.removeCallbacks(it) }
        permissionListener = null
        permissionDeadListener = null
        permissionTimeout = null
    }

    /** 只观察连接变化，不绑定特权服务，也不触发任何网络操作。 */    fun observeBinderChanges(onChanged: () -> Unit): () -> Unit {
        val received = Shizuku.OnBinderReceivedListener { onChanged() }
        val dead = Shizuku.OnBinderDeadListener {
            clearPrivilegedService()
            onChanged()
        }
        Shizuku.addBinderReceivedListener(received)
        Shizuku.addBinderDeadListener(dead)
        return {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }

    fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        if (!isShizukuRunning() || !checkSelfPermission()) {
            NexioLog.w(TAG, "Shizuku not available or no permission")
            return false
        }

        val xmsfUid = try {
            context.packageManager.getPackageUid(XMSF_PACKAGE, 0)
        } catch (e: Exception) {
            NexioLog.e(TAG, "Failed to resolve XMSF uid", e)
            return false
        }
        val serviceSucceeded = try {
            setPackageNetworkingEnabledViaService(xmsfUid, enabled)
        } catch (e: Exception) {
            clearPrivilegedService()
            NexioLog.e(TAG, "Failed to set networking via service, trying fallback", e)
            false
        }
        // 未连上、服务返回 false 与抛异常都应尝试 Binder 通道。
        return serviceSucceeded || setPackageNetworkingEnabledViaBinder(xmsfUid, enabled)
    }

    private fun setPackageNetworkingEnabledViaService(uid: Int, enabled: Boolean): Boolean {
        val service = getPrivilegedService() ?: return false
        return service.setPackageNetworkingEnabled(uid, enabled)
    }

    private fun getPrivilegedService(): IPrivilegedService? = synchronized(bindLock) {
        if (!checkSelfPermission() || applicationPackage.isBlank()) return@synchronized null
        privilegedService?.takeIf { serviceConnected && it.asBinder().pingBinder() }
            ?.let { return@synchronized it }
        clearPrivilegedService()
        // 主线程等待会阻塞 ServiceConnection；直接交给 Binder fallback。
        if (Looper.myLooper() == Looper.getMainLooper()) return@synchronized null

        try {
            val latch = CountDownLatch(1)
            bindLatch = latch
            val args = Shizuku.UserServiceArgs(
                ComponentName(applicationPackage, PrivilegedServiceImpl::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("privileged")
                .debuggable(false)
                .version(1)
            Shizuku.bindUserService(args, serviceConnection)
            latch.await(3, TimeUnit.SECONDS)
            privilegedService?.takeIf { serviceConnected && it.asBinder().pingBinder() && checkSelfPermission() }
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            NexioLog.e(TAG, "Failed to bind privileged service", e)
            clearPrivilegedService()
            null
        }
    }

    private fun setPackageNetworkingEnabledViaBinder(uid: Int, enabled: Boolean): Boolean {
        return try {
            val connectivityManager = SystemServiceHelper.getSystemService("connectivity")
                ?: return false

            val wrappedBinder = ShizukuBinderWrapper(connectivityManager)
            val iConnectivityManager = Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrappedBinder)

            val setFirewallChainEnabled = iConnectivityManager.javaClass.getMethod(
                "setFirewallChainEnabled",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            setFirewallChainEnabled.invoke(iConnectivityManager, FIREWALL_CHAIN_OEM_DENY, true)

            val rule = if (enabled) 0 else 2 // 0 = ALLOW, 2 = DENY
            val setUidFirewallRule = iConnectivityManager.javaClass.getMethod(
                "setUidFirewallRule",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            setUidFirewallRule.invoke(iConnectivityManager, FIREWALL_CHAIN_OEM_DENY, uid, rule)

            true
        } catch (e: Exception) {
            NexioLog.e(TAG, "Failed to set networking via binder", e)
            false
        }
    }

    /**
     * 通过 Shizuku 以 shell 身份执行命令行（如 pm install）。
     * @return 退出码与输出；退出码 -1 表示 Shizuku 不可用或执行异常。
     */
    fun execAsShell(arguments: Array<String>): Pair<Int, String> {
        if (!isShizukuRunning() || !checkSelfPermission()) {
            return Pair(-1, AndroidMiscStr.shizukuUnavailable())
        }
        return try {
            // 13.x API 中 Shizuku.newProcess 为 private static，通过反射调用以 shell 身份启动子进程
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val newProcess = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcess.isAccessible = true
            val process = newProcess.invoke(null, arguments, null, null) as java.lang.Process
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { output.append(it.readText()) }
            process.errorStream.bufferedReader().use { output.append(it.readText()) }
            val exit = process.waitFor()
            Pair(exit, output.toString().trim())
        } catch (e: Exception) {
            NexioLog.e(TAG, "execAsShell failed", e)
            Pair(-1, e.message ?: AndroidMiscStr.execError())
        }
    }

    /**
     * 通过 Shizuku 静默安装 APK（等效 ADB 的 pm install）。
     * 采用 stdin 流式传入安装包，避免 shell 无法读取 App 私有目录的问题。
     */
    fun silentInstallApk(apkPath: String): Pair<Boolean, String> {
        if (!isShizukuRunning() || !checkSelfPermission()) {
            return Pair(false, AndroidMiscStr.shizukuUnavailable())
        }
        return try {
            val file = File(apkPath)
            if (!file.exists()) return Pair(false, AndroidMiscStr.apkNotFoundN(apkPath))

            // 13.x API 中 Shizuku.newProcess 为 private static，通过反射以 shell 身份启动 pm install
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val newProcess = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcess.isAccessible = true
            val process = newProcess.invoke(
                null,
                arrayOf("pm", "install", "-r", "-S", file.length().toString()),
                null,
                null
            ) as java.lang.Process

            // 把 APK 字节流写到 pm 的 stdin
            process.outputStream.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { output.append(it.readText()) }
            process.errorStream.bufferedReader().use { output.append(it.readText()) }
            val exit = process.waitFor()
            val ok = exit == 0 && output.contains("Success", ignoreCase = true)
            return Pair(
                ok,
                if (ok) AndroidMiscStr.silentInstallSuccess() else "exit=$exit, ${output.take(300)}"
            )
        } catch (e: Exception) {
            NexioLog.e(TAG, "silentInstallApk failed", e)
            Pair(false, e.message ?: AndroidMiscStr.execError())
        }
    }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.addBinderDeadListener(listener)
    }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.removeBinderDeadListener(listener)
    }
}