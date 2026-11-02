/*
 * Copyright 2025 Huawei Device Co., Ltd. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

#ifndef ANDROIDX_COMPOSE_UI_ARKUI_UTILS_PAGE_DPI_NOTIFIER_H
#define ANDROIDX_COMPOSE_UI_ARKUI_UTILS_PAGE_DPI_NOTIFIER_H

#include <cstdint>

namespace androidx::compose::ui::arkui::utils {

/** Cache main window id from ArkTS (source of truth for the CAPI call). */
void PageDpiNotifier_setWindowId(int32_t windowId);

void PageDpiNotifier_clearWindowId();

/**
 * OH_AbilityRuntime_ApplicationContextNotifyPageChanged (API 23+).
 *
 * @return 0 success; -1 CAPI unavailable; -2 invalid pageName; -3 windowId not ready;
 *         otherwise AbilityRuntime_ErrorCode from the system.
 */
int PageDpiNotifier_notifyPageChanged(const char *pageName, int32_t pageNameLength);

bool PageDpiNotifier_isAvailable();

} // namespace androidx::compose::ui::arkui::utils

#endif // ANDROIDX_COMPOSE_UI_ARKUI_UTILS_PAGE_DPI_NOTIFIER_H
