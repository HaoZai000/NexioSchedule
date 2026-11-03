package com.haooz.chedule.i18n

/**
 * 今日页（日期 pager / 课程列表 / 格言卡 / 跳转日期弹窗）文案。
 *
 * 格言按「场景底池 + 时段加菜」组织成 [L10n] 列表，运行时按场景 × 时段取一条；
 * 列表存 [L10n] 本体、调用时才取当前语言，语言切换即时生效。
 *
 * 注意：课程行状态机的内部状态键（"未开始/进行中/已结束/未知"）保留在 TodayScreen，
 * 状态值参与多处比较与写入，不本地化；仅展示处经本命名空间取词。
 */
object TodayStr {

    // ── 课程行状态 ──
    val notStarted = L("未开始", "Not started")
    val inClass = L("正在上课", "In class")
    val ended = L("已结束", "Ended")

    // ── 节次时间缺省时的兜底显示 ──
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

    // ── 格言 · 底池：深夜 ──
    val quotesDeepNight: List<L10n> = listOf(
        L("凌晨了，你的黑眼圈在申请吉尼斯", "It's the small hours and your dark circles are applying for a Guinness record"),
        L("别修仙了，早八不会为你的仙气让路", "Quit cultivating immortality — your 8 a.m. class won't make way for your aura"),
        L("这个点的努力，感动天感动地，感动不了绩点", "Effort at this hour moves heaven and earth, but not your GPA"),
        L("手机还剩 20% 电，你的意志力还剩多少", "Your phone is at 20% battery — how much willpower is left?"),
        L("关机，睡觉，别逼我跪下来求你", "Power off and sleep — don't make me get on my knees and beg"),
        L("你熬的不是夜，是明天迟到的底气", "You're not burning the night — you're banking confidence for being late tomorrow"),
        L("深夜的你最清醒——清醒地摆烂", "You're at your sharpest this late — sharp enough to slack off"),
        L("头发在掉，作业在飘，就你在熬", "Hair falling out, homework drifting away, and you're the one still up"),
        L("别拿灵感当借口，你刷的是短视频不是文献", "Don't call it inspiration — you're scrolling short videos, not papers"),
        L("再不睡，明天第一节课的主角就是你的呼噜", "Sleep now, or your snoring stars in tomorrow's first class"),
        L("凌晨的自我感动，白天的现实打脸，稳了", "Midnight self-flattery, daytime reality check — a solid combo"),
        L("睡吧，让明天的你去面对今天的债", "Go to sleep — let tomorrow's you deal with today's debt"),
        L("台灯比你努力，它至少在工作", "Even your desk lamp works harder — at least it's doing its job"),
        L("这个点的「再学五分钟」，一般等于再玩两小时", "Five more minutes of studying at this hour usually means two more hours of playing"),
        L("别数羊了，数数你今天翘过的知识点", "Stop counting sheep — count the lessons you skipped today"),
        L("月亮都下班了，你还在硬撑，图啥", "Even the moon has clocked out — what exactly are you holding on for?")
    )

    // ── 格言 · 底池：今天没课 ──
    val quotesNoClass: List<L10n> = listOf(
        L("今天没课，你的生产力大概率也没上班", "No classes today — and your productivity is probably off duty too"),
        L("自由日？通常是废掉日的委婉说法", "A free day? Usually a polite word for a wasted one"),
        L("别躺出褥子，明天课表会找你算账", "Don't sink too deep into the mattress — tomorrow's schedule will come collect"),
        L("没课的你：计划满满，进度零零", "Class-free you: big plans, zero progress"),
        L("今天没有老师催你，你也别把自己催崩了", "No teachers pushing you today — don't push yourself to the breaking point either"),
        L("空出来的时间，最后都会变成刷手机的时长", "All that freed-up time will end up as screen time"),
        L("难得没课，做点人事——比如真的睡一觉", "A rare day off — do something human, like actually sleeping"),
        L("别羡慕别人有课充实，你空虚得很彻底", "Don't envy anyone's packed schedule — your emptiness is nothing if not thorough"),
        L("没课不是奖励，是你自己把今天过成了过场", "No class isn't a reward — you're the one turning today into a blur"),
        L("图书馆开门了，你大概率在门外路过", "The library is open, and you're most likely walking right past it"),
        L("闹钟不用响，你也照样起不来，很稳定", "The alarm needn't even ring — you won't get up either way. Very consistent"),
        L("别人满课像渡劫，你没课像断线", "Their packed schedule is a trial by thunder; your free day is a dropped connection"),
        L("自由是有了，自律呢？哦，没装这个模块", "Freedom: installed. Self-discipline? Oh — that module was never installed"),
        L("今天省下的通勤，会在晚上变成刷夜", "The commute you saved today will turn into a night of scrolling")
    )

