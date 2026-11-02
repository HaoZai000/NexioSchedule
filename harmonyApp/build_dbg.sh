#!/bin/bash
# 出鸿蒙 debug 包并装到设备（含重启应用）。
#
# ⚠ 必须两步：先链 Kotlin 侧 .so（顺手拷进 entry/libs），再交给 hvigor 打包。
#   只跑 hvigor 会把旧 .so 打进去，表现为「Compose 不画、ArkUI 也不画 = 整屏空白」。
set -e
cd "$(dirname "$0")"

export DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk
export NODE_HOME=/Applications/DevEco-Studio.app/Contents/tools/node
# hvigor 用 JDK25 会报 IllegalArgumentException: 25.0.3，链接必须 JDK21
export JAVA_HOME="$HOME/jdk21/jdk-21.0.2.jdk/Contents/Home"
HVIGORW=/Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw
HDC=/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc

( cd ../ohos && ./gradlew :ohosApp:linkDebugSharedOhosArm64 -q ) || { echo "link failed"; exit 1; }

"$HVIGORW" --mode module -p module=entry@default -p product=default -p buildMode=debug assembleHap --no-daemon > /tmp/hap_build.log 2>&1 || { tail -20 /tmp/hap_build.log; exit 1; }
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/hap_build.log | tail -1
"$HDC" install -r entry/build/default/outputs/default/entry-default-unsigned.hap | grep -o "install bundle successfully"
"$HDC" shell "aa force-stop com.haooz.schedule; aa start -b com.haooz.schedule -a EntryAbility"
