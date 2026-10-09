/**
 * 应用内开源协议全文页（内容与官网 https://nexioschedule.icu/license.html 保持一致）。
 *
 * AGPL-3.0 全文放在 assets/LICENSE，由本文件运行时解析成块结构渲染，
 * 避免在源码里硬编码三万多字符。
 */
package com.haooz.chedule.ui.activities

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import com.haooz.chedule.ui.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val LICENSE_ASSET = "LICENSE"

/**
 * 条款正文的一个块。与隐私政策页共用这套模型。
 */
sealed interface PolicyBlock {
    data class Para(val text: String) : PolicyBlock
    /** 小节标题（加粗） */
    data class Sub(val text: String) : PolicyBlock
    data class Bullets(val items: List<String>) : PolicyBlock
    data class Note(val text: String) : PolicyBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : PolicyBlock

    /** 等宽预格式化文本块（AGPL 官方模板块需保留原始换行与缩进） */
    data class Code(val text: String) : PolicyBlock
}

/** 一个可独立成卡片渲染的章节 */
data class PolicySection(val title: String, val blocks: List<PolicyBlock>)

// LICENSE 行号分区（0-based）
private const val IDX_PREAMBLE_BODY = 9    // 第 7 行是 "Preamble" 居中标题，第 9 行起为前言正文
private const val IDX_TERMS_HEAD = 58      // TERMS AND CONDITIONS
private const val IDX_S0 = 60              // "  0. Definitions."
private const val IDX_END = 618            // END OF TERMS AND CONDITIONS
private const val IDX_APPLY = 620          // How to Apply These Terms...

/** 中文摘要：与官网 license.html 第一节保持一致 */
private val LICENSE_SUMMARY = PolicySection(
    title = "一、开源协议摘要",
    blocks = listOf(
        PolicyBlock.Para("本应用采用 GNU Affero General Public License v3.0（AGPL-3.0）开源。你可以自由使用、学习、修改，甚至用于商业目的；但如果你把本应用或其修改版通过网络提供给他人使用，就必须同时向这些用户提供完整的对应源代码。"),
        PolicyBlock.Note("这是 AGPL 与 MIT 最主要的区别：MIT 只要求「分发二进制时附带许可证」。AGPL 多了一条 —— 只要通过网络向用户提供服务，就必须开放源码。如果你只是个人下载安装自用，AGPL 不会向你提出任何要求。"),
        PolicyBlock.Sub("你可以做什么"),
        PolicyBlock.Bullets(
            listOf(
                "自由使用：个人学习、备份、多设备安装、非商业目的使用，无需申请。",
                "研究源码：本应用全部代码开源，可自由查看、理解其实现方式。",
                "二次分发：可以重新发布原版或修改版，但必须保留本协议声明与版权信息。",
                "商用：允许收费销售软件、基于它提供收费服务，AGPL 并不禁止商业使用。",
                "修改与再创作：可以 fork 并自由修改，功能、配色、课表逻辑都可以换成你自己的。",
            )
        ),
        PolicyBlock.Sub("你需要做什么"),
        PolicyBlock.Bullets(
            listOf(
                "保留声明：再分发时必须保留原版权声明与本协议副本。",
                "标明修改：修改过原版后，需在作品中注明你做过改动及改动内容。",
                "开放源码：把修改版提供给他人（包括通过网络提供服务）时，需同时提供完整对应源码。",
                "同等协议：衍生作品须以本协议继续授权（第 13 条允许改用 GPL v3）。",
                "不作担保：软件按「现状」提供，作者不承担使用后果（第 15、16 条）。",
            )
        ),
        PolicyBlock.Sub("如果你要二次开发"),
        PolicyBlock.Para("建议在 fork 仓库的 README 中说明：项目名称、基于 Nexio 课程表修改、源码获取方式。如果你希望用户能直接看到源码链接，可以在应用内放一个指向你 fork 仓库的入口 —— AGPL 第 13 条正是为此设计的。"),
        PolicyBlock.Para("官方在协议第 13 条给出的建议做法是：在应用界面中提供一个「Source」链接，指向一份代码归档；对于桌面应用，通常指向公开的代码仓库即可。"),
        PolicyBlock.Note("免责声明：本摘要为便于阅读的通俗说明，不构成法律意见，也不能替代协议原文。摘要与英文原文表述不一致时，以「四、条款正文」的英文原文为准。如需确认某项使用方式是否合规，建议咨询专业法律人士。"),
    )
)

