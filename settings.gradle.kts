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

// 柔光玻璃是独立仓库（HaoZai000/softglass）。本地存在源码时用复合构建直接编源码，
// 改库即改即测；没有源码（CI / 他人 clone）时才回落到 JitPack 坐标。
// 复合构建按 group:name 精确替换，而 JitPack 给的是 com.github.HaoZai000 前缀，
// 与库自身的 group 不同，所以必须显式声明 substitution。
val softglassSource = file("../softglass")
if (softglassSource.resolve("build.gradle.kts").exists()) {
    includeBuild(softglassSource) {
        dependencySubstitution {
            substitute(module("com.github.HaoZai000.softglass:softglass")).using(project(":"))
        }
    }
}
include(":androidApp")
include(":core")
include(":miuix")
include(":ui-shared")
include(":desktopApp")
 

