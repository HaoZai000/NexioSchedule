// 跨平台共享模块（KMP）。

import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("NexioDatabase") {
            packageName.set("com.haooz.chedule.data.db")
            // 迁移基线：verifyMigrations 拿它跑一遍，结果必须与 .sq 的最新 schema 一致。
            // 加新版本时，改 .sq 前先跑 generateCommonMainNexioDatabaseSchema 落基线。
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            // 迁移缺失时编译失败，而不是运行时丢数据。
            verifyMigrations.set(true)
        }
    }
}

kotlin {
    android {
        namespace = "com.haooz.chedule.core"
        compileSdk = 37
        minSdk = 26
        // 宿主机测试：3 个类依赖设备字段/Application 生命周期，在 testAndroidHostTest 里显式排除。
        withHostTest {}
    }
    jvm()
    // iOS target 默认关（-Pnexio.ios=true 开启），开启后需联网构建。
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
        iosTargets.forEach { target ->
            target.compilations.getByName("main") {
                cinterops.create("applesysctl") {
                    defFile = project.file("src/nativeInterop/cinterop/apple-sysctl.def")
                    // 包名必须显式给：默认取 def 文件名，带连字符不合法
                    packageName = "applesysctl"
                }
            }
        }
    }

    sourceSets {
        // Android 与 JVM 桌面共用的实现（java.io.File 系），用 srcDir 共享同一份源码
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
        }
        jvmMain {
            kotlin.srcDir("src/jvmShared/kotlin")
        }

        commonMain.dependencies {
            // 必须 api：LocalDate 出现在公开签名里
            api(libs.kotlinx.datetime)
            // 必须 api：StateFlow 出现在公开签名里
            api(libs.kotlinx.coroutines.core)
            // 必须 api：JsonSupport 的扩展挂在 JsonObject 上，类型出现在下游调用点
            api(libs.kotlinx.serialization.json)
            // 实体表运行时（生成的 NexioDatabase 依赖它）
            implementation(libs.sqldelight.runtime)
        }

        androidMain.dependencies {
            // 网络走 OkHttp；不引 Ktor，它会把 coroutines 顶到 1.10+
            implementation(libs.okhttp)
            implementation(libs.sqldelight.android.driver)
        }

        jvmMain.dependencies {
            // 桌面端用 JDBC 驱动，同一个 .sq schema、同一套查询
            implementation(libs.sqldelight.sqlite.driver)
        }

        // 驱动依赖跟 iOS target 同一个开关
        if (enableIos) {
            iosMain.dependencies {
                implementation(libs.sqldelight.native.driver)
            }
        }

        // KeyValueStore 的读写语义要全平台一致，InMemory 实现即可验证
        commonTest.dependencies {
            implementation(kotlin("test"))
            // HttpService 是 suspend 接口
            implementation(libs.kotlinx.coroutines.test)
        }

        // 仅 JVM 测试：用真 Gson 对拍 ScheduleCodec
        jvmTest.dependencies {
            implementation(libs.gson)
            // 课程行表要跑真 SQL：外键、级联、INSERT OR REPLACE 的顺序，假后端验不出来
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// Android 宿主机测试的排除项
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter {
        excludeTestsMatching("com.haooz.chedule.data.PlatformInfoTest")
        excludeTestsMatching("com.haooz.chedule.data.StatsReporterPayloadTest")
        excludeTestsMatching("com.haooz.chedule.data.DataFetcherLogicTest")
    }
}
