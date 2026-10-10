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
    // ── wasmJs 编译门禁：**默认关闭**，用 -Pnexio.wasm=true 显式开启 ───────────
    // 目的：skikoMain 此前只被 jvm 目标复用，从未针对非 JVM 目标编译过，
    // 静态检查（checkKmpPurity）挡不住未知 API。wasmJs 同为非 JVM 目标
    //（没有 kotlin.jvm.* 默认导入），能拦 @Volatile / synchronized /
    // Dispatchers.IO 这类「JVM 全绿但 Native 编不过」的污染。
    //
    // 为什么不是 linuxX64：实测证伪。CMP 1.12.0 发布的变体只有
    // android / desktop(jvm) / iosArm64 / iosSimulatorArm64 / js / macosArm64 / wasmJs，
    // 无任何 linux 目标 —— 挂 linuxX64() 会直接依赖解析失败。
    //
    // ⚠ 为什么默认关（实测，与 iOS target 同理）：**只要 wasmJs 目标存在于构建中**，
    // Kotlin/Wasm 插件就会在**配置阶段**为 `kotlinWasmNodeJsSetup` 注册
    // Distributions 仓库，与 settings.gradle.kts 的 FAIL_ON_PROJECT_REPOS 硬冲突：
    //   "repository 'Distributions at https://nodejs.org/dist' was added by unknown code"
    // 配置期失败，连 `--offline` 的 Android 门禁都跑不了；把 EnvSpec.download
    // 置 false 也没用（注册发生在读取 download 之前）。所以只能让目标默认不存在。
    //
    // 用法（首次需联网，且需本机 PATH 中有 Node）：
    //     ./gradlew -Pnexio.wasm=true checkKmpWasmJs
    val enableWasm = (project.findProperty("nexio.wasm") as? String)?.toBoolean() ?: false
    if (enableWasm) {
        wasmJs {
            browser()
        }
    }
    // ── iOS target：**默认关闭**，用 -Pnexio.ios=true 显式开启 ──────────────────
    // 为什么默认关（与 :core 同理，已实测）：一旦声明 iOS target，`:miuix:check`
    // 就会去解析 iOS 的 klib 依赖，而门禁是 `--offline` 跑的 —— 本地没有这些
    // 变体的缓存，直接失败，把「Android 零回归」这条红线弄脏。
    //
    // 已实测（2026-10-10，Windows 上纯依赖解析，不需 macOS）：
    // 声明 iosArm64() 后 `:miuix:dependencies --configuration iosArm64CompileKlibraries`
    // **全链解析成功** —— 下面这 5 个官方 artifact 都有 -iosarm64 变体：
    //   miuix-squircle / miuix-icons / miuix-blur / miuix-preference / miuix-navigation3-ui
    // 即风险 R6「Miuix 是 fork 的 -android 变体会阻断跨平台」**就此排除**。
    // 只需要本模块已 fork 的 miuix-ui 在 iOS 侧有 skikoMain 的 actual 兜底。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
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
        // jvm 复用 skikoMain（Skia 实现）。用 srcDir 而不是拷贝一份，
        // 同一份 SkSL 代码跨 Skia 后端共享（wasmJs / iosMain 见下方条件块）。
        jvmMain {
            kotlin.srcDir("src/skikoMain/kotlin")
        }
        // 必须条件式：wasmJs / iOS target 默认关闭，此时这些源集不存在，
        // 无条件引用会配置失败（Unresolved reference 'wasmJsMain' / 'iosMain'）。
        if (enableWasm) {
            wasmJsMain {
                kotlin.srcDir("src/skikoMain/kotlin")
            }
        }
        if (enableIos) {
            iosMain {
                kotlin.srcDir("src/skikoMain/kotlin")
            }
        }
    }
}
