package com.haooz.chedule.data.platform

import com.haooz.chedule.data.storage.AppStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 隐私政策同意状态。用户明确同意前不得申请任何权限，也不得收集/上报任何信息，统计上报、定位申请、
 * 天气查询、公告/同步等行为都以此状态为前置条件。
 *
 * ⚠ 同意按「隐私政策版本号」记录：无记录读作 0 同样视为未同意；递增 [CURRENT_VERSION] 可让全部用户重新同意。
 */
object PrivacyConsent {

    private const val PREFS_NAME = "app_preferences"
    private const val KEY_CONSENTED_VERSION = "privacy_consent_version"

    /**
     * 当前隐私政策版本号。政策内容有实质变更时递增，用户会重新收到同意引导。
     *
     * 版本历史：
     * - 1：2026-10-02 初版
     * - 2：2026-11-15 补充天气服务商（小米天气 / 中国天气网 / Open-Meteo）、节假日数据接口，
     *   以及手环导出与日历同步涉及的蓝牙、日历权限
     * - 3：2026-11-16 设备标识改为平台稳定标识（Android ANDROID_ID 不可逆哈希 / iOS Keychain UUID），
     *   卸载重装后保持，并补充跨重装统计与 AI 额度连续性的说明
     */
    const val CURRENT_VERSION = 3

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
