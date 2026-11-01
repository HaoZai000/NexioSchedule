/**
 * `org.intellij.lang.annotations.Language` 的编译期桩（只给 ohos 构建用）。
 *
 * `org.jetbrains:annotations` 是 JVM 制品，native 目标解析不到；而 backdrop 用它给
 * AGSL/SkSL 字符串字面量加 IDE 语法提示（`@Language("AGSL")` 共 8 处）。
 * 注解不参与运行时行为，同名同形状即可。
 *
 * 与 `kyant-shapes/` 不同：这里不是「上游源码副本」，而是**我们写的空实现**，
 * 因为上游本来就没有 native 变体可拿。
 */
package org.intellij.lang.annotations

@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
)
annotation class Language(
    val value: String = "",
    val prefix: String = "",
    val suffix: String = "",
)
