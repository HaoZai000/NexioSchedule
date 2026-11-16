package com.haooz.chedule.data.ai

import com.haooz.chedule.data.storage.AppStorage

/**
 * 用户选择的模型服务商与本地配置。
 *
 * 只存在本机偏好里、只在客户端直连供应商时使用：**apikey 不上传服务器**，
 * 服务端配置里的 key 与这里互不影响。默认 [AiProviders.FREE]（Nexio 每日免费额度）。
 */
data class AiByokConfig(
    /** [AiProviders.presets] 里的 id；默认免费额度 */
    val provider: String = AiProviders.FREE,
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
) {
    /** 选了非免费服务商且三项都填了才算真正可用。 */
    val isUsable: Boolean
        get() = enabled
            && !AiProviders.isFree(provider)
            && baseUrl.isNotBlank()
            && model.isNotBlank()
            && apiKey.isNotBlank()
}

/** BYOK 配置的读写入口。偏好名改了就等同于清空用户已填的 key，不要动。 */
object AiByokStore {

    private const val PREFS = "ai_byok_prefs"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_API_KEY = "api_key"

    /** AppStorage 未初始化（预览 / 测试环境）时返回默认值，不抛异常。 */
    fun load(): AiByokConfig {
        if (!AppStorage.isReady) return AiByokConfig()
        val prefs = AppStorage.store(PREFS)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val savedProvider = prefs.getString(KEY_PROVIDER, "")
        // 旧版本没有 provider：开着的视为「自定义」，关着的回到免费额度
        val provider = when {
            savedProvider.isNotEmpty() && AiProviders.presets.any { it.id == savedProvider } -> savedProvider
            enabled -> AiProviders.CUSTOM
            else -> AiProviders.FREE
        }
        return AiByokConfig(
            provider = provider,
            enabled = enabled && provider != AiProviders.FREE,
            baseUrl = prefs.getString(KEY_BASE_URL, ""),
            model = prefs.getString(KEY_MODEL, ""),
            apiKey = prefs.getString(KEY_API_KEY, ""),
        )
    }

    fun save(config: AiByokConfig) {
        if (!AppStorage.isReady) return
        val provider = config.provider.ifBlank { AiProviders.FREE }
        val enabled = config.enabled && provider != AiProviders.FREE
        AppStorage.store(PREFS).edit {
            putString(KEY_PROVIDER, provider)
            putBoolean(KEY_ENABLED, enabled)
            putString(KEY_BASE_URL, config.baseUrl.trim())
            putString(KEY_MODEL, config.model.trim())
            putString(KEY_API_KEY, config.apiKey.trim())
        }
    }

    fun clear() = save(AiByokConfig())
}
