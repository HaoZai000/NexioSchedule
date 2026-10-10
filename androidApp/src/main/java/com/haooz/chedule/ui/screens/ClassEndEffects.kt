/** 下课庆祝：烟花粒子 + 随爆炸的短促震动 */
package com.haooz.chedule.ui.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.edit
import com.haooz.chedule.data.AppStorage
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 下课烟花 / 爆炸震动偏好。随烟花爆炸的短促 tick。
 */
object ClassEndEffectSettings {
    const val PREFS_NAME = "app_preferences"
    const val KEY_FIREWORKS = "class_end_fireworks"

    fun fireworksEnabled(context: Context): Boolean =
        AppStorage.store(PREFS_NAME)
            .getBoolean(KEY_FIREWORKS, true)

    /** 爆炸震动跟随全局触感开关，无单独开关 */
    fun vibrateEnabled(context: Context): Boolean {
        val prefs = AppStorage.store(PREFS_NAME)
        return prefs.getBoolean(com.haooz.chedule.ui.theme.KEY_HAPTIC_FEEDBACK, true)
    }

    fun setFireworks(context: Context, enabled: Boolean) {
        AppStorage.store(PREFS_NAME).edit {
            putBoolean(KEY_FIREWORKS, enabled)
        }
    }
}

/**
 * 烟花爆炸瞬间的短促震动。
 * 优先系统触感（清脆）；Vibrator one-shot 只作兜底。
 */
fun pulseClassEndHaptic(context: Context, haptic: HapticFeedback? = null) {
    if (!ClassEndEffectSettings.vibrateEnabled(context)) return

    if (haptic != null) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.VirtualKey) }
        return
    }

    val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
        val manager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return

    val clickSupported = android.os.Build.VERSION.SDK_INT >= 30 &&
        runCatching {
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        }.getOrDefault(false)
    val effect = if (clickSupported) {
        VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
            .compose()
    } else if (android.os.Build.VERSION.SDK_INT >= 30) {
        VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
            .compose()
    } else {
        VibrationEffect.createOneShot(10L, 255)
    }
    runCatching { vibrator.vibrate(effect) }
}

// 设计空间：1.0f ≈ 画布短边一格。x/y 同比缩放，爆发保持正圆
private const val KIND_SPARK = 0
private const val KIND_GLITTER = 1
private const val KIND_SHELL = 2
private const val KIND_FLASH = 3
/** 主爆散出的子母弹，延迟后二次起爆 */
private const val KIND_SUB_SHELL = 4

private data class FireworkParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val life: Float,
    val age: Float = 0f,
    val kind: Int = KIND_SPARK,
    val twinklePhase: Float = 0f,
    /** 子母弹二次起爆延迟（秒），仅 KIND_SUB_SHELL 使用 */
    val breakAfter: Float = 0f,
) {
    val alpha: Float
        get() = (1f - age / life).coerceIn(0f, 1f)

    fun step(dt: Float): FireworkParticle {
        val drag = when (kind) {
            KIND_GLITTER -> 0.975f
            KIND_SUB_SHELL -> 0.988f
            KIND_SHELL -> 0.992f
            else -> 0.968f
        }
        val gravity = when (kind) {
            KIND_SHELL -> 0.18f
            KIND_SUB_SHELL -> 0.32f
            else -> 0.55f
        }
        return copy(
            x = x + vx * dt,
            y = y + vy * dt + 0.5f * gravity * dt * dt,
            vx = vx * drag,
            vy = (vy + gravity * dt) * drag,
            age = age + dt
        )
    }
}

private val burstPalettes = listOf(
    listOf(Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFFFFCDD2), Color(0xFFFFEB3B)),
    listOf(Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFFF176), Color(0xFFFFAB40)),
    listOf(Color(0xFF40C4FF), Color(0xFF82B1FF), Color(0xFFB388FF), Color(0xFFE0F7FA)),
    listOf(Color(0xFF69F0AE), Color(0xFFB9F6CA), Color(0xFF00E676), Color(0xFFA7FFEB)),
    listOf(Color(0xFFFF80AB), Color(0xFFEA80FC), Color(0xFFB388FF), Color(0xFFFFF59D)),
)