    // ── 格言 · 底池：翻到往后的日子（未来） ──
    // 未来向：别提前焦虑、计划可以做、慌没必要。
    val quotesFutureDay: List<L10n> = listOf(
        L("这天还没到，你提前慌什么", "That day isn't here yet — what exactly are you panicking about?"),
        L("提前预习焦虑，是你唯一的超前学习", "Pre-studying anxiety is the only advanced learning you do"),
        L("未来的苦，现在的你替它尝了一口", "You just took a sip of future-you's suffering"),
        L("计划现在做可以，慌没必要现在慌", "Plan now if you like. No need to panic now"),
        L("那天起不起得来，是那天的事", "Whether you get up that day is that day's problem"),
        L("现在研究教室位置，那天你照样迷路", "Map out the classroom all you want — you'll still get lost"),
        L("多看两眼，课也不会少一节", "Stare all you want — the class count won't drop by one"),
        L("先把今天过完，比研究那天有用", "Finishing today beats studying that day"),
        L("课表翻得越勤，心里越慌，很科学", "The more you flip ahead, the more you panic. Very scientific")
    )

    // ── 格言 · 底池：翻到从前的日子（过去） ──
    // 过去向：改不了、别沉浸、复盘≠内耗。
    val quotesPastDay: List<L10n> = listOf(
        L("这天已经过去了，改不了，也补不了", "That day is gone. Can't edit it, can't redo it"),
        L("回头看可以，别站那儿不走", "Looking back is fine. Standing there isn't"),
        L("旧课表改不了，你的遗憾也一样", "The old schedule won't change — neither will your regret"),
        L("翻旧账翻不出新东西", "Digging up old days won't turn up anything new"),
        L("那节课没听懂，现在补也不晚，别装忘", "So you didn't get it. Not too late to learn it — just don't play dumb"),
        L("回忆是用来参考的，不是用来住的", "The past is a reference, not an address"),
        L("昨天不会因为你看得久就变好一点", "Yesterday won't improve from being stared at"),
        L("复盘叫经验，反复看叫内耗", "Reviewing is learning. Replaying is just wear"),
        L("前面还有课，别在这儿停太久", "There are classes ahead. Don't linger here too long")
    )

    // ── 格言 · 底池：课表残缺 ──
    val quotesScheduleBroken: List<L10n> = listOf(
        L("课有，时间没有——你这是薛定谔的课表", "Classes exist, times don't — a Schrödinger schedule"),
        L("节次或时间没填全，格言都不知道怎么嘲你", "Periods or times are missing — even this quote doesn't know how to roast you"),
        L("去课表补一下，不然倒计时和笑话都对不上", "Fill in the schedule, or neither the countdown nor the jokes will line up"),
        L("时间残缺得跟期末范围似的，先补数据", "Your times are as incomplete as a final-exam scope — fix the data first"),
        L("课表在，起点终点不在，当代悬疑片", "The schedule is there, start and end times aren't — a modern mystery"),
        L("别指望我编故事，先把上课时间写上", "Don't expect me to improvise — enter the class times first")
    )

    // ── 格言 · 底池：第一节课前 ──
    val quotesBeforeFirst: List<L10n> = listOf(
        L("离上课还有点时间，够你再赖一次", "Still some time before class — enough for one more snooze"),
        L("别吃了，再吃就得用跑的了", "Stop eating, or the only way you'll make it is running"),
        L("今天的第一节课，是你和被窝的告别演出", "Today's first class is the farewell show between you and your blanket"),
        L("出门前再看一眼床，它舍不得你，但课表舍得", "Take one last look at the bed — it can't let you go, but the schedule can"),
        L("还有时间？不，是还有借口", "Time left? No — excuses left"),
        L("你的早晨流程：醒→赖→慌→跑，稳定发挥", "Your morning routine: wake → snooze → panic → sprint — consistently delivered"),
        L("别收拾了，教室的灯不会给你打光", "Stop tidying yourself up — classroom lighting flatters no one"),
        L("早饭随便塞两口，你的胃会记住这笔账", "Shovel down a quick breakfast — your stomach will remember this debt"),
        L("这个点还从容的，等会儿会在走廊飙车", "Anyone this calm right now will be flying down the hallway soon"),
        L("闹钟响了三遍，你赢了两次，课赢了最后一次", "The alarm rang three times; you won twice, and the class won the last round"),
        L("现在有多悠闲，待会儿就有多冲刺", "The more relaxed you are now, the harder you'll sprint later"),
        L("第一节还没开始，迟到的剧本已经写好了", "First period hasn't started, and the late-to-class script is already written"),
        L("外套记得带，教室空调不跟你讲武德", "Bring a jacket — the classroom AC shows no mercy"),
        L("水杯记得带，忘带的上午你会懂", "Take your water bottle — you'll understand on the morning you forget it")
    )

