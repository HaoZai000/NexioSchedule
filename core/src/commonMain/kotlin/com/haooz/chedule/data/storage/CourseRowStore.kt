package com.haooz.chedule.data.storage

import com.haooz.chedule.data.model.Course
import kotlin.concurrent.Volatile

/**
 * 写行表前必须先就位的两行外键目标：`time_config(id)` 与 `schedule(id)`。
 *
 * 三个设置字段（当前周 / 总周数 / 开课日）跟着锚点一起落进行 —— 行建出来之后，
 * 读路径只认这几列，prefs 里已经没有副本了。
 */
data class CourseRowAnchor(
    val scheduleId: String,
    val timeConfigId: Long,
    val timeConfigName: String,
    val timeConfigPayload: String,
    val sortOrder: Int,
    val currentWeek: Int,
    val totalWeeks: Int,
    val classStart: String?,
)

/**
 * 一次性迁移（`CourseRepository.migrateLegacyEntitiesOnce`）的结果。
 *
 * 调用方据此决定要不要打日志 / 提示用户 —— 迁移失败是**静默的**（懒路径会兜住界面），
 * 没有这个返回值就只能靠翻 logcat。
 */
sealed interface MigrationOutcome {
    /** 没有行表。三端都装了行表，走到这里说明接线漏了。 */
    data object NoRowStore : MigrationOutcome

    /** 之前已经迁完，这次是空操作。 */
    data object AlreadyDone : MigrationOutcome

    /** 全部课表都进了实体表，全局标记已写。 */
    data class Done(val scheduleCount: Int) : MigrationOutcome

    /** 还有课表没进去，标记没写，下次启动重试。 */
    data class Incomplete(val failed: List<String>) : MigrationOutcome
}

/**
 * 课程行表访问口。实现缺失时（[CourseStorage.rows] 为 null）调用方退回 JSON。
 */
interface CourseRowStore {

    /** 该课表是否已从 JSON 导入。true 时行表是真相。 */
    fun isImported(scheduleId: String): Boolean

    /** 读一个课表的全部课程（含周次 / 周备注）。 */
    fun load(scheduleId: String): List<Course>

    /** 首次导入：整表写入 + 同一事务内回读对拍 + 写导入标记。对拍不一致抛异常并整体回滚。 */
    fun importAll(anchor: CourseRowAnchor, courses: List<Course>)

    /** 行级差量写：只写 [previous] 与 [next] 之间变了的课。 */
    fun writeDiff(anchor: CourseRowAnchor, previous: List<Course>, next: List<Course>)

    /**
     * 课表改名：把行数据搬到新名字下。不能删了等重新导入 ——
     * 导入之后 JSON blob 不再更新，重建只会读到导入那一刻的课表。
     */
    fun renameSchedule(oldId: String, newId: String)

    /** 删整张课表的数据（含锚点行，课程由外键级联删除）。 */
    fun deleteSchedule(scheduleId: String)

    // ── 作息绑定 ──────────────────────────────────────────────────────────────
    // 绑定是 `schedule.time_config_id`（NOT NULL 外键）这一列，不另存键。
    // 键名里带课表身份的话，改名要搬键、删表要删键、孤儿只能靠键名猜；作为列它跟着行走。

    /** 这张课表绑的作息配置 id。没有对应的行（未写过 / 库不可用）返回 null。 */
    fun bindingOf(scheduleId: String): Long?

    /** 写绑定，顺带补齐两行外键目标。幂等。 */
    fun setBinding(anchor: CourseRowAnchor)

    /** 全部课表的绑定。孤儿配置清理对它求差集。 */
    fun bindings(): Map<String, Long>

    // ── 课表设置 ──────────────────────────────────────────────────────────────
    // current_week / total_weeks / class_start 是 `schedule` 行上的列，prefs 键是写穿镜像
    // （备份导出遍历 prefs，没有行表的平台也靠它）。

    /** 三个设置值。行不存在返回 null。 */
    fun settingsOf(scheduleId: String): ScheduleRowSettings?

    /** 写三个设置值；传 null 的字段保持库里的值。行不存在时静默无效，调用方需先补建行。 */
    fun writeSettings(scheduleId: String, currentWeek: Int?, totalWeeks: Int?, classStart: String?)

    // ── 显示开关 ──────────────────────────────────────────────────────────────
    // 跟着课表走，以前是 prefs 的 schedule_{名}_smart_weekend / _show_non_current_week。

    /** 两个显示开关。行不存在返回 null，调用方回落 prefs 的默认值。 */
    fun displaySettingsOf(scheduleId: String): ScheduleDisplaySettings?

    fun writeSmartWeekend(scheduleId: String, smart: Boolean)

    fun writeShowNonCurrentWeek(scheduleId: String, show: Boolean)

    // ── 教学周重组规则 ────────────────────────────────────────────────────────
    // payload 是整份规则列表的 JSON（TeachingWeekReorganization.encode 的产物）。

    /** 整份规则 JSON。没有行返回 null。 */
    fun teachingWeekReorganizationsOf(scheduleId: String): String?

