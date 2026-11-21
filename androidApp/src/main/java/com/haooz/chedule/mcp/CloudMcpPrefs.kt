package com.haooz.chedule.mcp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public data class CloudMcpConfig(
    val url: String,
    val token: String,
    val enabled: Boolean = false,
) {
    override fun toString(): String = "CloudMcpConfig(enabled=$enabled)"
}

public class CloudMcpPrefsException internal constructor(message: String) : Exception(message)

/** The config is only stored in the device's non-backed-up app directory. */
public class CloudMcpPrefs(context: Context) {
    private val appContext = context.applicationContext

    /** Returns null only when no config exists. An unreadable config must be entered again. */
    public suspend fun load(): CloudMcpConfig? = withContext(Dispatchers.IO) {
        fileLock.withLock {
            try {
                val file = configFile()
                // AtomicFile.openRead() can restore a legacy backup after an interrupted write.
                if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withLock null
                val encrypted = file.openRead().use { stream ->
                    val size = stream.channel.size()
                    check(size in (IV_BYTES + TAG_BYTES + 1).toLong()..MAX_FILE_BYTES.toLong())
                    stream.readBytes().also { check(it.size.toLong() == size) }
                }
                val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: error("Missing key")
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, encrypted, 0, IV_BYTES))
                val clear = cipher.doFinal(encrypted, IV_BYTES, encrypted.size - IV_BYTES)
                try {
                    val json = JsonParser.parseString(clear.toString(Charsets.UTF_8)).asJsonObject
                    check(json.getAsJsonPrimitive("version").let { it.isNumber && it.asString == VERSION.toString() })
                    CloudMcpConfig(
                        url = json.getAsJsonPrimitive("url").also { check(it.isString) }.asString,
                        token = json.getAsJsonPrimitive("token").also { check(it.isString) }.asString,
                        enabled = json.getAsJsonPrimitive("enabled").also { check(it.isBoolean) }.asBoolean,
                    )
                } finally {
                    clear.fill(0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw CloudMcpPrefsException("无法读取云端 MCP 配置，请解锁设备或重新填写配置。")
            }
        }
    }

    /** Atomically replaces the encrypted config. This does not start or contact a server. */
    public suspend fun store(config: CloudMcpConfig): Unit = withContext(Dispatchers.IO) {
        fileLock.withLock {
            try {
                val file = configFile()
                val clear = JsonObject().apply {
                    addProperty("version", VERSION)
                    addProperty("url", config.url)
                    addProperty("token", config.token)
                    addProperty("enabled", config.enabled)
                }.toString().toByteArray(Charsets.UTF_8)
                val encrypted = try {
                    check(clear.size <= MAX_FILE_BYTES - IV_BYTES - TAG_BYTES)
                    val cipher = encryptionCipher()
                    check(cipher.iv.size == IV_BYTES)
                    cipher.iv + cipher.doFinal(clear)
                } finally {
                    clear.fill(0)
                }
                val stream = file.startWrite()
                try {
                    stream.write(encrypted)
                    file.finishWrite(stream)
                } catch (e: Exception) {
                    file.failWrite(stream)
                    throw e
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw CloudMcpPrefsException("无法加密保存云端 MCP 配置，请解锁设备后重试。")
            }
        }
    }

    private fun configFile() = AtomicFile(File(appContext.noBackupFilesDir, "cloud_mcp.enc"))

    private fun encryptionCipher(): Cipher {
        val store = keyStore()
        val existingKey = store.getKey(KEY_ALIAS, null) as? SecretKey
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, existingKey ?: createKey()) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Only an explicit replacement may recreate a permanently invalidated key.
            store.deleteEntry(KEY_ALIAS)
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        }
    }

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        val fileLock = Mutex()
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nexio_cloud_mcp_config_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION = 1
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val MAX_FILE_BYTES = 16 * 1024
    }
}
