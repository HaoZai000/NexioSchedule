/** Shizuku 特权服务实现 - 提供跨应用操作能力 */
package com.haooz.chedule.shizuku

import android.os.IBinder
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PrivilegedServiceImpl : IPrivilegedService.Stub() {
    companion object {
        private const val TAG = "PrivilegedServiceImpl"
        private const val FIREWALL_CHAIN_OEM_DENY = 9
        private const val TIMEOUT_SECONDS = 3L
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        return try {
            // Use reflection to call ServiceManager.getService
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val connectivityBinder = getServiceMethod.invoke(null, "connectivity") as? IBinder
                ?: throw IllegalStateException("Connectivity service not found")

            val iConnectivityManager = Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, connectivityBinder)

            val latch = CountDownLatch(1)
            var result = false

            val thread = Thread {
                try {
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

                    result = true
                    Log.d(TAG, "Successfully set networking for uid $uid, enabled: $enabled")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed in worker thread", e)
                    result = false
                } finally {
                    latch.countDown()
                }
            }

            thread.start()
            val completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!completed) {
                thread.interrupt()
                Log.w(TAG, "Operation timed out")
                false
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set package networking", e)
            false
        }
    }
}