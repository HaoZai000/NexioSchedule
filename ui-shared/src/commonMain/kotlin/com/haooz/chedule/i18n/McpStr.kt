package com.haooz.chedule.i18n

/** 「MCP 服务」页：本地服务开关、地址、可选 Token 与接入说明。 */
object McpStr {

    val entry = L("MCP 服务", "MCP Service")
    val entrySummary = L(
        "让 AI 助手直接查询或修改Nexio课程表",
        "Let AI assistants read or edit NexioSchedule",
    )
    val title = L("MCP 服务", "MCP Service")

    val enable = L("开启 MCP 服务", "Enable MCP service")
    val enableOn = L("运行中，可在 AI 助手中添加", "Running — ready to add in your AI assistant")
    val enableOff = L("关闭时不会启动服务，也没有常驻通知", "When off, no service runs and no notification is shown")
    val unsupported = L(
        "当前平台暂不支持本地 MCP 服务，仅 Android 可用",
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
        L("打开设置 → 小米澎湃 AI → 超级小爱 → MCP 服务 → 开启MCP 服务", "Open Settings → HyperOS AI → Xiao AI → MCP services → Open MCP services"),
        L("点右上角「+」，名称随意填写", "Tap + in the top-right corner and name it anything"),
        L("服务器地址填写上方地址；如果设置了 Token 就填同一个值", "Paste the URL above; enter the same token if you set one"),
        L("保存后对「专家」小爱提问需求即可", "After saving, simply ask Xiao Ai in Expert Mode for your request"),
    )

    val toolsSection = L("提供的能力", "Available tools")
    val toolsNote = L(
        "查询直接返回；新增、修改、删除、调课、停课、放假等写操作会先给出摘要，确认后才生效，且可撤销",
        "Reads return instantly. Writes (add, edit, delete, move, cancel, holidays) show a summary first and can be undone.",
    )
}
