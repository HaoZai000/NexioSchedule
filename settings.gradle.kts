pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 柔光玻璃库（独立仓库，tag 触发 JitPack 构建）
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.HaoZai000.*") }
        }
    }
}

rootProject.name = "Nexio课程表"

// 柔光玻璃是独立仓库（HaoZai000/SoftGlass）。本地存在源码时用复合构建直接编源码，
// 改库即改即测；没有源码（CI / 他人 clone）时才回落到 JitPack 坐标。
// 复合构建按 group:name 精确替换，而 JitPack 给的是 com.github.HaoZai000 前缀，
// 与库自身的 group 不同，所以必须显式声明 substitution。
val softglassSource = file("../softglass")
if (softglassSource.resolve("build.gradle.kts").exists()) {
    includeBuild(softglassSource) {
        dependencySubstitution {
            substitute(module("com.github.HaoZai000.SoftGlass:softglass")).using(project(":"))
        }
    }
}

// NexioUI 是独立开源控件库。本地存在源码时走复合构建，改库即改即测；
// 后续发布后可改为 Maven 坐标。
val nexiouiSource = file("../nexioui")
if (nexiouiSource.resolve("build.gradle.kts").exists()) {
    includeBuild(nexiouiSource) {
        dependencySubstitution {
            substitute(module("com.github.HaoZai000.NexioUI:nexioui")).using(project(":nexioui"))
        }
    }
}
include(":androidApp")
include(":core")
include(":ui-shared")
include(":desktopApp")

