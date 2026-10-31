/**
 * 课表主页面（鸿蒙端第一屏）。
 *
 * 这一版刻意只做「看得见的正确」：周视图网格 + 翻周 + 课程详情 + 今日高亮。
 * 数据来自本地 preferences（首启播种示例课表），导入 / 编辑 / 提醒都还没接。
 *
 * 页面自身的口径（与 Android 端 CourseScheduleApp 对齐）：
 * - **周次是页面状态，不是数据**：翻周只改 viewWeek，不写库；点「本周」跳回真实周。
 * - 隐藏无课的周末：「智能显示周末」开启且本周末无课时只渲染 5 列。
 * - 本周不上但存在的课**仍然渲染**（降透明度）：直接不画会让用户以为课被删了。
 */

import { common } from '@kit.AbilityKit';
import { ScheduleStore } from '../data/ScheduleStore';
import {
  Course,
  colorIndexOf,
  displayName,
  isActiveInWeek,
  noteForWeek,
  pickNonCurrentWeekCourse,
  sectionText,
  weekText
} from '../model/Course';
import {
  allSectionSlots,
  breakDividers,
  defaultTimeConfig,
  SectionSlot,
  TimeConfig
} from '../model/TimeConfig';
import {
  addDays,
  clampWeek,
  formatMonthDay,
  isSameDay,
  mondayOfWeek,
  parseYmd,
  WEEKDAY_LABELS,
  weekIndexFor
} from '../model/Week';
import { CourseLane, DayCell, WeekGrid } from '../ui/components/WeekGrid';
import { cardFill, cardInk, COURSE_COLORS, Theme } from '../ui/theme/Tokens';

@Entry
@Component
struct Index {
  @StorageProp('darkMode') darkMode: boolean = false;
  @StorageProp('topInsetVp') topInsetVp: number = 0;
  @StorageProp('bottomInsetVp') bottomInsetVp: number = 0;

  @State courses: Course[] = [];
  @State timeConfig: TimeConfig = defaultTimeConfig();
  @State classStartYmd: string = '';
  @State totalWeeks: number = 20;
  /** 用户正在看的周 */
  @State viewWeek: number = 1;

  @State showDetail: boolean = false;
  @State detailLane: CourseLane = blankLane();

  private theme(): Theme {
    return Theme.of(this.darkMode);
  }

  async aboutToAppear(): Promise<void> {
    const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
    await ScheduleStore.open(ctx);
    const doc = await ScheduleStore.load();
    this.courses = doc.courses;
    this.timeConfig = doc.timeConfig;
    this.classStartYmd = doc.classStart;
    this.totalWeeks = doc.totalWeeks;
    // 打开就落在真实周上，而不是上次翻到的那一周：翻周是浏览，不是改设置。
    const real = weekIndexFor(parseYmd(doc.classStart), new Date());
    this.viewWeek = clampWeek(real, doc.totalWeeks);
  }

  // ── 派生数据 ──────────────────────────────────────────────────────────────

  private slots(): SectionSlot[] {
    return allSectionSlots(this.timeConfig);
  }

  private weekStart(): Date {
    return mondayOfWeek(parseYmd(this.classStartYmd), this.viewWeek);
  }

  /**
   * 本页要显示的星期列。周末无课时收成 5 列 ——
   * 判据是「本周有没有课」，不是「有没有周末的课」：周六有课时整列必须出现。
   */
  private visibleDows(): number[] {
    const all: number[] = [];
    for (let d = 1; d <= 7; d++) {
      all.push(d);
    }
    const today = new Date();
    for (let i = 0; i < this.courses.length; i++) {
      const c = this.courses[i];
      if (c.dayOfWeek >= 6 && isActiveInWeek(c, this.viewWeek)) {
        return all;
      }
    }
    // 今天就是周末时也放出来，否则「今日」高亮无处可落
    const dow = today.getDay() === 0 ? 7 : today.getDay();
    return dow >= 6 ? all : [1, 2, 3, 4, 5];
  }

  private dayCells(): DayCell[] {
    const out: DayCell[] = [];
    const start = this.weekStart();
    const today = new Date();
    const dows = this.visibleDows();
    for (let i = 0; i < dows.length; i++) {
      const dow = dows[i];
      const date = addDays(start, dow - 1);
      out.push({
        dow: dow,
        label: WEEKDAY_LABELS[dow - 1],
        dateText: formatMonthDay(date),
        isToday: isSameDay(date, today)
      });
    }
    return out;
  }

