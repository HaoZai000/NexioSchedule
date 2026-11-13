import {
  registerReminderHost,
  setReminderResult,
  setNotificationEnabled
} from 'libentry.so';
// 通知能力直接用模块入口（与 @kit.NotificationKit 是同一套 API）
import notificationManager from '@ohos.notificationManager';
import { common, wantAgent, WantAgent } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { refreshCourseWidgets } from '../widget/WidgetStore';

/**
 * 课程提醒 / 小组件刷新的 ArkTS 宿主。
 *
 * ## 为什么是普通通知
 * 鸿蒙能在后台定时弹通知的官方机制只有「代理提醒」(`reminderAgentManager`)，
 * 但它依赖受限权限 `ohos.permission.PUBLISH_AGENT_REMINDER`，要先去 AGC 申请、
 * 拿到带 ACL 的签名 profile 才装得上。现在先用**普通通知**：
 *
 * - Kotlin（`OhosCourseReminder`）算好未来一周的提醒时间点；
 * - 这里用 `setTimeout` 在进程内定时，到点 `notificationManager.publish`；
 * - 应用进程被系统回收后定时器消失，下次打开应用会重新对账补发。
 *
 * 等 AGC 权益批下来，只要把 `publishReminders` 换成 `reminderAgentManager.publishReminder`，
 * Kotlin 侧不用动（协议不变）。
 */

/** Kotlin 侧 `OhosCourseReminder` 发过来的一条提醒。 */
interface ReminderItem {
  id: string;
  title: string;
  content: string;
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

interface ReschedulePayload {
  items: ReminderItem[];
}

/** 包名 / Ability 名：点通知时拉起主界面。 */
const BUNDLE_NAME: string = 'com.haooz.schedule';
const ABILITY_NAME: string = 'EntryAbility';

/** 会话内的定时器句柄；重新对账时全部清掉。 */
let timers: number[] = [];
let nextNotificationId: number = 1000;
/** 点通知拉应用的 WantAgent，只建一次。 */
let cachedAgent: WantAgent | undefined = undefined;

/** Kotlin 侧 [ReminderController.hasNotificationPermission] 的同步数据源。 */
async function syncNotificationState(): Promise<boolean> {
  try {
    const enabled = await notificationManager.isNotificationEnabled();
    setNotificationEnabled(enabled);
    return enabled;
  } catch (e) {
    hilog.warn(0x0000, 'nexio', 'isNotificationEnabled failed: %{public}s',
      JSON.stringify(e as BusinessError));
    setNotificationEnabled(false);
    return false;
  }
}

export function installReminderHost(ctx: common.UIAbilityContext): void {
  registerReminderHost((requestId: number, op: string, payload: string): void => {
    onRequest(ctx, requestId, op, payload).catch((e: BusinessError) => {
      hilog.error(0x0000, 'nexio', 'reminder op %{public}s failed: %{public}s',
        op, JSON.stringify(e));
      setReminderResult(requestId, false, '');
    });
  });
  // 冷启动播种：页面第一次读 hasNotificationPermission() 时就是真实值
  syncNotificationState();
}

async function onRequest(
  ctx: common.UIAbilityContext,
  requestId: number,
  op: string,
  payload: string
): Promise<void> {
  if (op === 'reschedule') {
    await publishReminders(requestId, payload);
    return;
  }
  if (op === 'cancelAll') {
    clearTimers();
    await cancelPublished();
    setReminderResult(requestId, true, '{"status":"ok"}');
    return;
  }
  if (op === 'queryNotification') {
    const enabled = await syncNotificationState();
    setReminderResult(requestId, true, `{"enabled":${enabled}}`);
    return;
  }
  if (op === 'requestNotification') {
    await requestNotification(ctx, requestId);
    return;
  }
  if (op === 'openNotificationSettings') {
    try {
      await notificationManager.openNotificationSettings(ctx);
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'openNotificationSettings failed: %{public}s',
        JSON.stringify(e as BusinessError));
    }
    setReminderResult(requestId, true, '{}');
    return;
  }
  if (op === 'refreshWidgets') {
    await refreshCourseWidgets(ctx);
    setReminderResult(requestId, true, '{}');
    return;
  }
  setReminderResult(requestId, false, '');
}

