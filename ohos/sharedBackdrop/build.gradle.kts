// :backdrop 的 ohosArm64 变体，源码仍是主仓库那一份（srcDir）。

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
            kotlin.srcDir(rootProject.file("../../softglass/src/commonMain/kotlin"))
            // org.jetbrains:annotations 只有 JVM 制品，这里用同名空注解桩
            kotlin.srcDir(rootProject.file("third_party/jetbrains-annotations-stub/commonMain"))
            dependencies {
                implementation(compose.foundation)
                implementation(compose.ui)
            }
        }
        // 不用 skikoMain：那套 actual 依赖 `skiaImageFilter` / `skiaPaint` / `asComposeShader`
        // 这几个扩展名，本 klib 里没有同名符号（等价能力在 `asFrameworkPaint()`、
        // `asSkiaImageFilter()` 与 `Shader` 类型别名上）。ohos 自己的 actual 在
        // src/ohosMain/kotlin（默认源根），用这些公开 API 做与 Android 等价的真实现。
    }
}