/** 一次主爆：闪光 + 主环 + 内环 + 闪粉，并散出子母弹 */
private fun spawnBurst(
    cx: Float,
    cy: Float,
    random: Random,
    scale: Float,
    palette: List<Color>,
    subShellCount: Int = 0,
): List<FireworkParticle> {
    val particles = ArrayList<FireworkParticle>(120)

    particles += FireworkParticle(
        x = cx, y = cy, vx = 0f, vy = 0f,
        color = Color.White, size = 22f * scale, life = 0.18f,
        kind = KIND_FLASH
    )

    // 主环：清晰的一圈亮星，构成“花”的轮廓
    val mainCount = 48
    for (i in 0 until mainCount) {
        val angle = (i.toFloat() / mainCount) * (Math.PI * 2.0) + random.nextDouble(-0.04, 0.04)
        val speed = (0.62f + random.nextFloat() * 0.22f) * scale
        particles += FireworkParticle(
            x = cx, y = cy,
            vx = (cos(angle) * speed).toFloat(),
            vy = (sin(angle) * speed).toFloat(),
            color = palette[i % palette.size],
            size = (2.8f + random.nextFloat() * 1.6f) * scale,
            life = 0.95f + random.nextFloat() * 0.35f,
            kind = KIND_SPARK
        )
    }

    // 少量内层细星，只做层次，不糊满
    for (i in 0 until 16) {
        val angle = random.nextDouble() * Math.PI * 2.0
        val speed = (0.18f + random.nextFloat() * 0.28f) * scale
        particles += FireworkParticle(
            x = cx, y = cy,
            vx = (cos(angle) * speed).toFloat(),
            vy = (sin(angle) * speed).toFloat(),
            color = palette[random.nextInt(palette.size)].copy(alpha = 0.85f),
            size = (1.6f + random.nextFloat() * 1.2f) * scale,
            life = 0.55f + random.nextFloat() * 0.3f,
            kind = KIND_SPARK
        )
    }

    // 稀疏闪粉，点缀用
    for (i in 0 until 12) {
        val angle = random.nextDouble() * Math.PI * 2.0
        val speed = (0.35f + random.nextFloat() * 0.55f) * scale
        particles += FireworkParticle(
            x = cx, y = cy,
            vx = (cos(angle) * speed).toFloat(),
            vy = (sin(angle) * speed).toFloat() - 0.03f * scale,
            color = if (random.nextBoolean()) Color.White else palette[random.nextInt(palette.size)],
            size = 1.2f * scale,
            life = 1.0f + random.nextFloat() * 0.45f,
            kind = KIND_GLITTER,
            twinklePhase = random.nextFloat() * 6.28f
        )
    }

    // 子母弹：从主爆中心甩出，0.28~0.5s 后各自再爆
    if (subShellCount > 0) {
        val baseAngle = random.nextDouble() * Math.PI * 2.0
        for (i in 0 until subShellCount) {
            val angle = baseAngle + (i.toFloat() / subShellCount) * (Math.PI * 2.0) +
                random.nextDouble(-0.25, 0.25)
            val speed = (0.55f + random.nextFloat() * 0.35f) * scale
            val color = palette[random.nextInt(palette.size)]
            particles += FireworkParticle(
                x = cx, y = cy,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat() - 0.06f * scale,
                color = color,
                size = (4.2f + random.nextFloat() * 1.6f) * scale,
                life = 0.9f,
                kind = KIND_SUB_SHELL,
                // 错峰二次起爆，保证每朵小烟花都能打出独立震动
                breakAfter = 0.28f + i * 0.075f + random.nextFloat() * 0.05f
            )
        }
    }
    return particles
}

/** 子母弹二次起爆：小一圈的爆花 */
private fun spawnMiniBurst(
    cx: Float,
    cy: Float,
    random: Random,
    scale: Float,
    palette: List<Color>,
): List<FireworkParticle> {
    val particles = ArrayList<FireworkParticle>(40)
    particles += FireworkParticle(
        x = cx, y = cy, vx = 0f, vy = 0f,
        color = Color.White, size = 11f * scale, life = 0.12f,
        kind = KIND_FLASH
    )
    val count = 18
    for (i in 0 until count) {
        val angle = (i.toFloat() / count) * (Math.PI * 2.0) + random.nextDouble(-0.1, 0.1)
        val speed = (0.30f + random.nextFloat() * 0.18f) * scale
        particles += FireworkParticle(
            x = cx, y = cy,
            vx = (cos(angle) * speed).toFloat(),
            vy = (sin(angle) * speed).toFloat(),
            color = palette[i % palette.size],
            size = (1.8f + random.nextFloat() * 1.2f) * scale,
            life = 0.45f + random.nextFloat() * 0.25f,
            kind = KIND_SPARK
        )
    }
    for (i in 0 until 6) {
        val angle = random.nextDouble() * Math.PI * 2.0
        val speed = (0.2f + random.nextFloat() * 0.35f) * scale
        particles += FireworkParticle(
            x = cx, y = cy,
            vx = (cos(angle) * speed).toFloat(),
            vy = (sin(angle) * speed).toFloat(),
            color = if (random.nextBoolean()) Color.White else palette[random.nextInt(palette.size)],
            size = 1.1f * scale,
            life = 0.55f + random.nextFloat() * 0.3f,
            kind = KIND_GLITTER,
            twinklePhase = random.nextFloat() * 6.28f
        )
    }
    return particles
}

