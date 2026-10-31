/**
 * 课程模型 —— 字段与 `core/data/model/Course.kt` 逐项对齐。
 *
 * ⚠ 命名字段是**落盘契约**：备份 JSON / 分享码 / Android 端 relStore 列都认这些名字，
 * 改一个等于鸿蒙端读不了别人的备份。ArkTS 侧用小驼峰，与 Kotlin 的 data class 一致。
 */

import { COURSE_COLORS } from '../ui/theme/Tokens';

export interface Course {
  id: string;
  name: string;
  classroom: string;
  teacher: string;
  /** 1=周一 … 7=周日 */
  dayOfWeek: number;
  /** 全局绝对节次号，从 1 开始（上午 1-4、下午 5-8、晚上 9-12） */
  startSection: number;
  endSection: number;
  startWeek: number;
  endWeek: number;
  /** 0=全周 1=单周 2=双周 */
  weekType: number;
  /** 课程色板索引；-1 表示未指定，渲染时按 id 散列兜底 */
  colorIndex: number;
  /** 非空时优先于 startWeek/endWeek/weekType 判定周次 */
  selectedWeeks: number[];
  /** 空串 = 未归属任何课表 */
  scheduleId: string;
  lastModified: number;
  isCustomTime: boolean;
  /** "HH:mm"，仅 isCustomTime 时有意义 */
  customStartTime: string;
  customEndTime: string;
  /** 对所有周生效的备注 */
  note: string;
  /** 按周覆盖 note：key 是周次字符串，value 是备注 */
  weekNotes: Record<string, string>;
}

export const NOTE_MAX_LENGTH: number = 50;

export const WEEK_TYPE_ALL: number = 0;
export const WEEK_TYPE_ODD: number = 1;
export const WEEK_TYPE_EVEN: number = 2;

/** 该课是否在指定周上课。selectedWeeks 非空时**完全**以它为准。 */
export function isActiveInWeek(course: Course, week: number): boolean {
  if (course.selectedWeeks.length > 0) {
    return course.selectedWeeks.indexOf(week) >= 0;
  }
  if (week < course.startWeek || week > course.endWeek) {
    return false;
  }
  if (course.weekType === WEEK_TYPE_ODD) {
    return week % 2 === 1;
  }
  if (course.weekType === WEEK_TYPE_EVEN) {
    return week % 2 === 0;
  }
  return true;
}

/** 该课在 week **之后**最近一次会上的是哪一周；已全部上完返回 0 */
export function nextActiveWeekAfter(course: Course, week: number): number {
  if (course.selectedWeeks.length > 0) {
    let best = 0;
    for (let i = 0; i < course.selectedWeeks.length; i++) {
      const w = course.selectedWeeks[i];
      if (w > week && (best === 0 || w < best)) {
        best = w;
      }
    }
    return best;
  }
  if (course.endWeek < week) {
    return 0;
  }
  const from = Math.max(week + 1, course.startWeek);
  for (let w = from; w <= course.endWeek; w++) {
    if (isActiveInWeek(course, w)) {
      return w;
    }
  }
  return 0;
}

/** 该课在 week **及之前**最后一次会上的是哪一周；还没开始返回 0 */
export function lastActiveWeekAtOrBefore(course: Course, week: number): number {
  if (course.selectedWeeks.length > 0) {
    let best = 0;
    for (let i = 0; i < course.selectedWeeks.length; i++) {
      const w = course.selectedWeeks[i];
      if (w <= week && w > best) {
        best = w;
      }
    }
    return best;
  }
  if (course.startWeek > week) {
    return 0;
  }
  const to = Math.min(week, course.endWeek);
  for (let w = to; w >= course.startWeek; w--) {
    if (isActiveInWeek(course, w)) {
      return w;
    }
  }
  return 0;
}

/**
 * 一组同槽位、本周都没课的课里挑一条显示：取「下一次会上」最近的那门；
 * 全都上完了取最后结束的那门。判据必须用 nextActiveWeekAfter 而不是 startWeek ——
 * 后者会把「第 1 周开始、第 10 周还有一次」的课判出局。
 */
export function pickNonCurrentWeekCourse(group: Course[], week: number): Course | null {
  if (group.length === 0) {
    return null;
  }
  if (group.length === 1) {
    return group[0];
  }
  let upcoming: Course | null = null;
  let upcomingWeek = 0;
  for (let i = 0; i < group.length; i++) {
    const w = nextActiveWeekAfter(group[i], week);
    if (w > 0 && (upcoming === null || w < upcomingWeek
      || (w === upcomingWeek && group[i].startSection < upcoming.startSection))) {
      upcoming = group[i];
      upcomingWeek = w;
    }
  }
  if (upcoming !== null) {
    return upcoming;
  }
  let past: Course | null = null;
  let pastWeek = 0;
  for (let i = 0; i < group.length; i++) {
    const w = lastActiveWeekAtOrBefore(group[i], week);
    if (w > 0 && (past === null || w > pastWeek
      || (w === pastWeek && group[i].endWeek > past.endWeek))) {
      past = group[i];
      pastWeek = w;
    }
  }
  return past !== null ? past : group[0];
}

