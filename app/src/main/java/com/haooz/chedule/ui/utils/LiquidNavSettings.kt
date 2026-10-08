package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LiquidNavSettings {

    const val KEY_LIQUID_BOTTOM_NAV = "liquid_bottom_nav_enabled"

    var enabled: Boolean by mutableStateOf(true)
}
