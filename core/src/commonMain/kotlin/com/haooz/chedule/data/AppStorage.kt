package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 全局存储入口。
 *
 * ## 为什么需要它
 *
 * 迁移前 28 个文件、约 700 处调用都是 `context.getSharedPreferences(名字, MODE_PRIVATE)`，
 * `Context` 是 Android 专有类型。要让这些文件进 `:core`，必须把 `Context` 从签名里去掉。
 *
 * 两种做法：
 *
 * **A. 显式传参** —— `fun hasConsented(store: KeyValueStore)`。
 *    依赖关系清晰，但 `PrivacyConsent.hasConsented()` 这类被 UI 到处调用的判断
 *    要在每个调用点都备好 store，改动面大。
 *
 * **B. 全局入口（本方案）** —— 启动时 [init] 一次，业务代码用 [store] 按名字取。
 *    调用点零负担，也是 KMP 项目的常见做法（Android 的 `PreferenceManager`、
 *    iOS 的 `UserDefaults.standard` 都是全局入口）。
 *
 * 选 B。本项目没有 DI 框架，既有的 `StatsReporter.init(context)` /
 * `NexioApplication.onCreate` 已是「启动时初始化单例」的风格，B 与之一致。
 *
 * ## ⚠ 注意：是「按名字取」，不是单一 store
 *
 * 项目有 **17 个不同的偏好文件名**（见 [PrefsFile]）。原来的
 * `getSharedPreferences(name, MODE_PRIVATE)` 每个名字对应一个独立文件，
 * 绝不能合并成一个 —— 合并等于篡改存量数据的落盘位置。
 * 因此这里持有的是**工厂**而不是单个实例。
 *
 * ## ⚠ 为什么不违反「避免全局单例」的原则
 *
 * 计划文档把「Kotlin/Native 线程模型撞全局单例」列为风险 R4 —— 那条针对的是
 * **可变的应用状态**（`CourseRepository` 的缓存），不是存储句柄。
 * `KeyValueStore` 是无状态的读写通道，各平台实现本身线程安全
 * （SharedPreferences / NSUserDefaults 均保证），因此全局入口是安全的。
 *
 * ## 初始化时机
 *
 * 必须在**任何** [store] 读取之前调用。未初始化时抛异常而不是静默返回空数据 ——
 * 静默失败会让「用户设置全部丢失」这种问题极难定位。
 */
object AppStorage {
    // 必须用 kotlin.concurrent.Volatile：不限定的 @Volatile 靠 JVM 专有的默认导入
    // `kotlin.jvm.*` 解析，Kotlin/Native 与 JS 上直接「Unresolved reference」。
    // JVM 上 kotlin.concurrent.Volatile 就是 kotlin.jvm.Volatile 的 typealias，行为零变化。
    @Volatile
    private var factory: ((String) -> KeyValueStore)? = null

    /**
     * 已创建的实例缓存：同一名字返回同一个包装对象，避免重复创建 Editor 适配器。
     *
     * 用 **copy-on-write 的不可变 Map** 而不是 `synchronized` + `LinkedHashMap`：
     * `synchronized` 是 JVM 专有（Kotlin/Native 没有），而这里只是「读多写极少」的缓存
     * —— 每个名字一生只写入一次，重建 Map 的开销可以忽略，换来的是无锁读取与跨平台。
     */
    @Volatile
    private var cache: Map<String, KeyValueStore> = emptyMap()

    /** 是否已初始化。用于初始化前可能被调用的容错分支。 */
    val isReady: Boolean get() = factory != null

    /**
     * @param factory 由平台侧提供：Android 传
     *   `{ name -> context.getSharedPreferences(name, MODE_PRIVATE).asKeyValueStore() }`
     */
    fun init(factory: (String) -> KeyValueStore) {
        this.factory = factory
        cache = emptyMap()
    }

    /** 取某个偏好文件的读写入口。 */
    fun store(name: String): KeyValueStore {
        cache[name]?.let { return it }
        val f = factory ?: error(
            "AppStorage 未初始化：请在 Application.onCreate() 中调用 AppStorage.init { name -> … }"
        )
        // 并发下可能有两个线程同时创建，但结果等价（同一个 name 得到功能相同的包装），
        // 后写入者胜出；不做加锁换取跨平台与无锁读取。
        val created = f(name)
        cache = cache + (name to created)
        return created
    }
}

/*
 * ⚠ 刻意不提供「偏好文件名常量表」。
 *
 * 一开始我建了一张 `PrefsFile` 常量表，凭印象写了 `HOLIDAY = "holiday_prefs"` ——
 * 而真实值是 **`"holiday_settings"`**（见 Holidays.kt 的 `PREFS`）。另有 9 个文件名
 * 散落在各文件的 private 常量里（`appearance_settings` / `course_schedule_prefs` /
 * `reminder_alarm_registry` / `reminder_sent_history` / `day_change_state` /
 * `crash_log_prefs` / `island_countdown_state_test` …）。
 *
 * 一旦集中成表，就有两种名字并存：各文件里既有的正确常量、和表里我手抄的可能错误的常量。
 * **名字写错的后果是用户数据静默读不出来**，且测试很难覆盖（真实数据在设备上）。
 *
 * 所以：**各文件继续用它自己已有的 private 常量**，只把取用方式从
 * `context.getSharedPreferences(PREFS, MODE_PRIVATE)` 换成 `AppStorage.store(PREFS)`。
 * 字符串不动，就没有抄错的机会。
 */
