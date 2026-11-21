# MCP 隐私设置与个人云接入（实验分支）

本次改动位于 `feat/island-auth-local-mcp`。本地 MCP 基于上游提交 `17905e6ea657dea3119e37b838b6687fe54ec715` 接入旧 fork；云端面板是独立可选扩展。工作区还包含之前的超级岛实验改动，尚未拆分提交。

## 页面入口

`设置 → AI 功能 → MCP 服务`。

页面分为「手机本地 MCP」与「云端 MCP（可选扩展）」。两个开关相互独立。云端连接开关仅控制本应用是否允许发起云端请求，不负责启停服务器进程，也不影响手机本地服务。

## 隐私显示

- 模型服务商的接口地址、API Key，以及本地和云端 Token 默认掩码显示。
- 主动点击「显示」后可查看；30 秒后、输入框失焦、窗口失焦或控件禁用时重新隐藏。
- 显示状态不通过 `rememberSaveable` 保存，重新进入页面默认隐藏。
- 复制需要主动点击。Android 剪贴板数据标记为敏感，页面短时提醒不要分享到聊天或截图中。
- 模型连接测试不显示供应商原始错误响应，避免错误正文回显凭据和地址。
- 本次没有禁止全应用截图。主动显示、截图瞬间的系统行为、屏幕录制和第三方键盘应另行在设备上验证。

“掩码显示”不等于“所有原有凭据已加密”。本地 MCP Token、原有 BYOK Key 沿用原本的存储；本次新增的云端 URL、Token 和启用状态使用 Android Keystore AES-GCM 加密，密文保存在 `noBackupFilesDir/cloud_mcp.enc`，不写入课表 SQLite 和导出 JSON。无法解密时提示重新填写，不回退明文。Android Keystore 的设备读写尚未实机验收。

## UI 规范对齐

深色主题下部分标题显示为黑色的根因：新增文字用了 Material3 版全局 `Text`，其默认样式取 `LocalTextStyle`，在 Miuix 深色主题里不跟随主题色。修复方式是把 MCP 相关页面统一到工程现有的 Miuix 版全局 `Text`（`text.miuix.Text`，默认回退 Miuix 主题色），并对所有新增行显式传入 `onSurface` / `onSurfaceVariantSummary`，与设置页其余部分同源。

其余视觉按 Nexio 设置页现行结构重做，不引入自创样式：

- **本地设置收为一张偏好卡**：`SwitchPreference`（启用与运行摘要）→ 细分隔线 → `BasicComponent`（服务器地址 + 右侧「复制」胶囊）→ 细分隔线 → 私密 Token 字段，与「备份中心」等原生设置页的行密度一致；不再是开关、地址、Token 各自一张大卡。
- **云端区收为一张配置卡**：`SwitchPreference`（启用）→ 云端地址 → 云端 Token → 状态行 → 保存/测试按钮行 → 隐私说明 → 细分隔线 → 上传行（标题 + 摘要 + 主题蓝「上传」胶囊）→ 预览确认区，全部在卡内用 0.5dp 低对比分隔线分节。
- 私密字段与 AI 页行内输入同款：标签 17sp/Medium 靠左，输入无边框右对齐，显隐/复制为胶囊按钮（64/36dp、cornerRadius 100dp）。
- 页面顶部留白按 `pageTopExtra(hasSmallTitle = true)` 修正（此前误传 false 多出一截空白）。
- 掩码、30 秒/失焦/窗口失焦重隐藏、复制敏感标记等隐私行为保持不变。

## 超级岛「一键授权」移除

课程提醒页的「一键授权」串行授权链已整体移除：页面入口（`ArrowPreference`）、串行编排（`ReminderMaintenance` 及其 Action/结果汇总）、控制器接口 `authorizeReminderPermissions` / `supportsReminderMaintenance` / `cancelReminderMaintenance`、页面 `pendingMaintenanceStep` 回调接管逻辑，以及专属文案（`oneTapAuthorize*`、`maintain*`）。

保留不变的部分：每个权限项自己的授权入口（通知弹窗、精确闹钟页、勿扰设置页、电池优化页）、超级岛卡内的 Shizuku 单项授权按钮，以及「强制执行」（闹钟重排与提醒刷新，独立状态，不再与授权链共用 busy/result）。

## 云端接入步骤

