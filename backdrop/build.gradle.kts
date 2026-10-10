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
    // ── wasmJs 编译门禁：**默认关闭**，用 -Pnexio.wasm=true 显式开启 ───────────
    // 目的：skikoMain 此前只被 jvm 目标复用，从未针对非 JVM 目标编译过，
    // 静态检查（checkKmpPurity）挡不住未知 API。wasmJs 同为非 JVM 目标
    //（没有 kotlin.jvm.* 默认导入），能拦 @Volatile / synchronized /
    // Dispatchers.IO 这类「JVM 全绿但 Native 编不过」的污染。
    //
    // 为什么不是 linuxX64：实测证伪。CMP 1.12.0 发布的变体只有
    // android / desktop(jvm) / iosArm64 / iosSimulatorArm64 / js / macosArm64 / wasmJs，
    // 无任何 linux 目标。挂 linuxX64() 会直接依赖解析失败
    //（Couldn't resolve ... Unresolved platforms: [linuxX64]）。
    // 本机 konan 虽有 x86_64-unknown-linux-gnu-gcc 交叉工具链，但没有可链的 klib 变体。
    //
    // ⚠ 为什么默认关（实测，与 iOS target 同理）：**只要 wasmJs 目标存在于构建中**，
    // Kotlin/Wasm 插件就会在**配置阶段**为 `kotlinWasmNodeJsSetup` 注册
    // Distributions 仓库，与 settings.gradle.kts 的 FAIL_ON_PROJECT_REPOS 硬冲突：
    //   "repository 'Distributions at https://nodejs.org/dist' was added by unknown code"
    // 这是配置期失败，与挂不挂 check 无关 —— 连 `--offline` 的 Android 门禁都跑不了。
    // 把 EnvSpec.download 置 false 也没用（注册发生在读取 download 之前）。
    // 所以只能让目标默认不存在。
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
    // 为什么默认关（与 :core 同理，已实测）：一旦声明 iOS target，
    // `:backdrop:check` / `:miuix:check` 就会去解析 iOS 的 klib 依赖，
    // 而门禁是 `--offline` 跑的 —— 本地没有这些变体的缓存，直接失败，
    // 把「Android 零回归」这条红线弄脏。Windows 上本来也编译不了 iOS。
    //
    // 已验证可用（2026-10-10，Windows 上纯依赖解析，不需 macOS）：
    // 声明 iosArm64() 后 `:miuix:dependencies --configuration iosArm64CompileKlibraries`
    // 全链解析成功 —— 官方 miuix 5 个 artifact **都有 -iosarm64 变体**：
    //   miuix-squircle / miuix-icons / miuix-blur / miuix-preference / miuix-navigation3-ui
    // 风险 R6「Miuix 是 fork 的 -android 变体会阻断跨平台」**就此排除**。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
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
