package com.haooz.chedule.i18n

/**
 * 今日页（日期 pager / 课程列表 / 语录卡 / 跳转日期弹窗）文案。
 *
 * 语录按「场景底池 + 时段补充」组织为 [L10n] 列表，调用时按当前语言取值。
 * 课程行状态机内部键（"未开始/进行中/已结束/未知"）保留在 TodayScreen，仅展示处经本命名空间取词。
 */
object TodayStr {

    // ── 课程行状态 ──
    val notStarted = L("未开始", "Not started")
    val inClass = L("正在上课", "In class")
    val ended = L("已结束", "Ended")

    // ── 节次时间缺省时的显示 ──
    val sectionRange = L("第%d-%d节", "Periods %d-%d")

    // ── 跳转日期弹窗 ──
    val jumpToDate = L("跳转日期", "Jump to Date")
    val yearN = L("%d年", "%d")
    val monthN = L("%d月", "%d")

    // ── 课程分组标题 ──
    val morningClasses = L("上午课程", "Morning classes")
    val afternoonClasses = L("下午课程", "Afternoon classes")
    val eveningClasses = L("晚上课程", "Evening classes")

    // ── 空态 ──
    val holidayTodayRest = L("今天是假期，好好休息吧！", "It's a holiday today — enjoy the break!")
    val holidayOtherDay = L("这天是假期", "It's a holiday")
    val noCourseTodayRest = L("今天没有课程，好好休息吧！", "No classes today — enjoy the break!")
    val noCourseOtherDay = L("这天没有课程", "No classes on this day")

    // ── 语录 · 底池：深夜 ──
    val quotesDeepNight: List<L10n> = listOf(
        L("夜深了，早点休息，明天才有好精神", "It's late — get some rest for tomorrow"),
        L("再重要的事，也值得睡饱了再做", "Even important things go better after a good night's sleep"),
        L("放下手机，让眼睛和大脑都歇一会儿", "Put the phone down and let your mind rest"),
        L("熬夜透支的是明天的精力，早些睡吧", "Staying up borrows from tomorrow's energy — sleep well"),
        L("夜晚很安静，适合安心入睡", "The night is quiet — time to get some sleep"),
        L("今晚辛苦了，祝你好梦", "You've done enough for today — sleep well")
    )

    // ── 语录 · 底池：今天没课 ──
    val quotesNoClass: List<L10n> = listOf(
        L("今天没有课，按自己的节奏过就好", "No classes today — take the day at your own pace"),
        L("难得的空闲日，休息和充电都很重要", "A free day is a great time to rest and recharge"),
        L("没有课表催促的一天，去做一件喜欢的事吧", "No schedule rushing you today — do something you enjoy"),
        L("空出来的时间，留给想读的书或想见的风景", "Use the open hours for a good book or a walk outside"),
        L("不用赶课的日子，从容一点最舒服", "Days without classes are best taken unhurried"),
        L("今天空闲，补个好觉或整理一下手头的事都很不错", "Free day — catch up on sleep or tidy up your to-do list")
    )

    // ── 语录 · 底池：未来日期 ──
    val quotesFutureDay: List<L10n> = listOf(
        L("提前看看安排，心里更有底", "Looking ahead makes the week feel lighter"),
        L("做好计划就好，不必提前焦虑", "Plan ahead if you like — no need to worry early"),
        L("把今天过好，后面的事到那天再从容应对", "Focus on today; take the rest as it comes"),
        L("提前熟悉一下教室和时间，那天会更从容", "Checking the rooms ahead of time always helps"),
        L("新的一天到来前，先享受当下", "Enjoy today before the next schedule arrives")
    )

    // ── 语录 · 底池：过去日期 ──
    val quotesPastDay: List<L10n> = listOf(
        L("这一天已经顺利走过", "That day is behind you"),
        L("回头看看走过的路，也是一种收获", "Looking back can be a quiet reminder of how far you've come"),
        L("有没弄懂的地方，随时都可以再补上", "Anything unclear from back then can always be revisited"),
        L("记下经验，轻装往前走", "Take what you learned and keep moving forward"),
        L("过去的留在过去，把精力留给接下来的课", "Leave yesterday behind and save your energy for what's next")
    )

    // ── 语录 · 底池：课表时间未完善 ──
    val quotesScheduleBroken: List<L10n> = listOf(
        L("有课程尚未设置上下课时间，可在作息设置中补全", "Some classes are missing times — fill them in under time settings"),
        L("补全作息时间后，这里会自动显示精确倒计时", "Once class times are set, live countdowns will appear here"),
        L("课程时间还不完整，点进设置补充一下吧", "Class times are incomplete — add them in settings"),
        L("完善节次时间后，提醒和进度都会更准确", "Complete your period times for accurate reminders and progress")
    )

