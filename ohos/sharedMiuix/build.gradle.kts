// miuix 的 ohosArm64 变体：本地 fork 的 miuix-ui + 上游子库（源码 vendoring 在 third_party/miuix-vendor）。
// 合在一个模块是因为 miuix-icons 与 miuix-ui 互相引用，分开会成环。
// 官方 artifact 只有 android 变体，源码平台无关，直接编。
// 升级：libs.versions.toml 的 miuix 版本一变，按同版本重下 -sources.jar 覆盖 vendor。

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
