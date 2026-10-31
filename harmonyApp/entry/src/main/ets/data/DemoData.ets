/**
 * 首次启动的示例课表。
 *
 * 目的只有一个：**空课表看不出网格对不对**。真数据来自备份导入 / 教务导入，
 * 在那两条链路接上之前，先用一份「日期跟着今天走」的样例把渲染跑起来 ——
 * 起始日定成 8 周前的周一，所以今天恒落在第 9 周，翻周、今日高亮、单双周都能验。
 */

import { Course } from '../model/Course';
import { addDays, mondayOf, toYmd } from '../model/Week';

class CourseSeed {
  name: string = '';
  room: string = '';
  teacher: string = '';
  day: number = 1;
  from: number = 1;
  to: number = 1;
  color: number = 0;
  startWeek: number = 1;
  endWeek: number = 16;
  weekType: number = 0;
  selectedWeeks: number[] = [];
  note: string = '';
}

export class DemoData {
  /** 示例课表的开学日期（8 周前的周一） */
  static classStartYmd(): string {
    const monday = mondayOf(new Date());
    return toYmd(addDays(monday, -56));
  }

  static currentWeek(): number {
    return 9;
  }

  static courses(): Course[] {
    const seeds: CourseSeed[] = [];
    const push = (s: CourseSeed): void => {
      seeds.push(s);
    };

    const s1 = new CourseSeed();
    s1.name = '统计学';
    s1.room = '博105';
    s1.teacher = '张晓薇';
    s1.day = 1;
    s1.from = 1;
    s1.to = 2;
    s1.color = 0;
    push(s1);

    const s2 = new CourseSeed();
    s2.name = '统计学';
    s2.room = '博105';
    s2.teacher = '张晓薇';
    s2.day = 2;
    s2.from = 1;
    s2.to = 2;
    s2.color = 0;
    push(s2);

    const s3 = new CourseSeed();
    s3.name = '马克思主义基本原理';
    s3.room = '博107';
    s3.teacher = '孔维';
    s3.day = 1;
    s3.from = 3;
    s3.to = 4;
    s3.color = 2;
    s3.weekType = 1;
    push(s3);

    const s4 = new CourseSeed();
    s4.name = '马克思主义基本原理';
    s4.room = '博107';
    s4.teacher = '孔维';
    s4.day = 2;
    s4.from = 3;
    s4.to = 4;
    s4.color = 2;
    s4.weekType = 1;
    push(s4);

    const s5 = new CourseSeed();
    s5.name = '拓展英语I';
    s5.room = '安503-语音室';
    s5.teacher = '魏佳';
    s5.day = 3;
    s5.from = 3;
    s5.to = 4;
    s5.color = 5;
    push(s5);

    const s6 = new CourseSeed();
    s6.name = '概率论与数理统计';
    s6.room = '博109';
    s6.teacher = '李薇';
    s6.day = 4;
    s6.from = 4;
    s6.to = 5;
    s6.color = 7;
    push(s6);

    const s7 = new CourseSeed();
    s7.name = '概率论与数理统计';
    s7.room = '博109';
    s7.teacher = '李薇';
    s7.day = 5;
    s7.from = 4;
    s7.to = 5;
    s7.color = 7;
    push(s7);

    const s8 = new CourseSeed();
    s8.name = '数字经济学';
    s8.room = '博106';
    s8.teacher = '郑琼娥';
    s8.day = 4;
    s8.from = 1;
    s8.to = 3;
    s8.color = 8;
    push(s8);

    const s9 = new CourseSeed();
    s9.name = '数字营销';
    s9.room = '博106';
    s9.teacher = '郑琼娥';
    s9.day = 5;
    s9.from = 1;
    s9.to = 3;
    s9.color = 6;
    push(s9);

    const s10 = new CourseSeed();
    s10.name = '三茶统筹专题';
    s10.room = '博106';
    s10.teacher = '金琦芳';
    s10.day = 1;
    s10.from = 6;
    s10.to = 7;
    s10.color = 5;
    push(s10);

    const s11 = new CourseSeed();
    s11.name = '体育3(羽毛球)';
    s11.room = '安溪运动场';
    s11.teacher = '陈龙强';
    s11.day = 2;
    s11.from = 6;
    s11.to = 7;
    s11.color = 4;
    push(s11);

    const s12 = new CourseSeed();
    s12.name = '统计学';
    s12.room = '机房1-2902';
    s12.teacher = '张晓薇';
    s12.day = 3;
    s12.from = 6;
    s12.to = 7;
    s12.color = 0;
    push(s12);

    const s13 = new CourseSeed();
    s13.name = '数字经济学';
    s13.room = '博106';
    s13.teacher = '郑琼娥';
    s13.day = 5;
    s13.from = 6;
    s13.to = 8;
    s13.color = 8;
    push(s13);

    const s14 = new CourseSeed();
    s14.name = '数字营销';
    s14.room = '网络教学平台5';
    s14.teacher = '郑琼娥';
    s14.day = 2;
    s14.from = 8;
    s14.to = 9;
    s14.color = 6;
    s14.endWeek = 8;
    push(s14);

    const s15 = new CourseSeed();
    s15.name = '数字营销';
    s15.room = '网络教学平台5';
    s15.teacher = '郑琼娥';
    s15.day = 3;
    s15.from = 8;
    s15.to = 9;
    s15.color = 6;
    s15.endWeek = 8;
    push(s15);

    const s16 = new CourseSeed();
    s16.name = '数字营销';
    s16.room = '网络教学平台5';
    s16.teacher = '郑琼娥';
    s16.day = 5;
    s16.from = 8;
    s16.to = 10;
    s16.color = 6;
    push(s16);

    const list: Course[] = [];
    for (let i = 0; i < seeds.length; i++) {
      const s = seeds[i];
      const c: Course = {
        id: `demo-${i + 1}`,
        name: s.name,
        classroom: s.room,
        teacher: s.teacher,
        dayOfWeek: s.day,
        startSection: s.from,
        endSection: s.to,
        startWeek: s.startWeek,
        endWeek: s.endWeek,
        weekType: s.weekType,
        colorIndex: s.color,
        selectedWeeks: s.selectedWeeks,
        scheduleId: 'default',
        lastModified: 0,
        isCustomTime: false,
        customStartTime: '',
        customEndTime: '',
        note: s.note,
        weekNotes: {}
      };
      list.push(c);
    }
    return list;
  }
}
