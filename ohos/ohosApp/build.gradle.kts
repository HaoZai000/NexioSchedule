// 鸿蒙端构建脚本（独立于根构建）。
//
// 工具链版本线（CPF-KMP-CMP 1.2.0 批次，对应官方 Kotlin 2.2.21 / CMP 1.9.2）：
//   Kotlin  2.4.20-0.1.0-12
//   Compose 1.12.0-0.1.0-15
//   Gradle  9.5.1（与主构建同一个 wrapper）
// 升级时三个版本必须一起动，且要与 ArkTS 侧 `@cpf-kmp-cmp/compose` HAR 的版本对齐 ——
// HAR 负责把 Compose 的绘制接到 ArkUI 上，两边版本不一致会在运行时（不是编译时）黑屏。

plugins {
    kotlin("multiplatform") version "2.4.20-0.1.0-12"
    id("org.jetbrains.compose") version "1.12.0-0.1.0-15"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20-0.1.0-12"
}

kotlin {
    // ⚠ 不能声明 jvm()：适配版只发布 AndroidJVM 与 native(ohos/ios) 变体，
    // 从不发布 jvm(desktop) 制品 —— 加了 jvm 目标会去要 ui-desktop，直接解析失败（已实测）。
    // 想在本机看排版，用上游 CMP 起一个临时工程指到同一个源文件，别往这里加。

    // 真机 + 模拟器都是 arm64（Apple Silicon 上的鸿蒙模拟器同样走 arm64）
    ohosArm64 {
        binaries {
            sharedLib {
                baseName = "nexioohos"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
