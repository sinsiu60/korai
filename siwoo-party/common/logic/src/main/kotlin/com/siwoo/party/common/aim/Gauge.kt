package com.siwoo.party.common.aim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * 0..1 → 0..1 바꾸기 (게이지 바늘의 움직임 느낌).
 * 숫자 상자(boxing) 없이 부르려고 Float 전용 인터페이스로 둔다.
 */
fun interface Easing {
    fun apply(f: Float): Float

    companion object {
        /** 일정한 속도 */
        @JvmField val LINEAR = Easing { it }
        /** 양 끝에서 느려지고 가운데서 빠름 (진자처럼) — 끝에서 멈추기 쉬움 */
        @JvmField val SINE = Easing { ((1.0 - cos(it * PI)) / 2.0).toFloat() }
        /** 처음엔 느리다가 점점 빨라짐 (위쪽이 빨리 지나감 → 큰 값을 맞히기 어려움) */
        @JvmField val EASE_IN = Easing { it * it }
        /** 처음엔 빠르다가 점점 느려짐 */
        @JvmField val EASE_OUT = Easing { 1f - (1f - it) * (1f - it) }
    }
}

/**
 * 게이지의 최적 구간 하나. 가운데 [center] ± [half] 안이면 정확도 [weight](기본 1),
 * 구간 밖으로 [falloff] 만큼 멀어지면 0 까지 직선으로 줄어든다 (falloff ≤ 0 이면 구간 밖은 바로 0).
 * 여러 구간을 겹쳐 "아주 좋음(작은 구간, weight 1) / 좋음(넓은 구간, weight 0.6)" 처럼 쓸 수도 있다.
 */
class SweetZone(
    val center: Float,
    val half: Float,
    val falloff: Float,
    val weight: Float = 1f,
) {
    /** 구간 시작·끝 (값 단위) */
    val start: Float get() = center - half
    val end: Float get() = center + half

    /** v 의 정확도 0..weight */
    fun accuracy(v: Float): Float = weight * accuracy(v, center, half, falloff)

    /** v 가 구간 안(경계 포함)인지 */
    operator fun contains(v: Float): Boolean = abs(v - center) <= half

    companion object {
        /** 최적 구간 안이면 1, 멀어질수록 줄어 falloff 만큼 벗어나면 0 */
        @JvmStatic
        fun accuracy(v: Float, center: Float, half: Float, falloff: Float): Float {
            val dist = abs(v - center) - half
            if (dist <= 0f) return 1f
            if (falloff <= 0f) return 0f
            return (1f - dist / falloff).coerceIn(0f, 1f)
        }
    }
}

/**
 * 왕복 게이지 (힘·각도·타이밍 막대 등). 시간은 밖에서 넣는다 (게임 시계 ms — 일시정지 시간이 빠진 timers.now() 권장).
 *
 *  - [start] 한 시각부터 값이 0 → 1 → 0 으로 [periodMs] 마다 한 번 왕복한다 ([pingPong] = false 면 0 → 1 톱니).
 *  - [easing] 으로 움직임 느낌을 바꾼다 (예: [Easing.SINE] 은 양 끝에서 느려짐).
 *  - [stop] 하면 그 순간 값이 고정된다 ([stopped], [stoppedValue]). 다시 [start] 하면 0 부터.
 *  - 최적 구간([zones])으로 정확도 0..1 계산: [accuracy] (여러 구간이면 가장 좋은 값).
 *  - 쓸 때마다 빨라지기: [speedUp] (주기를 factor 로 나누되 [minPeriodMs] 아래로는 안 감), [resetSpeed] 로 처음 속도.
 *    움직이는 중에 주기를 바꿔도 바늘이 튀지 않는다 (지금 위치에서 새 속도로 이어짐).
 *
 * 예) 팽이 힘 게이지:
 * ```
 * val g = Gauge(1400L, zones = listOf(SweetZone(0.85f, 0.05f, 0.4f)))
 * g.start(now)                 // 매 프레임: drawer.draw(c, g.valueAt(now), active = true)
 * val v = g.stop(now)          // 터치한 순간
 * val acc = g.accuracy(v)      // 0..1
 * g.speedUp(1.15f)             // 다음 차례엔 조금 더 빠르게
 * ```
 */
