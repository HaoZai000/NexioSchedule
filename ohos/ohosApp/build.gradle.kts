// 鸿蒙端构建脚本（独立于根构建）。
//
// 工具链版本线：CPF-KMP-CMP **1.2.0 批次**（2026-11-01 实测确认，别改成 0.1.0 批次）：
//   Kotlin  2.2.21-1.2.0-04
//   Compose 1.9.2-1.2.0-08
//   Skiko   0.9.22.2-1.2.0
//   Gradle  8.14.1 + JDK 21
// 依据：ArkTS 侧 HAR @cpf-kmp-cmp/compose 只发布到 1.9.2-1.2.0，且其 CHANGELOG 明确
// 「版本对齐 CPF-KMP-CMP 1.2.0 批次（配套 Kotlin 2.2.21-1.2.0 / Skiko 0.9.22.2-1.2.0 /
// CMP 1.9.2-1.2.0）」。Maven 上更新的 1.12.0-0.1.0-15 是预发批次，宿主包没发，接不上。
// 升级时三个版本必须一起动，且要与 ArkTS 侧 `@cpf-kmp-cmp/compose` HAR 的版本对齐 ——
// HAR 负责把 Compose 的绘制接到 ArkUI 上，两边版本不一致会在运行时（不是编译时）黑屏。

plugins {
    kotlin("multiplatform") version "2.2.21-1.2.0-04"
    id("org.jetbrains.compose") version "1.9.2-1.2.0-08"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21-1.2.0-04"
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
        // ohos 分组不在默认层级模板里，必须手工建出来再让目标源集依赖它
        val ohosMain by creating { dependsOn(commonMain.get()) }
        ohosArm64Main { dependsOn(ohosMain) }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}

// ── 产物同步：把 Kotlin 编出来的 .so 交给 ArkTS 宿主 ────────────────────────
// 官方示例里这一步叫 publishDebugBinariesToHarmonyApp，就是 hvigor 侧的 Copy 任务。
// 放在这里而不是 hvigorfile，是因为产物归 Kotlin 构建所有：谁生成谁负责搬，
// 免得「忘了拷」被误判成「Compose 没生效」。
val publishToHarmonyApp by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("bin/ohosArm64/debugShared")) {
        include("libnexioohos.so")
    }
    into(rootProject.file("../harmonyApp/entry/libs/arm64-v8a"))
}

tasks.matching { it.name == "linkDebugSharedOhosArm64" }.configureEach {
    finalizedBy(publishToHarmonyApp)
}