本版针对个人课表云服务 `nexio-cloud-mcp` 的无状态 JSON MCP，不作为任意 MCP 服务器的通用管理器。

1. 在服务器部署个人课表云 MCP，准备最终 HTTPS 地址与该实例的 Bearer Token。
2. 在本页填写 `https://你的域名/mcp` 和 Token。
3. 开启「启用云端连接」，点击「保存配置」。保存只写本地加密配置，不联网、不上传课程。
4. 点击「测试连接」。依次执行 MCP 初始化、工具列表和 `get_sync_status`，显示课表是否存在及云端版本。关闭连接后禁止测试和上传。
5. 需要发布手机当前课表时，点击「上传当前课表」。这个动作会把课程 JSON 发送给所填服务器，生成未提交的预览。
6. 查看排课条目数、是否替换现有云端课表、基准版本。点击「确认上传」才提交；「取消」仅放弃本次预览。

修改地址、Token 或开关后，旧预览作废，并要求先保存再操作。云端版本在预览后发生变化时，服务器拒绝旧预览覆盖。确认调用只带 `confirm=true` 与 `pending_id`，不会重复附上课表正文。确认成功直接使用本次保存返回的版本，避免后续状态查询失败被误报成保存失败。

本版是手动上传。云端 AI 改课不会自动同步回手机，也不会自动重排本地闹钟。当前课表为空时不自动用空表替换云端数据。

## 从云端下载

上传卡下方有「从云端下载」：拉取 `export_schedule_file`（nexio 格式快照，只读、不产生云端版本变化），预览显示云端课表名、排课条数与云端版本；点「确认导入」后走应用现成的单课表导入路径落库：

- **导入为独立课表**并自动切换过去，本地已有同名课表时自动加「 · 2」「 · 3」后缀，不覆盖任何现有课表。
- 课程正文只在确认导入后进入内存与数据库，预览阶段只显示元信息。
- 导入失败（格式不合法、节次缺字段等）走统一失败文案，不产生半截课表。

## 网络与错误边界

- 只接受 HTTPS，路径为空时补成 `/mcp`；拒绝 URL 内的账号、查询参数和片段。
- 不跟随服务器重定向，避免 Token 被转发到另一地址。
- Bearer Token 放在请求头，不进入 URL、结果日志或配置 `toString()`。
- 响应体最多 1 MiB；连接超时 5 秒、读取超时 10 秒、单次请求超时 20 秒。
- 协议版本支持 `2025-03-26`、`2025-06-18`、`2025-11-25` 的旧版 JSON 初始化流程；不支持任意新版本、OAuth-only 客户端或通用 SSE 响应。
- 错误页只展示固定提示，完整服务端响应不直接放进设置页。
- 不在打开页面、保存配置或应用启动时自动上传。

## 验证范围

已执行 Android 宿主机的 `CloudMcpClientTest`，共 10 项：关闭连接不发请求、HTTPS URL 校验、只读测试、独立预览确认、版本绑定替换、旧预览失败、重定向拒绝、响应 ID/预览版本校验、响应大小限制和敏感配置 `toString()` 隐藏。

测试通过 OkHttp 应用拦截器使用合成数据，未发送真实网络请求。它验证客户端协议行为，不代表 TLS、DNS 或远端部署已经实连。

构建与检查命令：

```sh
gradle :androidApp:testDebugUnitTest :androidApp:assembleDebug :ui-shared:compileKotlinJvm checkKmpPurity
```

最终结果和 APK 校验值由本次构建报告确认。

### 模拟器验收（2026-11-21，emulator-5554，Android 16 / API 36）

