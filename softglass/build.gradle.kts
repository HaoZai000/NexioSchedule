plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

group = "com.haooz.softglass"
version = "0.1.0"

kotlin {
    android {
        namespace = "com.haooz.softglass"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    val enableIos = (project.findProperty("nexio.ios") as? String)?.toBoolean() ?: false
    if (enableIos) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        val skikoMain by creating {
            dependsOn(commonMain.get())
        }
        getByName("jvmMain").dependsOn(skikoMain)
        if (enableIos) {
            val iosMain = maybeCreate("iosMain").apply { dependsOn(commonMain.get()) }
            iosMain.dependsOn(skikoMain)
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }
    }
}

/**
 * 发布坐标：com.haooz.softglass:softglass:<version>
 *
 * 各 target 的 publication 由 KMP / AGP 自动注册，这里只补 POM 元数据。
 * 本地验证：./gradlew :softglass:publishToMavenLocal
 */
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("SoftGlass")
            description.set("Compose Multiplatform 的柔光玻璃渲染引擎：采样、折射、7 级模糊金字塔与连续圆角几何")
            url.set("https://github.com/HaoZai000/NexioSchedule")
            licenses {
                license {
                    name.set("AGPL-3.0-only")
                    url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                }
            }
            scm {
                url.set("https://github.com/HaoZai000/NexioSchedule")
                connection.set("scm:git:git://github.com/HaoZai000/NexioSchedule.git")
                developerConnection.set("scm:git:ssh://git@github.com/HaoZai000/NexioSchedule.git")
            }
        }
    }
}
