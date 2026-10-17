package com.haooz.chedule.data

/**
 * 上课地点在缩略态（超级岛槽位 / 灵动岛胶囊 / 实况通知右侧 / 息屏标题）与
 * 岛展开后「地点」行的显示规则。
 *
 * ## 问题
 *
 * 这两处由系统排版，可用宽度 7 个半角（真机锁屏大岛实测），超出部分从尾部丢弃。
 * 「2号综合楼B3027」显示成「2号综合楼B3」，「新校区J02西414」显示成「新校区J02西4」。
 * 楼栋号与房间号在末尾，被截掉的正是这几位。
 *
 * 各校地点写法不统一（「新校区」「B区」「2号综合楼」「博学楼」），没有一种自动规则
 * 能同时适配，所以给三种模式：原样、自动精简、自定义正则。
 *
 * ## 默认不删字样
 *
 * 自动精简只做格式处理：剥「××校区 / ××校园 / ××学区」前缀、丢空白、
 * 全角字母数字转半角，不因为宽度不够就删掉用户的字。
 *
 * 需要「塞不进就丢开头」时，用 [ClassroomLabelRule.tailKeepEnabled] 显式打开，
 * 并按 [ClassroomLabelRule.slotWidth] 指定可用宽度（档位见 [SLOT_WIDTH_PRESETS]）。
 * 打开后从尾部保留：前缀丢了还能认出是哪栋楼，房间号丢了这条信息就没用了。
 *
 * ## 作用范围
 *
 * 缩略态与岛展开的「地点」行都用精简结果，两处都是窄条，真机实测同样截断。
 * 通知正文（bigText）仍用课表原文，那里是整行排版，放得下完整地点。
 */
object ClassroomLabel {

    /** 不处理，按课表原文显示。默认值，升级后行为不变。 */
    const val MODE_RAW = 0

    /** 自动精简：缩楼名 + 剥校区前缀 + 丢空白 + 全角转半角。不截断。 */
    const val MODE_AUTO = 1

    /** 自定义：正则查找替换，结果原样使用。 */
    const val MODE_CUSTOM = 2

    /**
     * 内置的楼名缩写表（长的在前）。
     *
     * 只按宽度从尾部裁会切出半个词：「第五教学楼C509」在窄档位下变成「学楼C509」，
     * 「2号综合楼B3027」变成「合楼B3027」。同一份数据在不同档位下碎片形态还不一样，
     * 得反复试档位才能碰巧得到可读结果。
     *
     * 缩写是语义压缩，压完既短又成词，也不依赖机型宽度：
     * 「第五教学楼C509」（14 半角）→「五教C509」（8 半角）。
     *
     * 顺序敏感：「第五教学楼」必须排在「教学楼」前面，否则前者会被后者先吃掉，
     * 变成「第五教」。同理「综合楼」「实验楼」要排在单字规则之前。
     */
    val BUILDING_ABBREVIATIONS = listOf(
        "第一教学楼" to "一教", "第二教学楼" to "二教", "第三教学楼" to "三教",
        "第四教学楼" to "四教", "第五教学楼" to "五教", "第六教学楼" to "六教",
        "第七教学楼" to "七教", "第八教学楼" to "八教", "第九教学楼" to "九教",
        "第十教学楼" to "十教",
        "教学楼" to "教",
        "综合楼" to "综",
        "实验楼" to "实",
        "图书馆" to "图",
        "体育馆" to "体",
        "办公楼" to "办",
        "宿舍楼" to "宿",
        "食堂" to "食",
        "阶梯教室" to "阶",
        "报告厅" to "报",
    )

    const val KEY_MODE = "classroom_label_mode"
    const val KEY_PATTERN = "classroom_label_pattern"
    const val KEY_REPLACEMENT = "classroom_label_replacement"
    const val KEY_TAIL_KEEP = "classroom_label_tail_keep"
    const val KEY_SLOT_WIDTH = "classroom_label_slot_width"

    /** 用户在设置页自己补的缩写，每行一条「原名=缩写」；与内置表合并，同名时以用户的为准。 */
    const val KEY_ABBREVIATIONS = "classroom_label_abbreviations"

