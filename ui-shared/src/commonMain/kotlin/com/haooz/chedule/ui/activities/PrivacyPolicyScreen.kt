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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.i18n.L
import com.haooz.chedule.i18n.L10n
import com.haooz.chedule.ui.components.text.miuix.Text as Text
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 权限表第三列的「必需 / 可选」标签：数据与 PolicyTable 的着色判断复用同一实例
private val TAG_REQUIRED = L("必需", "Required")
private val TAG_OPTIONAL = L("可选", "Optional")

private val POLICY_SECTIONS: List<PolicySection> = listOf(
    PolicySection(
        title = L("一、引言与适用范围", "1. Introduction and Scope of Application"),
        blocks = listOf(
            PolicyBlock.Para(
                L(
                    "感谢您使用 Nexio 课程表（以下简称「本应用」）。我们深知个人信息对您的重要性，并按照《中华人民共和国个人信息保护法》《中华人民共和国网络安全法》《移动互联网应用程序信息服务管理规定》等法律法规的要求，遵循合法、正当、必要和诚信原则处理您的信息。",
                    "Thank you for using Nexio Schedule (hereinafter referred to as “the App”). We fully recognize the importance of your personal information. In accordance with laws and regulations such as the Personal Information Protection Law of the People's Republic of China (PIPL), the Cybersecurity Law of the PRC, and the Provisions on the Administration of Mobile Internet Application Information Services, we process your information under the principles of legality, legitimacy, necessity, and good faith."
                )
            ),
            PolicyBlock.Para(
                L(
                    "本政策适用于您通过 Android 设备下载、安装、使用本应用及相关网站（nexioschedule.icu）的全部场景，用以说明我们收集哪些信息、为何收集、如何使用与保护，以及您对信息享有的权利。",
                    "This policy applies to all scenarios in which you download, install, and use the App on Android devices, as well as the related website (nexioschedule.icu). It explains what information we collect and why, how we use and protect it, and what rights you have over your information."
                )
            ),
            PolicyBlock.Note(
                L(
                    "特别说明：本应用是本地优先的课程表工具。您的课程表、节次时间、外观设置等核心数据默认仅保存在设备本地；本应用没有账号体系，不要求注册或登录，也不会收集可单独识别您个人身份的信息（如姓名、手机号、身份证号）。",
                    "Special note: the App is a local-first timetable tool. By default, your core data — such as your timetable, class-period times, and appearance settings — is saved only on your device. The App has no account system, requires no registration or login, and does not collect any information that can identify your personal identity on its own (such as your name, phone number, or ID card number)."
                )
            ),
        )
    ),
    PolicySection(
        title = L("二、开发者信息与联系方式", "2. Developer Information and Contact Details"),
        blocks = listOf(
            PolicyBlock.Bullets(
                listOf(
                    L("应用名称：Nexio 课程表", "App name: Nexio Schedule"),
                    L("应用包名：com.haooz.chedule", "App package name: com.haooz.chedule"),
                    L("开发者：Nexio 课程表开发团队（王凯哲）", "Developer: Nexio Schedule development team (Wang Kaizhe)"),
                    L("联系邮箱：439089703@qq.com", "Contact email: 439089703@qq.com"),
                    L("官方网站：nexioschedule.icu（闽ICP备2026037367号-1）", "Official website: nexioschedule.icu (闽ICP备2026037367号-1)"),
                )
            ),
            PolicyBlock.Para(
                L(
                    "如您对本政策或个人信息处理有任何疑问、意见或投诉，可通过上述邮箱与我们联系，我们将在收到后及时处理并回复。",
                    "If you have any questions, comments, or complaints about this policy or the processing of personal information, please contact us via the email address above. We will handle your message and respond in a timely manner."
                )
            ),
        )
    ),
    PolicySection(
        title = L("三、我们如何收集和使用信息", "3. How We Collect and Use Information"),
        blocks = listOf(
            PolicyBlock.Sub(L("1. 设备与运行信息（自动收集）", "1. Device and runtime information (collected automatically)")),
            PolicyBlock.Para(
                L(
                    "为了统计安装量与活跃情况、向您下发应用公告、排查兼容性问题并改进产品体验，本应用会在您启动时向开发者自建服务器发送以下信息：",
                    "To measure installations and active usage, deliver in-app announcements, troubleshoot compatibility issues, and improve the product experience, the App sends the following information to the developer's self-hosted server when you launch it:"
                )
            ),
            PolicyBlock.Bullets(
                listOf(
                    L(
                        "随机设备标识：由本应用在本机随机生成的 UUID 并存储在本地，不含任何硬件唯一标识（我们不获取 IMEI、MAC 地址、Android ID、手机号等信息）；",
                        "Random device identifier: a UUID generated randomly by the App on the device and stored locally; it contains no hardware-unique identifier (we do not obtain your IMEI, MAC address, Android ID, phone number, or similar information);"
                    ),
                    L("设备与系统信息：设备型号、品牌、厂商、Android 系统版本、系统 API 等级；", "Device and system information: device model, brand, manufacturer, Android version, and system API level;"),
                    L("应用信息：本应用版本号；", "App information: the version number of the App;"),
                    L("事件信息：安装事件、启动（活跃）事件及对应时间。", "Event information: installation and launch (activity) events, along with their timestamps."),
                )
            ),
            PolicyBlock.Para(
                L(
                    "上述信息仅用于匿名化统计与运营，不用于广告推送，也不会与您的课程数据关联。我们未接入任何第三方商业统计、广告或崩溃采集 SDK。",
                    "The above information is used only for anonymized statistics and operations, not for advertising, and is never associated with your course data. The App integrates no third-party commercial analytics, advertising, or crash-collection SDK."
                )
            ),
            PolicyBlock.Sub(L("2. 位置信息（可选，用于天气）", "2. Location information (optional, for weather)")),
            PolicyBlock.Para(
                L(
                    "本应用的「今日」页面提供天气展示。仅当您主动授权定位权限并使用天气功能时，我们才会获取您设备的大致/精确位置坐标，用于向天气服务商查询所在地天气；坐标仅用于该次天气查询，不会上传至开发者服务器用于其他用途。若您拒绝授权，天气卡片会提示需要定位，但不影响课程表等核心功能。您可随时在系统设置中关闭该权限。",
                    "The “Today” page of the App offers a weather display. Only when you proactively grant the location permission and use the weather feature do we obtain your device's approximate/precise location coordinates, and only to query the weather at your location from the weather service provider. The coordinates are used solely for that weather query and are never uploaded to the developer's server for any other purpose. If you deny the permission, the weather card will indicate that location access is needed, but core features such as the timetable remain unaffected. You can turn this permission off in the system settings at any time."
                )
            ),
            PolicyBlock.Sub(L("3. 课表与课程数据（本地存储）", "3. Timetable and course data (stored locally)")),
            PolicyBlock.Para(
                L(
                    "您创建、导入或编辑的课程、节次时间、周次、外观配色等数据，默认全部存储在您的设备本地。我们不会主动上传或读取您的课表内容。",
                    "The courses you create, import, or edit, together with class-period times, week numbers, appearance colors, and similar data, are all stored locally on your device by default. We never proactively upload or read your timetable content."
                )
            ),
            PolicyBlock.Sub(L("4. 教务系统导入信息", "4. Academic system import information")),
            PolicyBlock.Para(
                L(
                    "当您使用「教务系统导入」时，本应用会在应用内浏览器中打开对应学校的教务系统页面，您输入的账号、密码等登录凭据仅用于当次登录与课程抓取，相关凭据由您所访问的学校教务系统处理，本应用不会将其上传至开发者服务器，也不作长期存储。",
                    "When you use “Academic system import”, the App opens the corresponding school's academic system page in an in-app browser. The login credentials you enter, such as your account and password, are used only for that sign-in and the course extraction that follows; they are processed by the academic system of the school you access. The App neither uploads these credentials to the developer's server nor stores them long-term."
                )
            ),
            PolicyBlock.Sub(L("5. 云同步（WebDAV，可选）", "5. Cloud sync (WebDAV, optional)")),
            PolicyBlock.Para(
                L(
                    "当您主动开启 WebDAV 云同步时，您的课表数据会被上传至您自行填写的 WebDAV 服务器（例如坚果云等第三方网盘）。该服务器地址与账号由您自行配置与控制，数据的存储与安全取决于您所选的第三方服务，我们无法访问您在该服务中的数据。",
                    "When you proactively enable WebDAV cloud sync, your timetable data is uploaded to the WebDAV server you configure yourself (for example, a third-party cloud drive such as Jianguoyun). The server address and account are configured and controlled by you; the storage and security of your data depend on the third-party service you choose, and we cannot access your data within that service."
                )
            ),
            PolicyBlock.Sub(L("6. 课表分享（可选）", "6. Timetable sharing (optional)")),
            PolicyBlock.Para(
                L(
                    "当您使用「分享课表」生成分享口令时，被分享的课表数据会经开发者自建服务器临时中转，口令有效期为 30 分钟，到期后自动失效删除。该功能仅在您主动发起分享时使用，且仅包含课表课程与时间设置，不含设备标识等个人信息。",
                    "When you use “Share timetable” to generate a share code, the shared timetable data is relayed temporarily through the developer's self-hosted server; the code is valid for 30 minutes and is automatically invalidated and deleted upon expiry. This feature is used only when you initiate a share, and it contains only the timetable's courses and time settings — no personal information such as device identifiers."
                )
            ),
            PolicyBlock.Sub(L("7. 备份、导入与导出文件", "7. Backup, import, and export files")),
            PolicyBlock.Para(
                L(
                    "当您使用本地备份、课表导出或导入功能时，相关文件会保存至您所选择的位置（如设备存储或系统分享目标）。这些文件由您自行保管，我们不会读取或上传。",
                    "When you use local backup or the timetable export or import features, the relevant files are saved to the location you choose (such as device storage or a system share target). These files are kept under your own care, and we do not read or upload them."
                )
            ),
            PolicyBlock.Sub(L("8. 我们不会收集的信息", "8. Information we do not collect")),
            PolicyBlock.Bullets(
                listOf(
                    L("姓名、手机号、身份证号等身份信息；", "Identity information such as your name, phone number, and ID card number;"),
                    L("通讯录、短信、通话记录、相册照片与视频；", "Contacts, SMS messages, call logs, and photos or videos in your gallery;"),
                    L("设备硬件唯一标识（IMEI、MAC 地址、Android ID 等）；", "Hardware-unique device identifiers (IMEI, MAC address, Android ID, etc.);"),
                    L("您在其他应用中的使用行为。", "Your usage behavior in other apps."),
                )
            ),
        )
    ),
    PolicySection(
        title = L("四、应用权限清单及使用目的", "4. App Permissions and Their Purposes"),
        blocks = listOf(
            PolicyBlock.Para(
                L(
                    "本应用仅在实现具体功能所必需的范围内申请系统权限。所有权限均在您同意本隐私政策之后、于您使用对应功能时按需申请；您可以随时在系统「设置 - 应用 - Nexio 课程表 - 权限」中查看、授权或撤销。",
                    "The App requests system permissions only within the scope necessary to implement specific features. All permissions are requested on demand, after you agree to this privacy policy and when you use the corresponding feature. You can view, grant, or revoke them at any time under the system's “Settings - Apps - Nexio Schedule - Permissions”."
                )
            ),
            PolicyBlock.Table(
                headers = listOf(
                    L("权限", "Permission"),
                    L("使用目的", "Purpose"),
                    L("是否必需", "Required"),
                ),
                rows = listOf(
                    listOf(
                        L("INTERNET", "INTERNET"),
                        L("访问网络。用于教务导入、天气查询、检查更新、WebDAV 云同步、课表分享及匿名统计上报。", "Network access. Used for academic system import, weather queries, checking for updates, WebDAV cloud sync, timetable sharing, and anonymous statistics reporting."),
                        TAG_REQUIRED,
                    ),
                    listOf(
                        L("ACCESS_NETWORK_STATE", "ACCESS_NETWORK_STATE"),
                        L("读取网络连接状态，无网络时避免无效请求并给出提示。", "Reads the network connection state to avoid ineffective requests and show a notice when there is no network."),
                        TAG_REQUIRED,
                    ),
                    listOf(
                        L("POST_NOTIFICATIONS", "POST_NOTIFICATIONS"),
                        L("发送课前提醒、次日课程提醒、上课中等通知。", "Sends notifications such as pre-class reminders, next-day course reminders, and in-class alerts."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("VIBRATE", "VIBRATE"),
                        L("课程提醒通知的振动提示。", "Vibration feedback for course reminder notifications."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("POST_PROMOTED_NOTIFICATIONS", "POST_PROMOTED_NOTIFICATIONS"),
                        L("Android 16 及以上将「上课中」常驻提醒提升为实时更新通知展示；系统自动授予的普通权限，不涉及个人信息。", "On Android 16 and above, promotes the persistent “in class” reminder to a real-time updating notification. A normal permission granted automatically by the system; involves no personal information."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("SCHEDULE_EXACT_ALARM", "SCHEDULE_EXACT_ALARM"),
                        L("按课程开始时间准时触发课前提醒；需在系统「闹钟和提醒」手动授权，未授权时自动降级为非精确提醒。", "Triggers pre-class reminders exactly at each course's scheduled start time. Requires manual authorization under the system's “Alarms & reminders” setting; without it, reminders automatically fall back to inexact alarms."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("RECEIVE_BOOT_COMPLETED", "RECEIVE_BOOT_COMPLETED"),
                        L("设备重启后重新注册已设置的课程提醒，避免重启后提醒失效。", "Re-registers the configured course reminders after the device restarts, so that reminders do not stop working after a reboot."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("ACCESS_NOTIFICATION_POLICY", "ACCESS_NOTIFICATION_POLICY"),
                        L("实现「上课勿扰」，上课期间自动开启系统勿扰；需在系统「免打扰访问权限」单独授权。", "Enables “Do not disturb during class”, which automatically turns on the system Do Not Disturb mode during class. Requires separate authorization under the system's “Do Not Disturb access” setting."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("MODIFY_AUDIO_SETTINGS", "MODIFY_AUDIO_SETTINGS"),
                        L("配合课程提醒通知的声音与静音相关设置。", "Works with the sound and mute related settings of course reminder notifications."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION"),
                        L("仅在开启天气功能并授权后获取当前位置查询天气；不使用天气功能则不申请、不获取。", "Obtains the current location to query the weather only after the weather feature is enabled and the permission is granted. Not requested or obtained if the weather feature is not used."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE / WAKE_LOCK", "FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE / WAKE_LOCK"),
                        L("保障课程提醒触发与桌面小组件刷新在后台可靠运行。", "Keeps course reminder triggering and home-screen widget refreshes running reliably in the background."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"),
                        L("引导你在系统设置中允许本应用后台运行，避免省电策略导致提醒被延迟或拦截；需你手动确认。", "Guides you to allow the App to run in the background in the system settings, so that battery-saving policies do not delay or block reminders. Requires your manual confirmation."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("WRITE_EXTERNAL_STORAGE（仅 Android 9 及以下）", "WRITE_EXTERNAL_STORAGE (Android 9 and below only)"),
                        L("将课表备份与导出文件写入设备存储；Android 10 及以上无需该权限。", "Writes timetable backups and exported files to device storage. Not needed on Android 10 and above."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("REQUEST_INSTALL_PACKAGES", "REQUEST_INSTALL_PACKAGES"),
                        L("仅当你主动检查并选择更新时，用于下载并安装新版本安装包。", "Used to download and install the new version package only when you actively check for updates and choose to install one."),
                        TAG_OPTIONAL,
                    ),
                    listOf(
                        L("Shizuku 授权（非系统权限）", "Shizuku authorization (not a system permission)"),
                        L("需你自行安装 Shizuku 并在其中授权，用于超级岛 / 灵动岛通知的进阶展示。", "Requires you to install Shizuku yourself and grant authorization within it; used for the advanced display of Super Island / Dynamic Island notifications."),
                        TAG_OPTIONAL,
                    ),
                )
            ),
            PolicyBlock.Para(
                L(
                    "标注为「可选」的权限，拒绝授权不影响课程表查看、课程管理等核心功能，仅会导致对应的附加功能不可用。",
                    "For permissions marked “Optional”, declining authorization does not affect core features such as viewing your timetable or managing courses; it only makes the corresponding extra features unavailable."
                )
            ),
        )
    ),
    PolicySection(
        title = L("五、第三方服务清单", "5. Third-Party Services"),
        blocks = listOf(
            PolicyBlock.Para(
                L(
                    "为实现特定功能，本应用在您使用相关功能时会与以下第三方服务通信。这些服务仅接收完成功能所必需的信息，各服务方对其信息处理行为负责，请同时参阅其各自的隐私政策。",
                    "To deliver specific features, the App communicates with the following third-party services when you use the corresponding features. Each service receives only the information necessary to complete the function in question; each provider is responsible for its own information processing, and we encourage you to also review their respective privacy policies."
                )
            ),
            PolicyBlock.Table(
                headers = listOf(
                    L("服务 / 提供方", "Service / Provider"),
                    L("用途", "Purpose"),
                    L("涉及的信息", "Information involved"),
                ),
                rows = listOf(
                    listOf(
                        L("Nexio 服务端（开发者自建，nexioschedule.icu）", "Nexio server (self-hosted by the developer, nexioschedule.icu)"),
                        L("匿名安装/活跃统计、应用公告下发、课表分享口令中转、赞赏榜展示", "Anonymous install/activity statistics, in-app announcement delivery, timetable share-code relaying, and the supporters leaderboard"),
                        L("随机设备标识、设备型号、品牌/厂商、系统版本、应用版本、事件时间", "Random device identifier, device model, brand/manufacturer, system version, app version, and event timestamps"),
                    ),
                    listOf(
                        L("天气服务（彩云天气，经 v1.apizero.cn 接口）", "Weather service (Caiyun Weather, via the v1.apizero.cn API)"),
                        L("展示所在地天气", "Displays the weather at your location"),
                        L("经纬度坐标", "Latitude and longitude coordinates"),
                    ),
                    listOf(
                        L("天气服务（t.weather.itboy.net，中国天气网数据源）", "Weather service (t.weather.itboy.net, using data from China Weather Network)"),
                        L("展示所在地天气（可切换数据源）", "Displays the weather at your location (data source switchable)"),
                        L("定位解析得到的城市编码", "The city code resolved from your location"),
                    ),
                    listOf(
                        L("GitHub / Gitee Releases 接口", "GitHub / Gitee Releases APIs"),
                        L("检查应用新版本", "Checks for new versions of the App"),
                        L("无个人标识，仅请求版本信息", "No personal identifiers; only version information is requested"),
                    ),
                    listOf(
                        L("unpkg CDN（holiday-calendar 数据）", "unpkg CDN (holiday-calendar data)"),
                        L("获取节假日与调休数据", "Fetches holiday and adjusted-working-day data"),
                        L("无个人标识", "No personal identifiers"),
                    ),
                    listOf(
                        L("星链课表（api.starlinkkb.cn）", "Starlink Timetable (api.starlinkkb.cn)"),
                        L("导入第三方课表分享", "Imports timetable shares from third-party apps"),
                        L("您输入的分享码", "The share code you enter"),
                    ),
                    listOf(
                        L("WakeUp 课表（api.wakeup.fun）", "WakeUp Schedule (api.wakeup.fun)"),
                        L("导入第三方课表分享", "Imports timetable shares from third-party apps"),
                        L("您输入的分享码", "The share code you enter"),
                    ),
                    listOf(
                        L("拾光课表脚本仓库（Gitee）", "Shiguang Timetable script repository (Gitee)"),
                        L("获取教务系统导入脚本", "Fetches academic system import scripts"),
                        L("无个人标识", "No personal identifiers"),
                    ),
                    listOf(
                        L("系统定位与地理编码服务（操作系统）", "System location and geocoding services (operating system)"),
                        L("将定位坐标解析为城市名称", "Resolves location coordinates into a city name"),
                        L("经纬度坐标", "Latitude and longitude coordinates"),
                    ),
                )
            ),
            PolicyBlock.Note(
                L(
                    "本应用未接入任何广告 SDK、第三方商业数据统计 SDK 或用户画像服务。发送至境外服务（如 GitHub、unpkg）的请求仅包含公开的版本或数据信息，不含您的个人信息或课表内容。",
                    "The App integrates no advertising SDK, third-party commercial analytics SDK, or user-profiling service. Requests sent to services outside mainland China (such as GitHub and unpkg) contain only public version or data information, never your personal information or timetable content."
                )
            ),
        )
    ),
    PolicySection(
        title = L("六、信息的存储与保护", "6. Storage and Protection of Information"),
        blocks = listOf(
            PolicyBlock.Bullets(
                listOf(
                    L(
                        "存储地点：本应用收集的匿名统计信息存储于中华人民共和国境内的服务器（阿里云 ECS）中，不涉及个人信息出境。",
                        "Storage location: the anonymized statistics collected by the App are stored on servers within the People's Republic of China (Alibaba Cloud ECS); no personal information is transferred out of the country."
                    ),
                    L(
                        "存储期限：课表等核心数据存储于您的设备本地，由您自行控制；匿名统计信息在实现统计目的所必需的期限内保存，超出期限后将予以删除或匿名化处理；分享口令数据在 30 分钟有效期届满后自动删除。",
                        "Retention period: core data such as your timetable is stored locally on your device and remains under your control; anonymized statistics are kept only for the period necessary to fulfill the statistical purposes and are deleted or anonymized once that period expires; share-code data is deleted automatically when its 30-minute validity period ends."
                    ),
                    L(
                        "安全措施：我们采用访问控制、传输与存储加密（在支持的链路上）等合理可行的安全措施保护信息安全，并限制内部人员对数据的访问。但请您理解，任何技术手段都无法保证绝对安全，请妥善保管您的设备与账户信息。",
                        "Security measures: we adopt reasonably practicable security measures — such as access control and encryption of transmission and storage (on supported links) — to protect information security, and we restrict internal personnel's access to data. Please understand, however, that no technical measure can guarantee absolute security, so please keep your device and account information safe."
                    ),
                )
            ),
        )
    ),
    PolicySection(
        title = L("七、信息的共享、转让与公开披露", "7. Sharing, Transfer, and Public Disclosure of Information"),
        blocks = listOf(
            PolicyBlock.Bullets(
                listOf(
                    L(
                        "共享：除为实现本政策所述功能所必需（如天气查询向天气服务商发送坐标、云同步向您指定的 WebDAV 服务上传数据）外，我们不会向任何第三方共享您的信息。",
                        "Sharing: except where necessary to deliver the features described in this policy (for example, sending coordinates to the weather service provider for weather queries, or uploading data to the WebDAV service you designate for cloud sync), we do not share your information with any third party."
                    ),
                    L("转让：我们不会将您的信息转让给任何公司、组织或个人。", "Transfer: we do not transfer your information to any company, organization, or individual."),
                    L(
                        "公开披露：我们不会公开披露您的信息。仅在法律法规要求、司法机关或行政机关依法提出要求时，我们才可能依法提供必要的信息。",
                        "Public disclosure: we do not publicly disclose your information. Only where laws and regulations so require, or where judicial or administrative authorities make a lawful request, may we provide the necessary information in accordance with the law."
                    ),
                )
            ),
        )
    ),
    PolicySection(
        title = L("八、您的权利", "8. Your Rights"),
        blocks = listOf(
            PolicyBlock.Para(
                L(
                    "在个人信息处理活动中，您依法享有以下权利，我们为您提供了实现途径：",
                    "In personal information processing activities, you enjoy the following rights under the law, and we provide the means for you to exercise them:"
                )
            ),
            PolicyBlock.Bullets(
                listOf(
                    L("查阅与更正：您的课程表等数据均可在本应用内直接查看、编辑和修改。", "Access and correction: your timetable and other data can be viewed, edited, and modified directly within the App."),
                    L("删除：您可在应用内删除课程、课表及本地备份数据；卸载本应用将删除设备本地存储的全部数据。", "Deletion: you can delete courses, timetables, and local backup data within the App; uninstalling the App deletes all data stored locally on the device."),
                    L(
                        "撤回授权：您可随时在系统「设置 - 应用 - Nexio 课程表 - 权限」中关闭定位、通知等权限，或在应用内关闭天气、云同步等功能；也可在「关于」页点击「撤回同意」。",
                        "Withdrawal of consent: you can turn off permissions such as location and notifications at any time under the system's “Settings - Apps - Nexio Schedule - Permissions”, or disable features such as weather and cloud sync within the App; you can also tap “Withdraw consent” on the “About” page."
                    ),
                    L(
                        "注销与删除服务器数据：本应用无账号体系，无需注销账户。若您希望删除与您设备随机标识关联的匿名统计数据，或对信息处理有任何诉求，请发送邮件至 439089703@qq.com。",
                        "Account cancellation and server data deletion: the App has no account system, so there is no account to cancel. If you wish to delete the anonymized statistics associated with your device's random identifier, or have any other request regarding the processing of your information, please email 439089703@qq.com."
                    ),
                )
            ),
        )
    ),
    PolicySection(
        title = L("九、未成年人保护", "9. Protection of Minors"),
        blocks = listOf(
            PolicyBlock.Para(
                L(
                    "本应用主要面向高校学生等用户群体。若您是未满 14 周岁的未成年人，请务必在您的父母或其他监护人的指导下阅读本政策并决定是否使用本应用；若您是未成年人的监护人，请在未成年人使用本应用前向其说明本政策内容。我们不会主动收集未成年人的个人信息，如发现相关情况，我们将及时删除。",
                    "The App is primarily intended for users such as college and university students. If you are a minor under the age of 14, please be sure to read this policy and decide whether to use the App under the guidance of your parents or other guardian; if you are the guardian of a minor, please explain this policy to them before they use the App. We do not proactively collect personal information from minors, and if we become aware of any such case, we will delete it promptly."
                )
            ),
        )
    ),
    PolicySection(
        title = L("十、本政策的更新", "10. Updates to This Policy"),
        blocks = listOf(
            PolicyBlock.Para(
                L(
                    "我们可能会根据法律法规变化或业务调整适时更新本政策。更新后，我们会在本页面发布最新版本并更新「更新日期」，重大变更还将通过应用内公告等显著方式另行提示。若您在政策更新后继续使用本应用，即表示您同意接受更新后的政策；若您不同意，请停止使用并卸载本应用。",
                    "We may update this policy from time to time in response to changes in laws and regulations or adjustments to our operations. After each update, we will publish the latest version on this page and refresh the “Last updated” date; material changes will also be highlighted separately through prominent means such as in-app announcements. If you continue to use the App after the policy is updated, you are deemed to have accepted the updated policy; if you do not agree, please stop using and uninstall the App."
                )
            ),
        )
    ),
    PolicySection(
        title = L("十一、联系我们", "11. Contact Us"),
        blocks = listOf(
            PolicyBlock.Para(
                L(
                    "如您对本政策有任何疑问、意见、建议或投诉，可通过以下方式与我们联系，我们将在收到后尽快处理：",
                    "If you have any questions, comments, suggestions, or complaints about this policy, please contact us through the channels below, and we will handle your message as soon as possible after receiving it:"
                )
            ),
            PolicyBlock.Bullets(
                listOf(
                    L("联系邮箱：439089703@qq.com", "Contact email: 439089703@qq.com"),
                    L("官方网站：nexioschedule.icu", "Official website: nexioschedule.icu"),
                    L("开源仓库：github.com/HaoZai000/NexioSchedule", "Open-source repository: github.com/HaoZai000/NexioSchedule"),
                )
            ),
        )
    ),
)

@Composable
fun PrivacyPolicyScreen(
    scrollBehavior: SharedScrollBehavior? = null,
) {

    val listState = rememberLazyListState()
    val isTablet = screenWidthDp() >= 600
    // 平板按屏宽在 20~128dp 间线性放大：600dp 屏=20dp，1200dp 及以上封顶 128dp
    val tabletHorizontalPadding = if (isTablet) {
        val widthDp = screenWidthDp()
        ((widthDp - 600).coerceIn(0, 600) / 600f * 108 + 20).dp
    } else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                            text = L("Nexio 课程表 隐私政策", "Nexio Schedule Privacy Policy")(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = L("生效日期：2026 年 10 月 2 日\n更新日期：2026 年 10 月 2 日", "Effective date: October 2, 2026\nLast updated: October 2, 2026")(),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }

                itemsIndexed(POLICY_SECTIONS, key = { index, _ -> "policy_section_$index" }) { _, section ->
                    PolicySectionCard(section)
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = L("本政策自 2026 年 10 月 2 日起生效 · Nexio 课程表", "This policy is effective as of October 2, 2026 · Nexio Schedule")(),
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
                text = section.title(),
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
                text = block.text(),
                fontSize = 14.sp,
                lineHeight = 23.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        is PolicyBlock.Sub -> {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = block.text(),
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
                        text = item(),
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
                        com.haooz.softglass.shape.ContinuousRoundedRectangle(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = block.text(),
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
                        com.haooz.softglass.shape.ContinuousRoundedRectangle(12.dp)
                    )
                    .padding(14.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        is PolicyBlock.Raw -> {
            Text(
                text = block.text,
                fontSize = 14.sp,
                lineHeight = 23.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PolicyTable(headers: List<L10n>, rows: List<List<L10n>>) {
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
                    text = row.getOrNull(0)?.invoke() ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 第二列：用途说明
                Text(
                    text = row.getOrNull(1)?.invoke() ?: "",
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                // 第三列：必需 / 可选（权限表）
                val third = row.getOrNull(2)
                if (third === TAG_REQUIRED || third === TAG_OPTIONAL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = third?.invoke() ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (third === TAG_REQUIRED) MiuixTheme.colorScheme.primary
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