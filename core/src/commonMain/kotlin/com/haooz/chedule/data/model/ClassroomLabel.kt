package com.haooz.chedule.data.model

import com.haooz.chedule.data.storage.KeyValueStore
import kotlin.text.iterator

/**
 * 缩略态（超级岛 / 灵动岛 / 实况通知右侧 / 息屏标题）的地点与课程名显示规则。
 *
 * 公共部分是格式清理：摘括号注解、剥「××校区 / ××校园 / ××学区」前缀、丢空白、
 * 全角字母数字转半角。[METHOD_REGEX] 再套内置楼名缩写表，[METHOD_AI] 只套
 * AI 生成的缩写表（内置表兜底）。压缩靠缩写表而非按宽度切字；缩写表覆盖不到的
 * 楼名原样保留，宁可由系统截尾，也不由 App 决定删哪个字。
 *
 * **地点在缩略态与展开态都生效**（超级岛大岛地点行、通知展开正文）；课程名仍只作用于
 * 缩略态槽位，展开态里的课名保持原文。课程名走
 * [applyCourse] 同一套入口，但只删修饰词、不压成简称（课名没有「五教」式公认缩写）。
 *
 * ⚠ 两条真实语料红线（2026-10 复跑）：括号里可能是楼栋/房号而非注解（如 `(一)101`、
 * `西校区(西)2#教学楼2301`），规则见 [keepBracket]；课程名不能套地点的格式清理
 * （丢空白会拼成 `VisualBasic`，「××校园」前缀会吃掉课名里的「校园」），故走
 * [cleanUpCourse]，只摘括号，不动前缀、空格、全角标点。
 */
object ClassroomLabel {

    /** 内置规则：格式清理 + 内置楼名缩写表。 */
    const val METHOD_REGEX = 0

    /** AI 精简：格式清理 + 用户贴回来的楼名缩写表（内置表兜底）。 */
    const val METHOD_AI = 1

    const val KEY_METHOD = "classroom_label_method"

    /** 精简作用范围：0=原始文本，1=只精简地点，2=只精简课程，3=两者都精简。 */
    const val SCOPE_OFF = 0
    const val SCOPE_CLASSROOM = 1
    const val SCOPE_COURSE = 2
    const val SCOPE_BOTH = 3

    /** 作用范围偏好键。没写过时是 [SCOPE_OFF]，即原样显示。 */
    const val KEY_SCOPE = "classroom_label_scope"

    /**
     * AI 精简产出的缩写，每行一条「原名=缩写」。
     *
     * 由端上的 AI 一键生成后写进来（见设置页「精简方式 = AI 精简」那个按钮）。
     */
    const val KEY_ABBREVIATIONS = "classroom_label_abbreviations"

    /**
     * 手动改写：每行一条「课表原文=想显示的文本」。
     *
     * 与 [KEY_ABBREVIATIONS] 不是一回事：对照表是**规则**，按片段替换；这里是
     * **这一条就要显示成这个**，原文整串相等才命中，优先级高于一切自动结果。
     * 关闭精简（[SCOPE_OFF]）时整块不生效。
     */
    const val KEY_OVERRIDES = "classroom_label_overrides"

    /**
     * 内置的楼名缩写表（长的在前）。
     *
     * 顺序敏感：「第五教学楼」必须排在「教学楼」前面，否则会被先吃掉变成「第五教」。
     * 每条只在前一字**不是汉字**时才替换（见 [abbreviatize]）：真实课表里
     * 「教研综合楼A座302」这类「专名前缀 + 通用后缀」楼名很多，无边界替换会压出
     * 「教研综」这种不成词的半截楼名。
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

    /**
     * 整串映射：这几门课靠删修饰压不下来，只能按公认写法整串换掉。
     *
     * 放在最前面、且**必须整串相等**才命中 —— 前缀匹配会把
     * 「毛泽东思想和中国特色社会主义理论体系概论（下）」这类也误伤。
     */
    private val COURSE_EXACT = listOf(
        "毛泽东思想和中国特色社会主义理论体系概论" to "毛泽东思想概论",
        "毛泽东思想与中国特色社会主义理论体系概论" to "毛泽东思想概论",
        "习近平新时代中国特色社会主义思想概论" to "习近平新时代思想",
        "大学生生涯发展与就业指导" to "大学生就业指导",
        "大学生心理健康教育" to "心理健康",
    )

