#ifndef SKIKOBRIDGE_TYPES_H
#define SKIKOBRIDGE_TYPES_H

#ifdef SKIKO_WASM
#include <emscripten.h>
#define SKIKO_EXPORT EMSCRIPTEN_KEEPALIVE extern "C"
#else
#define SKIKO_EXPORT extern "C"
#endif

#include <stdint.h>

typedef int8_t KBoolean;
typedef int8_t KByte;
typedef int16_t KChar;
typedef int16_t KShort;
typedef int32_t KInt;
typedef float KFloat;
typedef int64_t KLong;
typedef double KDouble;
typedef void* KNativePointer;
typedef void* KInteropPointer;
// TODO: remove me!
typedef void* KInteropPointerArray;
// TODO: remove me!
typedef void* KNativePointerArray;

#endif /* SKIKOBRIDGE_TYPES_H */
