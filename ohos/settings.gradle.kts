// Nexio课程表 - 鸿蒙端（KMP/CMP）独立构建
//
// 为什么是独立构建而不是主构建加一个 target：
// Compose 的 UI 制品带平台渲染实现，鸿蒙线的 CMP 版本（1.9.2-1.2.0）与主构建在用的
// 1.12.0 不是同一条版本线，而一个 Gradle 构建只能解析一套 Compose 制品。
// 硬塞进主构建的结果是：要么 Android/iOS 降到 1.9.2（miuix 0.9.4、Material3 1.5 全得跟着降），
// 要么鸿蒙端拿不到 klib。取舍写在 docs/HARMONY_PORT_PLAN.md。

pluginManagement {
    repositories {
        // CPF-KMP-CMP 定制版 Kotlin / Compose / skiko / kotlinx 都在这个仓。
        // 必须排在最前：同名 artifact 的上游版本没有 ohos 变体，先命中会上游。
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}

rootProject.name = "Nexio-ohos"
include(":ohosApp")