    /**
     * 第一档：术语换公认短形式 + 明确冗余词整段删。
     *
     * 只要名字超过 [COURSE_TARGET_WIDTH] 就会走这一档。长的排前面，避免被短规则先吃掉。
     */
    private val COURSE_TERMS = listOf(
        "概率论" to "概率",
        "数理统计" to "数统",
        "基本原理" to "原理",
        "理论体系" to "",
        "重要思想" to "",
        "概论" to "",
        "导论" to "",
        "纲要" to "",
        "基础" to "",
        "主义" to "",
        "基本" to "",
    )

    /**
     * 第二档：只有第一档压完还超宽才动。
     *
     * 删的是连接词和泛化后缀，语义损失比第一档大（「与」一删就少一层并列关系），
     * 故只作兜底，不无条件下手。
     */
    private val COURSE_TRIM = listOf(
        // 「大学生」「指导」不在这里：「大学生就业指导」删掉就只剩「就业」，信息没了。
        // 需要去掉「大学生」的那几门走 [COURSE_EXACT] 整串钉死。
        "及应用" to "",
        "及其应用" to "",
        "优秀" to "",
        "与" to "",
        "技术" to "",
        "教育" to "",
        "软件" to "",
        "作品" to "",
        "鉴赏" to "",
        "发展" to "",
        "分析" to "",
    )

    /** 房间号与用途说明之间的连字符（半角/全角都算）。 */
    private val ROOM_DASH = Regex("[-－]")

    /** 「数字/中文数字 + 号 + 教学楼」→「X号楼」。学生口语里就是「4号楼」，不说「4号教」。 */
    private val NUMBERED_TEACHING_BUILDING = Regex("([0-9]+|[一二三四五六七八九十]+)号教学楼")

    /** 房号后面缀的「教室」两个字，缩略态里没有信息量。 */
    private val TRAILING_CLASSROOM = Regex("教室$")

    /** 课程名里成对出现的直引号 / 弯引号。缩略态不需要这层装饰，去掉引号保留内容。 */
    private val QUOTE_PAIRS = listOf("\"" to "\"", "“" to "”", "「" to "」", "『" to "』")

    /** 课程名不够短时，末尾的这层装饰（书名号等）先留着；这里只处理对称引号。 */
    private val QUOTE_CHARS = charArrayOf('"', '“', '”', '「', '」', '『', '』')

    /**
     * 显示宽度上限：缩略态大约能放 6 个汉字。
     *
     * ⚠ 不能按 `String.length` 算 —— 真实课名有 `Python语言程序设计基础` 这类
     * ASCII 混排，按字符数会低估一半宽度（ASCII 只占半个汉字）。
     */
    private const val COURSE_TARGET_WIDTH = 12

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

    /** 成对括号，捕获括号内的内容。 */
    private val BRACKET_NOTE = Regex("[（(\\[【]([^）)\\]】]{1,12})[）)\\]】]")

    /**
     * 括号里出现这些词，就认定是注解（学期/学分/校区/教室属性/开课单位），整段摘掉。
     *
     * 「大学」「学院」「联盟」是慕课挂的开课学校，「版」是教材版本 —— 真实课名里
     * 这三种括号占绝大多数，且对「哪门课」没有帮助。
     */
    private val NOTE_WORDS = listOf(
        "大学", "学院", "联盟", "平台", "版", "学时", "学分", "必修", "选修", "公选",
        "限选", "网络", "课程", "论文", "设计", "实习", "实验", "实践", "理论", "讲座",
        "系列", "双语", "全英", "翻转", "慕课", "校区", "教室", "班级", "年级", "学期",
        "学年", "册",
    )

    /** 单字括号里属于「修饰」的几个 ；其余单字（一/二/三/人/J/A）是楼栋代号，要留。 */
    private const val SINGLE_NOTE_CHARS = "新旧东西南北主老分本"

    /**
     * 缩写「原名」的长度上限。
     *
     * 挡一类误操作：用户把提示词当结果粘回来时，提示词里带 `=` 的说明句会被当成
     * 规则解析。取 40 而非更小：真实课名有 18 字的，卡在 16 会把 AI 合法产出的
     * 长条目静默丢掉，表现为「AI 说精简了 N 条，界面上没变」。
     */
    const val MAX_ABBREV_ORIGIN = 40

