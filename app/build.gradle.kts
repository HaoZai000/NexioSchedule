// Nexio课程表 - 应用模块构建配置

import java.net.HttpURLConnection
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.haooz.chedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.haooz.chedule"
        minSdk = 31
        targetSdk = 37
        versionCode = 149
        versionName = "1.4.9beta2"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

composeCompiler {
    stabilityConfigurationFiles.set(listOf(project.layout.projectDirectory.file("compose-stability.conf")))
}

// miuix-ui 已 fork 到本地源码，排除传递依赖中的 miuix-ui jar 避免 R8 重复定义
configurations.all {
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui-android")
}

dependencies {
    // ===== AndroidX / Compose 基础 =====
    // Compose BOM：统一管理所有 Compose 库版本
    implementation(platform(libs.androidx.compose.bom))
    // Activity 与 Compose 集成（setContent 入口）
    implementation(libs.androidx.activity.compose)
    // Compose UI 核心运行时
    implementation(libs.androidx.compose.ui)
    // Compose 图形模块（Canvas、绘制等）
    implementation(libs.androidx.compose.ui.graphics)
    // Material3 组件库
    implementation(libs.androidx.compose.material3)
    // AndroidX 核心 KTX 扩展
    implementation(libs.androidx.core.ktx)
    // 生命周期感知型运行时 KTX
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ViewModel 与 Compose 集成
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ===== Activity 嵌入（大屏分屏）=====
    // Jetpack WindowManager：Activity 嵌入核心库
    implementation(libs.androidx.window)
    // App Startup：自动初始化分屏规则
    implementation(libs.androidx.startup.runtime)

    // ===== Miuix UI =====
    // miuix-ui 已 fork 到本地源码，不再使用 jar 依赖
    // 偏好设置组件
    implementation(libs.miuix.preference)
    // 图标资源
    implementation(libs.miuix.icons)
    // Squircle形状支持
    implementation(libs.miuix.squircle)
    // 模糊效果支持
    implementation(libs.miuix.blur)
    // Navigation3 导航组件
    implementation(libs.miuix.navigation3)

    // ===== NavigationEvent =====
    // SearchBar 返回键处理
    implementation(libs.navigationevent.compose)

    // ===== 形状库 =====
    // 形状使用本地 fork 的 capsule 库（ContinuousRoundedRectangle / ContinuousCapsule）
    // Backdrop 模糊背景已 fork 到本地源码，不再使用 jar 依赖
    // 其编译的 AGSL 运行时需要 org.jetbrains 注解（org.intellij.lang.annotations.Language）
    implementation("org.jetbrains:annotations:26.1.0")
    // Material Color（miuix theme 依赖）
    implementation(libs.materialKolor.utilities)

    // ===== 序列化 =====
    // Gson：JSON 序列化/反序列化（课表数据持久化）
    implementation(libs.gson)

    // ===== Shizuku =====
    // Shizuku API：运行时服务调用
    implementation(libs.shizuku.api)
    // Shizuku Provider：进程间通信接入
    implementation(libs.shizuku.provider)

    // ===== 网络 =====
    // OkHttp：HTTP 客户端
    implementation(libs.okhttp)

    // ===== 调试专用 =====
    // Compose UI Tooling：Layout Inspector
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ===== 教务索引内置 =====
// Release 构建时从云端拉取最新 school_index.pb 打包进 assets，使用户首次使用教务导入时
// 无需联网即可获得学校/适配器索引（含 importUrl、脚本路径），进入后脚本仍按需下载。
tasks.register<DefaultTask>("downloadEduIndex") {
    group = "eduimport"
    description = "每次 Release 构建拉取最新 school_index.pb 到 assets/eduloader"
    val targetLocation = project.layout.projectDirectory.dir("src/main/assets/eduloader/school_index.pb").asFile
    outputs.upToDateWhen { false } // 每次 Release 构建都重新拉取，确保内置索引最新
    doLast {
        targetLocation.parentFile?.mkdirs()
        val url = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/index-pb-release/school_index.pb"
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "okhttp/4.12.0")
                setRequestProperty("Accept", "*/*")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val contentType = connection.contentType?.lowercase() ?: ""
            if (contentType.contains("text/html")) {
                throw RuntimeException("gitee 返回了 HTML 页面（可能被反爬拦截）")
            }
            connection.inputStream.use { inbound ->
                targetLocation.outputStream().use { outbound ->
                    inbound.copyTo(outbound)
                }
            }
            println("[EduIndex] 已拉取最新索引 -> ${targetLocation.absolutePath}")
        } catch (e: Exception) {
            // 拉取失败不阻断构建，保留现有内置索引
            println("[EduIndex] 索引拉取失败（保留现有内置索引）: ${e.message}")
        }
    }
}

// 仅 Release 变体打包资产时拉取内嵌索引：Debug 等构建不触发。
// configureEach 惰性挂依赖，规避 release 任务未实例化时的急切解析。
tasks.configureEach {
    if (name == "mergeReleaseAssets") {
        dependsOn("downloadEduIndex")
    }
}


