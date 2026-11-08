package com.haooz.softglass.platform

/** 图层 RenderEffect 是否可用（Android 31+，Skiko 恒 true）。 */
expect fun isRenderEffectSupported(): Boolean

/** 运行时 shader 能否作为 Compose Paint 的 shader 用（Android 33+，Skiko 恒 true）。 */
expect fun isRuntimeShaderSupported(): Boolean

/** 运行时 shader 能否作为图层滤镜用；鸿蒙只通这一条。 */
expect fun isRuntimeShaderEffectSupported(): Boolean

/** 柔光玻璃 shader 是否可用；不可用时玻璃件退回纯色表面。 */
expect fun isSoftGlassShaderAvailable(): Boolean
