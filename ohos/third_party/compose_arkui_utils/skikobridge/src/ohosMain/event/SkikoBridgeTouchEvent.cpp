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
// Created on 2024/3/26.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "skikobridge/SkikoBridgeOhosTouchEvent.h"
#include "skikobridge/ComposeDispatcher.h"
#include "../utils/skiko_napi_utils.h"
#include <cstdarg>
#include <cstdio>
#include <cmath>
#include <vector>
#include <hilog/log.h>

static int skikobridge_invalid_type = -9999;

static inline void SLog(int level, const char *log, ...) {
    if (LOG_OFF_SKIKO) {
        return;
    }

    va_list args;
    va_start(args, log);
    int len = vsnprintf(nullptr, 0, log, args);
    va_end(args);

    char *buffer = new char[len + 1];
    va_start(args, log);
    vsnprintf(buffer, len + 1, log, args);
    va_end(args);

    OH_LOG_Print(LOG_APP, (LogLevel)level, 0, "SkiohBridge", "%{public}s", buffer);
}

static inline bool isArrayProperty(napi_env env, napi_value object, const char *propertyName) {
    napi_value result = nullptr;
    napi_get_named_property(env, object, propertyName, &result);

    bool is_array = false;
    napi_is_array(env, result, &is_array);

    return is_array;
}

static inline uint32_t skikobridge_getArrayLength(napi_env env, napi_value object, const char *propertyName) {
    bool is_array = isArrayProperty(env, object, propertyName);
    if (is_array) {
        napi_value result = nullptr;
        napi_get_named_property(env, object, propertyName, &result);

        uint32_t length = 0;
        napi_get_array_length(env, result, &length);
        return length;
    }
    return 0;
}

static inline int skikobridge_touch_event_get_touches_count(napi_env env, SkikoBridgeJSTouchEvent event) {
    if (event && env) {
        napi_value touchObject = (napi_value)event;
        uint32_t touches_count = skikobridge_getArrayLength(env, touchObject, "touches");
        return touches_count;
    }
    return 0;
}

static inline SkikoBridgeJSTouchObject
skikobridge_touch_event_get_touche_object_with_index(napi_env env, SkikoBridgeJSTouchEvent event, int index) {
    if (event && env) {
        napi_value touchEvent = (napi_value)event;
        napi_value toucheArray = nullptr;
        napi_get_named_property(env, touchEvent, "touches", &toucheArray);
        napi_value touchObject = nullptr;
        napi_get_element(env, toucheArray, index, &touchObject);
        return touchObject;
    }
    return nullptr;
}

static inline float skikobridge_touch_object_get_position_with_direction(napi_env env,
                                                                         SkikoBridgeJSTouchObject touchObject,
                                                                         bool directionX) {
    if (touchObject && env) {
        napi_value touch = (napi_value)touchObject;
        napi_value target = nullptr;
        napi_get_named_property(env, touch, directionX ? "x" : "y", &target);

        double result = 0;
        napi_get_value_double(env, target, &result);
        return (float)result;
    }
    return 0;
}

static inline int skikobridge_touch_object_get_type(napi_env env, SkikoBridgeJSTouchObject touchObject) {
    if (env && touchObject) {
        napi_value touch = (napi_value)touchObject;
        napi_value target = nullptr;
        napi_get_named_property(env, touch, "type", &target);

        int result = 0;
        napi_get_value_int32(env, target, &result);
        return result;
    }
    return skikobridge_invalid_type;
}

static inline long skikobridge_touch_object_get_id(napi_env env, SkikoBridgeJSTouchObject touchObject) {
    if (env && touchObject) {
        napi_value touch = (napi_value)touchObject;
        napi_value target = nullptr;
        napi_get_named_property(env, touch, "id", &target);

        long result = 0;
        napi_get_value_int64(env, target, &result);
        return result;
    }
    return 0;
}

