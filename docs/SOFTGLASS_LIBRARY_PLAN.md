# 柔光玻璃依赖库抽离方案

目标：渲染引擎自己重写一遍，产出一个自研、自包含、命名规范的单一 KMP 库。调用方只引这一个库，不需要再引任何其它模糊 / 形状 / 玻璃库；应用侧该改名就改名。`docs/hyperos-glass-token.md` 里只记在纸上的参数落成代码中可选的档位。

## 1 验收标准

1. **单依赖**：`implementation("com.haooz.softglass:softglass:0.1.0")` 一行就够。新库的 POM / klib / AAR 不含任何第三方模糊、形状、玻璃制品；`softglass/src` 与对外 API 中不出现其它库名或包名。
2. **效果一致**：库默认档在课表卡片、今日卡片、悬浮控件、实时浮层四条路径上的输出与当前逐像素一致。判据是参数 golden 测试 + shader 编译测试 + release 包同壁纸同页面截图比对。
3. **自研引擎**：新库源码按本文的分层与 API 重新实现，旧模块只作为行为基线用于对照，不搬文件、不复制代码、不保留旧包名。
4. **档位可选**：`GlassToken` 13 档 + `GlassPreset.Nexio` 默认档 + 3 档质量档，都是代码中真实对象。
5. **零回归**：调用方全部切换后，Android / iOS / 桌面 / 鸿蒙构建与现有单测全绿。

## 2 现状盘点

### 2.1 引擎规模与分布

| 层 | 路径 | 规模 |
|---|---|---|
| 采样 / 折射 / 边光引擎 | `backdrop/src/`（commonMain 58 文件、androidMain 5、skikoMain 5、ohosMain 4） | 7125 行 |
| 连续圆角几何 / SDF | 上者内的 `capsule` 包（18 文件） | 2223 行 |
| 柔光玻璃本体 | `ui-shared/.../ui/glass/`（9 common + android / skiko 各 1 个 actual） | 2342 行 |
| 接线层 | `SoftGlassSurface.kt`、`AppMaterialSettings.kt`、`Theme.kt`、`ScheduleGlassSurface.kt`、`EdgeLightBindings.kt` | 5 文件 |
| 测试 | `SoftGlassTexMapTest`、`SoftGlassShaderCompileTest`、`G2CornerParityTest`、`LiquidFusionContinuityTest` | 4 个 |

合计约 9500 行渲染相关代码要重写。全仓 215 个源文件含旧包名，其中 94 个文件 import 采样引擎、93 个 import 形状包、17 个 import 柔光样式。

### 2.2 必须清掉的外部依赖与旧名字

| 类别 | 位置 | 处理 |
|---|---|---|
| 远程形状库 | `gradle/libs.versions.toml:7,85`、`backdrop/build.gradle.kts:31` 的 `api(...)` | 删除；唯一使用点是 `effects/Lens.kt:16` 的 `RoundedRectangularShape`，由新库 `shape` 包自研替代 |
| 鸿蒙 vendor 形状源码 | `ohos/third_party/kyant-shapes/`（11 文件 1068 行）、`ohos/sharedBackdrop/build.gradle.kts:19` 的 srcDir | 删除，改指向新库 `shape` 源根 |
| 旧包名 | 采样引擎包、形状包、柔光包 | 全仓机械替换为 `com.haooz.softglass.*`，含 `:miuix` 与 `:androidApp` 里的 import |
| 第三方 blur 兼容层 | `ui-shared` 4 个文件（`AboutScreen.kt`、`BgEffectPainter.kt`、`BgEffectBackground.kt`、`effects/miuix/BlurUtils.kt`）、`ui-shared/build.gradle.kts:81` 与 `miuix/build.gradle.kts:45` 的远程 blur 依赖 | 玻璃相关调用改走新库；4 个文件改用新库 API 后移除该远程依赖。`:miuix` 作为 UI 组件库保留，其玻璃能力只读新库的 Local |
| 鸿蒙旧 blur vendor | `ohos/third_party/miuix-vendor/.../blur/` | 由新库 `ohosMain` 后端替代，不再编这份副本 |
| 业务语义名 | `AppThemeSnapshot` / `PageBackdropSnapshot`、`ScheduleSoftGlassStyle` / `TodaySoftGlassStyle`、shader key 前缀 `NexioSoftGlassLiveCourseCard_` | 分别改为 `GlassEnvironment`、`GlassPreset.Nexio.style`、`SoftGlassLive_` |