    /**
     * [ClassroomLabelRule.slotWidth] 的默认值。
     *
     * 7 是从真机锁屏大岛 / 下拉通知栏「地点」槽位实测的容量：用户两张截图里
     * 分别完整显示「新校区W…」「新校区J…」，都是 新(2)+校(2)+区(2)+ASCII(1) = 7 个
     * 半角，第 8 个半角起被 SystemUI 截断并追加省略号。
     *
     * 不能取更大：SystemUI 截的是尾部，输出 8 个半角时被砍掉的就是房号最后一位
     * （「J02西414」→「J02西41…」）。先压到 ≤7，房号才一定落在容量内。
     *
     * 只有打开 [ClassroomLabelRule.tailKeepEnabled] 时这个值才参与运算。
     */
    const val DEFAULT_SLOT_WIDTH = 7

    /**
     * 可选宽度档位（半角）。
     *
     * 必须包含 [DEFAULT_SLOT_WIDTH]，否则设置页下拉里没有任何一项是选中态。
     * 默认 7 对应锁屏大岛 / 通知栏实测；其余档位留给别的位置（息屏 AOD 可能更窄、
     * 桌面展开态更宽），用户按自己设备调。
     */
    val SLOT_WIDTH_PRESETS = listOf(5, 6, 7, 8, 10, 12)

    /**
     * 自定义规则的现成模板。
     *
     * 写正则对多数人不是能随手做的事，而这里要解决的其实是几个固定的诉求
     * （前缀太长、只想要房号、去掉括号里的东西）。每条配一句说明和实际效果，
     * 选中即填进输入框，填完也能自己改。
     *
     * [sample] 用的是各校常见的写法。
     */
    val CUSTOM_PRESETS = listOf(
        CustomPreset(
            title = "去掉开头的校区名",
            description = "只删「××校区」这几个字，后面原样保留",
            pattern = "^.*?校区",
            replacement = "",
            sample = "新校区J02西414 → J02西414",
        ),
        CustomPreset(
            title = "去掉括号里的内容",
            description = "楼名后面常带的「（新）」「(东)」这类注解",
            pattern = "[（(][^）)]*[）)]",
            replacement = "",
            sample = "博学楼（新）A302 → 博学楼A302",
        ),
        CustomPreset(
            title = "只保留最后的房间号",
            description = "前半段全删，只留字母 + 数字的房号",
            pattern = "^.*?([A-Za-z]?\\d{3,4})$",
            replacement = "$1",
            sample = "2号综合楼B3027 → B3027",
        ),
        CustomPreset(
            title = "只保留楼号和房间号",
            description = "把「第X教学楼」压成「X教」，前面有校区名也一并去掉",
            pattern = "^(?:.*?校区)?(?:第)?(.)教学楼(.*)$",
            replacement = "$1教$2",
            sample = "第二教学楼A302 → 二教A302",
        ),
    )

    /** 正则长度上限。合法地点串都在 64 字符内，超长的正则只可能是误输入。 */
    const val MAX_PATTERN_LENGTH = 96

    /**
     * 输入长度上限，超长直接不做替换。
     *
     * 用户正则的复杂度不可控（`(a+)+$` 这类嵌套量词在长串上是灾难性回溯），
     * 而这条链路跑在发通知的线程上，卡住就是 ANR。地点名本来就短，
     * 超过这个长度说明数据不是地点，跳过替换。
     */
    const val MAX_INPUT_LENGTH = 64

    /** 校区前缀最多剥几层。合并办学时会叠（「新校区·南校区」），限制层数防死循环。 */
    private const val CAMPUS_STRIP_ROUNDS = 3

    /**
     * 「××校区 / ××校园 / ××学区」前缀。
     *
     * CJK 区间显式写成 `\u4E00-\u9FA5` 而不是 `\p{L}`：后者在 JS / Native 正则实现上支持度不一致，
     * 而 :core 的 commonMain 要过 linuxX64 编译门禁。
     */
    private val CAMPUS_PREFIX = Regex(
        "^[（(\\[【]?[\\u4E00-\\u9FA5A-Za-z0-9·\\-—]{0,8}(?:校区|校园|学区)[）)\\]】]?[·\\-—、,，:：]?"
    )

