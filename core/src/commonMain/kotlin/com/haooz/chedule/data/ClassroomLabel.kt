package com.haooz.chedule.data

/**
 * 上课地点在**缩略态**（超级岛槽位 / 灵动岛胶囊 / 实况通知右侧 / 息屏）的显示规则。
 *
 * ## 为什么需要
 *
 * 缩略态由系统排版，可用宽度只有 12 个半角左右，**超出部分从尾部丢弃**：
 * 「2号综合楼B3027」被截成「2号综合楼B3」，「新校区J02西414」被截成「新校区J02西4」。
 * 而地点里真正用来找教室的信息（楼栋号、房间号）正好在末尾 —— 系统截掉的就是最要紧的那几位。
 *
 * 各校地点写法不统一（「新校区」「B区」「2号综合楼」「博学楼」），做不到一个通吃的自动规则，
 * 所以这里给三种模式：原样、自动精简、自定义正则。
 *
 * ## 自动精简的两步
 *
 * 1. **剥前缀**：去掉「××校区 / ××校园 / ××学区」这类只在校园内定位时才有用的前缀。
 *    这一步的效果是「新校区J02西414」→「J02西414」，楼栋与房间号都还在。
 * 2. **保尾部**：剥完仍超出可用宽度时，从**尾部**保留到放得下为止。理由是同一件事：
 *    前缀丢了还能认出是哪栋楼，房间号丢了这条信息就废了 —— 宁可丢「综合」也不丢「B3027」。
 *
 * 第 2 步只在自动模式生效。自定义模式按用户给的正则**原样**输出，不做任何二次加工，
 * 否则「我写清楚了要显示什么，App 又给我截一刀」无法解释。
 *
 * ## 作用范围
 *
 * 只喂给缩略态。展开后的正文（通知 bigText、岛的 hintInfo.subTitle）仍用课表原文，
 * 那里放得下完整地点，没必要牺牲信息。
 */
object ClassroomLabel {

    /** 不处理，按课表原文显示。默认值 —— 现有用户的行为不因本功能改变。 */
    const val MODE_RAW = 0

    /** 自动精简：剥校区前缀 + 超宽时保尾部。 */
    const val MODE_AUTO = 1

    /** 自定义：正则查找替换，结果原样使用。 */
    const val MODE_CUSTOM = 2

    const val KEY_MODE = "classroom_label_mode"
    const val KEY_PATTERN = "classroom_label_pattern"
    const val KEY_REPLACEMENT = "classroom_label_replacement"

    /**
     * 缩略态可用宽度（半角为单位）。12 是几个实测值里最保守的一个：
     * 小米超级岛模板 2 的 B 区、原生实况胶囊、ColorOS 实时动态都在 12 上下。
     * 宁可留一点空白，也不要让系统再截一次 —— 系统的截断方向与这里相反（丢尾）。
     */
    const val SLOT_WIDTH = 12

    /** 正则长度上限。合法地点串都在 64 字符内，超长的正则只可能是误输入。 */
    const val MAX_PATTERN_LENGTH = 96

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
        slotWidth: Int = SLOT_WIDTH,
    ): ClassroomLabelRule = ClassroomLabelRule(mode, pattern, replacement, slotWidth)

    /** 从偏好文件读规则。三个键都不存在时得到 [MODE_RAW]，与升级前的显示完全一致。 */
    fun read(store: KeyValueStore, slotWidth: Int = SLOT_WIDTH): ClassroomLabelRule = ClassroomLabelRule(
        mode = store.getInt(KEY_MODE, MODE_RAW),
        pattern = store.getString(KEY_PATTERN, ""),
        replacement = store.getString(KEY_REPLACEMENT, ""),
        slotWidth = slotWidth,
    )

    /**
     * 按 [rule] 生成缩略态地点文案。
     *
     * 任何一步出错（正则写错、替换串里的 `$` 越界）都退回原文：
     * 提醒是强时效通知，宁可显示得难看也不能因为一条正则整个发不出去。
     */
    fun apply(raw: String, rule: ClassroomLabelRule): String {
        if (raw.isBlank()) return raw
        return when (rule.mode) {
            MODE_AUTO -> autoShorten(raw, rule.slotWidth)
            MODE_CUSTOM -> customReplace(raw, rule)
            else -> raw
        }
    }

    /** 半角为单位的显示宽度：CJK / 全角算 2，其余算 1。用于预览里告诉用户「还剩多少余量」。 */
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

    private fun autoShorten(raw: String, slotWidth: Int): String {
        var label = normalize(raw)
        if (label.isEmpty()) return raw

        var rounds = 0
        while (rounds++ < CAMPUS_STRIP_ROUNDS) {
            val stripped = label.replaceFirst(CAMPUS_PREFIX, "")
            // 剥不动、或剥完什么都不剩（整串就是「新校区」）都停手
            if (stripped == label || stripped.isBlank()) break
            label = stripped
        }

        if (slotWidth > 0 && slotDisplayWidth(label) > slotWidth) {
            label = tailWithin(label, slotWidth)
        }
        return label
    }

    private fun customReplace(raw: String, rule: ClassroomLabelRule): String {
        val pattern = rule.pattern
        if (pattern.isBlank() || pattern.length > MAX_PATTERN_LENGTH) return raw
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return raw
        return runCatching { regex.replace(raw, rule.replacement) }.getOrElse { raw }
    }

    /**
     * 去掉全部空白、全角字母数字转半角（「Ｂ３０２７」这样的一串省下 4 个半角）。
     *
     * 空白在地点串里没有信息量（「3 号 楼 201」和「3号楼201」指同一间教室），
     * 留着只会白占缩略态的宽度，所以一律丢。
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

    /** 从尾部取到宽度放得下为止。地点串很短，逐字符累加足够了。 */
    private fun tailWithin(text: String, slotWidth: Int): String {
        var width = 0
        var index = text.length
        while (index > 0) {
            val w = charWidth(text[index - 1])
            if (width + w > slotWidth) break
            width += w
            index--
        }
        return text.substring(index)
    }

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

/** [ClassroomLabel.apply] 的参数。默认值 = 不做任何处理。 */
data class ClassroomLabelRule(
    val mode: Int = ClassroomLabel.MODE_RAW,
    val pattern: String = "",
    val replacement: String = "",
    val slotWidth: Int = ClassroomLabel.SLOT_WIDTH,
)