### 2.3 效果口径散落在 6 处

| 来源 | 关键数值 |
|---|---|
| `SoftGlassStyle()` 默认 | edge 60 / thickness 96 / sat 2.0 / smallBlur 32 / bigBlur 500 / ior 3 / 光方向 `(-0.42, 0.74, -1.15)` / angleRange 1.0 / darker 0 |
| `GlassTone` 三档 | `TokenTone`（darker 0.08、深紫黑）、`BrightTone`（`Color.Black`）、`BrightLargeTone`（edge 75 / thickness 120） |
| `GlassFill` 四策略 | `ThemeFill` / `BarFill` 深 0.45 浅 0.55，`CustomizeFill` 深 0.55 浅 0.65，均钳 `saturation ≤ 2.0` |
| `AppMaterialSettings` | 模糊 6 / 12 / 12 dp 全档统一；alpha 降幅浅 0.2 深 0.32；无材质档描边 0.8dp 纯色 SrcOver；BALANCED 关折射与方向光 |
| `ScheduleGlassSurface` | 光强 0.8 / 0.45、angleRange 1.78、falloff 0.86、浮层 darker 0.2 |
| 渲染基建 | 金字塔 `BASE_MAX_EDGE=1024` / `LEVEL_COUNT=7` / `LEVEL1_SIGMA=3f`、program 缓存 32、`σ=(r-0.5)/0.57735`、AA 阈值 `smooth5Vertical(3/edge)` |

**双份默认值冲突**：`miuix/MaterialSettings.kt` 的 `LocalGlassBlurDp=8f` / `LocalGlassPanelBlurDp=24f` / `LocalGlassDialogBlurDp=12f`，而 `Theme.kt` 实际下发 6 / 12 / 12。新库只保留一套，否则外部调用方不注入时看到的是另一套画面。

## 3 目标架构

### 3.1 模块与坐标

新模块 `:softglass`，`settings.gradle.kts` 增加 `include(":softglass")`。唯一外部依赖是 Compose（`compose.foundation` / `compose.ui`，skiko 由 CMP 传递），加一个仅编译期用的 `@Language` 注解依赖；`org.jetbrains:annotations` 也去掉，改用自己的注解或纯字符串常量。

```
调用方 → :softglass（单一 artifact，KMP 按 target 自动出变体）
:core / :miuix / :ui-shared / i18n / composeResources → 不进 :softglass
```

Maven 坐标 `com.haooz.softglass:softglass:0.1.0`。

### 3.2 包与 API 命名规范

统一根包 `com.haooz.softglass`，公开 API 全在根包及下列子包，`internal` 只放平台桥与缓存：

| 包 | 内容 | 对应旧实现 |
|---|---|---|
| `com.haooz.softglass` | `GlassSpec` / `GlassPreset` / `GlassToken` / `GlassMaterialLevel` / `GlassTuning` / `SoftGlassTheme` / `Local*` | 新增 + 接线层 |
| `...softglass.backdrop` | `GlassBackdrop` / `drawGlassBackdrop` / `rememberLayerBackdrop` / effects DSL（`blur` / `lens` / `vibrancy` / `runtimeShaderEffect`）/ 边光 / 高光 / 阴影 | 采样引擎 |
| `...softglass.shape` | 连续圆角矩形、胶囊、G2 连续性、`SdfG2Corner`、`continuousClip` 等 Modifier | 形状包 + 远程形状库 |
| `...softglass.glass` | `SoftGlassStyle` / `GlassTone` / `GlassFill` / shader 源码 / 7 级金字塔 / 静态与实时两条绘制路径 | 柔光层 |
| `...softglass.platform` | `expect/actual` 能力探测与 shader 后端（AGSL / SkSL / 鸿蒙退化） | 各 actual |
| `...softglass.internal` | 录制节点、shader 缓存、颜色矩阵、坐标映射 | 内部件 |