static inline long skikobridge_time_stamp_from_napi_value(napi_env env, napi_value targetObject) {
    if (targetObject && env) {
        napi_value result = nullptr;
        napi_get_named_property(env, targetObject, "timestamp", &result);
        double timeStamp = 0;
        napi_get_value_double(env, result, &timeStamp);
        return floor(timeStamp / 1000000);
    }
    return 0;
}

#define ELF_STEP(B)                                                                                                    \
    T1 = (H << 4) + B;                                                                                                 \
    T2 = T1 & 0xF0000000;                                                                                              \
    if (T2)                                                                                                            \
        T1 ^= (T2 >> 24);                                                                                              \
    T1 &= (~T2);                                                                                                       \
    H = T1;

static unsigned long ASHashBytes(void *bytesarg, size_t length) {
    uint8_t *bytes = (uint8_t *)bytesarg;
    unsigned int H = 0, T1, T2;
    signed int rem = (signed int)length;
    while (3 < rem) {
        ELF_STEP(bytes[length - rem]);
        ELF_STEP(bytes[length - rem + 1]);
        ELF_STEP(bytes[length - rem + 2]);
        ELF_STEP(bytes[length - rem + 3]);
        rem -= 4;
    }
    switch (rem) {
    case 3:
        ELF_STEP(bytes[length - 3]);
    case 2:
        ELF_STEP(bytes[length - 2]);
    case 1:
        ELF_STEP(bytes[length - 1]);
    case 0:;
    }
    return H;
}

static inline unsigned long skikobridge_native_touch_object_hash(SkikoBridgeNativeTouchObject *object) {
    return ASHashBytes((void *)object, sizeof(SkikoBridgeNativeTouchObject));
}

static inline unsigned long skikobridge_native_touch_event_hash(SkikoBridgeNativeTouchEvent *object) {
    return ASHashBytes((void *)object, sizeof(SkikoBridgeNativeTouchEvent));
}

static inline unsigned long skikobridge_native_touch_historical_point_hash(SkikoBridgeNativeTouchHistoricalPoint *object) {
    return ASHashBytes((void *)object, sizeof(SkikoBridgeNativeTouchHistoricalPoint));
}

