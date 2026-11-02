// 液态玻璃效果库（fork 自 io.github.kyant0:backdrop 2.0.1）。源集分工：
// commonMain 跨平台逻辑 + 定制；androidMain RenderEffect/AGSL；skikoMain Skia RuntimeEffect + SkSL。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.backdrop"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    // iOS target 默认关（-Pnexio.ios=true 开启）：声明后会去解析 iOS klib，而门禁是 --offline 跑的。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        // 只声明 iosArm64 会让下游解析不到模拟器 klib
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            // 形状运算
            api(libs.kyant.shapes)
            // @Language("AGSL") 注解
            api("org.jetbrains:annotations:26.1.0")
        }
        // jvm 复用 skikoMain，同一份 SkSL 代码跨 Skia 后端共享
        jvmMain {
            kotlin.srcDir("src/skikoMain/kotlin")
        }
        // 必须条件式：iOS target 关闭时 iosMain 源集不存在
        if (enableIos) {
            iosMain {
                kotlin.srcDir("src/skikoMain/kotlin")
            }
        }
    }
}
