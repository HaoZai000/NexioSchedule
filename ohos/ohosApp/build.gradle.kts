// 鸿蒙端构建（独立于根构建）。版本线 CPF-KMP-CMP 1.2.0 批次：
// Kotlin 2.2.21-1.2.0-04 / Compose 1.9.2-1.2.0-08 / Skiko 0.9.22.2-1.2.0-10（由 compose 传递）。
// 三者必须与 ArkTS 侧 HAR 同步升降，不一致运行时黑屏。

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    // 2.2.1-1.1.0-01 是唯一发布了 ohos 变体的线；主构建的 2.3.2 没有。
    id("app.cash.sqldelight")
}

sqldelight {
    // 必须关：默认会给 native 产物加 -lsqlite3，鸿蒙 sysroot 里没有 sqlite。
    linkSqlite.set(false)
    databases {
        create("NexioDatabase") {
            packageName.set("com.haooz.chedule.data.db")
            // 直接指向 :core 的 .sq，不复制
            srcDirs.setFrom(rootProject.file("../core/src/commonMain/sqldelight"))
            // 鸿蒙线没有历史基线库，迁移正确性由主构建的 verifyMigrations 守
            verifyMigrations.set(false)
        }
    }
}

// 上游 C++ 宿主胶水（编法见 third_party/compose_arkui_utils/build.sh）
val arkuiUtilsLib = rootProject.file("third_party/compose_arkui_utils/lib/arm64-v8a/libcompose_arkui_utils.a")
require(arkuiUtilsLib.isFile) {
    "缺少 $arkuiUtilsLib —— 先跑 ohos/third_party/compose_arkui_utils/build.sh arm64-v8a"
}

// 生成类必须与主构建同名同包，资源目录也指向 ui-shared 那一份。
compose.resources {
    packageOfResClass = "com.haooz.chedule.uishared.resources"
    publicResClass = true
    customDirectory("commonMain", provider { layout.projectDirectory.dir("../../ui-shared/src/commonMain/composeResources") })
}

