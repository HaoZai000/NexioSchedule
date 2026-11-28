package com.haooz.chedule.data

import com.haooz.chedule.data.model.ClassroomLabel
import com.haooz.chedule.data.model.ClassroomLabelRule
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [com.haooz.chedule.data.model.ClassroomLabel] 的行为锁定。
 *
 * 两组真实反馈串是重点：`2号综合楼B3027` 被系统截成 `2号综合楼B3`（issue #69），
 * `新校区J02西414` 被截成 `新校区J02西4`（前缀把房号挤出去了）。
 *
 * 另外固化一条设计红线：精简只做格式清理与缩写，房号与楼栋永远不由程序删；
 * 缩写表覆盖不到的楼名原样保留。`超宽也不丢字样` 钉的就是这条，不能省。
 */
class ClassroomLabelTest {

    private val regexRule = ClassroomLabelRule(enabled = true, method = ClassroomLabel.METHOD_REGEX)

    private fun ai(abbreviations: String) = ClassroomLabelRule(
        enabled = true,
        method = ClassroomLabel.METHOD_AI,
        abbreviations = abbreviations,
    )

    private fun shorten(raw: String): String = ClassroomLabel.apply(raw, regexRule)

    @Test
    fun `关闭时与原文逐字相同`() {
        // 升级后的老用户没有这份偏好，行为必须一点不变
        val rule = ClassroomLabelRule()
        for (raw in listOf("新校区J02西414", "第五教学楼C509", "2号综合楼B3027")) {
            assertEquals(raw, ClassroomLabel.apply(raw, rule))
        }
    }

    @Test
    fun `空串与空白串原样返回`() {
        assertEquals("", shorten(""))
        assertEquals("   ", shorten("   "))
    }

    @Test
    fun `剥掉校区前缀且房号完整`() {
        // 前缀 6 个半角去掉后，楼栋 J02 与房号西414 全在
        assertEquals("J02西414", shorten("新校区J02西414"))
    }

    @Test
    fun `把常见楼名缩成成词的形式`() {
        // 不做缩写的话只能靠宽度裁，会切出「学楼」「合楼」这种半个词
        assertEquals("五教C509", shorten("第五教学楼C509"))
        assertEquals("五教C509", shorten("南校区第五教学楼C509"))
        assertEquals("二教A302", shorten("东校区第二教学楼A302"))
        assertEquals("图B1-101", shorten("图书馆B1-101"))
        assertEquals("实D区203", shorten("实验楼D区203"))
    }

    @Test
    fun `缩写不能把长楼名吃成半截`() {
        // 「第五教学楼」必须排在「教学楼」前面，否则会变成不合语感的「第五教」
        assertEquals("五教C509", shorten("第五教学楼C509"))
        assertEquals("十教C509", shorten("第十教学楼C509"))
        // 已经缩到「二教」的输入不能再被动一次
        assertEquals("二教A302", shorten("二教A302"))
    }

    @Test
    fun `缩写要认得没带序号的教学楼`() {
        assertEquals("教A302", shorten("教学楼A302"))
    }

    @Test
    fun `摘掉括号注解`() {
        // 「（新）」「(东)」这类注解是补充说明，留着占缩略态的宽度
        assertEquals("博学楼A302", shorten("博学楼（新）A302"))
    }

    @Test
    fun `超宽也不丢字样`() {
        // 缩写表覆盖不到的楼名保持原样，不按宽度丢字
        assertEquals("博学楼A302", shorten("博学楼（新）A302"))
        assertEquals("2号综B3027", shorten("2号综合楼B3027"))
    }

    @Test
    fun `支持括号与分隔符包裹的前缀`() {
        assertEquals("J02西414", shorten("（新校区）J02西414"))
        assertEquals("博学楼A302", shorten("[南校区]博学楼A302"))
        assertEquals("博学楼A302", shorten("东校区-博学楼A302"))
        assertEquals("博学楼A302", shorten("新校区·博学楼A302"))
    }

    @Test
    fun `整串就是一个校区名时保留原文`() {
        // 剥完剩下空串没有意义，退回原文而不是显示空白
        assertEquals("新校区", shorten("新校区"))
        assertEquals("南校区", shorten("南校区"))
    }

    @Test
    fun `全角字母数字转半角`() {
        assertEquals("B3027", shorten("Ｂ３０２７"))
    }

