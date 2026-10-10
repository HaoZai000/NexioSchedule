/** 应用内隐私政策全文页（内容与官网 https://nexioschedule.icu/privacy.html 保持一致） */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val POLICY_SECTIONS: List<PolicySection> = listOf(
    PolicySection(
        title = "一、引言与适用范围",
        blocks = listOf(
            PolicyBlock.Para("感谢您使用 Nexio 课程表（以下简称「本应用」）。我们深知个人信息对您的重要性，并按照《中华人民共和国个人信息保护法》《中华人民共和国网络安全法》《移动互联网应用程序信息服务管理规定》等法律法规的要求，遵循合法、正当、必要和诚信原则处理您的信息。"),
            PolicyBlock.Para("本政策适用于您通过 Android 设备下载、安装、使用本应用及相关网站（nexioschedule.icu）的全部场景，用以说明我们收集哪些信息、为何收集、如何使用与保护，以及您对信息享有的权利。"),
            PolicyBlock.Note("特别说明：本应用是本地优先的课程表工具。您的课程表、节次时间、外观设置等核心数据默认仅保存在设备本地；本应用没有账号体系，不要求注册或登录，也不会收集可单独识别您个人身份的信息（如姓名、手机号、身份证号）。"),
        )
    ),
    PolicySection(
        title = "二、开发者信息与联系方式",
        blocks = listOf(
            PolicyBlock.Bullets(
                listOf(
                    "应用名称：Nexio 课程表",
                    "应用包名：com.haooz.chedule",
                    "开发者：Nexio 课程表开发团队（王凯哲）",
                    "联系邮箱：439089703@qq.com",
                    "官方网站：nexioschedule.icu（闽ICP备2026037367号-1）",
                )
            ),
            PolicyBlock.Para("如您对本政策或个人信息处理有任何疑问、意见或投诉，可通过上述邮箱与我们联系，我们将在收到后及时处理并回复。"),
        )
    ),
    PolicySection(
        title = "三、我们如何收集和使用信息",
        blocks = listOf(
            PolicyBlock.Sub("1. 设备与运行信息（自动收集）"),
            PolicyBlock.Para("为了统计安装量与活跃情况、向您下发应用公告、排查兼容性问题并改进产品体验，本应用会在您启动时向开发者自建服务器发送以下信息："),
            PolicyBlock.Bullets(
                listOf(
                    "随机设备标识：由本应用在本机随机生成的 UUID 并存储在本地，不含任何硬件唯一标识（我们不获取 IMEI、MAC 地址、Android ID、手机号等信息）；",
                    "设备与系统信息：设备型号、品牌、厂商、Android 系统版本、系统 API 等级；",
                    "应用信息：本应用版本号；",
                    "事件信息：安装事件、启动（活跃）事件及对应时间。",
                )
            ),
            PolicyBlock.Para("上述信息仅用于匿名化统计与运营，不用于广告推送，也不会与您的课程数据关联。我们未接入任何第三方商业统计、广告或崩溃采集 SDK。"),
            PolicyBlock.Sub("2. 位置信息（可选，用于天气）"),
            PolicyBlock.Para("本应用的「今日」页面提供天气展示。仅当您主动授权定位权限并使用天气功能时，我们才会获取您设备的大致/精确位置坐标，用于向天气服务商查询所在地天气；坐标仅用于该次天气查询，不会上传至开发者服务器用于其他用途。若您拒绝授权，天气卡片会提示需要定位，但不影响课程表等核心功能。您可随时在系统设置中关闭该权限。"),
            PolicyBlock.Sub("3. 课表与课程数据（本地存储）"),
            PolicyBlock.Para("您创建、导入或编辑的课程、节次时间、周次、外观配色等数据，默认全部存储在您的设备本地。我们不会主动上传或读取您的课表内容。"),
            PolicyBlock.Sub("4. 教务系统导入信息"),
            PolicyBlock.Para("当您使用「教务系统导入」时，本应用会在应用内浏览器中打开对应学校的教务系统页面，您输入的账号、密码等登录凭据仅用于当次登录与课程抓取，相关凭据由您所访问的学校教务系统处理，本应用不会将其上传至开发者服务器，也不作长期存储。"),
            PolicyBlock.Sub("5. 云同步（WebDAV，可选）"),
            PolicyBlock.Para("当您主动开启 WebDAV 云同步时，您的课表数据会被上传至您自行填写的 WebDAV 服务器（例如坚果云等第三方网盘）。该服务器地址与账号由您自行配置与控制，数据的存储与安全取决于您所选的第三方服务，我们无法访问您在该服务中的数据。"),
            PolicyBlock.Sub("6. 课表分享（可选）"),
            PolicyBlock.Para("当您使用「分享课表」生成分享口令时，被分享的课表数据会经开发者自建服务器临时中转，口令有效期为 30 分钟，到期后自动失效删除。该功能仅在您主动发起分享时使用，且仅包含课表课程与时间设置，不含设备标识等个人信息。"),
            PolicyBlock.Sub("7. 备份、导入与导出文件"),
            PolicyBlock.Para("当您使用本地备份、课表导出或导入功能时，相关文件会保存至您所选择的位置（如设备存储或系统分享目标）。这些文件由您自行保管，我们不会读取或上传。"),
            PolicyBlock.Sub("8. 我们不会收集的信息"),
            PolicyBlock.Bullets(
                listOf(
                    "姓名、手机号、身份证号等身份信息；",
                    "通讯录、短信、通话记录、相册照片与视频；",
                    "设备硬件唯一标识（IMEI、MAC 地址、Android ID 等）；",
                    "您在其他应用中的使用行为。",
                )
            ),
        )
    ),
    PolicySection(
        title = "四、应用权限清单及使用目的",
        blocks = listOf(
            PolicyBlock.Para("本应用仅在实现具体功能所必需的范围内申请系统权限。所有权限均在您同意本隐私政策之后、于您使用对应功能时按需申请；您可以随时在系统「设置 - 应用 - Nexio 课程表 - 权限」中查看、授权或撤销。"),
            PolicyBlock.Table(
                headers = listOf("权限", "使用目的", "是否必需"),
                rows = listOf(
                    listOf("INTERNET", "访问网络。用于教务导入、天气查询、检查更新、WebDAV 云同步、课表分享及匿名统计上报。", "必需"),
                    listOf("ACCESS_NETWORK_STATE", "读取网络连接状态，无网络时避免无效请求并给出提示。", "必需"),
                    listOf("POST_NOTIFICATIONS", "发送课前提醒、次日课程提醒、上课中等通知。", "可选"),
                    listOf("VIBRATE", "课程提醒通知的振动提示。", "可选"),
                    listOf("POST_PROMOTED_NOTIFICATIONS", "Android 16 及以上将「上课中」常驻提醒提升为实时更新通知展示；系统自动授予的普通权限，不涉及个人信息。", "可选"),
                    listOf("SCHEDULE_EXACT_ALARM", "按课程开始时间准时触发课前提醒；需在系统「闹钟和提醒」手动授权，未授权时自动降级为非精确提醒。", "可选"),
                    listOf("RECEIVE_BOOT_COMPLETED", "设备重启后重新注册已设置的课程提醒，避免重启后提醒失效。", "可选"),
                    listOf("ACCESS_NOTIFICATION_POLICY", "实现「上课勿扰」，上课期间自动开启系统勿扰；需在系统「免打扰访问权限」单独授权。", "可选"),
                    listOf("MODIFY_AUDIO_SETTINGS", "配合课程提醒通知的声音与静音相关设置。", "可选"),
                    listOf("ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION", "仅在开启天气功能并授权后获取当前位置查询天气；不使用天气功能则不申请、不获取。", "可选"),
                    listOf("FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE / WAKE_LOCK", "保障课程提醒触发与桌面小组件刷新在后台可靠运行。", "可选"),
                    listOf("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "引导你在系统设置中允许本应用后台运行，避免省电策略导致提醒被延迟或拦截；需你手动确认。", "可选"),
                    listOf("WRITE_EXTERNAL_STORAGE（仅 Android 9 及以下）", "将课表备份与导出文件写入设备存储；Android 10 及以上无需该权限。", "可选"),
                    listOf("REQUEST_INSTALL_PACKAGES", "仅当你主动检查并选择更新时，用于下载并安装新版本安装包。", "可选"),
                    listOf("Shizuku 授权（非系统权限）", "需你自行安装 Shizuku 并在其中授权，用于超级岛 / 灵动岛通知的进阶展示。", "可选"),
                )
            ),
            PolicyBlock.Para("标注为「可选」的权限，拒绝授权不影响课程表查看、课程管理等核心功能，仅会导致对应的附加功能不可用。"),
        )
    ),
    PolicySection(
        title = "五、第三方服务清单",
        blocks = listOf(
            PolicyBlock.Para("为实现特定功能，本应用在您使用相关功能时会与以下第三方服务通信。这些服务仅接收完成功能所必需的信息，各服务方对其信息处理行为负责，请同时参阅其各自的隐私政策。"),
            PolicyBlock.Table(
                headers = listOf("服务 / 提供方", "用途", "涉及的信息"),
                rows = listOf(
                    listOf("Nexio 服务端（开发者自建，nexioschedule.icu）", "匿名安装/活跃统计、应用公告下发、课表分享口令中转、赞赏榜展示", "随机设备标识、设备型号、品牌/厂商、系统版本、应用版本、事件时间"),
                    listOf("天气服务（彩云天气，经 v1.apizero.cn 接口）", "展示所在地天气", "经纬度坐标"),
                    listOf("天气服务（t.weather.itboy.net，中国天气网数据源）", "展示所在地天气（可切换数据源）", "定位解析得到的城市编码"),
                    listOf("GitHub / Gitee Releases 接口", "检查应用新版本", "无个人标识，仅请求版本信息"),
                    listOf("unpkg CDN（holiday-calendar 数据）", "获取节假日与调休数据", "无个人标识"),
                    listOf("星链课表（api.starlinkkb.cn）", "导入第三方课表分享", "您输入的分享码"),
                    listOf("WakeUp 课表（api.wakeup.fun）", "导入第三方课表分享", "您输入的分享码"),
                    listOf("拾光课表脚本仓库（Gitee）", "获取教务系统导入脚本", "无个人标识"),
                    listOf("系统定位与地理编码服务（操作系统）", "将定位坐标解析为城市名称", "经纬度坐标"),
                )
            ),
            PolicyBlock.Note("本应用未接入任何广告 SDK、第三方商业数据统计 SDK 或用户画像服务。发送至境外服务（如 GitHub、unpkg）的请求仅包含公开的版本或数据信息，不含您的个人信息或课表内容。"),
        )
    ),
    PolicySection(
        title = "六、信息的存储与保护",
        blocks = listOf(
            PolicyBlock.Bullets(
                listOf(
                    "存储地点：本应用收集的匿名统计信息存储于中华人民共和国境内的服务器（阿里云 ECS）中，不涉及个人信息出境。",
                    "存储期限：课表等核心数据存储于您的设备本地，由您自行控制；匿名统计信息在实现统计目的所必需的期限内保存，超出期限后将予以删除或匿名化处理；分享口令数据在 30 分钟有效期届满后自动删除。",
                    "安全措施：我们采用访问控制、传输与存储加密（在支持的链路上）等合理可行的安全措施保护信息安全，并限制内部人员对数据的访问。但请您理解，任何技术手段都无法保证绝对安全，请妥善保管您的设备与账户信息。",
                )
            ),
        )
    ),
    PolicySection(
        title = "七、信息的共享、转让与公开披露",
        blocks = listOf(
            PolicyBlock.Bullets(
                listOf(
                    "共享：除为实现本政策所述功能所必需（如天气查询向天气服务商发送坐标、云同步向您指定的 WebDAV 服务上传数据）外，我们不会向任何第三方共享您的信息。",
                    "转让：我们不会将您的信息转让给任何公司、组织或个人。",
                    "公开披露：我们不会公开披露您的信息。仅在法律法规要求、司法机关或行政机关依法提出要求时，我们才可能依法提供必要的信息。",
                )
            ),
        )
    ),
    PolicySection(
        title = "八、您的权利",
        blocks = listOf(
            PolicyBlock.Para("在个人信息处理活动中，您依法享有以下权利，我们为您提供了实现途径："),
            PolicyBlock.Bullets(
                listOf(
                    "查阅与更正：您的课程表等数据均可在本应用内直接查看、编辑和修改。",
                    "删除：您可在应用内删除课程、课表及本地备份数据；卸载本应用将删除设备本地存储的全部数据。",
                    "撤回授权：您可随时在系统「设置 - 应用 - Nexio 课程表 - 权限」中关闭定位、通知等权限，或在应用内关闭天气、云同步等功能；也可在「关于」页点击「撤回同意」。",
                    "注销与删除服务器数据：本应用无账号体系，无需注销账户。若您希望删除与您设备随机标识关联的匿名统计数据，或对信息处理有任何诉求，请发送邮件至 439089703@qq.com。",
                )
            ),
        )
    ),
    PolicySection(
        title = "九、未成年人保护",
        blocks = listOf(
            PolicyBlock.Para("本应用主要面向高校学生等用户群体。若您是未满 14 周岁的未成年人，请务必在您的父母或其他监护人的指导下阅读本政策并决定是否使用本应用；若您是未成年人的监护人，请在未成年人使用本应用前向其说明本政策内容。我们不会主动收集未成年人的个人信息，如发现相关情况，我们将及时删除。"),
        )
    ),
    PolicySection(
        title = "十、本政策的更新",
        blocks = listOf(
            PolicyBlock.Para("我们可能会根据法律法规变化或业务调整适时更新本政策。更新后，我们会在本页面发布最新版本并更新「更新日期」，重大变更还将通过应用内公告等显著方式另行提示。若您在政策更新后继续使用本应用，即表示您同意接受更新后的政策；若您不同意，请停止使用并卸载本应用。"),
        )
    ),
    PolicySection(
        title = "十一、联系我们",
        blocks = listOf(
            PolicyBlock.Para("如您对本政策有任何疑问、意见、建议或投诉，可通过以下方式与我们联系，我们将在收到后尽快处理："),
            PolicyBlock.Bullets(
                listOf(
                    "联系邮箱：439089703@qq.com",
                    "官方网站：nexioschedule.icu",
                    "开源仓库：github.com/HaoZai000/NexioSchedule",
                )
            ),
        )
    ),
)

@Composable
fun PrivacyPolicyScreen(
    scrollBehavior: SharedScrollBehavior? = null,
) {
    val backdropColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropColor)
        drawContent()
    }

    val listState = rememberLazyListState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    // 平板按屏宽在 20~128dp 间线性放大：600dp 屏=20dp，1200dp 及以上封顶 128dp
    val tabletHorizontalPadding = if (isTablet) {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        ((screenWidthDp - 600).coerceIn(0, 600) / 600f * 108 + 20).dp
    } else 16.dp

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
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, bottom = 8.dp)) {
                        Text(
                            text = "Nexio 课程表 隐私政策",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "生效日期：2026 年 10 月 2 日\n更新日期：2026 年 10 月 2 日",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }

                items(POLICY_SECTIONS, key = { it.title }) { section ->
                    PolicySectionCard(section)
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "本政策自 2026 年 10 月 2 日起生效 · Nexio 课程表",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PolicySectionCard(section: PolicySection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)) {
            Text(
                text = section.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(17.dp))
            section.blocks.forEach { block ->
                PolicyBlockView(block)
            }
        }
    }
}

