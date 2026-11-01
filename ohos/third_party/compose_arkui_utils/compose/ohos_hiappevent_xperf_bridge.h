/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef COMPOSE_OHOS_HIAPPEVENT_XPERF_BRIDGE_H
#define COMPOSE_OHOS_HIAPPEVENT_XPERF_BRIDGE_H

#include <functional>
#include <hiappevent/hiappevent.h>

namespace compose::hiappevent {

/**
 * Performance Radar (API 18+): register xperfbridge processor before
 * OH_HiAppEvent_Write. Linked via hiappevent_ndk.z like CreateParamList/Write.
 */
void InitXperfBridge();

bool IsXperfBridgeReady();

int WritePerformanceEvent(
    const char *domain,
    const char *eventName,
    const std::function<int()> &writeFn);

} // namespace compose::hiappevent

#endif // COMPOSE_OHOS_HIAPPEVENT_XPERF_BRIDGE_H
