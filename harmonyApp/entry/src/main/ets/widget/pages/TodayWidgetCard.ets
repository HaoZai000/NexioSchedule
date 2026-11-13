/**
 * 「今日课程」桌面卡片（2*2）。
 *
 * 照抄安卓 `TodayCourseWidgetProviderStandard` + `widget_today_course_standard.xml`：
 * - 容器 padding 10/12/10/12，20dp 圆角灰底
 * - 头部「周三」+「第N周」，14sp 粗体
 * - 只显示**一节**课：第一节没结束就显示它（正在上就是它），否则显示下一节
 * - 内容整体贴底：课程名 17sp、时间 14sp、教室 14sp、底部「还有N节课 / 没有其他课程」+ 彩色圆点
 * - 圆点规则：正在上课 → 其余未结束的课；还没上课 → 今天所有未结束的课（含当前这一节）
 * - 次日提醒触发后（showTomorrow）标题变「明天」，内容整段换成明天
 */

interface TodayCourse {
  name: string;
  room: string;
  info: string;
  start: number;
  end: number;
  /** `#AARRGGBB`，与安卓小部件的课程色点同源 */
  color: string;
}

interface TodayDay {
  week: number;
  weekday: string;
  status: string;
  month: number;
  day: number;
  courses: Array<TodayCourse>;
}

const todayStorage: LocalStorage = new LocalStorage();

@Entry(todayStorage)
@Component
struct TodayWidgetCard {

  @LocalStorageProp('week') week: number = 0;
  @LocalStorageProp('weekday') weekday: string = '';
  @LocalStorageProp('status') status: string = 'empty';
  @LocalStorageProp('showTomorrow') showTomorrow: boolean = false;
  @LocalStorageProp('courses') courses: Array<TodayCourse> = [];
  @LocalStorageProp('tomorrow') tomorrow: TodayDay = {
    week: 0,
    weekday: '',
    status: 'empty',
    month: 0,
    day: 0,
    courses: []
  };

  private titleText(): string {
    if (this.showTomorrow) {
      return '明天';
    }
    return this.weekday !== '' ? this.weekday : '今天';
  }

  private dayWeek(): number {
    return this.showTomorrow ? this.tomorrow.week : this.week;
  }

  private dayStatus(): string {
    return this.showTomorrow ? this.tomorrow.status : this.status;
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

  /** 待上的课：今天过滤已结束的，明日直接用全部。 */
  private unfinished(): Array<TodayCourse> {
    if (this.showTomorrow) {
      return this.tomorrow.courses;
    }
    const now: number = Date.now();
    const list: Array<TodayCourse> = [];
    for (let i = 0; i < this.courses.length; i++) {
      if (this.courses[i].end > now) {
        list.push(this.courses[i]);
      }
    }
    return list;
  }

  private display(): TodayCourse | undefined {
    const list = this.unfinished();
    return list.length > 0 ? list[0] : undefined;
  }

  private inClass(course: TodayCourse): boolean {
    if (this.showTomorrow) {
      return false;
    }
    const now: number = Date.now();
    return course.start <= now && course.end > now;
  }

  /** 与安卓一致：在课时圆点排除当前课，未开课时包含当前课。 */
  private dotCourses(course: TodayCourse): Array<TodayCourse> {
    const list = this.unfinished();
    const ongoing = this.inClass(course);
    const result: Array<TodayCourse> = [];
    for (let i = 0; i < list.length && result.length < 8; i++) {
      if (!ongoing || list[i].start !== course.start) {
        result.push(list[i]);
      }
    }
    return result;
  }

  private remainingText(course: TodayCourse): string {
    const list = this.unfinished();
    let others: number = 0;
    for (let i = 0; i < list.length; i++) {
      if (list[i].start !== course.start) {
        others++;
      }
    }
    return others > 0 ? `还有${others}节课` : '没有其他课程';
  }

  private clock(epoch: number): string {
    const date = new Date(epoch);
    const hour = date.getHours().toString().padStart(2, '0');
    const minute = date.getMinutes().toString().padStart(2, '0');
    return `${hour}:${minute}`;
  }

  build() {
    Column() {
      Row() {
        Text(this.titleText())
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.widget_title'))
          .layoutWeight(1)
        Text(this.weekText())
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.widget_title'))
      }
      .width('100%')
      .padding({ left: 4, right: 4 })
      .margin({ bottom: 8 })

      if (this.display() === undefined) {
        Column() {
          Text(this.emptyText())
            .fontSize(14)
            .fontColor($r('app.color.widget_empty'))
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
        .padding({ bottom: 12 })
      } else {
        Column() {
          Text(this.display()!.name)
            .fontSize(17)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.widget_name'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
            .padding({ left: 4 })
            .margin({ bottom: 4 })

          Text(`${this.clock(this.display()!.start)}-${this.clock(this.display()!.end)}`)
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.widget_time_end'))
            .width('100%')
            .padding({ left: 4 })
            .margin({ bottom: 2 })

          Text(this.display()!.room)
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.widget_time_end'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
            .padding({ left: 4 })
            .margin({ bottom: 16 })

          Row() {
            Text(this.remainingText(this.display()!))
              .fontSize(12)
              .fontColor($r('app.color.widget_time_end'))

            ForEach(this.dotCourses(this.display()!), (course: TodayCourse) => {
              Circle({ width: 7, height: 7 })
                .fill(course.color !== '' ? course.color : '#8A8A8E')
                .margin({ left: 4 })
            }, (course: TodayCourse) => `${course.start}`)
          }
          .width('100%')
          .padding({ left: 4 })
        }
        .width('100%')
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        .justifyContent(FlexAlign.End)
      }
    }
    .width('100%')
    .height('100%')
    .padding({ left: 10, right: 10, top: 12, bottom: 12 })
    .alignItems(HorizontalAlign.Start)
    .backgroundColor($r('app.color.widget_single_bg'))
    .borderRadius(20)
    .clip(true)
  }
}
