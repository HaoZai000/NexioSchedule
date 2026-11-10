package com.haooz.chedule

import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.settings.SettingsCategory
import com.haooz.chedule.ui.utils.HostOverlayEntry
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentEditorChrome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NativeDocumentHostTest {
    /** 二级/三级页全部由 UIKit 栈承载：Compose 的 AppRouter 栈在 iOS 上不接任何路由。 */
    @Test
    fun documentNavigationStaysInsideTheNativeStack() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.EducationalImport)
        host.router.navigate(AppRoute.Changelog)
        assertEquals(2, host.nativeRouteIds.size)
        assertEquals(0, host.router.depth)
        assertEquals("选择学校", host.nativeRouteTitle(host.nativeRouteIds.first()))
        assertEquals("更新日志", host.nativeRouteTitle(host.nativeRouteIds.last()))
    }

    /** 连点同一入口只压一层（与 AppRouter.navigate 同一规则）：否则要按多次返回才回得去。 */
    @Test
    fun repeatedNavigationToTheSameDocumentKeepsASingleLayer() {
        val host = IosAppHost()
        host.navigateNativeDocument("license")
        val id = host.nativeRouteIds.single()
        host.navigateNativeDocument("license")
        host.navigateNativeDocument("license")
        assertEquals(listOf(id), host.nativeRouteIds)
        assertEquals("开源协议", host.nativeRouteTitle(id))
    }

    /** 只挡「已在栈顶」：A → B → A 仍是合法路径，栈内同名页照旧顺延 `#2`、各有独立状态。 */
    @Test
    fun sameDocumentCanStillBePushedAgainOnceItLeftTheTop() {
        val host = IosAppHost()
        host.navigateNativeDocument("license")
        host.navigateNativeDocument("changelog")
        host.navigateNativeDocument("license")
        val ids = host.nativeRouteIds
        assertEquals(3, ids.size)
        assertNotEquals(ids[0], ids[2])
        assertEquals("license", host.nativePage(ids[0]).stateKey)
        assertEquals("license#2", host.nativePage(ids[2]).stateKey)
    }

    @Test
    fun completedPopOnlyRemovesNativePagesAboveTheConfirmedDepth() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        host.navigateNativeDocument("privacy_policy")
        val first = host.nativeRouteIds.first()
        host.completeNativePop(1)
        assertEquals(listOf(first), host.nativeRouteIds)
        host.completeNativePop(0)
        assertEquals(emptyList(), host.nativeRouteIds)
    }

    @Test
    fun pageStateStaysLiveUntilUIKitConfirmsThePopThenIsDestroyed() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        val id = host.nativeRouteIds.single()
        val page = host.nativePage(id)
        var value = "original"
        page.registry.registerProvider("form") { value }

        // 只是「请求出栈」：UIKit 还没确认，退出动画要画完，这一层与它的状态都还活着
        assertTrue(host.router.popBack())
        assertEquals(emptyList(), host.nativeRouteIds)
        assertSame(page, host.nativePage(id))
        value = "saved after completion request"

        // 确认即销毁（同 AppNavHost 的 stateHolder.removeState）：重进是全新初始状态
        host.completeNativePop(0)
        host.navigateNativeDocument("changelog")
        val reopened = host.nativePage(host.nativeRouteIds.single())
        assertNotSame(page, reopened)
        assertNull(reopened.registry.consumeRestored("form"))
        assertNull(reopened.controller)
    }

    @Test
    fun undisplayedPushCanBeReconciledWithoutAUIKitTransition() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        assertTrue(host.router.popBack())
        assertTrue(host.nativePopPending)
        assertEquals(emptyList(), host.nativeRouteIds)
        // synchronizeStack confirms the already-matching empty UIKit stack (no didShow).
        host.completeNativePop(0)
        assertFalse(host.nativePopPending)
        assertFalse(host.nativePageActive)
        host.navigateNativeDocument("license")
        assertEquals(1, host.nativeRouteIds.size)
    }

    /** 出栈即销毁；栈里的下层仍是同一个实例，registry 与组合都还在。 */
    @Test
    fun poppedPageStateIsDestroyedWhilePagesBelowThePopSurvive() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        val parent = host.nativePage(host.nativeRouteIds.single())
        host.navigateNativeDocument("license")
        val child = host.nativePage(host.nativeRouteIds.last())
        child.registry.registerProvider("scroll") { listOf(7, 40) }

        // 3 级 → 2 级：只销毁被弹出的那层
        host.completeNativePop(1)
        assertSame(parent, host.nativePage(host.nativeRouteIds.single()))

        // 重进二级页：状态已随出栈销毁，重新给初始值
        host.navigateNativeDocument("license")
        val reopened = host.nativePage(host.nativeRouteIds.last())
        assertNotSame(child, reopened)
        assertEquals("license", reopened.stateKey)
        assertNull(reopened.registry.consumeRestored("scroll"))
    }

    @Test
    fun inFlightAndCancelledTransitionsDoNotChangeTheNativeStack() {
        val host = IosAppHost()
        host.navigateNativeDocument("privacy_policy")
        val ids = host.nativeRouteIds
        host.setNativeTransitionInFlight(true)
        host.router.navigate(AppRoute.License)
        assertTrue(host.router.popBack())
        assertEquals(ids, host.nativeRouteIds)
        host.setNativeTransitionInFlight(false)
        assertEquals(ids, host.nativeRouteIds)
        host.completeNativePop(100)
        assertEquals(ids, host.nativeRouteIds)
        // 负数夹到 0：UIKit 报来的深度越界时也要收敛，不能把旧 page 留在栈里（否则重进会复用它的 controller）
        host.completeNativePop(-1)
        assertFalse(host.nativePageActive)
    }

    @Test
    fun nativeActionsCannotFireForCoveredPagesOrDuringTransitions() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        val first = host.nativeRouteIds.single()
        var invocations = 0
        host.nativePage(first).chrome.action = NativeDocumentAction("play.fill", "Test") { invocations++ }
        host.performNativePageAction(first)
        assertEquals(1, invocations)
        host.setNativeTransitionInFlight(true)
        assertFalse(host.nativePageActionEnabled(first))
        host.performNativePageAction(first)
        host.setNativeTransitionInFlight(false)
        host.navigateNativeDocument("license")
        host.performNativePageAction(first)
        assertEquals(1, invocations)
    }

    @Test
    fun allDocumentsEnterTheNativeStackWithTheirTitles() {
        val documents = listOf(
            AppRoute.Changelog to "更新日志",
            AppRoute.License to "开源协议",
            AppRoute.PrivacyPolicy to "隐私政策",
            AppRoute.Communication to "交流与反馈",
            AppRoute.AppreciateAuthor to "捐赠支持",
            AppRoute.UpdateSettings to "更新设置",
            AppRoute.LocalBackup to "本地备份",
            AppRoute.AiImport to "AI 文本导入",
            AppRoute.WidgetIntro to "桌面小部件",
            AppRoute.CourseReminder to "课程提醒",
            AppRoute.HolidaySettings to "节假日与调休",
            AppRoute.WebDavSettings to "WebDAV 云备份",
            AppRoute.ScheduleExport to "课表导出",
            AppRoute.ScheduleBackup to "课表备份",
            AppRoute.ScheduleImport to "课表导入",
            AppRoute.About to "关于应用",
            AppRoute.CourseTimeSettings to "课程时间管理",
        ) + SettingsCategory.entries.filter { !it.isDirect }.map { category ->
            // 设置分类二级页：标题由分类决定，漏一条 iOS 上就会「点了没反应」
            AppRoute.SettingsCategoryDetail(category.key) to category.title()
        }
        for ((route, title) in documents) {
            val host = IosAppHost()
            host.router.navigate(route)
            assertEquals(title, host.nativeRouteTitle(host.nativeRouteIds.single()))
            assertEquals(0, host.router.depth)
            assertTrue(host.router.popBack())
            host.completeNativePop(0)
            assertFalse(host.nativePageActive)
        }
    }

    @Test
    fun backupChildPagesShareOnlyTheNativeStack() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.ScheduleBackup)
        val backup = host.nativeRouteIds.single()
        host.router.navigate(AppRoute.WebDavSettings)
        assertEquals(2, host.nativeRouteIds.size)
        assertTrue(host.router.popBack())
        assertEquals(listOf(backup), host.nativeRouteIds)
        host.completeNativePop(1)
        host.router.navigate(AppRoute.LocalBackup)
        assertEquals(2, host.nativeRouteIds.size)
        assertEquals(0, host.router.depth)
    }

    /** 教务导入也是原生文档：从「课表导入/导出/备份」这些原生页跳进来才不会被上层挡住。 */
    @Test
    fun educationalImportIsNativeSoNestedPagesAreReachable() {
        val host = IosAppHost()
        assertTrue(AppRoute.EducationalImport.isNativeDocument())
        host.router.navigate(AppRoute.EducationalImport)
        assertEquals(1, host.nativeRouteIds.size)
        assertEquals("选择学校", host.nativeRouteTitle(host.nativeRouteIds.single()))
        assertEquals(0, host.router.depth)
    }

    @Test
    fun nativeActionStateAndLatestCallbackKeepTheirOriginalSemantics() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var result = ""
        chrome.action = NativeDocumentAction("arrow.clockwise", "更新", enabled = false, busy = true) { result = "busy" }
        assertTrue(host.nativePageActionBusy(id))
        assertFalse(host.nativePageActionEnabled(id))
        host.performNativePageAction(id)
        assertEquals("", result)
        chrome.action = NativeDocumentAction("checkmark", "已连接", successful = true) { result = "latest callback" }
        assertTrue(host.nativePageActionSuccessful(id))
        assertEquals("checkmark", host.nativePageActionSymbol(id))
        assertEquals("已连接", host.nativePageActionLabel(id))
        host.performNativePageAction(id)
        assertEquals("latest callback", result)
    }

    @Test
    fun aboutChildrenAndReturnStayWithinTheNativeStack() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.About)
        val about = host.nativeRouteIds.single()
        host.router.navigate(AppRoute.Changelog)
        assertEquals(2, host.nativeRouteIds.size)
        assertFalse(host.router.hasOverlay)
        assertTrue(host.router.popBack())
        host.completeNativePop(1)
        assertEquals(listOf(about), host.nativeRouteIds)
        assertTrue(host.router.popBack())
        host.completeNativePop(0)
        assertFalse(host.nativePageActive)
        assertFalse(host.router.hasOverlay)
    }

    @Test
    fun timeEditorOwnsChromeAndBackUntilItsMorphExitCompletes() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var clicks = 0
        chrome.action = NativeDocumentAction("play.fill", "Test") { clicks++ }
        chrome.contentOverlayActive = true
        assertTrue(host.nativeContentOverlayActive)
        assertFalse(host.nativePageActionEnabled(id))
        host.performNativePageAction(id)
        assertEquals(0, clicks)
        assertTrue(host.router.popBack())
        assertFalse(host.nativePopPending)
        host.router.navigate(AppRoute.License)
        assertEquals(listOf(id), host.nativeRouteIds)
        chrome.contentOverlayActive = false
        assertFalse(host.nativeContentOverlayActive)
        assertTrue(host.nativePageActionEnabled(id))
        assertTrue(host.router.popBack())
        assertTrue(host.nativePopPending)
        host.completeNativePop(0)
        assertFalse(host.nativePageActive)
    }

    @Test
    fun nativeEditorOverridesRetainedParentAndRestoresItsLatestChrome() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var cancelled = 0
        var saved = 0
        var parentClicks = 0
        chrome.title = "课程时间管理"
        chrome.contentOverlayActive = true
        chrome.editor = NativeDocumentEditorChrome(
            "编辑作息",
            NativeDocumentAction("xmark", "取消") { cancelled++ },
            NativeDocumentAction("checkmark", "保存并关闭") { saved++ },
        )
        // The live parent can publish again without taking ownership from the editor.
        chrome.action = NativeDocumentAction("arrow.clockwise", "Parent") { parentClicks++ }
        assertEquals("编辑作息", host.nativeRouteTitle(id))
        assertEquals("xmark", host.nativePageLeadingSymbol(id))
        assertEquals("checkmark", host.nativePageActionSymbol(id))
        assertTrue(host.nativePageActionEnabled(id))
        host.performNativePageAction(id)
        host.performNativePageLeadingAction(id)
        assertEquals(1, saved)
        assertEquals(1, cancelled)
        assertEquals(0, parentClicks)
        assertTrue(host.router.popBack())
        assertFalse(host.nativePopPending)
        host.router.navigate(AppRoute.License)
        assertEquals(listOf(id), host.nativeRouteIds)
        // The editor owns back even if the parent flag has already been cleared.
        chrome.contentOverlayActive = false
        assertTrue(host.nativeContentOverlayActive)
        chrome.editor = null
        assertEquals("课程时间管理", host.nativeRouteTitle(id))
        assertEquals("chevron.backward", host.nativePageLeadingSymbol(id))
        assertEquals("arrow.clockwise", host.nativePageActionSymbol(id))
        host.performNativePageLeadingAction(id)
        assertTrue(host.nativePopPending)
    }

    @Test
    fun nativeEditorControlsAreGuardedDuringClosingAndOuterTransitions() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var clicks = 0
        val cancel = NativeDocumentAction("xmark", "取消") { clicks++ }
        val save = NativeDocumentAction("checkmark", "保存并关闭") { clicks++ }
        chrome.editor = NativeDocumentEditorChrome("编辑作息", cancel, save)
        host.setNativeTransitionInFlight(true)
        assertFalse(host.nativePageLeadingEnabled(id))
        assertFalse(host.nativePageActionEnabled(id))
        host.performNativePageLeadingAction(id)
        host.performNativePageAction(id)
        host.setNativeTransitionInFlight(false)
        chrome.editor = NativeDocumentEditorChrome(
            "编辑作息", cancel.copy(enabled = false), save.copy(enabled = false),
        )
        host.performNativePageLeadingAction(id)
        host.performNativePageAction(id)
        assertEquals(0, clicks)
        assertFalse(host.nativePopPending)
    }

    @Test
    fun parentBackAcceptsClosingMorphTapAndPopsOnlyAfterSaveAndExit() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        chrome.contentOverlayActive = true
        chrome.editor = NativeDocumentEditorChrome(
            "编辑作息",
            NativeDocumentAction("xmark", "取消") {},
            NativeDocumentAction("checkmark", "保存并关闭") {},
        )
        // Entering chrome is usable from the first frame, without progress thresholds.
        assertTrue(host.nativePageActionEnabled(id))
        assertTrue(host.nativePageLeadingEnabled(id))
        // Closing intent restores the parent's back item immediately.
        chrome.editor = null
        assertEquals("chevron.backward", host.nativePageLeadingSymbol(id))
        assertTrue(host.nativePageLeadingEnabled(id))
        host.performNativePageLeadingAction(id)
        host.performNativePageLeadingAction(id)
        host.reconcileNativeEditorExit()
        assertFalse(host.nativePopPending)
        assertEquals(listOf(id), host.nativeRouteIds)
        // The original save callback runs before the content overlay is disposed.
        chrome.contentOverlayActive = false
        host.reconcileNativeEditorExit()
        assertTrue(host.nativePopPending)
        assertEquals(emptyList(), host.nativeRouteIds)
        host.completeNativePop(0)
        host.reconcileNativeEditorExit()
        assertFalse(host.nativePopPending)
    }

    @Test
    fun unknownOrUnmigratedRoutesDoNotEnterTheNativeStack() {
        val host = IosAppHost()
        host.navigateNativeDocument("missing")
        assertEquals(emptyList(), host.nativeRouteIds)
    }

    @Test
    fun courseManagementUsesNativeStackAndBlocksPopDuringMorph() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseManage)
        val id = host.nativeRouteIds.single()
        assertEquals("课程管理", host.nativeRouteTitle(id))
        assertEquals("chevron.backward", host.nativePageLeadingSymbol(id))
        assertEquals(0, host.router.depth)
        val chrome = host.nativePage(id).chrome
        chrome.contentOverlayActive = true
        host.performNativePageLeadingAction(id)
        assertEquals(listOf(id), host.nativeRouteIds)
        chrome.contentOverlayActive = false
        host.reconcileNativeEditorExit()
        assertEquals(emptyList(), host.nativeRouteIds)
    }

    @Test
    fun rootScheduleLibraryPublishesChromeWithoutChangingNavigationAndSharesInsets() {
        val host = IosAppHost()
        val chrome = host.navigation.scheduleLibraryChrome
        host.navigation.scheduleLibraryActive = true
        host.updateTopBarInset(44.0)
        assertTrue(host.scheduleLibraryActive)
        assertEquals("全部课表", host.scheduleLibraryTitle)
        assertEquals(44.0, chrome.barInset)
        assertEquals(44.0, host.navigation.topBarInset)
        assertFalse(host.nativePageActive)
        assertEquals(0, host.router.depth)
        var closed = 0
        chrome.leading = NativeDocumentAction("chevron.backward", "返回") { closed++ }
        assertEquals("chevron.backward", host.scheduleLibraryLeadingSymbol())
        assertEquals("返回", host.scheduleLibraryLeadingLabel())
        host.performScheduleLibraryLeadingAction()
        assertEquals(1, closed)
        assertFalse(host.nativePopPending)
    }

    @Test
    fun rootScheduleLibraryLayerIsBelowNativeChromeInsteadOfAFloatingOverlay() {
        val host = IosAppHost()
        val layer = checkNotNull(host.navigation.scheduleLibraryOverlays)
        val entry = HostOverlayEntry()
        layer.entries.add(entry)
        host.navigation.scheduleLibraryActive = true
        var clicks = 0
        host.navigation.scheduleLibraryChrome.leading = NativeDocumentAction("chevron.backward", "返回") { clicks++ }
        host.navigation.scheduleLibraryChrome.action = NativeDocumentAction("plus", "添加课表") { clicks++ }
        host.navigation.scheduleLibraryChrome.secondaryAction = NativeDocumentAction("folder.badge.plus", "新建文件夹") { clicks++ }
        assertFalse(host.overlayActive)
        entry.active = true
        assertTrue(host.overlayActive)
        assertFalse(host.floatingOverlayActive)
        assertFalse(host.nativePageActive)
        assertTrue(host.scheduleLibraryLeadingEnabled())
        assertTrue(host.scheduleLibraryActionEnabled())
        assertTrue(host.scheduleLibrarySecondaryActionEnabled())
        host.performScheduleLibraryLeadingAction()
        host.performScheduleLibraryAction()
        host.performScheduleLibrarySecondaryAction()
        assertEquals(3, clicks)
        entry.active = false
        assertFalse(host.overlayActive)
        assertFalse(host.floatingOverlayActive)
    }

    @Test
    fun rootScheduleLibraryEditCanCloseWhileTrailingActionsAreBlocked() {
        val host = IosAppHost()
        host.navigation.scheduleLibraryActive = true
        val chrome = host.navigation.scheduleLibraryChrome
        var exits = 0
        var added = 0
        chrome.title = "编辑课表"
        chrome.contentOverlayActive = true
        chrome.leading = NativeDocumentAction("xmark", "退出编辑") {
            exits++
            chrome.contentOverlayActive = false
        }
        chrome.action = NativeDocumentAction("plus", "添加课表") { added++ }
        chrome.secondaryAction = NativeDocumentAction("folder.badge.plus", "新建文件夹") { added++ }
        assertEquals("编辑课表", host.scheduleLibraryTitle)
        assertEquals("xmark", host.scheduleLibraryLeadingSymbol())
        assertTrue(host.scheduleLibraryLeadingEnabled())
        assertFalse(host.scheduleLibraryActionEnabled())
        assertFalse(host.scheduleLibrarySecondaryActionEnabled())
        host.performScheduleLibraryAction()
        host.performScheduleLibrarySecondaryAction()
        assertEquals(0, added)
        host.performScheduleLibraryLeadingAction()
        assertEquals(1, exits)
        assertTrue(host.scheduleLibraryActionEnabled())
        assertTrue(host.scheduleLibrarySecondaryActionEnabled())
        assertFalse(host.nativePopPending)
    }

    @Test
    fun rootScheduleLibraryControlsRemainEnabledDuringMorphButNotWhenCoveredOrInactive() {
        val host = IosAppHost()
        val navigation = host.navigation
        val chrome = navigation.scheduleLibraryChrome
        var calls = 0
        chrome.leading = NativeDocumentAction("chevron.backward", "返回") { calls++ }
        chrome.action = NativeDocumentAction("plus", "添加课表") { calls++ }
        chrome.secondaryAction = NativeDocumentAction("folder.badge.plus", "新建文件夹") { calls++ }
        fun invokeAll() {
            host.performScheduleLibraryLeadingAction()
            host.performScheduleLibraryAction()
            host.performScheduleLibrarySecondaryAction()
        }
        invokeAll()
        assertEquals(0, calls)
        navigation.scheduleLibraryActive = true
        navigation.scheduleLibraryTransitionActive = true
        assertTrue(host.scheduleLibraryLeadingEnabled())
        assertTrue(host.scheduleLibraryActionEnabled())
        assertTrue(host.scheduleLibrarySecondaryActionEnabled())
        invokeAll()
        assertEquals(3, calls)
        navigation.scheduleLibraryTransitionActive = false
        invokeAll()
        assertEquals(6, calls)
        host.router.navigate(AppRoute.License)
        invokeAll()
        assertEquals(6, calls)
        assertFalse(host.scheduleLibraryLeadingEnabled())
        host.completeNativePop(0)
        invokeAll()
        assertEquals(9, calls)
        navigation.scheduleLibraryActive = false
        invokeAll()
        assertEquals(9, calls)
    }

    @Test
    fun rootScheduleLibraryUsesLatestCallbacksAndHonorsBusyAndEnabledState() {
        val host = IosAppHost()
        host.navigation.scheduleLibraryActive = true
        val chrome = host.navigation.scheduleLibraryChrome
        var calls = 0
        val leading = NativeDocumentAction("xmark", "退出编辑") { calls++ }
        val action = NativeDocumentAction("plus", "添加课表") { calls++ }
        val secondary = NativeDocumentAction("folder.badge.plus", "新建文件夹") { calls++ }
        chrome.leading = leading.copy(enabled = false)
        chrome.action = action.copy(enabled = false)
        chrome.secondaryAction = secondary.copy(enabled = false)
        host.performScheduleLibraryLeadingAction()
        host.performScheduleLibraryAction()
        host.performScheduleLibrarySecondaryAction()
        assertEquals(0, calls)
        chrome.leading = leading.copy(busy = true)
        chrome.action = action.copy(busy = true)
        chrome.secondaryAction = secondary.copy(busy = true)
        assertFalse(host.scheduleLibraryLeadingEnabled())
        assertFalse(host.scheduleLibraryActionEnabled())
        assertFalse(host.scheduleLibrarySecondaryActionEnabled())
        assertTrue(host.scheduleLibraryActionBusy())
        assertTrue(host.scheduleLibrarySecondaryActionBusy())
        host.performScheduleLibraryLeadingAction()
        host.performScheduleLibraryAction()
        host.performScheduleLibrarySecondaryAction()
        assertEquals(0, calls)
        chrome.leading = leading.copy(onClick = { calls += 10 })
        chrome.action = action.copy(successful = true, onClick = { calls += 20 })
        chrome.secondaryAction = secondary.copy(successful = true, onClick = { calls += 30 })
        assertEquals("plus", host.scheduleLibraryActionSymbol())
        assertEquals("添加课表", host.scheduleLibraryActionLabel())
        assertEquals("folder.badge.plus", host.scheduleLibrarySecondaryActionSymbol())
        assertEquals("新建文件夹", host.scheduleLibrarySecondaryActionLabel())
        assertTrue(host.scheduleLibraryActionSuccessful())
        assertTrue(host.scheduleLibrarySecondaryActionSuccessful())
        host.performScheduleLibraryLeadingAction()
        host.performScheduleLibraryAction()
        host.performScheduleLibrarySecondaryAction()
        assertEquals(60, calls)
    }

    @Test
    fun mainHeaderActionDispatchInvokesCallbacks() {
        val host = IosAppHost()
        assertFalse(host.showsNativeHeader)
        var switched = false
        var jumpedWeek = false
        var courseManaged = false
        var customized = false
        var jumpedDate = false

        host.performMainAction("switchSchedule")
        assertFalse(switched)

        // Inject callbacks via HostNavigationState
        val navigation = host.navigation
        navigation.showsNativeHeader = true
        navigation.onOpenSwitchSchedule = { switched = true }
        navigation.onJumpWeek = { jumpedWeek = true }
        navigation.onCourseManage = { courseManaged = true }
        navigation.onEnterCustomize = { customized = true }
        navigation.onJumpToDate = { jumpedDate = true }

        assertTrue(host.showsNativeHeader)
        host.performMainAction("switchSchedule")
        assertTrue(switched)
        host.performMainAction("jumpWeek")
        assertTrue(jumpedWeek)
        host.performMainAction("courseManage")
        assertTrue(courseManaged)
        host.performMainAction("customize")
        assertTrue(customized)
        host.performMainAction("jumpToDate")
        assertTrue(jumpedDate)
    }
}
