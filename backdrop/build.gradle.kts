// Nexio课程表 - 液态玻璃效果库（fork 自 io.github.kyant0:backdrop 2.0.1）
// fork 而非依赖 artifact：项目对 backdrop 有 671 行定制（DrawBackdropModifier / LayerBackdropModifier / RuntimeShader 等 14 个文件），定制住在源码里，才能分别落到 KMP 的平台源集上。
// 源集分工：commonMain 上游 2.0.1 跨平台逻辑 + 定制；androidMain Android actual（RenderEffect / AGSL）；skikoMain iOS/Desktop/Web actual（Skia RuntimeEffect + SkSL）。

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
    // ── iOS target：**默认关闭**，-Pnexio.ios=true 开启 ──────────────────────
    // 同理：声明 iOS target 会让 `check` 去解析 iOS 的 klib 依赖，而门禁是 --offline 跑的，本地没这些变体缓存，直接失败。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        // 模拟器变体：日常开发跑模拟器，只声明 iosArm64 会让下游解析不到。
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            // 上游用来做形状运算的库，本项目 ui/effects/shapes 与之同源
            api(libs.kyant.shapes)
            // @Language("AGSL") 注解（RuntimeShader / Shaders 等共 8 处）
            api("org.jetbrains:annotations:26.1.0")
        }
        // jvm 复用 skikoMain（Skia 实现）：用 srcDir 而非拷贝，同一份 SkSL 代码跨 Skia 后端共享。
        jvmMain {
            kotlin.srcDir("src/skikoMain/kotlin")
        }
        // 必须条件式：iOS target 默认关闭，无条件引用会配置失败。
        if (enableIos) {
            iosMain {
                kotlin.srcDir("src/skikoMain/kotlin")
            }
        }
    }
}
