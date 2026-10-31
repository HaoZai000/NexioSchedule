/**
 * 周视图网格。
 *
 * 结构照抄 Android 端：**左侧节次列 + 每日一列**，横向等分，纵向按节次固定高度。
 * 刻意不用 Grid 容器：
 * 1. 课程要跨节（2-3 节连堂），Grid 的 item 无法跨行，得自己叠；
 * 2. 午休/晚休分隔线要横跨所有列，用一个整宽叠层比每列各画一条更准。
 *
 * 本组件只负责画。哪些课上、哪些变灰（不属于本周）、哪几天显示，全部由页面算好传进来 ——
 * 网格里再判周次就会出现两处口径。
 */

import { SectionSlot } from '../../model/TimeConfig';
import { GridMetrics } from '../theme/Tokens';
import { CourseCardTile } from './CourseCardTile';
import { CardMetrics } from './CardMetrics';

/** 网格里的一天 */
export interface DayCell {
  /** 1=周一 … 7=周日 */
  dow: number;
  label: string;
  /** "10/26" */
  dateText: string;
  isToday: boolean;
}

/** 一条要画的课：同槽位多门课已在上层收敛成一条 */
export interface CourseLane {
  id: string;
  day: number;
  startSection: number;
  endSection: number;
  name: string;
  room: string;
  teacher: string;
  fill: string;
  ink: string;
  hasNote: boolean;
  /** 本周不上（已结课 / 还没开课 / 单双周不匹配）：整卡降透明度 */
  faded: boolean;
  // ── 详情弹层用，卡片本身不画 ──
  color: string;
  sectionText: string;
  weekText: string;
  note: string;
}

@Component
export struct WeekGrid {
  @Prop days: DayCell[] = [];
  @Prop lanes: CourseLane[] = [];
  @Prop slots: SectionSlot[] = [];
  /** 分隔线插在第几节之后（午休=4，晚休=8） */
  @Prop dividers: number[] = [];
  @Prop dark: boolean = false;
  @Prop showDividers: boolean = true;
  @Prop showRoom: boolean = true;
  @Prop showTeacher: boolean = true;
  onTapCourse: (lane: CourseLane) => void = () => {};

  private lanesOf(day: number): CourseLane[] {
    const out: CourseLane[] = [];
    for (let i = 0; i < this.lanes.length; i++) {
      if (this.lanes[i].day === day) {
        out.push(this.lanes[i]);
      }
    }
    return out;
  }