class Gauge(
    periodMs: Long,
    var easing: Easing = Easing.LINEAR,
    zones: List<SweetZone> = emptyList(),
    /** true: 0 → 1 → 0 왕복, false: 0 → 1 뒤 0 으로 돌아가 다시 (톱니) */
    var pingPong: Boolean = true,
    /** speedUp 해도 이보다 짧아지지 않음 */
    var minPeriodMs: Long = 250L,
) {
    /** 처음 주기 (resetSpeed 로 돌아갈 값) */
    var basePeriodMs: Long = maxOf(1L, periodMs)
        private set

    /** 지금 주기 (ms, 한 번 왕복) */
    var periodMs: Long = basePeriodMs
        private set

    /** 최적 구간들 (그리기에서도 씀) */
    var zones: List<SweetZone> = zones

    /** 움직이기 시작한 시각 (start 또는 마지막 속도 변경 시각) */
    var startMs = 0L
        private set

    /** start 한 뒤로 움직이는 중인지 (start 전이거나 stop 뒤면 false) */
    var running = false
        private set

    /** stop 으로 멈췄는지 */
    var stopped = false
        private set

    /** 멈춘 값 (stop 전에는 0) */
    var stoppedValue = 0f
        private set

    /** startMs 때의 위상 0..1 (속도를 바꿔도 이어지게) */
    private var phase0 = 0.0

    /** 0 에서 출발 */
    fun start(nowMs: Long) {
        startMs = nowMs
        phase0 = 0.0
        running = true
        stopped = false
        stoppedValue = 0f
    }

    /** 멈춤 (start 전으로) — 값은 0 */
    fun reset() {
        running = false
        stopped = false
        stoppedValue = 0f
        phase0 = 0.0
    }

    /** 위상 0..1 (한 주기에서 어디쯤인지) */
    fun phaseAt(nowMs: Long): Float {
        if (!running) return 0f
        val e = maxOf(nowMs - startMs, 0L)
        if (phase0 == 0.0) return (e % periodMs).toFloat() / periodMs
        val p = (phase0 + e.toDouble() / periodMs) % 1.0
        return p.toFloat()
    }

    /** 지금 값 0..1 (멈췄으면 멈춘 값, 시작 전이면 0) */
    fun valueAt(nowMs: Long): Float {
        if (stopped) return stoppedValue
        if (!running) return 0f
        val f = phaseAt(nowMs)
        val raw = if (pingPong) triangleOf(f) else f
        return easing.apply(raw).coerceIn(0f, 1f)
    }

    /** 이번 왕복에서 값이 올라가는 중인지 (톱니면 항상 true) */
    fun risingAt(nowMs: Long): Boolean = !pingPong || phaseAt(nowMs) < 0.5f

    /** 그 순간 값으로 멈추고 돌려준다 */
    fun stop(nowMs: Long): Float {
        val v = valueAt(nowMs)
        stoppedValue = v
        stopped = true
        running = false
        return v
    }

    /** 주기를 바꿈. 움직이는 중이면 nowMs 의 위치에서 새 속도로 이어진다 */
    fun setPeriod(newPeriodMs: Long, nowMs: Long = startMs) {
        val p = maxOf(1L, newPeriodMs)
        if (running) {
            phase0 = phaseAt(nowMs).toDouble()
            startMs = nowMs
        }
        periodMs = p
    }

    /** 빨라지기: 주기 ÷ factor (minPeriodMs 아래로는 안 감). 움직이는 중이면 nowMs 를 넣어 줄 것 */
    fun speedUp(factor: Float, nowMs: Long = startMs) {
        if (factor <= 0f) return
        val p = maxOf(minOf(minPeriodMs, periodMs), (periodMs / factor).toLong())
        setPeriod(p, nowMs)
    }

    /** 처음 속도로 */
    fun resetSpeed(nowMs: Long = startMs) = setPeriod(basePeriodMs, nowMs)

    /** 처음 속도 자체를 바꿈 (지금 주기도 같이) */
    fun setBasePeriod(ms: Long, nowMs: Long = startMs) {
        basePeriodMs = maxOf(1L, ms)
        setPeriod(basePeriodMs, nowMs)
    }

    /** v 의 정확도 0..1 (구간이 없으면 0, 여러 구간이면 가장 좋은 값) */
    fun accuracy(v: Float): Float {
        var best = 0f
        for (i in 0 until zones.size) {
            val a = zones[i].accuracy(v)
            if (a > best) best = a
        }
        return best
    }

    /** 멈춘 값의 정확도 */
    fun stoppedAccuracy(): Float = accuracy(stoppedValue)

    /** v 가 들어 있는 구간 번호 (weight 가 가장 큰 것, 없으면 -1) */
    fun zoneIndex(v: Float): Int {
        var best = -1
        for (i in 0 until zones.size) {
            val z = zones[i]
            if (v in z && (best < 0 || z.weight > zones[best].weight)) best = i
        }
        return best
    }

    companion object {
        /** 위상 f(0..1) → 0 → 1 → 0 */
        @JvmStatic
        fun triangleOf(f: Float): Float = if (f < 0.5f) f * 2f else 2f - f * 2f

        /** 0 → 1 → 0 왕복 (period 마다 한 번) — 시작 뒤 지난 시간으로 바로 계산 */
        @JvmStatic
        fun triangle(elapsedMs: Long, periodMs: Long): Float {
            val p = maxOf(1L, periodMs)
            val f = (maxOf(elapsedMs, 0L) % p).toFloat() / p
            return triangleOf(f)
        }

        /** 값 0..1 → lo..hi (예: 각도 게이지 0..1 → 15°..75°) */
        @JvmStatic
        fun map(v: Float, lo: Float, hi: Float): Float = lo + (hi - lo) * v
    }
}
