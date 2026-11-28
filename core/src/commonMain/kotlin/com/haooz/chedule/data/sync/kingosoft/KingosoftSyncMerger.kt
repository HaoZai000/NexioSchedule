package com.haooz.chedule.data.sync.kingosoft

import com.haooz.chedule.data.model.Course
import kotlin.time.Clock

data class KingosoftMergeResult(
    val merged: List<Course>, val added: Int, val updated: Int, val removed: Int,
    /** Only counts courses managed by this sync. */
    val unchanged: Int,
) {
    val hasChanges: Boolean get() = added + updated + removed != 0
}

object KingosoftSyncMerger {
    fun mergeCourses(existing: List<Course>, incoming: List<Course>): KingosoftMergeResult {
        val pending = incoming.associateByTo(linkedMapOf()) { it.id }
        require(pending.size == incoming.size && pending.keys.all { it.startsWith("kg_") }) { "Invalid Kingosoft course IDs" }
        var removed = 0
        var updated = 0
        var unchanged = 0
        val merged = buildList {
            for (local in existing) {
                if (!local.id.startsWith("kg_")) {
                    add(local)
                    continue
                }
                val remote = pending.remove(local.id)
                if (remote == null) {
                    removed++
                    continue
                }
                val next = local.copy(
                    name = remote.name, classroom = remote.classroom, teacher = remote.teacher,
                    dayOfWeek = remote.dayOfWeek, startSection = remote.startSection, endSection = remote.endSection,
                    startWeek = remote.startWeek, endWeek = remote.endWeek, weekType = remote.weekType,
                    selectedWeeks = remote.selectedWeeks,
                )
                if (next == local) {
                    unchanged++
                    add(local)
                } else {
                    updated++
                    add(next.copy(lastModified = Clock.System.now().toEpochMilliseconds()))
                }
            }
            addAll(pending.values)
        }
        return KingosoftMergeResult(merged, pending.size, updated, removed, unchanged)
    }
}
