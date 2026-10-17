package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ClassroomLabel] 的行为锁定。
 *
 * 两组真实反馈串是重点：`2号综合楼B3027` 被系统截成 `2号综合楼B3`（issue #69），
 * `新校区J02西414` 被截成 `新校区J02西4`（前缀把房号挤出去了）。
 *
 * 另外固化一条设计红线：默认不截断。自动精简只做格式清理，房号与楼栋永远不由程序删；
 * 只有显式打开 [ClassroomLabelRule.tailKeepEnabled] 才会按宽度丢开头。这条一旦改动，
 * 下面的 `自动模式超宽也不丢字样` 会立刻翻车，所以那处检查不能省。
 */
class ClassroomLabelTest {

    private fun auto(raw: String, slotWidth: Int = ClassroomLabel.DEFAULT_SLOT_WIDTH): String =
        ClassroomLabel.apply(raw, ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO, slotWidth = slotWidth))

    private fun autoWithTailKeep(
        raw: String,
        slotWidth: Int = ClassroomLabel.DEFAULT_SLOT_WIDTH,
    ): String = ClassroomLabel.apply(
        raw,
        ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_AUTO,
            tailKeepEnabled = true,
            slotWidth = slotWidth,
        ),
    )

    private fun custom(raw: String, pattern: String, replacement: String): String =
        ClassroomLabel.apply(
            raw,
            ClassroomLabel.rule(mode = ClassroomLabel.MODE_CUSTOM, pattern = pattern, replacement = replacement),
        )

    @Test
    fun `原样模式不改写地盘`() {
        val raw = "新校区J02西414"
        assertEquals(raw, ClassroomLabel.apply(raw, ClassroomLabel.rule()))
    }

    @Test
    fun `自动模式剥掉校区前缀且房号完整`() {
        // 前缀 6 个半角去掉后，楼栋 J02 与房号西414 全在，宽度 8 不再触发系统截断。
        // 真机（HyperOS 超级岛）实测：下发到槽位的 title 就是 J02西414
        assertEquals("J02西414", auto("新校区J02西414"))
    }

    @Test
    fun `自动模式顺手把常见楼名缩成成词的形式`() {
        // 不做缩写的话只能靠宽度裁，会切出「学楼」「合楼」这种半个词（见 cut 实测）
        assertEquals("五教C509", auto("第五教学楼C509"))
        assertEquals("五教C509", auto("南校区第五教学楼C509"))
        assertEquals("二教A302", auto("东校区第二教学楼A302"))
        assertEquals("图B1-101", auto("图书馆B1-101"))
        assertEquals("实D区203", auto("实验楼D区203"))
    }

    @Test
    fun `缩写不能把长楼名吃成半截`() {
        // 「第五教学楼」必须排在「教学楼」前面，否则会变成不合语感的「第五教」
        assertEquals("五教C509", auto("第五教学楼C509"))
        assertEquals("十教C509", auto("第十教学楼C509"))
        // 已经缩到「二教」的输入不能再被动一次
        assertEquals("二教A302", auto("二教A302"))
    }

    @Test
    fun `缩写要认得没带序号的教学楼`() {
        assertEquals("教A302", auto("教学楼A302"))
    }

    @Test
    fun `用户自定义缩写优先于内置`() {
        val rule = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_AUTO,
            abbreviations = "第五教学楼=五号楼",
        )
        assertEquals("五号楼C509", ClassroomLabel.apply("第五教学楼C509", rule))
    }

    @Test
    fun `用户自定义缩写能用中文冒号且忽略坏行`() {
        val parsed = ClassroomLabel.parseAbbreviations(
            """
            # 这是注释
            第五教学楼=五号楼
            实验楼：实验
            这行没有等号
            =只有缩写
            只有原名=
            等长替换=等长替换
            """.trimIndent(),
        )
        // 「实验楼：实验」用中文冒号也要认；等长替换被丢弃（缩了等于没缩）
        assertEquals(listOf("第五教学楼" to "五号楼", "实验楼" to "实验"), parsed)
    }

    @Test
    fun `缩写对自定义正则的结果同样生效`() {
        // 正则先把前缀处理掉，楼名该缩还是要缩，否则用户还得再开一次宽度裁剪
        val rule = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_CUSTOM,
            pattern = "^.*?校区",
            replacement = "",
        )
        assertEquals("五教C509", ClassroomLabel.apply("南校区第五教学楼C509", rule))
    }

    @Test
    fun `原样模式下填了缩写也生效`() {
        // 设置页那个入口在任何模式下都点得开，填完不生效用户只会以为坏了
        val rule = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_RAW,
            abbreviations = "第五教学楼=五号楼",
        )
        assertEquals("五号楼C509", ClassroomLabel.apply("第五教学楼C509", rule))
    }

    @Test
    fun `缩写原名带校区前缀也能生效`() {
        // 用户和 AI 都容易照着课表原文写「新校区J02西=J02西」。自动精简会先剥掉前缀，
        // 若不做归一化，这条规则永远匹配不上，表现就是「填了没反应」。
        val withPrefix = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_AUTO,
            abbreviations = "新校区J02西=J02西",
        )
        val withoutPrefix = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_AUTO,
            abbreviations = "J02西=J02西",
        )
        val raw = "新校区J02西414"
        assertEquals("J02西414", ClassroomLabel.apply(raw, withPrefix))
        assertEquals(
            ClassroomLabel.apply(raw, withoutPrefix),
            ClassroomLabel.apply(raw, withPrefix),
        )
    }

    @Test
    fun `缩写原名带括号包裹的校区前缀也能生效`() {
        val rule = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_AUTO,
            abbreviations = "（新校区）J02西=J02西",
        )
        assertEquals("J02西414", ClassroomLabel.apply("新校区J02西414", rule))
    }

    @Test
    fun `原样模式没填缩写时仍与原文逐字相同`() {
        // 升级后的老用户没有这份偏好，行为必须一点不变
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_RAW)
        for (raw in listOf("新校区J02西414", "第五教学楼C509", "2号综合楼B3027")) {
            assertEquals(raw, ClassroomLabel.apply(raw, rule))
        }
    }

    @Test
    fun `原样模式填了缩写也能配合保尾部`() {
        val rule = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_RAW,
            tailKeepEnabled = true,
            slotWidth = 7,
            abbreviations = "第五教学楼=五教",
        )
        // 缩写先把 14 半角压到 8，再由保尾部收到 7：放不下「五教」时留「五」不留「教」
        assertEquals("五C509", ClassroomLabel.apply("第五教学楼C509", rule))
    }

    @Test
    fun `缩写生效后不需要宽度裁剪也放得下`() {
        // 这是缩写这一步的主要收益：不靠裁字就压进槽位，也就不会产生碎片。
        // 默认宽度 7 半角（真机锁屏大岛 / 通知栏实测）。
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO)
        // 注意：这一步只验「纯缩写」，不开保尾部。缩写后仍 >7 的（如 2号综B3027=10）
        // 要靠保尾部开关兜底，这里只验缩写本身。
        for (raw in listOf("新校区J02西414", "第五教学楼C509", "东校区第二教学楼A302")) {
            val shown = ClassroomLabel.apply(raw, rule)
            assertEquals(
                8,
                ClassroomLabel.slotDisplayWidth(shown),
                "「$raw」缩完是「$shown」，8 半角是缩写表的正常产出（保尾部再收到 7）",
            )
        }
    }

    @Test
    fun `自动模式不为了宽度删掉楼栋与房号`() {
        // 默认不截断：宁可让系统去截，也不由 App 把「综合」「2号」这些字样替用户删掉
        assertEquals("2号综B3027", auto("2号综合楼B3027"))
        assertEquals("二教A302", auto("东校区第二教学楼A302"))
    }

    @Test
    fun `自动模式摘掉括号注解`() {
        // 「（新）」「(东)」这类注解是补充说明，留着占宽度，按宽度切还会留下
        // 「新)A302」这种半个括号的碎片，所以在缩写阶段就摘掉
        assertEquals("博学楼A302", auto("博学楼（新）A302"))
        assertEquals(10, ClassroomLabel.slotDisplayWidth("博学楼A302"))
    }

    @Test
    fun `自动模式超宽也不丢字样`() {
        // 摘完注解仍超宽的（缩写表覆盖不到的楼名），保持结果不按宽度丢字
        assertEquals("博学楼A302", auto("博学楼（新）A302"))
        assertTrue(
            ClassroomLabel.slotDisplayWidth("博学楼A302") > ClassroomLabel.DEFAULT_SLOT_WIDTH,
            "这条用例要的是「缩写管不了、交给保尾部开关」的情形",
        )
    }

    @Test
    fun `显式打开保尾部才按宽度截断`() {
        // 默认关；打开后从尾部保留，房号一定在。
        // 实测默认容量 7。SystemUI 截的是尾部，我们不先压到 ≤7 的话，
        // 被砍掉的就是房号最后一位
        assertEquals("综B3027", autoWithTailKeep("2号综合楼B3027"))
        // 括号注解先被摘掉（「博学楼A302」10 半角），楼名压到放得下，剩「博A302」(6)
        assertEquals("博A302", autoWithTailKeep("博学楼（新）A302"))
        // 「新校区J02西414」缩成 J02西414 (8) > 7，切到 7，剩 02西414
        assertEquals("02西414", autoWithTailKeep("新校区J02西414"))
    }

    @Test
    fun `保尾部时先压楼名再切字`() {
        // 房号一个字都不能少，能压的只有楼名。楼名放不下时先丢通用尾字
        // （「五教」的「教」不区分楼栋），再退到只留头一个字。
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO, tailKeepEnabled = true)
        val cases = listOf(
            "第五教学楼C509" to "五C509",   // 五教(4)+C509(4)=8 > 7 → 五+C509
            "第二教学楼A302" to "二A302",
            "第十教学楼B203" to "十B203",
            "博学楼A302" to "博A302",       // 无内置缩写，楼名 3 字 → 博+A302
        )
        for ((raw, expected) in cases) {
            assertEquals(expected, ClassroomLabel.apply(raw, rule), "「$raw」的楼名压缩结果不对")
        }
        // 楼名本身就放得下时不动它，别把「五教」无谓压成「五」
        assertEquals("五教201", ClassroomLabel.apply("第五教学楼201", rule))
        // 楼名超过 3 字不走这条路，硬留头一个字反而更认不出
        assertEquals("中心301", ClassroomLabel.apply("大学生活动中心301", rule))
        // 楼名与房号之间的分隔符不影响拆分：空格、短横线、下划线都当分隔符
        for (raw in listOf("第五教学楼-C509", "第五教学楼 C509", "第五教学楼_C509")) {
            assertEquals("五C509", ClassroomLabel.apply(raw, rule), "「$raw」没按分隔符拆开")
        }
    }

    @Test
    fun `保尾部时放宽宽度就不截断`() {
        // 放宽到 16 后，缩写完的结果本来就放得下
        assertEquals("二教A302", autoWithTailKeep("东校区第二教学楼A302", slotWidth = 16))
        assertEquals("2号综B3027", autoWithTailKeep("2号综合楼B3027", slotWidth = 16))
    }

    @Test
    fun `保尾部对原样模式同样生效`() {
        // 原样模式不做缩写，此时才需要真的按宽度切
        val raw = "2号综合楼B3027"
        val out = ClassroomLabel.apply(
            raw,
            ClassroomLabel.rule(tailKeepEnabled = true, slotWidth = 8),
        )
        // 尾部逐字保留到放得下：「B3027」+「楼」= 7 个半角，再往前一个「合」就 9 > 8
        assertEquals("楼B3027", out)
        assertEquals(7, ClassroomLabel.slotDisplayWidth(out))
    }

    @Test
    fun `自动模式不动本来就放得下的地点`() {
        assertEquals("博A201", auto("博A201"))
        assertEquals("教三201", auto("教三201"))
    }

    @Test
    fun `自动模式支持括号与分隔符包裹的前缀`() {
        assertEquals("J02西414", auto("（新校区）J02西414"))
        assertEquals("博学楼A302", auto("[南校区]博学楼A302"))
        assertEquals("博学楼A302", auto("东校区-博学楼A302"))
        assertEquals("博学楼A302", auto("新校区·博学楼A302"))
    }

    @Test
    fun `自动模式整串就是一个校区名时保留原文`() {
        // 剥完剩下空串没有意义，退回原文而不是显示空白
        assertEquals("新校区", auto("新校区"))
        assertEquals("南校区", auto("南校区"))
    }

    @Test
    fun `自动模式全角字母数字转半角`() {
        assertEquals("B3027", auto("Ｂ３０２７"))
    }

    @Test
    fun `自动模式丢掉空白`() {
        assertEquals("博A201", auto("  博  A201 "))
        assertEquals("3号楼201", auto("3 号 楼 201"))
    }

    @Test
    fun `自动模式空串与空白串原样返回`() {
        assertEquals("", auto(""))
        assertEquals("   ", auto("   "))
    }

    @Test
    fun `自定义模式按正则替换`() {
        assertEquals("J02西414", custom("新校区J02西414", "^新校区", ""))
    }

    @Test
    fun `自定义模式支持捕获组`() {
        assertEquals("B3027", custom("2号综合楼B3027", "^.*?([A-Za-z]\\d{3,4})$", "$1"))
    }

    @Test
    fun `自定义模式的字母类吃不到中文`() {
        // 提示用：ASCII 的 [A-Za-z] 匹配不到「西」，只留数字就会把楼栋里的中文吃掉。
        // 想要「J02西414」这种效果，正则要写成 ^新校区 这类只删前缀的规则。
        assertEquals("414", custom("新校区J02西414", "^.*?([A-Za-z]?\\d{3,4})$", "$1"))
        assertEquals("J02西414", custom("新校区J02西414", "^[^\\d]*?(?=J)", ""))
    }

    @Test
    fun `自定义模式默认不追加宽度截断`() {
        // 用户写清楚了要显示什么，App 不再补一刀；缩写仍会生效（「综合楼」→「综」）
        assertEquals("二号综B3027", custom("2号综合楼B3027", "^2号", "二号"))
    }

    @Test
    fun `自定义模式也可以叠加保尾部`() {
        val out = ClassroomLabel.apply(
            "2号综合楼B3027",
            ClassroomLabel.rule(
                mode = ClassroomLabel.MODE_CUSTOM,
                pattern = "^2号",
                replacement = "二号",
                tailKeepEnabled = true,
                slotWidth = 8,
            ),
        )
        // 正则先换成「二号综B3027」（10 半角），仍超 8，才从尾部切到放得下
        assertEquals("综B3027", out)
    }

    @Test
    fun `自定义模式正则不合法时退回原文`() {
        assertEquals("博A201", custom("博A201", "([", ""))
        assertEquals("博A201", custom("博A201", "", "x"))
        assertEquals("博A201", custom("博A201", "a".repeat(ClassroomLabel.MAX_PATTERN_LENGTH + 1), "x"))
    }

    @Test
    fun `自定义模式超长输入不做替换`() {
        // 用户正则复杂度不可控，长输入上可能灾难性回溯；这条链路在发通知的线程上，跳过更安全
        val long = "3".repeat(ClassroomLabel.MAX_INPUT_LENGTH + 1)
        assertEquals(long, custom(long, "3", "4"))
    }

    @Test
    fun `读取偏好默认原样且不截断`() {
        val store = InMemoryKeyValueStore()
        val rule = ClassroomLabel.read(store)
        assertEquals(ClassroomLabelRule(), rule)
        assertFalse(rule.tailKeepEnabled)
        assertEquals(ClassroomLabel.DEFAULT_SLOT_WIDTH, rule.slotWidth)
    }

    @Test
    fun `读取偏好取用户设置`() {
        val store = InMemoryKeyValueStore(
            mapOf(
                ClassroomLabel.KEY_MODE to ClassroomLabel.MODE_AUTO,
                ClassroomLabel.KEY_PATTERN to "^新校区",
                ClassroomLabel.KEY_REPLACEMENT to "",
                ClassroomLabel.KEY_TAIL_KEEP to true,
                ClassroomLabel.KEY_SLOT_WIDTH to 16,
            ),
        )
        val rule = ClassroomLabel.read(store)
        assertEquals(ClassroomLabel.MODE_AUTO, rule.mode)
        assertEquals("^新校区", rule.pattern)
        assertTrue(rule.tailKeepEnabled)
        assertEquals(16, rule.slotWidth)
    }

    @Test
    fun `读出来的规则直接可用`() {
        // 回归用：设置页把「保留尾部 + 8 半角」写进偏好后，通知链路读出来的规则
        // 必须真的执行截断。曾经 Android 侧手抄字段漏掉这两个键，界面开关点了没反应，
        // 而编译期没有任何提示。这条用例钉住「读出来的规则与写入的意图一致」。
        val store = InMemoryKeyValueStore(
            mapOf(
                ClassroomLabel.KEY_MODE to ClassroomLabel.MODE_AUTO,
                ClassroomLabel.KEY_TAIL_KEEP to true,
                ClassroomLabel.KEY_SLOT_WIDTH to 8,
            ),
        )
        val rule = ClassroomLabel.read(store)
        assertEquals("综B3027", ClassroomLabel.apply("2号综合楼B3027", rule))
    }

    @Test
    fun `宽度按全角半角计算`() {
        assertEquals(0, ClassroomLabel.slotDisplayWidth(""))
        assertEquals(5, ClassroomLabel.slotDisplayWidth("A3027"))
        assertEquals(6, ClassroomLabel.slotDisplayWidth("博A201"))
        assertEquals(8, ClassroomLabel.slotDisplayWidth("J02西414"))
    }

    @Test
    fun `正则合法性检查`() {
        assertNull(ClassroomLabel.patternError("^新校区"))
        assertNull(ClassroomLabel.patternError(""))
        assertNotNull(ClassroomLabel.patternError("(["))
        assertNotNull(ClassroomLabel.patternError("a".repeat(ClassroomLabel.MAX_PATTERN_LENGTH + 1)))
    }

    @Test
    fun `预置模板都能用且不含语法错误`() {
        // 模板是给不会写正则的人一键套用的，写错了比没有更糟
        assertTrue(ClassroomLabel.CUSTOM_PRESETS.isNotEmpty())
        for (preset in ClassroomLabel.CUSTOM_PRESETS) {
            assertNull(
                ClassroomLabel.patternError(preset.pattern),
                "模板「${preset.title}」正则有误",
            )
            assertTrue(preset.title.isNotBlank())
            assertTrue(preset.description.isNotBlank())
            assertTrue(preset.sample.isNotBlank())
        }
    }

    @Test
    fun `预置模板的说明与正则行为一致`() {
        // sample 写的是「输入 → 输出」，这里逐条跑一遍：
        // 万一以后有人改了正则忘了改 sample，这条会挂
        fun applyPreset(index: Int, raw: String): String {
            val preset = ClassroomLabel.CUSTOM_PRESETS[index]
            return custom(raw, preset.pattern, preset.replacement)
        }
        // 去掉开头的校区名
        assertEquals("J02西414", applyPreset(0, "新校区J02西414"))
        // 去掉括号里的内容
        assertEquals("博学楼A302", applyPreset(1, "博学楼（新）A302"))
        // 只保留最后的房间号
        assertEquals("B3027", applyPreset(2, "2号综合楼B3027"))
        // 第X教学楼 → X教
        assertEquals("二教A302", applyPreset(3, "第二教学楼A302"))
    }

    @Test
    fun `只保留楼号的模板要能穿过校区前缀`() {
        // 回归用：这条模板原来写 ^第?(.)教学楼(.*)$，带校区前缀的地点整条不匹配、
        // 原样返回（南校区第五教学楼C509 仍是 20 半角），用户会以为模板坏了
        val preset = ClassroomLabel.CUSTOM_PRESETS[3]
        assertEquals("五教C509", custom("南校区第五教学楼C509", preset.pattern, preset.replacement))
        assertEquals("二教A302", custom("东校区第二教学楼A302", preset.pattern, preset.replacement))
        // 不带前缀的写法不能被改坏
        assertEquals("二教A302", custom("第二教学楼A302", preset.pattern, preset.replacement))
        // 不匹配的结构保持原样（缩写照旧生效），不能误伤
        assertEquals("2号综B3027", custom("2号综合楼B3027", preset.pattern, preset.replacement))
        assertEquals("博A201", custom("博A201", preset.pattern, preset.replacement))
    }

    @Test
    fun `默认宽度取保守值`() {
        // 容量是从真机锁屏大岛 / 通知栏量出来的：两张截图分别显示「新校区W…」
        // 「新校区J…」= 新(2)+校(2)+区(2)+ASCII(1) = 7 半角，第 8 个半角起被截。
        // 必须取 7：SystemUI 截尾部，我们若输出 8，丢的就是房号最后一位。
        assertEquals(7, ClassroomLabel.DEFAULT_SLOT_WIDTH)
        assertTrue(
            ClassroomLabel.SLOT_WIDTH_PRESETS.contains(ClassroomLabel.DEFAULT_SLOT_WIDTH),
            "默认值若不在档位里，设置页会显示不出当前选中项",
        )
    }

    @Test
    fun `保尾部压到实测容量后房号完整`() {
        // 这是这个功能的核心保证：默认设置（自动精简 + 保尾部）下，
        // 输出必须 ≤ 实测容量 7，且末尾房号一位不丢
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO, tailKeepEnabled = true)
        val cases = listOf(
            "新校区J02西414" to "02西414",
            "2号综合楼B3027" to "综B3027",
            "第五教学楼C509" to "五C509",
            "南校区第五教学楼C509" to "五C509",
            "实验楼D区203" to "D区203",
            "图书馆B1-101" to "B1-101",
        )
        for ((raw, expected) in cases) {
            val shown = ClassroomLabel.apply(raw, rule)
            assertEquals(expected, shown, "「$raw」的缩略结果与实测不符")
            assertTrue(
                ClassroomLabel.slotDisplayWidth(shown) <= ClassroomLabel.DEFAULT_SLOT_WIDTH,
                "「$raw」缩成「$shown」后仍超默认容量，房号会被系统截掉",
            )
        }
        // 房号最后一位必须在
        for ((raw, _) in cases) {
            val shown = ClassroomLabel.apply(raw, rule)
            val roomTail = raw.filter { it.isDigit() }.takeLast(2)
            assertTrue(
                shown.endsWith(roomTail) || shown.contains(roomTail),
                "「$raw」缩成「$shown」后末尾房号「$roomTail」丢了",
            )
        }
    }

    @Test
    fun `预览结论只在真超宽时给出`() {
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO, slotWidth = 12)
        // 放得下：不打扰用户
        assertNull(ClassroomLabel.previewHint("新校区J02西414", rule))
        assertNull(ClassroomLabel.previewHint("东校区第二教学楼A302", rule))
        // 超宽且没开保尾部：给出结论。缩写表覆盖不到的名字才会到这里
        val raw = "博学楼（新）A302"
        val hint = ClassroomLabel.previewHint(raw, rule)
        assertNull(hint, "「博学楼A302」是 10 半角 ≤ 12，不该提示")
        // 换一个确实超 12 的
        val wide = "某某大学附属中学新校区J02西414"
        val wideHint = ClassroomLabel.previewHint(wide, rule)
        assertNotNull(wideHint, "这条 20+ 半角，应该提示")
        // 开了保尾部：不再提示会被系统截
        val kept = ClassroomLabel.rule(
            mode = ClassroomLabel.MODE_AUTO,
            tailKeepEnabled = true,
            slotWidth = 12,
        )
        assertNull(ClassroomLabel.previewHint(wide, kept))
    }

    @Test
    fun `缩写让更多地点不再需要提示`() {
        // 缩写的核心收益：本来会提示「系统会截掉末尾」的常见写法，缩完就放得下。
        // 真机锁屏大岛 / 通知栏实测容量 7 半角。
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO, tailKeepEnabled = true)
        for (raw in listOf(
            "新校区J02西414",
            "2号综合楼B3027",
            "第五教学楼C509",
            "南校区第五教学楼C509",
            "东校区第二教学楼A302",
            "实验楼D区203",
            "图书馆B1-101",
        )) {
            assertNull(
                ClassroomLabel.previewHint(raw, rule),
                "「$raw」在默认设置下仍被判定超宽，说明缩写或保尾部没生效",
            )
        }
    }

    @Test
    fun `预览结论带上该动哪个开关`() {
        // 用一个确实超宽的样本：默认容量 7 下，「博学楼A302」10 半角放不下
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO)
        val raw = "博学楼（新）A302"
        // 不给开关名：只讲后果，用于自定义规则弹窗（那层看不到开关）
        val plain = ClassroomLabel.previewHint(raw, rule)
        assertNotNull(plain)
        assertFalse(plain!!.contains("打开"), "没给开关名时不应指路：$plain")
        // 给了开关名：直接说该打开哪一个
        val actionable = ClassroomLabel.previewHint(raw, rule, tailKeepActionLabel = "太长的地点保留后半段")
        assertNotNull(actionable)
        assertTrue(
            actionable!!.contains("太长的地点保留后半段"),
            "给了开关名就该指路，实际：$actionable",
        )
    }

    @Test
    fun `提示词带上用户自己的地点`() {
        val prompt = ClassroomLabel.abbreviationPrompt(
            listOf("第五教学楼C509", "南校区第五教学楼C509", "新校区J02西414"),
        )
        // 用户自己的地点要出现在提示词里，否则 AI 无从归纳
        assertTrue(prompt.contains("第五教学楼C509"))
        assertTrue(prompt.contains("南校区第五教学楼C509"))
        // 重复地点只出现一次，省 token
        assertEquals(1, prompt.split("南校区第五教学楼C509").size - 1)
        // 输出格式说清楚，且举了例子
        assertTrue(prompt.contains("原名=缩写") || prompt.contains("楼名=缩写"))
        assertTrue(prompt.contains("第五教学楼=五教"))
    }

    @Test
    fun `提示词要求同一栋楼只写一条`() {
        // 缩写表匹配的是楼名这个词，换房号照样能用；若让 AI 按整串地点输出，
        // 就得每个房号各写一条，用户改一次房号就失效
        val prompt = ClassroomLabel.abbreviationPrompt(listOf("第五教学楼C509", "第五教学楼A302"))
        assertTrue(prompt.contains("同一栋楼只写一条"), "提示词没交代去重要求")
        assertTrue(prompt.contains("不要按房号重复"), "提示词没交代不要按房号重复")
        assertTrue(prompt.contains("同一种写法"), "提示词没交代同一栋楼写法要统一")
    }

    @Test
    fun `提示词对空课表也要能生成`() {
        val prompt = ClassroomLabel.abbreviationPrompt(emptyList())
        assertTrue(prompt.isNotBlank(), "空课表也要给出一份可用的提示词，而不是空串")
    }

    @Test
    fun `AI 回包里混进序号与解释句时只留能用的行`() {
        val reply = """
            好的，我帮你整理如下：
            1. 第五教学楼=五教
            - 综合楼=综
            * 图书馆=图
            以上缩写都比较直观。
            3、实验楼=实
        """.trimIndent()
        assertEquals(
            listOf("第五教学楼=五教", "综合楼=综", "图书馆=图", "实验楼=实"),
            ClassroomLabel.sanitizeAbbreviationReply(reply).lines(),
        )
    }

    @Test
    fun `AI 回包清洗后能直接用于显示`() {
        val reply = "1. 第五教学楼=五教\n解释：这样更短"
        val cleaned = ClassroomLabel.sanitizeAbbreviationReply(reply)
        val rule = ClassroomLabel.rule(mode = ClassroomLabel.MODE_AUTO, abbreviations = cleaned)
        assertEquals("五教C509", ClassroomLabel.apply("第五教学楼C509", rule))
    }

    @Test
    fun `把提示词本身粘回来时不能解析出垃圾规则`() {
        // 真实误操作：用户没发给 AI，直接把提示词复制进输入框。
        // 提示词正文里有「格式为「原地点里的楼名=缩写」」这种带等号的句子，
        // 不设长度上限就会被当成一条规则（原型上真出现过「已识别 4 条」而实际只有 3 条示例）
        val prompt = ClassroomLabel.abbreviationPrompt(listOf("第五教学楼C509", "2号综合楼B3027"))
        val parsed = ClassroomLabel.parseAbbreviations(prompt)
        // 提示词里只有示例那几条是合法的，不该多出正文里的句子
        for ((from, _) in parsed) {
            assertTrue(
                from.length <= ClassroomLabel.MAX_ABBREV_ORIGIN,
                "解析出了不像楼名的规则：「$from」",
            )
        }
        assertTrue(parsed.all { it.first == "第五教学楼" || it.first == "综合楼" || it.first == "图书馆" },
            "提示词里只应解析出那 3 条示例，实际：$parsed")
    }
}