- `mcpTest` 包（com.haooz.chedule.mcptest）覆盖安装成功，深浅色两轮截图验收：合并后的本地单卡与云端单卡、细分隔线、行密度与字号与 Nexio 设置页一致；深色主题文字全部为正确的主题色（M3 全局 Text 不跟随 Miuix 深色主题的根因已修复）。
- 课程提醒页确认：一键授权入口消失，仅剩「强制执行」（提醒未开启时禁用）与超级岛卡内 Shizuku 单项授权；通知/精确闹钟/勿扰/电池各权限项的原有入口保留。
- **真实云端 HTTPS 实连通过**：在 MCP 页填入用户提供的云端地址与 Bearer Token（经模拟器本地文件注入，屏幕只显示掩码），点「保存配置」状态行显示「配置已加密保存」（Keystore AES-GCM 写入实机验证），覆盖安装重启后地址/Token/开关从加密存储完整读回（重启读取验证）。
- **上传确认与取消全流程通过**（2026-11-21）：空课表点上传被客户端拦截（固定失败文案，未向云端发送任何数据）；添加合成测试课程 TestCourseA 后点上传生成预览「将上传 1 条排课记录并替换当前云端课表，基于云端版本 1」，点「确认上传」后状态行「连接正常 · 云端版本 2 · 已有课表」（服务端版本递增）；再次生成预览后点「取消」，「测试连接」确认版本仍为 2——取消不落盘。云端为用户自建测试服务器，测试数据为合成课程名。
- 显隐/复制实测：点「显示」明文出现且按钮切「隐藏」，30 秒后自动重掩码；点「复制」按钮变「已复制」并出现剪贴板敏感警告行，系统剪贴板气泡同步显示（系统侧亦打码）；测试后剪贴板已用本地无害地址覆盖。
- 本地服务开关：开→「运行中，可在 AI 助手中添加」→关，`dumpsys notification` 确认 `nexio_mcp_server` 渠道注册与通知随开关移除。
- **云端下载实测**（2026-11-21）：点下载生成预览「将导入云端课表「默认课表」（1 条排课记录，云端版本 2）」，确认导入后 SQLite `schedule`/`course` 双表落库（`默认课表 · 2` + TestCourseA），自动切换为当前课表；「全部课表」列表重启后可见新表。12 项客户端单测通过（新增下载只读、云端空表拒绝）。
- 测试后已删除模拟器与宿主机上的令牌临时文件；测试令牌未写入任何日志、文档或交付物。
- 仍需覆盖：Token 字段的「显示」点击（与地址同组件，未单独点）、切后台/窗口失焦重掩码、Keystore 密钥失效提示、超级小爱实际调用本地 MCP 端口。

## Nexio 分支与可共存测试构建

目标仍是 Nexio 内的功能分支：普通 `debug` / `release` 保留包名 `com.haooz.chedule` 和本地 MCP 端口 `26611`。为安装测试增加了独立的 `mcpTest` 构建类型，并非另起一个 App 项目。

| 项目 | 普通 Nexio 构建 | 本次共存测试构建 |
| --- | --- | --- |
| 包名 | `com.haooz.chedule` | `com.haooz.chedule.mcptest` |
| 桌面名称 | Nexio 原名称 | Nexio MCP 测试 |
| 本地 MCP 端口 | `26611` | `26612` |
| Provider 前缀 | `com.haooz.chedule` | `com.haooz.chedule.mcptest` |
| 签名 | 原有构建配置 | 本地专用测试证书 |

共存包使用独立数据目录，不会直接读取正式版课程、Key 或 Token。需要在测试版手动导入课表、填写云端地址。两个版本若配置相同云端地址和 Token，操作的是同一份云端课表；测试包隔离不意味着远端数据隔离。

本地测试钥匙位于 `.gitignore` 已排除的 `keystore/nexio-mcp-test.p12`，本次未将私钥放入交付 ZIP。以后本机继续使用同一钥匙可覆盖更新测试包，不应每次重新生成。新 checkout 构建前需生成自己的测试钥匙：

```sh
mkdir -p keystore
keytool -genkeypair -keystore keystore/nexio-mcp-test.p12 \
  -storetype PKCS12 -storepass nexio-local-test -keypass nexio-local-test \
  -alias nexio-mcp-test -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=Nexio MCP Local Test, OU=Development, O=Nexio Fork" -noprompt
gradle :androidApp:assembleMcpTest :androidApp:testDebugUnitTest checkKmpPurity
```

测试证书密码是构建约定，不是正式发布凭据；正式签名配置未改。本次验证了 `assembleMcpTest` 生成包名、桌面名、Provider 前缀、签名和 APK 对齐，并运行原有 10 项 Debug 接入测试；没有 `testMcpTestUnitTest` 任务，不把 Debug 测试说成独立变体的设备测试。

设备插件已连接但其运行环境没有识别本机 SDK / adb，因而未完成安装和点击验收；没有改动真机或卸载正式 App。测试包支持独立安装的包名与签名条件已离线核对，实际共存启动和厂商系统行为仍需设备验收。