kotlin {
    // 不能声明 jvm()：适配版只发布 AndroidJVM 与 native 变体，加 jvm 会去要 ui-desktop。

    // 真机与 Apple Silicon 上的模拟器都是 arm64
    ohosArm64 {
        binaries {
            sharedLib {
                baseName = "nexioohos"
                // 与上游 compose_arkui_utils.def 一致：静态库引用到的系统库
                linkerOpts(
                    "-limage_source", "-lpixelmap", "-lpixelmap_ndk.z", "-lnative_buffer",
                    "-ldeviceinfo_ndk.z", "-lhiappevent_ndk.z", "-lnative_drawing", "-lnative_window",
                    // OhosAssetReader 读 rawfile 用的 `OH_ResourceManager_*`
                    "-lrawfile.z",
                    // `OH_ResourceManager_GetMediaData` 只在 libohresmgr.so；缺这条时 GOT 槽留脏值，
                    // 首次调用跳到链接期地址（SIGSEGV，pc 落在 .plt）。
                    "-lohresmgr"
                )
                // sqlite3 静态库：只给 `-L`，native-driver 的 cinterop 自带 -lsqlite3，重复会 duplicate symbol
                linkerOpts(
                    "-L${rootProject.file("third_party/sqlite/lib/arm64-v8a").absolutePath}"
                )
                // libskikobridge 进 DT_NEEDED：compose_arkui_utils 的 C++ 通过 GOT 调 skikobridge_*
                linkerOpts(
                    "-L${rootProject.file("third_party/compose_arkui_utils/skikobridge/lib/arm64-v8a").absolutePath}",
                    "-lskikobridge"
                )
                // 诊断开关，平时关：打开后未定义符号在加载期就报名字，但只要还剩一个 App 就起不来。
                // linkerOpts("-z", "now")
            }
        }

    }

    sourceSets {
        // ohos 分组不在默认层级模板里，手工建
        val ohosMain by creating { dependsOn(commonMain.get()) }
        ohosArm64Main { dependsOn(ohosMain) }

        // C ABI 转发层必须编进本模块：库 klib 里的 @CName 不算导出根，会被 DCE 掉。
        ohosMain.kotlin.srcDir(rootProject.file("third_party/compose_export/commonMain"))
        ohosMain.kotlin.srcDir(rootProject.file("third_party/compose_export/ohosMain"))

        // 与主构建同一份源码，这里再编一次成 ohosArm64 klib。
        // expect 进 commonMain、actual 进 ohosMain，混在一起会报同一模块内 expect/actual。
        // -Pnexio.ohos.shared=false 可临时关掉。
        if ((project.findProperty("nexio.ohos.shared") as? String)?.toBoolean() != false) {
            getByName("commonMain").kotlin.srcDir(rootProject.file("../ui-shared/src/portable/kotlin"))
            getByName("commonMain").kotlin.srcDir(rootProject.file("../ui-shared/src/commonMain/kotlin"))
            getByName("commonMain").kotlin.srcDir(rootProject.file("../core/src/commonMain/kotlin"))
            // 需要被共享代码看见的平台实现只能进 commonMain 源根
            getByName("commonMain").kotlin.srcDir("src/ohosSharedCommon/kotlin")
            ohosMain.kotlin.srcDir("src/ohosSharedCore/kotlin")
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // 共享页面用的 Res / painterResource
            api(compose.components.resources)
            // 版本都取 1.2.0 批次里发布了 ohos 变体的那条线，写上游版本会解析失败
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-1.1.0-02")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2-1.1.0-02")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.1-1.1.0-02")
            implementation("app.cash.sqldelight:runtime:2.2.1-1.1.0-01")
            implementation("app.cash.sqldelight:native-driver:2.2.1-1.1.0-01")
            // 液态玻璃与 miuix 的 ohos 变体（玻璃在鸿蒙上降级为纯色，API 面与 Android 一致）
            api(project(":sharedBackdrop"))
            api(project(":sharedMiuix"))
        }
    }

    // 转发层要 opt-in 的内部 API。必须挂在源集上：languageSettings 只对编译该源集自身生效。
    sourceSets.getByName("ohosMain").languageSettings.apply {
        optIn("androidx.compose.ui.InternalComposeUiApi")
        optIn("androidx.compose.ui.annotation.InternalComposeApi")
        optIn("kotlin.experimental.ExperimentalNativeApi")
        optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        // kotlinx-datetime 0.7.x 把 Clock/Instant 挪进了 kotlin.time（实验 API）
        "-opt-in=kotlin.time.ExperimentalTime",
        "-opt-in=androidx.compose.ui.InternalComposeUiApi",
        "-opt-in=androidx.compose.ui.annotation.InternalComposeApi",
        "-opt-in=kotlin.experimental.ExperimentalNativeApi",
        "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
        // 静态库整包塞进 .so；只能走编译任务，NativeBinary.freeCompilerArgs 不生效。
        "-include-binary", arkuiUtilsLib.absolutePath
    )
}

// compose resources → HAP rawfile：路径必须带 package 一层，少一层打开页面即闪退。
// hvigor 只认 harmonyApp/entry/src/main/resources/rawfile，所以由 Gradle 拷。
val syncComposeResources = tasks.register<Copy>("syncComposeResourcesToHarmony") {
    group = "compose resources"
    description = "把 compose resources 拷进 HAP 的 rawfile（带 package 目录层）"
    dependsOn("prepareComposeResourcesTaskForCommonMain")
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources")) {
        into("com.haooz.chedule.uishared.resources")
    }
    into(rootProject.file("../harmonyApp/entry/src/main/resources/rawfile/composeResources"))
}

// 产物同步：Kotlin 编出来的 .so 交给 ArkTS 宿主，谁生成谁负责搬。
val publishToHarmonyApp by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("bin/ohosArm64/debugShared")) {
        include("libnexioohos.so")
    }
    into(rootProject.file("../harmonyApp/entry/libs/arm64-v8a"))
}

tasks.matching { it.name == "linkDebugSharedOhosArm64" }.configureEach {
    finalizedBy(publishToHarmonyApp)
}

// 链接完顺手把 compose resources 也搬过去
publishToHarmonyApp.configure {
    finalizedBy(syncComposeResources)
}