@Composable
internal fun PolicyBlockView(block: PolicyBlock) {
    when (block) {
        is PolicyBlock.Para -> {
            Text(
                text = block.text,
                fontSize = 14.sp,
                lineHeight = 23.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        is PolicyBlock.Sub -> {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = block.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        is PolicyBlock.Bullets -> {
            block.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(5.dp)
                            .height(5.dp)
                            .background(
                                MiuixTheme.colorScheme.primary,
                                ContinuousRoundedRectangle(2.5f.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        lineHeight = 23.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        is PolicyBlock.Note -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.08f),
                        com.kyant.capsule.ContinuousRoundedRectangle(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = block.text,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        is PolicyBlock.Table -> {
            PolicyTable(block.headers, block.rows)
        }

        is PolicyBlock.Code -> {
            Text(
                text = block.text,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.08f),
                        com.kyant.capsule.ContinuousRoundedRectangle(12.dp)
                    )
                    .padding(14.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PolicyTable(headers: List<String>, rows: List<List<String>>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                // 第一列：权限 / 服务名
                Text(
                    text = row.getOrElse(0) { "" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 第二列：用途说明
                Text(
                    text = row.getOrElse(1) { "" },
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                // 第三列：必需 / 可选（权限表）
                val third = row.getOrElse(2) { "" }
                if (third == "必需" || third == "可选") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = third,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (third == "必需") MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
            if (index < rows.lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.12f))
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}