    @Test
    fun `丢掉空白`() {
        assertEquals("博A201", shorten("  博  A201 "))
        assertEquals("3号楼201", shorten("3 号 楼 201"))
    }

    @Test
    fun `不动本来就放得下的地点`() {
        assertEquals("博A201", shorten("博A201"))
        assertEquals("教三201", shorten("教三201"))
    }

    @Test
    fun `去掉房间号后面的用途说明`() {
        assertEquals("安实3-102", shorten("安实3-102-茶叶加工实验室"))
        assertEquals("安503", shorten("安503-语音室"))
        assertEquals("安实3-406", shorten("安实3-406-茶叶审评实验室"))
    }

    @Test
    fun `纯文字地名不会被当成用途说明砍掉`() {
        // 整串里没有任何带数字的段时不动手，否则「东区-教学楼」会被削成「东区」。
        // 楼名缩写照旧生效（教学楼→教），这里钉的是「整段没被丢」
        assertEquals("东区-教", shorten("东区-教学楼"))
    }

    @Test
    fun `没有连字符的地点原样保留`() {
        assertEquals("J02西414", shorten("J02西414"))
        assertEquals("博105", shorten("博105"))
    }

    @Test
    fun `作用范围只精简地点时课程名不动`() {
        val rule = regexRule.copy(scope = ClassroomLabel.SCOPE_CLASSROOM)
        assertEquals("五教C509", ClassroomLabel.apply("第五教学楼C509", rule))
        assertEquals("大学英语", ClassroomLabel.applyCourse("大学英语", rule))
    }

    @Test
    fun `作用范围只精简课程时地点不动`() {
        val rule = regexRule.copy(scope = ClassroomLabel.SCOPE_COURSE)
        assertEquals("第五教学楼C509", ClassroomLabel.apply("第五教学楼C509", rule))
        assertEquals("马克思原理", ClassroomLabel.applyCourse("马克思主义基本原理", rule))
    }

    @Test
    fun `作用范围两者都精简`() {
        val rule = regexRule.copy(scope = ClassroomLabel.SCOPE_BOTH)
        assertEquals("五教C509", ClassroomLabel.apply("第五教学楼C509", rule))
        assertEquals("马克思原理", ClassroomLabel.applyCourse("马克思主义基本原理", rule))
    }

    @Test
    fun `作用范围原始文本时两边都不动`() {
        val rule = regexRule.copy(enabled = false, scope = ClassroomLabel.SCOPE_OFF)
        assertEquals("第五教学楼C509", ClassroomLabel.apply("第五教学楼C509", rule))
        assertEquals("大学英语（二）", ClassroomLabel.applyCourse("大学英语（二）", rule))
    }

    @Test
    fun `存过范围后以范围为准`() {
        val store = InMemoryKeyValueStore(
            mapOf(ClassroomLabel.KEY_SCOPE to ClassroomLabel.SCOPE_COURSE)
        )
        val rule = ClassroomLabel.read(store)
        assertEquals(ClassroomLabel.SCOPE_COURSE, rule.scope)
        assertTrue(rule.shrinksCourse)
        assertFalse(rule.shrinksClassroom)
    }

    @Test
    fun `课程名先摘括号注解再判断长短`() {
        // 括号里的（必修）（二）这类是修饰，不是课程名的一部分
        assertEquals("大学英语", ClassroomLabel.applyCourse("大学英语（二）", regexRule))
        assertEquals("高等数学", ClassroomLabel.applyCourse("高等数学(必修)", regexRule))
    }

    @Test
    fun `真实课表里的长课程名都能压到 6 字以内`() {
        // 语料来自各校公开的课程设置：公共必修 + 专业基础课
        val cases = listOf(
            "思想道德与法治" to "思想道德法治",
            "中国近现代史纲要" to "中国近现代史",
            "马克思主义基本原理" to "马克思原理",
            "毛泽东思想和中国特色社会主义理论体系概论" to "毛泽东思想概论",
            "概率论与数理统计" to "概率与数统",
            "大学生心理健康教育" to "心理健康",
            "职业生涯与发展规划" to "职业生涯规划",
            "就业与创业指导" to "就业创业指导",
            "大学计算机基础" to "大学计算机",
            "数据结构与算法" to "数据结构算法",
            "液压与气动技术" to "液压气动",
            "单片机应用技术" to "单片机应用",
            "工业机器人基础" to "工业机器人",
            "心理健康教育概论" to "心理健康教育",
            "中外音乐简史与作品鉴赏" to "中外音乐简史",
            "心理统计软件与数据分析" to "心理统计数据",
            "大学生生涯发展与就业指导" to "大学生就业指导",
        )
        for ((raw, expected) in cases) {
            assertEquals(expected, ClassroomLabel.applyCourse(raw, regexRule), raw)
        }
    }

