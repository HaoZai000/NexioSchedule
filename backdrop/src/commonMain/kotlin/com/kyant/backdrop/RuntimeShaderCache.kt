package com.kyant.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    // 调用方均先校验 isRuntimeShaderSupported() 才走到这里，运行时安全
    @Suppress("NewApi")
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return ShaderRegistry.runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    fun clear() {
        // C2：lens 的 AGSL shader 全局共享编译，detach 时不清除，各卡片复用同一份已编译程序，避免重复编译。
        // key 与 shader 字符串一一对应（静态常量），可常驻缓存。
    }
}

// C2 优化：进程级共享编译程序池。各卡片仅改自身 RenderEffect 的 uniform，共享同一 RuntimeShader 程序不互相污染。
private object ShaderRegistry {
    val runtimeShaders = mutableMapOf<String, RuntimeShader>()
}
