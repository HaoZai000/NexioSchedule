package com.haooz.chedule.data.ai

import com.haooz.chedule.data.platform.ApiEndpoints
import com.haooz.chedule.data.platform.HttpService
import com.haooz.chedule.data.platform.StatsReporter
import com.haooz.chedule.data.util.jsonToPlainValue
import com.haooz.chedule.data.util.optInt
import com.haooz.chedule.data.util.optJsonArray
import com.haooz.chedule.data.util.optJsonObject
import com.haooz.chedule.data.util.optString
import com.haooz.chedule.data.util.parseJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 云端 AI 能力概览。[tasks] 是服务端当前注册的任务名，App 据此决定露出哪些入口。 */
data class AiCapabilities(
    val enabled: Boolean,
    val tasks: List<String>,
    /** 该设备今天的免费额度；没带 device_id 或服务端没给时为 null */
    val quota: AiQuota? = null,
    /** 服务端当前模型 ID，仅兜底展示；客户端不拿它做判断，调用只发任务名 */
    val model: String = "",
    /** App 上显示的名字（服务端预设的别名，如 `hy4` → `Hy4`），优先用它 */
    val modelDisplay: String = "",
)

/** 免费额度的今日用量（按设备）。 */
data class AiQuota(
    val shrinkUsed: Int,
    val shrinkLimit: Int,
    val agentUsed: Int,
    val agentLimit: Int,
) {
    val shrinkRemaining: Int get() = (shrinkLimit - shrinkUsed).coerceAtLeast(0)
    val agentRemaining: Int get() = (agentLimit - agentUsed).coerceAtLeast(0)
}

/**
 * AI 调用的结果。
 *
 * 刻意不用「失败一律 null」：这条链路跨了加密、HTTP、服务端、模型四段，
 * 全都返回 null 时用户只看到一句「失败」，分不清是没网、没配公钥、服务端拒了
 * 还是模型没按格式回。每种失败都有自己的说法，界面直接显示 [Error.message]。
 */
sealed class AiResult<out T> {
    /** @param remaining 今天还剩几次；服务端没给就是 null */
    data class Ok<T>(val value: T, val remaining: Int? = null) : AiResult<T>()
    data class Error(val message: String) : AiResult<Nothing>()
}

/**
 * 云端 AI 客户端。
 *
 * ## 免费额度 / 自带服务商
 *
 * 默认所有请求都发给自己的服务器：apikey 只存服务端（加密落盘），App 从不接触，
 * 地址与模型都在服务端改，改完所有客户端立刻生效，不用发版。也因此 `/api/ai/task`
 * 只接受**预设任务**（prompt 模板在云端），不能拿它当通用 chat 代理。
 *
 * 用户在「AI 功能」页填了自己的服务商（BYOK）后，[shrinkLabels] 改走 [AiDirectClient]
 * 直连供应商：不占免费额度，key 只在本机，更不经过我们的服务器。
 *
 * ## 加密
 *
 * 每条请求都现开一个 [AiCryptoSession]：会话密钥一次性，请求与响应共用同一把。
 */
