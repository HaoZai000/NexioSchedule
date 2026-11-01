# compose_arkui_utils（上游源码副本）

缺 `libcompose_arkui_utils.so` 导致宿主崩在 `ArkUIViewController_getId`，详见
`docs/HARMONY_PORT_PLAN.md` 第十节。这里放的是**未修改的上游 C++ 源码**，只补一个我们自己的
`CMakeLists.txt`（只编静态库，不编 skikobridge —— 那个 HAR 已经带了）。

| 项 | 值 |
|---|---|
| 来源 | **本地 release 包 `compose-multiplatform-core-v1.9.2-1.2.0`（以此为准）** |
| 仓库 | https://gitcode.com/CPF-KMP-CMP/compose-multiplatform-core（分支 develop-1.9.2-OH） |
| 路径 | compose/ui/ui-arkui/src/ohosArm64Main/cpp/compose/src/main/cpp |
| 许可 | Apache-2.0（见 LICENSE） |

⚠ **不要从 git 分支 HEAD 拉**：分支 HEAD 比 ohpm 上 `@cpf-kmp-cmp/compose` 1.9.2-1.2.0 打出来的
二进制新（会多调 `skikobridge_set_platform_provider` 这类 HAR 里还没有的符号）。
以 release 包为准，版本号与 HAR 对齐。

升级时：取对应版本的 release 包（或 git tag），覆盖 `compose/` `include/` `skikobridge/`
`napi_init.cpp`，再跑 `./build.sh arm64-v8a`。

### 本地补丁

只加了一个自己的文件 `nexio/nexio_skikobridge_shim.cpp`（补 HAR 版 libskikobridge.so 缺的
`skikobridge_set_platform_provider`）。上游文件本身**未改动** —— 改上游源码会让下次升级无从比对。
