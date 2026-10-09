// Nexio课程表 - 跨平台共享模块（KMP）
//
// 迁移阶段 1：先只开 android + jvm，验证骨架能编译。
// iOS 目标等这一版跑通后再追加（Windows 上无法编译 Kotlin/Native，先不加噪音）。
//
// AGP 9 起的 KMP 写法与旧版不同：
//   - 插件是 com.android.kotlin.multiplatform.library，不是 com.android.library
//   - Android 目标配置写在 kotlin { android { ... } } 里，不是顶层的 android { }

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.core"
        compileSdk = 37
        minSdk = 26
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            // java.time 的跨平台替代：LocalDate / LocalDateTime / Instant 等。
            // 必须用 api —— LocalDate 出现在 :core 的公开签名里（Course / HolidayEntry /
            // TeachingWeekReorganization 等），implementation 不会传递给 :app，:app 侧会
            // 报 "Cannot access class kotlinx.datetime.LocalDate"。
            api(libs.kotlinx.datetime)
        }
    }
}
