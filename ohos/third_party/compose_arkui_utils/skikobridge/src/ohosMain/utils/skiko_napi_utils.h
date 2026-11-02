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

#ifndef SKIKOBRIDGE_SKIKO_NAPI_UTILS_H
#define SKIKOBRIDGE_SKIKO_NAPI_UTILS_H
#include "napi/native_api.h"
#include <cstdlib>

static bool LOG_OFF_SKIKO = true;
static bool LOG_ON_SKIKO = !LOG_OFF_SKIKO;

namespace skikobridge {

    typedef int8_t KBoolean;
    typedef int8_t KByte;
    typedef int16_t KChar;
    typedef int16_t KShort;
    typedef int32_t KInt;
    typedef float KFloat;
    typedef int64_t KLong;
    typedef double KDouble;
    typedef void *KNativePointer;
    typedef void *KInteropPointer;
    // TODO: remove me!
    typedef void *KInteropPointerArray;
    // TODO: remove me!
    typedef void *KNativePointerArray;

    static inline napi_value napi_create_undefined(napi_env env) {
        napi_value undefined = nullptr;
        napi_get_undefined(env, &undefined);
        return undefined;
    }

    static inline napi_value napi_create_bool(napi_env env, bool value) {
        napi_value boolValue = nullptr;
        napi_get_boolean(env, value, &boolValue);
        return boolValue;
    }

    struct NArgs {
        NArgs(napi_env env, napi_callback_info info) : env(env), info(info) {
            size_t initCount = 0;
            napi_get_cb_info(env, info, &initCount, nullptr, nullptr, nullptr);
            values = new napi_value[initCount + 1];
            napi_get_cb_info(env, info, &initCount, values, nullptr, nullptr);
        }

        int64_t intAt(int index) const {
            int64_t result;
            napi_get_value_int64(env, values[index], &result);
            return result;
        }

        bool boolAt(int index) const {
            bool result;
            napi_get_value_bool(env, values[index], &result);
            return result;
        }

        KFloat *floatArrayAt(int index) const {
            int64_t result;
            napi_get_value_int64(env, values[index], &result);
            return reinterpret_cast<KFloat *>(result);
        }

        char *stringAt(int index, int size) const {
            char *result = (char *)malloc(sizeof(char *) * size);
            size_t valueLen = 0;
            napi_get_value_string_utf8(env, values[index], result, size, &valueLen);
            return result;
        }

        int32_t stringSizeAt(int sizeIndex) const {
            return intAt(sizeIndex);
        }

        KInt *intArrayAt(int index) const {
            int64_t result;
            napi_get_value_int64(env, values[index], &result);
            return reinterpret_cast<KInt *>(result);
        }

        KLong *longArrayAt(int index) const {
            int64_t result;
            napi_get_value_int64(env, values[index], &result);
            return reinterpret_cast<KLong *>(result);
        }

        KInteropPointer interopPointerAt(int index) const {
            int64_t result;
            napi_get_value_int64(env, values[index], &result);
            return reinterpret_cast<KInteropPointer>(result);
        }

        double doubleAt(int index) const {
            double result;
            napi_get_value_double(env, values[index], &result);
            return result;
        }

        float floatAt(int index) const {
            double result;
            napi_get_value_double(env, values[index], &result);
            return result;
        }

        long *longArray(int index) const {
            // 获取传入数组typedarray生成input_buffer
            napi_typedarray_type type; // 数据类型
            napi_value input_buffer;
            size_t byte_offset; // 数据偏移
            size_t i, length;   // 数据字节大小
            // 从values[index]中读取数组类型信息
            napi_get_typedarray_info(env, values[index], &type, &length, NULL, &input_buffer, &byte_offset);

            // 获取数组数据
            void *data;
            size_t byte_length;
            // 将数组读入buffer中，并将buffer地址保存到data指针中
            napi_get_arraybuffer_info(env, input_buffer, &data, &byte_length);
            // 检查数组类型是否为bigint64array
            if (type == napi_bigint64_array) {
                return reinterpret_cast<long *>(data);
            }
            return nullptr;
        }

        KByte *byteArray(int index) const {
            // 获取传入数组typedarray生成input_buffer
            napi_typedarray_type type; // 数据类型
            napi_value input_buffer;
            size_t byte_offset; // 数据偏移
            size_t i, length;   // 数据字节大小
            // 从values[index]中读取数组类型信息
            napi_get_typedarray_info(env, values[index], &type, &length, NULL, &input_buffer, &byte_offset);

            // 获取数组数据
            void *data;
            size_t byte_length;
            // 将数组读入buffer中，并将buffer地址保存到data指针中
            napi_get_arraybuffer_info(env, input_buffer, &data, &byte_length);
            // 检查数组类型是否为uint8array
            if (type == napi_uint8_array) {
                return reinterpret_cast<KByte *>(data);
            }
            return nullptr;
        }

        napi_value createDoubleValue(double value) const {
            napi_value result;
            napi_create_double(env, value, &result);
            return result;
        }

        napi_value createIntValue(int64_t value) const {
            napi_value result;
            napi_create_int64(env, value, &result);
            return result;
        }

        napi_value createInt64ArrayValue(long *array, size_t length) {
            napi_value result;
            napi_create_array_with_length(env, length, &result);
            for (int i = 0; i < length; i++) {
                napi_value element;
                napi_create_int64(env, array[i], &element);
                napi_set_element(env, result, i, element);
            }
            return result;
        }

        template <typename T> napi_value createIntValueFromObj(T obj) const {
            napi_value result;
            napi_create_int64(env, reinterpret_cast<intptr_t>(obj), &result);
            return result;
        }

        napi_value createBoolValue(bool value) const {
            napi_value boolValue = nullptr;
            napi_get_boolean(env, value, &boolValue);
            return boolValue;
        }

        template <typename T> T objectAt(int argc_index) const {
            int64_t result;
            napi_get_value_int64(env, values[argc_index], &result);
            return reinterpret_cast<T>((result));
        }

        napi_value undefined() const {
            napi_value undefined = nullptr;
            napi_get_undefined(env, &undefined);
            return undefined;
        }

        ~NArgs() {
            if (values != NULL) {
                delete[] values;
            }
        }

    private:
        size_t argcCount;
        napi_value *values;
        napi_env env;
        napi_callback_info info;
    };
} // namespace skioh


#endif //SKIKOBRIDGE_SKIKO_NAPI_UTILS_H