命名规则：公共类型不带业务前缀（`Glass*`、`SoftGlass*`），状态对象用 `@Immutable`，`expect/actual` 只出现在 `platform`，shader 常量与 program 一律 `internal`。

### 3.3 平台后端

commonMain 放 shader 源码 / 样式 / 档位；`androidMain` 放 AGSL 后端（API 33 门禁）；`skikoMain` 放 SkSL 后端，`jvmMain` 与 iOS 通过 `kotlin.srcDir("src/skikoMain/kotlin")` 复用；`ohosMain` 放退化后端。iOS target 仍由 `-Pnexio.ios=true` 条件开启。能力门禁（API 31 RenderEffect / API 33 RuntimeShader）内置在库里，调用方不判断。

鸿蒙是独立构建树，用另一条 Kotlin/Compose 版本线，不消费 Maven 制品，继续以 srcDir 复用新库 `commonMain` / `skikoMain` / `ohosMain`；`ohos/sharedBackdrop` 与 `ohos/ohosApp/build.gradle.kts` 的 srcDir 全部改指 `:softglass`。

## 4 档位设计

三层互不混淆：`GlassSpec`（一份完整渲染参数）/ `GlassToken`（系统原值 13 档）/ `GlassMaterialLevel`（质量三档）。

### 4.1 API 签名

```kotlin
package com.haooz.softglass

@Immutable
data class GlassSpec(
    val style: SoftGlassStyle,
    val tone: GlassTone = TokenTone,
    val fill: GlassFill = NoneFill,
)

object GlassPreset {
    val Nexio: GlassSpec              // = 当前 SoftGlassStyle() 逐字段，默认档
    fun of(token: GlassToken): GlassSpec
}

enum class GlassToken(val spec: GlassSpec) { BIONIC_MEDIUM_THIN, /* ... 共 13 项 */ }

enum class GlassMaterialLevel { SOFT, FROSTED, NONE }

@Immutable
data class GlassTuning(
    val blurDp: Float, val panelBlurDp: Float, val dialogBlurDp: Float,
    val surfaceAlphaDropLight: Float, val surfaceAlphaDropDark: Float,
    val refractionScale: Float, val edgeHighlight: Boolean,
    val edgeLightResolver: (EdgeLight, Boolean, Color?) -> EdgeLight,
)
fun GlassMaterialLevel.tuning(): GlassTuning

@Composable
fun SoftGlassTheme(
    preset: GlassSpec = GlassPreset.Nexio,
    level: GlassMaterialLevel = GlassMaterialLevel.SOFT,
    isDark: Boolean,
    content: @Composable () -> Unit,
)
```

`SoftGlassTheme` 一次性 provide 全部 Local，包含原来 `:miuix` 的 8 个；`:miuix` 改为依赖 `:softglass` 并读新 Local。持久化仍留在 App 侧 `AppMaterialSettings`：读出的 `best` / `balanced` / `none` 映射成 `GlassMaterialLevel` 传入，键名与存量数据不动。

### 4.2 13 档字段映射

`docs/hyperos-glass-token.md:30-42` 的 18 列逐列映射到 `SoftGlassStyle`：