  @Builder
  dayHeader(cell: DayCell) {
    Column() {
      Text(cell.label)
        .fontSize(13)
        .fontWeight(cell.isToday ? FontWeight.Bold : FontWeight.Medium)
        .fontColor(this.dark
          ? (cell.isToday ? '#FFFFFF' : '#9A9AA0')
          : (cell.isToday ? '#1C1C1E' : '#6B6B70'))
      Text(cell.dateText)
        .fontSize(10)
        .margin({ top: 3 })
        .fontColor(this.dark ? '#6A6A70' : '#A0A0A8')
    }
    .layoutWeight(1)
    .height(GridMetrics.DAY_HEADER_H)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  sectionCell(slot: SectionSlot) {
    Column() {
      Text(`${slot.index}`)
        .fontSize(11)
        .fontColor(this.dark ? '#8A8A90' : '#8E8E93')
      Text(slot.start)
        .fontSize(8)
        .margin({ top: 2 })
        .fontColor(this.dark ? '#5E5E64' : '#B4B4BA')
      Text(slot.end)
        .fontSize(8)
        .fontColor(this.dark ? '#5E5E64' : '#B4B4BA')
    }
    .width('100%')
    .height(GridMetrics.SECTION_H)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  dayColumn(cell: DayCell) {
    Stack({ alignContent: Alignment.TopStart }) {
      // 底色 + 节次分隔线：放到最底层，卡片压在上面
      Column() {
        ForEach(this.slots, (slot: SectionSlot) => {
          Row()
            .width('100%')
            .height(GridMetrics.SECTION_H)
            .border({
              width: { bottom: 0.5 },
              color: this.dark ? 'rgba(255,255,255,0.055)' : 'rgba(0,0,0,0.055)'
            })
        }, (slot: SectionSlot) => `line-${cell.dow}-${slot.index}`)
      }
      .width('100%')
      .height('100%')

      if (cell.isToday) {
        Column()
          .width('100%')
          .height('100%')
          .backgroundColor(this.dark ? 'rgba(255,255,255,0.032)' : 'rgba(0,0,0,0.022)')
      }

      ForEach(this.lanesOf(cell.dow), (lane: CourseLane) => {
        // 卡片靠「外层撑满格子 + 内边距留缝」定位，不用 calc()：
        // 列宽是等分的动态值，calc 里再减常量在窄屏上容易算出 0 宽。
        Column() {
          CourseCardTile({
            name: lane.name,
            room: lane.room,
            teacher: lane.teacher,
            fill: lane.fill,
            ink: lane.ink,
            hasNote: lane.hasNote,
            showRoom: this.showRoom,
            showTeacher: this.showTeacher,
            compact: (lane.endSection - lane.startSection) === 0
          })
        }
        .width('100%')
        .height((lane.endSection - lane.startSection + 1) * GridMetrics.SECTION_H)
        .padding(CardMetrics.GAP)
        .position({ x: 0, y: (lane.startSection - 1) * GridMetrics.SECTION_H })
        .opacity(lane.faded ? 0.42 : 1)
        .onClick(() => this.onTapCourse(lane))
      }, (lane: CourseLane) => lane.id)
    }
    .height(this.slots.length * GridMetrics.SECTION_H)
    .layoutWeight(1)
  }

  @Builder
  breakDivider(index: number, afterSection: number, label: string) {
    Row() {
      Row() {
        Text(label)
          .fontSize(9)
          .fontColor(this.dark ? '#7A7A80' : '#9C9CA2')
      }
      .padding({ left: 8, right: 8, top: 1, bottom: 1 })
      .backgroundColor(this.dark ? 'rgba(30,30,32,0.92)' : 'rgba(242,242,247,0.94)')
      .borderRadius(7)
    }
    .width('100%')
    .height(16)
    .justifyContent(FlexAlign.Center)
    .position({ x: 0, y: afterSection * GridMetrics.SECTION_H - 8 })
  }

  build() {
    Column() {
      // 星期头：左上角留出与节次列同宽的空白，保证列对齐
      Row() {
        Row().width(GridMetrics.SECTION_COL_W).height(GridMetrics.DAY_HEADER_H)
        ForEach(this.days, (cell: DayCell) => {
          this.dayHeader(cell)
        }, (cell: DayCell) => `h-${cell.dow}`)
      }
      .width('100%')

      Scroll() {
        Stack({ alignContent: Alignment.TopStart }) {
          Row() {
            Column() {
              ForEach(this.slots, (slot: SectionSlot) => {
                this.sectionCell(slot)
              }, (slot: SectionSlot) => `sec-${slot.index}`)
            }
            .width(GridMetrics.SECTION_COL_W)
            .height(this.slots.length * GridMetrics.SECTION_H)

            ForEach(this.days, (cell: DayCell) => {
              this.dayColumn(cell)
            }, (cell: DayCell) => `col-${cell.dow}`)
          }
          .width('100%')
          .alignItems(VerticalAlign.Top)

          if (this.showDividers) {
            ForEach(this.dividers, (after: number, idx: number) => {
              this.breakDivider(idx, after, idx === 0 ? '午休' : '晚休')
            }, (after: number) => `div-${after}`)
          }
        }
        .width('100%')
        .height(this.slots.length * GridMetrics.SECTION_H)
      }
      .layoutWeight(1)
      .width('100%')
      .scrollBar(BarState.Off)
      .edgeEffect(EdgeEffect.Spring)
    }
    .width('100%')
    .height('100%')
  }
}