    @Test
    fun `够短的课程名一个都不动`() {
        // 6 字以内不进入任何一档规则，避免「统计学→统计」这种无谓删减
        listOf(
            "统计学", "管理学原理", "高等数学", "线性代数", "大学英语",
            "计算机网络", "离散数学", "大学物理", "团体心理辅导", "艺术概论",
        ).forEach { name ->
            assertEquals(name, ClassroomLabel.applyCourse(name, regexRule), name)
        }
    }

    @Test
    fun `没有命中任何规则的长课程名原样保留`() {
        // 两档规则都不匹配就一个字不动，交给 AI 那条路
        assertEquals("普通生物学实验", ClassroomLabel.applyCourse("普通生物学实验", regexRule))
    }

    @Test
    fun `课程名走 AI 对照表`() {
        assertEquals(
            "茶检",
            ClassroomLabel.applyCourse("茶叶审评与检验", ai("茶叶审评与检验=茶检")),
        )
    }

    @Test
    fun `AI 方式按对照表改写`() {
        // 「明德楼」不在内置表里，靠 AI 给的这条才压得下来
        assertEquals("明德A302", ClassroomLabel.apply("明德楼A302", ai("明德楼=明德")))
    }

    @Test
    fun `AI 方式不跑正则那一套`() {
        // 这是刻意分开的：AI 看着课表原文归纳，键也是原文片段。若先剥前缀、缩楼名，
        // AI 的键就对不上，表现为「AI 精简填了却没反应」。
        val raw = "新校区第五教学楼C509"
        // 没有对照表时一个字都不动（不剥前缀、不缩楼名）
        assertEquals(raw, ClassroomLabel.apply(raw, ai("")))
        // 有对照表时只替换命中部分，前缀照旧留着
        assertEquals(
            "新校区五教C509",
            ClassroomLabel.apply(raw, ai("第五教学楼=五教")),
        )
    }

    @Test
    fun `AI 方式的键按课表原文写`() {
        // 键要带前缀才匹配得上（因为不剥前缀）；写成剥过的形态反而不生效
        assertEquals(
            "J02西414",
            ClassroomLabel.apply("新校区J02西414", ai("新校区J02西=J02西")),
        )
        assertEquals(
            "新校区J02西414",
            ClassroomLabel.apply("新校区J02西414", ai("J02西=J02西")),
        )
    }

    @Test
    fun `正则方式不套用 AI 对照表`() {
        // 选了正则方式时，AI 留下的对照表不该偷偷生效
        val rule = regexRule.copy(abbreviations = "明德楼=明德")
        assertEquals("明德楼A302", ClassroomLabel.apply("明德楼A302", rule))
    }

    @Test
    fun `两种方式都关闭时完全不动`() {
        for (method in listOf(ClassroomLabel.METHOD_REGEX, ClassroomLabel.METHOD_AI)) {
            val rule = ClassroomLabelRule(
                enabled = false,
                method = method,
                abbreviations = "第五教学楼=五教",
            )
            assertEquals("新校区第五教学楼C509", ClassroomLabel.apply("新校区第五教学楼C509", rule))
        }
    }

