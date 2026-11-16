package com.haooz.chedule.i18n

/** 「AI 功能」页：免费额度、自带服务商设置与功能入口。 */
object AiStr {

    val freeQuota = L("免费额度", "Free Quota")
    val quotaShrink = L("通知精简", "Notification labels")
    val quotaAgent = L("AI 教务导入", "Academic import")
    val quotaUsed = L("今天已用 %d / %d 次", "%d of %d used today")
    val quotaRemaining = L("剩余 %d 次", "%d left")
    val quotaNote = L(
        "每天 0 点重置；使用其它服务商不用免费额度",
        "Resets at midnight. Using your own provider does not consume free quota.",
    )
    val quotaLoading = L("正在读取额度…", "Loading quota…")
    val quotaUnavailable = L("暂时拿不到额度信息", "Quota is unavailable right now")
    val retry = L("重试", "Retry")
    val serverDisabled = L("Nexio 免费 AI 暂未启用", "Nexio free AI is not enabled")

    val byokSection = L("模型服务商", "Model Provider")
    val providerLabel = L("服务商", "Provider")
    val providerFree = L("每日免费 AI", "Free daily AI")
    val providerCustom = L("自定义", "Custom")
    val providerFreeNote = L(
        "当前使用 Nexio 免费额度，无需填写 apikey",
        "Nexio's free quota is in use. No API key is required.",
    )
    val byokBaseUrl = L("接口地址", "Base URL")
    val byokBaseUrlHint = L("https://…/v1", "https://…/v1")
    val byokModel = L("模型名", "Model")
    val byokModelHint = L("模型 ID", "Model ID")
    val modelCustom = L("自定义模型", "Custom model")
    val byokApiKey = L("API key", "API key")
    val byokApiKeyHint = L("sk-...", "sk-...")
    val byokPrivacy = L(
        "API key 只保存在本机，不会上传到服务器",
        "Your API key stays on this device and is never uploaded.",
    )

    val features = L("功能入口", "Features")
    val entryShrink = L("通知精简缩略语", "Shorten notification labels")
    val entryShrinkSummary = L("AI 自动压缩课程名与上课地点", "Let AI shorten course names and locations")
    val entryEduImport = L("AI 教务导入", "AI academic import")
    val entryEduImportSummary = L(
        "自动读取教务系统并生成导入脚本",
        "Read the academic system and generate an import script",
    )
    val entryTextImport = L("AI 文本导入", "AI text import")
    val entryTextImportSummary = L(
        "用提示词把课表文本整理成导入格式",
        "Turn schedule text into the import format with a prompt",
    )
}
