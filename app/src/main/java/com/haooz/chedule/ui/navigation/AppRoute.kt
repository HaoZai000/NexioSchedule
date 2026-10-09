package com.haooz.chedule.ui.navigation

/**
 * 应用内子页路由表。
 *
 * ## 为什么要有这层（而不是继续用 Intent 跳 Activity）
 *
 * 跨端的硬前提：CMP 在 iOS 上跑在 `UIViewController` 里，**没有 Activity**。
 * 更关键的是 —— **导航状态原本活在 Android 的 Activity back stack 里，iOS 拿不到**。
 * 只要还用 Intent 跳 Activity，那些 Screen 就永远共享不了。
 * 所以「单宿主 + 显式路由表」不是风格选择，是跨端的必要结构。
 *
 * ## 为什么**手写**而不是引入 Navigation 库
 *
 * 1. `androidx.navigation:navigation-compose` 是 **Android-only**，
 *    阶段 5 还得换成 CMP 的 `org.jetbrains.androidx.navigation` —— 白做一遍。
 * 2. 而 CMP 那个 artifact **本机离线缓存里没有**（只有它的 `lifecycle` / `savedstate` /
 *    `navigationevent` 兄弟包），加不上。
 * 3. 路由表本身很小（几十行），而且**将来要整体搬进共享模块**，
 *    手写反而没有「换库」这一步。
 *
 * 这与项目既有的「不引入 Ktor」是同一个判断：**不为了一点便利，把可控的东西换成
 * 会连带升级核心依赖的黑盒**。
 *
 * ## ⚠ [id] 是持久化契约
 *
 * [id] 用于 `rememberSaveable` 的进程/配置重建。**已发布路由的 id 不要改** ——
 * 改了等于用户在多任务切换回来后落回首页。
 * 新增路由随便加；要改 id 就得同时写迁移。
 *
 * ## ⚠ 这里**没有**主界面路由
 *
 * 主界面（`CourseScheduleApp`）是**常驻底座**，不参与路由的 save/restore ——
 * 否则切到子页再回来，`remember { mutableStateOf }` 之类非 saveable 状态全丢
 * （见 [AppRouter] 的 KDoc）。将来若要把某个页面"变成主 tab"，应该改
 * `CourseScheduleApp` 内部的 pager，而不是在这里加一条主界面路由。
 */
sealed interface AppRoute {

    /** 稳定标识。见接口 KDoc：**已发布的不要改**。 */
    val id: String

    /** 关于页。 */
    data object About : AppRoute {
        override val id: String get() = "about"
    }

    /** 更新日志。 */
    data object Changelog : AppRoute {
        override val id: String get() = "changelog"
    }

    /** 开源协议全文。 */
    data object License : AppRoute {
        override val id: String get() = "license"
    }

    /** 隐私政策全文。 */
    data object PrivacyPolicy : AppRoute {
        override val id: String get() = "privacy_policy"
    }

    companion object {
        /** 按 [id] 还原路由；未知 id 返回 null（版本回退/脏数据时由调用方兜底）。 */
        fun fromId(id: String): AppRoute? = when (id) {
            About.id -> About
            Changelog.id -> Changelog
            License.id -> License
            PrivacyPolicy.id -> PrivacyPolicy
            else -> null
        }
    }
}
