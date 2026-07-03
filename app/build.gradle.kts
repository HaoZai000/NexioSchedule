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
        versionCode = 104
        versionName = "1.0.4-0703"

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
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Miuix UI
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.navigation3)

    // Shapes
    implementation(libs.shapes)

    // Gson for JSON serialization
    implementation(libs.gson)

    // Focus Notification / Super Island
    implementation(libs.focus.api)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // OkHttp for network requests
    implementation(libs.okhttp)

    // JGit for Git operations (script repository)
    implementation(libs.jgit) {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
