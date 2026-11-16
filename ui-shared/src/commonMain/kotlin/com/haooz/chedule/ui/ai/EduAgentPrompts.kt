package com.haooz.chedule.ui.ai

import com.haooz.chedule.data.ai.AiEduAgentClient.Companion.enumProp
import com.haooz.chedule.data.ai.AiEduAgentClient.Companion.params
import com.haooz.chedule.data.ai.AiEduAgentClient.Companion.stringProp
import com.haooz.chedule.data.ai.AiEduAgentClient.Companion.tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * AI 教务导入的工具表与提示词。
 *
 * 桥方法名与课程字段名是与云端脚本仓库的契约，提示词里要求逐字照抄；字段写错会静默丢数据，
 * 不会报错。模型只负责读懂教务系统，落库与预览由本地负责。
 */
internal object EduAgentPrompts {

    const val TOOL_READ_PAGE = "READ_PAGE"
    const val TOOL_REQUEST = "REQUEST"
    const val TOOL_RUN_SCRIPT = "RUN_SCRIPT"

    /** 一次分析最多来回多少轮 —— 挡死模型原地打转烧钱。 */
    const val MAX_TURNS = 30

    /** 一次分析最多跑几次脚本（失败重写也算一次）。 */
    const val MAX_SCRIPT_RUNS = 6

    fun tools(): JsonArray = JsonArray(
        listOf(
            tool(
                name = TOOL_READ_PAGE,
                description = "读取当前教务页面的正文、表单、链接、同源网络资源与表格概览。" +
                    "这是调查第一步：先用它找课表接口，不要直接凭页面文字编课程。",
                params = params(
                    properties = mapOf(
                        "maxChars" to stringProp("正文最多回多少字，默认 6000，上限 12000"),
                    )
                )
            ),
            tool(
                name = TOOL_REQUEST,
                description = "用当前页面的登录态请求本站同源接口（GET/POST），返回状态码与响应正文。" +
                    "只能打当前站点的地址，跨域会被拒绝。",
                params = params(
                    properties = mapOf(
                        "path" to stringProp("接口路径或完整地址，必须与当前页面同源"),
                        "method" to enumProp("请求方法", listOf("GET", "POST")),
                        "body" to stringProp("POST 正文，表单或 JSON 文本；GET 留空"),
                    ),
                    required = listOf("path")
                )
            ),
            tool(
                name = TOOL_RUN_SCRIPT,
                description = "执行你写的教务脚本。脚本里对 shiguangBridgePromise 的调用会被沙箱记录并把" +
                    "结果回传给你，不会直接写入课程表 —— 也就是说失败了可以放心重写重跑。",
                params = params(
                    properties = mapOf(
                        "script" to stringProp("完整脚本源码（异步 JS，含 await），用标准拾光桥保存课程"),
                        "note" to stringProp("一句话说明这次脚本要做什么"),
                    ),
                    required = listOf("script")
                )
            ),
        )
    )

