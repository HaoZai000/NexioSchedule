/** Shizuku 管理器 - 管理 Shizuku 服务的绑定和生命周期 */
package com.haooz.chedule.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import rikka.sui.Sui
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val FIREWALL_CHAIN_OEM_DENY = 9

    private var privilegedService: IPrivilegedService? = null
    private var serviceConnected = false
    private var shizukuAvailable = false
    private var bindLatch = CountDownLatch(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                privilegedService = IPrivilegedService.Stub.asInterface(binder)
                serviceConnected = true
                bindLatch.countDown()
                Log.d(TAG, "Privileged service connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            privilegedService = null
            serviceConnected = false
            bindLatch = CountDownLatch(1)
            Log.d(TAG, "Privileged service disconnected")
        }
    }

    fun init(context: Context) {
        try {
            Sui.init(context.packageName)
            shizukuAvailable = Shizuku.pingBinder()
            Log.d(TAG, "Shizuku initialized, available: $shizukuAvailable")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Shizuku", e)
            shizukuAvailable = false
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
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(callback: (Boolean) -> Unit) {
        if (!isShizukuRunning()) {
            callback(false)
            return
        }

        if (checkSelfPermission()) {
            callback(true)
            return
        }

        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                callback(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }

        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            Shizuku.removeRequestPermissionResultListener(listener)
            callback(false)
        }
    }

    fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        if (!isShizukuRunning() || !checkSelfPermission()) {
            Log.w(TAG, "Shizuku not available or no permission")
            return false
        }

        return try {
            val xmsfUid = context.packageManager.getPackageUid(XMSF_PACKAGE, 0)
            setPackageNetworkingEnabledViaService(xmsfUid, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set networking via service, trying fallback", e)
            try {
                val xmsfUid = context.packageManager.getPackageUid(XMSF_PACKAGE, 0)
                setPackageNetworkingEnabledViaBinder(xmsfUid, enabled)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to set networking via binder fallback", e2)
                false
            }
        }
    }

    private fun setPackageNetworkingEnabledViaService(uid: Int, enabled: Boolean): Boolean {
        val service = getPrivilegedService() ?: return false
        return service.setPackageNetworkingEnabled(uid, enabled)
    }

    private fun getPrivilegedService(): IPrivilegedService? {
        if (privilegedService != null && serviceConnected) {
            return privilegedService
        }

        return try {
            // Reset latch for fresh bind
            bindLatch = CountDownLatch(1)

            val args = Shizuku.UserServiceArgs(
                ComponentName("com.haooz.chedule", PrivilegedServiceImpl::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("privileged")
                .debuggable(false)
                .version(1)

            Shizuku.bindUserService(args, serviceConnection)

            // Wait for onServiceConnected callback, max 3 seconds
            val connected = bindLatch.await(3, TimeUnit.SECONDS)
            if (connected) {
                Log.d(TAG, "Privileged service bound successfully")
            } else {
                Log.w(TAG, "Privileged service bind timed out")
            }

            privilegedService
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind privileged service", e)
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

            // Enable OEM firewall chain
            val setFirewallChainEnabled = iConnectivityManager.javaClass.getMethod(
                "setFirewallChainEnabled",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            setFirewallChainEnabled.invoke(iConnectivityManager, FIREWALL_CHAIN_OEM_DENY, true)

            // Set UID rule
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
            Log.e(TAG, "Failed to set networking via binder", e)
            false
        }
    }

    /**
     * 通过 Shizuku 以 shell 身份执行命令行（如 pm install）。
     * @return 退出码与输出；退出码 -1 表示 Shizuku 不可用或执行异常。
     */
    fun execAsShell(arguments: Array<String>): Pair<Int, String> {
        if (!isShizukuRunning() || !checkSelfPermission()) {
            return Pair(-1, "Shizuku 不可用或未授权")
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
            Log.e(TAG, "execAsShell failed", e)
            Pair(-1, e.message ?: "执行异常")
        }
    }

    /**
     * 通过 Shizuku 静默安装 APK（等效 ADB 的 pm install）。
     * 采用 stdin 流式传入安装包，避免 shell 无法读取 App 私有目录的问题。
     */
    fun silentInstallApk(apkPath: String): Pair<Boolean, String> {
        if (!isShizukuRunning() || !checkSelfPermission()) {
            return Pair(false, "Shizuku 不可用或未授权")
        }
        return try {
            val file = File(apkPath)
            if (!file.exists()) return Pair(false, "APK 不存在: $apkPath")

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
                if (ok) "静默安装成功" else "exit=$exit, ${output.take(300)}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "silentInstallApk failed", e)
            Pair(false, e.message ?: "执行异常")
        }
    }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.addBinderDeadListener(listener)
    }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.removeBinderDeadListener(listener)
    }
}