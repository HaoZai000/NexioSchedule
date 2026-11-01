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

#ifndef SKIKOBRIDGE_MAIN_CPP_INCLUDE_SKIKOBRIDGE_SKIKOBRIDGEOHOSTOUCHEVENT_H_
#define SKIKOBRIDGE_MAIN_CPP_INCLUDE_SKIKOBRIDGE_SKIKOBRIDGEOHOSTOUCHEVENT_H_

#include "types.h"
#include "napi/native_api.h"

typedef napi_value SkikoBridgeJSTouchEvent;
typedef napi_value SkikoBridgeJSTouchObject;

#pragma mark - v2
/* 单个触摸事件点位对象 */
typedef struct SkikoBridgeNativeTouchObject {
    int touchType;
    int id;
    float x;
    float y;
} SkikoBridgeNativeTouchObject;

/* 单个触摸事件历史点位对象（touchObject 内嵌为值，POD 无 ownership 坑） */
typedef struct SkikoBridgeNativeTouchHistoricalPoint {
    int size;
    float force;
    long timestamp;
    SkikoBridgeNativeTouchObject touchObject;
} SkikoBridgeNativeTouchHistoricalPoint;

/* 单个触摸事件历史点位对象 */
typedef struct SkikoBridgeNativeTouchEvent {
    int id;
    int touchType;
    long timeStamp; // 对齐到 Android Compose

    int touchesCount;
    void *touchesObject;

    int historicalPointsCount;
    void *historicalPoints;

} SkikoBridgeNativeTouchEvent;

/*
根据 JS 的 touchEvent 创建一个 C 的 TouchEvent
*/
SKIKO_EXPORT SkikoBridgeNativeTouchEvent *
skikobridge_create_native_touch_event_from_jsevent(SkikoBridgeJSTouchEvent jsTouchEvent);

/*
获取 nativeTouchEvent 数组中的 touchObject
*/
SKIKO_EXPORT SkikoBridgeNativeTouchObject *
skikobridge_native_touch_get_touch_object_at_index(SkikoBridgeNativeTouchEvent *nativeEvent, int index);

/*
获取 nativeTouchEvent 数组中的 historicalPoint
*/
SKIKO_EXPORT SkikoBridgeNativeTouchHistoricalPoint *
skikobridge_native_touch_get_historycal_point_at_index(SkikoBridgeNativeTouchEvent *nativeEvent, int index);

/*
释放 nativeTouchEvent
*/
SKIKO_EXPORT void skikobridge_delete_native_touch_event(SkikoBridgeNativeTouchEvent *nativeEvent);

#endif // SKIKOBRIDGE_MAIN_CPP_INCLUDE_SKIKOBRIDGE_SKIKOBRIDGEOHOSTOUCHEVENT_H_
