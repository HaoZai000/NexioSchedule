// Nexio课程表 - 顶层构建配置
//
// 除插件声明外，这里还挂了一条 **commonMain/skikoMain 平台纯净度检查**。
// 原因见下面 CheckKmpPurityTask 的注释：这类「Android/JVM 全绿、iOS 编不过」的问题
// 编译器在本机拦不住，只能用静态检查兜底。

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// ─────────────────────────────────────────────────────────────────────────────
// commonMain / skikoMain 平台纯净度检查
//
// 背景（真实教训）：`:core` 此前只有 android + jvm 两个 **JVM** 目标，
// `compileCommonMainKotlinMetadata` 是 SKIPPED —— commonMain 从未被平台中立的 stdlib
// 检查过。于是 5 类 JVM 专有 API 一路绿灯，直到真正编 Kotlin/Native 才全部暴露：
//   @Volatile（靠 JVM 默认导入 kotlin.jvm.* 解析）、synchronized、Dispatchers.IO
//   （在 Native 上是 internal）、System.currentTimeMillis()、String.format()
//
// 各模块的应对不同：
//   :core              → 挂了 linuxX64 目标，由编译器真编译（最强）
//   :backdrop / :miuix → 做不到：Compose Multiplatform **不支持 linuxX64**，
//                        而 iOS 目标需要 macOS 宿主。只能退化为静态检查。
//
// 静态检查不如编译器完备（未知 API 查不出来），但能把已知的坑全拦住，
// 且在任何平台都能跑、无需 Kotlin/Native 工具链。
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 一条禁止规则。[appliesTo] 用于排除文件级的已知例外。
 *
 * 规则设计上的两个坑（都已实测确认，不是推测）：
 *  1. `@Volatile` 只有在**文件未导入** `kotlin.concurrent.Volatile` 时才是问题，
 *     否则是误报 —— 必须做文件级判断。
 *  2. `@JvmInline` / `import kotlin.jvm.JvmInline` 是**合法**的：Kotlin 对 value class
 *     的注解做了特殊处理，实测 linuxX64 能编过。
 */
class Forbidden(
    val name: String,
    val regex: Regex,
    val fix: String,
    val appliesTo: (hasKotlinConcurrentVolatile: Boolean) -> Boolean = { true },
)

abstract class CheckKmpPurityTask : DefaultTask() {

    /** 待扫描的 .kt 文件。 */
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    /** 仓库根路径，仅用于把绝对路径显示成相对路径。 */
    @get:Input
    abstract val rootPath: Property<String>

    @TaskAction
    fun check() {
        val root = rootPath.get()
        val violations = mutableListOf<String>()
        var scanned = 0

        // 必须用 asFileTree 展开目录：ConfigurableFileCollection 里放的是「目录」本身，
        // 直接取 .files 只会拿到目录对象（曾因此扫描到 0 个文件却报通过 —— 假绿）。
        val ktFiles = sources.asFileTree.matching { include("**/*.kt") }.files
            .sortedBy { it.absolutePath }

        for (src in ktFiles) {
            val text = src.readText()
            scanned++
            val hasKotlinConcurrentVolatile = text.contains("import kotlin.concurrent.Volatile")
            val rel = src.absolutePath.removePrefix(root).trimStart('\\', '/')

            text.lines().forEachIndexed { index, line ->
                // 跳过注释行，避免把 KDoc 里的「反例说明」当成违规
                val trimmed = line.trimStart()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }
                for (f in FORBIDDEN) {
                    if (f.appliesTo(hasKotlinConcurrentVolatile) && f.regex.containsMatchIn(line)) {
                        violations += "$rel:${index + 1}  [${f.name}]  ${line.trim()}\n        修法：${f.fix}"
                    }
                }
            }
        }

