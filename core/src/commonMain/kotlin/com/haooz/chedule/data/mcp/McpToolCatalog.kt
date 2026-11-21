package com.haooz.chedule.data.mcp

/** 单个 MCP 工具的名称与说明（服务端拿去当工具描述，页面拿去展示）。 */
data class McpToolSpec(
    val name: String,
    val descriptionZh: String,
    val descriptionEn: String,
)

/**
 * 本地 MCP 服务的对外契约：端口、地址与工具清单。
 *
 * 服务端与设置页共用同一份，避免两边描述漂移。
 * 写操作统一「先预览、再确认」：首次调用只返回摘要，带 `confirm=true` 与
 * `pending_id` 再调一次才落库；可用 `undo_last_change` 回滚。
 */
object McpToolCatalog {

    const val PORT = 26611

    const val SERVER_URL = "http://127.0.0.1:$PORT/mcp"

    val tools: List<McpToolSpec> = listOf(
        McpToolSpec(
            name = "get_courses_on_date",
            descriptionZh = "查某天课程，不传 date 为今天。",
            descriptionEn = "List courses on a date; today when omitted.",
        ),
        McpToolSpec(
            name = "get_week_courses",
            descriptionZh = "查某教学周课表，不传 week 为当前周。",
            descriptionEn = "List a teaching week; current week when omitted.",
        ),
        McpToolSpec(
            name = "get_semester_info",
            descriptionZh = "学期信息：当前周、总周数、开学日期。",
            descriptionEn = "Semester info: week, total weeks, start date.",
        ),
        McpToolSpec(
            name = "get_next_class",
            descriptionZh = "下一节要上的课。",
            descriptionEn = "The next upcoming class.",
        ),
        McpToolSpec(
            name = "search_courses",
            descriptionZh = "按课程名、教师、地点或备注搜索。",
            descriptionEn = "Search courses by name, teacher, location or note.",
        ),
        McpToolSpec(
            name = "add_course",
            descriptionZh = "新增课程；先预览后确认。",
            descriptionEn = "Add a course; preview then confirm.",
        ),
        McpToolSpec(
            name = "update_course",
            descriptionZh = "修改课程，含星期、节次与周次；先预览后确认。",
            descriptionEn = "Edit a course, including weekday, sections and weeks.",
        ),
        McpToolSpec(
            name = "delete_course",
            descriptionZh = "删除课程；先预览后确认。",
            descriptionEn = "Delete a course; preview then confirm.",
        ),
        McpToolSpec(
            name = "move_course_occurrence",
            descriptionZh = "只把某一天的课移到另一天；先预览后确认。",
            descriptionEn = "Move one occurrence to another date; preview then confirm.",
        ),
        McpToolSpec(
            name = "cancel_class_on_date",
            descriptionZh = "停掉某天的课；先预览后确认。",
            descriptionEn = "Cancel classes on a date; preview then confirm.",
        ),
        McpToolSpec(
            name = "add_holiday",
            descriptionZh = "新增放假区间；先预览后确认。",
            descriptionEn = "Add a holiday range; preview then confirm.",
        ),
        McpToolSpec(
            name = "remove_holiday",
            descriptionZh = "删除假期；先预览后确认。",
            descriptionEn = "Remove a holiday; preview then confirm.",
        ),
        McpToolSpec(
            name = "set_semester_info",
            descriptionZh = "改开学日期、总周数或当前周。",
            descriptionEn = "Update start date, total weeks or current week.",
        ),
        McpToolSpec(
            name = "undo_last_change",
            descriptionZh = "撤销上一次修改。",
            descriptionEn = "Undo the last change.",
        ),
    )
}
