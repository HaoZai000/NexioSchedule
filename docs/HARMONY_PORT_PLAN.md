# 鸿蒙端（HarmonyOS）移植方案

> 2026-10-31 起。目标 HarmonyOS 6 / API 26（DevEco Studio 26.0.0）。
> 结论先说：**UI 走 Compose Multiplatform，ArkTS 只做宿主。**

---

## 一、方案：KMP 共享逻辑 + CMP 渲染 UI

用 CPF-KMP-CMP（社区 SIG 把 KMP/CMP 适配到鸿蒙的那套东西）把 Kotlin 直接编成鸿蒙 `.so`，
Compose 负责画界面，ArkTS 侧只留一个空壳页面挂载。技术栈：

| 层 | 用什么 | 说明 |
|---|---|---|
| 语言 / 编译器 | Kotlin/Native 鸿蒙定制版 | 主线 Kotlin 没有 `ohosArm64()`，必须用定制版 |
| UI | Compose Multiplatform（鸿蒙适配制品） | 声明式 UI，与 Android/iOS 同一套写法 |
| 渲染 | OHRender 统一渲染 / Skia 自渲染 | 统一渲染要求 API 19+，低版本退化到自渲染 |
| 宿主 | ArkTS + `@cpf-kmp-cmp/compose` HAR | HAR 提供 `Compose` / `ArkUIViewController` 组件与 `libskikobridge.so` |
| 互操作 | akinterop（Kotlin ↔ ArkTS） | 通知、卡片、文件选择器这类系统能力仍走 ArkTS，靠它桥 |

**否掉的方案**（都推演过，别回头再试）：
- KMP 官方 target —— 主线 Kotlin 没有 OHOS target，`ohosArm64()` 是定制版独有的；
- ArkTS 原生重写 —— 等于给鸿蒙单独养一套 UI，与「跨端」目标相反（10-31 先写过一版 ArkTS 周网格，已按这个结论删掉）；
- WebView 套壳 —— 应用市场审核与性能都过不去。

---

## 二、为什么是**两个构建**，而不是给主构建加 target

Compose 的 UI 制品带平台渲染实现，**一个 Gradle 构建只解析一套 Compose 版本**。而两条线对不上：

| | 主构建（Android / iOS / 桌面） | 鸿蒙适配线 |
|---|---|---|
| Kotlin | 2.4.10 | 2.4.20-0.1.0-12 或 2.2.21-1.2.0-04 |
| Compose | 1.12.0（JetBrains 官方） | 1.12.0-0.1.0-15 或 1.9.2-1.2.0-08（第三方适配） |
| Gradle | 9.5.1 | 9.5.1（2.4.20 线）/ 8.14.1（2.2.21 线） |
| miuix / backdrop | 0.9.4 与本地 fork，按 CMP 1.12 编译 | 无 ohos 变体 |

硬把 `ohosArm64()` 塞进主构建，只有两种结局：要么把 Android/iOS 一起降到 1.9.2
（miuix 0.9.4、Material3 1.5 全得跟着降，等于三端一起做次大回退），
要么鸿蒙端解析不到 klib。所以拆成两个构建：

```
/                     主构建（原样不动）
ohos/                 鸿蒙构建（Kotlin + CMP 鸿蒙适配版）
  ohosApp/            Compose UI → libnexioohos.so
harmonyApp/           ArkTS 宿主（DevEco 工程，签名/推包在这边）
```

代价是两套依赖图；收益是主构建零风险，鸿蒙端可独立升级/回滚。

---

## 三、今晚已验证跑通的

```bash
cd ohos
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :ohosApp:linkDebugSharedOhosArm64
# → ohosApp/build/bin/ohosArm64/debugShared/libnexioohos.so（Compose UI 已编进 .so）
```

- Kotlin/Native 自动拉到 `cpf-llvm-19` + `sysroot-ohos-26.0.0.38` + `sysroot-hms-26.0.0.38`（首次约 5 分钟）；
- `ohos_arm64` / `ohos_x64` 两个 target 都在本地 konan 里；
- Compose（runtime / foundation / material3 / ui）的 ohos 变体全部解析成功，UI 编译并链接成 arm64 `.so`。

**关于「本机怎么看见界面」**：适配版不发布 `jvm(desktop)` 制品，`ohosApp` 里加 `jvm()` 会解析失败
（已实测）。要看排版有两个办法，都不需要往仓库里加第二套 UI：

1. 临时工程：上游 CMP 1.12.0 + `ImageComposeScene` 离屏渲染，`kotlin.srcDir` 指向共享 UI 的源目录，
   `./gradlew run` 直接出 PNG（我 10-31 用这个办法验证过一次排版）；
2. 接上 HAR 后直接在模拟器看。

这一步顺带抓到一个真 bug 值得记下：卡片在 `Column` 里用 `padding(top)` 定位时，多张卡的 padding 会
**累加**，整列往下漂；必须放在同层 `Box` 里逐张定位。共享 UI 真正接进 ohos 时同样要注意。

---

## 四、宿主接法（下一步）

1. `harmonyApp` 里 `ohpm install @cpf-kmp-cmp/compose`（HAR 内含 `libskikobridge.so`、
   `libcompose_arkui_utils.so` 与 ArkTS 组件）。
