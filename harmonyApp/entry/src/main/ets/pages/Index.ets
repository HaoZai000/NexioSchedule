/**
 * 鸿蒙端宿主页。
 *
 * ⚠ 这里**不写业务 UI**。鸿蒙端的界面由 `ohos/` 里的 Compose 模块编译成
 * `libnexioohos.so`，再由 `@cpf-kmp-cmp/compose`（HAR）提供的 Compose / ArkUIViewController
 * 组件挂到本页上。ArkTS 侧只做宿主该做的事：窗口、安全区、系统栏。
 *
 * 现在 HAR 还没接，所以这页是「待接入」状态 —— 刻意不在 ArkTS 里画一份临时课表：
 * 那就有了第二套 UI 真相，等 Compose 视图接进来还得整页删掉。
 *
 * 接入步骤见 docs/HARMONY_PORT_PLAN.md 的「宿主接法」。
 */

import { display } from '@kit.ArkUI';

@Entry
@Component
struct Index {
  @StorageProp('darkMode') darkMode: boolean = false;
  @StorageProp('topInsetVp') topInsetVp: number = 0;
  @StorageProp('bottomInsetVp') bottomInsetVp: number = 0;

  build() {
    Column() {
      Blank().height(this.topInsetVp)

      Column() {
        Text('Nexio 课程表')
          .fontSize(22)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.darkMode ? '#F2F2F7' : '#1C1C1E')

        Text('界面由 Compose 渲染，宿主正在接线')
          .fontSize(12)
          .margin({ top: 8 })
          .fontColor(this.darkMode ? '#8A8A90' : '#8E8E93')

        // 能读到就说明 Ability、资源与窗口都正常 —— 排障时先看这一行。
        Text(`${display.getDefaultDisplaySync().width}×${display.getDefaultDisplaySync().height}`)
          .fontSize(11)
          .margin({ top: 18 })
          .fontColor(this.darkMode ? '#5E5E64' : '#B4B4BA')
      }
      .width('100%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.darkMode ? '#0E0E10' : '#F2F2F7')
    .padding({ bottom: this.bottomInsetVp })
  }
}
