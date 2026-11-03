package com.haooz.chedule.i18n

/**
 * 今日助手卡文案。多为随时间轮换的动态模板句，插值一律 %s/%d 按出现顺序传参。
 *
 * 与天气 API 返回值比较的中文天气类型词（"晴"、"多云" 等）属于数据键，
 * 保留在调用方（TodayAssistantCard.kt 的 getWeatherIconRes），不进本命名空间。
 */
object AssistantStr {

    // ── 卡片主行：状态标签与占位 ──
    val inClass = L("正在上课", "In class")
    val nextClass = L("下节课", "Next class")
    val noCourseTodayRest = L("今天没有课程，好好休息吧！", "No classes today — enjoy the break!")
    val noCourseNow = L("当前没有课程", "No class right now")

    // ── 剩余时间口径（最后一分钟秒级，其余分钟级） ──
    val alreadyStarted = L("已开始", "Started")
    val alreadyEnded = L("已结束", "Ended")
    val secondsToStart = L("%d 秒后", "in %d s")
    val secondsLeft = L("还剩 %d 秒", "%d s left")
    val durationHourMinute = L("%d小时%d分钟", "%d hr %d min")
    val durationHour = L("%d小时", "%d hr")
    val durationMinute = L("%d 分钟", "%d min")
    val durationToStart = L("%s后", "in %s")
    val durationLeft = L("还剩 %s", "%s left")

    // ── 晚间追加的明日预告 ──
    val tomorrowFree = L(" · 明天没课，闹钟关了吧", " · No classes tomorrow. Turn the alarm off")
    val tomorrowFirstTbd = L("第一节还没定", "first period's still TBD")
    val tomorrowStartsWith = L("%s开头", "first up %s")
    val tomorrowCount = L(" · 明天 %d 节，%s，早点睡", " · %d tomorrow, %s. Sleep early")

    // ── 教室 ──
    val roomTbd = L("教室待定", "Room TBD")

    // ── 节次时间残缺 ──
    val timesMissingFillFirst = L("%d 节课，时间没填，你让我怎么提醒", "%d classes and no times filled in — how am I supposed to remind you?")
    val timesMissingNoCountdown = L("课表里有 %d 节，时间是空的，光写课名可不算安排", "%d classes, empty time fields. A name isn't a schedule")
    val timesMissingFixIt = L("有 %d 节课没填时间，去补一下能累到哪", "%d classes with no times. Go fill them in — it's not that hard")

    // ── 今天没课（分时段轮换） ──
    val freeMorningAlarm = L("没课就别定闹钟了，定了也不会起", "No classes — an alarm you'll ignore isn't an alarm")
    val freeMorningBed = L("没课的早晨，你和床的关系又好了", "No class this morning. You and the bed are getting along great")
    val freeMorningSleepIn = L("多睡会儿行，睡到中午就是你的问题了", "Sleep in, fine. Noon is pushing it")
    val freeAmPlanIt = L("上午空着，别一睁眼就交给手机", "Morning's open — don't hand it straight to your phone")
    val freeAmLibrary = L("上午空着，图书馆有座，借口也顺便想好了吧", "Open morning, seats at the library. Got your excuse ready too?")
    val freeAmPrecious = L("这样的上午挺贵的，刷两下就没了", "Mornings like this aren't cheap. Two scrolls and it's gone")
    val freeNoonMeals = L("没课也别拿零食当饭，胃不欠你的", "No class doesn't make snacks a meal. Your stomach didn't sign up")
    val freeNoonStomach = L("中午随便吃，下午难受的是你自己", "Eat whatever at noon — you're the one paying for it later")
    val freeNoonFreedom = L("这顿饭没人催你，难得", "Nobody's rushing this meal. Rare")
    val freePmRealWork = L("下午空着，正事别又拖到晚上", "Afternoon's free — don't push the real work to tonight")
    val freePmSunshine = L("天这么好还躺着，也不怕闷", "Weather like this and you're still lying down. Not bored yet?")
    val freePmProgressBar = L("空着的下午，进度条稳在 0%，真稳定", "Empty afternoon, progress bar steady at 0%. Impressively stable")
    val freeEveUnwind = L("放松可以，别一路放松到三点", "Relax, sure. Just not until 3am")
    val freeEvePhone = L("晚上是自由的，手机都替你想好了", "Free evening. Your phone has it all planned out for you")
    val freeEveStayUp = L("我知道你会熬，还是说一句：别熬太狠", "I know you'll stay up. Just saying: not too far")
    val freeNightWrapUp = L("早点收，夜里熬的时间不算数", "Wrap it up. Late-night hours don't count")
    val freeNightSleep = L("该睡了，没课的夜晚通常以熬夜收场", "Bed. Class-free nights usually end at 3am")
    val freeNightFaceYourself = L("睡吧，明天起不起得来是另一回事", "Sleep. Whether you get up tomorrow is a separate matter")
    val noClassToday = L("今天没有课", "No classes today")

