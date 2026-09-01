package com.kyant.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return ShaderRegistry.runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    fun clear() {
        // C2：lens 的 AGSL shader 全局共享编译，detach 时不再清除，
        // 让各卡片复用同一份已编译的程序对象，避免每张卡重复编译。
        // key 与 shader 字符串一一对应（静态常量），可常驻缓存。
    }
}

// C2 优化：进程级共享的编译程序池。
// 各卡片仅修改自己 RenderEffect 里的 uniform（先 set 再 createRuntimeShaderEffect），
// 共享同一 android.graphics.RuntimeShader 程序本身不互相污染。
private object ShaderRegistry {
    val runtimeShaders = mutableMapOf<String, RuntimeShader>()
}
