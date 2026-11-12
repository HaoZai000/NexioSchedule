package com.haooz.chedule.wearable

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.repo.CourseRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.UUID

/** 扫描到的手表设备 */
data class WatchDevice(val name: String, val address: String)

/**
 * 手机侧蓝牙发送端（BLE 中心设备）。
 *
 * 角色分工与手表端 [com.haooz.chedule.sync.BleScheduleServer] 对应：
 * **手表广播并提供 GATT 服务，手机扫描、连接、把整表分片写进去**。
 *
 * 之所以由手机发起：导出的入口在手机设置页，用户需要先看到「附近有哪些手表」再选一个。
 *
 * 数据格式：4 字节大端总长度 + 整表 JSON，按协商后的 MTU 分片写入同一特征。
 * 手表端累计到长度足够即解析落盘。
 *
 * 用 WRITE_TYPE_DEFAULT（带响应）而不是 NO_RESPONSE：整表 70 片左右，需要流控，
 * 否则手表的 GATT 缓冲会被瞬间打满而丢片。
 */
@SuppressLint("MissingPermission")
class BleScheduleSender(private val context: Context) {

    companion object {
        private const val TAG = "BleScheduleSender"

        /** 与手表端 BleScheduleServer 保持一致 */
        val SERVICE_UUID: UUID = UUID.fromString("0000ab10-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000ab11-0000-1000-8000-00805f9b34fb")

        private const val REQUEST_MTU = 512
        private const val MIN_MTU = 23
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val OP_TIMEOUT_MS = 8_000L

        /** 扫描需要的运行时权限：S 起是 BLUETOOTH_SCAN/CONNECT，之前靠定位权限才能扫 */
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun hasPermissions(context: Context): Boolean =
            requiredPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
    }

    private val appContext = context.applicationContext
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private fun adapter(): BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * 扫描广播了 Nexio 服务 UUID 的设备，[timeoutMs] 后返回结果。
     *
     * 不做「扫到就返回」：多块手表/同名设备时让用户自己选，比自动连第一个可靠。
     */
    suspend fun scan(timeoutMs: Long = 8_000L): List<WatchDevice> = withContext(Dispatchers.IO) {
        val bt = adapter() ?: throw IllegalStateException("此设备不支持蓝牙")
        if (!bt.isEnabled) throw IllegalStateException("请先打开手机蓝牙")
        val le = bt.bluetoothLeScanner ?: throw IllegalStateException("蓝牙扫描不可用")

        val found = LinkedHashMap<String, WatchDevice>()
        val finished = CompletableDeferred<Unit>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = runCatching { device.name }.getOrNull().orEmpty()
                found[device.address] = WatchDevice(
                    name = name.ifBlank { device.address },
                    address = device.address,
                )
            }

            override fun onScanFailed(errorCode: Int) {
                NexioLog.w(TAG, "scan failed: $errorCode")
                finished.complete(Unit)
            }
        }

        scanner = le
        scanCallback = callback
        try {
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            le.startScan(filters, settings, callback)
            withTimeoutOrNull(timeoutMs) { finished.await() }
        } finally {
            runCatching { le.stopScan(callback) }
            scanner = null
            scanCallback = null
        }
        found.values.sortedBy { it.name }
    }

    /** 取消进行中的扫描（用户关掉面板时调用） */
    fun stopScan() {
        val cb = scanCallback ?: return
        runCatching { scanner?.stopScan(cb) }
        scanCallback = null
        scanner = null
    }

    /**
     * 连上 [device] 并把整表推过去。
     *
     * 失败会带回可读原因：连不上（超时/被拒）、找不到特征、写入中断。
     */
    suspend fun send(device: WatchDevice, scheduleName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val repo = CourseRepository.getInstance()
                val json = WatchPayload.buildFullJson(repo, appContext, scheduleName)
                val bytes = json.toByteArray(Charsets.UTF_8)
                if (bytes.isEmpty() || json.length <= 2) {
                    throw IllegalStateException("课表为空，无法导出")
                }
                NexioLog.i(TAG, "payload ${bytes.size}B -> ${device.address}")
                writeFramed(device, bytes)
            }
        }

    /** 首片带 4 字节长度头，其余裸写；写完断开 */
    private suspend fun writeFramed(device: WatchDevice, bytes: ByteArray) {
        val bt = adapter() ?: throw IllegalStateException("此设备不支持蓝牙")
        val remote = runCatching { bt.getRemoteDevice(device.address) }.getOrNull()
            ?: throw IllegalStateException("设备地址无效")

        val connected = CompletableDeferred<Boolean>()
        val mtuReady = CompletableDeferred<Int>()
        val servicesReady = CompletableDeferred<Boolean>()
        var written = CompletableDeferred<Boolean>()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED &&
                    status == BluetoothGatt.GATT_SUCCESS
                ) {
                    connected.complete(true)
                    runCatching { gatt.requestMtu(REQUEST_MTU) }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connected.complete(false)
                    if (!mtuReady.isCompleted) mtuReady.complete(MIN_MTU)
                    if (!servicesReady.isCompleted) servicesReady.complete(false)
                    if (!written.isCompleted) written.complete(false)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                // 协商失败就退回默认 MTU，仍然可以按 20 字节分片慢慢写
                mtuReady.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else MIN_MTU)
                runCatching { gatt.discoverServices() }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                servicesReady.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                written.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            @Deprecated("API 33 起由带 value 的重载替代，仍需实现以兼容低版本")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) = Unit
        }

        val gatt = remote.connectGatt(appContext, false, callback, BluetoothProfile.GATT)
            ?: throw IllegalStateException("无法建立 GATT 连接")

        try {
            val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } ?: false
            if (!ok) throw IllegalStateException("连接手表超时，请确认手表端已打开 Nexio 并停在首页")

            val mtu = withTimeoutOrNull(OP_TIMEOUT_MS) { mtuReady.await() } ?: MIN_MTU
            val discovered = withTimeoutOrNull(OP_TIMEOUT_MS) { servicesReady.await() } ?: false
            if (!discovered) throw IllegalStateException("未发现手表的课表服务")

            val service = gatt.getService(SERVICE_UUID)
                ?: throw IllegalStateException("手表未提供课表服务，请确认手表端版本")
            val characteristic = service.getCharacteristic(CHAR_UUID)
                ?: throw IllegalStateException("手表未提供课表特征")

            val framed = ByteBuffer.allocate(bytes.size + 4)
                .putInt(bytes.size)
                .put(bytes)
                .array()

            val chunkSize = (mtu - 3).coerceAtLeast(20)
            var offset = 0
            var index = 0
            while (offset < framed.size) {
                val len = minOf(chunkSize, framed.size - offset)
                val chunk = framed.copyOfRange(offset, offset + len)

                written = CompletableDeferred()
                val queued = runCatching {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    characteristic.value = chunk
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }.getOrDefault(false)
                if (!queued) throw IllegalStateException("写入被系统拒绝（第 ${index + 1} 片）")

                val chunkOk = withTimeoutOrNull(OP_TIMEOUT_MS) { written.await() } ?: false
                if (!chunkOk) throw IllegalStateException("传输中断（第 ${index + 1} 片）")

                offset += len
                index++
            }
            NexioLog.i(TAG, "sent ${framed.size}B in $index chunks (mtu=$mtu)")
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }
}
