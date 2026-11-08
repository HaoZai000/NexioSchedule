// 小米风格 UI 组件库（fork 自 miuix-ui 0.9.3，KMP）。源集分工同 :backdrop。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.miuix"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    // iOS target 默认关（-Pnexio.ios=true 开启）：声明后会去解析 iOS klib，而门禁是 --offline 跑的。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        // 模拟器变体也要，否则下游编模拟器解析不到本模块 klib
        iosSimulatorArm64()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            // 液态玻璃：绘制路径与能力探测
            // 连续圆角形状
            api(project(":softglass"))
            // Monet 取色
            api(libs.materialKolor.utilities)
            // AGSL/SkSL 运行时的编译期注解
            api("org.jetbrains:annotations:26.1.0")

            // 子库用官方 artifact，坐标不带 -android 后缀（写死会让 commonMain 编不过）。
            // 每个都要 exclude 官方 miuix-ui：与本 fork 同包同名，Native 链接期报 already bound。
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            // miuix-preference 已 fork 进本模块：官方版 ABI 与本 fork 对不上，iOS 链接报 Undefined symbols。
            // 排除 miuix-squircle：圆角已统一到 :softglass 的 shape 包。
            implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:${libs.versions.miuixNavigation3.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-squircle")
            }
        }
        androidMain.dependencies {
            // SearchBar / BottomSheet 的返回键处理，CMP 无对应库
            implementation(libs.navigationevent.compose)
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
