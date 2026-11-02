@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.nexio.ohos

import com.haooz.chedule.ui.utils.NativeTopBarGroup
import com.haooz.chedule.ui.utils.NativeTopBarHost
import com.haooz.chedule.ui.utils.NativeTopBarState
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

/**
 * 顶栏右上角两颗按钮的语义 → ArkUI。
 *
 * 标题、星期行、渐进模糊都在 Compose 里画；只有这两颗按钮和它们的菜单交给 ArkUI
 * （要的是系统沉浸光感材质 + 原生菜单）。这里只负责把 [NativeTopBarState] 编成 JSON
 * 推过去，把宿主点到的按钮 id 转回 [NativeTopBarHost.onAction]。
 */
object TopBarBridge {

    private var publishFn: ((String) -> Unit)? = null
    private var host: NativeTopBarHost? = null
    private var scope: CoroutineScope? = null
    private var lastJson: String? = null
    private var lastOffsets: String? = null
    private var lastChrome: NativeTopBarState? = null
    private var lastState: NativeTopBarState? = null
    private var hasPublished = false

    /** C++ 在模块注册时把推送入口交过来（见 `napi_init.cpp`）。 */
    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<(CPointer<ByteVar>?) -> Unit>> = fn.reinterpret()
        publishFn = { json -> memScoped { cFunction.invoke(json.cstr.ptr) } }
        // 注册可能晚于首次发布：那时 publish 因为没入口直接丢了，这里补发一次，
        // 否则要等下一次状态变化宿主才有东西可画（表现为顶栏/按钮一直不出现）
        if (hasPublished) {
            lastJson = null
            lastOffsets = null
            lastChrome = null
            publish(lastState)
        }
    }

    fun bind(host: NativeTopBarHost, scope: CoroutineScope) {
        this.host = host
        this.scope = scope
    }

    /** 由 Compose 的 apply 阶段同步调用。 */
    fun publish(state: NativeTopBarState?) {
        hasPublished = true
        lastState = state
        val fn = publishFn ?: return
        if (state == null) {
            if (lastJson != "null") {
                lastJson = "null"
                lastOffsets = null
                lastChrome = null
                fn("null")
            }
            return
        }
        // 翻页时只有位移在变：结构相同就只发四个位移数字，别让宿主整份替换状态
        val chrome = lastChrome
        if (chrome != null && sameChrome(chrome, state)) {
            val offsets = encodeOffsets(state)
            if (offsets == lastOffsets) return
            lastOffsets = offsets
            fn(offsets)
            return
        }
        lastChrome = state
        lastOffsets = null
        val json = encode(state)
        if (json == lastJson) return
        lastJson = json
        fn(json)
    }

    /** ArkTS 的动作回调（JS 线程）→ 回 Compose 状态写入，必须走 Main。 */
    fun dispatch(action: String) {
        val scope = scope ?: return
        val host = host ?: return
        scope.launch(Dispatchers.Main) { host.onAction?.invoke(action) }
    }

    private fun sameChrome(a: NativeTopBarState, b: NativeTopBarState): Boolean =
        a.onSurfaceArgb == b.onSurfaceArgb &&
            a.topDp == b.topDp &&
            a.sizeDp == b.sizeDp &&
            a.spacingDp == b.spacingDp &&
            a.rightPadDp == b.rightPadDp &&
            sameGroup(a.today, b.today) &&
            sameGroup(a.schedule, b.schedule)

    private fun sameGroup(a: NativeTopBarGroup, b: NativeTopBarGroup): Boolean =
        a.showsSwitch == b.showsSwitch && a.showsMore == b.showsMore && a.menu == b.menu

    /**
     * 位移的高频编码：`@<课程表x>,<课程表y>,<今日x>,<今日y>`，单位是 1/100 dp 的整数。
     * 用整数是为了避开浮点格式化（`-0.0` / 科学计数法），ArkTS 侧除以 100 即可。
     */
    private fun encodeOffsets(state: NativeTopBarState): String = buildString {
        append('@')
        append(centi(state.schedule.offsetXDp))
        append(',')
        append(centi(state.schedule.offsetYDp))
        append(',')
        append(centi(state.today.offsetXDp))
        append(',')
        append(centi(state.today.offsetYDp))
    }

    private fun centi(v: Float): Int = (v * 100f).roundToInt()

    private fun encode(state: NativeTopBarState?): String {
        if (state == null) return "null"
        return buildJsonObject {
            put("onSurface", state.onSurfaceArgb)
            put("topDp", state.topDp.toDouble())
            put("sizeDp", state.sizeDp.toDouble())
            put("spacingDp", state.spacingDp.toDouble())
            put("rightPadDp", state.rightPadDp.toDouble())
            putGroup("today", state.today)
            putGroup("schedule", state.schedule)
        }.toString()
    }

    private fun JsonObjectBuilder.putGroup(name: String, group: NativeTopBarGroup) {
        putJsonObject(name) {
            put("showsSwitch", group.showsSwitch)
            put("showsMore", group.showsMore)
            put("offsetXDp", group.offsetXDp.toDouble())
            put("offsetYDp", group.offsetYDp.toDouble())
            putJsonArray("menu") {
                group.menu.forEach { item ->
                    addJsonObject {
                        put("id", item.id)
                        put("label", item.label)
                    }
                }
            }
        }
    }
}

/** C++ 注册时调用。 */
@CName("NexioOnTopBarHostReady")
fun NexioOnTopBarHostReady(fn: COpaquePointer?) {
    TopBarBridge.onHostReady(fn)
}

/** ArkTS 点了顶栏按钮。 */
@CName("NexioOnTopBarAction")
fun NexioOnTopBarAction(action: CPointer<ByteVar>?) {
    val name = action?.toKString() ?: return
    TopBarBridge.dispatch(name)
}