        if (violations.isEmpty()) {
            logger.lifecycle("checkKmpPurity: 扫描 $scanned 个跨平台源文件，未发现 JVM 专有 API ✅")
        } else {
            throw GradleException(
                "commonMain/skikoMain 存在 ${violations.size} 处 JVM 专有 API，" +
                    "这些代码在 Android/JVM 上能编过但在 iOS(Kotlin/Native) 上会失败：\n" +
                    violations.joinToString("\n"),
            )
        }
    }

    companion object {
        // 放在伴生对象里而不是脚本顶层：避免 @TaskAction 捕获 build script 实例，
        // 否则 Gradle 配置缓存会报 "cannot serialize Gradle script object references"。
        val FORBIDDEN = listOf(
            Forbidden(
                "@Volatile（无限定）",
                Regex("""(?<![\w.])@Volatile\b"""),
                "改用 import kotlin.concurrent.Volatile（JVM 上是 kotlin.jvm.Volatile 的 typealias，行为不变）",
                appliesTo = { hasKotlinConcurrentVolatile -> !hasKotlinConcurrentVolatile },
            ),
            Forbidden(
                "synchronized",
                Regex("""(?<![\w.])synchronized\s*[({]"""),
                "Kotlin/Native 没有 synchronized；读多写少的缓存改用 copy-on-write，或抽 expect/actual",
            ),
            Forbidden(
                "Dispatchers.IO",
                Regex("""Dispatchers\s*\.\s*IO\b"""),
                "在 Kotlin/Native 上是 internal；抽 expect val ioDispatcher（Native 用 Dispatchers.Default）",
            ),
            Forbidden(
                "java.* 导入",
                Regex("""(?m)^\s*import\s+java\.""", RegexOption.MULTILINE),
                "java.* 只存在于 JVM；换 kotlin.* 或抽 expect/actual",
            ),
            Forbidden(
                "kotlin.jvm.* 导入（JvmInline 除外）",
                Regex("""(?m)^\s*import\s+kotlin\.jvm\.(?!JvmInline\b)\w+""", RegexOption.MULTILINE),
                "kotlin.jvm.* 只存在于 JVM；@JvmInline 是唯一例外",
            ),
            Forbidden(
                "System.currentTimeMillis / nanoTime / getProperty",
                Regex("""(?<![\w.])System\s*\.\s*(currentTimeMillis|nanoTime|getProperty|getenv|arraycopy)\b"""),
                "改 kotlinx-datetime 的 Clock.System.now()，或抽 expect/actual",
            ),
            Forbidden(
                "String.format",
                Regex("""(?<![\w.])String\s*\.\s*format\b"""),
                "JVM 专有，且会按 Locale 数码字形输出（阿拉伯语 locale 下 \"08:00\" 变非 ASCII）；改 padStart",
            ),
            Forbidden(
                "java.util.UUID",
                Regex("""java\s*\.\s*util\s*\.\s*UUID"""),
                "用 kotlin.random.Random 自建 UUID v4 格式（见 PlatformInfo.kt 的 randomUuidV4）",
            ),
            Forbidden(
                "String.toByteArray()",
                // 只匹配**字符串字面量**：`Collection<Byte>.toByteArray()` 是 common API，不能误伤。
                // 变量形式（`s.toByteArray()`）静态区分不了，靠 :core 的 linuxX64 编译门禁兜底。
                Regex("\"[^\"]*\"\\s*\\.\\s*toByteArray\\(\\)"),
                "String.toByteArray() 是 JVM 专有；跨平台用 encodeToByteArray()",
            ),
            Forbidden(
                "::class.java",
                Regex("""::\s*class\s*\.\s*java\b"""),
                "JVM 反射不存在于 Native；测试里尤其常见，需换平台中立写法",
            ),
            Forbidden(
                "java.io.*",
                Regex("""(?<![\w.])java\s*\.\s*io\s*\.\s*\w+"""),
                "java.io 不存在于 Native；改用 ByteArray + 下标，或抽 expect/actual 文件接口",
            ),
            Forbidden(
                "Thread / ThreadLocal",
                Regex("""(?<![\w.])(Thread|ThreadLocal)\s*[(<]"""),
                "Native 不支持 JVM 线程模型；用协程调度器替代",
            ),
        )
    }
}

/** 需要扫描的源集：commonMain 是「必须跨平台」，skikoMain 是「jvm 与 iOS 共享」。 */
val puritySourceSets = listOf("commonMain", "skikoMain")
val purityModules = listOf("core", "backdrop", "miuix")

