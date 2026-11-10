package com.haooz.chedule.ui.activities.main

import androidx.compose.foundation.pager.PagerState
import com.haooz.chedule.ui.components.PageSwitchState

/** 主 tab 位置来源；口径与 [PagerState] 一致。 */
interface MainTabPosition {
    val currentPage: Int
    val offsetFraction: Float
    val pageCount: Int
    val isAnimating: Boolean

    /** 带动画切页。 */
    suspend fun animateTo(page: Int)

    /** 无动画落页。 */
    suspend fun snapTo(page: Int)

    /** 停止动画，停在当前值。 */
    suspend fun cancel()
}

/** 竖排分支：平板 rail / iOS 原生底栏。 */
class PagerMainTabPosition(private val pager: PagerState) : MainTabPosition {
    override val currentPage: Int get() = pager.currentPage
    override val offsetFraction: Float get() = pager.currentPageOffsetFraction
    override val pageCount: Int get() = pager.pageCount
    override val isAnimating: Boolean get() = pager.isScrollInProgress

    override suspend fun animateTo(page: Int) = pager.animateMainTabTo(page)
    override suspend fun snapTo(page: Int) = pager.scrollToPage(page)
    override suspend fun cancel() {
        if (pager.isScrollInProgress) pager.cancelScroll()
    }
}

/** 手机端自绘平移。 */
class SwitchMainTabPosition(private val state: PageSwitchState) : MainTabPosition {
    override val currentPage: Int get() = state.currentPage
    override val offsetFraction: Float get() = state.offsetFraction
    override val pageCount: Int get() = state.pageCount
    override val isAnimating: Boolean get() = state.isAnimating

    override suspend fun animateTo(page: Int) = state.animateTo(page)
    override suspend fun snapTo(page: Int) = state.snapTo(page)
    override suspend fun cancel() = state.cancel()
}
