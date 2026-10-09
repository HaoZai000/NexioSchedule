// Nexio课程表 - 跨平台共享模块（KMP）
//
// 迁移阶段 1：先只开 android + jvm，验证骨架能编译。
// iOS 目标等这一版跑通后再追加（Windows 上无法编译 Kotlin/Native，先不加噪音）。
//
// AGP 9 起的 KMP 写法与旧版不同：
//   - 插件是 com.android.kotlin.multiplatform.library，不是 com.android.library
//   - Android 目标配置写在 kotlin { android { ... } } 里，不是顶层的 android { }

import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.core"
        compileSdk = 37
        minSdk = 26
        // 刻意**不**开 withHostTest {}：
        // 试过，会让 commonTest 也跑在 Android 宿主机测试上，而那里的 android.jar 是桩实现
        // —— `Build.MODEL` 等字段为 null，于是 currentDeviceInfo() 直接崩，
        // PlatformInfoTest / StatsReporterPayloadTest 共 8 个用例失败。
        // commonTest 的「平台中立性」已由 compileTestKotlinLinuxX64 编译门禁保证
        //（那正是原警告想防的问题：test 里混入 java.io.IOException / String.toByteArray 之类）。
    }
    jvm()
    // 仅作**编译门禁**：Windows 上无法编译 iosArm64/iosSimulatorArm64，
    // 而 linuxX64 同为 Kotlin/Native 目标 —— 同样没有 `kotlin.jvm.*` 默认导入，
    // 因此能拦下 @Volatile / synchronized / System.currentTimeMillis / String.format
    // 这类「Android/JVM 全绿但 iOS 编不过」的 commonMain 污染。
    // 不产出发布物，只跑 compileKotlinLinuxX64。
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // java.time 的跨平台替代：LocalDate / LocalDateTime / Instant 等。
            // 必须用 api —— LocalDate 出现在 :core 的公开签名里（Course / HolidayEntry /
            // TeachingWeekReorganization 等），implementation 不会传递给 :app，:app 侧会
            // 报 "Cannot access class kotlinx.datetime.LocalDate"。
            api(libs.kotlinx.datetime)
            // 协程：StateFlow 出现在公开签名里（PrivacyConsent.consented），
            // 与 kotlinx-datetime 同理必须 api，否则 :app 侧报
            // "Cannot access class kotlinx.coroutines.flow.StateFlow"。
            api(libs.kotlinx.coroutines.core)
            // JSON：替代 org.json。api —— JsonSupport 的扩展函数挂在
            // kotlinx.serialization.json.JsonObject 上，类型会出现在 :app 侧调用点。
            api(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // Android 侧的网络实现仍走 OkHttp —— 与迁移前完全同一套引擎、同一套超时语义。
            // 见 HttpService.kt 的说明：刻意不引入 Ktor，避免连带把全 app 的
            // kotlinx-coroutines 从 1.9.0 顶到 1.10+（实测 Ktor 3.x 都会）。
            implementation(libs.okhttp)
        }

        // round-trip 回归测试跑在 commonTest：KeyValueStore 的读写语义必须在
        // 所有平台一致，用 InMemory 实现即可验证，不需要 Android 设备。
        commonTest.dependencies {
            implementation(kotlin("test"))
            // runTest：HttpService 是 suspend 接口，契约测试需要协程测试运行时。
            implementation(libs.kotlinx.coroutines.test)
        }

        // 仅 JVM 测试用的差分基准：把**真实 Gson** 拉进来对拍 ScheduleCodec
        //（R2 迁移要求「换了序列化实现后输出与 Gson 一致」，光靠手写字面量不够）。
        // 只影响测试编译，不进任何产物，也不给 :app 增加依赖。
        jvmTest.dependencies {
            implementation(libs.gson)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