    // ── 格言 · 底池：刚上课 ──
    val quotesInClassJust: List<L10n> = listOf(
        L("刚点完名，你的魂就开始请假了", "Roll call just ended and your mind is already calling in sick"),
        L("这节课才开始，你已经在心里下课三次了", "Class just started and you've mentally dismissed it three times"),
        L("别急着关书，还有很久够你表演专注", "No rush to close the book — there's plenty of time left to perform focus"),
        L("老师还没讲到重点，你已经演完走神全套了", "The teacher hasn't reached the key point and you've already performed the full spacing-out routine"),
        L("坐姿挺标准，就是眼神在窗外办签证", "Posture is perfect — your eyes are just applying for a visa out the window"),
        L("别装了，你打开书只是为了挡脸", "Drop the act — you opened the book to hide your face"),
        L("连着上？恭喜，你可以完整睡一觉了", "Double period? Congratulations — time for one uninterrupted nap"),
        L("刚上课就盼下课，你的盼头比成绩稳定", "Longing for the bell the minute class starts — your hopes are steadier than your grades"),
        L("这节要是选修，你连装都懒得装", "If this were an elective, you wouldn't even bother pretending"),
        L("上课五分钟，摸鱼两小时——预算已分配好", "Five minutes of class, two hours of slacking — the budget is already allocated"),
        L("第一节就耗尽电量，后面靠什么撑？意志力？笑死", "Battery dead in the first period — what carries the rest? Willpower? Hilarious"),
        L("老师讲的是大纲，你听的是氛围", "The teacher is teaching the syllabus; you're absorbing the vibe"),
        L("别急着拍 PPT，拍了你也不会再看", "Don't rush to photograph the slides — you're never looking at them again"),
        L("现在坐得多端正，中段就瘫得多彻底", "The straighter you sit now, the harder you'll melt by mid-class")
    )

    // ── 格言 · 底池：课上中段 ──
    val quotesInClassMid: List<L10n> = listOf(
        L("过半了，你的耐心没有", "Halfway there — your patience isn't"),
        L("老师的进度条在走，你的倒计时在蹦迪", "The teacher's progress bar is crawling; your countdown is clubbing"),
        L("别掐大腿了，掐完还是困", "Stop pinching your leg — you'll still be sleepy after"),
        L("中段的你：眼睛睁着，脑子已签退", "Mid-class you: eyes open, brain already clocked out"),
        L("笔记记到一半开始画火柴人，艺术家实锤", "Halfway through your notes you started drawing stick figures — certified artist"),
        L("撑住，后面的内容你反正也听不懂（不是）", "Hang in there — you wouldn't follow the rest anyway (jk)"),
        L("你的专注力像流量包，早超额了", "Your focus is like a data plan — over the cap long ago"),
        L("别问同学借笔记，你借的是别人的努力", "Don't borrow a classmate's notes — that's their effort you're borrowing"),
        L("这会儿还能听进去的，建议保送", "Anyone still following at this point deserves automatic admission"),
        L("半节课过去，你的人设从「认真」改成「还活着」", "Half a class in, your persona has shifted from 'diligent' to 'still alive'"),
        L("知识点从左耳进右耳出，路过都不打卡", "Knowledge goes in one ear and out the other — it doesn't even stop to check in"),
        L("别刷学习软件了，你在教室，不是在图书馆装", "Quit opening study apps — you're in a classroom, not performing at the library"),
        L("现在犯的困，都是昨晚欠的债", "The sleepiness you feel now is debt from last night"),
        L("老师提问的概率在上升，你的低头角度也是", "The odds of the teacher cold-calling are rising — so is your head-tilt angle")
    )

    // ── 格言 · 底池：临近下课 ──
    val quotesInClassEnd: List<L10n> = listOf(
        L("还有几分钟，你的脚已经完成热身", "A few minutes left and your feet have finished warming up"),
        L("别装了，书包拉链你都摸三遍了", "Drop the act — you've touched that backpack zipper three times"),
        L("最后几分钟的演技，够评奥斯卡", "Your final-minutes acting deserves an Oscar"),
        L("下课铃还没响，你的魂已经出校门打卡了", "The bell hasn't rung, but your soul already clocked out past the school gate"),
        L("老师拖堂一分钟，你少活一年", "Every minute the teacher runs over costs you a year of life"),
        L("忍住，现在冲出去会被记仇的", "Hold it — bolting out now will be remembered"),
        L("你的坐姿从听课切换成起跑预备了", "Your posture has switched from listening to sprint-start position"),
        L("最后几分钟，你听进去的只有自己的心跳", "In these last minutes, the only thing registering is your own heartbeat"),
        L("别提前收东西，老师就爱抓这个", "Don't pack up early — that's exactly what teachers love to catch"),
        L("再撑一下，食堂不会跑，但队伍会", "Hold on a little longer — the canteen isn't going anywhere, but the queue is growing"),
        L("这个点的「认真」，是装给老师看的余光", "Your 'focus' at this point is side-eye staged for the teacher"),
        L("手机在包里震，你的心比它还抖", "The phone is buzzing in your bag, and your heart is shaking harder"),
        L("最后几分钟的知识点，往往是考点——你正好在收拾书包", "The last few minutes often hold the exam points — and you're busy packing your bag"),
        L("拖堂是老师的特权，崩溃是你的义务", "Running over is the teacher's privilege; falling apart is your duty")
    )

