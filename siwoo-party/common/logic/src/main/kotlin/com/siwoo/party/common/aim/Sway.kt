package com.siwoo.party.common.aim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 흔들리는 조준점의 궤적 모양 */
enum class SwayShape {
    /** 가로 8자 (∞) — x 한 번 왕복할 때 y 두 번 (리사주 1:2) */
    FIGURE8,
    /** 세로 8자 — y 한 번 왕복할 때 x 두 번 */
    FIGURE8_VERTICAL,
    /** 원 (amplitudeX ≠ amplitudeY 면 타원) */
    CIRCLE,
}

/**
 * 흔들리는 조준점 (다트·양궁 등). 시각 t(ms)를 넣으면 가운데에서 벗어난 위치(오프셋)를 준다 — 같은 t 면 늘 같은 값.
 *
 *  - [shape] 8자/원 궤적, [amplitudeX]/[amplitudeY] 흔들림 크기(화면 px 등 원하는 단위), [periodMs] 한 바퀴 시간.
 *  - [jitter] > 0 이면 [seed] 로 정해지는 작은 떨림을 더한다 (부드러운 값 잡음, [jitterPeriodMs] 마다 새 목표).
 *  - scale 인자로 흔들림 전체를 키우고 줄인다 (예: 숨 참기 [BreathHold.multiplierAt]).
 *
 * 예) 양궁:
 * ```
 * val sway = Sway(SwayShape.FIGURE8, amplitudeX = 40f * dp, amplitudeY = 26f * dp, periodMs = 2600L, jitter = 4f * dp, seed = 7)
 * val breath = BreathHold()                       // 누르면 breath.press(now), 떼면 breath.release(now) → 발사
 * sway.offset(now, breath.multiplierAt(now), out) // out[0], out[1] = 과녁 가운데에서 조준점까지
 * ```
 */
class Sway(
    var shape: SwayShape = SwayShape.FIGURE8,
    var amplitudeX: Float,
    var amplitudeY: Float = amplitudeX,
    var periodMs: Long = 2400L,
    /** 떨림 크기 (amplitude 와 같은 단위, 0 이면 없음) */
    var jitter: Float = 0f,
    var jitterPeriodMs: Long = 320L,
    var seed: Long = 0L,
    /** 궤적 시작 위치를 옮김 (ms) */
    var phaseMs: Long = 0L,
) {
    /** 두 방향 크기를 같이 바꿈 */
    fun setAmplitude(ax: Float, ay: Float = ax) {
        amplitudeX = ax
        amplitudeY = ay
    }

    /** 궤적 각도 θ (라디안, 0..2π) */
    private fun theta(tMs: Long): Double {
        val p = maxOf(1L, periodMs)
        val m = Math.floorMod(tMs + phaseMs, p)
        return 2.0 * PI * m / p
    }

    /** 가운데에서 벗어난 x (scale 배) */
    fun x(tMs: Long, scale: Float = 1f): Float {
        val th = theta(tMs)
        val base = when (shape) {
            SwayShape.FIGURE8 -> sin(th)
            SwayShape.FIGURE8_VERTICAL -> sin(2.0 * th)
            else -> cos(th) // CIRCLE (else: 도달 못 하는 예외 분기 없이)
        }
        var v = (base * amplitudeX).toFloat()
        if (jitter != 0f) v += jitter * noise(tMs, 0)
        return v * scale
    }

    /** 가운데에서 벗어난 y (scale 배) */
    fun y(tMs: Long, scale: Float = 1f): Float {
        val th = theta(tMs)
        val base = when (shape) {
            SwayShape.FIGURE8 -> sin(2.0 * th)
            SwayShape.FIGURE8_VERTICAL -> sin(th)
            else -> sin(th) // CIRCLE
        }
        var v = (base * amplitudeY).toFloat()
        if (jitter != 0f) v += jitter * noise(tMs, 1)
        return v * scale
    }

    /** out[0] = x, out[1] = y (할당 없음 — onDraw·프레임 루프에서 써도 됨) */
    fun offset(tMs: Long, scale: Float, out: FloatArray) {
        out[0] = x(tMs, scale)
        out[1] = y(tMs, scale)
    }

    /** 떨림 −1..1: jitterPeriodMs 마다 정해진 값 사이를 부드럽게 잇는다 (seed·ch 로 결정) */
    private fun noise(tMs: Long, ch: Int): Float {
        val jp = maxOf(1L, jitterPeriodMs)
        val k = Math.floorDiv(tMs, jp)
        val f = (tMs - k * jp).toFloat() / jp
        val a = hash(k, ch)
        val b = hash(k + 1, ch)
        val s = f * f * (3f - 2f * f)
        return a + (b - a) * s
    }

    /** 정수 → −1..1 (SplitMix64 섞기) */
    private fun hash(k: Long, ch: Int): Float {
        var z = k * -0x61c8864680b583ebL + seed * 0x5851F42D4C957F2DL + (ch + 1) * 0x14057B7EF767814FL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        val u = (z ushr 40).toFloat() / (1 shl 24).toFloat() // 0..1
        return u * 2f - 1f
    }
}