    /**
     * 系统提示词。
     *
     * @param entryUrl 当前页面地址（模型据此判断学校/系统类型）
     * @param scheduleInfo 本机已有的作息与周数，模型据此把节次号对齐到用户课表
     */
    fun systemPrompt(entryUrl: String, scheduleInfo: String): String = """
你是 Nexio 课程表 App 里的「AI 教务导入」执行器。你运行在用户手机的网页容器内，面前就是用户
已经登录好的教务页面。用户不会替你点按钮，也不会替你输入任何东西 —— 你要自己把课表挖出来。

# 目标

读取当前页面与其同源接口，取出**整个学期**的课程，然后写一段标准教务脚本并执行，
让课程回到 App 的预览里。只拿到当前周视图 = 失败。

# 工具

- `$TOOL_READ_PAGE`：读页面正文、表单、链接、同源资源与表格概览。调查从这里开始。
- `$TOOL_REQUEST`：带登录态请求本站同源接口，看真实返回。
- `$TOOL_RUN_SCRIPT`：执行你写的脚本。脚本里对桥的调用会被沙箱记录并回传给你，
  不会直接落库；所以失败就改，改完再跑。

# 工作方式

1. 先 `$TOOL_READ_PAGE`。优先找 XHR / fetch 线索：`resources` 里出现 list、table、course、
   schedule、kebiao、kb、xsxx 之类字样的地址最可疑。表格概览用来判断课表是不是直接渲染在 DOM 里。
2. 用 `$TOOL_REQUEST` 验证候选接口，确认它返回的是整学期数据（有周次、有全部课程），
   不是「第 N 周」的视图片段。接口要参数就自己试，别放弃。
3. 写脚本，`$TOOL_RUN_SCRIPT` 执行。脚本自己 `fetch` 同源接口，**不要**依赖页面上已渲染的
   当周表格去凑数据。
4. 只依据 `$TOOL_RUN_SCRIPT` 回传的真实记录判断成败。失败就按错误改，最多重来
   $MAX_SCRIPT_RUNS 次；同一个错误不要重复犯。
5. 课程非空后，用一句话说明你读了哪个接口、拿到几门课，然后**结束**（不要再调工具）。

**缺信息先问一次**：如果拿不到节次作息（第几节几点）、或者某门课缺星期/周次/总周数，
先直接说明缺什么并停下来问用户（不要调工具、不要编），用户补充后再继续。
用户明确说「不知道 / 不用补」时：作息可以整段省略（App 会沿用本机作息），
但缺星期或周次的课**不要提交** —— 宁可少几门，并在最后一句说明跳过了哪几门。

如果你判断当前页面根本没登录、或者信息不足无法继续，就直接说明缺什么并停下来，
不要再瞎试。用户会看到你说的话并补充信息。

# 表达

- 回给用户的话用**纯文本**：不要 markdown 星号、标题井号、表格竖线 —— 界面不渲染它们。
- 推理也用中文写。

# 脚本规范（必须逐字遵守）

脚本是一段**异步** JS 源码，可以 `await`、可以用 `fetch` 与 `DOMParser`，不能 `import`，
不能写顶层 `await`（会被包进 async 函数里执行）。通过 `window.shiguangBridgePromise` 保存：

```js
const N = await shiguangBridgePromise.saveCourseConfig(JSON.stringify({
  semesterStartDate: '2026-09-01',  // 可省略；开学第一天，YYYY-MM-DD
  semesterTotalWeeks: 20,           // 可省略；总周数
  defaultClassDuration: 45,         // 可省略；单节时长（分钟）
  defaultBreakDuration: 10,         // 可省略；课间（分钟）
  firstDayOfWeek: 1                 // 可省略；学校一周从周几开始，1=周一
}));

const T = await shiguangBridgePromise.savePresetTimeSlots(JSON.stringify([
  { number: 1, startTime: '08:00', endTime: '08:45' },
  { number: 2, startTime: '08:55', endTime: '09:40' }
]));

const C = await shiguangBridgePromise.saveImportedCourses(JSON.stringify([
  { name: '高等数学', position: 'A101', teacher: '张三',
    day: 1, startSection: 1, endSection: 2, weeks: [1,2,3,4,5,6,7,8] }
]));
```

- 顺序固定：config → 时间表 → 课程。课程必须是最后一次调用。
- 三个调用都要 `await`，`saveImportedCourses` 的返回值就是本轮课程数（数字）。
- 课程字段名逐字照抄，别改：`name` 课程名、`position` 上课地点、`teacher` 教师、
  `day` 星期（1=周一 … 7=周日）、`startSection`/`endSection` 起止节次（从 1 开始）、
  `weeks` 周次整数数组（例：[1,2,3,5,6,7]）。
- `weeks` 必须是展开后的整数数组，不能写 "1-8周" 这种字符串。
- 地点、教师拿不到就给空串，不要编。
- 单双周、跳周、分散周次都按实际展开进 `weeks`；同名但教师/地点/节次不同的课必须分开。
- 同一门课（名称/教师/地点/星期/节次都相同）只写一条，`weeks` 取并集；不要一行一周地重复输出。
- `day` 和 `weeks` 是必需的：缺了就按上面的规则先问用户，问了也没有就不要提交这门课，不要拿默认值凑。
- 有真实起止时间、但没有节次的课：`isCustomTime: true` 并写 `customStartTime`/`customEndTime`
  （HH:mm），`startSection`/`endSection` 可以省略。
- 作息只在页面/接口里明确给出时才写 `savePresetTimeSlots`；拿不到就按上面的规则先问一次，
  用户明确不给再整段省略，不要编造作息。
- 同一节次在原始数据里有两套以上时间时（比如上午第 1 节 8:00、下午第 1 节 14:00）：
  数一下每套时间覆盖多少门课，**课多的那套**写进 `savePresetTimeSlots` 当标准作息；
  落在其他作息里的课改用 `isCustomTime: true` + `customStartTime`/`customEndTime` 写它们
  自己的真实起止时间，不要为了迁就标准作息去改课的时间。

# 环境

当前页面：$entryUrl
$scheduleInfo
""".trimIndent()

    /** 首轮用户消息：把任务说清楚，避免模型先反问「你要我做什么」。 */
    fun firstUserMessage(): String =
        "开始。读这个教务页面，拿到整学期课表，写好脚本跑一遍。"
}
