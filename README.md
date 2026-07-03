# Nexio课程表 v1.0.4

一款基于 Jetpack Compose 的 Android 课程表应用，支持教务系统导入、WebDAV 同步、桌面小组件等功能。

#### 一起交流与讨论 [QQ频道](https://pd.qq.com/s/cfwkl5q9q?b=9)

## 功能特性

- 周视图课程表，支持多周切换
- 添加/编辑/删除课程
- 课程提醒通知（支持超级岛/灵动岛展示）
- 教务系统一键导入
- WebDAV 数据同步与备份
- 桌面小组件（课程预览、今日课程）
- 深色模式适配
- 偏好设置（节次时间、主题等）

## 预览界面

| 主课程表界面效果 | 添加/编辑课程页面 | 接入开源教务导入 | 桌面小部件及深色模式演示 |
|----------|----------|------------|----------|
| ![主课程表](picture/Screenshot_2026-06-30-15-33-20-100_com.haooz.chedule-edit.png) | ![添加课程](picture/Screenshot_2026-06-30-15-41-18-463_com.haooz.chedule-edit.png) | ![教务导入](picture/Screenshot_2026-06-30-15-35-28-017_com.haooz.chedule-edit.png) | ![小组件](picture/Screenshot_2026-06-30-15-35-39-075_com.haooz.chedule-edit.png) |

## 项目结构

```
app/src/main/java/com/haooz/chedule/
├── AboutActivity.kt          // 关于页面
├── MainActivity.kt           // 主页面 - 应用入口
├── CourseReminderActivity.kt // 课程提醒设置
├── CourseTimeSettingsActivity.kt // 课程时间设置
├── EducationalImportActivity.kt  // 教务系统导入
├── PreferenceSettingsActivity.kt // 偏好设置
├── SwitchScheduleActivity.kt     // 切换课程表
├── UpdateSettingsActivity.kt     // 应用更新设置
├── WebDavSettingsActivity.kt     // WebDAV 同步设置
├── WidgetIntroActivity.kt        // 小组件使用引导
├── ThemeUtils.kt                 // 主题工具类
├── AppreciateAuthorActivity.kt   // 赞赏作者页面
├── data/                    // 数据层
│   ├── Course.kt            // 课程数据模型
│   ├── CourseRepository.kt  // 课程数据仓库
│   ├── SyncManager.kt       // 同步管理器
│   ├── WebDavManager.kt     // WebDAV 同步管理
│   └── school/              // 教务系统适配
│       ├── SchoolIndex.kt       // 学校索引数据
│       ├── SchoolRepository.kt  // 学校信息仓库
│       └── ScriptRepository.kt  // 脚本仓库管理
├── effect/                  // 背景特效
├── reminder/                // 课程提醒
│   ├── CourseReminderHelper.kt  // 提醒调度核心
│   ├── IslandNotificationHelper.kt // 超级岛通知
│   └── ...                 // 各类广播接收器
├── shizuku/                 // Shizuku 特权服务
├── ui/                      // UI 层
│   ├── components/          // 通用组件
│   ├── screens/             // 页面
│   ├── theme/               // 主题
│   └── web/                 // WebView 兼容
├── viewmodel/               // ViewModel
└── widget/                  // 桌面小组件
```

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose + Material3
- **UI 组件**: [MiUiX](https://github.com/compose-miuix-ui/miuix)
- **圆角形状**: [Kyant Shapes](https://github.com/Kyant0/kyant-shapes)
- **数据存储**: SharedPreferences + Gson
- **网络同步**: WebDAV
- **脚本引擎**: Rhino (JavaScript)
- **最低支持**: Android 13 (API 33)

## 特别致谢

- [@XingHeYuZhuan](https://github.com/XingHeYuZhuan/shiguang_warehouse.git) - 教务系统导入适配
- [@MiUiX](https://github.com/compose-miuix-ui/miuix) - 应用 UI 框架
- [@Kyant0](https://github.com/Kyant0/kyant-shapes) - 连续曲率圆角
