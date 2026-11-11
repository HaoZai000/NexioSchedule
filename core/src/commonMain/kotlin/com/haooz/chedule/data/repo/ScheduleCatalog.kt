package com.haooz.chedule.data.repo

import com.haooz.chedule.data.backup.decodeCourses
import com.haooz.chedule.data.holiday.HolidayManager
import com.haooz.chedule.data.backup.decodeScheduleFolders
import com.haooz.chedule.data.backup.decodeStringList
import com.haooz.chedule.data.backup.encodeCourses
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CLASS_START_TIME
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_COURSES
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CURRENT_SCHEDULE_ID
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_CURRENT_WEEK
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SCHEDULE_FOLDERS
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_SCHEDULE_NAMES
import com.haooz.chedule.data.repo.CourseRepository.Companion.KEY_TEACHING_WEEK_REORGANIZATIONS
import com.haooz.chedule.data.repo.CourseRepository.Companion.SCHEDULE_KEY_PREFIX
import com.haooz.chedule.data.repo.CourseRepository.Companion.SCHEDULE_TIME_CONFIG_PREFIX
import com.haooz.chedule.data.storage.CourseRowStore
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.storage.ScheduleFolder
import com.haooz.chedule.data.storage.StoredFolder
import com.haooz.chedule.data.storage.getStringOrNull
import com.haooz.chedule.data.util.formatSlashDate
import com.haooz.chedule.data.util.todayLocalDate
import kotlin.concurrent.Volatile
import kotlinx.datetime.Clock

/**
 * 课表目录：有哪些课表、当前看哪张、文件夹怎么分组。
 *
 * 只碰「目录」这一层：课表的存在与名字（`schedule_names` + `schedule.id`）、当前选中项、
 * 文件夹。课程内容、作息内容都不在这里 —— 新建课表只是建一行、配一套作息，
 * 不写任何课程。
 *
 * 改名与删除是这里唯一危险的操作，顺序固定为**先动行表、再动键值**：
 * 行表那步失败时键值还停在旧状态，重试仍然是完整的一次操作；反过来会留下
 * 「新名字下既没有行也没有课程」的空壳。
 */
internal class ScheduleCatalog(private val host: Host) {

    internal interface Host {
        val prefs: KeyValueStore

        fun rowStore(): CourseRowStore?

        /** 整库缓存作废（删除 / 改名后键值被整体改写，任何按名缓存都不再可信）。 */
        fun invalidateAllCaches()

        /** 当前课表变了：占用周次与全局节次时间都要重算。 */
        fun invalidateTimeCaches()

        fun notifySettingsChanged()

        fun boundTimeConfigIdOrNull(scheduleId: String): Long?

        fun addTimeConfig(config: TimeConfig): Long

        fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long)

        fun getTimeConfig(id: Long): TimeConfig

        fun getScheduleTimeConfigId(scheduleId: String): Long

        /** 改名时要逐条搬运旧 blob，坏数据不能顺手清洗 —— 那会把原数据盖成空壳。 */
        fun coursesJsonLooksValid(json: String): Boolean

