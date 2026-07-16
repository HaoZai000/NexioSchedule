package com.haooz.chedule.ui.component

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

object BlurState {
    val isAnySheetVisible = mutableStateOf(false)
    val progress = mutableFloatStateOf(0f)

    fun show() { isAnySheetVisible.value = true }
    fun hide() { isAnySheetVisible.value = false; progress.floatValue = 0f }
    fun updateProgress(value: Float) { progress.floatValue = value.coerceIn(0f, 1f) }
}