/**
 * 卡片尺寸常量。单独成文件是为了让「外观页」和「网格」引用同一份 —— 目前是常量，
 * 等外观页落地后这里换成从 schedule 行读出来的值，调用点不用改。
 */
export class CardMetrics {
  /** 卡片圆角，对应 CombinationDefaults.CARD_CORNER_RADIUS */
  static readonly RADIUS: number = 10;
  /** 卡片与格子边缘的缝（左右各留一份） */
  static readonly GAP: number = 2.5;
}