/**
 * 升空壳：带随机侧向弯的抛物线。
 * fromX/fromY 起点、toX/toY 目标爆点、flight 飞行秒数、curve 侧向弯幅度。
 */
private fun spawnShell(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    scale: Float,
    flight: Float,
    curve: Float,
): List<FireworkParticle> {
    val dx = toX - fromX
    val dy = toY - fromY
    // 用重力 0.18 反推初速，保证 flight 时刻接近目标
    val g = 0.18f
    val vy0 = (dy - 0.5f * g * flight * flight) / flight
    // 侧向：初速带 curve，再被 drag 拉一把，轨迹成弧线而不是直线
    val vx0 = dx / flight + curve
    return listOf(
        FireworkParticle(
            x = fromX, y = fromY, vx = vx0, vy = vy0,
            color = Color(0xFFFFF59D), size = 5.5f * scale, life = flight,
            kind = KIND_SHELL
        ),
        FireworkParticle(
            x = fromX, y = fromY, vx = vx0 * 0.92f, vy = vy0 * 0.92f,
            color = Color(0xFFFFE082), size = 3.2f * scale, life = flight * 0.94f,
            kind = KIND_SHELL
        ),
        FireworkParticle(
            x = fromX, y = fromY, vx = vx0 * 1.05f + curve * 0.15f, vy = vy0 * 1.02f,
            color = Color(0xFFFFF176), size = 2.2f * scale, life = flight * 0.9f,
            kind = KIND_SHELL
        ),
    )
}

/**
 * 下课烟花覆盖层。[trigger] 递增时开一轮，播完自动清空粒子。
 * 主爆后散出子母弹，再二次起爆（多段）。
 */