| MD 列 | 字段 | MD 列 | 字段 |
|---|---|---|---|
| `edge` | `edgeWidth` | `thick` | `thickness` |
| `reflOff` | `reflectOffset` | `reflLt` | `reflectionLighten` |
| `reflStr` | `reflectionStrength` | `ior` | `refractIOR` |
| `inten` | `lightIntensity` | `opp` | `lightOppositeIntensity` |
| `satur` | `saturation` | `brigh` | `brightness` |
| `darker` | `darker` | `cWhite` | `innerColorWhite` |
| `cMix` | `innerColorMix` | `cPow` | `colorPow` |
| `bgSat` | `bgColorSaturation` | `burn` | `burn` |
| `sBlur` | `smallBlurRadius` | `bBlur` | `bigBlurRadius` |

13 个档位名：`Bionic_Medium_Thin` / `Bionic_Medium_Normal` / `Bionic_Medium_Regular` / `Bionic_Semibold_Motion` / `Common_Small_Regular` / `Common_Small_Bold` / `Common_Medium_Thin_Low` / `Common_Medium_Regular_Dark` / `Common_Large_Regular` / `Common_Large_Demibold` / `Tint_Purple` / `Tint_Black_Regular` / `Tint_Black_Heavy`。

全族统一常量（MD:44、50-54）：`ior 3.0`、`angleRange 1.4`、`reflectionLighten 1.0`、`reflectionStrength 0.8`、`colorPow 1.2`、`luminanceValues [0,2,0.5,0.8]`、`darkerRange [0.6,1.0]`、`innerColor [1,1,1,0.15]`、`direction [-0.4,0.6,-0.8]`、`pressRingStrength [0,0]`。

MD 未给出逐档值、从 `GlassPreset.Nexio` 继承的字段：`edgePow`、`slopeMode`、`luminanceAmount`、`luminanceCurveMode`、`glassColorMix`、`innerBottom`、`alpha`、`hit`、`unshade`、`usePowGate`、`darkerColor`、`lightDirection`、`edgeHighlight`、`edgeLightFalloff`、`cornerRadius`、`refractionScale`。

shader 未实现、只保留占位不参与渲染的字段：`pressLightCenterFall` / `CenterStrength` / `CenterPeak` / `pressRingStrength`、`directionalLight.edgePow`、`mask.*`。`bgColorBrightness` 有 uniform 但 MD 表无列，继承 0。

标 `*` 的 `bBlur=500` 是反汇编的寄存器继承值而非该档字面量，落地时统一按 500 记录并注明来源。

`Glass_Common_Large_Regular` 的系统原值（50 / 80 / 2.2 / 80 / 1.4 方向光）与 `GlassPreset.Nexio`（60 / 96 / 2.0 / 32 / 1.0）**故意不同**，两者是独立 preset，不做对齐。

### 4.3 数值单一来源

`GlassToken.kt` 是 13 档数值的唯一来源。MD 里手写数值表改为「档位名 + 字段映射 + 指向 `GlassToken.kt`」，由 `GlassTokenTableTest` 反向断言 MD 表与代码一致；新增 / 改档只改代码，文档不再抄第二份数字。历史会话记忆里的实测值（blur 2/8、panel 4/24 等）互相矛盾且多已回滚，不作为规范来源。

## 5 公共 API

| 类别 | 签名 |
|---|---|
| 主题 | `SoftGlassTheme(preset, level, isDark, content)` |
| 静态壁纸路径 | `rememberGlassSurface(wallpaperBitmap, wallpaperScale, wallpaperOffset, screenWidthPx, screenHeightPx, spec, originTransform): GlassSurface?` |
| 绘制 | `Modifier.softGlass(backdrop, styleProvider, density, origin, texMapProvider, circle, radiusPx, redraw)`、`Modifier.softGlassBackdrop(backdrop, shape, style, radiusPx, redraw, blurSigmaPx)` |
| 采样源 | `rememberLayerBackdrop` / `LayerBackdrop` / `Modifier.layerBackdrop` / `rememberCombinedBackdrop` / `GlassBackdrop` / `drawGlassBackdrop` DSL |
| 参数 | `SoftGlassStyle`、`GlassSpec`、`GlassPreset`、`GlassToken`、`GlassTone`、`GlassFill`、`Shadow` |
| 能力 | `isRenderEffectSupported()` / `isRuntimeShaderSupported()` / `isSoftGlassShaderAvailable()` |
| Local | `LocalGlassSurface` / `LocalGlassRedrawSignal` / `LocalMaterialNoneEnabled` / `LocalGlassBlurDp` / `LocalGlassPanelBlurDp` / `LocalGlassDialogBlurDp` / `LocalGlassSurfaceAlphaDrop` / `LocalSoftGlassLayer` / `LocalEdgeLightResolver` |

