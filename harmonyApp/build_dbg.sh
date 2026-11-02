#!/bin/bash
# 出鸿蒙调试包并装到设备（含重启应用）。
#
# 科特林侧的动态库由 entry/hvigorfile.ts 的前置任务自动链（按构建模式选调试/发布），
# 这里不再单独跑 Gradle —— 手工两步是漏库的根源。
set -e
cd "$(dirname "$0")"

export DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk
export NODE_HOME=/Applications/DevEco-Studio.app/Contents/tools/node
HVIGORW=/Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw
HDC=/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc

"$HVIGORW" --mode module -p module=entry@default -p product=default -p buildMode=debug assembleHap --no-daemon > /tmp/hap_build.log 2>&1 || { tail -30 /tmp/hap_build.log; exit 1; }
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/hap_build.log | tail -1
"$HDC" install -r entry/build/default/outputs/default/entry-default-signed.hap | grep -o "install bundle successfully"
"$HDC" shell "aa force-stop com.haooz.schedule; aa start -b com.haooz.schedule -a EntryAbility"
