package com.haooz.chedule.i18n

/**
 * 今日助手卡文案。动态模板句中的 %s/%d 按出现顺序传参。
 *
 * 与天气 API 返回值比较的中文天气词（"晴"、"多云" 等）属于数据键，
 * 保留在调用方（TodayAssistantCard.kt 的 getWeatherIconRes）。
 */
object AssistantStr {

    // ── 卡片主行：状态标签与占位 ──
    val inClass = L("正在上课", "In class")
    val nextClass = L("下节课", "Next class")
    val noCourseTodayRest = L("今天没有课程，好好休息吧！", "No classes today — enjoy your day!")
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
    val tomorrowFree = L(" · 明天没课", " · No classes tomorrow")
    val tomorrowFirstTbd = L("首节时间待定", "first class time TBD")
    val tomorrowStartsWith = L("首节 %s", "first up %s")
    val tomorrowCount = L(" · 明天 %d 节，%s", " · %d tomorrow, %s")

    // ── 教室 ──
    val roomTbd = L("教室待定", "Room TBD")

    // ── 节次时间残缺 ──
    val timesMissingFillFirst = L("今天有 %d 节课尚未设置时间，请先补充作息", "%d classes are missing times — set up your schedule first")
    val timesMissingNoCountdown = L("今天有 %d 节课未填时间，暂无法倒计时", "%d classes have no times set; countdown unavailable")
    val timesMissingFixIt = L("有 %d 节课缺少上课时间，可在作息设置中补全", "%d classes have no class times — add them in time settings")

    // ── 今天没课（分时段轮换） ──
    val freeMorningAlarm = L("今天没课，可以多睡一会儿", "No classes today — feel free to sleep in")
    val freeMorningBed = L("清晨没课，享受轻松的早晨", "A quiet, class-free morning")
    val freeMorningSleepIn = L("今天没有安排课程，慢慢开启新的一天", "No classes on the calendar — take your morning easy")
    val freeAmPlanIt = L("上午空闲，适合安排自己的计划", "Open morning — a good stretch for your own plans")
    val freeAmLibrary = L("上午没课，去图书馆或自习室都很合适", "Free morning — great time for the library or quiet study")
    val freeAmPrecious = L("安静的上午，按自己的节奏来就好", "A calm morning to move at your own pace")
    val freeNoonMeals = L("中午没课，记得好好吃顿饭", "No class at noon — take time for a proper meal")
    val freeNoonStomach = L("午间空闲，吃饱后可以小憩一会儿", "Free midday — lunch and a short rest sound good")
    val freeNoonFreedom = L("没有赶课压力的中午，慢慢吃", "No rush to class — enjoy a relaxed lunch")
    val freePmRealWork = L("下午空闲，可以专心处理手头的事", "Open afternoon — good time to focus on what matters")
    val freePmSunshine = L("下午没课，有空出去走走也不错", "No afternoon classes — nice time for a walk outside")
    val freePmProgressBar = L("下午时间充裕，学习或休息都很自在", "Plenty of room this afternoon to study or unwind")
    val freeEveUnwind = L("晚上没课，放松一下充充电", "Free evening — time to unwind and recharge")
    val freeEvePhone = L("今晚没有课程安排，好好享受闲暇时光", "No classes tonight — enjoy your free evening")
    val freeEveStayUp = L("晚上空闲，注意劳逸结合", "Open evening — take it easy and rest well")
    val freeNightWrapUp = L("夜深了，早点休息", "It's getting late — time to wind down")
    val freeNightSleep = L("今晚没课，祝你好梦", "No classes tonight — sleep well")
    val freeNightFaceYourself = L("夜里安静，早些入睡养足精神", "Quiet night — get some good rest for tomorrow")
    val noClassToday = L("今天没有课", "No classes today")