@Composable
fun FireworksOverlay(
    trigger: Int,
    modifier: Modifier = Modifier,
) {
    if (trigger <= 0) return
    var particles by remember { mutableStateOf(emptyList<FireworkParticle>()) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var canvasUnit by remember { mutableStateOf(320f) }

    LaunchedEffect(trigger) {
        if (!ClassEndEffectSettings.fireworksEnabled(context)) return@LaunchedEffect
        val random = Random(System.nanoTime() xor trigger.toLong())
        val scale = canvasUnit / 320f
        var stage = 0
        val startNanos = System.nanoTime()
        // 记录主爆落点，壳到点后按落点爆
        var pendingA: Pair<Float, Float>? = null
        var pendingB: Pair<Float, Float>? = null
        var pendingC: Pair<Float, Float>? = null
        var pendingDelayA: Long? = null
        var pendingDelayB: Long? = null
        var pendingDelayC: Long? = null
        particles = emptyList()

        while (true) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

            when (stage) {
                0 -> {
                    // 起点、落点、飞行时间、侧弯全部随机
                    val fx = 0.12f + random.nextFloat() * 0.76f
                    val tx = 0.12f + random.nextFloat() * 0.76f
                    val ty = 0.16f + random.nextFloat() * 0.18f
                    val flight = 0.30f + random.nextFloat() * 0.16f
                    val curve = (random.nextFloat() - 0.5f) * 0.9f
                    pendingA = tx to ty
                    val delayMs = (flight * 1000).toLong()
                    pendingDelayA = delayMs
                    particles = spawnShell(fx, 1.02f + random.nextFloat() * 0.08f, tx, ty, scale, flight, curve)
                    stage = 1
                }
                1 -> if (elapsedMs >= (pendingDelayA ?: 360L)) {
                    val (bx, by) = pendingA ?: (0.5f to 0.28f)
                    particles = particles.filter { it.kind != KIND_SHELL } +
                        spawnBurst(bx, by, random, scale, burstPalettes[0], subShellCount = 5)
                    pulseClassEndHaptic(context, haptic)
                    stage = 2
                }
                2 -> if (elapsedMs > (pendingDelayA ?: 360L) + 200L) {
                    val fx = 0.10f + random.nextFloat() * 0.80f
                    val tx = 0.10f + random.nextFloat() * 0.80f
                    val ty = 0.14f + random.nextFloat() * 0.20f
                    val flight = 0.28f + random.nextFloat() * 0.18f
                    val curve = (random.nextFloat() - 0.5f) * 1.0f
                    pendingB = tx to ty
                    pendingDelayB = (pendingDelayA ?: 360L) + 200L + (flight * 1000).toLong()
                    particles = particles + spawnShell(
                        fx, 1.02f + random.nextFloat() * 0.08f, tx, ty, scale, flight, curve
                    )
                    stage = 3
                }
                3 -> if (elapsedMs >= (pendingDelayB ?: 980L)) {
                    val (bx, by) = pendingB ?: (0.28f to 0.24f)
                    particles = particles.filter { it.kind != KIND_SHELL } +
                        spawnBurst(bx, by, random, scale, burstPalettes[2], subShellCount = 4)
                    pulseClassEndHaptic(context, haptic)
                    stage = 4
                }
                4 -> if (elapsedMs > (pendingDelayB ?: 980L) + 160L) {
                    val fx = 0.08f + random.nextFloat() * 0.84f
                    val tx = 0.08f + random.nextFloat() * 0.84f
                    val ty = 0.12f + random.nextFloat() * 0.22f
                    val flight = 0.26f + random.nextFloat() * 0.20f
                    val curve = (random.nextFloat() - 0.5f) * 1.1f
                    pendingC = tx to ty
                    pendingDelayC = (pendingDelayB ?: 980L) + 160L + (flight * 1000).toLong()
                    particles = particles + spawnShell(
                        fx, 1.02f + random.nextFloat() * 0.10f, tx, ty, scale, flight, curve
                    )
                    stage = 5
                }
                5 -> if (elapsedMs >= (pendingDelayC ?: 1540L)) {
                    val (bx, by) = pendingC ?: (0.68f to 0.24f)
                    particles = particles.filter { it.kind != KIND_SHELL } +
                        spawnBurst(bx, by, random, scale, burstPalettes[4], subShellCount = 5)
                    pulseClassEndHaptic(context, haptic)
                    stage = 6
                }
            }

            if (elapsedMs > 3800L) {
                particles = emptyList()
                break
            }

            val dt = 0.016f
            val nextList = ArrayList<FireworkParticle>(particles.size + 32)
            for (p in particles) {
                val next = p.step(dt)
                when {
                    next.age >= next.life -> Unit
                    // 子母弹到点二次起爆：小烟花每一朵都震一下
                    next.kind == KIND_SUB_SHELL && next.age >= next.breakAfter -> {
                        nextList += spawnMiniBurst(
                            next.x, next.y, random, scale,
                            burstPalettes[random.nextInt(burstPalettes.size)]
                        )
                        pulseClassEndHaptic(context, haptic)
                    }
                    // 闪光只淡出，不再碎成一圈细星（避免糊）
                    next.kind == KIND_FLASH && next.age > 0.16f && next.size > 8f -> {
                        // 丢弃，交给主环表达爆发
                    }
                    else -> nextList += next
                }
            }
            particles = nextList
            kotlinx.coroutines.delay(16L)
        }
    }

    Canvas(
        modifier = modifier.onGloballyPositioned { coords ->
            val side = minOf(coords.size.width, coords.size.height)
            if (side > 0) {
                canvasUnit = side.toFloat()
            }
        }
    ) {
        if (particles.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val unit = canvasUnit
        val ox = (w - unit) * 0.5f
        val oy = (h - unit) * 0.5f
        particles.forEach { drawParticle(it, unit, ox, oy) }
    }
}

private fun DrawScope.drawParticle(p: FireworkParticle, unit: Float, ox: Float, oy: Float) {
    val a = p.alpha
    if (a <= 0.015f) return
    val x = ox + p.x * unit
    val y = oy + p.y * unit
    val twinkle = if (p.kind == KIND_GLITTER) {
        (0.55f + 0.45f * sin(p.age * 28f + p.twinklePhase)).coerceIn(0.2f, 1f)
    } else 1f
    val alpha = a * twinkle
    val sizeScale = unit / 320f

    when (p.kind) {
        // 中心闪光：单层软圆即可，不再叠大光晕
        KIND_FLASH -> {
            val r = p.size * (0.9f + (1f - a) * 1.4f) * sizeScale
            drawCircle(Color.White.copy(alpha = alpha * 0.7f), r, Offset(x, y))
        }
        // 子母弹：亮核 + 短拖尾，无外圈
        KIND_SUB_SHELL -> {
            val r = p.size * sizeScale * (0.85f + 0.2f * a)
            drawLine(
                color = p.color.copy(alpha = alpha * 0.55f),
                start = Offset(x - p.vx * unit * 0.035f, y - p.vy * unit * 0.035f),
                end = Offset(x, y),
                strokeWidth = r * 0.7f,
                cap = StrokeCap.Round
            )
            drawCircle(p.color.copy(alpha = alpha), r * 0.85f, Offset(x, y))
        }
        // 火星：细拖尾 + 小实心点，干净锐利
        else -> {
            val r = (p.size * 0.55f) * sizeScale * (0.75f + 0.35f * a)
            drawLine(
                color = p.color.copy(alpha = alpha * 0.45f),
                start = Offset(x - p.vx * unit * 0.028f, y - p.vy * unit * 0.028f),
                end = Offset(x, y),
                strokeWidth = r.coerceAtLeast(1.1f),
                cap = StrokeCap.Round
            )
            drawCircle(p.color.copy(alpha = alpha), r, Offset(x, y))
        }
    }
}