    // ── 正在上课 ──
    // 只描述「课上到哪一段、现在该干嘛」；具体剩余时间由卡片顶部的倒计时负责，
    // 这里再报一次数字就是重复，所以整段文案一律不带分钟数。
    val endWrapUpNow = L("就要到点了，收吧", "Almost there. Pack it up")
    val endAtTheBell = L("铃在门口了，先别动", "Bell's at the door — hold still")
    val endFinalSeconds = L("最后几十秒，忍住", "Last few seconds. Hold it")
    val endWindDown = L("进收尾了，东西先归拢", "Winding down — gather your stuff")
    val endPackUp = L("书包可以先热身了", "Backpack can start warming up")
    val endHangIn = L("再撑一下，快了", "Almost. Hang on")
    val ongoingKeepRhythm = L("节奏别散，这儿最容易泄气", "Hold the rhythm — this is where people deflate")
    val ongoingSecondHalf = L("后半段了，最容易走神的时候", "Back half. Peak wandering-off hours")
    val ongoingKeyPart = L("重点大概就在这一段，别错过", "The important bit is roughly here. Don't miss it")
    val ongoingMidClass = L("中段最容易散，盯住", "Middle stretch — easiest place to drift")
    val ongoingOwnPace = L("按自己的来，别跟着乱", "Your pace. Don't get dragged along")
    val ongoingNoEarlyDone = L("还没下课，别急着收心", "Not over yet — don't check out early")
    val ongoingJustStarted = L("刚开讲，坐稳别飘", "Just started. Sit still")
    val ongoingSaveFocus = L("后面还长，注意力省着点用", "Long way to go — ration the attention")
    val ongoingLongStretch = L("这节还长，别一开始就透支", "Long session. Don't burn out early")

    // ── 连堂间隙 ──
    val gapBackToBack = L(" · 连堂，东西别收太干净", " · Back-to-back — don't pack up properly")
    val gapQuickRefuel = L(" · 这节课下课就上下一节，补给别磨", " · One ends, the next begins. Refuel fast")
    val gapCatchBreath = L(" · 下课后能喘口气，别浪费", " · You get a breather after. Don't waste it")
    val gapLeaveRoom = L(" · 下课后有空档，想走就走", " · Real gap after this. Leave if you want")

    // ── 课间 ──
    val breakGrabSeat = L("要上课了，座位先占上", "Class in a minute. Claim a seat")
    val breakBellAboutToRing = L("铃要响了，还不回", "Bell's about to go. Still here?")
    val breakZeroBuffer = L("没缓冲了，直接进", "No buffer. Walk straight in")
    val breakHeadBack = L("回 %s 吧，别磨了", "Back to %s. Move")
    val breakDontCutClose = L("回 %s，别又踩点", "Back to %s — and don't cut it close")
    val breakOnYourFeet = L("课间收尾了，起身去 %s", "Break's wrapping up. Up you get, %s")
    val breakWatchTheClock = L("课间不多了，回 %s 别踩点", "Break's nearly gone. Back to %s, no cutting close")
    val breakClassWaiting = L("回 %s 吧，%s 等着呢", "Back to %s — %s is waiting")
    val breakPlanRoute = L("课间不多了，去 %s 的路想好", "Break's short. Work out the route to %s")
    val breakNextRoom = L("课间过半，下节去 %s", "Half the break gone. Next: %s")
    val breakChill = L("课间，下节%s在 %s", "On break · next up %s in %s")
    val breakNoNewStarts = L("缓缓就行，别开新局", "Recharge only. No new projects")
    val breakDontWander = L("下节在 %s，别跑远", "Next class is in %s. Don't wander")
    val breakLongBreak = L("长课间，翻一眼%s也不亏", "Long break. Flipping through %s wouldn't hurt")
    val breakRecover = L("回点血，别掉段", "Recover a little. Don't lose your edge")
    val breakOwnRhythm = L("还早，按自己的来", "Still early. Do it your way")
    val breakBigGap = L("大空档，下节%s在 %s", "Big gap · next up %s in %s")
    val breakTimeYours = L("时间归你，别浪费得太明显", "Your time. Try not to waste it too obviously")

