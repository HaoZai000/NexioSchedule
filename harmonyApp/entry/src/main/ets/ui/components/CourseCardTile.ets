/**
 * 课程卡片 —— 网格里的最小显示单元。
 *
 * 三条口径与 Android 端 `CourseCard.kt` 一致：
 * 1. 卡片是**纯色粉彩块**，不是玻璃：窄格子下模糊没有信息增益，只会糊掉文字。
 * 2. 文字色由课程色派生（同色系加深），不写死黑/白 —— 否则紫色课上白字看不清。
 * 3. 空课名显示「特殊课程」。课程名、地点、教师三段各自独立换行，行数用 maxLines 截断。
 */

import { CardMetrics } from './CardMetrics';

@Component
export struct CourseCardTile {
  @Prop name: string = '';
  @Prop room: string = '';
  @Prop teacher: string = '';
  @Prop fill: string = '#EEEEEE';
  @Prop ink: string = '#333333';
  @Prop hasNote: boolean = false;
  @Prop showRoom: boolean = true;
  @Prop showTeacher: boolean = true;
  @Prop compact: boolean = false;

  build() {
    Stack({ alignContent: Alignment.BottomEnd }) {
      Column() {
        Text(this.name)
          .fontSize(this.compact ? 10 : 11)
          .lineHeight(this.compact ? 13 : 14)
          .fontColor(this.ink)
          .fontWeight(FontWeight.Medium)
          .textAlign(TextAlign.Center)
          .maxLines(this.compact ? 3 : 4)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .wordBreak(WordBreak.BREAK_ALL)
          .width('100%')

        if (this.showRoom && this.room.length > 0) {
          Text(`@${this.room}`)
            .fontSize(9)
            .lineHeight(11)
            .fontColor(this.ink)
            .opacity(0.72)
            .textAlign(TextAlign.Center)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .wordBreak(WordBreak.BREAK_ALL)
            .margin({ top: 2 })
            .width('100%')
        }

        if (this.showTeacher && this.teacher.length > 0) {
          Text(this.teacher)
            .fontSize(9)
            .lineHeight(11)
            .fontColor(this.ink)
            .opacity(0.6)
            .textAlign(TextAlign.Center)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ top: 1 })
            .width('100%')
        }
      }
      .width('100%')
      .height('100%')
      .padding({ left: 2, right: 2, top: 4, bottom: 4 })
      .justifyContent(FlexAlign.Center)

      if (this.hasNote) {
        Circle({ width: 4, height: 4 })
          .fill(this.ink)
          .opacity(0.55)
          .margin({ right: 4, bottom: 4 })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.fill)
    .borderRadius(CardMetrics.RADIUS)
    .clip(true)
  }
}
