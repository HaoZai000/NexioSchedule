package com.haooz.chedule.i18n

/** 「MCP 服务」页：本地服务开关、地址、可选 Token 与接入说明。 */
object McpStr {

    val entry = L("MCP 服务", "MCP Service")
    val localSection = L("手机本地 MCP", "On-device MCP")
    val entrySummary = L(
        "让 AI 助手直接查询或修改 Nexio 课程表，支持可选的云端同步",
        "Let AI assistants read or edit the Nexio schedule, with optional cloud sync",
    )
    val title = entry

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
    val show = L("显示", "Show")
    val hide = L("隐藏", "Hide")
    val clipboardWarning = L("已复制敏感内容，请勿分享到聊天或截图中", "Sensitive value copied. Keep it out of chats and screenshots")
    val noTokenWarning = L("未设置 Token：本机其它应用也可能调用此服务", "No token: other apps on this device may call this service")
    val cloudSection = L("云端 MCP（可选扩展）", "Cloud MCP (optional)")
    val cloudEnable = L("启用云端连接", "Enable cloud connection")
    val cloudEnableHint = L("仅允许本机访问已配置的服务器，不会启停远端进程", "Allows this device to contact your server; does not start or stop it")
    val cloudAddress = L("云端地址", "Cloud URL")
    val cloudToken = L("云端 Token", "Cloud token")
    val cloudPrivacy = L("地址和 Token 在本机加密保存，不进入课表导出；测试只发给你填写的服务器", "URL and token are encrypted on this device and excluded from schedule exports; tests contact only your configured server")
    val save = L("保存配置", "Save configuration")
    val saved = L("配置已加密保存", "Configuration saved encrypted")
    val test = L("测试连接", "Test connection")
    val working = L("处理中…", "Working…")
    val cloudIdle = L("尚未连接，不会自动上传课表", "Not connected. No automatic schedule upload")
    val cloudReady = L("连接正常 · 云端版本 %d · %s", "Connected · cloud revision %d · %s")
    val hasSchedule = L("已有课表", "Schedule available")
    val emptySchedule = L("云端为空", "Cloud is empty")
    val upload = L("上传当前课表", "Upload current schedule")
    val uploadAction = L("上传", "Upload")
    val uploadHint = L("点击会把当前课表发送到所填服务器生成预览，确认后才替换云端文件；不会自动回写手机", "Sends the current schedule to your server for a preview. Confirmation replaces the cloud file; no automatic update to this device")
    val uploadPreview = L("将上传 %d 条排课记录%s，基于云端版本 %d。确认后才会保存。", "Upload %d course records%s based on cloud revision %d. Nothing is saved until confirmed.")
    val replaces = L("并替换当前云端课表", " and replace the current cloud schedule")
    val confirmUpload = L("确认上传", "Confirm upload")
    val cancel = L("取消", "Cancel")
    val download = L("从云端下载", "Download from cloud")
    val downloadAction = L("下载", "Download")
    val downloadHint = L(
        "拉取云端课表快照，确认后导入为手机上的独立课表（不覆盖现有课表）",
        "Fetches the cloud snapshot and imports it as a separate local schedule after confirmation; current schedules stay untouched",
    )
    val downloadPreview = L(
        "将导入云端课表「%s」（%d 条排课记录，云端版本 %d）。",
        "Imports cloud schedule \"%s\" (%d course records, cloud revision %d).",
    )
    val downloadConfirm = L("确认导入", "Import")
    val downloadDone = L("已导入为独立课表「%s」，可在课表切换中查看", "Imported as a separate schedule \"%s\"; switch schedules to view it")
    val unsaved = L("配置已修改，请先保存后再操作", "Configuration changed. Save before continuing")
    val loadFailed = L("无法读取加密配置，请重新填写后保存", "Could not read encrypted configuration. Enter it again and save")
    val operationFailed = L("操作失败，请检查连接、Token 或云端版本后重试", "Operation failed. Check the connection, token or cloud revision")

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
