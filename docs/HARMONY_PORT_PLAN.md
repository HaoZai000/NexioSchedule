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
| Kotlin | 2.4.10 | 2.2.21-1.2.0-04（1.2.0 批次，见下一节） |
| Compose | 1.12.0（JetBrains 官方） | 1.9.2-1.2.0-08（第三方适配） |
| Gradle | 9.5.1 | 8.14.1（1.2.0 批次只支持 Gradle 8） |
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

## 三、版本矩阵（2026-11-01 实测，别再猜）

Maven（`maven.eazytec-cloud.com/nexus/repository/maven-public`）与 ohpm 上同时存在两条线，
**只有一条能落地**：

| | 1.2.0 批次（正式） | 0.1.0 批次（预发） |
|---|---|---|
| Kotlin | `2.2.21-1.2.0-04` | `2.4.20-0.1.0-12` |
| Compose Multiplatform | `1.9.2-1.2.0-08` | `1.12.0-0.1.0-15` |
| Skiko | `0.9.22.2-1.2.0` | 未见配套 |
| ArkTS HAR（ohpm） | `@cpf-kmp-cmp/compose` **1.9.2-1.2.0** ✅ | ❌ 没有 |
| Gradle / JDK | 8.14.1 / 21 | 9.x / 25 |
| 宿主入口 API | `androidx.compose.ui.window.ComposeArkUIViewController(env, content)` ✅ 实测存在 | ❌ 该 klib 里没有这个函数 |

依据：

1. HAR 的 `CHANGELOG.md` 原文：「版本对齐 CPF-KMP-CMP 1.2.0 批次（配套 Kotlin 2.2.21-1.2.0 /
   Skiko 0.9.22.2-1.2.0 / Compose Multiplatform 1.9.2-1.2.0）」；
2. ohpm 上 `@cpf-kmp-cmp/compose` 一共 7 个版本，最高就是 `1.9.2-1.2.0`，没有 1.12 线；
3. 我 dump 了 `ui-ohosarm64-1.9.2-1.2.0-08.klib` 的元数据，确认
   `public fun ComposeArkUIViewController(env: napi_env, content: @Composable () -> Unit): napi_value` 存在；
   而 `1.12.0-0.1.0-15` 的 ui klib 里搜不到这个函数 —— 我第一晚按 2.4.20 线写入口时报
   `Unresolved reference` 就是因为这个。

**结论**：以 1.2.0 批次为准。0.1.0 批次虽然 Maven 上"更新"，但 ArkTS 宿主没发布，接不上。

---

## 四、已验证跑通的