    // ── 格言 · 底池：短课间 ──
    val quotesShortBreak: List<L10n> = listOf(
        L("就这么几分钟，你还掏出手机？格局小了", "A few short minutes and you're reaching for your phone? Think bigger"),
        L("接水排队都比你准备下节课久", "The queue at the water dispenser outlasts your prep for next class"),
        L("别去走廊演风云人物了，下节课更困", "Skip the hallway fame tour — next class will be even sleepier"),
        L("课间是用来补觉的，不是用来假装社交的", "Breaks are for napping, not performative socializing"),
        L("上厕所+接水+刷手机：完美卡点迟到套餐", "Bathroom + water + scrolling: the perfect cut-it-close lateness combo"),
        L("这几分钟够你想清楚要不要翘下一节（别）", "These minutes are enough to decide whether to skip next class (don't)"),
        L("歇够没？没歇够也得滚回去了", "Rested enough? Doesn't matter — time to drag yourself back anyway"),
        L("课间刷到的瓜，下节课讲不完更难受", "The gossip you scrolled during break will be torture not to finish next class"),
        L("别瘫了，下节课的老师已经在门口蹲你了", "Get up — next period's teacher is already camping outside the door"),
        L("三分钟热度都不够你热完这个课间", "Your three-minute enthusiasm can't even last this short break"),
        L("同桌都睡着了，就你还清醒地浪费课间", "Even your deskmate is asleep — only you are lucidly wasting the break"),
        L("打水的队伍，是你今天见过最长的卷", "The water-refill queue is the most competitive line you'll join today"),
        L("走廊逛一圈，风没醒，瓜吃了一堆", "One lap of the hallway: no fresh air gained, plenty of gossip collected"),
        L("现在多玩一分钟，下节课多困十分钟，汇率很差", "One extra minute of play now costs ten minutes of drowsiness next class — a terrible exchange rate")
    )

    // ── 格言 · 底池：长课间 ──
    val quotesLongBreak: List<L10n> = listOf(
        L("这么久的空档，你大概率会用来后悔", "A gap this long will most likely be spent on regret"),
        L("计划：预习。实际：刷到手机发烫", "Plan: preview the material. Reality: scroll until the phone overheats"),
        L("别瘫了，待会还要演一小时清醒", "Get up — you still have an hour of acting awake ahead"),
        L("午休充电五分钟，续命一下午——并没充上", "Five-minute noon charge to power the afternoon — spoiler: it never charged"),
        L("这段时间够你写作业，也够你把作业忘光", "This stretch is enough for homework — and enough to forget all of it"),
        L("饭可以吃，觉可以睡，就是别打开那局游戏", "Eat, sleep, fine — just don't open that game"),
        L("你管这叫休息？手机电量都不信", "You call this resting? Even your battery percentage doubts it"),
        L("长课间的尽头，是你踩点冲进教室的背影", "At the end of a long break: you, charging into the classroom right at the bell"),
        L("别把空档过成加班，最后累的还是自己", "Don't turn a gap into overtime — you're the one who ends up exhausted"),
        L("现在有多闲，下节课开场就有多狼狈", "The lazier you are now, the rougher next class will start"),
        L("睡有睡的代价，玩有玩的报应，你自己选", "Naps have a price, games have consequences — your pick"),
        L("空档是用来回血的，不是用来掉段的", "Gaps are for recovering HP, not for losing rank"),
        L("说好只躺十分钟，醒来天都变了", "You said just ten minutes of lying down — you woke up to different skies"),
        L("别开新剧了，开了一集就是一下午", "Don't start a new show — one episode becomes a whole afternoon")
    )

