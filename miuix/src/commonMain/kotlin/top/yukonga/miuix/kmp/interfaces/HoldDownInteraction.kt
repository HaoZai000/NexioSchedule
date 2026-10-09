// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.interfaces

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction.HoldDown
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction.Release

/**
 * An interaction related to hold down events.
 *
 * @see HoldDown
 * @see Release
 */
interface HoldDownInteraction : Interaction {
    /**
     * An interaction representing a hold down event on a component.
     *
     * @see Release
     */
    class HoldDown : HoldDownInteraction

    /**
     * An interaction representing a [HoldDown] event being released on a component.
     *
     * @property holdDown the source [HoldDown] interaction that is being released
     *
     * @see HoldDown
     */
    class Release(
        val holdDown: HoldDown,
    ) : HoldDownInteraction
}

/**
 * Subscribes to this [MutableInteractionSource] and returns a [State] representing whether this
 * component is selected or not.
 *
 * @return [State] representing whether this component is being focused or not
 */
@Composable
fun InteractionSource.collectIsHeldDownAsState(): State<Boolean> {
    val isHeldDown = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        val holdInteraction = mutableListOf<HoldDown>()
        interactions.collect { interaction ->
            when (interaction) {
                is HoldDown -> holdInteraction.add(interaction)
                is Release -> holdInteraction.remove(interaction.holdDown)
            }
            isHeldDown.value = holdInteraction.isNotEmpty()
        }
    }
    return isHeldDown
}

/**
 * Mirrors [holdDownState] into [interactionSource], releasing any in-flight [HoldDown] on dispose.
 */
@Composable
internal fun HoldDownObserver(
    holdDownState: Boolean,
    interactionSource: MutableInteractionSource,
) {
    val holdDown = remember { mutableStateOf<HoldDown?>(null) }
    LaunchedEffect(holdDownState, interactionSource) {
        suspend fun release() {
            holdDown.value?.let { current ->
                interactionSource.emit(Release(current))
                holdDown.value = null
            }
        }
        if (holdDownState) {
            release()
            val interaction = HoldDown()
            holdDown.value = interaction
            interactionSource.emit(interaction)
        } else {
            release()
        }
    }
    DisposableEffect(interactionSource) {
        onDispose {
            holdDown.value?.let { current ->
                interactionSource.tryEmit(Release(current))
            }
            holdDown.value = null
        }
    }
}
