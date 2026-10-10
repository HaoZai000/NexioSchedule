// Nexio课程表 - 小米风格 UI 组件库（fork 自 top.yukonga.miuix.kmp:miuix-ui 0.9.3，KMP）
//
// 为什么 fork 而不是直接依赖 Maven artifact：本项目对 Miuix 有 3,177 行定制
// （ListPopup 1154 / NavigationRail 475 / DynamicColors 328 / SearchBar 268 / ListPopupLayout 266 …），
// 定制直接住在源码里，才方便在 KMP 的两个平台源集上分别落地。
//
// 源集分工：
//   commonMain  —— 走 CMP 的组件主体（含本项目全部定制，Import 一行不用改）
//   androidMain —— Android 原生实现（系统取色 / EditText 系输入框 / navigationevent 返回键）
//   skikoMain   —— iOS / Desktop 侧 actual（模糊描边 SkSL、系统取色降级）
//
// 与 :backdrop 同构。Kotlin/Compose 插件写法见 core/build.gradle.kts 的注释。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    // CMP 1.6.10 起必须显式应用 Kotlin 的 Compose 编译器插件
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.miuix"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    // ── wasmJs 编译门禁（与 :backdrop 同思路）────────────────────────────────
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
            implementation(compose.material3)
            // 液态玻璃：绘制路径与能力探测（isRenderEffectSupported 等）都在这里
            api(project(":backdrop"))
            // Material Color（miuix theme 的 Monet 取色依赖）
            api(libs.materialKolor.utilities)
            // AGLS/SkSL 运行时的编译期注解
            api("org.jetbrains:annotations:26.1.0")

            // 本项目只 fork 了 miuix-ui 这一个模块；其余子库仍是官方 artifact。
            // 注意这里用**不带 -android 后缀的根坐标**：KMP 模块由 Gradle 按目标解析变体，
            // 写死 -android 会让 commonMain 编不过（app 模块那边才是 -android）。
            implementation("top.yukonga.miuix.kmp:miuix-squircle:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-preference:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:${libs.versions.miuix.get()}")
        }
        androidMain.dependencies {
            // SearchBar / BottomSheet 的返回键处理，CMP 无对应库，Android 侧走原生实现
            implementation(libs.navigationevent.compose)
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