/**
 * 숨 참기 (양궁 등): 화면을 누르고 있는 시간 → 흔들림 배율.
 *
 *  누른 직후 [calmMs] 동안 1 → [minMultiplier] 로 줄고, 누른 지 [holdLimitMs] 까지 가장 작게 유지,
 *  그 뒤 숨이 차서 [growMs] 동안 [maxMultiplier] 까지 커진다 (그 뒤는 그대로). 안 누르면 1.
 *
 *  상태로 쓰기: [press] (누름) → 매 프레임 [multiplierAt] → [release] (뗌, 누른 시간 반환 — 보통 여기서 발사).
 *  순수 함수로 쓰기: [multiplier] (누른 시간 ms → 배율).
 */
class BreathHold(
    var minMultiplier: Float = 0.3f,
    var calmMs: Long = 600L,
    var holdLimitMs: Long = 2000L,
    var growMs: Long = 1500L,
    var maxMultiplier: Float = 2f,
) {
    /** 누르고 있는지 */
    var holding = false
        private set

    /** 누른 시각 */
    var pressedAt = 0L
        private set

    fun press(nowMs: Long) {
        holding = true
        pressedAt = nowMs
    }

    /** 뗌 → 누르고 있던 시간 (안 누르고 있었으면 0) */
    fun release(nowMs: Long): Long {
        if (!holding) return 0L
        holding = false
        return maxOf(0L, nowMs - pressedAt)
    }

    /** 누른 지 얼마나 됐는지 (안 누르면 0) */
    fun heldMs(nowMs: Long): Long = if (holding) maxOf(0L, nowMs - pressedAt) else 0L

    /** 지금 흔들림 배율 (안 누르면 1) */
    fun multiplierAt(nowMs: Long): Float = if (holding) multiplier(heldMs(nowMs)) else 1f

    /** 숨이 차기 시작했는지 (한계 넘음) */
    fun outOfBreath(nowMs: Long): Boolean = holding && heldMs(nowMs) > holdLimitMs

    /** 남은 숨 1 → 0 (한계까지 얼마나 남았는지, 숨 막대 그리기용) */
    fun breathLeft(nowMs: Long): Float {
        if (!holding) return 1f
        if (holdLimitMs <= 0L) return 0f
        return (1f - heldMs(nowMs).toFloat() / holdLimitMs).coerceIn(0f, 1f)
    }

    /** 누른 시간 → 배율: 1 → min (calmMs, 처음에 빨리 줄어듦) → min 유지 (holdLimitMs 까지) → max (growMs 동안, 점점 빨리) */
    fun multiplier(heldMs: Long): Float {
        if (heldMs <= 0L) return 1f
        val calm = minOf(calmMs, holdLimitMs)
        if (heldMs < calm) {
            val f = heldMs.toFloat() / calm
            val e = 1f - (1f - f) * (1f - f)
            return 1f + (minMultiplier - 1f) * e
        }
        if (heldMs <= holdLimitMs) return minMultiplier
        if (growMs <= 0L) return maxMultiplier
        val g = ((heldMs - holdLimitMs).toFloat() / growMs).coerceIn(0f, 1f)
        return minMultiplier + (maxMultiplier - minMultiplier) * g * g
    }
}
