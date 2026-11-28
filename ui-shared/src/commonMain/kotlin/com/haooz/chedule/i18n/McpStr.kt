package com.haooz.chedule.i18n

/** 「MCP 服务」页：本地服务开关、地址、可选 Token 与接入说明。 */
object McpStr {

    val entry = L("MCP 服务", "MCP Service")
    val entrySummary = L(
        "让 AI 助手查询或修改 Nexio 课程表",
        "Let AI assistants view or edit Nexio Schedule",
    )
    val title = L("MCP 服务", "MCP Service")

    val enable = L("开启 MCP 服务", "Enable MCP service")
    val enableOn = L("运行中，可在 AI 助手中添加", "Running — ready to add in your AI assistant")
    val enableOff = L("已关闭", "Off")
    val unsupported = L(
        "本地 MCP 服务目前仅支持 Android",
        "Local MCP service is currently Android-only",
    )

    val serverAddress = L("服务器地址", "Server URL")
    val copy = L("复制", "Copy")
    val copied = L("已复制", "Copied")

    val authSection = L("鉴权（可选）", "Authentication (optional)")
    val tokenLabel = L("Token", "Token")
    val tokenHint = L("留空则不校验", "Leave empty to skip")

    val howToSection = L("在超级小爱中接入", "Add to Xiao AI")
    val steps = listOf(
        L("打开设置 → 小米澎湃 AI → 超级小爱 → MCP 服务 → 开启 MCP 服务", "Open Settings → HyperOS AI → Xiao AI → MCP services → Enable MCP services"),
        L("点右上角「+」，名称随意填写", "Tap + in the top-right corner and name it anything"),
        L("服务器地址填写上方地址；如果设置了 Token 就填同一个值", "Paste the URL above; enter the same token if you set one"),
        L("保存后在超级小爱「专家」模式中直接提问即可", "After saving, ask Xiao AI in Expert Mode directly"),
    )

    val toolsSection = L("提供的能力", "Available tools")
    val toolsNote = L(
        "查询即时返回；新增、修改、删除、调课、停课与放假等修改会先显示摘要，经您确认后生效，且支持撤销。",
        "Queries return instantly. Changes (add, edit, delete, move, cancel, holidays) show a summary for your confirmation first and can be undone.",
    )
}