金字塔、`SoftGlassProgram`、shader 源码常量、颜色矩阵、录制节点一律 `internal`。

## 5.1 范围裁剪（按实际调用，2026-11-08）

统计口径：`androidApp` / `ui-shared` / `miuix` 里 23 个 `drawBackdrop` 调用与全部 import。

**保留（必要）**

| API | 依据 |
|---|---|
| `GlassBackdrop` / `drawGlassBackdrop` | 39 / 18 个文件 |
| `GlassEffectScope` + `blur` + `runtimeShaderEffect` | 23 / 17 / 16 |
| `colorControls` | 1 处（背景层饱和度） |
| `LayerBackdrop` / `rememberLayerBackdrop` / `layerBackdrop` / `rememberCanvasBackdrop` / `rememberCombinedBackdrop` | 5 / 12 / 15 / 2 / 1 |
| `onDrawSurface` / `layerBlock` | 17 / 9 |
| `GlassRuntimeShader` / `asComposeShader` | `InteractiveHighlight` 13 个文件，按压高光保留 |
| `GlassEnvironment`（原 `AppThemeSnapshot` / `PageBackdropSnapshot`） | 同上 |

**投影**：`drawBackdrop` 带 `shadow: (() -> Shadow?)? = DefaultShadow`，由库自己画（离屏层 + 模糊轮廓挖空，形状跟玻璃同源，三端一致），默认 24dp / 黑 10% / y+4dp，传 `null` 关闭。23 处调用点按旧配置逐点保留：14 处显式 `null`、2 处 `Shadow(alpha = 0.3f)`、7 处吃默认。

**删除（零使用或全传 null）**

- `lens()` + 色散 shader（0 调用）、`vibrancy()`（只在预热里）、`opacity()`、`colorFilter()`、`effect(RenderEffect)`（均 0 调用）。
- `highlight`（23/23 全传 `null`）、`innerShadow`（0）、`onDrawBehind`（0）、`onDrawFront`（0）、`exportedBackdrop`（0）、`viewport`（0）、`onDrawBackdrop`（0）、`drawPlainBackdrop`（0）。
- `Highlight`、`InnerShadow`、`SharedBlurBackdrop`。
- 颜色折叠优化（`compileChain`）：唯一可触发的 `colorControls` 场景没有 `lens`，条件永不成立。
- **边光整套**（`EdgeLight` / `edgeLight` / `rememberDefaultEdgeLight` / `rememberCardEdgeLight` / `isSystemLightTheme` / `EdgeLightShaderHelper` / `EdgeLightModifier`，共约 950 行）：产品决定去掉卡片边缘高光描边；`GlassTuning.edgeHighlight`（shader 内的倒角带方向光）保留，两者不是一回事。

净效果：引擎层少约 2500 行；`GlassEffectScope` 从 298 行降到 70 行，不再有 op 记录数组。

## 5.2 实施状态（2026-11-08）

