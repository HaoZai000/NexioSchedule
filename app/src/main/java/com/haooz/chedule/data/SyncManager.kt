package com.haooz.chedule.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 同步管理器 - 手动备份/恢复（单例）
 */
class SyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var repository: CourseRepository? = null
    private var webDavManager: WebDavManager? = null

    var onSyncCompleted: (() -> Unit)? = null

    private val _syncState = MutableStateFlow<SyncOperationState>(SyncOperationState.Idle)
    val syncState = _syncState.asStateFlow()

    sealed class SyncOperationState {
        data object Idle : SyncOperationState()
        data object Running : SyncOperationState()
        data class BackupSuccess(val backupId: String) : SyncOperationState()
        data class RestoreSuccess(val backupTime: String) : SyncOperationState()
        data class Error(val message: String) : SyncOperationState()
    }

    fun start(repository: CourseRepository, webDavManager: WebDavManager) {
        this.repository = repository
        this.webDavManager = webDavManager
        Log.d(TAG, "SyncManager started")
    }

    suspend fun backupNow(): SyncOperationState {
        val mgr = webDavManager ?: return SyncOperationState.Error("未初始化")
        val repo = repository ?: return SyncOperationState.Error("未初始化")
        if (!mgr.isConfigured()) return SyncOperationState.Error("请先配置 WebDAV 服务器")
        if (_syncState.value is SyncOperationState.Running) return SyncOperationState.Error("正在执行操作，请稍候")

        _syncState.value = SyncOperationState.Running
        Log.d(TAG, "Starting backup...")

        try {
            val result = mgr.backupAllData(repo)

            when (result) {
                is BackupResult.Success -> {
                    _syncState.value = SyncOperationState.BackupSuccess(result.backupId)
                    onSyncCompleted?.invoke()
                    Log.d(TAG, "Backup completed: ${result.backupId}")
                }
                is BackupResult.Error -> {
                    _syncState.value = SyncOperationState.Error(result.message)
                    Log.e(TAG, "Backup failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            _syncState.value = SyncOperationState.Error("备份异常: ${e.message}")
            Log.e(TAG, "Backup exception", e)
        }

        return _syncState.value
    }

    suspend fun restoreNow(): SyncOperationState {
        val mgr = webDavManager ?: return SyncOperationState.Error("未初始化")
        val repo = repository ?: return SyncOperationState.Error("未初始化")
        if (!mgr.isConfigured()) return SyncOperationState.Error("请先配置 WebDAV 服务器")
        if (_syncState.value is SyncOperationState.Running) return SyncOperationState.Error("正在执行操作，请稍候")

        _syncState.value = SyncOperationState.Running
        Log.d(TAG, "Starting restore...")

        try {
            val result = mgr.restoreLatestBackup(repo)

            when (result) {
                is RestoreResult.Success -> {
                    _syncState.value = SyncOperationState.RestoreSuccess(result.backupTime)
                    onSyncCompleted?.invoke()
                    Log.d(TAG, "Restore completed from ${result.backupTime}")
                }
                is RestoreResult.Error -> {
                    _syncState.value = SyncOperationState.Error(result.message)
                    Log.e(TAG, "Restore failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            _syncState.value = SyncOperationState.Error("恢复异常: ${e.message}")
            Log.e(TAG, "Restore exception", e)
        }

        return _syncState.value
    }

    fun resetState() {
        _syncState.value = SyncOperationState.Idle
    }
}