    // ── 正在上课 ──
    val endWrapUpNow = L("快下课了，准备收尾", "Almost done — wrapping up soon")
    val endAtTheBell = L("即将下课", "Almost at the bell")
    val endFinalSeconds = L("马上就下课了", "Just moments left")
    val endWindDown = L("接近下课，可以整理一下笔记", "Nearing the end — good time to tidy your notes")
    val endPackUp = L("快到下课时间了", "Nearly time for the bell")
    val endHangIn = L("坚持一下，很快下课", "Almost through this class")
    val ongoingKeepRhythm = L("上课进行中，保持专注", "Class in progress — stay focused")
    val ongoingSecondHalf = L("已过半程，继续保持", "Past the halfway mark")
    val ongoingKeyPart = L("课程中后段，留意重点内容", "Second half of class — watch for key takeaways")
    val ongoingMidClass = L("课程过半，稳住节奏", "Halfway through class")
    val ongoingOwnPace = L("正在上课，跟着节奏来", "In class — follow along at a steady pace")
    val ongoingNoEarlyDone = L("还在上课中，专注听讲", "Class is still underway")
    val ongoingJustStarted = L("课程刚开始，进入状态", "Class just started")
    val ongoingSaveFocus = L("课程才开始不久，慢慢进入状态", "Just getting started — settle in")
    val ongoingLongStretch = L("这节课时间较长，保持好节奏", "Longer session ahead — pace yourself")

    // ── 连堂间隙 ──
    val gapBackToBack = L(" · 接下来是连堂课", " · Back-to-back class next")
    val gapQuickRefuel = L(" · 下课后紧接下一节，注意休息", " · Another class right after")
    val gapCatchBreath = L(" · 下课后有短暂休息时间", " · Short break after this class")
    val gapLeaveRoom = L(" · 下课后有一段空闲", " · Free time after this class")

    // ── 课间 ──
    val breakGrabSeat = L("马上上课了，准备入座", "Class starts in a moment — time to take your seat")
    val breakBellAboutToRing = L("快打上课铃了，准备进教室", "The bell is about to ring — head inside")
    val breakZeroBuffer = L("即将开始上课", "Class is starting now")
    val breakHeadBack = L("准备前往 %s", "Time to head to %s")
    val breakDontCutClose = L("快上课了，准备去 %s", "Almost time — head over to %s")
    val breakOnYourFeet = L("课间快结束了，起身去 %s", "Break's almost over — heading to %s")
    val breakWatchTheClock = L("课间剩余不多，准备前往 %s", "Break's nearly up — head to %s")
    val breakClassWaiting = L("准备去 %s 上%s", "Head to %s for %s")
    val breakPlanRoute = L("课间时间较短，记得提前去 %s", "Short break — leave early for %s")
    val breakNextRoom = L("课间过半，下节在 %s", "Halfway through break · next in %s")
    val breakChill = L("课间休息，下节%s在 %s", "On break · next: %s in %s")
    val breakNoNewStarts = L("课间放松一下，活动活动", "Take a short breather during break")
    val breakDontWander = L("下节在 %s，注意上课时间", "Next class in %s — keep an eye on the time")
    val breakLongBreak = L("大课间休息，也可以提前看看%s", "Long break — rest up or preview %s")
    val breakRecover = L("课间歇一会儿，补充精力", "Rest up and recharge during the break")
    val breakOwnRhythm = L("离下节课还早，放松一下", "Plenty of break time left — take it easy")
    val breakBigGap = L("空档较长，下节%s在 %s", "Long gap · next: %s in %s")
    val breakTimeYours = L("有一段空闲时间，自由安排", "Free stretch ahead — spend it as you like")

    // ── 今日课已上完 ──
    val doneClaimKnowledge = L("今天 %d 节课已全部上完，辛苦了", "All %d classes done for today — nice work")
    val doneNextLevel = L("今日 %d 节课已结束，好好休息吧", "%d classes finished — time to relax")
    val doneOffClock = L("今天 %d 节课全部结束，接下来的时间自由安排", "All %d classes complete — the rest of the day is yours")
    val doneOnPlan = L("已完成 %d/%d 节课，稳步推进", "%d/%d classes done — making steady progress")
    val doneProgress = L("今日进度 %d/%d 节，继续加油", "%d/%d classes complete — keep it up")
    val doneSteadyFinish = L("今天的课程已告一段落", "Classes are wrapped up for now")

