/**
 * 教学周日历 —— 周次口径与 Android 端一致：**周一开始**，第 1 周 = 上课起始日所在周。
 *
 * 日期一律用本地时间零点参与运算，避免跨时区/夏令时把「今天」算成昨天。
 */

export const WEEKDAY_LABELS: string[] = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

export function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

export function addDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

/** 本周一（周一为一周之始，周日属于当周第 7 天） */
export function mondayOf(d: Date): Date {
  const base = startOfDay(d);
  const dow = base.getDay(); // 0=周日
  const back = dow === 0 ? 6 : dow - 1;
  return addDays(base, -back);
}

/** 两个日期之间的整天数（b - a） */
export function daysBetween(a: Date, b: Date): number {
  const ms = startOfDay(b).getTime() - startOfDay(a).getTime();
  return Math.round(ms / 86400000);
}

/** 1=周一 … 7=周日 */
export function mondayFirstDow(d: Date): number {
  const dow = startOfDay(d).getDay();
  return dow === 0 ? 7 : dow;
}

/** "2026-02-23" → Date（本地零点）；非法输入回退今天 */
export function parseYmd(s: string): Date {
  const parts = s.split('-');
  if (parts.length !== 3) {
    return startOfDay(new Date());
  }
  const y = parseInt(parts[0], 10);
  const m = parseInt(parts[1], 10);
  const d = parseInt(parts[2], 10);
  if (Number.isNaN(y) || Number.isNaN(m) || Number.isNaN(d)) {
    return startOfDay(new Date());
  }
  return new Date(y, m - 1, d);
}

export function toYmd(d: Date): string {
  const m = d.getMonth() + 1;
  const day = d.getDate();
  return `${d.getFullYear()}-${m < 10 ? '0' + m : m}-${day < 10 ? '0' + day : day}`;
}

/** "10/26" */
export function formatMonthDay(d: Date): string {
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

/**
 * 某日期落在第几周（1 起）。周 = 周一起算的 7 天块，
 * 所以「开学那一周」恒为第 1 周，与用户直觉一致。
 */
export function weekIndexFor(classStart: Date, date: Date): number {
  const diff = daysBetween(mondayOf(classStart), mondayOf(date));
  return Math.floor(diff / 7) + 1;
}

/** 第 week 周的周一日期 */
export function mondayOfWeek(classStart: Date, week: number): Date {
  return addDays(mondayOf(classStart), (week - 1) * 7);
}

export function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate();
}

/** 把周次夹进 [1, totalWeeks] */
export function clampWeek(week: number, totalWeeks: number): number {
  if (week < 1) {
    return 1;
  }
  return week > totalWeeks ? totalWeeks : week;
}
