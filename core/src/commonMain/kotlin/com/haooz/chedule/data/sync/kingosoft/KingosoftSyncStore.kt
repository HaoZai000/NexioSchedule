package com.haooz.chedule.data.sync.kingosoft

import com.haooz.chedule.data.platform.displayText
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.KeyValueEditor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KingosoftSyncConfig(
    val jwBaseUrl: String = "", val encrptKey: String = "", val userId: String = "",
    val userType: String = "STU", val autoSyncEnabled: Boolean = false,
    /** Course IDs must survive renaming a schedule, but not deleting it and reusing its name. */
    val syncScopeId: String = "",
) {
    val isComplete: Boolean get() = jwBaseUrl.isNotBlank() && encrptKey.isNotBlank() && userId.isNotBlank() && userType.isNotBlank()

    fun validate() {
        require(isComplete) { displayText("请先填完同步配置", "Complete the sync settings first") }
        require(Regex("""https?://[^/?#\s]+(?:/[^?#\s]*)?""").matches(jwBaseUrl)) {
            displayText("请输入完整的 HTTP 或 HTTPS 教务地址", "Enter a full HTTP or HTTPS academic system URL")
        }
        require('&' !in userId && '&' !in userType) { displayText("学号和用户类型不能包含 &", "User ID and type cannot contain &") }
    }
}

object KingosoftSyncStore {
    private val prefs get() = AppStorage.store("kingosoft_sync")
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    fun getConfig(id: String) = KingosoftSyncConfig(
        jwBaseUrl = prefs.getString("url:$id", ""), encrptKey = prefs.getString("key:$id", ""),
        userId = prefs.getString("user:$id", ""), userType = prefs.getString("type:$id", "STU"),
        autoSyncEnabled = prefs.getBoolean("auto:$id", false), syncScopeId = prefs.getString("scope:$id", ""),
    )

    fun saveConfig(id: String, config: KingosoftSyncConfig): KingosoftSyncConfig {
        require(id.isNotBlank())
        config.validate()
        val previous = getConfig(id)
        val next = config.copy(syncScopeId = previous.syncScopeId.ifEmpty { randomUuidV4() })
        prefs.edit {
            writeConfig(id, next)
            if (previous.copy(autoSyncEnabled = next.autoSyncEnabled) != next) {
                remove("at:$id")
                remove("summary:$id")
            }
        }
        _revision.update { it + 1 }
        return next
    }

    private fun KeyValueEditor.writeConfig(id: String, config: KingosoftSyncConfig) {
        putString("url:$id", config.jwBaseUrl)
        putString("key:$id", config.encrptKey)
        putString("user:$id", config.userId)
        putString("type:$id", config.userType)
        putBoolean("auto:$id", config.autoSyncEnabled)
        putString("scope:$id", config.syncScopeId)
    }

    fun lastSyncAtMillis(id: String) = prefs.getLong("at:$id", 0L)
    fun lastSyncSummary(id: String) = prefs.getString("summary:$id", "")

    internal fun recordSuccess(id: String, at: Long, summary: String, importedConfig: KingosoftSyncConfig? = null) {
        prefs.edit {
            if (importedConfig != null) writeConfig(id, importedConfig)
            putLong("at:$id", at)
            putString("summary:$id", summary)
        }
        _revision.update { it + 1 }
    }

    private fun KeyValueEditor.clearSchedule(id: String) {
        for (field in listOf("url", "key", "user", "type", "auto", "scope", "at", "summary")) remove("$field:$id")
    }

    fun onScheduleRenamed(oldId: String, newId: String) {
        if (oldId == newId || !prefs.contains("scope:$oldId")) return
        val config = getConfig(oldId)
        val at = lastSyncAtMillis(oldId)
        val summary = lastSyncSummary(oldId)
        prefs.edit {
            writeConfig(newId, config)
            putLong("at:$newId", at)
            putString("summary:$newId", summary)
            clearSchedule(oldId)
        }
        _revision.update { it + 1 }
    }

    fun onScheduleDeleted(id: String) {
        if (!prefs.contains("scope:$id")) return
        prefs.edit { clearSchedule(id) }
        _revision.update { it + 1 }
    }

    fun deleteConfig(id: String) = onScheduleDeleted(id)
}
