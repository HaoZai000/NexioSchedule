/**
 * 「课程表」桌面卡片（2*4，宽卡）。
 *
 * 照抄安卓 `CourseWidgetProviderStandard` + `widget_course_reminder_standard.xml`：
 * - 容器 padding 11/9/11/3，20dp 圆角灰底
 * - 头部「今天 / 周三」+「第N周」，14sp 粗体，左右各留 4dp
 * - 固定 2 个卡位（2 格高）：有课填内容，没课是空白白卡
 * - 卡内：时间竖排 12sp、4dp 色条、课程名 14sp + 信息 12sp、进行中的课右侧「N分钟结束」14sp 粗体
 * - 次日提醒触发后（showTomorrow）整卡切成「明日课程 / 周四」，不再按时间过滤
 */

interface ScheduleCourse {
  name: string;
  room: string;
  info: string;
  start: number;
  end: number;
  /** `#AARRGGBB`，与安卓小部件的课程色条同源 */
  color: string;
}

interface ScheduleDay {
  week: number;
  weekday: string;
  status: string;
  month: number;
  day: number;
  courses: Array<ScheduleCourse>;
}

const scheduleStorage: LocalStorage = new LocalStorage();

@Entry(scheduleStorage)
@Component
struct ScheduleWidgetCard {

  @LocalStorageProp('week') week: number = 0;
  @LocalStorageProp('weekday') weekday: string = '';
  @LocalStorageProp('status') status: string = 'empty';
  @LocalStorageProp('showTomorrow') showTomorrow: boolean = false;
  @LocalStorageProp('courses') courses: Array<ScheduleCourse> = [];
  @LocalStorageProp('tomorrow') tomorrow: ScheduleDay = {
    week: 0,
    weekday: '',
    status: 'empty',
    month: 0,
    day: 0,
    courses: []
  };

  private dayWeek(): number {
    return this.showTomorrow ? this.tomorrow.week : this.week;
  }

  private dayWeekday(): string {
    return this.showTomorrow ? this.tomorrow.weekday : this.weekday;
  }

  private dayStatus(): string {
    return this.showTomorrow ? this.tomorrow.status : this.status;
  }

  private headerText(): string {
    const prefix = this.showTomorrow ? '明日课程' : '今天';
    return this.dayWeekday() !== '' ? `${prefix} / ${this.dayWeekday()}` : prefix;
  }

  private weekText(): string {
    if (this.dayStatus() === 'holiday') {
      return '放假中';
    }
    if (this.dayStatus() === 'notStarted') {
      return '未开始';
    }
    return this.dayWeek() > 0 ? `第${this.dayWeek()}周` : '';
  }

  private emptyText(): string {
    if (this.dayStatus() === 'holiday') {
      return '假期中，暂无课程';
    }
    if (this.dayStatus() === 'notStarted') {
      return '学期暂未开始';
    }
    if (this.showTomorrow) {
      return '明日无课';
    }
    if (this.courses.length === 0) {
      return '今日无课';
    }
    return '今日课程已上完';
  }

  /** 今天的卡位过滤已结束的课；明日卡位直接按顺序取。 */
  private slot(index: number): ScheduleCourse | undefined {
    if (this.showTomorrow) {
      return index < this.tomorrow.courses.length ? this.tomorrow.courses[index] : undefined;
    }
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

  private clock(epoch: number): string {
    const date = new Date(epoch);
    const hour = date.getHours().toString().padStart(2, '0');
    const minute = date.getMinutes().toString().padStart(2, '0');
    return `${hour}:${minute}`;
  }

  /** 只有「正在上」且不是明日预览时才显示剩余分钟。 */
  private remaining(course: ScheduleCourse): string {
    if (this.showTomorrow) {
      return '';
    }
    const now: number = Date.now();
    if (course.start <= now && course.end > now) {
      return `${Math.max(1, Math.ceil((course.end - now) / 60000))}分钟结束`;
    }
    return '';
  }

  build() {
    Column() {
      Row() {
        Text(this.headerText())
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.widget_title'))
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.weekText())
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.widget_title'))
      }
      .width('100%')
      .padding({ left: 4, right: 4 })
      .margin({ bottom: 8 })

      if (this.slot(0) === undefined) {
        Column() {
          Text(this.emptyText())
            .fontSize(14)
            .fontColor($r('app.color.widget_empty'))
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
        .padding({ bottom: 20 })
      } else {
        ForEach([0, 1], (index: number) => {
          Row() {
            if (this.slot(index) !== undefined) {
              Column() {
                Text(this.clock(this.slot(index)!.start))
                  .fontSize(12)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.widget_time_start'))
                Text(this.clock(this.slot(index)!.end))
                  .fontSize(12)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.widget_time_end'))
              }
              .alignItems(HorizontalAlign.Start)
              .margin({ right: 10 })

              Column()
                .width(4)
                .height(28)
                .backgroundColor(this.slot(index)!.color !== ''
                  ? this.slot(index)!.color
                  : '#8A8A8E')
                .borderRadius(2)

              Column() {
                Text(this.slot(index)!.name)
                  .fontSize(14)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.widget_name'))
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                if (this.slot(index)!.info !== '') {
                  Text(this.slot(index)!.info)
                    .fontSize(12)
                    .fontWeight(FontWeight.Medium)
                    .fontColor($r('app.color.widget_time_end'))
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .margin({ top: 2 })
                }
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)
              .padding({ left: 10 })

              Text(this.remaining(this.slot(index)!))
                .fontSize(14)
                .fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.widget_now'))
            }
          }
          .width('100%')
          .layoutWeight(1)
          .alignItems(VerticalAlign.Center)
          .padding({ left: 12, right: 12, top: 4, bottom: 4 })
          .margin({ bottom: 8 })
          .backgroundColor(this.slot(index) !== undefined && this.remaining(this.slot(index)!) !== ''
            ? $r('app.color.widget_card_active')
            : $r('app.color.widget_card'))
          .borderRadius(12)
          .clip(true)
        }, (index: number) => `${index}`)
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 11, right: 11, top: 9, bottom: 3 })
    .alignItems(HorizontalAlign.Start)
    .backgroundColor($r('app.color.widget_background'))
    .borderRadius(20)
    .clip(true)
  }
}
