// :backdrop 的 ohosArm64 变体。变体在本构建里成型，源码仍是主仓库那一份（srcDir 引过去）。
// 不能给主构建的 :backdrop 加 target：一个 Gradle 构建只能解析一套 Compose 制品。

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
        // 不能用 skikoMain：CPF 的 ui-ohos klib 没公开 asComposeShader / skiaPaint /
        // skiaImageFilter，玻璃退化为「表面色 + 描边」（同 Android API < 31 那条分支）。
        ohosMain {
            kotlin.srcDir(rootProject.file("../backdrop/src/ohosMain/kotlin"))
        }
    }
}
