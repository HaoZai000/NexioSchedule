// 鸿蒙端独立构建：CMP 版本与主构建不同线，一个 Gradle 构建只能解析一套 Compose 制品。

pluginManagement {
    repositories {
        // CPF-KMP-CMP 定制版 Kotlin / Compose / skiko / kotlinx；必须最前，
        // 上游同名 artifact 没有 ohos 变体，先命中就错了。
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
// :backdrop / :miuix 的 ohosArm64 变体，源码仍是主仓库那一份
include(":sharedBackdrop")
include(":sharedMiuix")
