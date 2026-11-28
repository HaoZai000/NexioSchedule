package com.haooz.chedule.ui.utils

import android.content.Context
import com.haooz.chedule.data.platform.DiagnosticRecorder

/** 使用宿主 Context 将诊断操作交给 [CrashLogHelper]。 */
class AndroidDiagnosticRecorder(private val context: Context) : DiagnosticRecorder {
    override val isRecording: Boolean get() = CrashLogHelper.isRecording
    override val hasReadyRecording: Boolean get() = CrashLogHelper.hasReadyRecording
    override val readyReason: String get() = CrashLogHelper.readyReason
    override val maxRecordMs: Long get() = CrashLogHelper.MAX_RECORD_MS
    override fun recordingElapsedMs(): Long = CrashLogHelper.recordingElapsedMs()
    override fun ensureRecovered() = CrashLogHelper.ensureRecovered(context)
    override fun ensureRecordingNotExpired() = CrashLogHelper.ensureRecordingNotExpired()
    override fun startRecording(): Boolean = CrashLogHelper.startRecording(context)
    override fun stopRecording(reason: String): Boolean = CrashLogHelper.stopRecording(context, reason)
    override fun shareReadyRecording(): Boolean = CrashLogHelper.shareReadyRecording(context)
}
