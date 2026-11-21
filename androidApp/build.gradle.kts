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

fun keyMaterial(name: String): String? =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: keystorePropertiesProvider.orNull?.getProperty(name)?.takeIf { it.isNotBlank() }

val signingStoreFile = keyMaterial("RELEASE_STORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.isFile }
val hasSigningKey = signingStoreFile != null

android {
    namespace = "com.haooz.chedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.haooz.chedule"
        minSdk = 26
        targetSdk = 37
        versionCode = 172
        versionName = "1.7.2-1116"
        buildConfigField("int", "MCP_LOCAL_PORT", "26611")

        ndk {
            abiFilters += listOf("arm64-v8a")
            // 模拟器联调需要 x86_64；release 由 CI 过滤，不进正式包
            if (gradle.startParameter.taskNames.any {
                    it.contains("Debug", ignoreCase = true) || it.contains("McpTest", ignoreCase = true)
                }) {
                abiFilters += listOf("x86_64")
            }
        }
    }

    // 可选的本地共存测试构建：需要自备被 .gitignore 排除的 keystore/nexio-mcp-test.p12，
    // 钥匙不存在时整个 buildType 不注册，不影响 debug/release 构建。
    val hasMcpTestKey = rootProject.file("keystore/nexio-mcp-test.p12").exists()

    signingConfigs {
        // 只在钥匙存在时创建，否则 assembleRelease 会因 storeFile 为空失败
        if (hasSigningKey) {
            create("haooz") {
                storeFile = signingStoreFile
                storePassword = keyMaterial("RELEASE_STORE_PASSWORD")
                keyAlias = keyMaterial("RELEASE_KEY_ALIAS")
                keyPassword = keyMaterial("RELEASE_KEY_PASSWORD")
            }
        }
        // 专用本地测试钥匙不进入版本控制；不继承正式版签名。
        if (hasMcpTestKey) {
            create("mcpTest") {
                storeFile = rootProject.file("keystore/nexio-mcp-test.p12")
                storeType = "PKCS12"
                storePassword = "nexio-local-test"
                keyAlias = "nexio-mcp-test"
                keyPassword = "nexio-local-test"
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
        if (hasMcpTestKey) {
            create("mcpTest") {
                initWith(getByName("debug"))
                applicationIdSuffix = ".mcptest"
                versionNameSuffix = "-mcp-test"
                signingConfig = signingConfigs.getByName("mcpTest")
                matchingFallbacks += listOf("debug")
                buildConfigField("int", "MCP_LOCAL_PORT", "26612")
            }
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

    implementation("com.github.HaoZai000.softglass:softglass:0.1.2")

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
    testImplementation("junit:junit:4.13.2")

    // Layout Inspector
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Release 构建时拉最新内置索引进 assets（不缓存）：拾光 pb + edu-ai-dock json
tasks.register<DefaultTask>("downloadEduIndex") {
    group = "eduimport"
    description = "每次 Release 构建拉取最新 school_index.pb 与 school_index_ai.json 到 assets/eduloader"
    val assetsDir = project.layout.projectDirectory.dir("src/main/assets/eduloader").asFile
    outputs.upToDateWhen { false } // 每次都重新拉，不缓存
    doLast {
        assetsDir.mkdirs()

        // 按顺序尝试多个源；失败不阻断构建，保留现有内置索引
        fun fetch(fileName: String, urls: List<String>) {
            val target = assetsDir.resolve(fileName)
            for (url in urls) {
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
                        throw RuntimeException("返回了 HTML 页面（可能被反爬拦截）")
                    }
                    connection.inputStream.use { inbound ->
                        target.outputStream().use { outbound ->
                            inbound.copyTo(outbound)
                        }
                    }
                    println("[EduIndex] 已拉取 $fileName -> ${target.absolutePath}")
                    return
                } catch (e: Exception) {
                    println("[EduIndex] $fileName 拉取失败（$url）: ${e.message}")
                }
            }
            println("[EduIndex] $fileName 全部源失败（保留现有内置索引）")
        }

        fetch(
            "school_index.pb",
            listOf("https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/index-pb-release/school_index.pb"),
        )
        fetch(
            "school_index_ai.json",
            listOf(
                "https://gitee.com/com_haooz_account/edu-ai-dock/raw/main/index/school_index.json",
                "https://raw.githubusercontent.com/HaoZai000/edu-ai-dock/main/index/school_index.json",
            ),
        )
    }
}

// 仅 Release 打包资产时拉取；configureEach 惰性挂依赖，避免急切解析。
tasks.configureEach {
    if (name == "mergeReleaseAssets") {
        dependsOn("downloadEduIndex")
    }
}