/**
 * 重新对账：清掉上一轮的定时器与通知，再按新的时间点重排。
 * 与安卓 `cancelAll + scheduleAll` 同序，删课 / 改名不会留下孤儿提醒。
 */
async function publishReminders(requestId: number, payload: string): Promise<void> {
  let items: ReminderItem[] = [];
  try {
    const parsed = JSON.parse(payload) as ReschedulePayload;
    items = parsed.items ?? [];
  } catch (e) {
    setReminderResult(requestId, false, '{"status":"failed"}');
    return;
  }

  clearTimers();
  await cancelPublished();

  const enabled = await syncNotificationState();
  if (!enabled) {
    // 没开通知时 publish 也不会展示，直接如实回报，页面走授权引导
    setReminderResult(requestId, false, '{"status":"denied"}');
    return;
  }

  const agent: WantAgent | undefined = await getAgent();
  const now = Date.now();
  let armed = 0;
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    const fireAt = new Date(
      item.year, item.month - 1, item.day, item.hour, item.minute, 0, 0
    ).getTime();
    const delay = fireAt - now;
    // 已经过去的点直接跳过，下一次对账会补上新的
    if (delay <= 0) {
      continue;
    }
    const notificationId = nextNotificationId++;
    const handle = setTimeout((): void => {
      publishOne(notificationId, item, agent);
    }, delay);
    timers.push(handle);
    armed++;
  }
  setReminderResult(requestId, true, `{"status":"ok","count":${armed}}`);
}

async function publishOne(
  notificationId: number,
  item: ReminderItem,
  agent: WantAgent | undefined
): Promise<void> {
  const fireAt = new Date(
    item.year, item.month - 1, item.day, item.hour, item.minute, 0, 0
  ).getTime();
  // 进程被冻结后定时器会延后恢复：迟到太久的提醒就别弹了，免得「5 分钟后上课」在放学后出现
  if (Date.now() - fireAt > 10 * 60 * 1000) {
    return;
  }
  try {
    const request: notificationManager.NotificationRequest = {
      id: notificationId,
      content: {
        notificationContentType: notificationManager.ContentType.NOTIFICATION_CONTENT_BASIC_TEXT,
        normal: {
          title: item.title,
          text: item.content
        }
      }
      // 不指定 slotType：用系统默认通道，发布最稳；要更强的提醒强度再单独建通道
    };
    if (agent !== undefined) {
      request.wantAgent = agent;
    }
    await notificationManager.publish(request);
  } catch (e) {
    hilog.error(0x0000, 'nexio', 'publish notification %{public}s failed: %{public}s',
      item.id, JSON.stringify(e as BusinessError));
  }
}

function clearTimers(): void {
  for (let i = 0; i < timers.length; i++) {
    clearTimeout(timers[i]);
  }
  timers = [];
}

async function cancelPublished(): Promise<void> {
  try {
    await notificationManager.cancelAll();
  } catch (e) {
    hilog.warn(0x0000, 'nexio', 'cancelAll notifications failed: %{public}s',
      JSON.stringify(e as BusinessError));
  }
}

async function getAgent(): Promise<WantAgent | undefined> {
  if (cachedAgent !== undefined) {
    return cachedAgent;
  }
  try {
    const info: wantAgent.WantAgentInfo = {
      wants: [
        {
          bundleName: BUNDLE_NAME,
          abilityName: ABILITY_NAME
        }
      ],
      actionType: wantAgent.OperationType.START_ABILITY,
      requestCode: 0,
      wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
    };
    cachedAgent = await wantAgent.getWantAgent(info);
    return cachedAgent;
  } catch (e) {
    hilog.warn(0x0000, 'nexio', 'getWantAgent failed: %{public}s',
      JSON.stringify(e as BusinessError));
    return undefined;
  }
}

async function requestNotification(
  ctx: common.UIAbilityContext,
  requestId: number
): Promise<void> {
  try {
    await notificationManager.requestEnableNotification(ctx);
  } catch (e) {
    // 用户拒绝会走这里（1600004），不算异常路径
    hilog.warn(0x0000, 'nexio', 'requestEnableNotification denied: %{public}s',
      JSON.stringify(e as BusinessError));
  }
  const enabled = await syncNotificationState();
  setReminderResult(requestId, true, `{"enabled":${enabled}}`);
}
