package com.haooz.chedule.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 隐私政策同意状态。
 *
 * 合规要求：在用户明确同意隐私政策之前，不得申请任何权限，也不得收集/上报任何信息。
 * 因此统计上报、定位申请、天气查询、公告/同步等行为都必须以此状态为前置条件。
 *
 * 同意状态按「隐私政策版本号」记录：
 * - 老版本（无记录，读作 0）升级上来同样视为未同意，必须重新同意；
 * - 政策内容变更或需强制重新同意时，递增 [CURRENT_VERSION] 即可让全部用户重新同意。
 *
 * [consented] 是可观察状态：主界面在同意前已预加载，字段变化可让依赖同意的副作用
 * （如天气查询）在用户点「同意」后重新执行。
 *
 * ## 迁移说明
 *
 * 本对象原先每个方法都收 `Context`，且 `Context` **只用来 getSharedPreferences**。
 * 改用 [AppStorage] 后不再持有任何 Android 类型，因此**可直接下沉到 `:core`**
 * （依赖只有 `kotlinx.coroutines.flow`，是跨平台的）。
 * 这是存储层抽象后第一个真正具备跨平台资格的类。
 */
object PrivacyConsent {

    private const val PREFS_NAME = "app_preferences"
    private const val KEY_CONSENTED_VERSION = "privacy_consent_version"

    /** 当前隐私政策版本号。政策内容有实质变更时递增，用户会重新收到同意弹窗 */
    const val CURRENT_VERSION = 1

    /** 隐私政策在线地址（华为/小米等应用商店登记用同一网址） */
    const val POLICY_URL = "https://nexioschedule.icu/privacy.html"

    private val _consented = MutableStateFlow(false)

    /** 供 Compose 观察的同意状态；启动时由 [refresh] 从本地读取 */
    val consented: StateFlow<Boolean> = _consented.asStateFlow()

    /** 用户是否已同意「当前版本」的隐私政策；无记录或版本过低均为未同意 */
    fun hasConsented(): Boolean =
        AppStorage.store(PREFS_NAME).getInt(KEY_CONSENTED_VERSION, 0) >= CURRENT_VERSION

    /** 启动时同步内存状态，供界面观察 */
    fun refresh() {
        _consented.value = hasConsented()
    }

    /** 记录用户已同意当前版本的隐私政策 */
    fun setConsented() {
        AppStorage.store(PREFS_NAME).edit {
            putInt(KEY_CONSENTED_VERSION, CURRENT_VERSION)
        }
        _consented.value = true
    }

    /** 撤回同意：清空同意状态，下次启动需重新同意隐私政策 */
    fun revoke() {
        AppStorage.store(PREFS_NAME).edit {
            putInt(KEY_CONSENTED_VERSION, 0)
        }
        _consented.value = false
    }
}
