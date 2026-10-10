package com.haooz.chedule.data

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 隐私同意状态的版本语义。
 *
 * 这是**合规要求**，不是普通功能：在用户明确同意前不得收集/上报任何信息。
 * 其中「老版本升级上来视为未同意、必须重新同意」这一条一旦失效，
 * 表现是静默的（老用户不会被要求同意，但代码会照常上报），所以用测试锁住。
 */
class PrivacyConsentTest {

    private lateinit var store: InMemoryKeyValueStore

    private fun freshStore(initial: Map<String, Any?> = emptyMap()) {
        store = InMemoryKeyValueStore(initial)
        AppStorage.init { store }
    }

    @BeforeTest
    fun setUp() {
        freshStore()
    }

    private val consentKey = "privacy_consent_version"

    @Test
    fun `无记录时视为未同意`() {
        freshStore()
        assertFalse(PrivacyConsent.hasConsented())
    }

    @Test
    fun `版本号为 0 时视为未同意`() {
        freshStore(mapOf(consentKey to 0))
        assertFalse(PrivacyConsent.hasConsented())
    }

    /** 核心合规断言：老版本记录（低于 CURRENT_VERSION）必须重新同意。 */
    @Test
    fun `低于当前版本时视为未同意`() {
        assertTrue(PrivacyConsent.CURRENT_VERSION > 0, "CURRENT_VERSION 必须为正，否则本用例无意义")
        freshStore(mapOf(consentKey to PrivacyConsent.CURRENT_VERSION - 1))
        assertFalse(PrivacyConsent.hasConsented(), "老版本升级上来必须重新同意隐私政策")
    }

    @Test
    fun `等于当前版本时视为已同意`() {
        freshStore(mapOf(consentKey to PrivacyConsent.CURRENT_VERSION))
        assertTrue(PrivacyConsent.hasConsented())
    }

    /** 高于当前版本（例如用户装过更新版又降级）也应视为已同意，避免反复弹窗。 */
    @Test
    fun `高于当前版本时视为已同意`() {
        freshStore(mapOf(consentKey to PrivacyConsent.CURRENT_VERSION + 1))
        assertTrue(PrivacyConsent.hasConsented())
    }

    @Test
    fun `setConsented 会落盘并更新可观察状态`() {
        freshStore()
        assertFalse(PrivacyConsent.consented.value)

        PrivacyConsent.setConsented()

        assertTrue(PrivacyConsent.hasConsented(), "同意状态必须写入存储，不能只改内存")
        assertEquals(
            PrivacyConsent.CURRENT_VERSION,
            store.getInt(consentKey, -1),
            "落盘的必须是当前版本号",
        )
        assertTrue(PrivacyConsent.consented.value, "可观察状态要同步更新，天气等副作用才会重跑")
    }

    @Test
    fun `revoke 会清空落盘状态并更新可观察状态`() {
        freshStore(mapOf(consentKey to PrivacyConsent.CURRENT_VERSION))
        PrivacyConsent.setConsented()
        assertTrue(PrivacyConsent.hasConsented())

        PrivacyConsent.revoke()

        assertFalse(PrivacyConsent.hasConsented(), "撤回后必须重新同意")
        assertEquals(0, store.getInt(consentKey, -1))
        assertFalse(PrivacyConsent.consented.value)
    }

    @Test
    fun `refresh 从存储同步到可观察状态`() {
        // 存储里已是已同意，但内存状态还没同步（模拟冷启动）
        freshStore(mapOf(consentKey to PrivacyConsent.CURRENT_VERSION))
        PrivacyConsent.revoke()  // 先置为 false
        assertFalse(PrivacyConsent.consented.value)

        store.edit { putInt(consentKey, PrivacyConsent.CURRENT_VERSION) }
        PrivacyConsent.refresh()

        assertTrue(PrivacyConsent.consented.value, "refresh 必须把存储状态读进内存")
    }

    /** 存储名必须是 app_preferences —— 改错等于老用户的同意状态全部丢失。 */
    @Test
    fun `使用 app_preferences 这个既有文件名`() {
        assertTrue(
            AppStorage.isReady,
            "AppStorage 需由测试初始化；生产环境由 NexioApplication.onCreate 初始化",
        )
        // 直接往 app_preferences 里写，验证 PrivacyConsent 读的是同一份
        AppStorage.store("app_preferences").edit {
            putInt(consentKey, PrivacyConsent.CURRENT_VERSION)
        }
        assertTrue(
            PrivacyConsent.hasConsented(),
            "PrivacyConsent 必须读 app_preferences，文件名改动会让存量同意状态失效",
        )
    }
}