    // ── 语录 · 底池：第一节课前 ──
    val quotesBeforeFirst: List<L10n> = listOf(
        L("新的一天开始了，检查一下随身物品吧", "A new day begins — double-check what you need for class"),
        L("出门前吃口热乎的，上午更有精力", "A quick breakfast goes a long way for morning classes"),
        L("留出一点路上时间，从容走进教室", "Leave a few minutes early for an unhurried walk to class"),
        L("记得带水杯和笔记工具", "Don't forget your water bottle and notebook"),
        L("早起不容易，祝今天一切顺利", "Early mornings take effort — have a great day"),
        L("深吸一口新鲜空气，开启今天的课程", "Take a breath of fresh air and ease into today's classes")
    )

    // ── 语录 · 底池：刚上课 ──
    val quotesInClassJust: List<L10n> = listOf(
        L("课程刚开始，静下心来进入状态", "Class just started — settle in and focus"),
        L("先把手机放一边，专注听这一节", "Set the phone aside and tune in for this session"),
        L("开场往往会交代框架，认真听前面几分钟很有用", "The opening minutes often set the whole outline"),
        L("记下几个关键词，回头复习会轻松很多", "Jotting down a few keywords makes review much easier later"),
        L("跟着老师的思路走，慢慢就进入状态了", "Follow the flow — focus builds after the first few minutes")
    )

    // ── 语录 · 底池：课上中段 ──
    val quotesInClassMid: List<L10n> = listOf(
        L("已经过半了，保持专注", "Halfway through — keep your focus steady"),
        L("有点走神很正常，轻轻把注意力拉回来就好", "Drifting happens — gently bring your focus back"),
        L("核心内容常在课中展开，多记一笔不吃亏", "Key concepts often land mid-class — worth a quick note"),
        L("坐直一点，喝口水提提神", "Sit up straight and take a sip of water to refresh"),
        L("稳住节奏，这节课很快就要过半了", "Steady pace — you're making good progress")
    )

    // ── 语录 · 底池：临近下课 ──
    val quotesInClassEnd: List<L10n> = listOf(
        L("快下课了，再专注最后几分钟", "Almost done — stay with it for the last few minutes"),
        L("结尾常有总结和作业提醒，别错过", "Final minutes often include summaries and assignments"),
        L("顺手整理一下刚才记的要点", "Take a moment to review the notes you just took"),
        L("这节课辛苦了，马上就能休息一下", "Good work this session — break is right around the corner"),
        L("听完最后几句，从容收拾东西", "Catch the closing points, then pack up unhurried")
    )

    // ── 语录 · 底池：短课间 ──
    val quotesShortBreak: List<L10n> = listOf(
        L("站起来活动一下，看看远处放松眼睛", "Stand up, stretch, and rest your eyes"),
        L("去接杯水，给下节课充点电", "Refill your water and reset for the next class"),
        L("短暂的课间，闭眼歇两分钟也很舒服", "Even two minutes with your eyes closed helps"),
        L("整理一下下节课要用的课本和资料", "Get your materials ready for the next period"),
        L("出去透透气，换个心情", "Step out for a bit of fresh air")
    )

    // ── 语录 · 底池：长课间 ──
    val quotesLongBreak: List<L10n> = listOf(
        L("有一段宽裕的空档，好好休息或吃点东西", "A generous break — rest up or grab a bite"),
        L("趁着空档走走，或者小憩片刻恢复精力", "Take a short walk or a quick nap to recharge"),
        L("时间充裕，也可以顺手把刚才的笔记理一理", "Plenty of time to review your notes if you'd like"),
        L("放松一下肩颈，别一直盯着屏幕", "Give your neck and eyes a break from screens"),
        L("调整好状态，从容迎接下一节课", "Reset and ease into the next class")
    )

    // ── 语录 · 底池：今日课上完 ──
    val quotesAfterAll: List<L10n> = listOf(
        L("今天的课全部上完了，辛苦了", "All classes done for today — well done"),
        L("收起课本，享受下课后的轻松时光", "Pack up and enjoy the rest of your day"),
        L("走出教室透透气，给自己一点放松空间", "Step outside and unwind after a full day"),
        L("今天顺利完成课程，晚上好好休息", "Today's classes are complete — rest well tonight"),
        L("有空的话花几分钟回看一下今天的重点", "A quick glance over today's notes goes a long way"),
        L("忙碌告一段落，去吃顿喜欢的晚饭吧", "Classes are over — treat yourself to a good dinner")
    )

