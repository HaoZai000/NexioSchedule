// 根脚本只声明插件版本、不 apply：兄弟模块各自写 version 会各建一套类加载器，
// 而 Kotlin/Native toolchain 是共享 build service，配置期会报 classloader 不一致。
plugins {
    kotlin("multiplatform") version "2.2.21-1.2.0-04" apply false
    id("org.jetbrains.compose") version "1.9.2-1.2.0-08" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21-1.2.0-04" apply false
    id("app.cash.sqldelight") version "2.2.1-1.1.0-01" apply false
}