    /** 从偏好文件读规则。所有键都不存在时得到「关闭」，与升级前的显示完全一致。 */
    fun read(store: KeyValueStore): ClassroomLabelRule {
        val scope = store.getInt(KEY_SCOPE, SCOPE_OFF)
            .takeIf { it in SCOPE_OFF..SCOPE_BOTH }
            ?: SCOPE_OFF
        return ClassroomLabelRule(
            enabled = scope != SCOPE_OFF,
            method = store.getInt(KEY_METHOD, METHOD_REGEX),
            abbreviations = store.getString(KEY_ABBREVIATIONS, ""),
            scope = scope,
            overrides = store.getString(KEY_OVERRIDES, ""),
        )
    }

    /**
     * 按 [rule] 生成缩略态地点文案。关闭时结果与课表原文逐字相同。
     *
     * 两种方式互斥：[METHOD_REGEX] 走格式清理 + 内置缩写表；[METHOD_AI] 先套
     * AI 对照表**再摘一遍括号注解**。AI 方式不剥校区前缀、不缩楼名 —— AI 是看着
     * 原文归纳的，键也是原文片段，先被正则改写就对不上（表现为「填了却没反应」）。
     *
     * ⚠ 摘括号必须放在替换之后：实测（2026-10，真实教务导出）模型会把
     * 「中药药剂学(江西中医药大学)(网络公选)」这类「课名 + 两个注解括号」整条跳过
     * （60 条漏 18 条）。替换先跑保证 AI 的键仍对原文生效，再摘括号兜住漏网的；
     * 顺序反过来会让 AI 的键全部失效。
     */
    fun apply(raw: String, rule: ClassroomLabelRule): String {
        if (raw.isBlank() || !rule.enabled) return raw
        // 手动改写压过自动规则：用户点着改过的那一条，就该逐字显示成他写的样子
        rule.overrideMap[raw]?.let { return it }
        if (!rule.shrinksClassroom) return raw
        return when (rule.method) {
            METHOD_AI -> postClean(replaceByTable(raw, rule.abbreviations), forCourse = false)
            else -> abbreviatize(cleanUp(raw))
        }
    }

    /**
     * 按 [rule] 生成缩略态的课程名文案。关闭时与课表原文逐字相同。
     *
     * 与 [apply] 同一套规则，只是缩写表不同：正则方式走 [COURSE_TERMS] / [COURSE_TRIM]，
     * AI 方式复用同一张「原名=缩写」表 —— AI 一次就把课程名和楼名一起给了，
     * 分成两张表只会多一处要同步的地方。
     */
    fun applyCourse(raw: String, rule: ClassroomLabelRule): String {
        if (raw.isBlank() || !rule.enabled) return raw
        // 同上：手动改写优先。作用范围只管自动规则，管不了用户亲手写的那一条
        rule.overrideMap[raw]?.let { return it }
        if (!rule.shrinksCourse) return raw
        return when (rule.method) {
            METHOD_AI -> postClean(replaceByTable(raw, rule.abbreviations), forCourse = true)
            else -> abbreviatizeCourse(cleanUpCourse(raw))
        }
    }