```bash
cd ohos
export JAVA_HOME="$HOME/jdk21/jdk-21.0.2.jdk/Contents/Home"   # 1.2.0 批次要 JDK 21
./gradlew :ohosApp:linkDebugSharedOhosArm64
# → ohosApp/build/bin/ohosArm64/debugShared/libnexioohos.so（Compose UI 已编进 .so）
# → nm -gU 该文件可见 `T MainArkUIViewController`，即宿主要调的那个导出符号
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

## 五、宿主接法（已接通）

链路：`Index.ets` → `MainArkUIViewController()`（NAPI）→ `libentry.so` → `libnexioohos.so`
→ `ComposeArkUIViewController(env) { NexioRoot() }` → HAR 的 `Compose` 组件 → XComponent。

已落地的文件：

| 文件 | 作用 |
|---|---|
| `entry/src/main/cpp/napi_init.cpp` | 注册 NAPI 模块 `entry`，把 Kotlin 的 `MainArkUIViewController(env)` 包成 `napi_callback(env, info)` |
| `entry/src/main/cpp/CMakeLists.txt` | 以 IMPORTED 方式链接 `libs/arm64-v8a/libnexioohos.so`，需要额外链 `libace_napi.z.so`（napi_*）与 `libhilog_ndk.z.so`（OH_LOG_Print） |
| `entry/src/main/cpp/types/libentry/` | `libentry.so` 的 ArkTS 类型声明 + 本地 oh-package |
| `entry/oh-package.json5` | `@cpf-kmp-cmp/compose: file:../third_party/cpf-compose-1.9.2-1.2.0.har` + `libentry.so` |
| `entry/build-profile.json5` | `externalNativeOptions`（CMake 路径 + `abiFilters: arm64-v8a`） |
| `ohos/ohosApp/build.gradle.kts` | `publishToHarmonyApp`：link 完自动把 `.so` 拷到宿主（`entry/libs/` 不进版本库） |

出包验证（HAP 34MB）：`libs/arm64-v8a/` 里 `libnexioohos.so`、`libskikobridge.so`（HAR 带）、
`libentry.so`、`libc++_shared.so` 四个都在。

**还差两步才能上机**：

1. **签名**：`devecocli auth login` → `devecocli signature generate`（或 DevEco 里开自动签名），需华为账号；
2. **`initResourceManager`**：README 要求 `EntryAbility.onCreate` 里把 `context.resourceManager`
   交给 Compose（`import { initResourceManager } from 'libentry.so'`）。当前没接 —— 只影响
   Compose 资源（图片/字体）加载，纯文本界面不受影响，等接 `:ui-shared` 的图标资源前必须补。

---

## 六、怎么走到「一套 UI」

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

## 七、系统能力与后续顺序

---

## 八、本机环境（换机照做）

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


---

## 九、工程结构（2026-11-01 对照官方脚手架核过）

用 `devecocli create --app-name NexioRef --api-level 26` 生成参照工程，逐项比对 `harmonyApp/`：

**一致的部分**：`AppScope/`、`entry/`、`hvigor/`、`build-profile.json5`、`entry/build-profile.json5`、
`entry/src/main/module.json5`、`hvigorfile.ts`、`oh-package.json5`，以及
`compatibleSdkVersion: "26.0.0"`（这个写法与官方模板逐字相同，早先写成 `"26.0.0(26)"` 是错的）。

**已补齐的缺口**（原来手写工程漏掉的）：

| 文件 / 配置 | 作用 |
|---|---|
| `code-linter.json5` | ArkTS 规范扫描入口（`devecocli check lint` 读它） |
| `entry/obfuscation-rules.txt` + entry/profile 的 `buildOptionSet` | release 混淆配置位 |
| `entry/src/main/ets/entrybackupability/EntryBackupAbility.ets` | 系统备份 / 换机迁移扩展 |
| `entry/src/main/resources/base/profile/backup_config.json` | 上面那个 Ability 的 `allowToBackupRestore` |
| `entry/src/main/resources/dark/element/color.json` | 深色下启动窗口底色（否则冷启动会闪白） |
| `entry/build-profile.json5` 的 `ohosTest` target | 测试 target，官方模板默认就有 |
| `entry/.gitignore` | 模块级忽略（`/build`、`.preview` 等） |

**刻意没补**：官方模板里的 `resources/base/element/float.json`（`page_text_font_size` 是模板 Index 的
占位尺寸，我们没用到，加了就是死资源）；另外删掉了之前误拷的 `AppScope/.../app_icon.png` 与
`entry/.../icon.png` —— 官方模板用的是 `layered_image`（前景+背景分层图标），不需要这两张。

**还缺一块（预期内，下一步）**：接 Compose 后需要 `entry/src/main/cpp/`（NAPI 胶水 `napi_init.cpp` +
`CMakeLists.txt` 导入 `libnexioohos.so`）以及 entry/profile 的 `buildOptions.externalNativeOptions`。
官方模板不会有它，这是走 KMP/CMP 这条路的必要增量。

### 工具链现状

- `devecocli create` / `docs` / `device` 正常；
- `devecocli build` 与 `check lint` 在本机报 `spawn EPERM`（它内部 spawn 子进程时被拒），
  **与工程结构无关**：同一份工程用 `hvigorw assembleHap` 可以 BUILD SUCCESSFUL。
  lint 因此显示 "Files checked: 0"，等于没跑 —— 需要真跑规范检查时用 DevEco Studio 的 Code Linter。