        fun sanitizeCourses(courses: List<Course>): List<Course>
    }

    @Volatile
    private var namesCache: List<String>? = null

    @Volatile
    private var currentIdCache: String? = null

    @Volatile
    private var foldersCache: List<ScheduleFolder>? = null

    /** 只清自己的缓存。全库失效由仓库统一发起（见 `invalidateAllCaches`）。 */
    fun invalidateCaches() {
        namesCache = null
        currentIdCache = null
        foldersCache = null
    }

    // ── 课表列表与当前课表 ────────────────────────────────────────────────────

    fun getScheduleNames(): List<String> {
        namesCache?.let { return it }
        val json = host.prefs.getStringOrNull(KEY_SCHEDULE_NAMES)
        val names = try {
            if (json.isNullOrBlank()) {
                listOf(DEFAULT_SCHEDULE_NAME)
            } else {
                runCatching { decodeStringList(json) }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(DEFAULT_SCHEDULE_NAME)
            }
        } catch (_: Exception) {
            listOf(DEFAULT_SCHEDULE_NAME)
        }
        namesCache = names
        return names
    }

    fun saveScheduleNames(names: List<String>) {
        host.prefs.edit { putString(KEY_SCHEDULE_NAMES, encodeStringList(names)) }
        // 列表变了，「当前 id 是否还在列表里」的结论就失效了
        namesCache = names
        currentIdCache = null
    }

    fun getCurrentScheduleId(): String {
        currentIdCache?.let { return it }
        val saved = host.prefs.getString(KEY_CURRENT_SCHEDULE_ID, DEFAULT_SCHEDULE_NAME)
        val names = getScheduleNames()
        // 存的名字已经不在列表里（被删 / 改名）就退回第一个，不能让界面拿着空课表
        val resolved = if (saved in names) saved else names.first()
        currentIdCache = resolved
        return resolved
    }

    fun setCurrentScheduleId(scheduleId: String) {
        host.prefs.edit { putString(KEY_CURRENT_SCHEDULE_ID, scheduleId) }
        currentIdCache = scheduleId
        // 占用周次与全局节次时间都按当前课表算
        host.invalidateTimeCaches()
        host.notifySettingsChanged()
        HolidayManager.notifyScheduleSwitched()
    }

    fun addSchedule(name: String): List<String> {
        val names = getScheduleNames().toMutableList()
        if (name !in names) {
            names.add(name)
            saveScheduleNames(names)
            // 必须配一套专属作息：共用第一个配置的话，改这张表的节数会波及别的课表
            createDefaultTimeConfigForSchedule(name)
        }
        host.notifySettingsChanged()
        return names
    }

    /** 给课表配一套 4/4/4 的专属作息并绑定。已经绑过就什么都不做。 */
    fun createDefaultTimeConfigForSchedule(name: String) {
        // 不能用 getScheduleTimeConfigId 判断：它对未绑定会回退到第一个配置，永远非空
        if (host.boundTimeConfigIdOrNull(name) != null) return
        val newId = host.addTimeConfig(
            TimeConfig(
                name = name,
                morningSections = 4,
                afternoonSections = 4,
                eveningSections = 4,
            ),
        )
        host.setScheduleTimeConfigId(name, newId)
    }

    /** 复制当前课表的设置（不含课程）：开课日重置为今天、当前周回到第 1 周。 */
    fun createNewSemesterSchedule(name: String): List<String> {
        val currentId = getCurrentScheduleId()
        val names = getScheduleNames().toMutableList()
        if (name !in names) {
            // 追加到末尾，保持「添加时间」顺序，不因新建/选中而重排
            names.add(name)
            saveScheduleNames(names)
        }
        val currentPrefix = "$SCHEDULE_KEY_PREFIX${currentId}_"
        val newPrefix = "$SCHEDULE_KEY_PREFIX${name}_"
        // 先在外面复制作息：addTimeConfig 自己会提交一次 prefs，嵌套进 edit 块会打乱写入顺序
        val duplicatedConfigId = host.addTimeConfig(
            host.getTimeConfig(host.getScheduleTimeConfigId(currentId)).copy(id = 0L, name = name),
        )
        host.prefs.edit {
            for ((key, value) in host.prefs.all()) {
                if (!key.startsWith(currentPrefix)) continue
                val settingName = key.removePrefix(currentPrefix)
                // 课程与教学周规则不跟过来：新学期是空表
                if (settingName == KEY_COURSES || settingName == KEY_TEACHING_WEEK_REORGANIZATIONS) {
                    continue
                }
                val newKey = "$newPrefix$settingName"
                when (value) {
                    is Int -> putInt(newKey, value)
                    is Boolean -> putBoolean(newKey, value)
                    is String -> putString(newKey, value)
                    is Float -> putFloat(newKey, value)
                    is Long -> putLong(newKey, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(newKey, value as Set<String>)
                    }
                }
            }
            putString("$newPrefix$KEY_CLASS_START_TIME", todayLocalDate().formatSlashDate())
            putInt("$newPrefix$KEY_CURRENT_WEEK", 1)
            remove("$newPrefix$KEY_TEACHING_WEEK_REORGANIZATIONS")
        }
        // 绑定必须落在 edit 块外面：它写的是另一张表（另一个事务）
        host.setScheduleTimeConfigId(name, duplicatedConfigId)
        // 设置的真相是 schedule 行
        host.rowStore()?.let { rows ->
            rows.settingsOf(currentId)?.let { src ->
                rows.writeSettings(name, currentWeek = null, totalWeeks = src.totalWeeks, classStart = null)
            }
            rows.displaySettingsOf(currentId)?.let { srcDisplay ->
                rows.writeSmartWeekend(name, srcDisplay.smartWeekend)
                rows.writeShowNonCurrentWeek(name, srcDisplay.showNonCurrentWeek)
            }
        }
        // 行是绕过仓库直写的，scheduleSettingsCache 里可能还存着建行那一刻的旧值 —— 全库作废一次
        host.invalidateAllCaches()
        HolidayManager.onScheduleCopied(currentId, name)
        return names
    }

    // ── 删除与改名 ────────────────────────────────────────────────────────────

    fun deleteSchedule(name: String): List<String> {
        // 行表先删：失败时键值一个都没动，调用方看到异常即为「没删成」。
        // 吞掉的后果是「界面说删了，课还在库里」—— 同名课表一建就自动复活。
        val rows = host.rowStore() ?: error("删除课表失败：课程行表没有接线 schedule=$name")
        runCatching { rows.deleteSchedule(name) }
            .onFailure { NexioLog.e(TAG, "删除课程行表失败：schedule=$name", it) }
            .getOrThrow()

        val names = getScheduleNames().toMutableList()
        names.remove(name)
        // 删光后补回默认课表，否则应用启动时没有可显示的课表
        if (names.isEmpty()) names.add(DEFAULT_SCHEDULE_NAME)
        saveScheduleNames(names)
        // 文件夹里的引用要一起清，否则留下幽灵条目（备份也会把它带走）
        rewriteScheduleNameInFolders(name, newName = null)
        val prefix = "$SCHEDULE_KEY_PREFIX${name}_"
        host.prefs.edit {
            for (key in host.prefs.all().keys) {
                if (key.startsWith(prefix)) remove(key)
            }
            // 绑定键不匹配 schedule_{名}_ 前缀，得单独删镜像；权威绑定已随行表级联删掉
            remove("$SCHEDULE_TIME_CONFIG_PREFIX$name")
        }
        // 刚绕过 setter 直接改了 prefs
        host.invalidateAllCaches()
        HolidayManager.onScheduleDeleted(name)
        if (getCurrentScheduleId() == name) setCurrentScheduleId(names.first())
        else HolidayManager.notifyScheduleSwitched()
        host.notifySettingsChanged()
        return names
    }

    fun renameSchedule(oldName: String, newName: String): List<String> {
        // 同名短路：下面搬键的循环是「put 新键 → remove 旧键」，同名时等于写完自己删掉，
        // 一次同名改名就能清空这张课表在 prefs 里的全部键。
        if (oldName == newName) return getScheduleNames()
        val names = getScheduleNames().toMutableList()
        val index = names.indexOf(oldName)
        if (index != -1) {
            // 行表以课表名为主键，先搬行（失败即抛）。反过来的顺序会留下空壳。
            val rows = host.rowStore() ?: error("改名失败：课程行表没有接线 $oldName → $newName")
            runCatching { rows.renameSchedule(oldName, newName) }
                .onFailure { NexioLog.e(TAG, "改名搬行失败：$oldName → $newName", it) }
                .getOrThrow()

            // 当前课表指针先跟上，再改名字列表（saveScheduleNames 会清掉当前 id 缓存）
            if (host.prefs.getString(KEY_CURRENT_SCHEDULE_ID, DEFAULT_SCHEDULE_NAME) == oldName) {
                setCurrentScheduleId(newName)
            }
            names[index] = newName
            saveScheduleNames(names)
            // 文件夹里的旧名跟着改，否则新名字会跑到根目录
            rewriteScheduleNameInFolders(oldName, newName)
            migratePrefixedKeys(oldName, newName)
            // 镜像绑定键不匹配上面前缀，单独搬；权威绑定由上面那次 renameSchedule 连行带走
            val oldBoundKey = "$SCHEDULE_TIME_CONFIG_PREFIX$oldName"
            if (host.prefs.contains(oldBoundKey)) {
                val boundId = host.prefs.getLong(oldBoundKey, 0L)
                host.prefs.edit {
                    putLong("$SCHEDULE_TIME_CONFIG_PREFIX$newName", boundId)
                    remove(oldBoundKey)
                }
            }
            host.invalidateAllCaches()
            HolidayManager.onScheduleRenamed(oldName, newName)
            HolidayManager.notifyScheduleSwitched()
        }
        host.notifySettingsChanged()
        return names
    }

    /** 把 `schedule_{旧名}_*` 整批搬到新名字下。课表 blob 里的 scheduleId 也要一起改。 */
    private fun migratePrefixedKeys(oldName: String, newName: String) {
        val oldPrefix = "$SCHEDULE_KEY_PREFIX${oldName}_"
        val newPrefix = "$SCHEDULE_KEY_PREFIX${newName}_"
        host.prefs.edit {
            for ((key, value) in host.prefs.all()) {
                if (!key.startsWith(oldPrefix)) continue
                val suffix = key.removePrefix(oldPrefix)
                val newKey = "$newPrefix$suffix"
                when (value) {
                    is Int -> putInt(newKey, value)
                    is Boolean -> putBoolean(newKey, value)
                    is String -> {
                        putString(newKey, movedStringValue(suffix, value, newName))
                    }

                    is Float -> putFloat(newKey, value)
                    is Long -> putLong(newKey, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(newKey, value as Set<String>)
                    }
                }
                remove(key)
            }
        }
    }

    private fun movedStringValue(suffix: String, value: String, newName: String): String {
        if (suffix != KEY_COURSES) return value
        // 坏 JSON 原样搬：清洗后写回会变成空壳课，把原数据永久盖掉
        if (!host.coursesJsonLooksValid(value)) return value
        return try {
            // 旧 JSON 字段可能为 null，直接 copy() 会 NPE
            val courses = host.sanitizeCourses(decodeCourses(value))
            encodeCourses(courses.map { it.copy(scheduleId = newName) })
        } catch (_: Exception) {
            value
        }
    }

    /**
     * 从文件夹原始数据里去掉（或改名）某个课表引用。
     *
     * 只读原始 JSON，不用 [getScheduleFolders]：那份结果已经剔除了刚删的名字，
     * 条件恒为 false，幽灵引用会留在库里并被备份带走。
     */
    private fun rewriteScheduleNameInFolders(oldName: String, newName: String?) {
        val json = host.prefs.getStringOrNull(KEY_SCHEDULE_FOLDERS) ?: return
        val folders = try {
            decodeScheduleFolders(json)
        } catch (_: Exception) {
            return
        }
        if (folders.none { oldName in it.schedules }) return
        saveScheduleFolders(
            folders.map { folder ->
                if (oldName !in folder.schedules) folder
                else folder.copy(
                    schedules = folder.schedules
                        .map { if (it == oldName) newName else it }
                        .filterNotNull(),
                )
            },
        )
    }

    // ── 文件夹 ────────────────────────────────────────────────────────────────

    /**
     * 读文件夹，顺带清洗：剔掉已删除的课表名、保证一个课表只在一个文件夹里（留靠前的），
     * 并合并重复 id（重复 id 会让 LazyColumn key 冲突直接闪退）。
     */
    fun getScheduleFolders(): List<ScheduleFolder> {
        foldersCache?.let { return it }
        val allNames = getScheduleNames()
        // 只认表：迁移会把 prefs 里那份 JSON 删掉
        val stored = host.rowStore()?.let { rows -> runCatching { rows.folders() }.getOrNull() }
            .orEmpty()
        val seen = mutableSetOf<String>()
        val byId = linkedMapOf<String, ScheduleFolder>()
        stored.filter { it.id.isNotBlank() }.forEach { row ->
            val folder = ScheduleFolder(id = row.id, name = row.name, schedules = row.schedules)
            val kept = folder.schedules.filter { name -> name in allNames && seen.add(name) }
            // 文件夹内部按全局课表顺序展示
            val ordered = allNames.filter { it in kept }
            val existing = byId[folder.id]
            if (existing == null) {
                byId[folder.id] = folder.copy(schedules = ordered)
            } else {
                val merged = (existing.schedules + ordered).distinct()
                byId[folder.id] = existing.copy(schedules = allNames.filter { it in merged })
            }
        }
        val result = byId.values.toList()
        foldersCache = result
        return result
    }

    fun saveScheduleFolders(folders: List<ScheduleFolder>) {
        val rows = host.rowStore()
        if (rows == null) {
            NexioLog.e(TAG, "文件夹写失败：课程行表没有接线")
        } else {
            runCatching {
                folders.forEachIndexed { index, folder ->
                    rows.upsertFolder(
                        StoredFolder(
                            id = folder.id,
                            name = folder.name,
                            schedules = folder.schedules,
                            sortOrder = index,
                        ),
                    )
                }
                // 表里多出来的行（用户删掉的文件夹）一起清掉
                rows.folders().map { it.id }.filter { it !in folders.map { f -> f.id } }
                    .forEach { rows.deleteFolder(it) }
            }.onFailure { NexioLog.e(TAG, "文件夹写表失败", it) }
        }
        foldersCache = folders
    }

    fun addScheduleFolder(name: String): List<ScheduleFolder> {
        val folders = getScheduleFolders().toMutableList()
        folders.add(
            ScheduleFolder(
                id = "folder_${Clock.System.now().toEpochMilliseconds()}_${folders.size}",
                name = name,
            ),
        )
        saveScheduleFolders(folders)
        return folders
    }

    fun renameScheduleFolder(id: String, newName: String): List<ScheduleFolder> {
        val folders = getScheduleFolders().toMutableList()
        val index = folders.indexOfFirst { it.id == id }
        if (index != -1) {
            folders[index] = folders[index].copy(name = newName)
            saveScheduleFolders(folders)
        }
        return folders
    }

    /** 解散文件夹：里面的课表回到根目录，课表本身不删。 */
    fun disbandScheduleFolder(id: String): List<ScheduleFolder> {
        val folders = getScheduleFolders()
        if (folders.none { it.id == id }) return folders
        saveScheduleFolders(folders.filter { it.id != id })
        return getScheduleFolders()
    }

    /** 把课表移进文件夹；`folderId` 为 null 表示移回根目录。 */
    fun moveSchedulesToFolder(
        scheduleNames: List<String>,
        folderId: String?,
    ): List<ScheduleFolder> {
        if (scheduleNames.isEmpty()) return getScheduleFolders()
        val moving = scheduleNames.distinct()
        // 先从所有文件夹摘出来，避免同一课表挂在两个文件夹下
        val folders = getScheduleFolders()
            .map { it.copy(schedules = it.schedules.filter { name -> name !in moving }) }
            .toMutableList()
        if (folderId != null) {
            val index = folders.indexOfFirst { it.id == folderId }
            if (index != -1) {
                val merged = (folders[index].schedules + moving).distinct()
                folders[index] = folders[index].copy(
                    schedules = getScheduleNames().filter { it in merged },
                )
            }
        }
        saveScheduleFolders(folders)
        return folders
    }

    /** 课表所属文件夹 id；不在任何文件夹时返回 null。 */
    fun getFolderIdOfSchedule(scheduleName: String): String? {
        for (folder in getScheduleFolders()) {
            if (scheduleName in folder.schedules) return folder.id
        }
        return null
    }

    /** 未归入任何文件夹的课表（切换页的根目录）。 */
    fun getRootScheduleNames(): List<String> {
        val folders = getScheduleFolders()
        if (folders.isEmpty()) return getScheduleNames()
        return getScheduleNames().filter { name -> folders.none { name in it.schedules } }
    }

    private companion object {
        const val TAG = "ScheduleCatalog"
        const val DEFAULT_SCHEDULE_NAME = "默认课表"
    }
}
