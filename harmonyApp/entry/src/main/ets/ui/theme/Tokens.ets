/**
 * 设计令牌 —— 与 Android 端 `AppearanceConfig` / `CombinationDefaults` 同源，单位统一 vp。
 *
 * 这里只放「跨页面共享、且改了要一起改」的量。逐课表的可调项（卡片高度、圆角、模糊、
 * 折射、壁纸亮度…）后续由外观页写进 schedule 行，不要在这一层开分支。
 */

/** 课程色板：与 core/data/model/Course.kt 的 courseColors 逐项一致（顺序也不能变，落盘靠索引） */
export const COURSE_COLORS: string[] = [
  '#4CAF50', // 绿
  '#2196F3', // 蓝
  '#FF9800', // 橙
  '#F44336', // 红
  '#E6B422', // 金
  '#E91E63', // 粉
  '#00BCD4', // 青
  '#3F51B5', // 靛
  '#AB47BC', // 紫
  '#009688', // 蓝绿
  '#673AB7'  // 深紫
];

/** 网格骨架尺寸 */
export class GridMetrics {
  /** 左侧节次列宽（节次号 + 起止时间，两行小字） */
  static readonly SECTION_COL_W: number = 34;
  /** 星期头高 */
  static readonly DAY_HEADER_H: number = 50;
  /** 单节高度（45 分钟课 + 课间） */
  static readonly SECTION_H: number = 62;
}

export class Theme {
  /** 页面底色（无壁纸时） */
  bg: string = '#F2F2F7';
  textPrimary: string = '#1C1C1E';
  textSecondary: string = '#8A8A8E';
  textTertiary: string = '#B0B0B5';
  divider: string = 'rgba(0,0,0,0.07)';
  /** 今日列高亮底色 */
  todayWash: string = 'rgba(0,0,0,0.025)';

  static light(): Theme {
    return new Theme();
  }

  static dark(): Theme {
    const t = new Theme();
    t.bg = '#0E0E10';
    t.textPrimary = '#F2F2F7';
    t.textSecondary = '#9A9AA0';
    t.textTertiary = '#6A6A70';
    t.divider = 'rgba(255,255,255,0.08)';
    t.todayWash = 'rgba(255,255,255,0.035)';
    return t;
  }

  static of(dark: boolean): Theme {
    return dark ? Theme.dark() : Theme.light();
  }
}

// ── 颜色运算 ────────────────────────────────────────────────────────────────
// 卡片底色/文字色都由课程色派生，不另存一份表：加课换色不会出现「色板改了卡片没改」。

function clamp255(v: number): number {
  const n = Math.round(v);
  return n < 0 ? 0 : (n > 255 ? 255 : n);
}

/** #RRGGBB → [r,g,b]；非法输入回退灰色，宁可难看也不抛 */
export function hexToRgb(hex: string): number[] {
  let s = hex.trim();
  if (s.startsWith('#')) {
    s = s.substring(1);
  }
  if (s.length === 3) {
    s = s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
  }
  if (s.length !== 6) {
    return [128, 128, 128];
  }
  const v = parseInt(s, 16);
  if (Number.isNaN(v)) {
    return [128, 128, 128];
  }
  return [(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF];
}

function toHex2(n: number): string {
  const h = clamp255(n).toString(16);
  return h.length === 1 ? '0' + h : h;
}

/** 线性混合：t=0 取 a，t=1 取 b */
export function mixColor(a: string, b: string, t: number): string {
  const ca = hexToRgb(a);
  const cb = hexToRgb(b);
  const k = t < 0 ? 0 : (t > 1 ? 1 : t);
  return '#' + toHex2(ca[0] + (cb[0] - ca[0]) * k)
    + toHex2(ca[1] + (cb[1] - ca[1]) * k)
    + toHex2(ca[2] + (cb[2] - ca[2]) * k);
}

/** 带透明度的颜色串（ArkUI 认 rgba()，比 #AARRGGBB 更省心） */
export function alphaColor(hex: string, alpha: number): string {
  const c = hexToRgb(hex);
  const k = alpha < 0 ? 0 : (alpha > 1 ? 1 : alpha);
  return `rgba(${c[0]},${c[1]},${c[2]},${k.toFixed(3)})`;
}

/**
 * 卡片底色：课程色与底色按比例混合。
 * 亮色时大量混白（粉彩），暗色时混黑（暗调），与截图里的观感一致。
 */
export function cardFill(courseColor: string, dark: boolean): string {
  return dark
    ? mixColor(courseColor, '#000000', 0.78)
    : mixColor(courseColor, '#FFFFFF', 0.82);
}

/** 卡片文字色：同色系加深（亮）/ 提亮（暗），保证对比度 */
export function cardInk(courseColor: string, dark: boolean): string {
  return dark
    ? mixColor(courseColor, '#FFFFFF', 0.45)
    : mixColor(courseColor, '#000000', 0.30);
}