- `:softglass` 已完成：参数层、13 档 `GlassToken`、三档 `GlassMaterialLevel`、`SoftGlassTheme` + Local、shape 层（G2 连续圆角 + SDF）、platform 三端后端、录制层、`drawBackdrop`、柔光 shader（body / live）、7 级金字塔、静态与实时两条绘制路径。共 52 个 `.kt` / 4345 行。
- 应用侧 `com.kyant` 引用 **0**；`:backdrop` 模块、`io.github.kyant0:shapes` 远程依赖、`ohos/third_party/kyant-shapes` 已删除；鸿蒙 `sharedBackdrop` 改为直接编 `:softglass` 源码。
- `WidgetChromeCapsule` 已补柔光玻璃（overlay 建录制层 + `drawBackdrop`）；`BlurBottomSheet` 的假描边已删。真正的高光描边仍是柔光 shader 的 `processLighting`（外观页 `cardRim` 开关）。
- 投影在库内自绘（`backdrop/shadow/Shadow.kt` + `ShadowModifier.kt`，Linux/Android 走 `BlurMaskFilter`，Skiko 走 `MaskFilter.makeBlur`）。**带投影的玻璃件若有进离场 alpha，外层 `graphicsLayer` 必须用 `CompositingStrategy.ModulateAlpha`**：`Auto` 会离屏合成并按节点 bounds 裁掉整圈投影（外观页胶囊实测 alpha=0.5 时形状外像素回到纯白）。已有 `ShadowRenderTest` 固化「不传=开 / 传 null=关」。
- 门禁：Android / JVM / iOS 编译、`:softglass:jvmTest`、`:ui-shared:jvmTest`、`assembleDebug`、`checkKmpPurity` 全绿。鸿蒙独立构建树未跑。
- 保留的产品内容：`AboutScreen` 的开源致谢、`ChangelogData` 的历史更新日志仍含旧库名。
- 未配置 maven-publish（按约定发布后议）。

## 6 分步执行

**P0 冻结口径（不改结构）**
- 在现有位置新增 `GlassSpec.kt` / `GlassToken.kt` / `GlassTuning.kt`，把 §2.3 六处数值收进来；`AppMaterialSettings` 改为读 `GlassMaterialLevel.tuning()`，输出保持不变。
- 新增 `GlassGoldenTest`：断言 `GlassPreset.Nexio.style` 与当前样式全字段相等、三档 tuning 与当前 6 个函数输出相等、13 档数值与 MD 表一致。
- 补 `ShaderBaselineTest`：把三套 shader 源码与 G2 圆角路径的当前输出固化成对照基线（截图 / 像素哈希），供重写期比对。
- 验收：现有调用方零改动，`:ui-shared:compileAndroidMain` 与单测绿。

**P1 自研引擎（新模块，双跑对照）**
按依赖顺序重写，每完成一层跑一次对照测试：
1. `shape`：连续圆角 / G2 连续性 / SDF，替代远程形状库与旧 capsule 包（2223 行）。
2. `platform`：能力探测 + AGSL / SkSL / 鸿蒙三套 shader 后端与 `RuntimeShader` 抽象。
3. `backdrop`：录制层、采样源、effects DSL、边光 / 高光 / 阴影（约 4900 行）。
4. `glass`：柔光 shader、7 级金字塔、静态与实时两条绘制路径（2342 行）。
5. `api`：档位对象、`SoftGlassTheme`、全部 Local。

旧模块保留到全部对照通过再删除，避免长期双份维护。`softglass/build.gradle.kts` 按 §3.1 / §3.3 配好，根脚本 `purityModules` 加 `softglass`。

**P2 全仓改名与切换**
- 94 + 93 + 17 处 import 机械替换为 `com.haooz.softglass.*`；`project(":backdrop")` 依赖改指 `:softglass`，随后从 `settings.gradle.kts` 删除旧模块。
- 删除 §2.2 的远程依赖、鸿蒙 vendor 源码与 srcDir、第三方 blur 兼容层；`ui-shared` 4 个文件改用新库 API。
- `AppThemeSnapshot` → `GlassEnvironment`；`Theme.kt` 瘦身为一行 `SoftGlassTheme(...)`；`AppMaterialSettings` 只保留存盘 / 键名 / i18n / 平台锁定。
- `ScheduleGlassSurface` 的 App 侧坐标变换留在 `:ui-shared`，只把样式取值改成 `GlassPreset.Nexio`。
- 同步改 shader key 前缀与 `:miuix` 的 8 个 Local；清理 8 / 24 / 12 双份默认值。

