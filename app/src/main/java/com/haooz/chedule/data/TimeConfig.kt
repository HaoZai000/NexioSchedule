package com.haooz.chedule.data

/**
 * 特殊课程内部按星期划分的子块（如周一~周二"画黑板报"、周三"检查卫生"）。
 *
 * 一个特殊课程时间段（[SpecialBlock]）内部可容纳多个子块，各子块的星期区间互不重叠。
 */
data class SpecialItem(
    val id: Long = 0L,
    val name: String = "",       // 如"画黑板报""检查卫生"
    val startDay: Int = 1,       // 起始星期 1..7
    val endDay: Int = 1          // 结束星期 1..7，取值 >= startDay
) {
    companion object {
        /**
         * 把 Gson 可能留下的"原始形态"还原成 [SpecialItem]。
         *
         * 背景：R8 在未 keep 这些类时会剥掉字段的泛型签名，Gson 于是把
         * `List<SpecialItem>` 的元素按 Object 解析成 LinkedTreeMap，之后任何
         * `as SpecialItem` 强转都会抛 ClassCastException —— v1.5.0 正式版的线上崩溃
         * 正是这条：SpecialBandBody 里 `item.startDay` 抛 `nq1 cannot be cast to n63`。
         * 这里遇到 Map 就按字段名手工还原，既不崩、也不丢用户已录入的子块。
         *
         * 已是 [SpecialItem] 实例时同样重建：Gson 用 UnsafeAllocator 绕过构造器，
         * 旧 JSON 缺失 `name` 会得到 null 字段，UI 侧 Text/非空参数会崩。
         */
        // USELESS_ELVIS：以下 ?: 编译期看似走左值，但 Gson 反序列化后字段可能是 null
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
        internal fun fromRaw(raw: Any?): SpecialItem? = when (raw) {
            is SpecialItem -> SpecialItem(
                id = raw.id,
                name = raw.name ?: "",
                startDay = if (raw.startDay in 1..7) raw.startDay else 1,
                endDay = if (raw.endDay in 1..7) raw.endDay else 1
            )

            is Map<*, *> -> SpecialItem(
                id = (raw["id"] as? Number)?.toLong() ?: 0L,
                name = raw["name"] as? String ?: "",
                startDay = (raw["startDay"] as? Number)?.toInt()?.takeIf { it in 1..7 } ?: 1,
                endDay = (raw["endDay"] as? Number)?.toInt()?.takeIf { it in 1..7 } ?: 1
            )

            else -> null
        }
    }
}

/**
 * 特殊时段块（无编号，如早读/大课间/眼保健操）。
 * 按自定义起止时间沿时间轴定位，挤出让出纵向空间，不计入课程提醒。
 */
data class SpecialBlock(
    val id: Long = 0L,
    val name: String = "",          // 如"早读""眼保健操"
    val startTime: String = "08:00",
    val endTime: String = "08:40",
    // 内部按星期划分的子块。
    // 注意：Gson 反序列化旧版本数据时该字段缺失会被置为 null（Kotlin 默认值不生效），
    // 所以声明为可空，统一通过 [safeItems] 访问，避免升级后崩溃。
    val items: List<SpecialItem>? = null
) {
    /**
     * 子块列表。同时兜住两种情况：
     *  1. 旧数据缺失 items 字段（Gson 会置为 null）；
     *  2. R8 剥掉泛型签名，导致元素被解析成 Map。
     *
     * 这里刻意先把 items 转成 `List<*>`（擦除类型）再逐元素判断。
     * **不要**直接写 `items?.filterIsInstance<SpecialItem>()`：那个接收者的静态类型
     * 已经是 `List<SpecialItem>`，编译器/R8 有可能把这次过滤当成恒等变换而消除，
     * 坏元素于是照样漏进 UI（v1.5.0 的崩溃就是这么穿过去的）。
     */
    val safeItems: List<SpecialItem>
        get() = (items as List<*>?).orEmpty().mapNotNull { SpecialItem.fromRaw(it) }

    companion object {
        /**
         * 把 Gson 可能留下的"原始形态"还原成 [SpecialBlock]。
         *
         * 已是 [SpecialBlock] 实例时也必须重建：keep 规则生效后 Gson 会生成真正的
         * SpecialBlock，但 UnsafeAllocator 绕过 Kotlin 默认值，旧 JSON 缺
         * name/startTime/endTime 时字段为 null。若原样返回，`SpecialGridBand.<init>`
         * 的非空 String 参数会抛 NPE（R8 优化后表现为 `Object.getClass()` on null）。
         */
        // USELESS_ELVIS：以下 ?: 编译期看似走左值，但 Gson 反序列化后字段可能是 null
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "ELVIS_ALWAYS_NULL")
        internal fun fromRaw(raw: Any?): SpecialBlock? = when (raw) {
            is SpecialBlock -> SpecialBlock(
                id = raw.id,
                name = raw.name ?: "",
                startTime = raw.startTime ?: "08:00",
                endTime = raw.endTime ?: "08:40",
                items = (raw.items as List<*>?).orEmpty().mapNotNull { SpecialItem.fromRaw(it) }
            )

            is Map<*, *> -> SpecialBlock(
                id = (raw["id"] as? Number)?.toLong() ?: 0L,
                name = raw["name"] as? String ?: "",
                startTime = raw["startTime"] as? String ?: "08:00",
                endTime = raw["endTime"] as? String ?: "08:40",
                items = (raw["items"] as? List<*>)?.mapNotNull { SpecialItem.fromRaw(it) }
            )

            else -> null
        }
    }
}