    // ── 格言 · 底池：今日课上完 ──
    val quotesAfterAll: List<L10n> = listOf(
        L("课是上完了，作业才刚刚开始看你笑话", "Classes are over, but the homework is just starting to laugh at you"),
        L("别急着躺，明天的早八已经在门口蹲着了", "Don't collapse yet — tomorrow's 8 a.m. is already lurking at the door"),
        L("今天的你：完整出席，完整走神", "Today's you: fully present, fully zoned out"),
        L("恭喜通关今日副本，奖励是——明天还有", "Congrats on clearing today's dungeon — the reward is another one tomorrow"),
        L("书包可以放下了，良心放不下", "The backpack can go down; your conscience can't"),
        L("放学不是自由，是换个地方继续焦虑", "Dismissal isn't freedom — it's anxiety with a change of venue"),
        L("今天的知识点，一个都没办理入住", "Not a single knowledge point from today checked in"),
        L("别装充实了，你今天干了啥自己清楚", "Drop the productive act — you know exactly what you did today"),
        L("老师下班了，你的愧疚开始上班", "The teacher clocked out; your guilt just clocked in"),
        L("自由了？作业群消息响了没", "Free at last? Has the homework group chat pinged yet?"),
        L("走出校门的风，都比教室里的清醒", "Even the breeze outside the gate is sharper than you were in class"),
        L("今天的重点：你一个都没抓到", "Today's key points: you caught none of them"),
        L("人是放了，魂还在最后一排没领走", "You've been released, but your soul is still unclaimed in the back row"),
        L("复盘一下今天：出席率 100%，吸收率未知", "Today in review: 100% attendance, absorption rate unknown")
    )

    // ── 格言 · 加菜：上课中 × 时段 ──
    val extrasClassDawn: List<L10n> = listOf(
        L("早八的教室，灵魂还在宿舍办入住", "In the 8 a.m. classroom, your soul is still checking in at the dorm"),
        L("这个点的笔记，写的是睡姿速写", "The notes at this hour are quick sketches of your sleeping posture"),
        L("别问老师为什么看你，你的眼睛还没开机", "Don't ask why the teacher looked at you — your eyes haven't booted up"),
        L("天刚亮就上课，你的床想报警", "Class at first light — your bed wants to report a crime"),
        L("第一排都坐不醒的早八，建议改叫「晨间刑」", "An 8 a.m. that not even the front row wakes up for — rename it 'morning detention'"),
        L("你人到了，生物钟还在昨天", "You arrived, but your body clock is still on yesterday"),
        L("老师声音越稳，你眼皮越重", "The steadier the teacher's voice, the heavier your eyelids"),
        L("早八的正确打开方式：睁一只眼闭一只眼", "The correct way to do an 8 a.m.: one eye open, one eye shut")
    )
    val extrasClassMorning: List<L10n> = listOf(
        L("上午的清醒是限量款，你已经用完了", "Morning alertness is a limited edition — you've used yours up"),
        L("阳光正好，适合发呆，不适合听课", "The sunlight is perfect for daydreaming, terrible for lectures"),
        L("别看窗外了，窗外没有绩点", "Stop looking out the window — there's no GPA out there"),
        L("上午的效率曲线：开局即巅峰，然后跳水", "Your morning productivity curve: peak right at the start, then a cliff dive"),
        L("别人在记重点，你在记「老师今天穿什么」", "They're noting key points; you're noting what the teacher is wearing"),
        L("连堂的上午，是意志力的连续剧", "A morning of back-to-back classes is a willpower soap opera"),
        L("别点头了，老师以为你听懂了会更来劲", "Stop nodding — the teacher gets more energetic thinking you understand"),
        L("上午的咖啡，撑的是仪式感，不是脑子", "Morning coffee fuels the ritual, not the brain")
    )
    val extrasClassNoon: List<L10n> = listOf(
        L("中午的课，是胃在讲课", "A noon class is really your stomach lecturing"),
        L("别想食堂了，想了也得坐着", "Stop thinking about the canteen — you're seated either way"),
        L("这节上完就是饭点，你的魂已经去排队了", "This class ends right at mealtime — your soul is already in the queue"),
        L("饭点上课，是对干饭魂的公开处刑", "Class at mealtime is public execution for a hungry soul"),
        L("老师讲公式，你脑子里全是菜名", "The teacher is explaining formulas; your head is full of menu items"),
        L("中午的知识进不去，下午的困拦不住", "At noon knowledge can't get in, and afternoon sleepiness can't be stopped"),
        L("别人等下课铃，你等开饭铃", "They're waiting for the class bell; you're waiting for the dinner bell"),
        L("这个点还能听进去的，建议直接保送食堂", "Anyone still following at this point deserves a fast track straight to the canteen")
    )
    val extrasClassAfternoon: List<L10n> = listOf(
        L("下午的困，是生理规律，不是态度问题", "Afternoon drowsiness is biology, not attitude"),
        L("太阳晒着，老师讲着，你飘着", "The sun is shining, the teacher is talking, and you're floating"),
        L("别硬撑了，你的点头频率已经暴露了", "Stop forcing it — your nodding frequency has given you away"),
        L("空调在吹，你在睡，分工明确", "The AC blows, you doze — a clean division of labor"),
        L("下午第一节课，全班集体进入省电模式", "First period of the afternoon: the whole class switches to power-saving mode"),
        L("眼皮在跳科目一，你在跳下课", "Your eyelids are drilling test questions while your mind is already at the bell"),
        L("别怪自己，是午饭在体内开了慢充", "Don't blame yourself — lunch has switched your body to slow charging"),
        L("老师擦黑板的声音，是你的白噪音", "The sound of the teacher wiping the board is your white noise")
    )
    val extrasClassEvening: List<L10n> = listOf(
        L("晚课的教室，一半人在线下挂机", "In the evening classroom, half the room is AFK in person"),
        L("别睡了，晚课睡了晚上就真睡不着了", "Don't doze — napping through the evening class ruins the actual night"),
        L("这个点还在听的，不是热爱，是怕点名", "Anyone still listening now isn't passionate — they fear roll call"),
        L("晚课的意义：证明你今天还没完全摆", "The evening class exists to prove you haven't fully given up today"),
        L("窗外在天黑，你在神游，进度一致", "It's getting dark outside and you're wandering off — perfectly in sync"),
        L("晚课的手机亮度，暴露了你的听课深度", "Your screen brightness in the evening class betrays how much of it you're following"),
        L("别看表了，看一次慢一分钟", "Stop checking the clock — it slows down a minute per glance"),
        L("别人晚自习开卷，你晚自习开机", "Evening study: they open books, you press the power button")
    )
    val extrasClassNight: List<L10n> = listOf(
        L("晚课收尾，宿舍的床已经在想你", "As the last class wraps up, your dorm bed is already missing you"),
        L("别急，最后一节的结束铃比下课铃更神圣", "Patience — the final period's bell rings more sacred than any class bell"),
        L("撑完这节，今晚就可以名正言顺摆烂了", "Survive this period and tonight's slacking is fully justified"),
        L("夜色正好，可惜你在教室，不在操场", "The night is lovely — shame you're in a classroom, not out on the field"),
        L("这个点的知识，多半会和困意一起过期", "Knowledge absorbed at this hour mostly expires with the sleepiness"),
        L("最后一节的专注，全用来倒计时了", "Whatever focus the last period gets is spent on counting down"),
        L("老师还在讲，你的魂已办理入住宿舍", "The teacher is still talking, but your soul has already checked into the dorm"),
        L("别提前收包，最后五分钟才是修罗场", "Don't pack up early — the last five minutes are the real battlefield")
    )