    fun writeTeachingWeekReorganizations(scheduleId: String, payload: String)

    // ── 文件夹 ────────────────────────────────────────────────────────────────

    fun folders(): List<StoredFolder>

    /** 写一整行：先 UPDATE，不存在再 INSERT OR IGNORE（父表被 schedule 引用，不能用 REPLACE）。 */
    fun upsertFolder(row: StoredFolder)

    fun deleteFolder(id: String)

    // ── 作息配置 ──────────────────────────────────────────────────────────────
    // payload 是完整 TimeConfig JSON，一行一份；prefs 的 time_config_{id} 是写穿镜像。

    fun timeConfigs(): List<StoredTimeConfig>

    /** 写一整行：先 UPDATE，不存在再 INSERT OR IGNORE（父表不能用 REPLACE，见 .sq 注释）。 */
    fun upsertTimeConfig(row: StoredTimeConfig)

    fun deleteTimeConfigRow(id: Long)

    /** 全部作废（整库恢复备份后）。下次读取从恢复出的 JSON 重新导入。 */
    fun invalidateAll()

    // ── 一次性迁移 ────────────────────────────────────────────────────────────
    // 标记里存迁移块版本。没到版本就重跑，跑完 prefs 副本立刻删掉 ——
    // 表是唯一真相，读路径不再回落 prefs。
    //
    // ⚠ 用布尔的话，1.6.7 已经迁完的用户升级到 1.6.8 会直接判「已迁完」，新块永远不执行，
    //   而读路径只认表 —— 表现就是「升级后设置被重置、文件夹消失」。

    /** 迁移是否已经跑到当前块版本。恢复备份会清掉它（恢复出的是另一套数据）。 */
    fun isFullyMigrated(): Boolean

    fun markFullyMigrated()
}

/**
 * [CourseRowStore] 的注册点，启动时装一次。
 *
 * 未注册或提供方返回 null 时没有行存储；提供方抛出的异常向调用方传播。
 */
object CourseStorage {

    @Volatile
    private var provider: (() -> CourseRowStore?)? = null

    @Volatile
    private var resolved: CourseRowStore? = null

    /** 由平台侧在启动时调用。重复调用等价于重置。 */
    fun install(provider: () -> CourseRowStore?) {
        this.provider = provider
        resolved = null
    }

    val rows: CourseRowStore?
        get() {
            resolved?.let { return it }
            val p = provider ?: return null
            return p().also { resolved = it }
        }
}

/** 差分结果：删了哪些、更新了哪些。 */
internal data class CourseRowDiff(
    val deleted: List<String>,
    val updated: List<Course>,
)

/** `time_config` 表的一行。payload 是完整 TimeConfig JSON（含 id 与 name）。 */
data class StoredTimeConfig(val id: Long, val name: String, val payload: String)

/** `schedule` 行上的三个设置值。 */
data class ScheduleRowSettings(
    val currentWeek: Int,
    val totalWeeks: Int,
    val classStart: String?,
)

/** `schedule` 行上的两个显示开关。 */
data class ScheduleDisplaySettings(
    val smartWeekend: Boolean,
    val showNonCurrentWeek: Boolean,
)

/** `folder` 表的一行。[schedules] 是组内课表名，顺序即用户排的顺序。 */
data class StoredFolder(
    val id: String,
    val name: String,
    val schedules: List<String>,
    val sortOrder: Int,
)

/**
 * 按 id 比对出变化的课。整对象判等，不逐字段手写 ——
 * 手写比对表在加字段时会漏，表现为「改了没写盘」。
 */
internal fun diffCourses(previous: List<Course>, next: List<Course>): CourseRowDiff {
    val previousById = previous.associateBy { it.id }
    val nextIds = next.mapTo(HashSet(next.size)) { it.id }
    return CourseRowDiff(
        deleted = previousById.keys.filter { it !in nextIds },
        updated = next.filter { previousById[it.id] != it },
    )
}

/**
 * 行表读回结果与内存对象是否等价，供导入自校验。
 * 只放行两处归一化：`selectedWeeks` 比排序后结果、`weekNotes` 是 Map（与顺序无关）。
 */
internal fun sameCourseContent(expected: List<Course>, actual: List<Course>): Boolean {
    if (expected.size != actual.size) return false
    val byId = actual.associateBy { it.id }
    if (byId.size != actual.size) return false // 行表里出现重复 id
    for (course in expected) {
        val other = byId[course.id] ?: return false
        if (course.copy(selectedWeeks = course.selectedWeeks.distinct().sorted()) !=
            other.copy(selectedWeeks = other.selectedWeeks.distinct().sorted())
        ) {
            return false
        }
    }
    return true
}

/**
 * 课表文件夹：把多个课表归为一组，便于切换页分类。
 *
 * 一个课表最多属于一个文件夹（其余课表位于根目录「全部课表」下）。
 * 文件夹只保存课表名引用，不改动 getScheduleNames() 的全局顺序。
 */
data class ScheduleFolder(
    val id: String,
    val name: String,
    val schedules: List<String> = emptyList()
)
