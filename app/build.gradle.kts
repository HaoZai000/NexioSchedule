// Nexio课程表 - 应用模块构建配置
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.haooz.chedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.haooz.chedule"
        minSdk = 33
        targetSdk = 37
        versionCode = 120
        versionName = "1.2.0-0710"

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
    }
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

    // ===== Miuix UI =====
    // 基础 UI 组件
    implementation(libs.miuix.ui)
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

    // ===== 形状库 =====
    // Kyant0形状支持
    implementation(libs.shapes)
    // Backdrop 模糊背景
    implementation(libs.backdrop)

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

    // ===== Git 操作 =====
    // JGit：纯 Java 实现的 Git 库
    implementation(libs.jgit) {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    // SLF4J API：JGit 日志门面
    implementation(libs.slf4j.api)
    // SLF4J Android：将日志转发到 Android Logcat
    implementation(libs.slf4j.android)

    // ===== 调试专用 =====
    // Compose UI Tooling：Layout Inspector
    debugImplementation(libs.androidx.compose.ui.tooling)
}
