package com.haooz.chedule.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 文档页（二级及以下）顶栏下方到第一块内容的**额外**留白 —— 唯一来源。
 *
 * 口径（设计规范）：`top = 状态栏 + CollapsedHeight + pageTopExtra(...)`
 *
 * | 页面最上方是        | 平板 | 手机 |
 * | ------------------ | --- | --- |
 * | SmallTitle（分组小标题） | 12  | 0   |
 * | 卡片 / 控件（无小标题）  | 24  | 12  |
 */
fun pageTopExtra(isTablet: Boolean, hasSmallTitle: Boolean): Dp =
    if (hasSmallTitle) {
        if (isTablet) 12.dp else 0.dp
    } else {
        if (isTablet) 24.dp else 12.dp
    }
