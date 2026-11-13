/**
 * 「今日与明日」桌面卡片（2*4，宽卡）。
 *
 * 照抄安卓 `TodayTomorrowCourseWidgetProvider` + `widget_today_tomorrow_course.xml`：
 * - 容器 padding 9/9/9/2
 * - 表头两栏「今天 / 周三」「明天 / 周四」，14sp 粗体，列间距 8dp
 * - 每栏固定 2 个卡位（2 格高），卡内：4dp 色条 + 课程名 14sp + 时间 12sp
 * - 今天列过滤已结束的课；明天列显示全天课程
 * - 某栏没课：整列居中显示空态文案
 */

interface TtCourse {
  name: string;
  room: string;
  info: string;
  infoShort: string;
  start: number;
  end: number;
  /** `#AARRGGBB`，与安卓小部件的课程色条同源 */
  color: string;
}

interface TtDay {
  week: number;
  weekday: string;
  status: string;
  month: number;
  day: number;
  courses: Array<TtCourse>;
}

const ttStorage: LocalStorage = new LocalStorage();

@Entry(ttStorage)
@Component
struct TodayTomorrowWidgetCard {

  @LocalStorageProp('week') week: number = 0;
  @LocalStorageProp('weekday') weekday: string = '';
  @LocalStorageProp('status') status: string = 'empty';
  @LocalStorageProp('courses') courses: Array<TtCourse> = [];
  @LocalStorageProp('tomorrow') tomorrow: TtDay = {
    week: 0,
    weekday: '',
    status: 'empty',
    month: 0,
    day: 0,
    courses: []
  };

  private todayEmpty(): string {
    if (this.status === 'holiday') {
      return '假期中，暂无课程';
    }
    if (this.status === 'notStarted') {
      return '学期暂未开始';
    }
    return this.courses.length === 0 ? '今日无课' : '今日课程已上完';
  }

  private tomorrowEmpty(): string {
    if (this.tomorrow.status === 'holiday') {
      return '假期中，暂无课程';
    }
    return '明日无课';
  }

  /** 今天列：已结束的课不显示。 */
  private todaySlot(index: number): TtCourse | undefined {
    const now: number = Date.now();
    let cursor: number = 0;
    for (let i = 0; i < this.courses.length; i++) {
      if (this.courses[i].end > now) {
        if (cursor === index) {
          return this.courses[i];
        }
        cursor++;
      }
    }
    return undefined;
  }

  /** 明天列：全天课程。 */
  private tomorrowSlot(index: number): TtCourse | undefined {
    return index < this.tomorrow.courses.length ? this.tomorrow.courses[index] : undefined;
  }

  private clock(epoch: number): string {
    const date = new Date(epoch);
    const hour = date.getHours().toString().padStart(2, '0');
    const minute = date.getMinutes().toString().padStart(2, '0');
    return `${hour}:${minute}`;
  }

  /** 正在上的课与安卓一致：卡片底色变浅蓝。 */
  private isActive(course: TtCourse): boolean {
    const now: number = Date.now();
    return course.start <= now && course.end > now;
  }

  @Builder
  courseCard(course: TtCourse) {
    Row() {
      Column()
        .width(4)
        .height(28)
        .borderRadius(2)
        .backgroundColor(course.color !== '' ? course.color : '#8A8A8E')

      Column() {
        Text(course.name)
          .fontSize(14)
          .lineHeight(17)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.widget_name'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        Text(`${this.clock(course.start)}-${this.clock(course.end)}`)
          .fontSize(12)
          .lineHeight(14)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.widget_time_start'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        Text(course.infoShort !== '' ? course.infoShort : course.room)
          .fontSize(12)
          .lineHeight(14)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.widget_time_end'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .padding({ left: 10 })
      .justifyContent(FlexAlign.Center)
    }
    .width('100%')
    .layoutWeight(1)
    .alignItems(VerticalAlign.Center)
    .padding({ left: 8, right: 12, top: 3, bottom: 3 })
    .margin({ bottom: 7 })
    .backgroundColor(this.isActive(course)
      ? $r('app.color.widget_card_active')
      : $r('app.color.widget_card'))
    .borderRadius(12)
    .clip(true)
  }

  @Builder
  emptyCard(text: string) {
    Column() {
      Text(text)
        .fontSize(14)
        .fontColor($r('app.color.widget_empty'))
    }
    .width('100%')
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  column(isTomorrow: boolean) {
    Column() {
      if (isTomorrow ? this.tomorrowSlot(0) === undefined : this.todaySlot(0) === undefined) {
        this.emptyCard(isTomorrow ? this.tomorrowEmpty() : this.todayEmpty())
      } else {
        ForEach([0, 1], (index: number) => {
          if (isTomorrow) {
            if (this.tomorrowSlot(index) !== undefined) {
              this.courseCard(this.tomorrowSlot(index)!)
            } else {
              Row()
                .width('100%')
                .layoutWeight(1)
                .margin({ bottom: 7 })
                .backgroundColor($r('app.color.widget_card'))
                .borderRadius(12)
            }
          } else {
            if (this.todaySlot(index) !== undefined) {
              this.courseCard(this.todaySlot(index)!)
            } else {
              Row()
                .width('100%')
                .layoutWeight(1)
                .margin({ bottom: 7 })
                .backgroundColor($r('app.color.widget_card'))
                .borderRadius(12)
            }
          }
        }, (index: number) => `${isTomorrow ? 'm' : 't'}${index}`)
      }
    }
    .layoutWeight(1)
    .height('100%')
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      Row() {
        Text(`今天 / ${this.weekday !== '' ? this.weekday : '—'}`)
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.widget_title'))
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(`明天 / ${this.tomorrow.weekday !== '' ? this.tomorrow.weekday : '—'}`)
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.widget_title'))
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .padding({ left: 4 })
      }
      .width('100%')
      .padding({ left: 4, right: 4 })
      .margin({ bottom: 6 })

      Row() {
        this.column(false)
        Column()
          .width(8)
        this.column(true)
      }
      .width('100%')
      .layoutWeight(1)
      .alignItems(VerticalAlign.Top)
    }
    .width('100%')
    .height('100%')
    .padding({ left: 9, right: 9, top: 9, bottom: 2 })
    .alignItems(HorizontalAlign.Start)
    .backgroundColor($r('app.color.widget_background'))
    .borderRadius(20)
    .clip(true)
  }
}