SkikoBridgeNativeTouchEvent *skikobridge_create_native_touch_event_from_jsevent(napi_value jsTouchEvent) {
    if (jsTouchEvent == nullptr) {
        return nullptr;
    }

    napi_env env = (napi_env)skikobridge_get_current_napi_env();

    auto nativeEvent = new SkikoBridgeNativeTouchEvent();

    long eventTimestamp = skikobridge_time_stamp_from_napi_value(env, jsTouchEvent);
    nativeEvent->touchType = skikobridge_touch_object_get_type(env, jsTouchEvent);
    nativeEvent->timeStamp = eventTimestamp;

    int touchesCount = skikobridge_touch_event_get_touches_count(env, jsTouchEvent);
    auto touchList = new std::vector<SkikoBridgeNativeTouchObject>();
    touchList->reserve(static_cast<size_t>(touchesCount));
    for (int i = 0; i < touchesCount; i++) {
        napi_value jsTouchObject = skikobridge_touch_event_get_touche_object_with_index(env, jsTouchEvent, i);
        SkikoBridgeNativeTouchObject obj;
        obj.x = skikobridge_touch_object_get_position_with_direction(env, jsTouchObject, true);
        obj.y = skikobridge_touch_object_get_position_with_direction(env, jsTouchObject, false);
        obj.touchType = skikobridge_touch_object_get_type(env, jsTouchObject);
        obj.id = skikobridge_touch_object_get_id(env, jsTouchObject);
        touchList->push_back(obj);
    }
    nativeEvent->touchesObject = touchList;
    nativeEvent->touchesCount = touchesCount;

    napi_value getHistoricalPointsFn = nullptr;
    napi_get_named_property(env, jsTouchEvent, "getHistoricalPoints", &getHistoricalPointsFn);

    napi_value historicalPointList = nullptr;
    napi_call_function(env, jsTouchEvent, getHistoricalPointsFn, 0, nullptr, &historicalPointList);

    uint32_t historicalCount = 0;
    napi_get_array_length(env, historicalPointList, &historicalCount);
    if (historicalCount > 0) {
        auto nativeHistoricalPointList = new std::vector<SkikoBridgeNativeTouchHistoricalPoint>();
        for (int i = 0; i < (int)historicalCount; i++) {
            napi_value jsTouchHistoricalPoint = nullptr;
            napi_get_element(env, historicalPointList, i, &jsTouchHistoricalPoint);

            long timestamp = skikobridge_time_stamp_from_napi_value(env, jsTouchHistoricalPoint);
            if (jsTouchHistoricalPoint && timestamp != eventTimestamp) {
                SkikoBridgeNativeTouchHistoricalPoint point = {};
                napi_value jsTouchObject = nullptr;
                napi_get_named_property(env, jsTouchHistoricalPoint, "touchObject", &jsTouchObject);
                point.timestamp = skikobridge_time_stamp_from_napi_value(env, jsTouchHistoricalPoint);
                point.touchObject.x = skikobridge_touch_object_get_position_with_direction(env, jsTouchObject, true);
                point.touchObject.y = skikobridge_touch_object_get_position_with_direction(env, jsTouchObject, false);
                point.touchObject.touchType = skikobridge_touch_object_get_type(env, jsTouchObject);
                point.touchObject.id = skikobridge_touch_object_get_id(env, jsTouchObject);
                nativeHistoricalPointList->push_back(point);
            }
        }
        nativeEvent->historicalPoints = nativeHistoricalPointList;
        nativeEvent->historicalPointsCount = static_cast<int>(nativeHistoricalPointList->size());
    }

    nativeEvent->id = (int)skikobridge_native_touch_event_hash(nativeEvent);
    return nativeEvent;
}

SkikoBridgeNativeTouchObject *
skikobridge_native_touch_get_touch_object_at_index(SkikoBridgeNativeTouchEvent *nativeEvent, int index) {
    if (nativeEvent == nullptr || nativeEvent->touchesObject == nullptr) {
        return nullptr;
    }
    auto *list = static_cast<std::vector<SkikoBridgeNativeTouchObject> *>(nativeEvent->touchesObject);
    if (index >= 0 && index < (int)list->size()) {
        return &list->at(static_cast<size_t>(index));
    }
    return nullptr;
}

SkikoBridgeNativeTouchHistoricalPoint *
skikobridge_native_touch_get_historycal_point_at_index(SkikoBridgeNativeTouchEvent *nativeEvent, int index) {
    if (nativeEvent == nullptr || nativeEvent->historicalPoints == nullptr) {
        return nullptr;
    }
    auto *list = static_cast<std::vector<SkikoBridgeNativeTouchHistoricalPoint> *>(nativeEvent->historicalPoints);
    if (index >= 0 && index < (int)list->size()) {
        return &list->at(static_cast<size_t>(index));
    }
    return nullptr;
}

void skikobridge_delete_native_touch_event(SkikoBridgeNativeTouchEvent *nativeEvent) {
    if (nativeEvent == nullptr) {
        return;
    }
    // 存的是 vector<value>，delete vector 即释放全部，无需遍历
    if (nativeEvent->touchesObject != nullptr) {
        delete static_cast<std::vector<SkikoBridgeNativeTouchObject> *>(nativeEvent->touchesObject);
        nativeEvent->touchesObject = nullptr;
    }
    if (nativeEvent->historicalPoints != nullptr) {
        delete static_cast<std::vector<SkikoBridgeNativeTouchHistoricalPoint> *>(nativeEvent->historicalPoints);
        nativeEvent->historicalPoints = nullptr;
    }
    delete nativeEvent;
}
