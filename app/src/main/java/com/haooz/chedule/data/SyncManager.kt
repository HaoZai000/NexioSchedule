package com.haooz.chedule.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 同步管理器 - 管理待同步队列、网络监听、自动触发同步（单例）
 */
class SyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private const val PREFS_NAME = "sync_pending_queue"
        private const val KEY_PENDING_CHANGES = "pending_changes"
        private const val KEY_LAST_AUTO_CHECK_TIME = "last_auto_check_time"
        private const val SYNC_COOLDOWN_MS = 60_000L // 1分钟冷却
        private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24小时

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val pendingPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var repository: CourseRepository? = null
    private var webDavManager: WebDavManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 同步完成回调（用于通知 ViewModel 刷新内存缓存）
    var onSyncCompleted: (() -> Unit)? = null

    // 节流控制
    private var lastSyncCompletedTime = 0L
    private var pendingSyncJob: kotlinx.coroutines.Job? = null

    // 同步状态
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    // 待同步操作数
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount = _pendingCount.asStateFlow()

    sealed class SyncState {
        data object Idle : SyncState()
        data object Syncing : SyncState()
        data class Success(val uploaded: Int, val downloaded: Int) : SyncState()
        data class Error(val message: String) : SyncState()
        data object Offline : SyncState()
    }

    data class PendingChange(
        val courseId: String,
        val action: String,
        val timestamp: Long
    )

    /**
     * 启动同步管理器
     */
    fun start(repository: CourseRepository, webDavManager: WebDavManager) {
        this.repository = repository
        this.webDavManager = webDavManager

        // 注册变更回调
        repository.onCourseChanged = { action, courseId ->
            Log.d(TAG, "Course changed: action=$action, courseId=$courseId")
            onCourseChanged(action, courseId)
        }

        // 注册网络监听
        registerNetworkCallback()

        // 加载待同步队列
        loadPendingQueue()

        Log.d(TAG, "SyncManager started, pending=${_pendingCount.value}")
    }

    /**
     * 课程变更回调
     */
    private fun onCourseChanged(action: String, courseId: String) {
        val mgr = webDavManager ?: return
        if (!mgr.isConfigured()) return
        if (!mgr.autoSyncEnabled) return

        // 将操作加入待同步队列
        addPendingChange(PendingChange(courseId, action, System.currentTimeMillis()))

        // 节流：1分钟内最多同步一次
        if (isOnline()) {
            val now = System.currentTimeMillis()
            val timeUntilNextSync = (SYNC_COOLDOWN_MS - (now - lastSyncCompletedTime)).coerceAtLeast(0L)

            // 取消之前的延迟同步（如果有）
            pendingSyncJob?.cancel()

            if (timeUntilNextSync <= 0) {
                // 冷却已过，立即同步
                scope.launch {
                    syncNow()
                }
            } else {
                // 冷却中，延迟到冷却结束后同步
                Log.d(TAG, "Throttled, will sync in ${timeUntilNextSync}ms")
                pendingSyncJob = scope.launch {
                    kotlinx.coroutines.delay(timeUntilNextSync)
                    syncNow()
                }
            }
        } else {
            _syncState.value = SyncState.Offline
            Log.d(TAG, "Offline, queued change: $action $courseId")
        }
    }

    /**
     * 执行同步（同步所有课表）
     */
    suspend fun syncNow() {
        val mgr = webDavManager ?: return
        val repo = repository ?: return
        if (!mgr.isConfigured()) return
        if (_syncState.value is SyncState.Syncing) return

        _syncState.value = SyncState.Syncing
        Log.d(TAG, "Starting sync all schedules...")

        try {
            val result = mgr.syncAllSchedules(repo)

            when (result) {
                is SyncResult.Merged -> {
                    clearPendingQueue()
                    lastSyncCompletedTime = System.currentTimeMillis()
                    _syncState.value = SyncState.Success(result.uploaded, result.downloaded)
                    onSyncCompleted?.invoke()
                    Log.d(TAG, "Sync completed: uploaded=${result.uploaded}, downloaded=${result.downloaded}")
                }
                is SyncResult.Error -> {
                    lastSyncCompletedTime = System.currentTimeMillis()
                    _syncState.value = SyncState.Error(result.message)
                    Log.e(TAG, "Sync failed: ${result.message}")
                }
                else -> {
                    lastSyncCompletedTime = System.currentTimeMillis()
                    _syncState.value = SyncState.Idle
                }
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("同步异常: ${e.message}")
            Log.e(TAG, "Sync exception", e)
        }
    }

    /**
     * 启动时检查并同步（如果云端有更新则同步，每天只检查一次）
     */
    suspend fun checkAndSyncOnStart() {
        val mgr = webDavManager ?: return
        val repo = repository ?: return
        if (!mgr.isConfigured()) return
        if (!isOnline()) return

        // 检查是否已检查过（24小时内）
        val lastCheckTime = pendingPrefs.getLong(KEY_LAST_AUTO_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < AUTO_CHECK_INTERVAL_MS) {
            Log.d(TAG, "Already checked today, skipping")
            return
        }

        Log.d(TAG, "Checking remote changes on start...")
        try {
            val hasChanges = mgr.hasRemoteChanges(repo)

            if (hasChanges) {
                Log.d(TAG, "Remote has changes, syncing...")
                syncNow()
            } else {
                Log.d(TAG, "No remote changes")
            }
            // 同步完成（或无需同步）后才更新检查时间
            pendingPrefs.edit().putLong(KEY_LAST_AUTO_CHECK_TIME, now).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Check remote changes failed", e)
        }
    }

    // ============ 待同步队列管理 ============

    private fun addPendingChange(change: PendingChange) {
        val changes = getPendingChanges().toMutableList()
        // 如果已有同一课程的相同操作，替换；否则添加
        val existingIndex = changes.indexOfFirst {
            it.courseId == change.courseId && it.action == change.action
        }
        if (existingIndex >= 0) {
            changes[existingIndex] = change
        } else {
            // 对于 delete 操作，移除之前该课程的 add/update
            if (change.action == "delete") {
                changes.removeAll { it.courseId == change.courseId && it.action != "delete" }
            }
            changes.add(change)
        }
        savePendingChanges(changes)
        _pendingCount.value = changes.size
    }

    private fun getPendingChanges(): List<PendingChange> {
        val json = pendingPrefs.getString(KEY_PENDING_CHANGES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PendingChange>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePendingChanges(changes: List<PendingChange>) {
        pendingPrefs.edit().putString(KEY_PENDING_CHANGES, gson.toJson(changes)).apply()
        _pendingCount.value = changes.size
    }

    private fun clearPendingQueue() {
        pendingPrefs.edit().remove(KEY_PENDING_CHANGES).apply()
        _pendingCount.value = 0
    }

    private fun loadPendingQueue() {
        _pendingCount.value = getPendingChanges().size
    }

    // ============ 网络状态 ============

    private fun isOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available, processing queue...")
                if (_pendingCount.value > 0) {
                    val now = System.currentTimeMillis()
                    val timeUntilNextSync = (SYNC_COOLDOWN_MS - (now - lastSyncCompletedTime)).coerceAtLeast(0L)
                    pendingSyncJob?.cancel()
                    if (timeUntilNextSync <= 0) {
                        scope.launch { syncNow() }
                    } else {
                        pendingSyncJob = scope.launch {
                            kotlinx.coroutines.delay(timeUntilNextSync)
                            syncNow()
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                if (_syncState.value is SyncState.Syncing) {
                    _syncState.value = SyncState.Offline
                }
            }
        }
        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    /**
     * 停止同步管理器
     */
    fun stop() {
        repository?.onCourseChanged = null
        networkCallback?.let { cm.unregisterNetworkCallback(it) }
        networkCallback = null
    }
}
