import { common } from '@kit.AbilityKit';
import { fileIo } from '@kit.CoreFileKit';
import { preferences } from '@kit.ArkData';
import { formBindingData, formProvider } from '@kit.FormKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

/**
 * 桌面卡片的数据与 formId 存取。
 *
 * 卡片 UI 跑在桌面进程里，不能碰 Kotlin / 数据库；Kotlin 把算好的当天课程写成
 * 沙箱里的 `widget_snapshot.json`，这里在两处读它：
 * - `CourseWidgetAbility`（卡片提供方，负责加卡/定时刷新）；
 * - 入口 Ability（数据变更后立即刷新已添加的卡片）。
 */

export interface WidgetCourse {
  name: string;
  room: string;
  /** 绝对时间戳（毫秒）。卡片自己按 Date.now() 过滤，跨零点不会继续显示昨天的课。 */
  start: number;
  end: number;
  /** `#AARRGGBB`，与安卓小部件的课程色条同源 */
  color: string;
}

export interface WidgetDay {
  week: number;
  weekday: string;
  /** normal / holiday / notStarted */
  status: string;
  month: number;
  day: number;
  courses: WidgetCourse[];
}

export interface WidgetData {
  generatedAt: number;
  week: number;
  weekday: string;
  /** normal / holiday / notStarted */
  status: string;
  month: number;
  day: number;
  courses: WidgetCourse[];
  /** 「今日与明日」卡片用；旧快照没有这个字段 */
  tomorrow?: WidgetDay;
}

const SNAPSHOT_FILE: string = 'widget_snapshot.json';
const PREF_NAME: string = 'nexio_widget';
const KEY_FORM_IDS: string = 'formIds';

/** 读 Kotlin 写的快照；未生成或损坏时返回 undefined（卡片显示占位）。 */
export function readWidgetData(ctx: common.Context): WidgetData | undefined {
  try {
    const path = `${ctx.filesDir}/${SNAPSHOT_FILE}`;
    if (!fileIo.accessSync(path)) {
      return undefined;
    }
    const text = fileIo.readTextSync(path);
    const parsed = JSON.parse(text) as WidgetData;
    if (parsed === undefined || parsed === null) {
      return undefined;
    }
    if (!Array.isArray(parsed.courses)) {
      parsed.courses = [];
    }
    return parsed;
  } catch (e) {
    hilog.warn(0x0000, 'nexio', 'read widget snapshot failed: %{public}s',
      JSON.stringify(e as BusinessError));
    return undefined;
  }
}

export function buildBinding(data: WidgetData | undefined): formBindingData.FormBindingData {
  const payload: WidgetData = data ?? {
    generatedAt: 0,
    week: 0,
    weekday: '',
    status: 'empty',
    month: 0,
    day: 0,
    courses: []
  };
  return formBindingData.createFormBindingData(payload);
}

// ── formId 记录：卡片被加/删时由 CourseWidgetAbility 维护 ─────────────────────

export async function loadFormIds(ctx: common.Context): Promise<string[]> {
  try {
    const store = await preferences.getPreferences(ctx, PREF_NAME);
    const raw = await store.get(KEY_FORM_IDS, '[]') as string;
    const ids = JSON.parse(raw) as string[];
    return Array.isArray(ids) ? ids : [];
  } catch (e) {
    return [];
  }
}

async function saveFormIds(ctx: common.Context, ids: string[]): Promise<void> {
  try {
    const store = await preferences.getPreferences(ctx, PREF_NAME);
    await store.put(KEY_FORM_IDS, JSON.stringify(ids));
    await store.flush();
  } catch (e) {
    hilog.warn(0x0000, 'nexio', 'save form ids failed: %{public}s',
      JSON.stringify(e as BusinessError));
  }
}

export async function addFormId(ctx: common.Context, formId: string): Promise<void> {
  if (formId === '') {
    return;
  }
  const ids = await loadFormIds(ctx);
  if (ids.indexOf(formId) >= 0) {
    return;
  }
  ids.push(formId);
  await saveFormIds(ctx, ids);
}

export async function removeFormId(ctx: common.Context, formId: string): Promise<void> {
  const ids = await loadFormIds(ctx);
  const next = ids.filter((id: string) => id !== formId);
  if (next.length !== ids.length) {
    await saveFormIds(ctx, next);
  }
}

/**
 * 刷新所有已添加的卡片（数据变更时由 Kotlin 触发）。
 * 没有卡片时什么也不做 —— 不能因为没加卡就弹提示。
 */
export async function refreshCourseWidgets(ctx: common.Context): Promise<void> {
  const ids = await loadFormIds(ctx);
  if (ids.length === 0) {
    return;
  }
  const binding = buildBinding(readWidgetData(ctx));
  for (let i = 0; i < ids.length; i++) {
    try {
      await formProvider.updateForm(ids[i], binding);
    } catch (e) {
      // 卡片可能已被删除但没收到 onRemoveForm，顺手清掉
      hilog.warn(0x0000, 'nexio', 'updateForm %{public}s failed: %{public}s',
        ids[i], JSON.stringify(e as BusinessError));
    }
  }
}
