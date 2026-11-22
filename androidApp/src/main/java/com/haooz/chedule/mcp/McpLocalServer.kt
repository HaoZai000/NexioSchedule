package com.haooz.chedule.mcp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.haooz.chedule.data.holiday.HolidayEntry
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.mcp.McpServicePrefs
import com.haooz.chedule.data.mcp.McpServiceBridge
import com.haooz.chedule.data.mcp.McpToolCatalog
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.truncateNote
import com.haooz.chedule.data.repo.CourseRepository
import com.haooz.chedule.data.util.todayLocalDate
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 本地 MCP 服务。
 *
 * 只监听回环地址，供手机上的 AI 助手（超级小爱）直接读写课表，数据不出本机。
 * 实现 MCP Streamable HTTP 的最小闭环：`initialize` / `tools/list` / `tools/call` / `ping`，
 * 不维护会话（服务端无状态，响应一律 `application/json`）。
 *
 * 写操作统一走「预览 → 确认」：首次调用只登记待办并返回摘要，带 `pending_id` 与
 * `confirm=true` 再调一次才落库；每次成功修改都会保留一份快照供 `undo_last_change` 回滚。
 */
object McpLocalServer {

    private const val TAG = "NexioMcp"

    /** 待确认操作的有效期：过期后必须重新发起，避免用旧预览执行。 */
    private const val PENDING_TTL_MS = 5 * 60 * 1000L

    private val gson = Gson()

    @Volatile
    private var serverSocket: ServerSocket? = null

    val isRunning: Boolean get() = serverSocket != null

    private val pendingWrites = mutableMapOf<String, PendingWrite>()

    /** 最近一次修改：描述 + 回滚动作。 */
    private var lastUndo: Pair<String, () -> String>? = null