    // ── 今日课已上完 ──
    val doneClaimKnowledge = L("%d 节都完了，学到多少自己有数", "%d classes done. You know how much actually stuck")
    val doneNextLevel = L("%d 节结束，作业才是正题", "%d classes cleared. Homework is the real boss")
    val doneOffClock = L("%d 节完事，人放了，进度你自己看着办", "%d classes done. Off the clock — progress is your problem now")
    val doneOnPlan = L("已上完 %d/%d 节，剩下按计划走", "%d/%d done. Rest as planned")
    val doneProgress = L("进度 %d/%d，收尾别掉链子", "%d/%d. Don't fumble the finish")
    val doneSteadyFinish = L("走完就行，别在最后出岔子", "Walk it out. Don't trip at the end")

    // ── 全天强度预告 ──
    val planHeavy = L("今天 %d 节，不轻松", "%d classes. Not a light day")
    val planHeavyRation = L("%d 节，体力省着点用", "%d classes. Ration the energy")
    val planHeavyBattery = L("%d 节，上午别把电用光", "%d classes. Don't drain the battery by noon")
    val planNormal = L("%d 节，正常强度", "%d classes. Normal load")
    val planNormalPace = L("%d 节，节奏还行", "%d classes. Pace is fine")
    val planNormalFollow = L("%d 节，照着课表走", "%d classes. Follow the schedule")
    val planFewDontFloat = L("就 %d 节，别飘", "Only %d classes. Don't float away")
    val planFewSpendWisely = L("%d 节，少是少，别全糊掉", "Just %d. Try not to waste them too visibly")
    val planLightDay = L("%d 节，轻是轻，别全刷掉", "%d classes. Light, not empty")

    // ── 第一节课前（按「离上课还有多远」分档，但不报分钟数） ──
    val firstFarHours = L("%s · 第一节还早，慢慢来", "%s · First class is ages away")
    val firstFarOwnRhythm = L("%s · 时间还多，按自己的来", "%s · Plenty of time. Take it your way")
    val firstFarDontCrash = L("%s · 时间大把，别开局就躺", "%s · Loads of time. Don't collapse at the start")
    val first90Room = L("%s · 时间够，去 %s 不用跑", "%s · Time to spare. No need to run to %s")
    val first90Until = L("%s · %s还早着呢", "%s · %s isn't close yet")
    val first90NoRush = L("%s · 不急，但别一直躺着", "%s · No rush. Lying there is also an option, apparently")
    val first30GetReady = L("%s · 该准备了，去 %s", "%s · Time to get ready. Head for %s")
    val first30PackUp = L("%s · %s，东西该收了", "%s · %s. Start packing")
    val first30WarmUp = L("%s · 状态预热一下", "%s · Warm up a little")
    val first15HeadOut = L("该动身了，去 %s", "Time to move. %s")
    val first15RouteSet = L("%s，别走错教室", "%s. Don't walk into the wrong room")
    val first15Buffer = L("去 %s，留点缓冲", "Leave some buffer on the way to %s")
    val first5NoDawdle = L("去 %s 别磨蹭", "No dawdling on the way to %s")
    val first5SteadyWalk = L("去 %s，走稳点", "Steady walk to %s")
    val first5LeaveNow = L("去 %s，现在走正好", "Now's a good time to leave for %s")
    val first1KeepPace = L("去 %s，别跑也别晃", "Even pace to %s. No running, no strolling")
    val first1NoSprint = L("去 %s，冲也快不了多少", "Sprinting to %s won't help")
    val first1TakeSeat = L("到 %s 先占座", "Grab a seat first at %s")
    val hintAfternoonMore = L(" · 下午还有 %d 节，别高兴太早", " · %d more this afternoon. Don't celebrate yet")
    val hintEveningMore = L(" · 晚上还有 %d 节，别急着松", " · %d more tonight. Don't relax yet")
    val rushHurryIn = L("快进 %s，别磨", "Get into %s. Now")
    val rushBellRinging = L("铃都响了，%s 就在前面", "Bell's going. %s is right there")
    val rushStraightIn = L("%s，直接进，别在门口站着", "%s. Walk in, don't stand at the door")

    // ── 兜底：有课 ──
    val readyFollowSchedule = L("今天 %d 节，照着走", "%d classes. Follow along")
    val readyDayPlanned = L("%d 节，都排好了，执行吧", "%d classes. All planned. Execute")
    val readyScheduleSet = L("%d 节，就绪", "%d classes. Ready")

    // ── 天气/定位 ──
    val locationFailedTapRetry = L("无法获取位置信息·点击重试", "Can't get your location · Tap to retry")
    val locationPermissionNeeded = L("需要定位权限·点击授权", "Location permission needed · Tap to allow")
    val weatherServiceTapRetry = L("天气服务异常·点击重试", "Weather service error · Tap to retry")
}
