// Nexio课程表 - 液态玻璃效果库（fork 自 io.github.kyant0:backdrop 2.0.1）
//
// 为什么 fork 而不是直接依赖 Maven artifact：本项目对 backdrop 有 671 行定制
// （DrawBackdropModifier / LayerBackdropModifier / RuntimeShader / RenderEffect 等 14 个文件），
// 定制直接住在源码里，才方便在 KMP 的两个平台源集上分别落地。
//
// 源集分工：
//   commonMain  —— 上游 2.0.1 的跨平台逻辑 + 本项目的定制
//   androidMain —— Android 侧 actual（RenderEffect / AGSL / 原生 RuntimeShader / asFrameworkPaint）
//   skikoMain   —— iOS / Desktop / Web 侧 actual（Skia RuntimeEffect + SkSL）
//
// 上游 2.0.1 本身就是 KMP 库（platform type: common/native/jvm/wasm/js），
// 所以 SkSL 那半边是现成的，本项目只需要把定制在 skikoMain 上补齐即可。
//
// 插件写法与 :core 一致：AGP 9 下 KMP 模块的 Android 目标用
// com.android.kotlin.multiplatform.library，配置写在 kotlin { android { } } 里。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    // CMP 1.6.10 起必须显式应用 Kotlin 的 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.backdrop"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    // ── 实验性：wasmJs 编译门禁─────────────────────────────────────────────
    // 目的：skikoMain 只被 jvm 复用，从未针对非 JVM 目标编译过（静态检查挡不住未知 API）。
    // linuxX64 不可行 —— 实测 CMP 1.12.0 发布的变体只有
    // android / desktop(jvm) / iosArm64 / iosSimulatorArm64 / js / macosArm64 / wasmJs，
    // 无任何 linux 目标。能在 Windows 上编译的非 JVM 目标只剩 js / wasmJs。
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            // 上游用来做形状运算的库，本项目 ui/effects/shapes 与之同源
            api(libs.kyant.shapes)
            // @Language("AGSL") 注解（RuntimeShader / RuntimeShaderCache / RenderEffect / Shaders
            // 共 8 处）。wasmJs 目标上没有该依赖时会报 Unresolved reference 'intellij'；
            // 与 :miuix 一致显式声明，见 miuix/build.gradle.kts:40。
            api("org.jetbrains:annotations:26.1.0")
        }
        // jvm / wasmJs 目标复用 skikoMain（Skia 实现）。用 srcDir 而不是拷贝一份，
        // 将来加 iOS/desktop target 时同一份 SkSL 代码直接共享。
        jvmMain {
            kotlin.srcDir("src/skikoMain/kotlin")
        }
        wasmJsMain {
            kotlin.srcDir("src/skikoMain/kotlin")
        }
    }
}