    /** 幂等启动：重复调用不会重复占用端口。 */
    fun start() {
        if (serverSocket != null) return
        try {
            val socket = ServerSocket(
                McpServiceBridge.port,
                8,
                InetAddress.getByName("127.0.0.1"),
            )
            serverSocket = socket
            Thread({ acceptLoop(socket) }, "nexio-mcp").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "MCP 服务已启动: ${McpServiceBridge.serverUrl}")
        } catch (e: Exception) {
            Log.e(TAG, "MCP 服务启动失败", e)
        }
    }

    fun stop() {
        val socket = serverSocket ?: return
        serverSocket = null
        pendingWrites.clear()
        runCatching { socket.close() }
        Log.i(TAG, "MCP 服务已停止")
    }

    private fun acceptLoop(server: ServerSocket) {
        while (true) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                // stop() 关闭监听后 accept 抛异常，属正常退出
                Log.i(TAG, "accept 结束: ${e.message}")
                return
            }
            // 一次对话只有一两发请求，串行处理足够，不引入线程池
            try {
                handle(client)
            } catch (e: Exception) {
                Log.w(TAG, "请求处理失败", e)
            } finally {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = socket.getInputStream()
        val headerBlock = readHeaderBlock(input) ?: return
        val lines = headerBlock.split("\r\n")
        val requestLine = lines.firstOrNull().orEmpty().split(" ")
        val method = requestLine.getOrNull(0)?.uppercase() ?: return
        val path = requestLine.getOrNull(1)?.substringBefore('?')?.trimEnd('/') ?: return
        val headers = lines.drop(1)
            .filter { it.contains(':') }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val body = readBody(input, headers["content-length"]?.toIntOrNull() ?: 0)

        when {
            method == "GET" && path == "/health" ->
                write(socket, "200 OK", """{"ok":true,"server":"nexio-schedule"}""")

            method == "POST" && path == "/mcp" ->
                handleMcp(socket, headers["authorization"], body)

            method == "DELETE" && path == "/mcp" ->
                write(socket, "200 OK", "")

            path == "/mcp" ->
                // 规范允许服务端用 405 拒绝 GET SSE 流
                write(socket, "405 Method Not Allowed", "")

            else -> write(socket, "404 Not Found", """{"error":"not found"}""")
        }
    }

    private fun handleMcp(socket: Socket, authorization: String?, body: String) {
        // Token 留空即不校验；填写后 Bearer 前缀可有可无
        val token = McpServicePrefs.token()
        if (token.isNotBlank() &&
            !authorization.equals("Bearer $token", ignoreCase = true) &&
            !authorization.equals(token, ignoreCase = true)
        ) {
            write(socket, "401 Unauthorized", """{"error":"invalid token"}""")
            return
        }

        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        if (root == null) {
            write(socket, "400 Bad Request", """{"error":"invalid json"}""")
            return
        }
        // 通知（notifications/initialized 等）没有 id，按规范回 202 空体
        if (!root.has("id") || root.get("id").isJsonNull) {
            write(socket, "202 Accepted", "")
            return
        }

        val id = root.get("id")
        val method = root.get("method")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val params = root.getAsJsonObject("params") ?: JsonObject()

        val response = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
        }

        try {
            val result = when (method) {
                "initialize" -> initializeResult(params)
                "ping" -> JsonObject()
                "tools/list" -> toolsListResult()
                "tools/call" -> toolsCallResult(params)
                else -> throw UnsupportedOperationException("未知方法: $method")
            }
            response.add("result", result)
        } catch (e: UnsupportedOperationException) {
            response.add("error", JsonObject().apply {
                addProperty("code", -32601)
                addProperty("message", e.message)
            })
        } catch (e: Exception) {
            Log.w(TAG, "工具调用失败", e)
            response.add("error", JsonObject().apply {
                addProperty("code", -32602)
                addProperty("message", e.message ?: "调用失败")
            })
        }
        write(socket, "200 OK", gson.toJson(response))
    }

    private fun initializeResult(params: JsonObject): JsonObject {
        // 客户端报什么版本就回什么版本；未报时用当前规范版本
        val version = params.get("protocolVersion")?.takeIf { it.isJsonPrimitive }?.asString
            ?: "2025-06-18"
        return JsonObject().apply {
            addProperty("protocolVersion", version)
            add("capabilities", JsonObject().apply { add("tools", JsonObject()) })
            add("serverInfo", JsonObject().apply {
                addProperty("name", "nexio-schedule")
                addProperty("version", "1.0.0")
            })
        }
    }

    private fun toolsListResult(): JsonObject = JsonObject().apply {
        add("tools", JsonArray().apply {
            McpToolCatalog.tools.forEach { spec ->
                add(JsonObject().apply {
                    addProperty("name", spec.name)
                    addProperty("description", spec.descriptionZh)
                    add("inputSchema", toolSchema(spec.name))
                })
            }
        })
    }

    // ── 工具 schema ───────────────────────────────────────────────────────

    private fun prop(type: String, description: String): JsonObject = JsonObject().apply {
        addProperty("type", type)
        addProperty("description", description)
    }

    private fun schema(pairs: List<Pair<String, JsonObject>>, required: List<String>): JsonObject =
        JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                pairs.forEach { (key, value) -> add(key, value) }
            })
            add("required", JsonArray().apply { required.forEach { add(it) } })
        }

    /** 写工具统一追加确认参数。 */
    private fun writeSchema(pairs: List<Pair<String, JsonObject>>, required: List<String>): JsonObject {
        val withConfirm = pairs + listOf(
            "confirm" to prop("boolean", "首次调用不要传；确认执行时传 true"),
            "pending_id" to prop("string", "首次调用返回的 pendingId"),
        )
        return schema(withConfirm, required)
    }

    private fun toolSchema(name: String): JsonObject = when (name) {
        "get_week_courses" -> schema(
            listOf("week" to prop("integer", "教学周次，默认当前周")),
            emptyList(),
        )

        "search_courses" -> schema(
            listOf("query" to prop("string", "关键词：课程名/教师/地点/备注")),
            listOf("query"),
        )

        "get_courses_on_date" -> schema(
            listOf("date" to prop("string", "YYYY-MM-DD，默认今天")),
            emptyList(),
        )

        "add_course" -> writeSchema(
            listOf(
                "name" to prop("string", "课程名"),
                "day_of_week" to prop("integer", "1=周一 … 7=周日"),
                "start_section" to prop("integer", "起始节"),
                "end_section" to prop("integer", "结束节，默认同起始节"),
                "weeks" to prop("array", "周次数组，默认全部"),
                "location" to prop("string", "地点"),
                "teacher" to prop("string", "教师"),
                "note" to prop("string", "备注"),
                "week_type" to prop("integer", "0=全周 1=单周 2=双周"),
                "start_time" to prop("string", "自定义开始 HH:mm"),
                "end_time" to prop("string", "自定义结束 HH:mm"),
            ),
            listOf("name", "day_of_week", "start_section"),
        )

        "update_course" -> writeSchema(
            listOf(
                "course_id" to prop("string", "课程 id，优先"),
                "course_name" to prop("string", "课程名"),
                "from_day_of_week" to prop("integer", "原星期，用于区分同名"),
                "day_of_week" to prop("integer", "新星期"),
                "start_section" to prop("integer", "新起始节"),
                "end_section" to prop("integer", "新结束节"),
                "weeks" to prop("array", "新周次数组"),
                "week_type" to prop("integer", "0=全周 1=单周 2=双周"),
                "name" to prop("string", "新课程名"),
                "location" to prop("string", "地点"),
                "teacher" to prop("string", "教师"),
                "note" to prop("string", "备注"),
                "all" to prop("boolean", "多门时全部修改"),
            ),
            emptyList(),
        )

        "delete_course" -> writeSchema(
            listOf(
                "course_id" to prop("string", "课程 id，优先"),
                "course_name" to prop("string", "课程名"),
                "from_day_of_week" to prop("integer", "原星期"),
                "all" to prop("boolean", "多门时全部删除"),
            ),
            emptyList(),
        )

        "move_course_occurrence" -> writeSchema(
            listOf(
                "course_name" to prop("string", "课程名，同天多门时必填"),
                "from_date" to prop("string", "原日期 YYYY-MM-DD"),
                "to_date" to prop("string", "目标日期 YYYY-MM-DD"),
                "start_section" to prop("integer", "目标起始节，默认不变"),
                "end_section" to prop("integer", "目标结束节，默认不变"),
                "all" to prop("boolean", "多门时全部移动"),
            ),
            listOf("from_date", "to_date"),
        )

        "cancel_class_on_date" -> writeSchema(
            listOf(
                "date" to prop("string", "YYYY-MM-DD"),
                "course_name" to prop("string", "只停这一门，默认全部"),
            ),
            listOf("date"),
        )

        "add_holiday" -> writeSchema(
            listOf(
                "start_date" to prop("string", "开始 YYYY-MM-DD"),
                "end_date" to prop("string", "结束 YYYY-MM-DD，默认单日"),
                "name" to prop("string", "名称，默认「假期」"),
            ),
            listOf("start_date"),
        )

        "remove_holiday" -> writeSchema(
            listOf(
                "date" to prop("string", "该日期所在假期"),
                "name" to prop("string", "按名称"),
            ),
            emptyList(),
        )

        "set_semester_info" -> writeSchema(
            listOf(
                "class_start" to prop("string", "开学日期 YYYY-MM-DD"),
                "total_weeks" to prop("integer", "总周数"),
                "current_week" to prop("integer", "当前周"),
            ),
            emptyList(),
        )

        else -> schema(emptyList(), emptyList())
    }

    // ── 工具调用 ──────────────────────────────────────────────────────────

    private fun toolsCallResult(params: JsonObject): JsonObject {
        val name = params.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val args = params.getAsJsonObject("arguments") ?: JsonObject()
        val text = try {
            callTool(name, args)
        } catch (e: Exception) {
            Log.w(TAG, "工具调用失败: $name", e)
            return JsonObject().apply {
                add("content", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "调用失败: ${e.message}")
                    })
                })
                addProperty("isError", true)
            }
        }
        return JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                })
            })
            addProperty("isError", false)
        }
    }

    private fun callTool(name: String, args: JsonObject): String {
        val repository = CourseRepository()
        val today = todayLocalDate()
        return when (name) {
            // ── 只读 ──
            "get_courses_on_date" -> {
                val date = if (args.string("date") == null) {
                    today
                } else {
                    requireDate(args.date("date"), "date")
                }
                coursesOnDateJson(repository, date)
            }
            "get_week_courses" -> weekCoursesJson(repository, today, args.int("week"))
            "get_semester_info" -> semesterInfoJson(repository, today)
            "get_next_class" -> nextClassJson(repository)
            "search_courses" -> {
                val query = args.string("query")?.trim().orEmpty()
                if (query.isEmpty()) throw IllegalArgumentException("缺少 query 参数")
                searchCoursesJson(repository, query)
            }

            // ── 写操作：先预览，确认后再执行 ──
            "add_course" -> runWrite(name, args, repository) { buildAddCourse(repository, args) }
            "update_course" -> runWrite(name, args, repository) { buildUpdateCourse(repository, args) }
            "delete_course" -> runWrite(name, args, repository) { buildDeleteCourse(repository, args) }
            "move_course_occurrence" -> runWrite(name, args, repository) {
                buildMoveOccurrence(repository, args)
            }
            "cancel_class_on_date" -> runWrite(name, args, repository) {
                buildCancelOnDate(repository, args)
            }
            "add_holiday" -> runWrite(name, args, repository) { buildAddHoliday(args) }
            "remove_holiday" -> runWrite(name, args, repository) { buildRemoveHoliday(args) }
            "set_semester_info" -> runWrite(name, args, repository) {
                buildSemesterInfo(repository, args)
            }
            "undo_last_change" -> undoLastChange()

            else -> throw IllegalArgumentException("未知工具: $name")
        }
    }

    // ── 写操作基础设施 ────────────────────────────────────────────────────

    private class WritePlan(
        val summary: String,
        val execute: () -> String,
    )

    private class PendingWrite(
        val id: String,
        val createdAt: Long,
        val tool: String,
        val summary: String,
        val action: () -> String,
    )

    private class ScheduleSnapshot(
        val courses: List<Course>,
        val holidays: Map<Int, List<HolidayEntry>>,
    )

    private fun runWrite(
        toolName: String,
        args: JsonObject,
        repository: CourseRepository,
        build: () -> WritePlan,
    ): String {
        val confirm = args.bool("confirm") == true
        val pendingId = args.string("pending_id")

        if (confirm && !pendingId.isNullOrBlank()) {
            purgeExpired()
            val pending = pendingWrites.remove(pendingId)
                ?: throw IllegalArgumentException("预览不存在或已过期，请重新调用 $toolName 生成预览")
            // 快照必须在执行前取：撤销要回到「这次修改之前」的状态
            val snapshot = ScheduleSnapshot(
                courses = courseList(repository),
                holidays = runCatching { HolidayManager.loadAllByYear() }.getOrDefault(emptyMap()),
            )
            val result = pending.action()
            lastUndo = pending.summary to {
                repository.saveCourses(snapshot.courses)
                snapshot.holidays.forEach { (year, entries) -> HolidayManager.save(year, entries) }
                okJson("已撤销：${pending.summary}")
            }
            return result
        }

        val plan = build()
        purgeExpired()
        val id = randomId()
        pendingWrites[id] = PendingWrite(
            id = id,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            tool = toolName,
            summary = plan.summary,
            action = plan.execute,
        )
        return gson.toJson(JsonObject().apply {
            addProperty("requiresConfirmation", true)
            addProperty("pendingId", id)
            addProperty("summary", plan.summary)
            addProperty(
                "hint",
                "确认执行请再次调用 $toolName，参数传 {\"confirm\":true,\"pending_id\":\"$id\"}",
            )
        })
    }

    private fun purgeExpired() {
        val now = Clock.System.now().toEpochMilliseconds()
        pendingWrites.entries.removeAll { now - it.value.createdAt > PENDING_TTL_MS }
    }

    private fun undoLastChange(): String {
        val undo = lastUndo ?: throw IllegalArgumentException("没有可撤销的修改")
        val result = undo.second()
        lastUndo = null
        return result
    }

    private fun okJson(summary: String, detail: JsonObject? = null): String =
        gson.toJson(JsonObject().apply {
            addProperty("ok", true)
            addProperty("summary", summary)
            detail?.entrySet()?.forEach { (key, value) -> add(key, value) }
            addProperty("undoHint", "可用 undo_last_change 撤销本次修改")
        })

    private fun randomId(): String = buildString {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        repeat(8) { append(chars[Random.nextInt(chars.length)]) }
    }

    // ── 写操作：课程 ──────────────────────────────────────────────────────

    private fun buildAddCourse(repository: CourseRepository, args: JsonObject): WritePlan {
        val name = args.string("name")?.trim().orEmpty()
        require(name.isNotEmpty()) { "缺少课程名 name" }
        val day = args.int("day_of_week") ?: throw IllegalArgumentException("缺少 day_of_week")
        require(day in 1..7) { "day_of_week 需为 1-7" }
        val start = args.int("start_section")
            ?: throw IllegalArgumentException("缺少 start_section")
        val end = args.int("end_section") ?: start
        require(start in 1..20 && end in start..20) { "节次不合法（1-20，且结束不早于开始）" }

        val totalWeeks = repository.getTotalWeeks()
        val weekType = args.int("week_type") ?: Course.WEEK_TYPE_ALL
        require(weekType in 0..2) { "week_type 只能是 0/1/2" }
        // 未显式给周次时，week_type 决定单双周，否则按全周展开
        val weeks = (args.intList("weeks") ?: when (weekType) {
            Course.WEEK_TYPE_ODD -> (1..totalWeeks).filter { it % 2 == 1 }
            Course.WEEK_TYPE_EVEN -> (1..totalWeeks).filter { it % 2 == 0 }
            else -> (1..totalWeeks).toList()
        }).distinct().sorted()
        require(weeks.isNotEmpty() && weeks.all { it in 1..totalWeeks }) {
            "周次需在 1-$totalWeeks 之间"
        }

        val location = args.string("location").orEmpty()
        val teacher = args.string("teacher").orEmpty()
        val note = args.string("note").orEmpty().truncateNote()
        val customStart = args.string("start_time")
        val customEnd = args.string("end_time")
        val custom = !customStart.isNullOrBlank() && !customEnd.isNullOrBlank()

        val summary = buildString {
            append("新增课程《$name》：周")
            append(weekdayName(day))
            append(" 第$start-$end 节，")
            append(weeksText(weeks))
            if (location.isNotBlank()) append("，地点 $location")
            if (teacher.isNotBlank()) append("，教师 $teacher")
        }

        return WritePlan(summary) {
            val courses = courseList(repository)
            val course = Course(
                id = newCourseId(),
                name = name,
                classroom = location,
                teacher = teacher,
                dayOfWeek = day,
                startSection = start,
                endSection = end,
                startWeek = weeks.first(),
                endWeek = weeks.last(),
                weekType = weekType,
                colorRes = Course.courseColors[courses.size % Course.courseColors.size],
                selectedWeeks = weeks,
                scheduleId = repository.getCurrentScheduleId(),
                isCustomTime = custom,
                customStartTime = if (custom) customStart else null,
                customEndTime = if (custom) customEnd else null,
                note = note,
            )
            repository.addCourse(course)
            okJson(summary)
        }
    }

    private fun buildUpdateCourse(repository: CourseRepository, args: JsonObject): WritePlan {
        val matches = requireResolved(resolveCourses(repository, args), args)
        val targetDay = args.int("day_of_week")
        val targetStart = args.int("start_section")
        val targetEnd = args.int("end_section")
        val weeksArg = args.intList("weeks")
        val weekType = args.int("week_type")
        val newName = args.string("name")?.trim()
        val location = args.string("location")
        val teacher = args.string("teacher")
        val note = args.string("note")?.truncateNote()

        require(
            targetDay != null || targetStart != null || targetEnd != null || weeksArg != null ||
                weekType != null || newName != null || location != null || teacher != null || note != null
        ) { "没有要修改的字段" }

        val totalWeeks = repository.getTotalWeeks()
        weeksArg?.let { weeks ->
            require(weeks.isNotEmpty() && weeks.all { it in 1..totalWeeks }) {
                "周次需在 1-$totalWeeks 之间"
            }
        }
        targetDay?.let { require(it in 1..7) { "day_of_week 需为 1-7" } }
        weekType?.let { require(it in 0..2) { "week_type 只能是 0/1/2" } }

        val summary = matches.joinToString("；") { course ->
            val changes = mutableListOf<String>()
            newName?.let { changes += "名称 ${course.name} → $it" }
            targetDay?.let { changes += "星期 ${weekdayName(course.dayOfWeek)} → ${weekdayName(it)}" }
            if (targetStart != null || targetEnd != null) {
                changes += "节次 ${course.startSection}-${course.endSection} → " +
                    "${targetStart ?: course.startSection}-${targetEnd ?: course.endSection}"
            }
            weeksArg?.let { changes += "周次 ${weeksText(activeWeeks(course))} → ${weeksText(it)}" }
            weekType?.let { changes += "周类型 → $it" }
            location?.let { changes += "地点 ${course.classroom.ifBlank { "空" }} → $it" }
            teacher?.let { changes += "教师 ${course.teacher.ifBlank { "空" }} → $it" }
            note?.let { changes += "备注已更新" }
            "修改${describe(course)}：${changes.joinToString("，")}"
        }

        return WritePlan(summary) {
            val ids = matches.map { it.id }.toSet()
            val updated = courseList(repository).map { course ->
                if (course.id !in ids) return@map course
                val newStart = targetStart ?: course.startSection
                val newEnd = maxOf(newStart, targetEnd ?: course.endSection)
                val newWeeks = weeksArg
                course.copy(
                    name = newName ?: course.name,
                    dayOfWeek = targetDay ?: course.dayOfWeek,
                    startSection = newStart,
                    endSection = newEnd,
                    startWeek = newWeeks?.min() ?: course.startWeek,
                    endWeek = newWeeks?.max() ?: course.endWeek,
                    weekType = weekType ?: course.weekType,
                    selectedWeeks = newWeeks ?: course.selectedWeeks,
                    classroom = location ?: course.classroom,
                    teacher = teacher ?: course.teacher,
                    note = note ?: course.note,
                    lastModified = Clock.System.now().toEpochMilliseconds(),
                )
            }
            repository.saveCourses(updated)
            okJson(summary)
        }
    }

    private fun buildDeleteCourse(repository: CourseRepository, args: JsonObject): WritePlan {
        val matches = requireResolved(resolveCourses(repository, args), args)
        val summary = "删除${matches.joinToString("、") { describe(it) }}"
        return WritePlan(summary) {
            val ids = matches.map { it.id }.toSet()
            repository.saveCourses(courseList(repository).filterNot { it.id in ids })
            okJson(summary)
        }
    }

    /** 只移动某一天的那次课：原课程去掉那一周，再补一条只含目标周的新课程。 */
    private fun buildMoveOccurrence(repository: CourseRepository, args: JsonObject): WritePlan {
        val fromDate = requireDate(args.date("from_date"), "from_date")
        val toDate = requireDate(args.date("to_date"), "to_date")
        val fromWeek = repository.getLiveTeachingWeek(fromDate)
        val toWeek = repository.getLiveTeachingWeek(toDate)
        val totalWeeks = repository.getTotalWeeks()
        require(fromWeek in 1..totalWeeks && toWeek in 1..totalWeeks) {
            "日期不在本学期教学周范围内（当前学期 1-$totalWeeks 周）"
        }

        val fromDay = fromDate.dayOfWeek.isoDayNumber
        val toDay = toDate.dayOfWeek.isoDayNumber
        val courseName = args.string("course_name")?.trim()
        val matches = courseList(repository).filter {
            it.dayOfWeek == fromDay && it.isActiveInWeek(fromWeek) &&
                (courseName.isNullOrBlank() || it.name == courseName)
        }
        require(matches.isNotEmpty()) {
            "在 $fromDate（周${weekdayName(fromDay)}，第 $fromWeek 周）没有找到" +
                (courseName?.let { "《$it》" } ?: "任何课程")
        }
        val selected = requireResolved(matches, args)

        val sectionStart = args.int("start_section")
        val sectionEnd = args.int("end_section")
        val summary = selected.joinToString("；") { course ->
            val start = sectionStart ?: course.startSection
            val end = sectionEnd ?: course.endSection
            "把${describe(course)}在 $fromDate 的那次（第 $fromWeek 周）移到 " +
                "$toDate 周${weekdayName(toDay)}第$start-$end 节"
        }

        return WritePlan(summary) {
            val list = courseList(repository).toMutableList()
            val now = Clock.System.now().toEpochMilliseconds()
            selected.forEach { course ->
                val index = list.indexOfFirst { it.id == course.id }
                if (index == -1) return@forEach
                val start = sectionStart ?: course.startSection
                val end = sectionEnd ?: course.endSection
                val remaining = activeWeeks(course).filter { it != fromWeek }

                if (remaining.isEmpty()) {
                    // 整门课只有这一周：直接改日期，不用拆分
                    list[index] = course.copy(
                        dayOfWeek = toDay,
                        startSection = start,
                        endSection = maxOf(start, end),
                        startWeek = toWeek,
                        endWeek = toWeek,
                        selectedWeeks = listOf(toWeek),
                        lastModified = now,
                    )
                } else {
                    list[index] = course.copy(
                        startWeek = remaining.min(),
                        endWeek = remaining.max(),
                        selectedWeeks = remaining,
                        lastModified = now,
                    )
                    list.add(
                        course.copy(
                            id = newCourseId(),
                            dayOfWeek = toDay,
                            startSection = start,
                            endSection = maxOf(start, end),
                            startWeek = toWeek,
                            endWeek = toWeek,
                            selectedWeeks = listOf(toWeek),
                            lastModified = now,
                        )
                    )
                }
            }
            repository.saveCourses(list)
            okJson(summary)
        }
    }

    private fun buildCancelOnDate(repository: CourseRepository, args: JsonObject): WritePlan {
        val date = requireDate(args.date("date"), "date")
        val week = repository.getLiveTeachingWeek(date)
        val totalWeeks = repository.getTotalWeeks()
        require(week in 1..totalWeeks) {
            "$date 不在本学期教学周范围内（当前学期 1-$totalWeeks 周）"
        }
        val day = date.dayOfWeek.isoDayNumber
        val courseName = args.string("course_name")?.trim()
        val matches = courseList(repository).filter {
            it.dayOfWeek == day && it.isActiveInWeek(week) &&
                (courseName.isNullOrBlank() || it.name == courseName)
        }
        require(matches.isNotEmpty()) {
            "$date（周${weekdayName(day)}，第 $week 周）" +
                (courseName?.let { "没有《$it》的课" } ?: "没有课程")
        }

        val summary = "停掉 $date（第 $week 周）的" +
            matches.joinToString("、") { "《${it.name.ifBlank { "特殊课程" }}》" } +
            "：从这些课程的周次里去掉第 $week 周"

        return WritePlan(summary) {
            val ids = matches.map { it.id }.toSet()
            val result = courseList(repository).mapNotNull { course ->
                if (course.id !in ids) return@mapNotNull course
                val remaining = activeWeeks(course).filter { it != week }
                if (remaining.isEmpty()) {
                    null // 最后一节课被停掉，整条删除
                } else {
                    course.copy(
                        startWeek = remaining.min(),
                        endWeek = remaining.max(),
                        selectedWeeks = remaining,
                        lastModified = Clock.System.now().toEpochMilliseconds(),
                    )
                }
            }
            repository.saveCourses(result)
            okJson(summary)
        }
    }

    // ── 写操作：假期与学期 ────────────────────────────────────────────────

    private fun buildAddHoliday(args: JsonObject): WritePlan {
        val start = requireDate(args.date("start_date"), "start_date")
        val end = args.date("end_date") ?: start
        require(end >= start) { "end_date 不能早于 start_date" }
        val name = args.string("name")?.trim().takeUnless { it.isNullOrBlank() } ?: "假期"

        val summary = if (end == start) "新增假期「$name」：$start" else "新增假期「$name」：$start 至 $end"

        return WritePlan(summary) {
            for (year in start.year..end.year) {
                val existing = HolidayManager.loadAllByYear()[year].orEmpty()
                HolidayManager.save(
                    year,
                    existing + HolidayEntry(
                        date = start.toString(),
                        endDate = if (end == start) "" else end.toString(),
                        name = name,
                        type = HolidayEntry.TYPE_HOLIDAY,
                        custom = true,
                    ),
                )
            }
            okJson(summary)
        }
    }

    private fun buildRemoveHoliday(args: JsonObject): WritePlan {
        val targetDate = args.date("date")
        val name = args.string("name")?.trim()
        require(targetDate != null || !name.isNullOrBlank()) { "需要 date 或 name" }

        val all = HolidayManager.loadAllByYear()
        val toRemove = all.mapValues { (_, entries) ->
            entries.filter { entry ->
                (targetDate != null && entry.matches(targetDate.toString())) ||
                    (!name.isNullOrBlank() && entry.name == name)
            }
        }.filterValues { it.isNotEmpty() }
        require(toRemove.isNotEmpty()) { "没有找到匹配的假期" }

        val removedNames = toRemove.values.flatten().map { "${it.name}(${it.date})" }
        val summary = "删除假期：${removedNames.joinToString("、")}"

        return WritePlan(summary) {
            toRemove.forEach { (year, removed) ->
                val remaining = all[year].orEmpty().filterNot { it in removed }
                HolidayManager.save(year, remaining)
            }
            okJson(summary)
        }
    }

    private fun buildSemesterInfo(repository: CourseRepository, args: JsonObject): WritePlan {
        val classStart = args.date("class_start")
        val totalWeeks = args.int("total_weeks")
        val currentWeek = args.int("current_week")
        require(classStart != null || totalWeeks != null || currentWeek != null) {
            "没有要修改的字段"
        }
        totalWeeks?.let { require(it in 1..CourseRepository.MAX_TOTAL_WEEKS) { "总周数超出范围" } }
        currentWeek?.let { require(it >= 1) { "当前周次不合法" } }

        val summary = buildList {
            classStart?.let { add("开学日期 → $it") }
            totalWeeks?.let { add("总周数 → $it") }
            currentWeek?.let { add("当前周次 → $it") }
        }.joinToString("，")

        return WritePlan("修改学期设置：$summary") {
            classStart?.let { repository.setClassStartTime(it.toString()) }
            totalWeeks?.let { repository.setTotalWeeks(it) }
            currentWeek?.let { repository.setCurrentWeek(it) }
            okJson("修改学期设置：$summary")
        }
    }

    // ── 只读实现 ──────────────────────────────────────────────────────────

    private fun coursesOnDateJson(repository: CourseRepository, date: LocalDate): String {
        val week = repository.getLiveTeachingWeek(date)
        val weekday = date.dayOfWeek.isoDayNumber
        val sectionTimes = sectionTimes(repository)
        val courses = courseList(repository)
            .filter { it.dayOfWeek == weekday && it.isActiveInWeek(week) }
            .sortedBy { it.startSection }

        return gson.toJson(JsonObject().apply {
            addProperty("date", date.toString())
            addProperty("weekday", weekday)
            addProperty("week", week)
            addProperty("schedule", repository.getCurrentScheduleId())
            add("courses", JsonArray().apply {
                courses.forEach { add(courseJson(it, week, sectionTimes)) }
            })
        })
    }

    private fun weekCoursesJson(repository: CourseRepository, date: LocalDate, requestedWeek: Int?): String {
        val week = (requestedWeek ?: repository.getLiveTeachingWeek(date))
            .coerceIn(1, CourseRepository.MAX_TOTAL_WEEKS)
        val sectionTimes = sectionTimes(repository)
        val courses = courseList(repository).filter { it.isActiveInWeek(week) }

        // 按星期分组，保持 1..7 顺序；没课的星期不输出，减少无意义返回
        val days = JsonArray()
        (1..7).forEach { day ->
            val dayCourses = courses.filter { it.dayOfWeek == day }.sortedBy { it.startSection }
            if (dayCourses.isEmpty()) return@forEach
            days.add(JsonObject().apply {
                addProperty("dayOfWeek", day)
                add("courses", JsonArray().apply {
                    dayCourses.forEach { add(courseJson(it, week, sectionTimes)) }
                })
            })
        }

        return gson.toJson(JsonObject().apply {
            addProperty("week", week)
            addProperty("schedule", repository.getCurrentScheduleId())
            add("days", days)
        })
    }

    private fun semesterInfoJson(repository: CourseRepository, date: LocalDate): String {
        val courses = runCatching { courseList(repository) }.getOrDefault(emptyList())
        return gson.toJson(JsonObject().apply {
            addProperty("schedule", repository.getCurrentScheduleId())
            addProperty("week", repository.getLiveTeachingWeek(date))
            addProperty("totalWeeks", repository.getTotalWeeks())
            addProperty("classStart", runCatching { repository.getClassStartTime() }.getOrNull())
            addProperty("today", date.toString())
            addProperty("weekday", date.dayOfWeek.isoDayNumber)
            addProperty("courseCount", courses.size)
        })
    }

    /** 从今天起最多找 7 天；今天的课要过滤掉已经开始的那几节。 */
    private fun nextClassJson(repository: CourseRepository): String {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val nowMinutes = now.hour * 60 + now.minute
        val sectionTimes = sectionTimes(repository)
        val courses = courseList(repository)

        for (offset in 0..7) {
            val date = now.date.plus(DatePeriod(days = offset))
            val week = repository.getLiveTeachingWeek(date)
            val weekday = date.dayOfWeek.isoDayNumber
            val candidates = courses
                .filter { it.dayOfWeek == weekday && it.isActiveInWeek(week) }
                .mapNotNull { course ->
                    val start = course.getEffectiveStartTime(sectionTimes)?.let(::hmToMinutes)
                        ?: return@mapNotNull null
                    course to start
                }
                .filter { (_, start) -> offset > 0 || start >= nowMinutes }
                .sortedBy { (_, start) -> start }

            val (course, _) = candidates.firstOrNull() ?: continue
            return gson.toJson(JsonObject().apply {
                addProperty("found", true)
                addProperty("date", date.toString())
                addProperty("weekday", weekday)
                addProperty("week", week)
                add("course", courseJson(course, week, sectionTimes))
            })
        }

        return gson.toJson(JsonObject().apply {
            addProperty("found", false)
            addProperty("message", "近期没有需要上的课")
        })
    }

    private fun searchCoursesJson(repository: CourseRepository, query: String): String {
        val sectionTimes = sectionTimes(repository)
        val today = todayLocalDate()
        val week = repository.getLiveTeachingWeek(today)
        val matches = courseList(repository).filter { course ->
            course.name.contains(query, ignoreCase = true) ||
                course.teacher.contains(query, ignoreCase = true) ||
                course.classroom.contains(query, ignoreCase = true) ||
                course.note.contains(query, ignoreCase = true) ||
                course.weekNotes.values.any { it.contains(query, ignoreCase = true) }
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))

        return gson.toJson(JsonObject().apply {
            addProperty("query", query)
            addProperty("schedule", repository.getCurrentScheduleId())
            addProperty("count", matches.size)
            add("courses", JsonArray().apply {
                matches.take(50).forEach { add(courseJson(it, week, sectionTimes)) }
            })
        })
    }

    // ── 通用工具 ──────────────────────────────────────────────────────────

    private fun resolveCourses(repository: CourseRepository, args: JsonObject): List<Course> {
        val id = args.string("course_id")
        if (!id.isNullOrBlank()) {
            val byId = courseList(repository).filter { it.id == id }
            require(byId.isNotEmpty()) { "找不到 id=$id 的课程" }
            return byId
        }
        val name = args.string("course_name")?.trim()
        require(!name.isNullOrBlank()) { "请提供 course_id 或 course_name" }
        val fromDay = args.int("from_day_of_week")
        var matches = courseList(repository).filter { it.name == name }
        if (fromDay != null) matches = matches.filter { it.dayOfWeek == fromDay }
        require(matches.isNotEmpty()) { "找不到课程「$name」" }
        return matches
    }

    /** 匹配到多门时必须显式 all=true，避免误改一整天同名的课。 */
    private fun requireResolved(matches: List<Course>, args: JsonObject): List<Course> {
        val all = args.bool("all") == true
        if (matches.size > 1 && !all) {
            throw IllegalArgumentException(
                "匹配到 ${matches.size} 门课程，请用 course_id 或 from_day_of_week 指定，或传 all=true：" +
                    matches.joinToString("；") { describe(it) }
            )
        }
        return matches
    }

    private fun describe(course: Course): String =
        "《${course.name.ifBlank { "特殊课程" }}》周${weekdayName(course.dayOfWeek)}" +
            "第${course.startSection}-${course.endSection}节"

    private fun weekdayName(day: Int): String =
        listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(day - 1) { "?" }

    private fun weeksText(weeks: List<Int>): String {
        if (weeks.isEmpty()) return "无"
        val sorted = weeks.distinct().sorted()
        return if (sorted.size > 1 && sorted.zipWithNext().all { (a, b) -> b == a + 1 }) {
            "${sorted.first()}-${sorted.last()} 周"
        } else if (sorted.size == 1) {
            "第 ${sorted.first()} 周"
        } else {
            "共 ${sorted.size} 周（${sorted.joinToString("、")}）"
        }
    }

    /** 该课实际生效的周次；与 [Course.isActiveInWeek] 同口径。 */
    private fun activeWeeks(course: Course): List<Int> =
        if (course.selectedWeeks.isNotEmpty()) {
            course.selectedWeeks
        } else {
            (course.startWeek..course.endWeek).filter { course.isActiveInWeek(it) }
        }

    private fun newCourseId(): String =
        "mcp_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}"

    private fun sectionTimes(repository: CourseRepository): Map<Int, String> =
        runCatching { repository.getCurrentSectionTimes() }.getOrDefault(emptyMap())

    private fun courseList(repository: CourseRepository): List<Course> =
        runCatching { repository.getCoursesForSchedule(repository.getCurrentScheduleId()) }
            .getOrDefault(emptyList())

    private fun courseJson(course: Course, week: Int, sectionTimes: Map<Int, String>): JsonObject =
        JsonObject().apply {
            addProperty("id", course.id)
            addProperty("name", course.name.ifBlank { "特殊课程" })
            addProperty("dayOfWeek", course.dayOfWeek)
            addProperty("startSection", course.startSection)
            addProperty("endSection", course.endSection)
            course.getEffectiveStartTime(sectionTimes)?.let { addProperty("startTime", it) }
            course.getEffectiveEndTime(sectionTimes)?.let { addProperty("endTime", it) }
            if (course.classroom.isNotBlank()) addProperty("location", course.classroom)
            if (course.teacher.isNotBlank()) addProperty("teacher", course.teacher)
            addProperty("weeks", course.getWeekText())
            course.noteForWeek(week).takeIf { it.isNotBlank() }?.let { addProperty("note", it) }
            if (course.hasValidCustomTime()) addProperty("customTime", true)
        }

    /** "HH:mm" → 当天分钟数；格式不对返回 null。 */
    private fun hmToMinutes(value: String): Int? {
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun requireDate(value: LocalDate?, label: String): LocalDate =
        value ?: throw IllegalArgumentException("$label 需为 YYYY-MM-DD")

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asInt

    private fun JsonObject.bool(key: String): Boolean? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean

    private fun JsonObject.date(key: String): LocalDate? =
        string(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun JsonObject.intList(key: String): List<Int>? {
        val element = get(key) ?: return null
        if (!element.isJsonArray) return null
        return element.asJsonArray.mapNotNull { item ->
            runCatching { item.asInt }.getOrNull()
        }
    }

    // ── HTTP ──────────────────────────────────────────────────────────────

    /** 读到首个空行为止；返回的文本包含请求行与全部头。 */
    private fun readHeaderBlock(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val byte = input.read()
            if (byte == -1) return null
            buffer.write(byte)
            matched = when {
                matched == 4 -> 4
                (matched == 0 || matched == 2) && byte == '\r'.code -> matched + 1
                (matched == 1 || matched == 3) && byte == '\n'.code -> matched + 1
                byte == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) break
            if (buffer.size() > 64 * 1024) return null
        }
        return buffer.toString("UTF-8")
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count <= 0) break
            read += count
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun write(socket: Socket, status: String, body: String) {
        val payload = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            if (payload.isNotEmpty()) write(payload)
            flush()
        }
    }
}