    // ── 语录 · 时段补充：上课中 × 时段 ──
    val extrasClassDawn: List<L10n> = listOf(
        L("早课不易，慢慢唤醒专注力", "Early classes take effort — ease into focus"),
        L("清晨头脑清亮，适合记住新概念", "Fresh morning minds pick up new ideas quickly"),
        L("喝口温水，让身体跟上早课节奏", "A sip of warm water helps wake you up"),
        L("早起上课的你已经很棒了", "Showing up for an early class is already a win")
    )
    val extrasClassMorning: List<L10n> = listOf(
        L("上午精力充沛，适合攻克难点", "Morning energy is great for tackling tough topics"),
        L("窗外光线正好，保持好听课节奏", "Bright morning light — keep a steady pace"),
        L("上午的专注往往事半功倍", "Focused morning hours pay off quickly"),
        L("稳稳听完这一节，离午饭又近一步", "Stay with this class — lunch isn't far off")
    )
    val extrasClassNoon: List<L10n> = listOf(
        L("临近饭点，坚持听完这最后一段", "Almost lunchtime — stay focused through the finish"),
        L("上完这节就能安心去吃饭了", "Lunch is waiting right after this class"),
        L("午间课程容易分心，记几笔帮助集中", "Taking brief notes helps stay sharp before lunch"),
        L("撑过这一节，好好犒劳一下自己", "Finish strong and enjoy a good meal")
    )
    val extrasClassAfternoon: List<L10n> = listOf(
        L("午后容易犯困，换个坐姿或喝口水提神", "Afternoon slump is natural — adjust your posture or drink some water"),
        L("下午的阳光很暖，慢慢跟着节奏听", "Warm afternoon light — follow along steadily"),
        L("动手记笔记比干听更容易保持清醒", "Writing notes keeps you more alert than just listening"),
        L("下午的课一节节过去，很快就会迎来傍晚", "One class at a time — evening will be here before you know it")
    )
    val extrasClassEvening: List<L10n> = listOf(
        L("傍晚还在坚持上课，辛苦了", "Evening classes take persistence — keep going"),
        L("窗外渐渐入夜，静下心听完这一节", "As evening settles in, stay calm and focused"),
        L("晚课节奏通常更安静，适合慢慢消化内容", "Evening sessions are quieter — good for absorbing details"),
        L("坚持完今晚的课就能回去了", "Finish tonight's class and head back to rest")
    )
    val extrasClassNight: List<L10n> = listOf(
        L("夜间的最后一程，稳住专注", "The final stretch of the night — stay with it"),
        L("上完这节就可以安心休息了", "Rest is right on the other side of this class"),
        L("晚上听课辛苦了，回去早点放松", "Good effort tonight — unwind once you're back"),
        L("把今晚的重点记牢，回去就不用惦记了", "Capture the key points now so you can switch off later")
    )

