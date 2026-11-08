// 桌面客户端（Compose Desktop / JVM），与 :androidApp 共用 :ui-shared 与 :core。

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":ui-shared"))
            implementation(project(":core"))
            implementation(project(":miuix"))
            implementation(project(":softglass"))
            // Window / application
            implementation(compose.desktop.currentOs)
            // 桌面端没有 ViewModelStoreOwner，宿主必须自己补
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            // 必须显式声明：:core 的 api 依赖没传到桌面运行时 classpath，缺了会 ClassNotFoundException
            implementation(libs.kotlinx.datetime)
        }
    }
}

// kotlinx-datetime 由根脚本 force 到 0.6.2：桌面依赖树会把它提升到 0.7.1，运行时才炸。

compose.desktop {
    application {
        mainClass = "com.haooz.chedule.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Nexio Schedule"
            packageVersion = "1.0.0"
            description = "Nexio 课程表桌面版"
        }
    }
}
