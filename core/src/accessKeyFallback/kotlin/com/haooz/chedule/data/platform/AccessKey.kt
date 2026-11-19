package com.haooz.chedule.data.platform

/**
 * App 访问密钥（= 服务端 `NEXIO_ACCESS_KEY`）。
 *
 * 空值兜底：开源仓直接编译走这里，构建不受影响，只是接口会被服务端挡回。
 * 发布构建放 `core/src/accessKeySecret/`（已 gitignore），Gradle 二选一。
 */
internal object NexioAccessKey {
    const val VALUE: String = ""
}