    // ── 语录 · 时段补充：课间 × 时段 ──
    val extrasBreakDawn: List<L10n> = listOf(
        L("早课间洗把脸或走两步，精神会好很多", "A quick walk or splash of water wakes you right up"),
        L("清晨的风很凉爽，出去透透气", "Cool morning air is the best refresher"),
        L("补充点水分，准备下一节早课", "Grab some water before the next morning period"),
        L("趁课间伸个懒腰，活动一下肩膀", "Stretch your shoulders during the break")
    )
    val extrasBreakMorning: List<L10n> = listOf(
        L("上午课间走动一下，保持清醒", "A brief walk keeps your morning energy up"),
        L("接杯水，顺便让眼睛看看窗外", "Refill your bottle and look out the window for a bit"),
        L("短暂休息几分钟，下节课状态更好", "A few quiet minutes now help the next class go smoothly"),
        L("确认一下下节课的教室位置", "Double-check where your next class is")
    )
    val extrasBreakNoon: List<L10n> = listOf(
        L("午间空档，记得按时吃午饭", "Take time for a proper lunch"),
        L("吃完饭散散步，或者趴一会儿恢复精力", "A short walk or quick rest after lunch works wonders"),
        L("中午好好补给，下午上课才更有劲", "Refuel well at noon for the afternoon ahead"),
        L("午休片刻，给大脑充个电", "A brief midday rest resets your focus")
    )
    val extrasBreakAfternoon: List<L10n> = listOf(
        L("下午课间站起来活动活动，赶走困意", "Stand up and move around to shake off afternoon drowsiness"),
        L("看看远处的绿植或天空，让眼睛歇一会儿", "Look out at the sky or trees to rest your eyes"),
        L("喝点水，深呼吸几下提提神", "A glass of water and a few deep breaths help refresh"),
        L("稍微走动一下，准备迎接下一节", "Move around a little before the next session")
    )
    val extrasBreakEvening: List<L10n> = listOf(
        L("傍晚的风很舒服，出去走两步吧", "Step outside for the cool evening breeze"),
        L("晚课间喝口热水，舒缓一天的疲惫", "A cup of warm water helps ease the day's fatigue"),
        L("活动一下颈肩，放松心情", "Roll your shoulders and take a breather"),
        L("看一眼窗外的晚霞或灯光，歇一会儿", "Take a moment to enjoy the evening view")
    )
    val extrasBreakNight: List<L10n> = listOf(
        L("夜课间放松一下眼睛，别一直看屏幕", "Give your eyes a break from screens tonight"),
        L("走廊走两步，保持清醒听完最后一段", "A short walk in the hall helps for the last stretch"),
        L("喝口温水，很快就能结束今天的课程", "Take a sip of water — you're almost done for the day"),
        L("静静歇几分钟，准备收尾", "Rest quietly for a few minutes before wrapping up")
    )

    // ── 语录 · 时段补充：没课/课前/放学 × 时段 ──
    val extrasFreeDawn: List<L10n> = listOf(
        L("清晨安静无人打扰，适合从容开启一天", "Quiet early hours are a peaceful way to start the day"),
        L("早起的时光很宝贵，吃顿好早饭吧", "Make the most of an early morning with a good breakfast"),
        L("天色渐亮，祝今天有个好心情", "Daylight is breaking — wishing you a good day"),
        L("清晨空气清新，适合慢慢舒展身心", "Fresh morning air — ease into the day gently")
    )
    val extrasFreeMorning: List<L10n> = listOf(
        L("上午没课，去图书馆或找个安静角落都不错", "A free morning is ideal for quiet study or reading"),
        L("不用赶课的上午，按自己的节奏安排就好", "No morning rush — set your own pace today"),
        L("把想做的一两件小事定下来，上午会很充实", "Pick one or two small goals for a fulfilling morning"),
        L("享受轻松的上午时光", "Enjoy a relaxed morning")
    )
    val extrasFreeNoon: List<L10n> = listOf(
        L("没课的中午，可以慢慢挑顿喜欢的午饭", "No rush at noon — enjoy a leisurely lunch"),
        L("午后小睡二十分钟，整个下午都很清爽", "A twenty-minute nap refreshes the whole afternoon"),
        L("午间安静，适合放空一会儿", "Quiet midday hours — take a moment to unwind"),
        L("吃饱歇好，下午更自在", "A good lunch and a short rest set up a great afternoon")
    )
    val extrasFreeAfternoon: List<L10n> = listOf(
        L("下午空闲，读书、运动或整理笔记都很合适", "Open afternoon — great for reading, exercise, or reviewing notes"),
        L("挑一件最想完成的事先做掉，剩下的时间更轻松", "Finish one priority early and enjoy the rest of the afternoon"),
        L("阳光好的话，出去走走晒晒太阳", "If it's sunny, a short walk outside feels great"),
        L("没有课程束缚的下午，自在安排", "A wide-open afternoon — spend it well")
    )
    val extrasFreeEvening: List<L10n> = listOf(
        L("晚上没有课程，好好放松充充电", "No evening classes — unwind and recharge"),
        L("吃顿舒心的晚饭，散散步消消食", "Enjoy a good dinner and an evening stroll"),
        L("空闲的夜晚，留给兴趣、朋友或安静独处", "A free evening for hobbies, friends, or quiet time"),
        L("提前看看明天的安排，晚上更安心", "A quick glance at tomorrow's schedule brings peace of mind")
    )
    val extrasFreeNight: List<L10n> = listOf(
        L("夜深人静，早点放下手头的事去休息", "It's getting late — wrap up and get some rest"),
        L("今晚没有课程压力，睡个安稳觉", "No schedule pressure tonight — sleep peacefully"),
        L("给自己留足睡眠时间，明天状态更好", "A full night's sleep sets up a better tomorrow"),
        L("晚安，祝好梦", "Good night, and sleep well")
    )
}
