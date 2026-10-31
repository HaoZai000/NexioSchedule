/**
 * 作息配置 —— 与 `core/data/model/TimeConfig.kt` 的**时间计算部分**对齐。
 *
 * 只搬「算课表要用的」：节次骨架 + 三段时间参数 + sectionTimes 覆盖表。
 * 多作息方案（routines）、特殊时段（specialBlocks）留到存储改造时再进来 —— 它们的
 * 语义是「同一套骨架换时间」，对网格渲染没有新增接口，先不做半成品。
 */

export interface TimeConfig {
  name: string;
  /** 上午 / 下午 / 晚上各几节（0 表示该时段无课） */
  morningSections: number;
  afternoonSections: number;
  eveningSections: number;
  /** 快速排课：用「时长 + 课间」推算各节时间，忽略 sectionTimes */
  quickTimeEnabled: boolean;
  classDuration: number;
  shortBreak: number;
  longBreakEnabled: boolean;
  longBreakMorning: number;
  longBreakAfternoon: number;
  longBreakEvening: number;
  longBreakMorningSection: number;
  longBreakAfternoonSection: number;
  longBreakEveningSection: number;
  morningStartHour: number;
  morningStartMinute: number;
  afternoonStartHour: number;
  afternoonStartMinute: number;
  eveningStartHour: number;
  eveningStartMinute: number;
  /** key = "morning_1"，value = "HH:mm-HH:mm"；仅 quickTimeEnabled=false 时生效 */
  sectionTimes: Record<string, string>;
}

/** 网格里一节的时间格 */
export interface SectionSlot {
  /** 全局绝对节次号，1 起 */
  index: number;
  /** "08:00" */
  start: string;
  /** "08:45" */
  end: string;
  /** "08:00-08:45" */
  label: string;
}

export function defaultTimeConfig(): TimeConfig {
  const tc: TimeConfig = {
    name: '默认配置',
    morningSections: 4,
    afternoonSections: 4,
    eveningSections: 4,
    quickTimeEnabled: false,
    classDuration: 45,
    shortBreak: 10,
    longBreakEnabled: true,
    longBreakMorning: 20,
    longBreakAfternoon: 20,
    longBreakEvening: 20,
    longBreakMorningSection: 2,
    longBreakAfternoonSection: 2,
    longBreakEveningSection: 2,
    morningStartHour: 8,
    morningStartMinute: 0,
    afternoonStartHour: 14,
    afternoonStartMinute: 0,
    eveningStartHour: 18,
    eveningStartMinute: 30,
    sectionTimes: {}
  };
  return tc;
}

function pad2(n: number): string {
  return n >= 0 && n <= 9 ? `0${n}` : `${n}`;
}

/**
 * 从起始时间累加「课时 + 课间」生成各节时间；第 longBreakSection 节之后插长休息。
 * 与 Kotlin 的 `Course.calculatePeriodTimes` 逐字对应（含"最后一节不加课间"）。
 */
export function calculatePeriodTimes(
  sectionCount: number, startHour: number, startMinute: number,
  classDuration: number, shortBreak: number,
  longBreak: number, longBreakSection: number
): string[] {
  const out: string[] = [];
  if (sectionCount <= 0) {
    return out;
  }
  let cur = startHour * 60 + startMinute;
  for (let i = 1; i <= sectionCount; i++) {
    const endMinute = cur + classDuration;
    out.push(`${pad2(Math.floor(cur / 60))}:${pad2(cur % 60)}-${pad2(Math.floor(endMinute / 60))}:${pad2(endMinute % 60)}`);
    cur = endMinute;
    if (i < sectionCount) {
      cur += (i === longBreakSection) ? longBreak : shortBreak;
    }
  }
  return out;
}

function periodTimes(tc: TimeConfig, period: string): string[] {
  let count = 0;
  let startHour = 0;
  let startMinute = 0;
  let longBreak = 0;
  let longBreakSection = 2;
  if (period === 'morning') {
    count = tc.morningSections;
    startHour = tc.morningStartHour;
    startMinute = tc.morningStartMinute;
    longBreak = tc.longBreakMorning;
    longBreakSection = tc.longBreakMorningSection;
  } else if (period === 'afternoon') {
    count = tc.afternoonSections;
    startHour = tc.afternoonStartHour;
    startMinute = tc.afternoonStartMinute;
    longBreak = tc.longBreakAfternoon;
    longBreakSection = tc.longBreakAfternoonSection;
  } else if (period === 'evening') {
    count = tc.eveningSections;
    startHour = tc.eveningStartHour;
    startMinute = tc.eveningStartMinute;
    longBreak = tc.longBreakEvening;
    longBreakSection = tc.longBreakEveningSection;
  } else {
    return [];
  }
  if (count <= 0) {
    return [];
  }
  return calculatePeriodTimes(
    count, startHour, startMinute, tc.classDuration, tc.shortBreak,
    tc.longBreakEnabled ? longBreak : tc.shortBreak, longBreakSection
  );
}

/**
 * 某时段的各节时间。quickTime 关闭时优先用 sectionTimes 覆盖表，
 * 缺项回退到按参数推算的值（与 Kotlin `getPeriodTimes` 一致）。
 */
function resolvedPeriodTimes(tc: TimeConfig, period: string): string[] {
  const computed = periodTimes(tc, period);
  if (tc.quickTimeEnabled) {
    return computed;
  }
  const out: string[] = [];
  for (let i = 0; i < computed.length; i++) {
    const key = `${period}_${i + 1}`;
    const override = tc.sectionTimes[key];
    out.push((override !== undefined && override.length > 0) ? override : computed[i]);
  }
  return out;
}

/** 全部节次（按上午→下午→晚上拼接），下标 0 对应第 1 节 */
export function allSectionSlots(tc: TimeConfig): SectionSlot[] {
  const slots: SectionSlot[] = [];
  const groups: string[] = ['morning', 'afternoon', 'evening'];
  for (let g = 0; g < groups.length; g++) {
    const list = resolvedPeriodTimes(tc, groups[g]);
    for (let i = 0; i < list.length; i++) {
      const label = list[i];
      const dash = label.indexOf('-');
      const s = dash > 0 ? label.substring(0, dash) : label;
      const e = dash > 0 ? label.substring(dash + 1) : '';
      slots.push({ index: slots.length + 1, start: s, end: e, label: label });
    }
  }
  return slots;
}

/** 总节数（上午+下午+晚上） */
export function totalSections(tc: TimeConfig): number {
  return tc.morningSections + tc.afternoonSections + tc.eveningSections;
}

/** 某一节属于哪个时段：0=上午 1=下午 2=晚上 */
export function periodOfSection(tc: TimeConfig, section: number): number {
  if (section <= tc.morningSections) {
    return 0;
  }
  if (section <= tc.morningSections + tc.afternoonSections) {
    return 1;
  }
  return 2;
}

/** 午休 / 晚休分隔线的落点（分隔线插在 return 的节次**之后**），返回空数组表示不显示 */
export function breakDividers(tc: TimeConfig): number[] {
  const out: number[] = [];
  if (tc.morningSections > 0 && (tc.afternoonSections > 0 || tc.eveningSections > 0)) {
    out.push(tc.morningSections);
  }
  if (tc.afternoonSections > 0 && tc.eveningSections > 0) {
    out.push(tc.morningSections + tc.afternoonSections);
  }
  return out;
}

/** 分隔线标签：第 1 条是午休，第 2 条是晚休 */
export function dividerLabel(ordinal: number): string {
  return ordinal === 0 ? '午休' : '晚休';
}
