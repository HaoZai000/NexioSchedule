# HyperOS 4 玻璃材质参数（GlassToken）

来源：`/system_ext/framework/miuix.jar` → `Lmiuix/theme/token/GlassToken.<clinit>`。
由 `dexdump -d` 反汇编 + dex 常量池还原，35 个档位全部硬编码在静态初始化里（不在 MAML、不在 APK）。

## 结构

`GlassToken.create` 收 34 个参数，拆成 9 个子配置：

| 子配置 | 字段 |
|---|---|
| `blend` | luminanceValues[4] · luminanceAmount · saturation · brightness · darker · darkerRange[2] |
| `inner` | bottom · color[4] · colorWhite · colorMix · colorPow · alpha |
| `shape` | edge · edgePow · thickness · reflectOffset |
| `reflect` | lighten · strength |
| `directionalLight` | direction[3] · intensity · oppositeIntensity · angleRange · edgePow |
| `refract` | ior |
| `blurBg` | saturation · brightness · burn · unShade |
| `mask` | pressLightCenterFall / Strength / Peak · pressRingStrength[2] |
| `blurRadius` | smallBlurRadius(int) · bigBlurRadius(int) |

⚠ `edge` / `thickness` / `reflectOffset` / `smallBlur` / `bigBlur` 单位是 **px**，不是 dp。

## 35 个档位

三族：`Bionic_Medium_*`（仿生）、`Common_{Small,Medium,Large}_*`（常规）、`Tint_*`（彩色玻璃）。

| 档位 | edge | thick | reflOff | reflLt | reflStr | ior | inten | opp | satur | brigh | darker | cWhite | cMix | cPow | bgSat | burn | sBlur | bBlur |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Bionic_Medium_Thin | 60 | 60 | 1000 | 1.0 | 0.8 | 3.0 | 1.2 | 0.8 | 1.4 | 0.08 | 0.20 | 0.15 | 0.40 | 1.2 | 2.4 | 0.0 | 40 | 500* |
| Bionic_Medium_Normal | 60 | 80 | 1200 | 1.0 | 0.8 | 3.0 | 1.6 | 0.8 | 1.4 | -0.02 | 0.30 | 0.20 | 0.30 | 1.2 | 2.6 | 0.0 | 120 | 120 |
| Bionic_Medium_Regular | 60 | 80 | 600 | 1.0 | 0.8 | 3.0 | 2.6 | 1.2 | 1.8 | -0.18 | 0.10 | 0.16 | 0.30 | 1.2 | 2.0 | 1.0 | 36 | 500* |
| Bionic_Semibold_Motion | 60 | 80 | 1200 | 1.0 | 0.8 | 3.0 | 3.0 | 2.0 | 2.0 | -0.02 | 0.30 | 0.15 | 0.90 | 1.2 | 2.6 | 0.0 | 120 | 120 |
| Common_Small_Regular | 18 | 100 | 110 | 1.0 | 0.8 | 3.0 | 1.1 | 0.5 | 1.4 | 0.14 | 0.20 | 0.30 | 0.50 | 1.2 | 1.0 | 0.0 | 8 | 500* |
| Common_Small_Bold | 60 | 80 | 1200 | 1.0 | 0.8 | 3.0 | 1.6 | 0.8 | 1.4 | 0.28 | 0.40 | 0.20 | 0.30 | 1.2 | 2.6 | 0.0 | 120 | 120 |
| Common_Medium_Thin_Low | 60 | 60 | 600 | 1.0 | 0.8 | 3.0 | 1.2 | 0.8 | 1.5 | 0.20 | 0.10 | 0.00 | 0.30 | 1.2 | 1.0 | 0.0 | 36 | 500* |
| Common_Medium_Regular_Dark | 60 | 80 | 600 | 1.0 | 0.8 | 3.0 | 2.4 | 1.6 | 3.0 | 0.20 | 0.00 | 0.00 | 0.80 | 1.2 | 0.0 | 0.0 | 36 | 500* |
| Common_Large_Regular | 50 | 80 | 600 | 1.0 | 0.8 | 3.0 | 1.6 | 0.8 | 2.2 | 0.08 | 0.20 | 0.15 | 0.40 | 1.2 | 0.0 | 0.0 | 80 | 500* |
| Common_Large_Demibold | 50 | 80 | 600 | 1.0 | 0.8 | 3.0 | 2.0 | 1.0 | 2.0 | 0.18 | 0.26 | 0.60 | 0.90 | 1.2 | 0.0 | 0.0 | 50 | 500* |
| Tint_Purple | 60 | 80 | 600 | 1.0 | 0.8 | 3.0 | 1.4 | 1.0 | 1.0 | 0.60 | 0.20 | 0.50 | 0.80 | 1.2 | 0.0 | 0.0 | 36 | 500* |
| Tint_Black_Regular | 60 | 80 | 1200 | 1.0 | 0.8 | 3.0 | 3.4 | 2.6 | 1.4 | 0.10 | 0.30 | 0.20 | 0.30 | 1.2 | 4.0 | 0.0 | 120 | 120 |
| Tint_Black_Heavy | 72 | 80 | 1000 | 1.0 | 0.8 | 3.0 | 1.6 | 1.0 | 1.2 | 0.00 | 0.40 | 0.26 | 0.30 | 1.2 | 0.0 | 0.0 | 40 | 500* |

\* `bigBlur` 由上一个档位的寄存器继承（编译器复用），不是它自己的字面量。`ior` 全族统一 3.0，`angleRange` 统一 1.4，`reflectLighten` 统一 1.0，`reflectStrength` 统一 0.8。

数组（以 Thin 为例）：

| 字段 | 值 |
|---|---|
| luminanceValues | `[0.0, 2.0, 0.5, 0.8]` |
| darkerRange | `[0.6, 1.0]` |
| innerColor | `[1.0, 1.0, 1.0, 0.15]` |
| direction | `[-0.4, 0.6, -0.8]` |
| pressRingStrength | `[0.0, 0.0]` |