    /** 解析用户缩写文本。格式：每行 `原名=缩写`。空行、`#` 注释与不带分隔符的行忽略。 */
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
            if (from.length > MAX_ABBREV_ORIGIN) continue
            // 缩写比原名还长就没有意义，直接忽略，免得用户以为生效了
            if (to.length >= from.length) continue
            // 只删了连字符/空隙的「伪精简」不值得占一行（真实教务导出里 `A-103` 这种很多）
            if (to == from.filterNot { it in PSEUDO_SHRINK_CHARS }) continue
            result += from to to
        }
        return result
    }

    /**
     * 解析手动改写表（[KEY_OVERRIDES]）。格式：每行 `课表原文=想显示的文本`。
     *
     * 比 [parseAbbreviations] 宽松，因为它存的是「结果」不是「规则」：只按 `=` 切
     * （课名里有冒号，按冒号切会把键切成半截）；不要求右边更短（用户可能只是把
     * 自动结果改回认得出的写法）。
     */
    fun parseOverrides(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val half = line.indexOf('=')
            val full = line.indexOf('＝')
            val sep = when {
                half > 0 -> half
                full > 0 -> full
                else -> continue
            }
            val from = line.substring(0, sep).trim()
            val to = line.substring(sep + 1).trim()
            if (from.isEmpty() || to.isEmpty()) continue
            if (from.length > MAX_ABBREV_ORIGIN) continue
            out[from] = to
        }
        return out
    }

    /** 把一条手动改写写回文本（`value` 为空 = 删掉这一条，回到自动结果）。 */
    fun putOverride(text: String, raw: String, value: String?): String {
        val map = LinkedHashMap(parseOverrides(text))
        if (value.isNullOrBlank()) map.remove(raw) else map[raw] = value.trim()
        return map.entries.joinToString(separator = "\n") { "${it.key}=${it.value}" }
    }

    /**
     * AI 替换之后的一层兜底清理：只摘括号注解（课程名另外去对称引号）。
     *
     * 不碰前缀、楼名、空格 —— 那几件是 AI 已按原文处理过的，再动一次只会和产出打架。
     */
    private fun postClean(text: String, forCourse: Boolean): String {
        var out = dropBracketNotes(text, forCourse)
        if (forCourse) out = dropQuoteMarks(out)
        return out.trim().ifEmpty { text }
    }

    /** 只清格式，不按宽度丢字。地点用。 */
    private fun cleanUp(raw: String): String {
        var label = normalize(raw)
        if (label.isEmpty()) return raw

        // 先摘掉括号注解（「博学楼（新）A302」→「博学楼A302」）。
        // 放在剥离前缀之前做，否则「（新校区）J02西414」的括号会被前缀规则先吃掉一半。
        label = dropBracketNotes(label, forCourse = false)
        if (label.isEmpty()) return raw

        var rounds = 0
        while (rounds++ < CAMPUS_STRIP_ROUNDS) {
            val stripped = label.replaceFirst(CAMPUS_PREFIX, "")
            // 剥不动、或剥完什么都不剩（整串就是「新校区」）都停手
            if (stripped == label || stripped.isBlank()) break
            label = stripped
        }
        // 「X号教学楼」→「X号楼」要在缩写表之前，否则会先被压成「X号教」
        label = label.replace(NUMBERED_TEACHING_BUILDING) { "${it.groupValues[1]}号楼" }
        // 房号后面缀的「教室」只在整串确实带房号时才去，别把「阶梯教室」削成「阶梯」
        if (label.any { it.isDigit() }) label = label.replace(TRAILING_CLASSROOM, "")
        return dropUsageSuffix(label)
    }

    /**
     * 课程名的格式清理。刻意比 [cleanUp] 轻，只做两件事：摘括号注解、去对称引号。
     *
     * ⚠ 不剥「××校区」前缀（会把「校园英语」的「校园」当校区吃掉）、不丢空格
     * （「Visual Basic」会拼成「VisualBasic」）、不转全角标点（「看美剧，学口语」
     * 被改成半角逗号就和课表原文对不上）。
     */
    private fun cleanUpCourse(raw: String): String {
        var label = raw.trim()
        label = dropBracketNotes(label, forCourse = true)
        label = dropQuoteMarks(label)
        label = label.trim()
        return label.ifEmpty { raw }
    }

    /**
     * 摘括号注解。只摘「看着像注解」的，规则见 [keepBracket]。
     *
     * 地点与课程名判据不同：课程名里除 `(C语言)`「(TRIZ)」这种带字母的，其余括号
     * 都是修饰；地点里 `(一)`「(汽-423)」是楼栋与房号，必须留。
     */
    private fun dropBracketNotes(text: String, forCourse: Boolean): String {
        if (text.none { it == '（' || it == '(' || it == '[' || it == '【' }) return text
        return BRACKET_NOTE.replace(text) { match ->
            if (keepBracket(match.groupValues[1].trim(), forCourse)) match.value else ""
        }
    }

    /** @see dropBracketNotes */
    private fun keepBracket(content: String, forCourse: Boolean): Boolean {
        if (content.isEmpty()) return false
        // 注解词优先：`（新校区）`「(网络公选)」「(24学时)」不管多短都摘
        if (NOTE_WORDS.any { content.contains(it) }) return false
        val hasLetter = content.any { it in 'a'..'z' || it in 'A'..'Z' }
        if (forCourse) return hasLetter
        if (content.length == 1) {
            // 单字：`(一)`「(人)」是楼栋代号，`（新）` 是修饰，`(3)` 是序号
            val only = content[0]
            return !only.isDigit() && SINGLE_NOTE_CHARS.indexOf(only) < 0
        }
        // 含字母（`(J)`「(集毅楼A)」）或含数字（`(汽-423)`）的都可能是楼栋/房号
        return hasLetter || content.any { it.isDigit() }
    }

    /** 去掉对称引号，保留引号里的内容：「大学生"四史"教育」→「大学生四史教育」。 */
    private fun dropQuoteMarks(text: String): String {
        if (text.none { it in QUOTE_CHARS }) return text
        var out = text
        for ((open, close) in QUOTE_PAIRS) {
            var from = out.indexOf(open)
            while (from >= 0) {
                val to = out.indexOf(close, from + open.length)
                if (to < 0) break
                out = out.removeRange(to, to + close.length).removeRange(from, from + open.length)
                from = out.indexOf(open, from)
            }
        }
        return out
    }

    /**
     * 去掉房间号后面用「-」连着的用途说明。
     *
     * 课表里常见「安实3-102-茶叶加工实验室」这种写法，占地方的正是后半段。
     * 判据是「这一段里没有数字」—— 房号一定带数字，说明一般不带；只有整串里
     * 至少还剩一段带数字时才动手，否则「东区-教学楼」会被削成「东区」。
     */
    private fun dropUsageSuffix(text: String): String {
        val parts = text.split(ROOM_DASH)
        if (parts.size < 2) return text
        var end = parts.size
        while (end > 1 && parts[end - 1].none { it.isDigit() }) end--
        if (end == 1 && parts[0].none { it.isDigit() }) return text
        return parts.subList(0, end).joinToString("-")
    }

    /**
     * 楼名缩写：把「第五教学楼」压成「五教」这类。只走内置表。
     *
     * 按原词长度降序走，保证「第五教学楼」先于「教学楼」被替换。
     * 命中位置的前一字必须是**非汉字**（或串首、或「号」），否则跳过：
     * 「教研综合楼」不该压成「教研综」、「化工实验楼」不该压成「化工实」。
     */
    private fun abbreviatize(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        for ((from, to) in BUILDING_ABBREVIATIONS) {
            if (from.isNotEmpty()) out = out.replaceAtBoundary(from, to)
        }
        return out
    }

    /** 只在「词首边界」替换，语义同 [abbreviatize] 的注释。 */
    private fun String.replaceAtBoundary(from: String, to: String): String {
        val first = indexOf(from)
        if (first < 0) return this
        val sb = StringBuilder(length)
        var cursor = 0
        var index = first
        while (index >= 0) {
            sb.append(this, cursor, index)
            val prev = if (index == 0) null else this[index - 1]
            val allowed = prev == null || !prev.isCjk() || prev == '号'
            sb.append(if (allowed) to else from)
            cursor = index + from.length
            index = indexOf(from, cursor)
        }
        sb.append(this, cursor, length)
        return sb.toString()
    }

    /**
     * 课程名精简：只删修饰词，不压成简称。
     *
     * 已经够宽（[COURSE_TARGET_WIDTH] 以内）的直接原样返回 —— 「统计学」这种
     * 本来就放得下，再缩反而认不出。
     */
    private fun abbreviatizeCourse(text: String): String {
        if (text.isEmpty()) return text
        COURSE_EXACT.firstOrNull { it.first == text }?.let { return it.second }
        if (displayWidth(text) <= COURSE_TARGET_WIDTH) return text
        val first = applyRules(text, COURSE_TERMS)
        return if (displayWidth(first) <= COURSE_TARGET_WIDTH) first else applyRules(first, COURSE_TRIM)
    }

    /**
     * 逐条替换，带三条护栏（都是真实语料跑出来的）：
     *
     * - 「零基础」「无基础」里的「基础」不能删，否则「零基础日语听说」变「零日语听说」；
     * - 「基本功」里的「基本」不能删，否则「中国舞基本功训练」变「中国舞功训练」；
     * - 删完不能以引号/标点收尾，否则「大学生"四史"教育」会剩下一个孤零零的引号。
     */
    private fun applyRules(text: String, rules: List<Pair<String, String>>): String {
        var out = text
        for ((from, to) in rules) {
            if (from.isEmpty()) continue
            var index = out.indexOf(from)
            while (index >= 0) {
                if (!isProtectedRemoval(out, index, from)) {
                    // 这一处不能动，跳过它继续找后面的同一条规则
                    index = out.indexOf(from, index + from.length)
                    continue
                }
                out = out.replaceRange(index, index + from.length, to)
                index = out.indexOf(from, index + to.length)
            }
        }
        return out
    }

    /** @see applyRules */
    private fun isProtectedRemoval(text: String, index: Int, from: String): Boolean {
        val prev = if (index == 0) null else text[index - 1]
        val next = if (index + from.length >= text.length) null else text[index + from.length]
        if (from == "基础" && prev != null && (prev == '零' || prev == '无')) return false
        if (from == "基本" && next == '功') return false
        val result = text.removeRange(index, index + from.length)
        val tail = result.lastOrNull()
        if (tail != null && (tail in QUOTE_CHARS || tail in TRAILING_PUNCT)) return false
        return true
    }

    /**
     * 按「原名=缩写」对照表逐条替换。只给 [METHOD_AI] 用。
     *
     * 做的是纯文本替换，不剥前缀、不缩楼名：AI 看到的原文是什么，键就该是什么。
     * 按原名长度降序，避免「第五教学楼」被更短的「教学楼」先吃掉。
     */
    private fun replaceByTable(text: String, table: String): String {
        val entries = parseAbbreviations(table)
        if (entries.isEmpty()) return text
        var out = text
        for ((from, to) in entries.sortedByDescending { it.first.length }) {
            out = out.replace(from, to)
        }
        return out
    }

    /**
     * 去掉全部空白、全角字母数字转半角（「Ｂ３０２７」这样的一串省下 4 个半角）。
     *
     * 空白在地点串里没有信息量（「3 号 楼 201」和「3号楼201」指同一间教室），
     * 留着占缩略态的宽度。
     */
    private fun normalize(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            val half = toHalfWidth(c)
            if (half == ' ' || half == '\t' || half == '\n' || half == '\r') continue
            append(half)
        }
    }

    /**
     * 全角转半角**只认字母与数字**。
     *
     * ⚠ 早先按 `0xFF01..0xFF5E` 整段转，顺手把全角逗号、冒号也改成半角，
     * 「看美剧，学口语」显示成「看美剧,学口语」，和课表原文对不上。
     */
    private fun toHalfWidth(c: Char): Char = when {
        c.code == 0x3000 -> ' '
        c.code in 0xFF10..0xFF19 || c.code in 0xFF21..0xFF3A || c.code in 0xFF41..0xFF5A ->
            (c.code - 0xFEE0).toChar()
        else -> c
    }

    private fun Char.isCjk(): Boolean = this in '\u4E00'..'\u9FA5'

    /** 缩略态按像素排，中文占两个半角宽。 */
    private fun displayWidth(text: String): Int = text.sumOf { c ->
        when {
            c.code >= 0x1100 && (
                c.code <= 0x115F ||
                    c.code in 0x2E80..0xA4CF ||
                    c.code in 0xAC00..0xD7A3 ||
                    c.code in 0xF900..0xFAFF ||
                    c.code in 0xFE30..0xFE6F ||
                    c.code in 0xFF00..0xFF60 ||
                    c.code in 0xFFE0..0xFFE6
                ) -> 2
            else -> 1
        }
    }

    private const val TRAILING_PUNCT = "、，,。：:；;·-—"
    private const val PSEUDO_SHRINK_CHARS = " -－—·、,，"
}