    @Test
    fun `解析缩写文本`() {
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
    fun `读取偏好默认关闭`() {
        assertEquals(ClassroomLabelRule(), ClassroomLabel.read(InMemoryKeyValueStore()))
    }

    @Test
    fun `读出来的规则直接可用`() {
        // 回归用：设置页开了开关、选了 AI 方式后，通知链路读出来的规则必须真的生效。
        // 曾经 Android 侧手抄字段漏掉键，界面改了通知没反应，而编译期没有任何提示。
        val store = InMemoryKeyValueStore(
            mapOf(
                ClassroomLabel.KEY_SCOPE to ClassroomLabel.SCOPE_BOTH,
                ClassroomLabel.KEY_METHOD to ClassroomLabel.METHOD_AI,
                ClassroomLabel.KEY_ABBREVIATIONS to "明德楼=明德",
            ),
        )
        assertEquals("明德A302", ClassroomLabel.apply("明德楼A302", ClassroomLabel.read(store)))
    }

    // ── 以下用例来自 2026-10 的真实语料复跑（527 条真实课名 + 117 条真实教室名称） ──

    @Test
    fun `括号里是楼栋代号就留着`() {
        // 燕山大学把教室写成「(一)101」= 东校区第一教学楼 101。早期一律摘括号，
        // 摘完只剩「101」，楼栋没了，学生反而更找不到教室。
        assertEquals("(一)101", shorten("(一)101"))
        assertEquals("(二)305", shorten("(二)305"))
        assertEquals("西(一)301", shorten("西(一)301"))
        assertEquals("(J)阶梯一", shorten("(J)阶梯一"))
        assertEquals("(人)305", shorten("(人)305"))
    }

    @Test
    fun `括号里是房号就不丢`() {
        // 集美 / 南通理工的真实导出：序号括号该摘，房号括号必须留
        assertEquals("多媒体教室(汽-423)", shorten("多媒体教室(3)(汽-423)"))
        assertEquals("06A0203(集毅楼A)", shorten("06A0203(集毅楼A)"))
    }

    @Test
    fun `副楼名不压成半截`() {
        // 「专名前缀 + 通用后缀」的楼名：教研综合楼→教研综 这种半截词没有意义
        assertEquals("教研综合楼A座302", shorten("教研综合楼A座302"))
        assertEquals("化工实验楼302", shorten("化工实验楼302"))
        // 前面是数字+号或者串首时照旧缩
        assertEquals("2号综B3027", shorten("2号综合楼B3027"))
    }

    @Test
    fun `X号教学楼写成X号楼`() {
        // 「4号教B114」不成词；学生平时就说「4号楼B114」
        assertEquals("4号楼B114", shorten("东校区4号教学楼B114教室"))
        assertEquals("2号楼101", shorten("东校区2号教学楼101教室"))
        assertEquals("11号楼A区C104", shorten("西校区11号教学楼A区C104教室"))
    }

    @Test
    fun `课程名不动空格与被改成半角的全角标点`() {
        // 早期课程名走的是地点那套清理：丢空白会把 VisualBasic 拼成一坨，
        // 全角转半角会把「，」改成「,」，显示出来和课表原文对不上
        assertEquals("Visual Basic 程序设计", shortenCourse("Visual Basic 程序设计（海南联盟）(网络公选)"))
        assertEquals("看美剧，学口语", shortenCourse("看美剧，学口语(网络公选)"))
        assertEquals("校园英语，我的大学", shortenCourse("校园英语，我的大学(网络公选)"))
    }

    @Test
    fun `零基础与基本功里的字不能当修饰词删`() {
        // 「零基础日语听说」删「基础」剩下「零日语」，「中国舞基本功训练」删「基本」剩下「中国舞功训练」
        assertEquals("零基础日语听说", shortenCourse("零基础日语听说"))
        assertEquals("中国舞基本功训练", shortenCourse("中国舞基本功训练"))
    }

    @Test
    fun `课程名按显示宽度而不是字数卡阈值`() {
        // 6 个汉字 = 12 个半角宽；「Python语言程序设计」正好 16 宽，要压
        assertEquals("Python语言程序设计", shortenCourse("Python语言程序设计基础"))
        // 纯 ASCII 的「大学英语A1」= 10 宽，不动
        assertEquals("大学英语A1", shortenCourse("大学英语A1"))
    }

    @Test
    fun `AI 方式在替换之后仍会摘括号注解`() {
        // 实测线上模型会把「课名 + 两个连续括号」整条跳过（60 条漏 18 条）。
        // 替换必须排在清理之前（键是原文片段），清理放在后面兜底。
        val raw = "中药药剂学(江西中医药大学)(网络公选)"
        assertEquals("中药药剂学", ClassroomLabel.applyCourse(raw, ai("")))
        assertEquals(
            "中药药剂学",
            ClassroomLabel.applyCourse(raw, ai("中药药剂学(江西中医药大学)(网络公选)=中药药剂学")),
        )
    }

    @Test
    fun `长课名的缩写条目不会被长度上限丢掉`() {
        // 卡在 16 字会把 18 字的真实课名静默扔掉，界面上表现为「AI 说精简了，实际没变」
        val long = "从古至今话廉洁——大学生廉洁素养教育"
        val rule = ai("$long=廉洁素养")
        assertEquals("廉洁素养", ClassroomLabel.applyCourse(long, rule))
        assertEquals(1, ClassroomLabel.parseAbbreviations("$long=廉洁素养").size)
    }

    @Test
    fun `只删空格或连字符的条目被忽略`() {
        // 真实导出里 A-103 这种很多，模型顺手去掉连字符，省一个半角却和原文对不上
        assertTrue(ClassroomLabel.parseAbbreviations("A-103=A103").isEmpty())
        assertTrue(ClassroomLabel.parseAbbreviations("学生服务中心-320=学生服务中心320").isEmpty())
        // 真变短的照旧收下
        assertEquals(
            listOf("学生服务中心-320" to "学服320"),
            ClassroomLabel.parseAbbreviations("学生服务中心-320=学服320"),
        )
    }

    // ── 手动改写（精简预览里点右侧文字改出来的那几张） ──

    @Test
    fun `手动改写压过自动结果`() {
        // 用户亲手写的那一条，就得逐字照他写的显示，而不是自动规则算出来的
        val rule = regexRule.copy(overrides = "第五教学楼C509=五教509")
        assertEquals("五教509", ClassroomLabel.apply("第五教学楼C509", rule))
    }

    @Test
    fun `手动改写只认整串相等而不是片段`() {
        // 键是「这一条原文」，不是「片段」—— 写「第五教学楼=五教」不该去动别的行
        val rule = regexRule.copy(overrides = "第五教学楼=五教")
        assertEquals("五教", ClassroomLabel.apply("第五教学楼", rule))
        assertEquals("五教C509", ClassroomLabel.apply("第五教学楼C509", rule))
    }

    @Test
    fun `手动改写不受作用范围限制`() {
        // 范围选了「只精简地点」，课程名那几行在预览里照样能改 —— 改完必须生效
        val rule = ClassroomLabelRule(
            enabled = true,
            method = ClassroomLabel.METHOD_REGEX,
            scope = ClassroomLabel.SCOPE_CLASSROOM,
            overrides = "高等数学（1）=高数",
        )
        assertEquals("高数", ClassroomLabel.applyCourse("高等数学（1）", rule))
        // 没被手动改过的课程名仍然按范围走原文
        assertEquals("大学物理", ClassroomLabel.applyCourse("大学物理", rule))
    }

    @Test
    fun `关闭精简时手动改写也不生效`() {
        // 整块关掉了就该是课表原文，不然「关掉」这个动作看起来坏了
        val rule = ClassroomLabelRule(enabled = false, overrides = "第五教学楼C509=五教509")
        assertEquals("第五教学楼C509", ClassroomLabel.apply("第五教学楼C509", rule))
    }

    @Test
    fun `课名里的冒号不会被当成分隔符`() {
        // 「创业管理:创业者的十八般武艺」按冒号切，键就成了半截，那条改写永远命中不了
        val raw = "创业管理:创业者的十八般武艺"
        val rule = regexRule.copy(overrides = "$raw=创业管理")
        assertEquals("创业管理", ClassroomLabel.applyCourse(raw, rule))
    }

    @Test
    fun `改写表能增删覆盖一条`() {
        val one = ClassroomLabel.putOverride("", "第五教学楼C509", "五教509")
        assertEquals("第五教学楼C509=五教509", one)
        val two = ClassroomLabel.putOverride(one, "图书馆B1-101", "图101")
        assertEquals("第五教学楼C509=五教509\n图书馆B1-101=图101", two)
        // 空值（清空输入）= 回到自动结果
        assertEquals("图书馆B1-101=图101", ClassroomLabel.putOverride(two, "第五教学楼C509", ""))
        // 改同一条是覆盖，不会多出一行
        assertEquals("第五教学楼C509=五教", ClassroomLabel.putOverride(one, "第五教学楼C509", "五教"))
    }

    @Test
    fun `从偏好读出手动改写`() {
        // 设置页里改完写偏好，发通知的 Receiver 是另起进程读的，必须读得回来
        val store = InMemoryKeyValueStore(
            mapOf(
                ClassroomLabel.KEY_SCOPE to ClassroomLabel.SCOPE_BOTH,
                ClassroomLabel.KEY_METHOD to ClassroomLabel.METHOD_REGEX,
                ClassroomLabel.KEY_OVERRIDES to "第五教学楼C509=五教509",
            ),
        )
        assertEquals("五教509", ClassroomLabel.apply("第五教学楼C509", ClassroomLabel.read(store)))
    }

    private fun shortenCourse(raw: String): String = ClassroomLabel.applyCourse(raw, regexRule)
}
