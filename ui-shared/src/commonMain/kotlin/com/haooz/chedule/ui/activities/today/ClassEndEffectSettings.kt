/** 下课庆祝效果偏好（爆炸震动）。跨平台：只走 :core 的 AppStorage。 */
package com.haooz.chedule.ui.activities.today

import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.ui.theme.KEY_HAPTIC_FEEDBACK

/** 下课爆炸震动偏好。 */
object ClassEndEffectSettings {
    const val PREFS_NAME = "app_preferences"

    /** 爆炸震动跟随全局触感开关，无单独开关 */
    fun vibrateEnabled(): Boolean =
        AppStorage.store(PREFS_NAME).getBoolean(KEY_HAPTIC_FEEDBACK, true)
}