tasks.register<CheckKmpPurityTask>("checkKmpPurity") {
    group = "verification"
    description = "检查 commonMain/skikoMain 是否混入 JVM 专有 API（这些代码在 iOS 上编不过）"
    rootPath.set(projectDir.absolutePath)
    sources.from(
        purityModules.flatMap { module ->
            puritySourceSets.map { set -> file("$module/src/$set") }
        },
    )
}

// 让各模块的 `check` 自动带上纯净度检查 —— 否则这条检查很容易被忘掉。
// 根项目没有 java 插件、没有 check 任务，所以挂在子项目上。
val purityCheckTask = tasks.named("checkKmpPurity")

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(purityCheckTask)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kotlin/Wasm：不要从 nodejs.org 下载 Node 发行版，用本机已装的
//
// 背景：`:backdrop` / `:miuix` 挂了 wasmJs 编译门禁（见各自 build.gradle.kts）。
// Kotlin/Wasm 插件会为 `kotlinWasmNodeJsSetup` 任务自行注册 Distributions 仓库，
// 与 settings.gradle.kts 的 FAIL_ON_PROJECT_REPOS 直接冲突：
//   "repository 'Distributions at https://nodejs.org/dist' was added by unknown code"
//
// 官方做法（kotlinlang.org/docs/js-project-setup.html#use-pre-installed-node-js）是
// 把 EnvSpec.download 置为 false —— 本机 Node v24.16.0 已在 PATH 中。
//
// ⚠ 实测（2026-10-10）只做这一步**不够**：即使 download=false，
// 该仓库仍在配置阶段被注册（注册动作发生在读取 download 之前），冲突依旧。
// 所以 wasmJs 门禁**刻意不挂在 check 上**，而是独立命令跑：
//     ./gradlew checkKmpWasmJs
// 这样 `--offline` 的 Android 零回归门禁（:core:check / :miuix:check / :backdrop:check）
// 完全不受影响 —— 红线不脏。
// ─────────────────────────────────────────────────────────────────────────────
subprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        extensions.getByType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>()
            .download = false
    }
}

// wasmJs 编译门禁：独立任务，**不挂 check**（原因见上）。
//
// ⚠ 两个 property 必须**同时**给 —— wasmJs 目标默认不存在，只给
// `-Pnexio.wasm=true` 但目标没声明时，下面 tasks.named 会找不到任务而失败。
// 所以本任务自己也读 nexio.wasm 来决定要不要配置依赖。
//
// 用法：./gradlew -Pnexio.wasm=true checkKmpWasmJs   （首次需联网，需 PATH 中有 Node）
val wasmGateEnabled = (project.findProperty("nexio.wasm") as? String)?.toBoolean() ?: false
if (wasmGateEnabled) {
    tasks.register("checkKmpWasmJs") {
        group = "verification"
        description = "编译 wasmJs 目标，验证 commonMain/skikoMain 不含 JVM 专有 API（iOS 移植前的非 JVM 编译门禁）"
        dependsOn(
            project(":backdrop").tasks.named("compileKotlinWasmJs"),
            project(":miuix").tasks.named("compileKotlinWasmJs"),
        )
    }
} else {
    // 目标不存在时注册一个占位任务，让 `./gradlew checkKmpWasmJs` 不至于
    // 报 "Task 'checkKmpWasmJs' not found" —— 而是给出明确提示。
    tasks.register("checkKmpWasmJs") {
        group = "verification"
        description = "wasmJs 编译门禁（需 -Pnexio.wasm=true 开启）"
        doLast {
            logger.lifecycle(
                "checkKmpWasmJs: wasmJs 目标默认关闭。请改用：\n" +
                    "    ./gradlew -Pnexio.wasm=true checkKmpWasmJs\n" +
                    "（原因见 build.gradle.kts 里 wasmJs 目标处的注释：Kotlin/Wasm 插件会在配置阶段\n" +
                    " 注册 Distributions 仓库，与 FAIL_ON_PROJECT_REPOS 冲突，导致 --offline 的 Android 门禁跑不了）",
            )
        }
    }
}