    fun rule(
        mode: Int = MODE_RAW,
        pattern: String = "",
        replacement: String = "",
        tailKeepEnabled: Boolean = false,
        slotWidth: Int = DEFAULT_SLOT_WIDTH,
        abbreviations: String = "",
    ): ClassroomLabelRule =
        ClassroomLabelRule(mode, pattern, replacement, tailKeepEnabled, slotWidth, abbreviations)

    /** 从偏好文件读规则。所有键都不存在时得到「原样 + 不截断」，与升级前的显示完全一致。 */
    fun read(store: KeyValueStore, slotWidth: Int = DEFAULT_SLOT_WIDTH): ClassroomLabelRule = ClassroomLabelRule(
        mode = store.getInt(KEY_MODE, MODE_RAW),
        pattern = store.getString(KEY_PATTERN, ""),
        replacement = store.getString(KEY_REPLACEMENT, ""),
        tailKeepEnabled = store.getBoolean(KEY_TAIL_KEEP, false),
        // 存量键优先，[slotWidth] 只在用户没选过档位时兜底
        slotWidth = store.getInt(KEY_SLOT_WIDTH, slotWidth),
        abbreviations = store.getString(KEY_ABBREVIATIONS, ""),
    )

    /**
     * 按 [rule] 生成缩略态地点文案。
     *
     * 任何一步出错（正则写错、替换串里的 `$` 越界）都退回原文：
     * 提醒是强时效通知，不能因为一条正则整条发不出去。
     *
     * 用户自己填的缩写与模式无关，只要填了就生效。设置页那个入口在任何模式下都点得开，
     * 填完却不生效的话，用户只会看到「填了没反应」。内置表仍只属于「自动精简」：
     * [MODE_RAW] 且用户没填缩写时，结果与课表原文逐字相同。
     *
     * 缩写对「自定义正则」的结果同样生效：正则把前缀处理干净后，楼名该缩还是要缩，
     * 否则还得再走一次宽度裁剪。
     */
    fun apply(raw: String, rule: ClassroomLabelRule): String {
        if (raw.isBlank()) return raw
        val rewritten = when (rule.mode) {
            MODE_AUTO -> autoShorten(raw)
            MODE_CUSTOM -> customReplace(raw, rule)
            else -> raw
        }
        val label = abbreviatize(rewritten, rule.abbreviations, builtIn = rule.mode != MODE_RAW)
        if (!rule.tailKeepEnabled || rule.slotWidth <= 0) return label
        return if (slotDisplayWidth(label) > rule.slotWidth) tailWithin(label, rule.slotWidth) else label
    }

    /** 半角为单位的显示宽度：CJK / 全角算 2，其余算 1。预览用它告诉用户「还差多少」。 */
    fun slotDisplayWidth(text: String): Int {
        var width = 0
        for (c in text) width += charWidth(c)
        return width
    }

    /** 自定义正则的合法性检查。返回 null 表示可用；UI 用它拦下明显写错的输入。 */
    fun patternError(pattern: String): String? {
        if (pattern.isBlank()) return null
        if (pattern.length > MAX_PATTERN_LENGTH) return "正则过长（上限 $MAX_PATTERN_LENGTH 字符）"
        return if (runCatching { Regex(pattern) }.isFailure) "正则语法有误" else null
    }

    /**
     * 给用户一句结论，而不只是把结果摆出来。
     *
     * 预览只显示「原文 → 结果」时，用户看不出超宽了要不要动、该开开关还是该调宽度。
     * 这里把判断做掉，界面直接用这句话。
     *
     * @param tailKeepActionLabel 打开保留尾部的那个开关在界面上的叫法。给了就在结论里
     *   说出该动哪里；自定义规则弹窗里那个开关不在这层，传 null 只讲后果。
     * @return null 表示没什么要提醒的（放得下，或已开保尾部兜住）。
     */
    fun previewHint(
        raw: String,
        rule: ClassroomLabelRule,
        tailKeepActionLabel: String? = null,
    ): String? {
        if (raw.isBlank()) return null
        val shown = apply(raw, rule)
        val width = slotDisplayWidth(shown)
        if (rule.slotWidth <= 0 || width <= rule.slotWidth) return null
        if (rule.tailKeepEnabled) {
            // 开了还超宽，只可能是宽度档位小到单个字都放不下
            return "保留尾部后仍有 $width 个半角，系统会再截一次"
        }
        val consequence = "共 $width 个半角，系统会截掉末尾（房号可能丢）"
        return if (tailKeepActionLabel.isNullOrBlank()) {
            consequence
        } else {
            "$consequence；打开「$tailKeepActionLabel」可保住房号"
        }
    }