/**
 * [ClassroomLabel.apply] 的参数。默认值 = 关闭，即原样显示。
 *
 * @param enabled 关闭时结果就是课表原文。
 * @param method [ClassroomLabel.METHOD_REGEX] 或 [ClassroomLabel.METHOD_AI]。
 * @param abbreviations [ClassroomLabel.METHOD_AI] 下生效的「原名=缩写」文本。
 */
data class ClassroomLabelRule(
    val enabled: Boolean = false,
    val method: Int = ClassroomLabel.METHOD_REGEX,
    val abbreviations: String = "",
    /** 作用范围，见 `ClassroomLabel.SCOPE_*`；不传时按 [enabled] 推导成「两者 / 原始」。 */
    val scope: Int = if (enabled) ClassroomLabel.SCOPE_BOTH else ClassroomLabel.SCOPE_OFF,
    /** 手动改写文本，见 [ClassroomLabel.KEY_OVERRIDES]。原文整串相等才命中。 */
    val overrides: String = "",
) {

    /**
     * [overrides] 解析后的对照表。
     *
     * 不进 `equals`（[ClassroomLabelRule] 仍是按 [overrides] 原文比较），
     * 一次解析只有几行，不值得再加一层缓存。
     */
    val overrideMap: Map<String, String> = ClassroomLabel.parseOverrides(overrides)

    /** 地点要不要精简。 */
    val shrinksClassroom: Boolean
        get() = enabled && (scope == ClassroomLabel.SCOPE_BOTH || scope == ClassroomLabel.SCOPE_CLASSROOM)

    /** 课程名要不要精简。 */
    val shrinksCourse: Boolean
        get() = enabled && (scope == ClassroomLabel.SCOPE_BOTH || scope == ClassroomLabel.SCOPE_COURSE)
}
