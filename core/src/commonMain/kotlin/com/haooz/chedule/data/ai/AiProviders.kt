package com.haooz.chedule.data.ai

/**
 * 预设模型服务商。
 *
 * 地址与模型按 2026-11 各家官方文档核对过；地址都填到 OpenAI 兼容层这一级，
 * 请求时由 [AiDirectClient] 统一拼 `/chat/completions`。
 *
 * 模型列表只保留当前主力与上一代，方便用户选；服务商随时会发新模型，
 * 填不对就选「自定义模型」自己写。
 */
data class AiProviderPreset(
    val id: String,
    /** 品牌名，中英文一致，不走 L10n */
    val label: String,
    val baseUrl: String,
    /** 第一个是默认模型；空列表表示只能自己填 */
    val models: List<String>,
) {
    val defaultModel: String get() = models.firstOrNull().orEmpty()
}

object AiProviders {

    /** 不填 key，走 Nexio 每日免费额度 */
    const val FREE = "free"

    /** 地址与模型都自己填 */
    const val CUSTOM = "custom"

    val presets: List<AiProviderPreset> = listOf(
        AiProviderPreset(FREE, "Nexio", "", emptyList()),

        // https://api-docs.deepseek.com — V4.1 Flash 为默认，V4 Pro 为高配
        AiProviderPreset(
            "deepseek", "DeepSeek", "https://api.deepseek.com",
            listOf("deepseek-flash", "deepseek-v4-pro"),
        ),

        // https://mimo.mi.com/docs — 1M 上下文，flash 快、pro 强
        AiProviderPreset(
            "mimo", "MiMo", "https://api.xiaomimimo.com/v1",
            listOf("mimo-v2.6-flash", "mimo-v2.6-pro"),
        ),

        // https://docs.bigmodel.cn — GLM-5.3 旗舰 / Flash 系列
        AiProviderPreset(
            "glm", "GLM", "https://open.bigmodel.cn/api/paas/v4",
            listOf("glm-5.3", "glm-5.3-flash", "glm-5.3-flashx", "glm-5.2", "glm-4.7"),
        ),

        // https://platform.kimi.com — K3.1 Pro 为当前旗舰
        AiProviderPreset(
            "kimi", "Kimi", "https://api.moonshot.cn/v1",
            listOf("kimi-k3.1-pro", "kimi-k3.1-pro-highspeed", "kimi-k3"),
        ),

        // https://platform.minimaxi.com — M3.1 Flash 最新，M3 为旗舰
        AiProviderPreset(
            "minimax", "MiniMax", "https://api.minimax.cn/v1",
            listOf("MiniMax-M3.1-Flash", "MiniMax-M3"),
        ),

        // https://help.aliyun.com/zh/model-studio — 百炼 OpenAI 兼容入口
        AiProviderPreset(
            "qwen", "Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            listOf("qwen4-max", "qwen3.8-max", "qwen3.8-flash"),
        ),

        // 腾讯 TokenHub（Hy4 官方 OpenAI 兼容入口，国内）
        AiProviderPreset(
            "hunyuan", "混元", "https://tokenhub.tencentmaas.com/v1",
            listOf("hy4", "hy4-flash", "hy4-preview"),
        ),

        // 火山方舟 — Seed 2.1 系列
        AiProviderPreset(
            "seed", "Seed", "https://ark.cn-beijing.volces.com/api/v3",
            listOf(
                "doubao-seed-2-1-pro-260915",
                "doubao-seed-evolving",
                "doubao-seed-2-1-turbo-260628",
                "doubao-seed-2-1-lite-260915",
            ),
        ),

        // https://platform.openai.com/docs/models — GPT-6.1 / GPT-6 系列
        AiProviderPreset(
            "gpt", "GPT", "https://api.openai.com/v1",
            listOf("gpt-6.1-sol", "gpt-6.1-terra", "gpt-6-astra", "gpt-6-luna"),
        ),

        // https://platform.claude.com — OpenAI SDK 兼容入口
        AiProviderPreset(
            "claude", "Claude", "https://api.anthropic.com/v1",
            listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-1", "claude-haiku-5-5"),
        ),

        // https://ai.google.dev/gemini-api/docs/openai
        AiProviderPreset(
            "gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
            listOf("gemini-3.8-flash", "gemini-4-argon"),
        ),

        // https://docs.x.ai — Grok 4.7 为当前旗舰
        AiProviderPreset(
            "grok", "Grok", "https://api.x.ai/v1",
            listOf("grok-4.7", "grok-4.6", "grok-4.5"),
        ),

        AiProviderPreset(CUSTOM, "自定义", "", emptyList()),
    )

    fun byId(id: String): AiProviderPreset =
        presets.firstOrNull { it.id == id } ?: presets.first()

    /** 是否是「自己填地址与模型」的自定义服务商。 */
    fun isCustom(id: String): Boolean = id == CUSTOM

    /** 是否是免费额度（不直连任何服务商）。 */
    fun isFree(id: String): Boolean = id == FREE
}
