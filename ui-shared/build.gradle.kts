// 共享 UI 模块：平台中立页面在 commonMain，Android 与桌面共用，iOS 走 framework。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// 包名必须显式指定：不配会拿 Gradle 项目名拼，本仓库项目名是中文。
compose.resources {
    packageOfResClass = "com.haooz.chedule.uishared.resources"
    // :androidApp 的页面直接引用 Res，internal 会报无法访问
    publicResClass = true
}

kotlin {
    android {
        namespace = "com.haooz.chedule.uishared"
        compileSdk = 37
        minSdk = 26
        // CMP 1.8.0 起 androidLibrary 必须显式开资源，否则访问 composeResources 抛 MissingResourceException
        androidResources.enable = true
    }
    jvm()
    // iOS target 默认关（-Pnexio.ios=true 开启）：声明后会解析 iOS klib，而门禁是 --offline。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        iosSimulatorArm64()

        // framework 产物：klib 版本冲突只在链接期才合账，得有 link 任务可跑
        iosArm64 { binaries.framework { baseName = "NexioShared"; isStatic = true } }
        iosSimulatorArm64 { binaries.framework { baseName = "NexioShared"; isStatic = true } }
    }

    sourceSets {
        // 鸿蒙构建用 srcDir 指到这里编 ohosArm64，所以只能是同模块的第二个源码根
        // （换新模块会断 internal 可见性）。加文件条件：不碰 android.* / java.*。
        commonMain {
            kotlin.srcDir("src/portable/kotlin")
        }
        // 桌面 / iOS / 鸿蒙共用的 Skiko shader 后端。Android 不用 Skia，单独吃 androidMain。
        val skikoMain by creating {
            dependsOn(commonMain.get())
        }
        getByName("jvmMain").dependsOn(skikoMain)
        if (enableIos) {
            // iosMain 在默认层级里是 lazy 的；显式建出来并把 src/iosMain 的 actual 挂上
            val iosMain = maybeCreate("iosMain").apply { dependsOn(commonMain.get()) }
            iosMain.dependsOn(skikoMain)
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // shader 冒烟测试要 Skia 的运行时编译器（SkSL）。currentOs 会带上当前平台的 skiko
        // native，否则测试里 `RuntimeEffect` 一加载就 LibraryLoadException。
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            // 必须 api：Res / painterResource 被 :androidApp 直接引用
            api(compose.components.resources)
            // 数据层与跨平台抽象
            api(project(":core"))
            // 页面骨架与组件
            api(project(":miuix"))
            // 液态玻璃采样
            api(project(":backdrop"))

            // miuix 子库用不带 -android 的根坐标（写死会让 jvm 编不过），
            // 且必须 exclude 官方 miuix-ui：与 :miuix 的同名 fork 一起进 iOS 链接会 already bound。
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}") {
                exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
            }
            // KMP 版 lifecycle：取 jetbrains 组（转发 androidx 2.9.4），不影响 Android 侧版本
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")
            // viewModel() 工厂在单独 artifact；默认工厂只认无参构造，各 VM 保持无参
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
        }
    }
}
