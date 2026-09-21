/**
 * Release 常驻功能轨迹 + 手动录制会话。
 *
 * 日常：内存环形缓冲（最近 30 分钟），trace 只写内存，无磁盘。
 * 录制：用户点「开始」后同一路 trace 会增量刷到文件，最长 30 分钟；
 *       手动结束 / 崩溃 / 进程被杀（下次启动补尾）自动结束，结束后可分享。
 *
 * Release 常驻原则：未开启录制时不得有磁盘 I/O、不得有常驻线程、
 * 不得拖慢冷启动；崩溃与导出路径才允许重操作（且必须带超时）。
 * 注：release 崩溃栈是混淆后的，需配合当次构建产出的 mapping.txt 还原。
 */
package com.haooz.chedule.ui.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object CrashLogHelper {

    private const val TAG = "CrashLogHelper"
    private const val CRASH_DIR = "crash_logs"
    // 崩溃日志上限：单份含 logcat，取 12 份控制占用在 1~2MB 内
    private const val MAX_CRASH_FILES = 12
    private const val SHARE_DIR = "share"
    private const val MAX_STACK_CHARS = 80_000
    private const val LOGCAT_LINE_LIMIT = 200

    private const val PREFS = "crash_log_prefs"
    private const val KEY_RECORDING_ACTIVE = "recording_active"
    private const val KEY_RECORDING_FILE = "recording_file"
    private const val KEY_RECORDING_STARTED = "recording_started"
    private const val KEY_READY_FILE = "ready_file"

    /** 功能轨迹 / 录制时间窗 */
    const val TRACE_WINDOW_MS = 30L * 60L * 1000L
    const val MAX_RECORD_MS = TRACE_WINDOW_MS

    private const val TRACE_CAPACITY = 512

    private val traceLock = Any()
    private val traceTimes = LongArray(TRACE_CAPACITY)
    private val traceFeatures = arrayOfNulls<String>(TRACE_CAPACITY)
    private val traceEvents = arrayOfNulls<String>(TRACE_CAPACITY)
    private val traceDetails = arrayOfNulls<String>(TRACE_CAPACITY)
    private val traceSeqs = IntArray(TRACE_CAPACITY)
    private var traceWriteIndex = 0
    private var traceCount = 0
    private val traceSeqCounter = AtomicInteger(0)

    // 懒创建：不录制就永远不会有这条常驻线程
    private val flushExecutor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "crash-log-flush").apply { isDaemon = true }
        }
    }
    private val lastFlushedSeq = AtomicInteger(0)
    private val lastFlushAt = AtomicLong(0L)

    @Volatile
    private var installed = false
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    var isRecording = false
        private set

    @Volatile
    var recordingStartedAt = 0L
        private set

    @Volatile
    var hasReadyRecording = false
        private set

    @Volatile
    var readyRecordingPath: String? = null
        private set

    @Volatile
    var readyReason: String = ""
        private set

    @Volatile
    var lastFeature: String = ""
        private set

    @Volatile
    var lastEvent: String = ""
        private set

    private var appContext: Context? = null

    private val recoveryLock = Any()
    @Volatile
    private var recovered = false
    @Volatile
    private var sessionStartedAt = 0L
    private val sessionId by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UUID.randomUUID().toString().take(8)
    }

    /**
     * 冷启动唯一入口。刻意保持「零 I/O」：
     * 只注册崩溃兜底，不碰磁盘 / SharedPreferences / PackageManager。
     * 需要读写的恢复逻辑一律丢到一次性后台线程，避免拖慢启动。
     */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        appContext = app
        sessionStartedAt = System.currentTimeMillis()
        runCatching {
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    if (isRecording) stopRecording(app, reason = "crash")
                    writeCrashFile(app, thread, throwable)
                }
                val prev = defaultHandler
                if (prev != null) prev.uncaughtException(thread, throwable)
                else {
                    Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
                    Runtime.getRuntime().exit(2)
                }
            }
        }.onFailure {
            Log.w(TAG, "install failed", it)
        }
        // 恢复上次被中断的录制：涉及 SP 与文件，必须挪出启动路径
        Thread {
            runCatching { ensureRecovered(app) }
        }.apply {
            isDaemon = true
            name = "crash-log-boot"
        }.start()
    }

    /**
     * 幂等恢复。UI 需要在读 hasReadyRecording 之前调用一次，
     * 保证后台线程还没跑完时也能拿到正确状态。
     */
    fun ensureRecovered(context: Context) {
        if (recovered) return
        synchronized(recoveryLock) {
            if (recovered) return
            runCatching { recoverInterruptedRecording(context.applicationContext) }
            recovered = true
        }
    }

    /** 功能埋点：只写内存环；若正在录制则按需增量刷盘（后台线程） */
    fun trace(feature: String, event: String, detail: String = "") {
        lastFeature = feature
        lastEvent = event
        val now = System.currentTimeMillis()
        val seq = traceSeqCounter.incrementAndGet()
        synchronized(traceLock) {
            val i = traceWriteIndex
            traceTimes[i] = now
            traceFeatures[i] = feature
            traceEvents[i] = event
            traceDetails[i] = detail
            traceSeqs[i] = seq
            traceWriteIndex = (i + 1) % TRACE_CAPACITY
            if (traceCount < TRACE_CAPACITY) traceCount++
        }
        if (isRecording) {
            val last = lastFlushAt.get()
            if (now - last >= 2000L && lastFlushAt.compareAndSet(last, now)) {
                scheduleFlush()
            }
        }
    }

    /** 仅录制中生效的详细流程日志；未录制时零成本返回（调用方仍可能拼 detail 字符串） */
    fun traceRecording(feature: String, event: String, detail: String = "") {
        if (!isRecording) return
        trace(feature, event, detail)
    }

    /**
     * lambda 版：未录制时 detail 完全不求值。
     * detail 里有 String.format / 多段拼接的埋点请用这个，
     * 正式版里未开启录制时调用成本等于一次 volatile 读。
     */
    inline fun traceRecording(feature: String, event: String, detail: () -> String) {
        if (!isRecording) return
        trace(feature, event, detail())
    }

    fun startRecording(context: Context): Boolean {
        if (isRecording) return false
        val app = context.applicationContext
        appContext = app
        val dir = File(app.filesDir, CRASH_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val file = File(dir, "recording_$stamp.txt")
        recordingStartedAt = System.currentTimeMillis()
        lastFlushedSeq.set(0)
        runCatching {
            file.writeText(
                buildString {
                    appendLine("=== Nexio课程表 功能录制 ===")
                    appendLine("startedAt=${formatTime(recordingStartedAt)}")
                    appendLine("maxRecordMin=${MAX_RECORD_MS / 60000}")
                    appendLine(deviceInfo(app))
                    appendLine("=== traces ===")
                }
            )
        }.onFailure {
            Log.w(TAG, "startRecording write failed", it)
            return false
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RECORDING_ACTIVE, true)
            .putString(KEY_RECORDING_FILE, file.absolutePath)
            .putLong(KEY_RECORDING_STARTED, recordingStartedAt)
            .apply()
        readyRecordingPath = file.absolutePath
        hasReadyRecording = false
        readyReason = "recording"
        isRecording = true
        trace("日志录制", "start", "file=${file.name}")
        scheduleFlush()
        return true
    }

    /**
     * 结束录制：manual / crash / timeout / process_killed。
     * 结束后 hasReadyRecording=true，分享按钮可点亮。
     */
    fun stopRecording(context: Context, reason: String = "manual"): Boolean {
        val app = context.applicationContext
        appContext = app
        if (!isRecording) {
            // 允许对「异常中断」路径补结束
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_RECORDING_ACTIVE, false)) return false
        }
        val path = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDING_FILE, null)
            ?: readyRecordingPath
        flushTracesNow(path, final = true, endReason = reason)
        isRecording = false
        hasReadyRecording = true
        readyReason = reason
        readyRecordingPath = path
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RECORDING_ACTIVE, false)
            .putString(KEY_READY_FILE, path)
            .apply()
        return path != null && File(path).exists()
    }

    /** 录制中剩余毫秒；未录制返回 0 */
    fun recordingRemainingMs(): Long {
        if (!isRecording) return 0L
        val elapsed = System.currentTimeMillis() - recordingStartedAt
        return (MAX_RECORD_MS - elapsed).coerceAtLeast(0L)
    }

    fun recordingElapsedMs(): Long {
        if (!isRecording) return 0L
        return (System.currentTimeMillis() - recordingStartedAt).coerceAtMost(MAX_RECORD_MS)
    }

    /** 录制到 30 分钟自动结束；UI 也可轮询调用 */
    fun ensureRecordingNotExpired() {
        if (isRecording && recordingRemainingMs() <= 0L) {
            val ctx = appContext ?: return
            stopRecording(ctx, reason = "timeout_30min")
        }
    }

    fun shareReadyRecording(context: Context): Boolean {
        ensureRecovered(context)
        ensureRecordingNotExpired()
        val path = readyRecordingPath
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_READY_FILE, null)
        if (path.isNullOrBlank() || !File(path).exists()) {
            // 没有录制文件时回退为完整导出，仍可分享崩溃/轨迹
            return exportAndShare(context)
        }
        val file = File(path)
        // 补一段设备信息与历史崩溃，方便开发者一次看完
        val body = runCatching {
            val base = file.readText()
            if (base.contains("=== 已捕获崩溃")) base
            else buildString {
                append(base)
                appendLine()
                appendLine("=== readyReason=$readyReason ===")
                appendLine(deviceInfo(context.applicationContext))
                appendLine()
                appendLine("=== live trace snapshot ===")
                appendLine(formatTrace())
                appendLine()
                appendLine("=== crash files ===")
                val crashes = File(context.filesDir, CRASH_DIR).listFiles()
                    ?.filter { it.isFile && it.name.startsWith("crash_") && !it.name.endsWith("_mem.txt") }
                    ?.sortedByDescending { it.name }
                    .orEmpty()
                if (crashes.isEmpty()) appendLine("(none)")
                else crashes.take(5).forEach { c ->
                    appendLine("--- ${c.name} ---")
                    appendLine(runCatching { c.readText().take(20_000) }.getOrDefault("?"))
                }
            }
        }.getOrDefault(file.readText())

        return runCatching {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val out = File(dir, "nexio_recording_$stamp.txt")
            out.writeText(body)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Nexio课程表功能录制日志")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val textBody = if (body.length > 20_000) body.take(20_000) + "\n…(完整内容见附件)" else body
                putExtra(Intent.EXTRA_TEXT, textBody)
            }
            context.startActivity(
                Intent.createChooser(send, "分享录制日志").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrElse {
            Log.w(TAG, "shareReadyRecording failed", it)
            false
        }
    }

    fun crashFileCount(context: Context): Int {
        return File(context.filesDir, CRASH_DIR).listFiles()
            ?.count { it.isFile && it.name.startsWith("crash_") && it.length() > 0 }
            ?: 0
    }

    fun exportAndShare(context: Context): Boolean {
        ensureRecordingNotExpired()
        val content = buildExportContent(context)
        val uri = runCatching {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val file = File(dir, "nexio_crash_$stamp.txt")
            file.writeText(content)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nexio课程表崩溃/轨迹日志")
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val body = if (content.length > 20_000) {
                content.substring(0, 20_000) + "\n…(完整内容见附件)"
            } else {
                content
            }
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(
                Intent.createChooser(send, "导出崩溃日志").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "share failed", e)
            false
        }
    }

    fun buildExportContent(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== Nexio课程表 崩溃/轨迹日志导出 ===")
        sb.appendLine("exportAt=${formatTime(System.currentTimeMillis())}")
        sb.appendLine("traceWindowMin=${TRACE_WINDOW_MS / 60000}")
        sb.appendLine("isRecording=$isRecording")
        sb.appendLine("hasReadyRecording=$hasReadyRecording reason=$readyReason")
        sb.appendLine("lastFeature=$lastFeature")
        sb.appendLine("lastEvent=$lastEvent")
        sb.appendLine()
        sb.append(deviceInfo(context))
        sb.appendLine()
        sb.appendLine("=== 本机会话 ===")
        sb.appendLine(sessionMarkerText())
        sb.appendLine()
        sb.appendLine("=== 功能轨迹（最近 ${TRACE_WINDOW_MS / 60000} 分钟） ===")
        sb.appendLine(formatTrace())
        sb.appendLine()
        sb.appendLine(crashSection(context))
        sb.appendLine()
        sb.appendLine("=== Logcat（release 可能受 ROM 限制） ===")
        sb.appendLine(tryDumpLogcat())
        return sb.toString()
    }

    fun formatTrace(windowMs: Long = TRACE_WINDOW_MS): String {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs
        val lines = ArrayList<String>(64)
        synchronized(traceLock) {
            val n = traceCount
            val start = (traceWriteIndex - n + TRACE_CAPACITY) % TRACE_CAPACITY
            for (k in 0 until n) {
                val i = (start + k) % TRACE_CAPACITY
                val t = traceTimes[i]
                if (t < cutoff) continue
                val feature = traceFeatures[i] ?: continue
                val event = traceEvents[i].orEmpty()
                val detail = traceDetails[i].orEmpty()
                lines.add(
                    if (detail.isEmpty()) "${formatTime(t)} [$feature] $event"
                    else "${formatTime(t)} [$feature] $event | $detail"
                )
            }
        }
        return if (lines.isEmpty()) {
            "(最近 ${windowMs / 60000} 分钟无功能轨迹)"
        } else {
            lines.joinToString("\n")
        }
    }

    private fun recoverInterruptedRecording(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_RECORDING_ACTIVE, false)
        val path = prefs.getString(KEY_RECORDING_FILE, null)
        val ready = prefs.getString(KEY_READY_FILE, null)
        if (!ready.isNullOrBlank() && File(ready).exists()) {
            hasReadyRecording = true
            readyRecordingPath = ready
            readyReason = prefs.getString("ready_reason", "previous") ?: "previous"
        }
        if (active && !path.isNullOrBlank()) {
            // 上次进程被杀：把已刷盘轨迹标成自动结束，分享按钮可点亮
            runCatching {
                File(path).appendText(
                    "\n=== auto_ended=process_killed at ${formatTime(System.currentTimeMillis())} ===\n"
                )
            }
            isRecording = false
            hasReadyRecording = true
            readyRecordingPath = path
            readyReason = "process_killed"
            prefs.edit()
                .putBoolean(KEY_RECORDING_ACTIVE, false)
                .putString(KEY_READY_FILE, path)
                .putString("ready_reason", "process_killed")
                .apply()
        }
    }

    private fun scheduleFlush() {
        val path = appContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_RECORDING_FILE, null) ?: return
        flushExecutor.execute {
            runCatching { flushTracesNow(path, final = false, endReason = null) }
        }
    }

    private fun flushTracesNow(path: String?, final: Boolean, endReason: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        val from = lastFlushedSeq.get()
        val snapshot = ArrayList<String>(64)
        var maxSeq = from
        synchronized(traceLock) {
            val n = traceCount
            val start = (traceWriteIndex - n + TRACE_CAPACITY) % TRACE_CAPACITY
            for (k in 0 until n) {
                val i = (start + k) % TRACE_CAPACITY
                val seq = traceSeqs[i]
                if (seq <= from) continue
                val feature = traceFeatures[i] ?: continue
                val event = traceEvents[i].orEmpty()
                val detail = traceDetails[i].orEmpty()
                val line = if (detail.isEmpty()) {
                    "${formatTime(traceTimes[i])} [$feature] $event"
                } else {
                    "${formatTime(traceTimes[i])} [$feature] $event | $detail"
                }
                snapshot.add(line)
                if (seq > maxSeq) maxSeq = seq
            }
        }
        if (snapshot.isNotEmpty()) {
            runCatching {
                file.appendText(snapshot.joinToString(separator = "\n", postfix = "\n"))
            }
        }
        if (maxSeq > from) lastFlushedSeq.set(maxSeq)
        if (final) {
            val reason = endReason ?: "manual"
            runCatching {
                file.appendText(
                    buildString {
                        appendLine()
                        appendLine("=== ended reason=$reason at ${formatTime(System.currentTimeMillis())} ===")
                        appendLine("lastFeature=$lastFeature lastEvent=$lastEvent")
                    }
                )
            }
        }
    }

    private fun crashSection(context: Context): String {
        val crashFiles = File(context.filesDir, CRASH_DIR).listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") && !it.name.endsWith("_mem.txt") && it.length() > 0 }
            ?.sortedByDescending { it.name }
            .orEmpty()
        val sb = StringBuilder()
        sb.appendLine("=== 已捕获崩溃 (${crashFiles.size}) ===")
        if (crashFiles.isEmpty()) {
            sb.appendLine("(尚无 Java/Kotlin 崩溃记录。若为原生崩溃，请附上 logcat / 功能轨迹。)")
        } else {
            crashFiles.take(8).forEach { file ->
                sb.appendLine()
                sb.appendLine("--- ${file.name} ---")
                runCatching {
                    val text = file.readText()
                    sb.appendLine(
                        if (text.length > MAX_STACK_CHARS) text.take(MAX_STACK_CHARS) + "\n…truncated"
                        else text
                    )
                }.onFailure { sb.appendLine("read failed: ${it.message}") }
            }
            if (crashFiles.size > 8) {
                sb.appendLine()
                sb.appendLine("…另有 ${crashFiles.size - 8} 条未展开")
            }
        }
        return sb.toString()
    }

    private fun crashDir(context: Context): File =
        File(context.filesDir, CRASH_DIR).apply { mkdirs() }

    /**
     * 会话标记只留在内存，不再每次冷启动写 session.txt：
     * 启动路径上不做任何文件写，导出时才按需拼出。
     */
    private fun sessionMarkerText(): String = buildString {
        appendLine("sessionId=$sessionId")
        appendLine("startedAt=${formatTime(sessionStartedAt)}")
        appendLine("uptimeMin=${(System.currentTimeMillis() - sessionStartedAt) / 60000}")
    }

    private fun writeCrashFile(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        var stack = sw.toString()
        if (stack.length > MAX_STACK_CHARS) {
            stack = stack.take(MAX_STACK_CHARS) + "\n…truncated"
        }
        val body = buildString {
            appendLine("time=${formatTime(System.currentTimeMillis())}")
            appendLine("thread=${thread.name}")
            appendLine("throwable=${throwable::class.java.name}")
            appendLine("message=${throwable.message}")
            appendLine("lastFeature=$lastFeature")
            appendLine("lastEvent=$lastEvent")
            appendLine("wasRecording=${!isRecording && readyReason == "crash"}")
            appendLine()
            appendLine(deviceInfo(context))
            appendLine()
            appendLine("=== feature trace (30 min) ===")
            appendLine(formatTrace())
            appendLine()
            appendLine("=== stack ===")
            appendLine(stack)
            appendLine()
            appendLine("=== logcat @ crash ===")
            // 崩溃路径只抓 crash 缓冲，避免写不完
            appendLine(tryDumpLogcat(quick = true))
        }
        runCatching { File(dir, "crash_$stamp.txt").writeText(body) }
        runCatching {
            val rt = Runtime.getRuntime()
            val heap = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
            val max = rt.maxMemory() / (1024f * 1024f)
            File(dir, "crash_${stamp}_mem.txt").writeText(
                "heapUsedMb=${"%.1f".format(Locale.ROOT, heap)} " +
                    "heapMaxMb=${"%.1f".format(Locale.ROOT, max)} " +
                    "lastFeature=$lastFeature lastEvent=$lastEvent"
            )
        }
        trimCrashFiles(dir)
        // 崩溃后分享按钮应亮起
        hasReadyRecording = true
        if (readyReason != "crash") {
            // 保留 crash 标记，便于 About 页展示
            readyReason = if (readyRecordingPath != null) readyReason else "crash"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ready_reason", readyReason)
            .putString(KEY_READY_FILE, readyRecordingPath)
            .apply()
    }

    private fun trimCrashFiles(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") && !it.name.endsWith("_mem.txt") }
            ?.sortedByDescending { it.name }
            ?: return
        files.drop(MAX_CRASH_FILES).forEach { runCatching { it.delete() } }
    }

    private fun deviceInfo(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val rt = Runtime.getRuntime()
        val heapUsed = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
        val heapMax = rt.maxMemory() / (1024f * 1024f)
        val dm = context.resources.displayMetrics
        return buildString {
            appendLine("app=${context.packageName}")
            appendLine("versionName=${packageInfo?.versionName ?: "?"}")
            appendLine("versionCode=${packageInfo?.longVersionCode ?: "?"}")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("brand=${Build.BRAND}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("release=${Build.VERSION.RELEASE}")
            appendLine("display=${Build.DISPLAY}")
            appendLine("screen=${dm.widthPixels}x${dm.heightPixels} density=${dm.density}")
            appendLine(
                "heapUsedMb=${"%.1f".format(Locale.ROOT, heapUsed)} " +
                    "heapMaxMb=${"%.1f".format(Locale.ROOT, heapMax)}"
            )
        }
    }

    /**
     * quick=true 只抓 crash 缓冲（崩溃路径用，越快越好）；
     * 用户主动导出时才抓全量。每条命令都有硬超时，绝不把崩溃线程卡死。
     */
    private fun tryDumpLogcat(quick: Boolean = false): String {
        val limit = if (quick) LOGCAT_LINE_LIMIT / 2 else LOGCAT_LINE_LIMIT
        val cmds = if (quick) {
            listOf(
                arrayOf("logcat", "-d", "-b", "crash", "-v", "threadtime", "-t", limit.toString())
            )
        } else {
            listOf(
                arrayOf("logcat", "-d", "-b", "crash", "-v", "threadtime", "-t", limit.toString()),
                arrayOf("logcat", "-d", "-b", "main", "-v", "threadtime", "-t", limit.toString()),
                arrayOf("logcat", "-d", "-v", "threadtime", "-t", (limit / 2).toString()),
            )
        }
        val chunks = mutableListOf<String>()
        for (cmd in cmds) {
            val text = runLogcat(cmd, if (quick) 1200L else 2500L)
            if (text.isNotBlank()) {
                chunks += "## ${cmd.joinToString(" ")}\n" + filterLogcat(text)
            }
        }
        if (chunks.isEmpty()) return "(logcat unavailable on this device/ROM)"
        return chunks.joinToString("\n\n")
    }

    private fun runLogcat(cmd: Array<String>, timeoutMs: Long): String {
        return runCatching {
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val buffer = StringBuilder()
            // 读流与等待都放子线程，调用方只等超时，超时即强杀
            val pump = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { r ->
                        while (true) {
                            val line = r.readLine() ?: break
                            buffer.append(line).append('\n')
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "logcat-pump"
            }
            pump.start()
            val waiter = Thread {
                runCatching { process.waitFor() }
            }.apply {
                isDaemon = true
                name = "logcat-wait"
            }
            waiter.start()
            waiter.join(timeoutMs)
            if (waiter.isAlive) {
                runCatching { process.destroyForcibly() }
                pump.join(200)
            }
            buffer.toString()
        }.getOrNull().orEmpty()
    }

    private fun filterLogcat(raw: String): String {
        val keywords = listOf(
            "FATAL", "AndroidRuntime", "haooz", "chedule", "Nexio",
            "SIGSEGV", "SIGABRT", "SIGBUS", "tombstone", "DEBUG",
            "RenderNode", "GraphicsLayer", "backdrop", "Crash",
        )
        val lines = raw.lineSequence().filter { line ->
            if (line.isBlank()) return@filter false
            keywords.any { line.contains(it, ignoreCase = true) }
        }.toList()
        return if (lines.size >= 20) {
            lines.takeLast(300).joinToString("\n")
        } else {
            raw.lineSequence().filter { it.isNotBlank() }.toList().takeLast(200).joinToString("\n")
        }
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(millis))
}

/** 各功能统一埋点入口：稳定短名，便于崩溃轨迹对账 */
object FeatureLog {
    fun t(feature: String, event: String, detail: String = "") =
        CrashLogHelper.trace(feature, event, detail)

    fun switchSchedule(event: String, detail: String = "") =
        t("切换课表", event, detail)

    fun main(event: String, detail: String = "") =
        t("主页", event, detail)

    fun settings(page: String) =
        t("设置", "open", page)

    fun today(event: String, detail: String = "") =
        t("今日页", event, detail)

    fun schedule(event: String, detail: String = "") =
        t("课程表", event, detail)

    fun course(event: String, detail: String = "") =
        t("课程", event, detail)

    fun appearance(event: String, detail: String = "") =
        t("课表外观", event, detail)

    fun shift(event: String, detail: String = "") =
        t("排班课表", event, detail)

    fun import(event: String, detail: String = "") =
        t("课表导入", event, detail)

    fun backup(event: String, detail: String = "") =
        t("备份迁移", event, detail)

    fun reminder(event: String, detail: String = "") =
        t("课程提醒", event, detail)

    /** 课程提醒页详细流程：仅「开始录制」后写入 */
    fun reminderFlow(event: String, detail: String = "") =
        CrashLogHelper.traceRecording("课程提醒", event, detail)

    /** lambda 版：未录制时 detail 不求值 */
    inline fun reminderFlow(event: String, detail: () -> String) =
        CrashLogHelper.traceRecording("课程提醒", event, detail)

    fun holiday(event: String, detail: String = "") =
        t("节假日调休", event, detail)

    fun widget(event: String, detail: String = "") =
        t("桌面小部件", event, detail)

    fun preference(event: String, detail: String = "") =
        t("应用偏好", event, detail)

    fun update(event: String, detail: String = "") =
        t("更新设置", event, detail)

    fun about(event: String, detail: String = "") =
        t("关于应用", event, detail)

    fun timeConfig(event: String, detail: String = "") =
        t("课表节数时间", event, detail)

    fun webdav(event: String, detail: String = "") =
        t("WebDAV", event, detail)

    fun shareSchedule(event: String, detail: String = "") =
        t("口令分享", event, detail)
}
