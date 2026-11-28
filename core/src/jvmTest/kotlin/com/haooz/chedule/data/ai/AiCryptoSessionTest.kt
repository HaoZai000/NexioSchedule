package com.haooz.chedule.data.ai

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 信封与**服务端**的兼容性锁定。
 *
 * 这里的「服务端」是用私钥手工模拟的：按 `server/index.js` 里 `openEnvelope` /
 * `aesGcmDecrypt` 的同一套参数解一遍。参数只要有一处不同（OAEP 摘要、MGF1 摘要、
 * IV 长度、tag 位置），这个测试立刻挂 —— 而真机上只会表现为「AI 永远不可用」，
 * 极难定位，所以必须在单元测试里钉住。
 */
class AiCryptoSessionTest {

    private fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun publicPem(pair: KeyPair): String {
        val body = Base64.getEncoder().encodeToString(pair.public.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n"
    }

    /** 模拟服务端：私钥解出会话密钥。 */
    private fun serverOpenSessionKey(pair: KeyPair, ekB64: String): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                pair.private,
                OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT,
                ),
            )
        }
        return cipher.doFinal(Base64.getDecoder().decode(ekB64))
    }

    /** 模拟服务端：用会话密钥解请求正文。 */
    private fun serverDecrypt(sessionKey: ByteArray, ivB64: String, ctB64: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(sessionKey, "AES"),
                GCMParameterSpec(128, Base64.getDecoder().decode(ivB64)),
            )
        }
        return String(cipher.doFinal(Base64.getDecoder().decode(ctB64)), Charsets.UTF_8)
    }

    /** 模拟服务端：用会话密钥加密响应。 */
    private fun serverEncrypt(sessionKey: ByteArray, plain: String): Pair<String, String> {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(128, iv))
        }
        return Base64.getEncoder().encodeToString(iv) to
            Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray()))
    }

    @Test
    fun `服务端能解开 App 发出的信封`() {
        val pair = newKeyPair()
        val session = AiCryptoSession(publicPem(pair))
        val body = """{"task":"shrink_classrooms","args":{"classrooms":["第五教学楼C509"]}}"""

        val envelope = session.encrypt(body)

        val sessionKey = serverOpenSessionKey(pair, envelope.ek)
        assertEquals(32, sessionKey.size, "会话密钥必须是 32 字节（AES-256）")
        assertEquals(12, Base64.getDecoder().decode(envelope.iv).size, "IV 必须 12 字节")
        assertEquals(body, serverDecrypt(sessionKey, envelope.iv, envelope.ct))
    }

    @Test
    fun `App 能解开服务端回的响应`() {
        val pair = newKeyPair()
        val session = AiCryptoSession(publicPem(pair))
        val sessionKey = serverOpenSessionKey(pair, session.encrypt("{}").ek)

        val (iv, ct) = serverEncrypt(sessionKey, """{"ok":true,"result":{"lines":["第五教学楼=五教"]}}""")

        assertEquals(
            """{"ok":true,"result":{"lines":["第五教学楼=五教"]}}""",
            session.decrypt(iv, ct),
        )
    }

    @Test
    fun `每条会话的密钥都不同`() {
        val pair = newKeyPair()
        val pem = publicPem(pair)
        val a = AiCryptoSession(pem)
        val b = AiCryptoSession(pem)
        // 会话密钥一次性：同一个公钥、同一份明文，两次封出来的东西必须不一样
        assertNotEquals(a.wrappedKey, b.wrappedKey)
        assertNotEquals(a.encrypt("{}").ct, b.encrypt("{}").ct)
    }

    @Test
    fun `密文被改过就解不开`() {
        val pair = newKeyPair()
        val session = AiCryptoSession(publicPem(pair))
        val sessionKey = serverOpenSessionKey(pair, session.encrypt("{}").ek)
        val (iv, ct) = serverEncrypt(sessionKey, "hello")

        // 翻转密文里一个字节：GCM 的 tag 校验必须拦下
        val raw = Base64.getDecoder().decode(ct)
        raw[0] = (raw[0].toInt() xor 0xFF).toByte()
        assertFailsWith<Exception> {
            session.decrypt(iv, Base64.getEncoder().encodeToString(raw))
        }
    }

    @Test
    fun `公钥格式不对时直接报错而不是静默失败`() {
        assertFailsWith<Exception> { AiCryptoSession("not a pem") }
    }

    @Test
    fun `内置的线上公钥能直接用`() {
        // 这条防的是「常量写错」：PEM 是 raw string + trimIndent，缩进、换行或漏行都会
        // 让 base64 解不开，而线上只表现为「AI 永远不可用」。这里拿真公钥建一次会话，
        // 建得出来就说明格式没问题。
        assertTrue(AiServerKey.isConfigured(), "线上公钥没填进 AiServerKey")
        val session = AiCryptoSession(AiServerKey.PUBLIC_KEY_PEM)
        assertTrue(session.wrappedKey.isNotEmpty())
        assertTrue(session.encrypt("{}").iv.isNotEmpty())
    }
}