class AiClient(
    private val http: HttpService,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /**
     * 缩略态文案精简：把课程名和上课地点一起交给云端 AI，拿回一张「原名=缩写」对照。
     *
     * 两类条目混在一张表里：客户端做的是纯文本替换，键互不重叠，没必要分两张表。
     */
    suspend fun shrinkLabels(
        classrooms: List<String>,
        courses: List<String>,
    ): AiResult<List<Pair<String, String>>> {
        val cleanRooms = classrooms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val cleanCourses = courses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanRooms.isEmpty() && cleanCourses.isEmpty()) {
            return AiResult.Error("课表里还没有课程名和地点")
        }

        // 用户配了自己的服务商就直连，不占免费额度、也不经过我们的服务器
        val byok = AiByokStore.load()
        if (byok.isUsable) {
            return AiDirectClient(http).shrinkLabels(classrooms = cleanRooms, courses = cleanCourses)
        }

        val body = buildJsonObject {
            put("task", TASK_SHRINK_CLASSROOMS)
            // 服务端按这个 ID 算每天用量（与统计上报同一个 ID）
            put("device_id", StatsReporter.ensureDeviceId())
            put("args", buildJsonObject {
                put("courses", buildJsonArray { cleanCourses.forEach { add(JsonPrimitive(it)) } })
                put("classrooms", buildJsonArray { cleanRooms.forEach { add(JsonPrimitive(it)) } })
            })
        }

        val plain = when (val r = roundTrip("$baseUrl/task", body)) {
            is AiResult.Ok -> r.value
            is AiResult.Error -> return r
        }

        val obj = runCatching { parseJsonObject(plain) }
            .getOrElse { return AiResult.Error("服务端响应不是 JSON") }
        if (!obj.optString("ok").toBoolean()) {
            return AiResult.Error(obj.optString("error").ifEmpty { "服务端说这次没成" })
        }
        // lines 存在但为空是正常结果：课表里的楼名本来就短（「博105」「安503」），
        // 没什么可缩的。这跟「服务端没给结果」要分开。
        val lines = obj.optJsonObject("result")?.optJsonArray("lines")
            ?: return AiResult.Error("服务端没返回对照表")

        val pairs = lines.mapNotNull { element ->
            val line = jsonToPlainValue(element) as? String ?: return@mapNotNull null
            val sep = line.indexOf('=')
            if (sep <= 0) return@mapNotNull null
            val from = line.substring(0, sep).trim()
            val to = line.substring(sep + 1).trim()
            if (from.isEmpty() || to.isEmpty()) null else from to to
        }
        val remaining = obj.optInt("remaining", -1).takeIf { it >= 0 }
        return AiResult.Ok(pairs, remaining)
    }

    /**
     * 问服务端「AI 能不能用 + 今天这台设备还剩多少免费额度」。
     *
     * 与 [shrinkLabels] 共用加密通道；不消耗额度，只读。
     */
    suspend fun capabilities(): AiResult<AiCapabilities> {
        val body = buildJsonObject {
            put("device_id", StatsReporter.ensureDeviceId())
        }
        val plain = when (val r = roundTrip("$baseUrl/capabilities", body)) {
            is AiResult.Ok -> r.value
            is AiResult.Error -> return r
        }
        val obj = runCatching { parseJsonObject(plain) }
            .getOrElse { return AiResult.Error("服务端响应不是 JSON") }
        if (obj.optString("error").isNotEmpty()) {
            return AiResult.Error(obj.optString("error"))
        }
        val tasks = obj.optJsonArray("tasks")?.mapNotNull { element ->
            jsonToPlainValue(element) as? String
        }.orEmpty()
        val quota = obj.optJsonObject("quota")?.let { q ->
            val shrink = q.optJsonObject("shrink") ?: return@let null
            val agent = q.optJsonObject("agent") ?: return@let null
            AiQuota(
                shrinkUsed = shrink.optInt("used", 0),
                shrinkLimit = shrink.optInt("limit", 0),
                agentUsed = agent.optInt("used", 0),
                agentLimit = agent.optInt("limit", 0),
            )
        }
        return AiResult.Ok(
            AiCapabilities(
                enabled = obj.optString("enabled").toBoolean(),
                tasks = tasks,
                quota = quota,
                model = obj.optString("model").trim(),
                modelDisplay = obj.optString("model_display").trim(),
            )
        )
    }

    /**
     * 测试本机配置的服务商能否接通（只发一条最小请求）。
     *
     * @return 耗时（毫秒）
     */
    suspend fun testByokConnection(): AiResult<Long> {
        val cfg = AiByokStore.load()
        if (!cfg.isUsable) return AiResult.Error("服务商配置不完整")
        return AiDirectClient(http).ping(cfg)
    }

    /**
     * 发一次加密请求并解开响应 —— 具体实现在 [aiRoundTrip]（教务 agent 共用同一份）。
     */
    private suspend fun roundTrip(path: String, body: JsonObject): AiResult<String> =
        aiRoundTrip(http, path, body)

    companion object {
        /** 与 `NoticeFetcher` / `StatsReporter` 等服务地址同源。 */
        const val DEFAULT_BASE_URL = ApiEndpoints.AI

        /** 服务端 `AI_TASKS` 里上课地点精简的任务名，逐字不能改。 */
        const val TASK_SHRINK_CLASSROOMS = "shrink_classrooms"
    }
}

