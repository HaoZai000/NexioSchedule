// miuix 的 ohosArm64 变体：本地 fork 的 miuix-ui + 上游子库（icons / squircle /
// materialKolor，源码 vendoring 在 third_party/miuix-vendor）。
//
// 合在一个模块是因为 miuix-icons 的扩展图标引用 miuix-ui 里的 MiuixIcons，分开会成环。
// 不用官方 artifact 是因为它们只发布了 android 变体，源码本身平台无关，直接编。
//
// miuix-blur / miuix-shader / miuix-navigation3-ui 没放进来：:miuix 对它们 0 引用。
// 升级口径：libs.versions.toml 的 miuix 版本一变，按同版本重新下载 -sources.jar 覆盖 vendor。

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    ohosArm64()

    sourceSets {
        val ohosMain by creating { dependsOn(commonMain.get()) }
        ohosArm64Main { dependsOn(ohosMain) }

        commonMain {
            // 本地 fork 的 miuix-ui，一份源码两个构建
            kotlin.srcDir(rootProject.file("../miuix/src/commonMain/kotlin"))
            kotlin.srcDir(rootProject.file("third_party/miuix-vendor/commonMain"))
            dependencies {
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                // 玻璃 / 超椭圆 / 描边都建在 backdrop 的类型上
                api(project(":sharedBackdrop"))
            }
        }
        // skikoMain = iOS/桌面的 Skia 实现，鸿蒙同为 Skia 后端
        ohosMain {
            kotlin.srcDir(rootProject.file("../miuix/src/skikoMain/kotlin"))
            kotlin.srcDir(rootProject.file("third_party/miuix-vendor/ohosMain"))
        }
    }
}

// miuix-blur 用了 Kotlin 2.2 的 context parameters，上游也是开着编的
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}