**P3 发布与文档**
- `maven-publish`：`group="com.haooz.softglass"`、`version="0.1.0"`、POM、sources jar；`publishToMavenLocal` 验证 android / jvm / iOS 三类产物。
- 新库 `README.md`（快速开始 + 档位表 + 平台能力矩阵 + 效果一致口径）；`docs/hyperos-glass-token.md` 按 §4.3 改写。
- 新增 `sample/`（一张壁纸 + 一个玻璃卡片 + 档位切换），用于人工对比。

**P4 全量门禁**
- 跑完第 7 节全部门禁后停止，工作区留改动等待发话，不提交。

## 7 门禁与验收

| 项 | 命令 / 判据 |
|---|---|
| Android 零回归 | `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :androidApp:assembleDebug` |
| UI 模块编译 | `:ui-shared:compileAndroidMain` |
| 跨平台裁决 | iOS 编译必须带 `-Pnexio.ios=true`；`checkKmpPurity` 只是正则，绿不等于干净 |
| 纯净度 | `checkKmpPurity` 的 `purityModules` 加 `softglass` |
| 单依赖 | `./gradlew :softglass:dependencies` 里除 Compose 外无第三方制品；全仓 grep 旧包名 / 旧坐标零命中 |
| shader | 三套 shader 编译冒烟；13 档逐个灌 uniform 不抛异常 + 数值范围检查 |
| 参数 golden | `GlassGoldenTest` 逐字段断言，任何默认值的改动都必须先改测试 |
| 效果 | `ShaderBaselineTest` 的基线比对 + release 包同壁纸同页面截图对比（debug 包不用于画质 / 性能归因） |
| 鸿蒙 | `ohos/` 独立构建，新 srcDir 编译通过 |

## 8 风险与约束

- **重写与「效果一样」是主要矛盾**：从零重写的视觉漂移风险集中在 G2 圆角 SDF、倒角剖面 `smooth5_vertical`、7 级金字塔选级、录制层失效时机、AGSL/SkSL 浮点差异。对策是 P0 先固化基线，P1 逐层对照，任何一层对不上就停在那一层。
- 鸿蒙以 srcDir 直接编 `ui-shared/commonMain`，路径改动漏一处就缺文件；且鸿蒙后端是退化实现，不走 Skiko。
- shader key 目前带 `NexioSoftGlassLiveCourseCard_` 前缀，改前缀时保持「同尺寸同 key」语义，否则同帧多卡 uniform 会互相覆盖。
- `SOFT_GLASS_RIM_SHADER` 生产零引用、`SharedBlurBackdrop` 未被柔光链路使用：重写时不实现，用测试确认没有调用方依赖。
- 工作区当前有 5 个课程卡片文件被另一会话修改（`MainScheduleScreen.kt`、`TimelineScheduleView.kt`、`CourseCard.kt`、`DayColumn.kt`、新增 `ScheduleReveal.kt`），P2 触碰时只改 import 行，不改逻辑。
- 注释按现有约定：只写「是什么 / 为什么」，不写历史过程与日期。

## 9 待确认

1. 库默认 `level` 取 `SOFT`（完整柔光效果）还是 `FROSTED`（当前 App 冷启动默认档）？建议库默认 `SOFT`，App 侧显式传持久化档位保持 `FROSTED`。
2. `:backdrop` 模块删除后，`ohos/sharedBackdrop` 是否也一并删掉，改为 `ohosApp` 直接 srcDir 复用 `:softglass`？建议删，少一层构建。
3. Maven 坐标是否就用 `com.haooz.softglass:softglass:0.1.0`，以及是否需要签名 / 远程仓库发布（当前只规划 `publishToMavenLocal`）。