    /** 只剥前缀、清噪声，不按宽度丢字。 */
    private fun autoShorten(raw: String): String {
        var label = normalize(raw)
        if (label.isEmpty()) return raw

        // 先摘掉括号注解（「博学楼（新）A302」→「博学楼A302」）。
        // 放在剥离前缀之前做，否则「（新校区）J02西414」的括号会被前缀规则先吃掉一半。
        // 括号注解几乎都是「（新）」「(东)」这类可丢的补充说明，留着占宽度，
        // 按宽度硬切时还会把括号对切开，留下「新)A302」这种碎片。
        label = label.replace(BRACKET_NOTE, "")
        if (label.isEmpty()) return raw

        var rounds = 0
        while (rounds++ < CAMPUS_STRIP_ROUNDS) {
            val stripped = label.replaceFirst(CAMPUS_PREFIX, "")
            // 剥不动、或剥完什么都不剩（整串就是「新校区」）都停手
            if (stripped == label || stripped.isBlank()) break
            label = stripped
        }
        return label
    }

    /**
     * 成对的括号注解：「（新）」「(东)」「[北]」。
     *
     * 只认成对的，不配对的不动。用户地点里偶尔有「A302(1)」这种编号，
     * 那是信息不是注解；不成对的留着交给宽度裁剪处理。
     */
    private val BRACKET_NOTE = Regex("[（(\\[【][^）)\\]】]{1,6}[）)\\]】]")

    /**
     * 楼名缩写：把「第五教学楼」压成「五教」这类。
     *
     * 顺序敏感：长规则必须排在短规则前面，否则「第五教学楼」会被「教学楼」先吃掉，
     * 变成「第五教」。所以这里按长度降序走一遍，不照表顺序。
     *
     * 用户自定义的缩写优先于内置表，同名键以用户的为准。
     *
     * @param userLines 用户补的缩写，每行一条「原名=缩写」；空行与不带 `=` 的行忽略。
     * @param builtIn 是否带上内置表。[MODE_RAW] 下只认用户自己写的：内置表属于「自动精简」，
     *   若在原样模式下也生效，升级后的老用户会发现自己什么都没设置、显示却变了。
     */
    private fun abbreviatize(text: String, userLines: String, builtIn: Boolean = true): String {
        if (text.isEmpty()) return text
        val merged = LinkedHashMap<String, String>()
        if (builtIn) for ((from, to) in BUILDING_ABBREVIATIONS) merged[from] = to
        for ((from, to) in parseAbbreviations(userLines)) {
            // 「新校区J02西=西区」与「J02西=西」等价：轮到缩写时前缀已经被剥掉了
            merged[stripCampusPrefix(from)] = to
        }
        if (merged.isEmpty()) return text

        var out = text
        // 按原词长度降序，保证「第五教学楼」先于「教学楼」被替换
        for ((from, to) in merged.entries.sortedByDescending { it.key.length }) {
            if (from.isNotEmpty()) out = out.replace(from, to)
        }
        return out
    }

    /**
     * 缩写「原名」里带的校区前缀要一并去掉。
     *
     * 用户和 AI 都容易把前缀写进「原名」：课表里是「新校区J02西414」，就写成
     * 「新校区J02西=西区」。但自动精简先剥前缀、后做缩写，轮到匹配时串已经是
     * 「J02西414」，这条规则永远匹配不上，表现是「填了没反应」。
     *
     * 这里在合并规则时把「原名」也过一遍前缀剥离，让两种写法等价。只处理前缀，
     * 括号注解与空白不动：那些在 [autoShorten] 里已经处理过，这里再摘会把
     * 用户刻意保留的括号吃掉。
     */
    private fun stripCampusPrefix(name: String): String {
        var label = name
        var rounds = 0
        while (rounds++ < CAMPUS_STRIP_ROUNDS) {
            val stripped = label.replaceFirst(CAMPUS_PREFIX, "")
            if (stripped == label || stripped.isBlank()) break
            label = stripped
        }
        return label.trim()
    }

