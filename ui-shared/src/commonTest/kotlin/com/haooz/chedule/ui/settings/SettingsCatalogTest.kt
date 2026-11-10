package com.haooz.chedule.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 设置目录的静态一致性：分类/分组/条目是硬编码枚举，改一处漏一处最容易在运行时
 * 表现为「分类点进去是空白」。这里把结构性约束钉死在测试里。
 */
class SettingsCatalogTest {

    @Test
    fun `容器型分类每个都有分组且分组内有条目`() {
        // 整页型分类的二级页不是行列表：「更新」= 设置+日志；「备份与同步」= 本地备份内容 + WebDAV；
        // 「数据管理」= 课表导入 + 导出 + 页尾数据源分组
        val fullPageCategories =
            setOf(SettingsCategory.Update, SettingsCategory.Sync, SettingsCategory.Data)
        SettingsCategory.entries.filter { !it.isDirect && it !in fullPageCategories }
            .forEach { category ->
                val sections = category.sections
                assertTrue(sections.isNotEmpty(), "分类 ${category.key} 没有任何分组")
                sections.forEach { section ->
                    assertTrue(
                        section.entries.isNotEmpty(),
                        "分组 ${section.name}（分类 ${category.key}）没有任何条目",
                    )
                }
            }
        SettingsCategory.entries.filter { it.isDirect || it in fullPageCategories }
            .forEach { category ->
                assertTrue(category.sections.isEmpty(), "分类 ${category.key} 不应有分组")
            }
    }

    @Test
    fun `课表分类的分组顺序与独立卡片约定`() {
        assertEquals(
            listOf(
                SettingsSection.Semester,
                SettingsSection.ClassTime,
                SettingsSection.CourseDisplay,
                SettingsSection.CourseExtra,
                SettingsSection.CourseMisc,
            ),
            SettingsCategory.Course.sections,
        )
        // 上课时间与节假日同卡；开启新学期独占一张卡
        assertEquals(
            listOf(SettingsEntry.CourseTime, SettingsEntry.Holiday),
            SettingsSection.ClassTime.entries,
        )
        assertEquals(listOf(SettingsEntry.NewSemester), SettingsSection.CourseMisc.entries)
        // 排班模式：开启新学期整卡收起，不留空卡
        assertTrue(SettingsSection.CourseMisc !in SettingsCategory.Course.visibleSections(isShiftMode = true))
    }

    @Test
    fun `直达型分类指向真实的页面路由`() {
        assertEquals(
            com.haooz.chedule.ui.navigation.AppRoute.CourseReminder,
            SettingsCategory.Reminder.directRoute,
        )
        assertEquals(
            com.haooz.chedule.ui.navigation.AppRoute.WidgetIntro,
            SettingsCategory.Widget.directRoute,
        )
        assertEquals(
            com.haooz.chedule.ui.navigation.AppRoute.About,
            SettingsCategory.About.directRoute,
        )
        // 「捐赠」紧跟「关于」，同为直达型
        assertEquals(
            com.haooz.chedule.ui.navigation.AppRoute.AppreciateAuthor,
            SettingsCategory.Donate.directRoute,
        )
    }

    @Test
    fun `分类 key 可往返且全表单分类可还原`() {
        SettingsCategory.entries.forEach { category ->
            assertEquals(category, SettingsCategory.fromKey(category.key))
            assertNull(SettingsCategory.fromKey("no_such_category"))
        }
    }

    @Test
    fun `常规模式可见全部分类，排班模式只剩课表，更新分类随平台隐藏`() {
        assertEquals(SettingsCategory.entries.size, SettingsCategory.visible(true, false).size)
        assertTrue(SettingsCategory.Update !in SettingsCategory.visible(false, false))
        assertEquals(listOf(SettingsCategory.Course), SettingsCategory.visible(true, true))
    }

    @Test
    fun `主页卡片分组符合约定`() {
        assertEquals(
            listOf(
                listOf(SettingsCategory.Course),
                listOf(SettingsCategory.Reminder, SettingsCategory.Widget),
                listOf(SettingsCategory.Personalization, SettingsCategory.Data, SettingsCategory.Sync),
                listOf(SettingsCategory.Update, SettingsCategory.About, SettingsCategory.Donate),
            ),
            SettingsCategory.homeGroups(),
        )
        // iOS/鸿蒙：末组只剩「关于 + 捐赠」，不出现空卡
        assertEquals(
            listOf(
                listOf(SettingsCategory.Course),
                listOf(SettingsCategory.Reminder, SettingsCategory.Widget),
                listOf(SettingsCategory.Personalization, SettingsCategory.Data, SettingsCategory.Sync),
                listOf(SettingsCategory.About, SettingsCategory.Donate),
            ),
            SettingsCategory.homeGroups(showUpdateSettings = false),
        )
        assertEquals(
            listOf(listOf(SettingsCategory.Course)),
            SettingsCategory.homeGroups(isShiftMode = true),
        )
    }

    @Test
    fun `更新分类二级页整页内联，不再有行条目`() {
        // 更新设置与历史日志都由 UpdateSettingsScreen(changelogInline = true) 承载
        assertTrue(SettingsCategory.Update.sections.isEmpty())
        assertTrue(SettingsCategory.Update.isDirect.not())
    }

    @Test
    fun `排班模式只隐藏约定的条目`() {
        val visibleInShift = SettingsSection.entries
            .flatMap { it.visibleEntries(isShiftMode = true) }
            .toSet()
        assertTrue(SettingsEntry.ShowNonCurrentWeek !in visibleInShift)
        assertTrue(SettingsEntry.NewSemester !in visibleInShift)
        assertTrue(SettingsEntry.StartDate in visibleInShift)
        assertTrue(SettingsEntry.CourseTime in visibleInShift)
    }

    @Test
    fun `排班模式在课表分组末尾追加对比课表`() {
        val normal = SettingsCategory.Course.visibleSections(isShiftMode = false)
        val shift = SettingsCategory.Course.visibleSections(isShiftMode = true)
        assertTrue(SettingsSection.ShiftCompare !in normal)
        assertEquals(SettingsSection.ShiftCompare, shift.last())
    }

    @Test
    fun `容器型分类路由 id 能往返还原`() {
        SettingsCategory.entries.filter { !it.isDirect }.forEach { category ->
            val route = com.haooz.chedule.ui.navigation.AppRoute.SettingsCategoryDetail(category.key)
            val restored = com.haooz.chedule.ui.navigation.AppRoute.fromId(route.id)
            assertEquals(route, restored)
        }
    }

    @Test
    fun `直达型分类与未知 key 都不是合法二级页路由`() {
        // 直达型没有二级页：主页点它直接进目标页面，id 还原时应丢弃而不是渲染空白页
        SettingsCategory.entries.filter { it.isDirect }.forEach { category ->
            assertNull(
                com.haooz.chedule.ui.navigation.AppRoute
                    .fromId("settings_category/${category.key}")
            )
        }
        assertNull(com.haooz.chedule.ui.navigation.AppRoute.fromId("settings_category/unknown"))
        assertNull(com.haooz.chedule.ui.navigation.AppRoute.fromId("settings_category/"))
    }
}