    // ── 全天强度预告 ──
    val planHeavy = L("今天共 %d 节课，安排较满", "%d classes today — a full day")
    val planHeavyRation = L("今天有 %d 节课，注意劳逸结合", "%d classes today — remember to pace yourself")
    val planHeavyBattery = L("今天 %d 节课，记得抽空休息", "%d classes today — take breaks when you can")
    val planNormal = L("今天共 %d 节课，节奏适中", "%d classes today — a balanced day")
    val planNormalPace = L("今天有 %d 节课，按部就班来", "%d classes today — steady schedule")
    val planNormalFollow = L("今天 %d 节课，祝学习顺利", "%d classes today — have a good day")
    val planFewDontFloat = L("今天只有 %d 节课，比较轻松", "Just %d classes today — a light day")
    val planFewSpendWisely = L("今天仅 %d 节课，空闲时间较多", "Only %d classes today — plenty of free time")
    val planLightDay = L("今天 %d 节课，日程轻松", "%d classes today — an easy schedule")

    // ── 第一节课前（按「离上课还有多远」分档，但不报分钟数） ──
    val firstFarHours = L("%s · 离第一节课还早，慢慢准备", "%s · Plenty of time before first class")
    val firstFarOwnRhythm = L("%s · 时间充裕，从容安排", "%s · Lots of time — take it easy")
    val firstFarDontCrash = L("%s · 离上课还早，可以先忙别的", "%s · Early yet — plenty of time to spare")
    val first90Room = L("%s · 时间宽裕，稍后去 %s", "%s · Time to spare before heading to %s")
    val first90Until = L("%s · 离%s还有一段时间", "%s · Still a while until %s")
    val first90NoRush = L("%s · 时间充裕，不必着急", "%s · Plenty of time — no rush")
    val first30GetReady = L("%s · 准备出发去 %s", "%s · Time to get ready for %s")
    val first30PackUp = L("%s · 即将上%s，检查一下物品", "%s · %s coming up — pack what you need")
    val first30WarmUp = L("%s · 准备进入上课状态", "%s · Time to get ready for class")
    val first15HeadOut = L("该出发去 %s 了", "Time to head out to %s")
    val first15RouteSet = L("准备前往 %s", "Heading to %s soon")
    val first15Buffer = L("可以动身去 %s 了，留点富余时间", "Good time to leave for %s")
    val first5NoDawdle = L("快上课了，抓紧去 %s", "Class starts soon — head to %s")
    val first5SteadyWalk = L("即将上课，前往 %s", "Almost time — on your way to %s")
    val first5LeaveNow = L("现在出发去 %s 正合适", "Good time to leave for %s")
    val first1KeepPace = L("马上上课，前往 %s", "Class is about to start at %s")
    val first1NoSprint = L("即将开课，前往 %s 注意安全", "Class starts any minute at %s")
    val first1TakeSeat = L("准备进入 %s", "Head into %s")
    val hintAfternoonMore = L(" · 下午还有 %d 节", " · %d more this afternoon")
    val hintEveningMore = L(" · 晚上还有 %d 节", " · %d more tonight")
    val rushHurryIn = L("即将开始，快进 %s", "Starting now — head into %s")
    val rushBellRinging = L("快打铃了，准备进入 %s", "Bell's ringing — %s is right ahead")
    val rushStraightIn = L("课程即将开始，请进 %s", "Class is starting — enter %s")

    // ── 默认：有课 ──
    val readyFollowSchedule = L("今天共 %d 节课，祝顺利", "%d classes today — have a smooth day")
    val readyDayPlanned = L("今天安排了 %d 节课", "%d classes scheduled today")
    val readyScheduleSet = L("今日 %d 节课", "%d classes today")

    // ── 天气/定位 ──
    val locationFailedTapRetry = L("无法获取位置信息·点击重试", "Can't get your location · Tap to retry")
    val locationPermissionNeeded = L("需要定位权限·点击授权", "Location permission needed · Tap to allow")
    val weatherServiceTapRetry = L("天气服务异常·点击重试", "Weather service error · Tap to retry")
}
