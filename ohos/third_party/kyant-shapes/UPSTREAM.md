# kyant shapes（上游源码副本）

`:backdrop` 的 `effects/Lens.kt` 有一个 `is RoundedRectangularShape ->` 分支，用来从形状里取四角半径。
该库是 KMP 的，但 `io.github.kyant0:shapes` **只发布了 android 变体**（仓库里只有
`shapes` / `shapes-android`），鸿蒙端没有可解析的制品，所以这里放**未修改的上游源码**，
由 ohos 构建直接编成 `ohosArm64`。

| 项 | 值 |
|---|---|
| 来源 | `io.github.kyant0:shapes:1.2.1` 的 `-sources.jar` |
| 路径 | `commonMain/com/kyant/shapes/` |
| 许可 | Apache-2.0 |
| 规模 | 11 个 kt，纯 Compose 依赖（无任何平台 API） |

⚠ 只有 `:backdrop` 用它（`com.kyant.capsule` 是 backdrop 自己 fork 的，跟这个库无关）。
miuix 用的是 `com.kyant.capsule`，不是本库。

升级时：下载新版本 `-sources.jar`，覆盖 `commonMain/`，并核对 `Lens.kt` 里用到的
`RoundedRectangularShape.corners(...)` 签名没变。
