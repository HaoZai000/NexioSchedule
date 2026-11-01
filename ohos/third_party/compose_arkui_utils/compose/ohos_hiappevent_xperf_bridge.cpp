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

#include "ohos_hiappevent_xperf_bridge.h"

#include <deviceinfo.h>
#include <mutex>

#include "xcomponent_log.h"

namespace compose::hiappevent {

namespace {

constexpr char kProcessorName[] = "xperfbridge";
constexpr int kRequiredApiVersion = 18;

std::mutex gInitMutex;
std::mutex gWriteMutex;
bool gInitAttempted = false;
bool gBridgeReady = false;

} // namespace

void InitXperfBridge() {
    std::lock_guard<std::mutex> lock(gInitMutex);
    if (gInitAttempted) {
        return;
    }
    gInitAttempted = true;

    const int apiVersion = OH_GetSdkApiVersion();
    if (apiVersion < kRequiredApiVersion) {
        LOGW("HiAppEvent xperf: skipped api=%{public}d required=%{public}d",
             apiVersion, kRequiredApiVersion);
        gBridgeReady = false;
        return;
    }

    gBridgeReady = true;
    LOGI("HiAppEvent xperf: bridge ready api=%{public}d", apiVersion);
}

bool IsXperfBridgeReady() {
    std::lock_guard<std::mutex> lock(gInitMutex);
    return gBridgeReady;
}

int WritePerformanceEvent(
    const char *domain,
    const char *eventName,
    const std::function<int()> &writeFn) {
    InitXperfBridge();
    if (!IsXperfBridgeReady()) {
        LOGD("HiAppEvent xperf: bridge not ready domain=%{public}s event=%{public}s",
             domain != nullptr ? domain : "",
             eventName != nullptr ? eventName : "");
        return static_cast<int>(HIAPPEVENT_INVALID_PARAM_VALUE);
    }

    std::lock_guard<std::mutex> writeLock(gWriteMutex);

    HiAppEvent_Processor *processor = OH_HiAppEvent_CreateProcessor(kProcessorName);
    if (processor == nullptr) {
        LOGE("HiAppEvent xperf: CreateProcessor failed");
        return static_cast<int>(HIAPPEVENT_INVALID_PARAM_VALUE);
    }

    int writeResult = static_cast<int>(HIAPPEVENT_INVALID_PARAM_VALUE);
    int64_t processorId = 0;
    do {
        if (OH_HiAppEvent_SetReportPolicy(processor, 1, 1, true, true) != 0) {
            LOGE("HiAppEvent xperf: SetReportPolicy failed");
            break;
        }
        if (OH_HiAppEvent_SetReportEvent(processor, domain, eventName, true) != 0) {
            LOGE("HiAppEvent xperf: SetReportEvent failed domain=%{public}s event=%{public}s",
                 domain != nullptr ? domain : "",
                 eventName != nullptr ? eventName : "");
            break;
        }
        processorId = OH_HiAppEvent_AddProcessor(processor);
        if (processorId <= 0) {
            LOGE("HiAppEvent xperf: AddProcessor failed id=%{public}lld",
                 static_cast<long long>(processorId));
            break;
        }
        writeResult = writeFn();
    } while (false);

    if (processorId > 0) {
        OH_HiAppEvent_RemoveProcessor(processorId);
    }
    OH_HiAppEvent_DestroyProcessor(processor);
    return writeResult;
}

} // namespace compose::hiappevent
