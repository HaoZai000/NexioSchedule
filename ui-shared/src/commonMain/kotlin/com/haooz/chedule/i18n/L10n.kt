package com.haooz.chedule.i18n

/**
 * 双语字符串。创建时同时给出中文与英文文案，调用 [invoke] 时按当前语言取值。
 *
 * 在组合内读取会跟踪 [AppLanguage] 的快照状态，语言切换后引用它的 UI 自动重组；
 * 非 Composable 上下文（ViewModel、通知、分享编解码等）同样按调用时刻的语言取值。
 *
 * 占位符：`%s` / `%d` 按出现顺序替换为 [invoke] 的实参；无占位符时不需要传参。
 *
 * ```
 * val ok = L("确定", "OK")
 * val weekN = L("第%d周", "Week %d")
 * Text(ok())
 * Text(weekN(currentWeek))
 * ```
 */
class L10n(private val zh: String, private val en: String) {

    /** 值是否为本条目的某一语言原文（默认名匹配等基础设施用） */
    internal fun matchesRaw(text: String): Boolean = text == zh || text == en

    /** 指定语言取原文：轮播/预览需要在同一屏同时展示中英文时用。 */
    internal fun raw(isZh: Boolean): String = if (isZh) zh else en

    operator fun invoke(vararg args: Any?): String {
        val template = if (AppLanguage.isZh) zh else en
        if (args.isEmpty()) return template
        val sb = StringBuilder(template.length + 24)
        var argIndex = 0
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '%' && i + 1 < template.length && (template[i + 1] == 's' || template[i + 1] == 'd') && argIndex < args.size) {
                sb.append(args[argIndex++]?.toString() ?: "")
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

/** 创建双语字符串，见 [L10n]。 */
fun L(zh: String, en: String): L10n = L10n(zh, en)

/**
 * 存储的默认名称 → 显示文本：值与 [defaults] 中任一默认名的中/英文原文一致
 * （即用户从未自定义）时随当前语言显示默认名；自定义过则原样返回。
 * 仅做显示映射，不回写存储——同一份数据切回原语言仍显示原默认名。
 */
fun String.orDefaultName(vararg defaults: L10n): String =
    defaults.firstOrNull { it.matchesRaw(this) }?.invoke() ?: this