  /**
   * 把课收敛成网格里的一条条卡：同一天、同起止节次视为同一格。
   * 同格里有多门（不同周次）时，本周有课就显示本周那门，都没课就显示最近要上的那门。
   */
  private lanes(): CourseLane[] {
    const groups = new Map<string, Course[]>();
    for (let i = 0; i < this.courses.length; i++) {
      const c = this.courses[i];
      const key = `${c.dayOfWeek}-${c.startSection}-${c.endSection}`;
      const bucket = groups.get(key);
      if (bucket === undefined) {
        groups.set(key, [c]);
      } else {
        bucket.push(c);
      }
    }

    const out: CourseLane[] = [];
    groups.forEach((list: Course[]) => {
      const active: Course[] = [];
      for (let i = 0; i < list.length; i++) {
        if (isActiveInWeek(list[i], this.viewWeek)) {
          active.push(list[i]);
        }
      }
      const rep = pickNonCurrentWeekCourse(active.length > 0 ? active : list, this.viewWeek);
      if (rep === null) {
        return;
      }
      const color = COURSE_COLORS[colorIndexOf(rep)];
      const note = noteForWeek(rep, this.viewWeek);
      out.push({
        id: rep.id,
        day: rep.dayOfWeek,
        startSection: rep.startSection,
        endSection: rep.endSection,
        name: displayName(rep),
        room: rep.classroom,
        teacher: rep.teacher,
        fill: cardFill(color, this.darkMode),
        ink: cardInk(color, this.darkMode),
        hasNote: note.length > 0,
        faded: active.length === 0,
        color: color,
        sectionText: sectionText(rep),
        weekText: weekText(rep),
        note: note
      });
    });

    out.sort((a: CourseLane, b: CourseLane) => (a.day !== b.day) ? (a.day - b.day)
      : (a.startSection - b.startSection));
    return out;
  }

  private isCurrentWeek(): boolean {
    return this.viewWeek === clampWeek(weekIndexFor(parseYmd(this.classStartYmd), new Date()), this.totalWeeks);
  }

  private goWeek(delta: number): void {
    const next = clampWeek(this.viewWeek + delta, this.totalWeeks);
    if (next === this.viewWeek) {
      return;
    }
    this.viewWeek = next;
  }

  private goToday(): void {
    this.viewWeek = clampWeek(weekIndexFor(parseYmd(this.classStartYmd), new Date()), this.totalWeeks);
  }

  // ── 顶栏 ──────────────────────────────────────────────────────────────────

  @Builder
  iconButton(label: string, action: string) {
    Text(label)
      .fontSize(15)
      .fontColor(this.theme().textPrimary)
      .width(34)
      .height(34)
      .textAlign(TextAlign.Center)
      .borderRadius(17)
      .backgroundColor(this.darkMode ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.05)')
      .onClick(() => {
        this.handleAction(action);
      })
  }

  // @Builder 的参数按值传递，函数类型在部分版本上不会随重建更新 ——
  // 所以统一传「动作名」，在这里分发，别把闭包塞进 builder。
  private handleAction(action: string): void {
    if (action === 'prev') {
      this.goWeek(-1);
    } else if (action === 'next') {
      this.goWeek(1);
    } else if (action === 'today') {
      this.goToday();
    } else {
      this.toast('这个入口还没接');
    }
  }