    /**
     * 缩写「原名」的长度上限。
     *
     * 楼名再长也到不了这个数（「某某大学附属中学教学楼」11 字）。设这个上限是为了挡一类
     * 误操作：用户把提示词当结果粘回来时，提示词正文里带 `=` 的说明句
     * （「每行输出一条，格式为「原地点里的楼名=缩写」」）会被当成一条规则解析出来。
     * 那种「原名」长得不像楼名，直接丢掉。
     */
    const val MAX_ABBREV_ORIGIN = 16

    /**
     * 解析用户缩写文本。格式：每行 `原名=缩写`。
     *
     * 用纯文本而不是 JSON：用户从设置页手改，也可能直接粘贴 AI 给的结果，
     * 一行一条最不容易写坏，出错也一眼能看出是哪行。
     */
    fun parseAbbreviations(text: String): List<Pair<String, String>> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val sep = line.indexOfFirst { it == '=' || it == '：' || it == ':' }
            if (sep <= 0) continue
            val from = line.substring(0, sep).trim()
            val to = line.substring(sep + 1).trim()
            if (from.isEmpty() || to.isEmpty()) continue
            // 比上限还长的「原名」不是楼名，多半是整句说明被切了一半
            if (from.length > MAX_ABBREV_ORIGIN) continue
            // 缩写比原名还长就没有意义，直接忽略，免得用户以为生效了
            if (to.length >= from.length) continue
            result += from to to
        }
        return result
    }

    private fun customReplace(raw: String, rule: ClassroomLabelRule): String {
        val pattern = rule.pattern
        if (pattern.isBlank() || pattern.length > MAX_PATTERN_LENGTH) return raw
        if (raw.length > MAX_INPUT_LENGTH) return raw
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return raw
        return runCatching { regex.replace(raw, rule.replacement) }.getOrElse { raw }
    }

    /**
     * 去掉全部空白、全角字母数字转半角（「Ｂ３０２７」这样的一串省下 4 个半角）。
     *
     * 空白在地点串里没有信息量（「3 号 楼 201」和「3号楼201」指同一间教室），
     * 留着占缩略态的宽度。这两步只动格式、不动内容。
     */
    private fun normalize(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            val half = toHalfWidth(c)
            if (half == ' ' || half == '\t' || half == '\n' || half == '\r') continue
            append(half)
        }
    }

    private fun toHalfWidth(c: Char): Char = when {
        c.code == 0x3000 -> ' '
        c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar()
        else -> c
    }

    /**
     * 从尾部取到宽度放得下为止。地点串很短，逐字符累加足够了。
     *
     * 取之前先试更省字的写法（见 [compactWithin]）：短楼名加房号时，硬切尾部
     * 往往切掉楼名里有辨识度的字、留下通用字（「五教C509」→「教C509」、
     * 「博学楼A302」→「楼A302」）。房号一个字都不能少，能压缩的只有楼名。
     *
     * 取完之后再把开头的孤立标点剪掉：按宽度硬切会把「博学楼（新）A302」切成
     * 「新)A302」这种以半个括号开头的碎片。剪掉不占信息的标点不影响房号，
     * 还能腾出宽度多留一个有效字符。
     */
    private fun tailWithin(text: String, slotWidth: Int): String {
        compactWithin(text, slotWidth)?.let { return it }

        var width = 0
        var index = text.length
        while (index > 0) {
            val w = charWidth(text[index - 1])
            if (width + w > slotWidth) break
            width += w
            index--
        }
        var head = index
        while (head < text.length && text[head] in ORPHAN_PUNCTUATION) head++
        // 整个结果都是标点（地点串不该出现，但别返回空串）时保留原样
        return if (head >= text.length) text.substring(index) else text.substring(head)
    }

    /**
     * 房号整段保住，楼名压到放得下为止。压缩分三步，逐级放宽：
     *
     * 1. 只丢楼名与房号之间的分隔符（「五教-C509」→「五教C509」）。
     * 2. 丢掉楼名末尾的通用字（「五教」→「五」、「博学楼」→「博学」）。
     *    两字缩写里后一个字多半是「教 / 楼 / 馆」这类只说明建筑类型、
     *    不区分是哪一栋的字，先丢它。
     * 3. 还不够就只留楼名的头一个字（「博学」→「博」）。
     *
     * 楼名超过 3 个字就不走这条路：「大学生活动中心301」硬留头一个字只会得到
     * 「大301」，不如从尾部切成「中心301」。楼名里带字母的（「J02西414」）
     * 也不能按字数截，切成「J02414」就认不出来了。
     *
     * @return 放得下时返回压缩后的结果；否则 null，交给 [tailWithin] 硬切。
     */
    private fun compactWithin(text: String, slotWidth: Int): String? {
        val (name, room) = splitNameAndRoom(text) ?: return null
        if (name.isEmpty() || room.isEmpty() || name.length > 3) return null

        if (slotDisplayWidth(name + room) <= slotWidth) return name + room

        var trimmed = name
        var droppedGeneric = false
        while (trimmed.length > 1 && trimmed.last() in GENERIC_NAME_TAIL) {
            trimmed = trimmed.dropLast(1)
            droppedGeneric = true
            if (slotDisplayWidth(trimmed + room) <= slotWidth) return trimmed + room
        }

        // 只对「确实以通用字结尾」的纯汉字楼名继续砍到只剩头一个字。
        // 「二号综B3027」这种末字本身就是辨识字的，再砍会把「综」丢掉，
        // 反而不如从尾部切成「综B3027」。
        if (droppedGeneric && name.all { it.code > 0x7F }) {
            var head = trimmed
            while (head.length > 1) {
                head = head.dropLast(1)
                if (slotDisplayWidth(head + room) <= slotWidth) return head + room
            }
        }
        return null
    }

    /**
     * 把串拆成「楼名」与「房号」两段。房号是从尾部往前扫出的那段 ASCII 字母、
     * 数字与短横线，且必须以数字结尾。
     *
     * 「五教C509」→（五教, C509）；「综B3027」→（综, B3027）；
     * 「J02西414」→（J02西, 414）。
     *
     * 分不出来（整串没有房号、或房号前面什么都不剩）时返回 null。
     */
    private fun splitNameAndRoom(text: String): Pair<String, String>? {
        var start = text.length
        while (start > 0) {
            val c = text[start - 1]
            if (c.code < 128 && (c.isLetterOrDigit() || c == '-' || c == '_')) start-- else break
        }
        // 房号必须以数字结尾，否则「ABC」这种整串字母也会被当成房号
        if (start >= text.length || !text[text.length - 1].isDigit()) return null
        var room = text.substring(start)
        // 楼名与房号之间的短横线是分隔符，不是房号的一部分
        while (room.length > 1 && (room[0] == '-' || room[0] == '_')) room = room.substring(1)
        return text.substring(0, start) to room
    }

    /** 楼名缩写里不区分楼栋的通用尾字：留着它不如留前面那个字。 */
    private val GENERIC_NAME_TAIL = charArrayOf('教', '楼', '馆', '堂', '厅', '室', '院', '场', '心')

    /** 从中间截断时不该留在开头的孤立标点。 */
    private val ORPHAN_PUNCTUATION = charArrayOf(
        ')', '）', ']', '】', '}', '、', ',', '，', ':', '：', ';', '；', '.', '。', '-', '—', '·', '/',
    )

    /**
     * 给外部 AI 的提示词，用来为各校自己的地名生成缩写。
     *
     * 缩写是学校内部约定：「第五教学楼」在本校叫「五教」还是「5教」还是「E5」，
     * 内置表猜不到。AI 看着用户真实的地点列表做归纳，比内置表覆盖面广，
     * 而且跑在 App 外面，不需要联网权限和 API key。
     *
     * 输出格式与设置页的缩写输入框一致（一行一条 `原名=缩写`），用户能逐条核对、
     * 想删哪条就删哪条。正则错了是静默错，缩写错了肉眼可见。
     *
     * 提示词里要求「同一栋楼只写一条」：缩写表匹配的是楼名这个词，换了房号照样能用。
     * 若让 AI 按整串地点输出，就得为每个房号各写一条，用户改一次房号就失效，
     * 且失效时没有任何提示。
     */
    fun abbreviationPrompt(classrooms: List<String>): String {
        val list = classrooms.filter { it.isNotBlank() }.distinct().joinToString("\n") { "- $it" }
        return """请帮我给下面这些教室地点起缩写，用于手机通知栏的窄条显示。

这些是我课表里的教室：
$list

要求：
1. 每行输出一条，格式为「原地点里的楼名=缩写」，用等号连接，不要加序号、空格或解释。
2. 只缩写楼名部分，房间号（如 A302、B3027、J02西414）保持原样不要动。
3. 楼名里不要带「××校区 / ××校园 / ××学区」这类前缀，只写楼本身的名字。例如写「J02西=J02西」而不是「新校区J02西=J02西」。
4. 缩写要本校人一眼能认出来，通常 1~2 个字，例如「第五教学楼=五教」「综合楼=综」。没有通用简称的楼名，去掉前缀后原样保留即可，不要硬压成认不出的单字。
5. 同一栋楼只写一条，不要按房号重复。课表里同时有「第五教学楼C509」和「第五教学楼A302」时，只写一行「第五教学楼=五教」。
6. 同一栋楼各处要用同一种写法，不要混用，例如不要一会儿「五教」一会儿「5教」。
7. 如果某个地点已经是简称、或没有可缩写的楼名，就不要输出它。
8. 不要输出任何除了这些行以外的东西。

示例输出：
第五教学楼=五教
综合楼=综
图书馆=图"""
    }

    /**
     * 把 AI 返回的文本清成可用的缩写行。
     *
     * 模型偶尔会带上序号、项目符号或解释句，这里只保留能解析成 `原名=缩写` 的行，
     * 其余丢掉。
     */
    fun sanitizeAbbreviationReply(reply: String): String =
        reply.lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("* ")
                    // 去掉「1. 」「2、」这类序号前缀
                    .replace(Regex("^\\d+\\s*[.、)）]\\s*"), "")
                    .trim()
            }
            .filter { it.isNotEmpty() && parseAbbreviations(it).isNotEmpty() }
            .joinToString("\n")

    private fun charWidth(c: Char): Int = when (c.code) {
        in 0x1100..0x115F, // 谚文字母
        in 0x2E80..0x303E, // CJK 部首扩展、假名标点
        in 0x3041..0x33FF,
        in 0x3400..0x4DBF,
        in 0x4E00..0x9FFF,
        in 0xA000..0xA4CF,
        in 0xAC00..0xD7A3,
        in 0xF900..0xFAFF,
        in 0xFE30..0xFE6F,
        in 0xFF00..0xFF60, // 全角形式
        in 0xFFE0..0xFFE6,
        -> 2

        else -> 1
    }
}

/**
 * [ClassroomLabel.apply] 的参数。默认值 = 不做任何处理、也不截断。
 *
 * @param tailKeepEnabled 超出 [slotWidth] 时是否从尾部保留（默认关）。
 *   关掉时超宽的地点原样交给系统，由系统截断；要用这个能力必须显式打开。
 */
data class ClassroomLabelRule(
    val mode: Int = ClassroomLabel.MODE_RAW,
    val pattern: String = "",
    val replacement: String = "",
    val tailKeepEnabled: Boolean = false,
    val slotWidth: Int = ClassroomLabel.DEFAULT_SLOT_WIDTH,
    val abbreviations: String = "",
)

/**
 * 自定义规则的一条现成模板，见 [ClassroomLabel.CUSTOM_PRESETS]。
 *
 * @param sample 该规则套在常见地点上的效果，直接展示给用户，不用他去读正则。
 */
data class CustomPreset(
    val title: String,
    val description: String,
    val pattern: String,
    val replacement: String,
    val sample: String,
)