    // ── 格言 · 加菜：课间 × 时段 ──
    val extrasBreakDawn: List<L10n> = listOf(
        L("早课间别趴了，趴了第二节更废", "Don't doze through the early break — period two will be worse"),
        L("这个点的课间，用来清醒，不是用来做梦", "This early break is for waking up, not for dreaming"),
        L("走廊风大，吹不走你的困", "The hallway wind is strong, but it won't blow your sleepiness away"),
        L("去洗把脸，比咖啡便宜，比硬撑体面", "Go splash some water on your face — cheaper than coffee, classier than white-knuckling it"),
        L("早课间补觉，是给下午挖坑", "Napping in the early break digs a hole for the afternoon"),
        L("别人晒太阳，你晒枕头印", "They're getting some sun; you're showing off pillow marks"),
        L("别刷手机了，屏幕比天光还晃眼", "Put the phone down — the screen out-glares the dawn"),
        L("站着也困的课间，建议原地罚站（不是）", "A break this sleepy even standing up — might as well call it standing detention (jk)")
    )
    val extrasBreakMorning: List<L10n> = listOf(
        L("上午课间，刷两分钟就变十分钟", "In a morning break, two minutes of scrolling becomes ten"),
        L("别去小卖部了，去了钱包和腰围一起受伤", "Skip the campus store — your wallet and waistline get hurt together"),
        L("接水的时候想想下节课，算了别想了", "Think about next class while refilling water — actually, never mind"),
        L("课间最忙的器官：拇指", "The busiest organ during break: your thumbs"),
        L("走廊走一圈，消息刷一堆，重点一个没有", "One lap of the hallway, a pile of messages, not one key point"),
        L("同桌补觉，你补瓜，分工合理", "Your deskmate naps, you catch up on gossip — sensible division of labor"),
        L("上午课间的规划很大，执行很手机", "Big plans for the morning break; the execution was all phone"),
        L("别笑太大声，下节课老师记得你", "Don't laugh too loud — next period's teacher will remember you")
    )
    val extrasBreakNoon: List<L10n> = listOf(
        L("饭点课间，食堂才是主战场", "On a mealtime break, the canteen is the main battlefield"),
        L("别纠结吃什么了，纠结完队伍就长了", "Stop agonizing over the menu — the queue grows while you do"),
        L("中午能趴就趴，下午会感谢现在的你", "Nap at noon if you can — your afternoon self will thank you"),
        L("冲食堂的速度，暴露了你对这门课的态度", "Your sprint to the canteen reveals how you feel about this class"),
        L("课间十分钟，排队二十分钟，很公平", "Ten minutes of break, twenty minutes of queue — very fair"),
        L("别人午休，你午刷，下午一起倒", "They rest at noon, you scroll — the afternoon flattens you both"),
        L("吃完别立刻躺，除非你想给下午签到失败", "Don't lie down right after eating — unless you want to fail the afternoon check-in"),
        L("中午的自由很短，队伍很长", "Noon freedom is short; the queue is long")
    )
    val extrasBreakAfternoon: List<L10n> = listOf(
        L("下午课间，走廊的风都比你清醒", "In the afternoon break, even the hallway breeze is sharper than you"),
        L("用这几分钟站起来，不然第三节直接焊在椅子上", "Spend these minutes standing, or you'll be welded to the chair by period three"),
        L("别刷手机了，你的眼睛比脑子先罢工", "Put the phone down — your eyes will strike before your brain does"),
        L("晒两分钟太阳，比续杯咖啡管用（大概）", "Two minutes of sun beats another coffee (probably)"),
        L("下午课间的打哈欠会传染，你就是传染源", "Afternoon-break yawns are contagious — and you're patient zero"),
        L("别买冰饮了，胃和钱包一起凉", "Skip the iced drink — stomach and wallet cool down together"),
        L("站起来伸个懒腰，假装自己还活着", "Stand up and stretch — pretend you're still alive"),
        L("走廊的吵闹，是下午唯一的提神饮料", "The hallway commotion is the afternoon's only energy drink")
    )
    val extrasBreakEvening: List<L10n> = listOf(
        L("晚课间，出去走两步，教室快把你腌入味了", "Evening break: step outside — the classroom is pickling you alive"),
        L("别瘫，晚课间瘫完，最后一节直接交卷式听课", "Don't slump — slump through this break and the final period turns into a blank exam"),
        L("这个点的清醒，全靠走廊冷风硬灌", "Your alertness at this hour is entirely cold-wind induced"),
        L("楼道灯比教室亮，也比你的前途亮一点", "The hallway light is brighter than the classroom's — and than your future, just barely"),
        L("晚课间的风是免费的，困是自费的", "The evening-break breeze is free; the drowsiness is on your own tab"),
        L("别开黑了，开了最后一节就没了", "No gaming session now — one match and the last period is gone"),
        L("去接杯热水，给晚上留点人样", "Go refill some hot water — stay human for the rest of the evening"),
        L("窗外路灯亮了，你的斗志该开灯了", "The streetlights are on outside — time to switch your fighting spirit on too")
    )
    val extrasBreakNight: List<L10n> = listOf(
        L("晚课间别开新局，开了就回不来了", "Don't start anything new in the late break — there's no coming back"),
        L("最后一点课间电量，留给下节课，不是短视频", "Save the last of your break battery for the next class, not short videos"),
        L("走廊灯下站会儿，回教室还能再演十分钟", "Stand a while under the hallway light — you can keep performing in class for ten more minutes"),
        L("夜课间的安静，适合反省——反省完接着玩", "The late-break quiet is perfect for self-reflection — then back to playing"),
        L("别去便利店了，去了钱包比人先空", "Skip the convenience store — the wallet empties before you do"),
        L("最后一点课间的体面，是回教室坐直", "The last shred of break-time dignity is sitting upright back in class"),
        L("晚课间刷到的下饭视频，下节课会在你脑子里重播", "The videos you scrolled will replay in your head all through next class")
    )

