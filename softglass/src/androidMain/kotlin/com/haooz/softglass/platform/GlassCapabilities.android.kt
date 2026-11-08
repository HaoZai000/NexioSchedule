package com.haooz.softglass.platform

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
actual fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
actual fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

actual fun isRuntimeShaderEffectSupported(): Boolean = isRuntimeShaderSupported()

actual fun isSoftGlassShaderAvailable(): Boolean = Build.VERSION.SDK_INT >= 33
