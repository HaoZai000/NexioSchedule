# org.jetbrains:annotations（编译期桩）

`org.jetbrains:annotations` 只有 JVM 制品，Kotlin/Native（含 ohosArm64）解析不到
`org.intellij.lang.annotations.Language`。backdrop 用它给 shader 字符串加 IDE 提示。

这里放一个**同名同形状的空注解**（我们自己写的，不是上游副本）：注解只在编译期存在，
不影响产物。若将来 `org.jetbrains:annotations` 发布了 native 变体，删掉本目录即可。