    // ── 格言 · 加菜：没课/课前/放学 × 时段 ──
    val extrasFreeDawn: List<L10n> = listOf(
        L("这么早没课，你确定不是忘看课表", "No class this early — are you sure you didn't misread the schedule?"),
        L("早起没事干，是奢侈，也是浪费", "Being up early with nothing to do is a luxury and a waste"),
        L("别睡回笼了，睡完中午更废", "Don't go back to sleep — you'll be wrecked until noon"),
        L("五点醒着还不学也不睡，卡在人生加载界面", "Awake at five, neither studying nor sleeping — stuck on life's loading screen"),
        L("清晨没课，食堂都比你有安排", "No class this morning — even the canteen has more plans than you"),
        L("这个点的自由，是昨晚熬夜的分期付款", "Freedom at this hour is an installment plan paying off last night's late run"),
        L("别感动自己起得早，起得早也没干正事", "Don't congratulate yourself for being up early — nothing real got done"),
        L("窗外鸟都开工了，你还在缓冲", "The birds outside are already at work and you're still buffering")
    )
    val extrasFreeMorning: List<L10n> = listOf(
        L("上午没课，图书馆有空位，你有借口", "No morning class, the library has seats — now you have an excuse"),
        L("别人在上课，你在计划——然后计划失败", "They're in class; you're making plans — then the plans fail"),
        L("自由的上午，通常从「先躺十分钟」开始", "A free morning usually starts with 'just ten more minutes of lying down'"),
        L("早饭可以慢慢吃，但别吃到中午", "Take your time with breakfast — just don't stretch it to noon"),
        L("没课的上午，手机电量掉得比绩点快", "On a class-free morning, battery drains faster than your GPA"),
        L("说好预习，结果在给收藏夹分类", "You promised to preview lessons — instead you're organizing your bookmarks"),
        L("别人在教室渡劫，你在宿舍修仙", "They're surviving class; you're cultivating immortality in the dorm"),
        L("上午的安静很贵，你用来刷完了", "Morning quiet is precious — and you scrolled it all away")
    )
    val extrasFreeNoon: List<L10n> = listOf(
        L("没课的中午，吃慢点，反正没人催你上课", "A class-free noon: eat slowly, nobody's rushing you to class"),
        L("午睡可以，别睡到以为今天是周末", "A nap is fine — just don't sleep until you believe it's the weekend"),
        L("饭点自由，是你今天最实在的自由", "Freedom at mealtime is the realest freedom you get today"),
        L("别人抢座，你抢被子，赛道不同", "They race for seats, you race for the blanket — different tracks"),
        L("中午的计划：学习。中午的实际：饭+躺", "Noon plan: study. Noon reality: food plus horizontal"),
        L("没课的午饭吃出了放假的感觉，然后就真放假了一下午", "Lunch with no class felt like a holiday — so the afternoon became one"),
        L("别点太多，下午你会困到骂自己", "Don't order too much — the afternoon you will curse this"),
        L("午后的太阳不等你，你还在第二碗", "The afternoon sun won't wait, and you're still on your second bowl")
    )
    val extrasFreeAfternoon: List<L10n> = listOf(
        L("下午没课，阳光这么好，适合焦虑", "No afternoon class and such lovely weather — perfect for anxiety"),
        L("别把空闲过成加班的愧疚，也别过成纯刷", "Don't spend the free time on work guilt — or on pure scrolling either"),
        L("这个点还躺着的，晚上会还的", "Still horizontal at this hour? The evening will collect"),
        L("说好下午学习，结果下午在想晚上吃什么", "You said you'd study this afternoon — instead you're planning dinner"),
        L("没课的下午，是给手机充电，不是给自己充电", "A class-free afternoon recharges your phone, not you"),
        L("别人实验报告，你实验发呆", "They're writing lab reports; you're experimenting with spacing out"),
        L("阳光透过窗帘：提示你该出门了，你当暖被", "Sunlight through the curtains says it's time to go out; you use it to warm the blanket"),
        L("下午的自由很长，进度条一直是 0%", "The afternoon feels long — the progress bar stays at 0%")
    )
    val extrasFreeEvening: List<L10n> = listOf(
        L("晚上没课，自由的味道是手机发烫", "No evening class — freedom smells like an overheating phone"),
        L("别报复性熬夜了，明天没有课也会困", "Skip the revenge late night — you'll be tired tomorrow even without class"),
        L("空出来的晚上，最后都交给了短视频", "The open evening ends up handed over to short videos"),
        L("晚上的计划很大，手很诚实地打开了娱乐软件", "The evening plan is ambitious; the hand honestly opened an entertainment app"),
        L("没课的夜，更容易把「明天一定」说出口", "On class-free nights, 'tomorrow for sure' slips out more easily"),
        L("别人在晚自习，你在晚自习游戏（也算自习？）", "They're in evening study hall; you're studying your game (self-study, technically?)"),
        L("晚饭后的躺平，会一路躺到愧疚", "The post-dinner sprawl tends to last until the guilt kicks in"),
        L("别开新番了，开了就是通宵预告", "Don't start a new anime — one episode is an all-nighter preview")
    )
    val extrasFreeNight: List<L10n> = listOf(
        L("没课的晚上，更容易把自己熬废", "Class-free nights make it easier to ruin yourself staying up"),
        L("别人晚自习，你晚自习手机——也算自习？", "Evening study for them; evening phone study for you — counts as studying?"),
        L("夜深了，你的待办列表还醒着，你快睡了", "It's late — your to-do list is still awake and you're nearly asleep"),
        L("没课的夜很长，玩起来又太短", "Class-free nights are long — until you start playing"),
        L("说好只玩一局，天都快亮了还没「只」", "You said one match — it's almost dawn and the 'one' is still missing"),
        L("别把自由夜过成通宵前传", "Don't turn a free night into an all-nighter prologue"),
        L("月亮上班了，你的正事还没上班", "The moon has clocked in; your real work never did"),
        L("这个点还不睡的自由，明天会连本带利收走", "The freedom of staying up now will be collected tomorrow with interest")
    )
}