/** 该周实际生效的备注：单周备注优先，空串则回退到全周备注 */
export function noteForWeek(course: Course, week: number): string {
  const w = course.weekNotes[`${week}`];
  if (w !== undefined && w.trim().length > 0) {
    return w;
  }
  return course.note;
}

/**
 * 备注截断到 NOTE_MAX_LENGTH，**不把代理对切一半**。
 * 直接 take(50) 在末尾是 emoji 时会留下半个代理对 → 渲染成豆腐块。
 */
export function truncateNote(s: string): string {
  if (s.length <= NOTE_MAX_LENGTH) {
    return s;
  }
  const c = s.charCodeAt(NOTE_MAX_LENGTH - 1);
  const cut = (c >= 0xD800 && c <= 0xDBFF) ? NOTE_MAX_LENGTH - 1 : NOTE_MAX_LENGTH;
  return s.substring(0, cut);
}

/** 未指定 colorIndex 时的稳定兜底：按 id 散列，同一门课每次进来颜色不变 */
export function colorIndexOf(course: Course): number {
  if (course.colorIndex >= 0 && course.colorIndex < COURSE_COLORS.length) {
    return course.colorIndex;
  }
  let h = 0;
  for (let i = 0; i < course.id.length; i++) {
    h = (h * 31 + course.id.charCodeAt(i)) % 100000;
  }
  return h % COURSE_COLORS.length;
}

/** 卡片上显示的课名：空名统一显示「特殊课程」（与 Android 端同一口径） */
export function displayName(course: Course): string {
  return course.name.trim().length > 0 ? course.name : '特殊课程';
}

/** 节次文本：第3-4节 */
export function sectionText(course: Course): string {
  if (course.startSection <= 0 && course.endSection <= 0) {
    return '';
  }
  return course.startSection === course.endSection
    ? `第${course.startSection}节`
    : `第${course.startSection}-${course.endSection}节`;
}

/**
 * 周次文本：先按步长 2（单双周）再按步长 1 合并，同类型序列 ≥3 才合并。
 * 例：[1,3,5,7,9,11,13,15,16,17] → "1-15 (单周)、16周、17周"
 * 规则与 Kotlin `Course.formatWeeks` 一致 —— 两端口径不同会让用户以为周次变了。
 */
export function weekText(course: Course): string {
  if (course.selectedWeeks.length > 0) {
    return formatWeeks(course.selectedWeeks.slice().sort((a: number, b: number) => a - b));
  }
  if (course.startWeek <= 0 && course.endWeek <= 0) {
    return '';
  }
  let type = '';
  if (course.weekType === WEEK_TYPE_ODD) {
    type = '单周';
  } else if (course.weekType === WEEK_TYPE_EVEN) {
    type = '双周';
  }
  const weeks = course.startWeek === course.endWeek
    ? `${course.startWeek}周`
    : `${course.startWeek}-${course.endWeek}周`;
  return type.length > 0 ? `${weeks} (${type})` : weeks;
}

function extractRun(sorted: number[], start: number, step: number): number[] {
  const run: number[] = [sorted[start]];
  let j = start + 1;
  while (j < sorted.length && sorted[j] - sorted[j - 1] === step) {
    run.push(sorted[j]);
    j++;
  }
  return run;
}

function formatWeeks(sorted: number[]): string {
  const groups: string[] = [];
  let i = 0;
  while (i < sorted.length) {
    const step2 = extractRun(sorted, i, 2);
    if (step2.length >= 3) {
      const parity = step2[0] % 2 === 1 ? '单周' : '双周';
      groups.push(`${step2[0]}-${step2[step2.length - 1]} (${parity})`);
      i += step2.length;
      continue;
    }
    const step1 = extractRun(sorted, i, 1);
    if (step1.length >= 3) {
      groups.push(`${step1[0]}-${step1[step1.length - 1]}周`);
      i += step1.length;
      continue;
    }
    groups.push(`${sorted[i]}周`);
    i++;
  }
  return groups.join('、');
}


/** 时间显示：自定义时间优先，跨午夜标「次日」 */
export function timeDisplayText(course: Course): string {
  if (course.isCustomTime && course.customStartTime.length > 0 && course.customEndTime.length > 0) {
    const s = hmToMinutes(course.customStartTime);
    const e = hmToMinutes(course.customEndTime);
    const nextDay = s >= 0 && e >= 0 && e <= s;
    return `${course.customStartTime} - ${nextDay ? '次日' : ''}${course.customEndTime}`;
  }
  return sectionText(course);
}

function hmToMinutes(hm: string): number {
  const parts = hm.split(':');
  if (parts.length !== 2) {
    return -1;
  }
  const h = parseInt(parts[0], 10);
  const m = parseInt(parts[1], 10);
  if (Number.isNaN(h) || Number.isNaN(m)) {
    return -1;
  }
  return h * 60 + m;
}
