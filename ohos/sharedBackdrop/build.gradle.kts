// :backdrop 的 ohosArm64 变体，源码仍是主仓库那一份（srcDir）。

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    ohosArm64()

    sourceSets {
        val ohosMain by creating { dependsOn(commonMain.get()) }
        ohosArm64Main { dependsOn(ohosMain) }

        commonMain {
            kotlin.srcDir(rootProject.file("../backdrop/src/commonMain/kotlin"))
            // kyant shapes：仓库里没有 ohos 制品，用 vendoring 的上游源码
            kotlin.srcDir(rootProject.file("third_party/kyant-shapes/commonMain"))
            // org.jetbrains:annotations 只有 JVM 制品，这里用同名空注解桩
            kotlin.srcDir(rootProject.file("third_party/jetbrains-annotations-stub/commonMain"))
            dependencies {
                implementation(compose.foundation)
                implementation(compose.ui)
            }
        }
        // 不能用 skikoMain：ui-ohos klib 没公开 asComposeShader，玻璃退化为「表面色 + 描边」。
        ohosMain {
            kotlin.srcDir(rootProject.file("../backdrop/src/ohosMain/kotlin"))
        }
    }
}
