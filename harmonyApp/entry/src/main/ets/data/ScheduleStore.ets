/**
 * 本地存储：第一版走 preferences 存整份 JSON。
 *
 * 为什么不直接上 relationalStore：课表是一份「整份读、改一门课再整份写」的聚合，
 * 首屏又要一次性拿到全部课程，拆表在第一版只增加迁移风险。等备份导入 / 增删课
 * 真正落地时再换成 relStore（表结构与 `core/.../Schedule.sq` 对齐，pref 表同样保留），
 * 那时 `ScheduleDoc` 就是迁移的输入源 —— 所以这里的字段名必须与备份 JSON 一致。
 */

import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { Course } from '../model/Course';
import { defaultTimeConfig, TimeConfig } from '../model/TimeConfig';
import { DemoData } from './DemoData';

export interface ScheduleDoc {
  /** 数据格式版本，与 Android 端 prefs 的版本号解耦，仅用于本地迁移判断 */
  version: number;
  name: string;
  currentWeek: number;
  totalWeeks: number;
  /** "YYYY-MM-DD"，第 1 周的周一 */
  classStart: string;
  courses: Course[];
  timeConfig: TimeConfig;
}

const PREF_FILE: string = 'nexio_schedule';
const KEY_DOC: string = 'schedule_doc_v1';

export class ScheduleStore {
  private static pref: preferences.Preferences | null = null;

  static async open(context: common.UIAbilityContext): Promise<void> {
    if (ScheduleStore.pref !== null) {
      return;
    }
    ScheduleStore.pref = await preferences.getPreferences(context, { name: PREF_FILE });
  }

  /** 读整份课表；首次启动播种示例数据并落盘 */
  static async load(): Promise<ScheduleDoc> {
    const pref = ScheduleStore.pref;
    if (pref !== null) {
      const raw = await pref.get(KEY_DOC, '') as string;
      if (raw.length > 0) {
        const parsed = parseDoc(raw);
        if (parsed !== null) {
          return parsed;
        }
      }
    }
    const seeded = seedDoc();
    await ScheduleStore.save(seeded);
    return seeded;
  }

  static async save(doc: ScheduleDoc): Promise<void> {
    const pref = ScheduleStore.pref;
    if (pref === null) {
      return;
    }
    await pref.put(KEY_DOC, JSON.stringify(doc));
    await pref.flush();
  }

  static async clear(): Promise<void> {
    const pref = ScheduleStore.pref;
    if (pref === null) {
      return;
    }
    await pref.delete(KEY_DOC);
    await pref.flush();
  }
}

function seedDoc(): ScheduleDoc {
  const doc: ScheduleDoc = {
    version: 1,
    name: '我的课表',
    currentWeek: DemoData.currentWeek(),
    totalWeeks: 20,
    classStart: DemoData.classStartYmd(),
    courses: DemoData.courses(),
    timeConfig: defaultTimeConfig()
  };
  return doc;
}

/**
 * 反序列化 + 兜底。损坏的 JSON 直接当「没有数据」处理（回示例课表），
 * 不要抛：打开就崩比丢一份示例数据糟得多。
 */
function parseDoc(raw: string): ScheduleDoc | null {
  let obj: ScheduleDoc | null = null;
  try {
    obj = JSON.parse(raw) as ScheduleDoc;
  } catch (e) {
    return null;
  }
  if (obj === null || obj === undefined) {
    return null;
  }
  const doc: ScheduleDoc = {
    version: typeof obj.version === 'number' ? obj.version : 1,
    name: typeof obj.name === 'string' ? obj.name : '我的课表',
    currentWeek: typeof obj.currentWeek === 'number' ? obj.currentWeek : 1,
    totalWeeks: typeof obj.totalWeeks === 'number' ? obj.totalWeeks : 20,
    classStart: typeof obj.classStart === 'string' ? obj.classStart : DemoData.classStartYmd(),
    courses: Array.isArray(obj.courses) ? obj.courses : [],
    timeConfig: obj.timeConfig !== undefined && obj.timeConfig !== null
      ? obj.timeConfig : defaultTimeConfig()
  };
  return doc;
}
