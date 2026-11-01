/*
 * Tencent is pleased to support the open source community by making ovCompose available.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
 *
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

//
// Created on 2024/4/2.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".
#include "hitrace/trace.h"
#include <sys/stat.h>
#include <hilog/log.h>

static inline char *taskDescriptionFromTaskKind(long taskKind) {
    char *taskDesc = (char *)"unknown";
    switch (taskKind) {
    case 1000: // val InitCanvas = SceneType(1000);
        taskDesc = (char *)"InitCanvas";
        break;
    case 1001: // val ComposeDrawFrame = SceneType(1001);
        taskDesc = (char *)"ComposeDrawFrame";
        break;
    case 1002: // DirectContextFlush = SceneType(1002);
        taskDesc = (char *)"DirectContextFlush";
        break;
    case 1003: // val EglFlush = SceneType(1003);
        taskDesc = (char *)"EglFlush";
        break;
    case 1004: // val VsyncFrame = SceneType(1004);
        taskDesc = (char *)"VsyncFrame";
        break;
    default:
        taskDesc = (char *)"unknown";
    }
    return taskDesc;
}

extern "C" void skikobridge_performance_trace_start(long taskKind, long taskId) {
    const char *taskDesc = taskDescriptionFromTaskKind(taskKind);
    OH_HiTrace_StartAsyncTrace(taskDesc, taskId);
}

extern "C" void skikobridge_performance_trace_end(long taskKind, long taskId) {
    const char *taskDesc = taskDescriptionFromTaskKind(taskKind);
    OH_HiTrace_FinishAsyncTrace(taskDesc, taskId);
}

extern "C" void skikobridge_performance_sync_trace_start(const char *taskDesc) {
    OH_HiTrace_StartTrace(taskDesc);
}

extern "C" void skikobridge_performance_sync_trace_end() {
    OH_HiTrace_FinishTrace();
}