2. 把 `:ohosApp:linkDebugSharedOhosArm64` 产出的 `libnexioohos.so` 放进 `entry/libs/arm64-v8a/`。
3. `entry/src/main/ets/pages/Index.ets` 用 HAR 的 `Compose` 组件替换占位页，传入 `.so` 与入口；
   `EntryAbility` 的窗口 / 安全区 / 深浅色同步保持不变。
4. `hvigorw assembleHap` 出包，DevEco 配自动签名后装真机 / 模拟器。

**版本对齐是硬约束**：HAR（`@cpf-kmp-cmp/compose`，ohpm 上目前最高 `1.9.2-1.2.0`）与 Kotlin 侧
Compose 必须同代。我们用了 1.12.0-0.1.0-15；若真机黑屏 / 白屏，第一件事是把
`ohos/ohosApp/build.gradle.kts` 的 Kotlin/Compose 降到 1.9.2-1.2.0 线
（同时 Gradle 换 8.14.1 + 装 JDK 21）。

---

## 五、怎么走到「一套 UI」

目标态只有一行：`ohosApp` 里的 `NexioRoot() = NexioApp()`，其余全部来自 `:ui-shared`。
现在的阻塞点有三层，按可解程度排：

| 阻塞 | 现状 | 解法 |
|---|---|---|
| `:ui-shared` 是 AGP 的 android library | 鸿蒙构建里没有 AGP，撑不起这个模块 | 把页面层拆成纯 KMP 模块（不带 android 目标） |
| miuix 三件套（icons / blur / squircle） | Maven 上的官方制品，只有 android/jvm/ios 变体 | fork 到本地并加 ohos target，或从页面层剥掉 |
| backdrop 的模糊 / 折射 | 本地 fork，Android 走 AGSL、skiko 走 SkSL | 换 OHRender 的模糊能力，或降级为纯色描边 |

好消息是**网格本身几乎不依赖 miuix**（实测：`CourseCard` 1 处、`SectionColumn` 1 处、
`MainScheduleScreen` 9 处、`DayColumn` 7 处，且都是 `Text` / 主题取色的薄用法），
所以可行顺序是「先搬网格、再搬页面」：

1. 新建 `:ui-core`（纯 KMP，无 android 目标），把 `classschedule/*` + `Tokens` + `CardReveal` 搬进去，
   `MiuixTheme.Text` 换成 `Compose Material3` 的 `Text`，主题取色改成自有令牌 —— 这几处改完，
   网格就是三端通用代码，Android 侧只是换个 import；
2. `:ui-core` 声明 `ohosArm64()`，`ohosApp` 直接依赖它，`NexioRoot()` 换成真正的网格；
3. 页面外壳（顶栏、底栏、设置页）依赖 miuix，作为第二批：fork miuix 加 ohos target，
   或把这些页也剥成纯 Compose；
4. 全绿之后 `ohosApp` 里只剩一行入口，`NexioRoot() = NexioApp()`。

**不走的路**：在鸿蒙侧另写一套页面。10-31 我按这个思路写过一版 ArkTS 网格、又写过一版 Compose 网格，
两版都已删除 —— 两套 UI 就是两份真相，改一版要改两处，且永远对不齐。

---

## 六、系统能力与后续顺序---

## 七、本机环境（换机照做）

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # JDK 25，两个构建都用
export NODE_HOME=/Applications/DevEco-Studio.app/Contents/tools/node
export DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk
export PATH="$NODE_HOME/bin:$PATH"
```

### 踩过的坑

1. **只有 JDK 25 时不能走 2.2.21 线**：那条线要 Gradle 8.14.1，而 Gradle 8.x 起不来 JDK 25
   （报错只有一句 `25.0.3`）。要么装 JDK 21，要么用 2.4.20 线（我们选的，Gradle 9.5.1 可用）。
2. **适配版没有 `jvm(desktop)` 制品**：`ohosApp` 里别加 `jvm()`，否则解析 `ui-desktop` 直接失败。
3. **hvigor 会先 `npm install pnpm`，本机 npm 走不通代理**（ECONNRESET）。手工解到缓存目录即可
   —— hvigor 只认 `~/.hvigor/wrapper/tools/<版本>/node_modules/.bin/pnpm` 这个文件存在：
   ```bash
   V=10.28.2; D=~/.hvigor/wrapper/tools/$V
   curl -sSL -o /tmp/pnpm.tgz https://registry.npmmirror.com/pnpm/-/pnpm-$V.tgz
   mkdir -p "$D/node_modules/pnpm" "$D/node_modules/.bin"
   tar -xzf /tmp/pnpm.tgz -C "$D/node_modules/pnpm" --strip-components=1
   ln -sf ../pnpm/bin/pnpm.cjs "$D/node_modules/.bin/pnpm"
   ```
4. **`compatibleSdkVersion` 不能写 `"26.0.0(26)"`**，只认 `<major>[.<minor>][.<patch>]`，写 `"26.0.0"`。
5. 鸿蒙构建要联网拉定制版制品；`maven.eazytec-cloud.com` 直连可达，不需要代理。
