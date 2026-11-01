// 鸿蒙端构建脚本（独立于根构建）。
//
// 工具链版本线：CPF-KMP-CMP **1.2.0 批次**（2026-11-01 实测确认，别改成 0.1.0 批次）：
//   Kotlin  2.2.21-1.2.0-04
//   Compose 1.9.2-1.2.0-08
//   Skiko   0.9.22.2-1.2.0
//   Gradle  8.14.1 + JDK 21
// 依据：ArkTS 侧 HAR @cpf-kmp-cmp/compose 只发布到 1.9.2-1.2.0，且其 CHANGELOG 明确
// 「版本对齐 CPF-KMP-CMP 1.2.0 批次（配套 Kotlin 2.2.21-1.2.0 / Skiko 0.9.22.2-1.2.0 /
// CMP 1.9.2-1.2.0）」。Maven 上更新的 1.12.0-0.1.0-15 是预发批次，宿主包没发，接不上。
// 升级时三个版本必须一起动，且要与 ArkTS 侧 `@cpf-kmp-cmp/compose` HAR 的版本对齐 ——
// HAR 负责把 Compose 的绘制接到 ArkUI 上，两边版本不一致会在运行时（不是编译时）黑屏。

plugins {
    kotlin("multiplatform") version "2.2.21-1.2.0-04"
    id("org.jetbrains.compose") version "1.9.2-1.2.0-08"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21-1.2.0-04"
}

// ── 上游 C++ 宿主胶水（compose_arkui_utils）──────────────────────────────────
// 45 个 androidx_compose_ui_arkui_ArkUIViewController_* 符号由这份 C++ 编出来。
// 编法：ohos/third_party/compose_arkui_utils/build.sh arm64-v8a
val arkuiUtilsLib = rootProject.file("third_party/compose_arkui_utils/lib/arm64-v8a/libcompose_arkui_utils.a")
require(arkuiUtilsLib.isFile) {
    "缺少 $arkuiUtilsLib —— 先跑 ohos/third_party/compose_arkui_utils/build.sh arm64-v8a"
}

kotlin {
    // ⚠ 不能声明 jvm()：适配版只发布 AndroidJVM 与 native(ohos/ios) 变体，
    // 从不发布 jvm(desktop) 制品 —— 加了 jvm 目标会去要 ui-desktop，直接解析失败（已实测）。
    // 想在本机看排版，用上游 CMP 起一个临时工程指到同一个源文件，别往这里加。

    // 真机 + 模拟器都是 arm64（Apple Silicon 上的鸿蒙模拟器同样走 arm64）
    ohosArm64 {
        binaries {
            sharedLib {
                baseName = "nexioohos"
                // 与上游 compose_arkui_utils.def 的 linkerOpts 一致：静态库引用到的系统库
                linkerOpts(
                    "-limage_source", "-lpixelmap", "-lpixelmap_ndk.z", "-lnative_buffer",
                    "-ldeviceinfo_ndk.z", "-lhiappevent_ndk.z", "-lnative_drawing", "-lnative_window"
                )
                // ⚠ 必须显式链 libskikobridge.so：**只为让它进 DT_NEEDED**。
                // compose_arkui_utils 的 C++ 通过 GOT 调 skikobridge_* 一组 C 函数，
                // 实现在 HAR 带的 libskikobridge.so 里。少了这条 DT_NEEDED，运行时 ld.so
                // 不会去那个库找符号，槽位被填成 0 —— 实测崩在
                // skiko#get-currentSystemTheme → syncSystemThemeState（NULL 解引用 @0x0）。
                linkerOpts(
                    "-L${rootProject.file("third_party/compose_arkui_utils/skikobridge/lib/arm64-v8a").absolutePath}",
                    "-lskikobridge"
                )
            }
        }

    }

    sourceSets {
        // ohos 分组不在默认层级模板里，必须手工建出来再让目标源集依赖它
        val ohosMain by creating { dependsOn(commonMain.get()) }
        ohosArm64Main { dependsOn(ohosMain) }

        // ── C ABI 转发层：编进我们自己的模块，不能靠 Maven 依赖 ────────────────
        // `androidx_compose_ui_arkui_ArkUIViewController_*` 这 44 个 C 符号由
        // compose/export/export 模块用 @CName 提供（转发给 ui 里的 _ArkUIViewController_*）。
        //
        // 只加 `implementation("org.jetbrains.compose.export:export:...")` 没用 —— 实测该 klib
        // 确实进了链接（libraries 里有 export-ohosArm64Main-*.klib），但那些 @CName 函数被
        // DCE 掉了：库 klib 里的 @CName 不会被当成本模块的导出根。结果 libnexioohos.so 里
        // 这 44 个符号仍是 U，宿主一建 controller 就跳空地址。
        // 把它们编进当前模块即可 —— 本模块的 @CName 才是真正的导出根。
        ohosMain.kotlin.srcDir(rootProject.file("third_party/compose_export/commonMain"))
        ohosMain.kotlin.srcDir(rootProject.file("third_party/compose_export/ohosMain"))

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }

    // 转发层要 opt-in 的内部 API（与上游 export 模块的 languageSettings 一致）。
    // 注意必须落到**编译任务**上：languageSettings 只对「编译该源集自己的 klib」生效，
    // ohosMain 的源码是和 ohosArm64Main 一起编的，挂在 ohosMain 上不生效（实测报一堆 opt-in 错误）。
    sourceSets.getByName("ohosMain").languageSettings.apply {
        optIn("androidx.compose.ui.InternalComposeUiApi")
        optIn("androidx.compose.ui.annotation.InternalComposeApi")
        optIn("kotlin.experimental.ExperimentalNativeApi")
        optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        "-opt-in=androidx.compose.ui.InternalComposeUiApi",
        "-opt-in=androidx.compose.ui.annotation.InternalComposeApi",
        "-opt-in=kotlin.experimental.ExperimentalNativeApi",
        "-opt-in=kotlinx.cinterop.ExperimentalForeignApi"
    )
}

// -include-binary 必须挂在**链接任务**上（KotlinNativeLink），不是编译任务：
// 它让 link 阶段把这个静态库整包塞进 .so，静态库里未被引用的对象才会进来。
// -include-binary 要让 konanc 在编译期就把静态库挂进产物；
// 任务级入口是 KotlinNativeCompile.compilerOptions（NativeBinary.freeCompilerArgs 实测不生效）。
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll("-include-binary", arkuiUtilsLib.absolutePath)
}

// ── 产物同步：把 Kotlin 编出来的 .so 交给 ArkTS 宿主 ────────────────────────
// 官方示例里这一步叫 publishDebugBinariesToHarmonyApp，就是 hvigor 侧的 Copy 任务。
// 放在这里而不是 hvigorfile，是因为产物归 Kotlin 构建所有：谁生成谁负责搬，
// 免得「忘了拷」被误判成「Compose 没生效」。
val publishToHarmonyApp by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("bin/ohosArm64/debugShared")) {
        include("libnexioohos.so")
    }
    into(rootProject.file("../harmonyApp/entry/libs/arm64-v8a"))
}

tasks.matching { it.name == "linkDebugSharedOhosArm64" }.configureEach {
    finalizedBy(publishToHarmonyApp)
}