/** 读取并解析协议全文（IO 线程） */
private suspend fun loadLicenseSections(context: Context): List<PolicySection> =
    withContext(Dispatchers.IO) {
        val lines = runCatching {
            context.assets.open(LICENSE_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.split("\n") ?: return@withContext listOf(LICENSE_SUMMARY)

        val idx = { i: Int -> i.coerceIn(0, lines.size) }
        listOf(
            LICENSE_SUMMARY,
            PolicySection(
                title = "二、版权声明",
                blocks = listOf(
                    PolicyBlock.Para("GNU Affero General Public License — Version 3, 19 November 2007"),
                    PolicyBlock.Para("Copyright (C) 2007 Free Software Foundation, Inc. Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed.")
                )
            ),
            PolicySection(
                title = "三、前言 Preamble",
                blocks = renderLicenseBlocks(lines.subList(idx(IDX_PREAMBLE_BODY), idx(IDX_TERMS_HEAD)))
            ),
            PolicySection(
                title = "四、条款正文 Terms and Conditions",
                blocks = buildList {
                    add(
                        PolicyBlock.Note("以下为 AGPL-3.0 完整条款原文（第 0 条至第 17 条），逐字摘自官方文本，未作任何删改。如中文摘要与本节英文原文表述不一致，以英文原文为准。")
                    )
                    addAll(renderLicenseBlocks(lines.subList(idx(IDX_S0), idx(IDX_END))))
                }
            ),
            PolicySection(
                title = "五、如何将本协议应用到您的新程序",
                blocks = listOf(PolicyBlock.Sub("How to Apply These Terms to Your New Programs")) +
                        renderLicenseBlocks(lines.subList(idx(IDX_APPLY + 1), lines.size), codeMode = true)
            )
        )
    }

/**
 * 把 AGPL 官方模板块里的尖括号占位符替换为本项目实际信息，
 * 与官网 license.html 的处理保持一致。其余内容逐字保留。
 */
private fun fillLicensePlaceholders(text: String): String = text
    .replace(
        "<one line to give the program's name and a brief idea of what it does.>",
        "Nexio 课程表 - 基于 Jetpack Compose 的 Android 课程表应用"
    )
    .replace("<year>", "2026")
    .replace("<name of author>", "Nexio 课程表开发团队")

/**
 * 把 AGPL 原文行序列渲染成块。
 *
 * 标题判定有两处必须收紧，否则会误判：
 * 1. 条款标题在官方原文中缩进恰好是 2 空格，而正文第 5 条里存在
 *    "    7. This requirement modifies..." 这样 4 空格缩进的续行，
 *    所以必须锁定 `^ {2}(\d+)\.`；
 * 2. 第 15、16 条的免责/责任声明正文是 2 空格缩进的全大写段落，
 *    仅靠 isUpperCase() 会被当成小标题；真实居中标题缩进均 >= 20。
 */
private fun renderLicenseBlocks(lines: List<String>, codeMode: Boolean = false): List<PolicyBlock> {
    val out = mutableListOf<PolicyBlock>()
    val para = mutableListOf<String>()
    val bullets = mutableListOf<String>()
    val code = mutableListOf<String>()

    fun flushPara() {
        if (para.isEmpty()) return
        out.add(PolicyBlock.Para(para.joinToString(" ") { it.trim() }))
        para.clear()
    }

    fun flushBullets() {
        if (bullets.isEmpty()) return
        out.add(PolicyBlock.Bullets(bullets.toList()))
        bullets.clear()
    }

    fun flushCode() {
        if (code.isEmpty()) return
        // 去掉统一的左侧缩进，保留相对排版
        val indent = code.filter { it.isNotBlank() }
            .minOfOrNull { it.takeWhile { c -> c == ' ' }.length } ?: 0
        val text = code.joinToString("\n") { it.drop(indent).trimEnd() }.trim('\n')
        out.add(PolicyBlock.Code(fillLicensePlaceholders(text)))
        code.clear()
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        val stripped = line.trim()

        if (stripped.isEmpty()) {
            flushPara(); flushBullets(); flushCode()
            continue
        }

        // 官方模板块：保留原始缩进与换行
        if (codeMode && line.startsWith("    ")) {
            flushPara(); flushBullets()
            code.add(line)
            continue
        }

        // 字母列表项 a) b) c) ...，以及其缩进续行
        val bullet = bulletMatch(stripped)
        if (bullet != null) {
            flushPara(); flushCode()
            bullets.add(bullet)
            continue
        }
        if (line.startsWith("      ") && bullets.isNotEmpty()) {
            bullets[bullets.lastIndex] = bullets.last() + " " + stripped
            continue
        }

        // 条款标题
        val head = Regex("^ {2}(\\d+)\\.\\s+(\\S.*)$").find(line)
        if (head != null) {
            flushPara(); flushBullets(); flushCode()
            out.add(PolicyBlock.Sub("${head.groupValues[1]}. ${head.groupValues[2].trim()}"))
            continue
        }

        // 居中全大写小标题
        val isUpper = stripped.all { it.isUpperCase() || it.isDigit() || it == ' ' || it == ',' }
        val indent = line.length - line.trimStart().length
        if (isUpper && indent >= 10 && stripped.length < 70) {
            flushPara(); flushBullets(); flushCode()
            out.add(PolicyBlock.Sub(stripped))
            continue
        }

        flushBullets(); flushCode()
        para.add(line)
    }

    flushPara(); flushBullets(); flushCode()
    return out
}

/** 匹配 "a) ..." 形式的列表项，返回正文；不匹配返回 null */
private fun bulletMatch(s: String): String? =
    Regex("^([a-f])\\)\\s+(.*)$").find(s)?.groupValues?.get(2)

@Composable
fun LicenseScreen(
    scrollBehavior: SharedScrollBehavior? = null,
) {
    val backdropColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
    }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    // 平板按屏宽在 20~128dp 间线性放大：600dp 屏=20dp，1200dp 及以上封顶 128dp
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 108 + 20).dp
    } else 16.dp

    // 协议全文从 assets 读取，避免在 Kotlin 里硬编码 3 万多字符
    val sections by produceState(initialValue = emptyList<PolicySection>(), context) {
        value = loadLicenseSections(context)
    }

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    )
                    .collapsibleTopInset(scrollBehavior)
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                            ?: Modifier
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    end = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + 12.dp,
                    bottom = 60.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "licenseHeader") {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, bottom = 8.dp)) {
                        Text(
                            text = "Nexio 课程表 开源协议",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "GNU Affero General Public License v3.0（AGPL-3.0）\n本项目源代码完全公开，欢迎阅读、学习与二次开发。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }

                items(sections, key = { it.title }) { section ->
                    PolicySectionCard(section)
                }

                item(key = "licenseFooter") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "AGPL-3.0 官方原文：gnu.org/licenses/agpl-3.0.html\nNexio 课程表",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
            }
        }
    }
}
