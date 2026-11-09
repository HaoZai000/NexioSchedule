// 发新版本时在 changelogData 顶部新增一个 ChangelogEntry，每行 L(中文, English) 中英并排填写。
package com.haooz.chedule.ui.data

import com.haooz.chedule.i18n.L
import com.haooz.chedule.i18n.L10n

data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<L10n>
)
val changelogData = listOf(
    ChangelogEntry(
        version = "v1.7.0-1103",
        date = "2026-11-03",
        changes = listOf(
            L("桌面小组件支持莫奈取色，配色跟随系统壁纸（Android 12+）", "Home screen widgets support Monet colors and follow the system wallpaper (Android 12+)"),
            L("优化今日助手提醒文案，不再重复显示倒计时", "Improve Today assistant tips so they no longer repeat the countdown"),
            L("优化今日助手文案语气，更自然、更随机", "Improve Today assistant wording — more natural and more varied"),
        )
    ),
    ChangelogEntry(
        version = "v1.6.8-1103",
        date = "2026-11-03",
        changes = listOf(
            L("新增课表页与今日页的时间轴视图，可在课表外观里切换", "Add a timeline view for the timetable and Today pages, switchable in Appearance"),
            L("新增课程备注，可按周单独填写", "Add course notes, with per-week notes supported"),
            L("新增桌面小组件壁纸与自定义浮层，支持自定义字号与模糊", "Add widget wallpapers and a custom widget sheet with font size and blur controls"),
            L("新增「无材质」材质档，无模糊、折射", "Add a \"No material\" tier with no blur or refraction"),
            L("新增多语言支持，默认跟随系统，可切换中文 / English", "Add multi-language support: follows the system by default, switchable between Chinese and English"),
            L("新增导入同名课表时可覆盖，不必改名新建", "Add overwrite when importing a timetable with the same name, instead of renaming"),
            L("新增导出页「同步到日历」，可把课表逐节课写入系统日历", "Add \"Sync to Calendar\" on the export page to write each class into the system calendar"),
            L("优化设置页改为独立二级页，更名为「设置」", "Move Settings to its own secondary page and rename it from \"Profile\" to \"Settings\""),
            L("优化底栏布局，加入融球动效", "Improve the bottom bar layout and add a metaball animation"),
            L("优化今日助手卡排版", "Improve the Today assistant card layout"),
            L("优化部分页面底部按钮", "Improve the bottom buttons on some pages"),
            L("优化数据存储底层，读写更快、启动更稳", "Improve the storage layer for faster reads and writes and steadier startup"),
            L("优化冷启动，首帧直接是用户外观与壁纸", "Improve cold start so the first frame already shows your appearance and wallpaper"),
            L("优化界面文案与术语统一", "Improve wording and terminology consistency"),
            L("修复切换更新通道可能闪退", "Fix a possible crash when switching update channels"),
            L("修复编辑作息时误改到其他课表的作息", "Fix time scheme edits affecting another timetable's scheme"),
            L("修复备份恢复后仍残留已删除的课表", "Fix deleted timetables lingering after a backup restore"),
            L("修复材质档切换后卡片模糊与折射不生效", "Fix card blur and refraction not applying after switching material tiers"),
            L("修复缩放与外观页玻璃卡片错位", "Fix glass card misalignment on the scale and appearance pages"),
            L("修复低版本设备打开课表外观页黑屏", "Fix a black screen when opening Appearance on older Android versions"),
            L("修复低版本设备连贯动画只显示上半截", "Fix shared-element transitions showing only the top half on older Android versions"),
        )
    ),
    ChangelogEntry(
        version = "v1.6.7-1025",
        date = "2026-10-25",
        changes = listOf(
            L("新增自定义时间课程支持长按编辑与删除", "Custom-time courses: long-press to edit and delete"),
            L("新增「今明课程」桌面小组件", "Add \"Today & Tomorrow\" home screen widget"),
            L("新增全局文字变化模糊动效", "Add global text-change blur animation"),
            L("新增全局控件变化模糊动效", "Add global control-change blur animation"),
            L("新增长按拖动课程支持跨周调课", "Add cross-week rescheduling via long-press drag"),
            L("新增课程提醒缩略态课程与地点精简", "Add condensed course and location in collapsed reminders"),
            L("新增 WebDAV 自定义备份路径", "Add custom WebDAV backup path"),
            L("新增手环整表推送逐日裁决", "Add per-day decision logic for band full-schedule push"),
            L("应用材质分级改版为材质选择", "Revamp app material tiers into a material picker"),
            L("优化课表星期行切换动画", "Improve weekday row switching animation"),
            L("优化小组件页面及小组件UI", "Improve widget page and widget UI"),
            L("优化翻页与调课跟手性", "Improve page-turn and reschedule responsiveness"),
            L("优化连贯动画预测性返回手感", "Improve predictive back feel of shared-element transitions"),
            L("优化课表页渲染性能与功耗", "Improve timetable rendering performance and power draw"),
            L("优化玻璃材质与壁纸表现", "Improve glass material and wallpaper rendering"),
            L("修复部分机型页面显示不完整", "Fix pages cut off on some devices"),
            L("修复课程弹窗收键盘与退出动画异常", "Fix keyboard dismiss and exit animation issues in the course dialog"),
            L("修复换周、跳周手势翻页异常", "Fix week-switch and week-jump gesture paging issues"),
            L("修复壁纸保存后分辨率低", "Fix low wallpaper resolution after saving")
        )
    ),
    ChangelogEntry(
        version = "v1.6.6-1017",
        date = "2026-10-17",
        changes = listOf(
            L("新增支持莫奈取色（Android 12+）", "Add Monet dynamic color (Android 12+)"),
            L("新增单色主题图标，桌面图标可跟随系统主题着色", "Add monochrome themed icons; home screen icons follow the system theme"),
            L("优化部分连贯动画性能与动画表现", "Improve performance and quality of some shared-element transitions"),
            L("优化课表页玻璃渲染链路，滑动更稳更省电", "Improve timetable glass rendering for steadier, more power-efficient scrolling"),
            L("优化课程管理页卡片排版，教师与地点信息更清晰", "Improve course management card layout; teacher and location info is clearer"),
            L("优化教务导入页顶栏取色性能", "Improve top bar color extraction performance on the academic import page"),
            L("优化底部悬浮间距，四处位置统一", "Unify bottom floating spacing across four spots"),
            L("优化底部弹窗滚动时的顶部遮罩", "Improve the top scrim while bottom sheets scroll"),
            L("优化连贯动画时长与预测性返回手感", "Improve shared-element transition timing and predictive back feel"),
            L("优化课程卡片点击反馈，去除点击水波纹", "Improve course card tap feedback and remove the tap ripple"),
            L("优化弹窗与转场圆角，跟随应用屏幕圆角", "Dialog and transition corner radii now follow the app screen corners"),
            L("修复部分页面按压反馈与文字行高被主题覆盖", "Fix press feedback and text line height being overridden by the theme on some pages"),
            L("修复长按课程浮层与今日页卡片模糊偏浅", "Fix too-light blur on the long-press course overlay and Today page cards"),
            L("修复二级页面返回后页面跳闪的问题", "Fix page flicker after returning from secondary pages"),
            L("修复节次错位时本周课程被非本周课程覆盖", "Fix this week's courses being covered by other weeks' courses when periods are misaligned"),
            L("修复小窗模式下圆角不一致与底部悬浮贴底", "Fix inconsistent corner radii and bottom floating bar in freeform window mode"),
            L("修复预测性返回开关需重启应用才生效", "Fix the predictive back toggle requiring an app restart to take effect")
        )
    ),
    ChangelogEntry(
        version = "v1.6.5-1015",
        date = "2026-10-15",
        changes = listOf(
            L("新增自定义课程时间支持跨午夜", "Add after-midnight support for custom course times"),
            L("新增小部件任意位置点击都能打开应用并刷新", "Add tap-anywhere on widgets to open and refresh the app"),
            L("新增课程提醒小组件支持横向缩放", "Add horizontal scaling for the course reminder widget"),
            L("优化主界面切页动画效果", "Improve the main page switch animation"),
            L("优化二级页面转场动画，更跟手且支持中途打断续播", "Improve secondary page transitions: more responsive and interruptible mid-flight"),
            L("优化页面转场的黑幕与圆角表现，层级过渡更自然", "Improve transition scrim and corner radii for more natural depth changes"),
            L("优化应用整体架构，重构为跨平台共享核心", "Rebuild the app architecture as a cross-platform shared core"),
            L("优化非当前页面的绘制策略，降低待机功耗", "Improve off-screen page drawing to reduce idle power draw"),
            L("优化关闭卡片折射时的课表性能", "Improve timetable performance with card refraction off"),
            L("优化节假日/调休编辑弹窗，改为底部弹出", "Move the holiday/adjustment editing dialog to a bottom sheet"),
            L("优化顶栏回弹时的材质显示效果", "Improve material appearance during top bar overscroll"),
            L("优化网络请求复用，教务导入更快更稳", "Improve network request reuse for faster, steadier academic import"),
            L("优化手环课表的假期与调休显示，与手机端保持一致", "Align band timetable holiday/adjustment display with the phone"),
            L("优化手环推送，修改假期/调休后立即同步", "Improve band push to sync immediately after holiday/adjustment changes"),
            L("修复切换底部标签后周课表卡死、无法滑动", "Fix the weekly timetable freezing after switching bottom tabs"),
            L("修复上课勿扰开启后下课不自动恢复", "Fix in-class DND not auto-restoring after class"),
            L("修复跨午夜课程的提醒时刻与课中倒计时显示异常", "Fix reminder times and in-class countdown for after-midnight courses"),
            L("修复部分学校教务导入页面只有文字、样式全无", "Fix the academic import page showing plain text without styles for some schools"),
            L("修复教务导入页面只显示左半边的问题", "Fix the academic import page showing only its left half"),
            L("修复旧版本备份、云端备份提示「无法恢复」", "Fix \"cannot restore\" prompts for legacy and cloud backups"),
            L("修复连贯动画下原课程卡片快照不显示", "Fix the original course card snapshot missing in shared-element transitions"),
            L("修复部分情况长按课程浮层与调课落点错位", "Fix long-press overlay and drop target misalignment in some cases")
        )
    ),
    ChangelogEntry(
        version = "v1.6.4-1008",
        date = "2026-10-08",
        changes = listOf(
            L("新增多作息方案，可设置某月某日起自动切换，支持从其他课表复制", "Add multiple time schemes with auto-switch from a set date and copying from other timetables"),
            L("新增节假日数据源可切换，调休自动预填「上哪天的课」", "Add a switchable holiday data source; adjustments auto-fill which day's classes to follow"),
            L("新增调休跟随绝对日期，切换课表后自动跟随对应日期的课", "Add absolute-date adjustments that auto-track the matching classes after switching timetables"),
            L("新增放假与调休提醒，明天上不上课一眼看到", "Add holiday and adjustment reminders to see at a glance whether tomorrow has class"),
            L("新增开学前一天的明日课程提醒", "Add a next-day course reminder for the day before term starts"),
            L("新增长按课程拖动课程时的跟手高光", "Add a touch-following highlight while long-press dragging courses"),
            L("新增支持全局菜单滑动选择选项", "Add drag-to-select for global menu options"),
            L("优化下拉菜单与弹窗组件，动画、回弹与跟手效果更统一", "Improve dropdown and dialog components with more unified animations, overscroll and touch feel"),
            L("优化顶部下拉菜单（课表切换 / 课表外观 / 跳转周数）的回弹与高光", "Improve overscroll and highlight of top dropdowns (timetable switch / appearance / week jump)"),
            L("优化课程名排序，按中文拼音排，数字按大小排", "Improve course name sorting: by Chinese pinyin, numbers by value"),
            L("优化课表外观存储，不随全量备份导出", "Improve timetable appearance storage so it no longer exports with full backups"),
            L("优化课程管理与课程编辑页排序", "Improve sorting in course management and the course editor"),
            L("优化平板端主界面切页，更流畅", "Improve tablet main page switching for smoother motion"),
            L("优化特殊课程添加与编辑流程", "Improve the special course add and edit flow"),
            L("修复关闭大课间后课表时间不生效、节次整体错位", "Fix timetable times not applying and periods misaligning after disabling the long break"),
            L("修复调休列拖动课程动到了其他周、拖回原位置误报冲突", "Fix adjustment-column drags moving other weeks and false conflicts when dropping back"),
            L("修复冲突弹窗「覆盖」把课写到错误的星期与周次", "Fix the conflict dialog's \"Overwrite\" placing courses on the wrong weekday and week"),
            L("修复两节同名课程互相交换没有反应", "Fix swapping two same-named courses doing nothing"),
            L("修复课程卡片模糊半径默认值与设置项不一致", "Fix the course card blur radius default not matching its setting"),
            L("修复切换底部标签时顶栏主题瞬间变色", "Fix the top bar theme flashing when switching bottom tabs"),
            L("修复分享课表时缺少数据校验", "Fix missing data validation when sharing timetables"),
            L("修复部分网页加载兼容性问题", "Fix web page loading compatibility issues"),
            L("修复部分数据迁移后数据丢失的问题", "Fix data loss after some data migrations"),
            L("修复部分设备上时间设置修改后保存不生效", "Fix time setting changes not saving on some devices"),
            L("修复特殊课程可能存在的错位问题", "Fix possible misalignment of special courses")
        )
    ),
    ChangelogEntry(
        version = "v1.6.3-1004",
        date = "2026-10-04",
        changes = listOf(
            L("新增小组件支持更多适应档位", "Add more widget scaling levels"),
            L("新增适配支持到安卓8.0", "Add support down to Android 8.0"),
            L("修复小组件显示异常", "Fix widget display issues"),
            L("修复部分UI界面显示异常", "Fix some UI display issues"),
            L("修复更新时可能获取不到链接", "Fix update checks possibly failing to fetch the link")
        )
    ),
    ChangelogEntry(
        version = "v1.6.2-1003",
        date = "2026-10-03",
        changes = listOf(
            L("本次更新包含应用合规性优化", "This update includes app compliance improvements"),
            L("优化更新安装包校验机制", "Improve update package verification"),
            L("修复应用部分已知问题", "Fix some known issues")
        )
    ),
    ChangelogEntry(
        version = "v1.6.1-1002",
        date = "2026-10-02",
        changes = listOf(
            L("新增Vela端能力支持（手环版App），来自PR提供者 @Alittlejelly", "Add Vela support for the band app, by @Alittlejelly"),
            L("新增支持添加教学周重组规则，来自PR提供者 @yulimfish", "Add teaching week rearrangement rules, by @yulimfish"),
            L("新增支持节假日末期课程排除，来自PR提供者 @yulimfish", "Add excluding courses at the tail of holidays, by @yulimfish"),
            L("新增假期开始/结束提醒，来自PR提供者 @yulimfish", "Add holiday start/end reminders, by @yulimfish"),
            L("底部导航栏支持自适应高度，来自PR提供者 @leset0ng", "Bottom navigation bar supports adaptive height, by @leset0ng"),
            L("重新设计Pad端布局，更简单易用", "Redesign the tablet layout for simpler use"),
            L("重新设计切换课表功能，支持文件夹分类", "Redesign timetable switching with folder categories"),
            L("课表外观可切换页面预览编辑效果", "Timetable appearance can switch pages to preview the edit effect"),
            L("优化小组件显示效果，并支持拖动改变大小，感谢 @xiaomanjun233", "Improve widget appearance with drag-to-resize, thanks @xiaomanjun233"),
            L("优化左右上下滑动页面手感", "Improve page sliding feel in all directions"),
            L("优化实况通知样式，适配ColorOS", "Improve Live Notification styling and adapt to ColorOS"),
            L("优化上课勿扰状态对账逻辑", "Improve in-class DND state reconciliation"),
            L("优化切页与课程表页面流畅度", "Improve page switching and timetable smoothness"),
            L("修复测试通知「上课勿扰」按钮不生效", "Fix the \"in-class DND\" button not working in test notifications"),
            L("测试通知也支持上课自动开启勿扰", "Test notifications also support auto-enabling DND in class"),
            L("修复未授予勿扰权限时静音档不生效", "Fix mute mode not working without the DND permission"),
            L("修复课程表页面，浮层错位、多指误触", "Fix timetable overlay misalignment and multi-touch misfires")
        )
    ),
    ChangelogEntry(
        version = "v1.5.6-0924",
        date = "2026-09-24",
        changes = listOf(
            L("课中提醒文案支持自定义", "In-class reminder text is now customizable"),
            L("排班课表功能重新上线", "Shift timetable feature is back online"),
            L("优化上课勿扰功能逻辑", "Improve in-class DND logic"),
            L("重新设计捐赠支持页面", "Redesign the donation page"),
            L("尝试优化课程提醒准时准点", "Improve course reminder punctuality"),
            L("修复部分已知问题", "Fix some known issues")
        )
    ),
    ChangelogEntry(
        version = "v1.5.5-0921",
        date = "2026-09-21",
        changes = listOf(
            L("修复部分已知问题", "Fix some known issues"),
            L("提升应用流畅度", "Improve app smoothness")
        )
    ),
    ChangelogEntry(
        version = "v1.5.4-0919",
        date = "2026-09-19",
        changes = listOf(
            L("优化更新下载逻辑，不重复跳转", "Improve update download logic with no duplicate redirects"),
            L("修复部分教务导入问题", "Fix some academic import issues"),
            L("修复预测性返回动画问题", "Fix predictive back animation issues"),
            L("修复横竖屏旋转时壁纸错位", "Fix wallpaper misalignment on orientation rotation")
        )
    ),
    ChangelogEntry(
        version = "v1.5.3-0918",
        date = "2026-09-18",
        changes = listOf(
            L("新增课中提醒实时倒计时，并支持自定义", "Add a real-time in-class reminder countdown with customization"),
            L("新增息屏显示超级岛摘要内容", "Add Super Island summary on the always-on display"),
            L("新增支持通过口令分享课表", "Add timetable sharing via passcode"),
            L("新增支持全局预测性返回动画", "Add app-wide predictive back animation"),
            L("优化返回今日/今周按钮，更方便", "Improve the Back to Today/This Week buttons for convenience"),
            L("优化主页面间切换的动画，更流畅", "Improve main page switch animations for smoother motion"),
            L("优化今日页UI，增强内容可读性", "Improve Today page UI and content readability"),
            L("优化今日页格言文案与今日助手文案", "Improve Today page quote and assistant wording"),
            L("优化拖拽调课课程示意遮罩的效果", "Improve the course preview overlay while drag-rescheduling"),
            L("优化课程表页与今日页滑动手感", "Improve scrolling feel on the timetable and Today pages"),
            L("修复今日页底部间距可能异常", "Fix possible bottom spacing issues on the Today page"),
            L("修复课程表页底部间距可能异常", "Fix possible bottom spacing issues on the timetable page"),
            L("修复部分调课的动画落点异常", "Fix animation drop points for some reschedules"),
            L("修复调课时落点可能示意错位", "Fix possible drop target misalignment while rescheduling"),
            L("修复调课日从网格添加课程周次错误", "Fix wrong weeks when adding courses from the grid on adjustment days"),
            L("修复调休页弹窗总是默认值的问题", "Fix adjustment page dialogs always showing default values"),
            L("修复部分页面切换深浅色后状态丢失", "Fix state loss after switching light/dark on some pages"),
            L("修复课表外观页面部分参数无法保存", "Fix some timetable appearance parameters not saving"),
            L("修复调课后星期行可能显示异常", "Fix possible weekday row glitches after rescheduling"),
            L("修复今日页不显示调休课程的问题", "Fix the Today page not showing adjusted courses"),
            L("修复滑动选择周次无法分段滑选", "Fix segmented range selection in the week slide picker"),
            L("修复今日助手距离时长错误的问题", "Fix wrong time-until-class in the Today assistant"),
            L("修复部分UI颜色显示异常的问题", "Fix some UI color issues"),
            L("修复Pad端小部件字体过大的问题", "Fix oversized widget fonts on tablets"),
            L("修复关闭显示非本周课程部分日期仍显示", "Fix non-current-week courses still showing on some dates after disabling the option")
        )
    ),
    ChangelogEntry(
        version = "v1.5.1-0913",
        date = "2026-09-13",
        changes = listOf(
            L("#1.5.0因存在数据被意外混淆，更新1.5.1后，会导致课表外观，课程时间/节数被重置！可提前备份课表，在1.5.1再还原#", "#1.5.0 data was accidentally scrambled: updating to 1.5.1 will reset timetable appearance and course times/periods! Back up your timetable in advance and restore it in 1.5.1#"),
            L("支持长按课程进行复制粘贴", "Support copy and paste via course long-press"),
            L("支持滑动快速选择周次", "Support quick week selection by sliding"),
            L("重构WebView页面，更美观", "Rebuild the WebView page with a nicer look"),
            L("重构页面顶部模糊为真·渐变模糊", "Rebuild the page top blur as true gradient blur"),
            L("优化底部导航栏动画样式", "Improve bottom navigation bar animation styles"),
            L("优化课程详情页导向滚动动画", "Improve the guided scroll animation on the course detail page"),
            L("修复课表外观无法保存居中方式", "Fix the alignment option not saving in timetable appearance"),
            L("修复各种因空值导致的应用崩溃", "Fix various crashes caused by null values"),
        )
    ),
    ChangelogEntry(
        version = "v1.5.0-0910",
        date = "2026-09-10",
        changes = listOf(
            L("#无缝丝滑，处处惊喜，细节更进#", "#Seamless and silky, surprises everywhere, finer details#"),
            L("新增添加按钮根据页面显隐动画", "Add a show/hide animation for the add button per page"),
            L("新增应用触感反馈开关", "Add an app haptic feedback toggle"),
            L("特殊课程支持添加安排", "Special courses support adding arrangements"),
            L("上课静音升级上课勿扰，上课时自动开启静音/勿扰，支持上课自动开启或手动开启", "In-class mute upgraded to in-class DND: auto-enables mute/DND during class, either automatically or manually"),
            L("更新应用图标，更精致、有质感", "Refresh the app icon for a more refined, textured look"),
            L("捐赠支持升级为实时更新", "Donation support upgraded to real-time updates"),
            L("重构课程提醒逻辑，提升稳定性", "Rebuild course reminder logic for better stability"),
            L("更新设置新增beta版通道", "Update settings add a beta channel"),
            L("自定义时间切换时跟随课程节次", "Custom times follow the course period when switching"),
            L("周末无课自动跳转下周课表", "Auto-jump to next week's timetable when the weekend is free"),
            L("AI文本导入支持增量导入", "AI text import supports incremental importing"),
            L("优化滚轮选择器选择逻辑", "Improve wheel picker selection logic"),
            L("课程详情页打开时定位选择周课程", "The course detail page locates the selected week's course on open"),
            L("优化天气获取失败时的提示", "Improve the prompt when weather fetching fails"),
            L("优化课程详情弹窗内排序", "Improve ordering within the course detail dialog"),
            L("优化添加页面弹出动效，更流畅", "Improve the add page pop-in animation for smoother motion"),
            L("优化课程表左右及上下滑动跟手性", "Improve timetable responsiveness for horizontal and vertical swipes"),
            L("优化教务导入更新逻辑，更快更轻", "Improve academic import update logic for speed and lightness"),
            L("优化安装应用更新时的流程", "Improve the app update installation flow"),
            L("修复课程时间可能被意外重置的问题", "Fix course times possibly being reset unexpectedly")
        )
    ),
    ChangelogEntry(
        version = "v1.4.9-0905",
        date = "2026-09-05",
        changes = listOf(
            L("新增自定义选项：课程表默认主题，壁纸模糊开关，卡片折射自定义，卡片文字缩放比例自定义，显示地点开关，显示教师开关", "Add options: default timetable theme, wallpaper blur toggle, card refraction, card text scaling, show location and show teacher"),
            L("优化添加页面弹出时可能出现的掉帧", "Reduce possible frame drops when the add page pops up"),
            L("修复教务导入可能无法导入开学时间", "Fix academic import possibly failing to import the term start date")
        )
    ),
    ChangelogEntry(
        version = "v1.4.8-0903",
        date = "2026-09-03",
        changes = listOf(
            L("精简重构课表外观功能，带来更丰富自定义选项，新增支持清除壁纸、新增支持选择文字颜色", "Streamline and rebuild timetable appearance with richer options: clear wallpaper and text color selection"),
            L("新增支持设置特殊课程时间", "Add setting times for special courses"),
            L("调休工作日不再自动获取假期", "Adjusted working days no longer auto-fetch holidays"),
            L("优化部分数据层逻辑", "Improve some data layer logic"),
            L("优化小部件显示效果", "Improve widget appearance"),
            L("优化部分界面流畅度与性能", "Improve smoothness and performance on some screens"),
            L("优化课程提醒稳定性", "Improve course reminder stability"),
            L("修复部分界面UI与颜色异常", "Fix UI and color issues on some screens"),
            L("修复教务导入无法导入开学时间", "Fix academic import failing to import the term start date")
        )
    ),
    ChangelogEntry(
        version = "v1.4.6-0830",
        date = "2026-08-30",
        changes = listOf(
            L("新增课程表显示假期/调休课程状态", "Add holiday/adjustment course status on the timetable"),
            L("新增今日助手获取定位后自动存储", "Add auto-saving of location after the Today assistant fetches it"),
            L("优化今日助手提示内容，更生动合理", "Improve Today assistant tips to be livelier and more sensible"),
            L("修复添加自定义时间课程闪退的问题", "Fix crashes when adding custom-time courses"),
            L("修复今日助手课程名称较长时显示异常", "Fix Today assistant display issues with long course names"),
            L("修复字体较大时小组件显示不全的问题", "Fix widget content clipping with larger fonts")
        )
    ),
    ChangelogEntry(
        version = "v1.4.5-0828",
        date = "2026-08-28",
        changes = listOf(
            L("小部件新增Pad端版本，且小部件页改版", "Add tablet widget variants and revamp the widget page"),
            L("修复课程提醒始终不发送的问题", "Fix course reminders never sending"),
            L("修复课程位置异常导致倒计时错误的问题", "Fix wrong countdowns caused by odd course locations"),
        )
    ),
    ChangelogEntry(
        version = "v1.4.4-0827",
        date = "2026-08-27",
        changes = listOf(
            L("合入 @九磅九万便士 pr，支持跳过节假日课程、假期调休补课功能", "Merge @九磅九万便士's PR: skip holiday courses and support holiday make-up classes"),
            L("合入 @玖柒 pr，支持单节课展示教师/地点", "Merge @玖柒's PR: show teacher/location per course"),
            L("升级天气数据源，并可在「我的」-「应用偏好设置」中切换天气数据源", "Upgrade the weather data source, switchable in \"Me\" - \"App Preferences\""),
            L("教务导入补齐缺失的上游仓库重要功能", "Academic import catches up on missing upstream features"),
            L("优化了应用偏好设置页面排版", "Improve the App Preferences page layout"),
            L("修复课程提醒可能意外发送的问题", "Fix course reminders possibly firing unexpectedly"),
            L("修复周数没有正确简写的问题", "Fix week numbers not abbreviated correctly"),
            L("修复教务导入搜索功能无法正确搜索的问题", "Fix the academic import search not working correctly")
        )
    ),
    ChangelogEntry(
        version = "v1.4.3-0825",
        date = "2026-08-25",
        changes = listOf(
            L("新增支持后端公告能力", "Add backend announcement support"),
            L("合入 @九磅九万便士 pr，支持超级岛光效，隐藏最近任务卡片", "Merge @九磅九万便士's PR: Super Island light effects and recents card hiding"),
            L("新增支持自定义节次名称", "Add custom period names"),
            L("优化课程卡片内容排版", "Improve course card content layout"),
            L("优化部分组件视觉效果", "Improve visuals of some components"),
            L("修复排班课表功能存在的各种异常", "Fix various shift timetable issues"),
            L("修复部分按钮圆角锯齿的问题", "Fix jagged corners on some buttons"),
            L("修复点击弹窗空白处会意外关闭的问题", "Fix dialogs closing unexpectedly when tapping blank areas"),
            L("修复部分弹窗内容/背景显示异常", "Fix content/background display issues in some dialogs"),
            L("修复自定义时间课程结束时间计算错误", "Fix wrong end time calculation for custom-time courses"),
            L("修复自定义时间课程异常合并的问题", "Fix abnormal merging of custom-time courses"),
            L("修复教务导入学校列表更新失败的问题", "Fix school list update failures in academic import")
        )
    ),
    ChangelogEntry(
        version = "v1.4.1-0822",
        date = "2026-08-22",
        changes = listOf(
            L("新增支持课程自定义时间", "Add custom course times"),
            L("课程提醒功能全面进化，优化原生实况通知样式，支持上课静音，支持自定义缩略态显示内容，同时小米超级岛去除Shizuku硬性要求", "Course reminders overhauled: improved native Live Notification styling, in-class mute, customizable collapsed content, and no more mandatory Shizuku for Xiaomi Super Island"),
            L("适配拾光教务导入仓库V2", "Adapt to the ShiGuang academic import repo V2"),
            L("优化部分界面深浅色效果与逻辑", "Improve light/dark styling and logic on some screens"),
            L("修复下拉弹窗缩放中心错位的问题", "Fix off-center scaling of pull-down dialogs"),
            L("修复学期未开始，明日提醒仍发送的问题", "Fix tomorrow's reminder still firing before the term starts"),
            L("修复更新学校列表时进度条丢失的问题", "Fix the progress bar disappearing when updating the school list"),
            L("修复周次计算可能出错/显示异常的问题", "Fix possible week calculation errors and display glitches"),
            L("修复今日页课程状态计算错误的问题", "Fix wrong course status calculations on the Today page")
        )
    ),
    ChangelogEntry(
        version = "v1.4.0-0817",
        date = "2026-08-17",
        changes = listOf(
            L("应用风格彻底重构为HyperOS4风格", "App style fully rebuilt as HyperOS 4"),
            L("全新视觉体验，全局高级材质", "Brand-new visuals with advanced materials throughout"),
            L("Pad端全面焕新，更好用更易用", "Tablet experience fully refreshed for better usability"),
            L("今日页、课程表页将根据壁纸自动切换主题", "Today and timetable pages auto-switch themes based on the wallpaper"),
            L("优化部分连贯动画、课程表页面的流畅度", "Improve smoothness of some shared-element transitions and the timetable page"),
            L("修复ShortCut菜单溢出屏幕的问题", "Fix the shortcut menu overflowing the screen"),
            L("修复切换外观后状态栏反色异常的问题", "Fix abnormal status bar color inversion after switching appearance"),
            L("修复超级岛可能不稳定的问题", "Fix possible Super Island instability"),
            L("注意：旧搭配请手动重新设置壁纸，否则可能出现反色异常/主题异常等问题", "Note: for old setups, re-apply your wallpaper manually or you may see color inversion/theme issues")
        )
    ),
    ChangelogEntry(
        version = "v1.3.8-0811",
        date = "2026-08-11",
        changes = listOf(
            L("优化课程表界面视觉效果", "Improve timetable page visuals"),
            L("优化课表外观设置逻辑", "Improve timetable appearance settings logic"),
            L("支持长按课程快速调课", "Support quick rescheduling via course long-press"),
            L("提示：在1.4.0版本后将不会再提供应用风格选择，将统一为类HyperOS4风格", "Note: after version 1.4.0 the app style picker will be removed; everything becomes HyperOS 4-like")
        )
    ),
    ChangelogEntry(
        version = "v1.3.7-0806",
        date = "2026-08-06",
        changes = listOf(
            L("为Pad端进行适配", "Adapt for tablets"),
            L("适配HyperOS无极窗口", "Adapt to HyperOS freeform windows"),
            L("新增今日页支持显示壁纸（可在「我的」-「应用偏好设置」中关闭）", "Today page can show the wallpaper (disable in \"Me\" - \"App Preferences\")"),
            L("课程管理支持长按卡片进行 [ 编辑 / 删除 ] ", "Course management: long-press a card to [ edit / delete ] "),
            L("新增支持调节课程卡片内容对齐方式", "Add course card content alignment options"),
            L("优化液态玻璃材质", "Improve the liquid glass material"),
            L("优化课程卡片可读性", "Improve course card readability"),
        )
    ),
    ChangelogEntry(
        version = "v1.3.4-0801",
        date = "2026-08-01",
        changes = listOf(
            L("该版本注重于优化应用基础体验", "This release focuses on polishing the core experience"),
            L("重构课程管理编辑页UI，并可快速添加删除/编辑课程，同时支持自动填充内容", "Rebuild the course editor UI with quick add/delete/edit and auto-fill support"),
            L("优化应用视觉一致性", "Improve app visual consistency"),
            L("优化应用部分界面UI", "Improve UI on some screens"),
            L("优化今日助手卡片显示内容", "Improve Today assistant card content"),
            L("优化应用选取壁纸时的逻辑", "Improve the wallpaper picking logic"),
            L("优化应用使用时的性能", "Improve performance during use"),
            L("优化组件动画/连贯动画的效果", "Improve widget animation and shared-element transition effects"),
            L("优化课程表页课程卡片显示效果", "Improve course card rendering on the timetable page"),
            L("优化课程表页部分判断逻辑显示", "Improve some conditional display logic on the timetable page"),
            L("修复了一些已知问题", "Fix some known issues"),
        )
    ),
    ChangelogEntry(
        version = "v1.3.2-0726",
        date = "2026-07-26",
        changes = listOf(
            L("课程管理编辑页支持快速删除课程", "The course editor supports quick course deletion"),
            L("适配支持安卓12", "Add support for Android 12"),
            L("优化ICS导入逻辑", "Improve ICS import logic"),
            L("优化导入导出功能", "Improve import/export features"),
            L("优化本地备份/云备份功能", "Improve local and cloud backup features"),
            L("优化编辑课程页UI", "Improve the course editor UI"),
            L("修复今日日期跳转弹窗问题", "Fix the today date jump dialog"),
        )
    ),
    ChangelogEntry(
        version = "v1.3.1-0723",
        date = "2026-07-23",
        changes = listOf(
            L("优化今日页天气显示图标", "Improve the weather icon on the Today page"),
            L("优化应用部分界面流畅度", "Improve smoothness on some screens"),
            L("适配Pad端显示效果", "Adapt display effects for tablets"),
            L("修复ICS导入/导出问题", "Fix ICS import/export issues"),
            L("修复部分界面UI异常", "Fix some UI issues"),
            L("修复部分连贯动画圆角异常", "Fix corner radius glitches in some shared-element transitions"),
            L("修复智能显示周末问题", "Fix smart weekend display"),
            L("修复课程节数与时间问题", "Fix course period count and time issues"),
        )
    ),
    ChangelogEntry(
        version = "v1.3.0-0722",
        date = "2026-07-22",
        changes = listOf(
            L("新增「课程节数与时间」页面，整合课程时间管理与课表节次功能，并支持保存时间配置，可在不同配置间快速切换", "Add a \"Course Periods & Times\" page combining time management and timetable periods, with savable configs for quick switching"),
            L("「显示周末」升级为「智能显示周末」，开启后根据周课程智能显示周末", "\"Show Weekend\" upgraded to \"Smart Show Weekend\", showing weekends based on the week's courses"),
            L("新增「课程管理」页面，更方便统一管理课程，并可以点击课程进行编辑", "Add a \"Course Management\" page for centralized course management; tap a course to edit"),
            L("新增「备份与迁移」页面，整合WebDAV云备份及课表导入/导出功能", "Add a \"Backup & Migration\" page combining WebDAV cloud backup and timetable import/export"),
            L("导出支持ICS日历格式、单独选择课表，并可自定义保存位置", "Export supports ICS calendar format, per-timetable selection and custom save locations"),
            L("新增本地备份功能，支持将课表数据备份至设备本地存储中", "Add local backup to save timetable data to device storage"),
            L("今日页新增「今日助手」，智能显示课程提醒与当日信息", "Today page adds a \"Today Assistant\" with smart course reminders and daily info"),
            L("今日页新增左右滑动快速查看其他日期课程", "Today page adds horizontal swiping to quickly view other days' courses"),
            L("新增今日课程/课程提醒标准版小部件", "Add standard Today courses and course reminder widgets"),
            L("更新支持自定义选择下载源", "Updates support choosing a custom download source"),
            L("课程表页当前课程节次新增高亮显示", "The current period is now highlighted on the timetable page"),
            L("今日页新增跳转日期，可快速跳转日期", "Today page adds a date jump for quick navigation"),
            L("为众多页面添加卡片入场退场动画", "Add card enter/exit animations to many pages"),
            L("优化应用防呆提示和防呆限制", "Improve foolproof prompts and guards"),
            L("优化部分界面UI效果与一致性", "Improve UI effects and consistency on some screens"),
            L("优化底部弹窗效果，支持模糊效果", "Improve bottom sheets with blur support"),
            L("优化连贯动画的动画效果", "Improve shared-element transition effects"),
            L("优化部分动画的流畅度", "Improve smoothness of some animations"),
            L("优化部分课程表状态显示逻辑", "Improve some timetable status display logic"),
            L("修复更新应用后仍提示更新的问题", "Fix the update prompt persisting after updating the app"),
            L("修复拾光JSON导入课程颜色分配异常", "Fix course color assignment in ShiGuang JSON import"),
            L("修复添加卡片可能出现在课程卡片底部的问题", "Fix the add card possibly appearing at the bottom of course cards"),
        )
    ),
    ChangelogEntry(
        version = "v1.2.6-0716",
        date = "2026-07-16",
        changes = listOf(
            L("优化部分动画曲线", "Improve some animation curves"),
            L("修复重命名课表导致课表丢失的问题", "Fix timetable loss caused by renaming"),
            L("修复关于应用页面无法全部展示的问题", "Fix About page content being cut off"),
            L("修复更新设置开关与实际不符的问题", "Fix the update toggle not matching the actual state")
        )
    ),
    ChangelogEntry(
        version = "v1.2.5-0715",
        date = "2026-07-15",
        changes = listOf(
            L("支持设置是否显示午休晚休分界线", "Add an option to show noon/evening break dividers"),
            L("支持自定义调节壁纸亮度", "Support custom wallpaper brightness"),
            L("重新设计关于应用页面", "Redesign the About page"),
            L("全面升级AI文本导入，更好用", "Fully upgrade AI text import for better usability"),
            L("新增今日课程2×2小部件", "Add a 2×2 Today courses widget"),
            L("优化了部分设置使用逻辑", "Improve some settings logic"),
            L("优化部分UI界面显示效果", "Improve some UI appearance"),
            L("优化设置壁纸后课程卡片可读性", "Improve course card readability over wallpapers"),
            L("优化部分课程显示/周判断逻辑", "Improve some course display/week determination logic"),
            L("优化课程跨午晚休显示逻辑", "Improve display of courses spanning noon/evening breaks"),
            L("修复教务导入无法导入时间的问题", "Fix academic import failing to import times"),
            L("修复添加课表后数据更新不及时的问题", "Fix stale data right after adding a timetable"),
            L("修复删除当前课表导致的连环崩溃", "Fix a cascade crash from deleting the current timetable"),
            L("修复小部件页在HyperOS3风格显示异常", "Fix widget page display glitches in HyperOS 3 style"),
            L("修复部分界面显示异常问题", "Fix display issues on some screens")
        )
    ),
    ChangelogEntry(
        version = "v1.2.2-0712",
        date = "2026-07-12",
        changes = listOf(
            L("重构云同步功能，改为手动上传下载模式", "Rebuild cloud sync as manual upload/download"),
            L("修复排班课表详情异常占满全屏的问题", "Fix shift timetable details wrongly filling the whole screen"),
            L("修复关于页面信息缺失的问题", "Fix missing info on the About page"),
            L("修复部分界面显示异常的问题", "Fix display issues on some screens"),
            L("修复教务系统导入无法使用的问题", "Fix the academic system import being unusable"),
            L("修复切换课表页面显示异常的问题", "Fix display issues on the timetable switch page")
        )
    ),
    ChangelogEntry(
        version = "v1.2.0-0710",
        date = "2026-07-10",
        changes = listOf(
            L("新增课表外观自定义，支持自定义课程卡片透明度、模糊度、高度和圆角", "Add timetable appearance customization: card opacity, blur, height and corner radius"),
            L("支持自定义背景图片", "Support custom background images"),
            L("支持应用风格选择，新增液态玻璃效果", "Add app style selection with a new liquid glass effect"),
            L("支持学期未开始和学期结束放假状态显示", "Support before-term and after-term holiday status display"),
            L("桌面小部件支持显示学期状态", "Home screen widgets show the term status"),
            L("为 Pad 端进行专属适配", "Dedicated adaptation for tablets"),
            L("重构应用代码结构，提高可维护性", "Rebuild the code structure for better maintainability"),
            L("小米超级岛支持 business 参数", "Xiaomi Super Island supports the business parameter"),
            L("修复小米超级岛通知重复发送的问题", "Fix duplicate Xiaomi Super Island notifications"),
            L("修复 HyperOS 不显示小部件预览图的问题", "Fix HyperOS not showing widget preview images"),
            L("修复退出排班模式后首页显示不正确的问题", "Fix incorrect home page display after exiting shift mode"),
            L("修复课程提醒页授权后提示仍然存在的问题", "Fix the permission prompt persisting on the course reminder page")
        )
    ),
    ChangelogEntry(
        version = "v1.1.1-0704",
        date = "2026-07-04",
        changes = listOf(
            L("修复小米超级岛功能失效的问题", "Fix Xiaomi Super Island not working")
        )
    ),
    ChangelogEntry(
        version = "v1.1.0-0704",
        date = "2026-07-04",
        changes = listOf(
            L("应用更名为 Nexio 课程表", "App renamed to Nexio Schedule"),
            L("彻底重构课程提醒功能", "Fully rebuild the course reminder feature"),
            L("课程提醒支持小米超级岛", "Course reminders support Xiaomi Super Island"),
            L("小部件支持明日课程预告", "Widgets support tomorrow's course preview"),
            L("快捷设置时间支持自定义", "Quick settings times are customizable"),
            L("新增 WebDAV 云同步功能", "Add WebDAV cloud sync"),
            L("支持设置悬浮底栏", "Add a floating bottom bar setting"),
            L("初步适配 Pad 端", "Initial tablet adaptation")
        )
    ),
    ChangelogEntry(
        version = "v1.0.0-0701",
        date = "2026-07-01",
        changes = listOf(
            L("新增教务系统导入功能", "Add academic system import"),
            L("新增可选默认首页设置", "Add a default home page setting"),
            L("新增深浅色可选跟随系统", "Add follow-system light/dark mode"),
            L("新增排班课表功能", "Add the shift timetable feature"),
            L("支持应用内检查更新", "Support in-app update checks"),
            L("优化应用整体视觉效果", "Improve overall app visuals"),
            L("优化多处 UI 表现", "Improve UI in many places"),
            L("优化应用多处流畅度", "Improve smoothness in many places"),
            L("修复课程表可能的偏移问题", "Fix possible timetable offsets"),
            L("修复课程表可能的显示异常", "Fix possible timetable display issues")
        )
    ),
    ChangelogEntry(
        version = "v0.0.8-0628",
        date = "2026-06-28",
        changes = listOf(
            L("新增课程提醒功能，支持安卓原生实况通知", "Add course reminders with Android native Live Notifications"),
            L("新增桌面小部件", "Add home screen widgets"),
            L("优化应用部分交互触感", "Improve some interaction haptics"),
            L("修复部分动画问题", "Fix some animation issues")
        )
    ),
    ChangelogEntry(
        version = "v0.0.6-0621",
        date = "2026-06-21",
        changes = listOf(
            L("新增部分一镜到底动画效果", "Add some seamless one-take transition effects"),
            L("新增点击查看课程详情功能", "Add tap-to-view course details"),
            L("支持 ICS 文件导入课表", "Support importing timetables from ICS files"),
            L("圆角改用 kyant 库实现", "Corner radii are now implemented with the kyant library"),
            L("优化部分 UI 细节", "Polish some UI details"),
            L("优化部分界面流畅度", "Improve smoothness on some screens"),
            L("修复部分界面 UI 问题", "Fix some UI issues"),
            L("修复部分功能逻辑问题", "Fix some functional logic issues")
        )
    ),
    ChangelogEntry(
        version = "v0.0.4-0616",
        date = "2026-06-16",
        changes = listOf(
            L("初版 Alpha 测试版发布", "First Alpha release"),
            L("基础功能完善，部分核心功能仍在开发中", "Basics are in place; some core features are still in development")
        )
    )
)
