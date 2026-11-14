import org.gradle.api.artifacts.ModuleDependency
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesProvider = providers.fileContents(
    rootProject.layout.projectDirectory.file("keystore.properties"),
).asText.map { text -> Properties().apply { text.reader().use { load(it) } } }

fun keyMaterial(vararg names: String): String? =
    names.asSequence().mapNotNull { name ->
        (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
            ?: System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: keystorePropertiesProvider.orNull?.getProperty(name)?.takeIf { it.isNotBlank() }
    }.firstOrNull()

// 本仓库沿用自备的 nexio-release.jks + keystore.properties（存的是 storePassword / keyAlias / keyPassword 旧键名）
val signingStoreFile = (keyMaterial("RELEASE_STORE_FILE") ?: "nexio-release.jks")
    .let { rootProject.file(it) }?.takeIf { it.isFile }
val hasSigningKey = signingStoreFile != null

android {
    namespace = "com.haooz.chedule"
    compileSdk = 37

    defaultConfig {
        // 允许 -PappId=xxx 覆盖包名，默认保持上游的 com.haooz.chedule
        applicationId = (findProperty("appId") as String?)?.takeIf { it.isNotBlank() } ?: "com.haooz.chedule"
        minSdk = 26
        targetSdk = 37
        versionCode = 171
        versionName = "1.7.1-1115"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        // 只在钥匙存在时创建，否则 assembleRelease 会因 storeFile 为空失败
        if (hasSigningKey) {
            create("haooz") {
                storeFile = signingStoreFile
                storePassword = keyMaterial("RELEASE_STORE_PASSWORD", "storePassword")
                keyAlias = keyMaterial("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = keyMaterial("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("haooz")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // 与 release 同签名，才能 adb install -r 覆盖安装、保留 data
        debug {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("haooz")
        }
    }
    packaging {
        dex {
            useLegacyPackaging = true
        }
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

// miuix-ui 已 fork 到 :miuix，排除传递依赖里的那份避免 R8 重复定义
configurations.all {
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui-android")
    // 圆角已统一到 :softglass，squircle 只被 navigation3 传递进来
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-squircle")
}

dependencies {
    implementation(project(":core"))

    implementation(project(":ui-shared"))

    implementation("com.github.HaoZai000.softglass:softglass:0.1.1")

    implementation(project(":miuix"))

    // 小米穿戴（手表 interconnect）
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // 统一管理所有 Compose 库版本
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // 桌面版视口必须在页面脚本前注入
    implementation(libs.androidx.webkit)

    // 每个子库都要 exclude 官方 miuix-ui：与本 fork 同包同名，Android 会 NoSuchMethodError，Native 链接期报错
    val excludeOfficialMiuixUi: ModuleDependency.() -> Unit = {
        exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
    }
    implementation(libs.miuix.icons) { excludeOfficialMiuixUi() }
    implementation(libs.miuix.blur) { excludeOfficialMiuixUi() }
    implementation(libs.miuix.navigation3) {
        excludeOfficialMiuixUi()
        exclude(group = "top.yukonga.miuix.kmp", module = "miuix-squircle")
    }

    // :miuix 的 androidMain 需要（返回键处理）
    implementation(libs.navigationevent.compose)

    // AGSL 运行时注解 / Monet 取色
    implementation("org.jetbrains:annotations:26.1.0")
    implementation(libs.materialKolor.utilities)

    implementation(libs.gson)

    // Shizuku 运行时服务调用
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.okhttp)

    // 用户首页时间轴的液态玻璃（fork 自 kyant0:backdrop，与上游 softglass 并存）
    implementation(project(":backdrop"))

    // Layout Inspector
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Release 构建时拉最新 school_index.pb 进 assets（不缓存）
tasks.register<DefaultTask>("downloadEduIndex") {
    group = "eduimport"
    description = "每次 Release 构建拉取最新 school_index.pb 到 assets/eduloader"
    val targetLocation = project.layout.projectDirectory.dir("src/main/assets/eduloader/school_index.pb").asFile
    outputs.upToDateWhen { false } // 每次都重新拉，不缓存
    doLast {
        targetLocation.parentFile?.mkdirs()
        val url = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/index-pb-release/school_index.pb"
        try {
            val connection = (
                    URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "okhttp/4.12.0")
                setRequestProperty("Accept", "*/*")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val contentType = connection.contentType?.lowercase() ?: ""
            if (contentType.contains("text/html")) {
                // 被反爬拦截时 gitee 返回 HTML 挑战页，不能进 assets
                throw RuntimeException("gitee 返回了 HTML 页面（可能被反爬拦截）")
            }
            connection.inputStream.use { inbound ->
                targetLocation.outputStream().use { outbound ->
                    inbound.copyTo(outbound)
                }
            }
            println("[EduIndex] 已拉取最新索引 -> ${targetLocation.absolutePath}")
        } catch (e: Exception) {
            // 失败不阻断构建，保留现有索引
            println("[EduIndex] 索引拉取失败（保留现有内置索引）: ${e.message}")
        }
    }
}

// 仅 Release 打包资产时拉取；configureEach 惰性挂依赖，避免急切解析。
tasks.configureEach {
    if (name == "mergeReleaseAssets") {
        dependsOn("downloadEduIndex")
    }
}


