/**
 * 鸿蒙端宿主页。
 *
 * 这一页只做一件事：把 Compose 的 controller 挂到 ArkUI 节点上。
 * 界面本身全在 Kotlin 侧（`ohos/ohosApp` → `libnexioohos.so`），这里不写任何业务 UI ——
 * 在 ArkTS 里再画一份就是两套真相。
 *
 * 链路：MainArkUIViewController()（NAPI，来自 libentry.so → libnexioohos.so）
 *   → Compose 组件（HAR）→ XComponent → Compose 渲染。
 */

import { Compose, ArkUIViewController } from '@cpf-kmp-cmp/compose';
import { MainArkUIViewController } from 'libentry.so';

@Entry
@Component
struct Index {
  private controller: ArkUIViewController | undefined = undefined;

  aboutToAppear(): void {
    // 拿到 Kotlin 侧的 controller 句柄；UI 树此时才真正建立
    this.controller = MainArkUIViewController();
  }

  build() {
    Stack() {
      if (this.controller !== undefined) {
        Compose({
          controller: this.controller,
          libraryName: 'entry',
          onBackPressed: () => false
        })
          .width('100%')
          .height('100%')
      }
    }
    .width('100%')
    .height('100%')
  }
}