/**
 * 混合加密信封：App 与服务端之间唯一的请求/响应形态。
 *
 * 服务端现在通过域名 HTTPS 访问；信封加密保留为第二层：即使链路被降级到明文，
 * 请求体和响应体也始终是密文。
 *
 * 1. App 每次随机生成 32 字节会话密钥；
 * 2. 会话密钥用服务端 RSA 公钥（OAEP + SHA-256）加密，得到 [ek]；
 * 3. 正文用会话密钥做 AES-256-GCM，得到 [iv] 与 [ct]；
 * 4. 服务端用私钥解出会话密钥，解正文，再用**同一把**会话密钥加密响应回传。
 *
 * 中间人既读不到明文，也因为没有私钥解不出 [ek]。会话密钥只在内存里，用完即弃。
 *
 * @property ek 被 RSA-OAEP 加密的会话密钥，base64
 * @property iv AES-256-GCM 的 12 字节 nonce，base64
 * @property ct 密文后附 16 字节 GCM tag，base64
 */
data class AiEnvelope(val ek: String, val iv: String, val ct: String)

/**
 * 服务端 RSA 公钥（PEM，SPKI/X.509 格式）。
 *
 * 公钥本来就是公开的，进仓库没问题；配套的私钥只留在服务端
 * （`server/ai-rsa-private.pem` 或环境变量 `AI_RSA_PRIVATE`）。
 *
 * ⚠ 换服务器或重新生成密钥对后必须同步改这里，否则信封解不开、AI 直接不可用。
 * 取服务端公钥：`curl -H "Authorization: Bearer $DASH_TOKEN" https://nexioschedule.icu/api/ai/public-key`
 */
object AiServerKey {

    private const val PLACEHOLDER = "REPLACE_WITH_SERVER_PUBLIC_KEY"

    /** 线上服务端公钥（`/api/ai/public-key`）；[isConfigured] 为 false 时 AI 调用一律跳过。 */
    val PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA49D6/61K00ruiFPLyjuZ
        FuBkZgf04YLyQoVac6JMyRTZGIyqxqiD6XgK+NNNasZFgkJZIZ2zCcopKPOy56sd
        yAK+NCDtOTs2uvFPutUR01/zFDrpMV4xYB+0zMRqfM4whovta4q6AihkLgyunhBE
        joHe7NOWDYznweHSCOWjGz+66u1jKVzX+TX7nkT34GVJIiEHtfhR9sB2MzPISK9X
        3w0PAdGRNCj/VhXgwkPsAMfqYw5XTgg0N01mB1OJUkcrNRtKCueVcteNEckU6hRm
        Ph+kmHLKhwaG0v7d3HuYCiQhdGCQ6prOQfmjjmyjL38xaCLV4VSts9lBNDoQWSSk
        lQIDAQAB
        -----END PUBLIC KEY-----
    """.trimIndent()

    fun isConfigured(): Boolean = PUBLIC_KEY_PEM != PLACEHOLDER
}

/**
 * 一次 AI 调用的加密会话。
 *
 * 构造时就地生成会话密钥并算出 [wrappedKey]（被公钥加密的形态）。
 * 各平台的加密实现都是平台原生的（Android/JVM 走 javax.crypto，iOS 走 Swift 桥）。
 *
 * @throws IllegalStateException 公钥不可用或这端没有加密能力
 */
expect class AiCryptoSession(publicKeyPem: String) {

    /** 被服务端公钥加密后的会话密钥（base64）。 */
    val wrappedKey: String

    /** 把请求正文封成信封。 */
    fun encrypt(plainText: String): AiEnvelope

    /** 解开服务端回的响应体。密文被改过或密钥不对时抛异常。 */
    fun decrypt(iv: String, ct: String): String
}
