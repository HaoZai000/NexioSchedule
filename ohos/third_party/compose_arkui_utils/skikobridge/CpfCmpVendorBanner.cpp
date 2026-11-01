/*
 * Copyright (c) 2026 Huawei Device Co., Ltd.
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

// 固定身份。不写 git hash，不写会变的构建号。
// 强定义 OBJECT：const char Name[] ，不要写成 const char*。
// 版本号由 ninja 每次构建时生成的 CpfCmpVersion.h 提供，已经带引号。
#if __has_include("CpfCmpVersion.h")
#include "CpfCmpVersion.h"
#endif
#ifndef CPF_CMP_VERSION
#define CPF_CMP_VERSION "1.9.2-1.1.0"
#endif

#if __has_attribute(retain)
#define CPF_CMP_BANNER_ATTR __attribute__((used, retain, visibility("default")))
#else
#define CPF_CMP_BANNER_ATTR __attribute__((used, visibility("default")))
#endif

extern "C" CPF_CMP_BANNER_ATTR
const char COMPOSE_CPF_CMP_VENDOR_BANNER[] = "CPF-CMP/" CPF_CMP_VERSION;