/**
 * 时间配置数据类 - 存储多套时间设置
 */
data class TimeConfig(
    val id: Long = 0L,
    val name: String = "默认配置",

    // 节数配置
    val morningSections: Int = 4,
    val afternoonSections: Int = 4,
    val eveningSections: Int = 4,

    // 快速时间配置
    val quickTimeEnabled: Boolean = false,
    val classDuration: Int = 45,
    val shortBreak: Int = 10,

    // 长课间休息配置
    val longBreakEnabled: Boolean = false,
    val longBreakMorning: Int = 20,
    val longBreakAfternoon: Int = 20,
    val longBreakEvening: Int = 20,
    val longBreakMorningSection: Int = 2,
    val longBreakAfternoonSection: Int = 2,
    val longBreakEveningSection: Int = 2,

    // 各时段起始时间
    val morningStartHour: Int = 8,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 14,
    val afternoonStartMinute: Int = 0,
    val eveningStartHour: Int = 18,
    val eveningStartMinute: Int = 30,

    // 各节次时间（全局绝对节次号 -> "HH:mm-HH:mm"）
    // 注意：Gson 会将 Int key 转换为 String，所以存储为 Map<String, String>
    val sectionTimes: Map<String, String> = emptyMap(),

    // 自定义节次名称（key 同 sectionTimes，如 "morning_1" -> "早自习"）
    val sectionNames: Map<String, String> = emptyMap(),

    // 特殊时段块（无编号，如早读/大课间/眼保健操），不计入课程提醒
    val specialBlocks: List<SpecialBlock> = emptyList()
) {

    /**
     * 特殊时段块列表。同 [SpecialBlock.safeItems]：先擦除类型再逐元素还原，
     * 兜住"R8 剥掉泛型签名 → Gson 把元素解析成 Map"的情况。
     * 需要遍历 specialBlocks 的地方都应通过这个 getter 访问。
     */
    val safeSpecialBlocks: List<SpecialBlock>
        get() = (specialBlocks as List<*>?).orEmpty().mapNotNull { SpecialBlock.fromRaw(it) }

    /**
     * 获取指定时段的节次时间映射
     * period: "morning" / "afternoon" / "evening"
     * 返回：时段内相对节次号 (1-6) -> "HH:mm-HH:mm"
     */
    fun getPeriodTimes(period: String): Map<Int, String> {
        // 如果有预设的快速时间，使用快速时间计算
        if (quickTimeEnabled) {
            return calculatePeriodTimes(period)
        }

        // 否则从 sectionTimes 中提取
        val result = mutableMapOf<Int, String>()
        for ((k, v) in sectionTimes) {
            val strKey = k.toString()
            if (strKey.startsWith("${period}_")) {
                val idx = strKey.removePrefix("${period}_").toIntOrNull()
                if (idx != null) result[idx] = v
            }
        }
        return result.ifEmpty { getDefaultTimesForPeriod(period) }
    }

    /**
     * 根据快速时间配置计算指定时段的节次时间
     */
    fun calculateSectionTimes(): Map<Int, String> {
        val morningTimes = calculatePeriodTimes("morning")
        val afternoonTimes = calculatePeriodTimes("afternoon")
        val eveningTimes = calculatePeriodTimes("evening")

        val result = mutableMapOf<Int, String>()
        // 转换为全局绝对节次号
        morningTimes.forEach { (k, v) -> result[k] = v }
        afternoonTimes.forEach { (k, v) -> result[morningSections + k] = v }
        eveningTimes.forEach { (k, v) -> result[morningSections + afternoonSections + k] = v }
        return result
    }

    /**
     * 内部方法：计算指定时段的节次时间
     */
    private fun calculatePeriodTimes(period: String): Map<Int, String> {
        val sectionCount = when (period) {
            "morning" -> morningSections
            "afternoon" -> afternoonSections
            "evening" -> eveningSections
            else -> return emptyMap()
        }
        if (sectionCount <= 0) return emptyMap()

        val (startHour, startMinute) = when (period) {
            "morning" -> morningStartHour to morningStartMinute
            "afternoon" -> afternoonStartHour to afternoonStartMinute
            "evening" -> eveningStartHour to eveningStartMinute
            else -> return emptyMap()
        }

        val longBreak = when (period) {
            "morning" -> longBreakMorning
            "afternoon" -> longBreakAfternoon
            "evening" -> longBreakEvening
            else -> 0
        }
        val longBreakSection = when (period) {
            "morning" -> longBreakMorningSection
            "afternoon" -> longBreakAfternoonSection
            "evening" -> longBreakEveningSection
            else -> 2
        }

        return Course.calculatePeriodTimes(
            sectionCount = sectionCount,
            startHour = startHour,
            startMinute = startMinute,
            classDuration = classDuration,
            shortBreak = shortBreak,
            longBreak = if (longBreakEnabled) longBreak else 0,
            longBreakSection = longBreakSection
        )
    }

    companion object {
        /**
         * 默认时间段的节次时间
         */
        private fun getDefaultTimesForPeriod(period: String): Map<Int, String> = when (period) {
            "morning" -> Course.defaultMorningTimes
            "afternoon" -> Course.defaultAfternoonTimes
            "evening" -> Course.defaultEveningTimes
            else -> emptyMap()
        }

        /**
         * 从 CourseRepository 创建默认 TimeConfig
         */
        fun fromRepository(repository: CourseRepository): TimeConfig {
            val sectionTimes = mutableMapOf<String, String>()
            for (period in listOf("morning", "afternoon", "evening")) {
                val times = repository.getPeriodTimes(period)
                for ((idx, time) in times) {
                    sectionTimes["${period}_$idx"] = time
                }
            }
            return TimeConfig(
                morningSections = repository.getMorningSections(),
                afternoonSections = repository.getAfternoonSections(),
                eveningSections = repository.getEveningSections(),
                quickTimeEnabled = repository.getQuickTimeEnabled(),
                classDuration = repository.getClassDuration(),
                shortBreak = repository.getShortBreak(),
                longBreakEnabled = repository.getLongBreakEnabled(),
                longBreakMorning = repository.getLongBreakMorning(),
                longBreakAfternoon = repository.getLongBreakAfternoon(),
                longBreakEvening = repository.getLongBreakEvening(),
                longBreakMorningSection = repository.getLongBreakMorningSection(),
                longBreakAfternoonSection = repository.getLongBreakAfternoonSection(),
                longBreakEveningSection = repository.getLongBreakEveningSection(),
                morningStartHour = repository.getMorningStartHour(),
                morningStartMinute = repository.getMorningStartMinute(),
                afternoonStartHour = repository.getAfternoonStartHour(),
                afternoonStartMinute = repository.getAfternoonStartMinute(),
                eveningStartHour = repository.getEveningStartHour(),
                eveningStartMinute = repository.getEveningStartMinute(),
                sectionTimes = sectionTimes
            )
        }
    }
}
