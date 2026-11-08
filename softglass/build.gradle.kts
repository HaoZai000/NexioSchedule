plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

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