  @Builder
  topBar() {
    Row() {
      Row() {
        this.iconButton('‹', 'prev')
        Blank().width(6)
        this.iconButton('›', 'next')
      }

      Blank()

      Column() {
        Text(`第${this.viewWeek}周`)
          .fontSize(20)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.theme().textPrimary)
        Text(this.isCurrentWeek() ? '本周' : `${formatMonthDay(this.weekStart())} 起`)
          .fontSize(10)
          .margin({ top: 1 })
          .fontColor(this.theme().textTertiary)
      }
      .onClick(() => this.goToday())

      Blank()

      this.iconButton('本', 'today')
    }
    .width('100%')
    .height(56)
    .padding({ left: 12, right: 12 })
  }

  // ── 底栏 ──────────────────────────────────────────────────────────────────

  @Builder
  tab(label: string, active: boolean, action: string) {
    Text(label)
      .fontSize(13)
      .fontWeight(active ? FontWeight.Medium : FontWeight.Regular)
      .fontColor(active ? this.theme().textPrimary
        : (this.darkMode ? 'rgba(255,255,255,0.45)' : 'rgba(0,0,0,0.4)'))
      .padding({ left: 18, right: 18, top: 8, bottom: 8 })
      .borderRadius(16)
      .backgroundColor(active
        ? (this.darkMode ? 'rgba(255,255,255,0.14)' : 'rgba(255,255,255,0.95)')
        : Color.Transparent)
      .onClick(() => {
        this.handleAction(action);
      })
  }

  @Builder
  bottomBar() {
    Row() {
      this.tab('今日', false, 'todayTab')
      Blank().width(4)
      this.tab('课表', true, 'scheduleTab')
      Blank().width(4)
      this.tab('设置', false, 'settingsTab')
    }
    .padding(5)
    .borderRadius(24)
    .backgroundColor(this.darkMode ? 'rgba(38,38,40,0.86)' : 'rgba(255,255,255,0.86)')
    .border({ width: 0.5, color: this.darkMode ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.06)' })
    .shadow({
      radius: 18,
      color: this.darkMode ? 'rgba(0,0,0,0.6)' : 'rgba(0,0,0,0.10)',
      offsetY: 6
    })
  }

  // ── 课程详情 ──────────────────────────────────────────────────────────────

  @Builder
  detailSheet() {
    Column() {
      Row()
        .width(36)
        .height(4)
        .borderRadius(2)
        .backgroundColor(this.darkMode ? 'rgba(255,255,255,0.18)' : 'rgba(0,0,0,0.14)')
        .margin({ top: 8, bottom: 14 })

      Row() {
        Column() {
          Text(this.detailLane.name)
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.theme().textPrimary)
            .maxLines(2)
          Text(`${this.detailLane.sectionText} · ${this.detailLane.weekText}`)
            .fontSize(12)
            .margin({ top: 6 })
            .fontColor(this.theme().textSecondary)
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        Row()
          .width(10)
          .height(34)
          .borderRadius(5)
          .backgroundColor(this.detailLane.color)
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)

      if (this.detailLane.room.length > 0) {
        this.detailRow('地点', this.detailLane.room)
      }
      if (this.detailLane.teacher.length > 0) {
        this.detailRow('教师', this.detailLane.teacher)
      }
      if (this.detailLane.note.length > 0) {
        this.detailRow('备注', this.detailLane.note)
      }
      if (this.detailLane.faded) {
        Text('这门课本周不上')
          .fontSize(11)
          .fontColor(this.theme().textTertiary)
          .margin({ top: 14 })
      }

      Text('关闭')
        .fontSize(14)
        .fontColor(this.theme().textPrimary)
        .width('100%')
        .height(44)
        .textAlign(TextAlign.Center)
        .margin({ top: 18 })
        .borderRadius(14)
        .backgroundColor(this.darkMode ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.05)')
        .onClick(() => {
          this.showDetail = false;
        })
    }
    .width('100%')
    .padding({ left: 18, right: 18, bottom: 16 })
    .alignItems(HorizontalAlign.Start)
    .borderRadius({ topLeft: 22, topRight: 22 })
    .backgroundColor(this.darkMode ? '#1C1C1E' : '#FFFFFF')
  }

  @Builder
  detailRow(key: string, value: string) {
    Row() {
      Text(key)
        .fontSize(12)
        .fontColor(this.theme().textTertiary)
        .width(44)
      Text(value)
        .fontSize(13)
        .fontColor(this.theme().textSecondary)
        .layoutWeight(1)
    }
    .width('100%')
    .margin({ top: 12 })
    .alignItems(VerticalAlign.Top)
  }

  private toast(msg: string): void {
    this.getUIContext().getPromptAction().showToast({ message: msg, duration: 1500 });
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      Column() {
        Blank().height(this.topInsetVp)

        this.topBar()

        Column() {
          WeekGrid({
            days: this.dayCells(),
            lanes: this.lanes(),
            slots: this.slots(),
            dividers: breakDividers(this.timeConfig),
            dark: this.darkMode,
            showDividers: true,
            onTapCourse: (lane: CourseLane) => {
              this.detailLane = lane;
              this.showDetail = true;
            }
          })
        }
        .width('100%')
        .layoutWeight(1)

        Column() {
          this.bottomBar()
        }
        .padding({ top: 8, bottom: 10 + this.bottomInsetVp })
      }
      .width('100%')
      .height('100%')

      if (this.showDetail) {
        Column()
          .width('100%')
          .height('100%')
          .backgroundColor('rgba(0,0,0,0.28)')
          .onClick(() => {
            this.showDetail = false;
          })

        Column() {
          this.detailSheet()
        }
        .width('100%')
        .transition(TransitionEffect.OPACITY
          .combine(TransitionEffect.translate({ y: 120 }))
          .animation({ duration: 220, curve: Curve.FastOutSlowIn }))
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.theme().bg)
  }
}

function blankLane(): CourseLane {
  const lane: CourseLane = {
    id: '',
    day: 1,
    startSection: 1,
    endSection: 1,
    name: '',
    room: '',
    teacher: '',
    fill: '#FFFFFF',
    ink: '#000000',
    hasNote: false,
    faded: false,
    color: '#4CAF50',
    sectionText: '',
    weekText: '',
    note: ''
  };
  return lane;
}
