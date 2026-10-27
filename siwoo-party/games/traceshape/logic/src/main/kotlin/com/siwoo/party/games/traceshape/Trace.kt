package com.siwoo.party.games.traceshape

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 채점 공식 (순수 함수) */
object TraceScoring {
    /** 평균 거리 → 거리 점수 0..1 (DIST_FREE 이하 1, DIST_ZERO 이상 0, 사이는 직선) */
    fun distScore(meanDist: Float): Float {
        if (meanDist.isNaN() || meanDist.isInfinite()) return 0f
        return (1f - (meanDist - GameConfig.DIST_FREE) / (GameConfig.DIST_ZERO - GameConfig.DIST_FREE)).coerceIn(0f, 1f)
    }

    /** 확인 점 하나의 덮음 점수: 가장 가까운 그린 점까지 거리 → 0..1 (COVER_FULL 이하 1, COVER_ZERO 이상 0) */
    fun coverCredit(minDist: Float): Float =
        (1f - (minDist - GameConfig.COVER_FULL) / (GameConfig.COVER_ZERO - GameConfig.COVER_FULL)).coerceIn(0f, 1f)

    /** 정확도 0..100 = 100 × 거리 점수 × 덮은 비율 (둘 다 있어야 높다 — 한 곳 낙서·온통 낙서 모두 낮음) */
    fun accuracy(meanDist: Float, coverage: Float): Float = 100f * distScore(meanDist) * coverage.coerceIn(0f, 1f)

    /** 속도 보너스: 완료 시간(라운드 시작부터 ms) → BONUS_MAX(빠름) .. 1.0(느림). 미완성은 1.0 */
    fun speedBonus(finishMs: Long, finished: Boolean = true): Float {
        if (!finished || finishMs < 0) return 1f
        val f = (GameConfig.BONUS_SLOW_MS - finishMs).toFloat() / (GameConfig.BONUS_SLOW_MS - GameConfig.BONUS_FAST_MS)
        return 1f + (GameConfig.BONUS_MAX - 1f) * f.coerceIn(0f, 1f)
    }

    /** 라운드 점수 = 반올림(정확도 × 보너스) */
    fun roundPoints(accuracy: Float, bonus: Float): Int = (accuracy * bonus).roundToInt()

    /** 점 목록 한 번에 채점 (테스트·확인용) — 한 획으로 이어 그린 것으로 본다 */
    fun evaluate(shape: Shape, xs: FloatArray, ys: FloatArray, n: Int = xs.size): Trace {
        val t = Trace(GameConfig.MAX_POINTS)
        t.reset(shape)
        for (i in 0 until n) if (i == 0) t.penDown(xs[i], ys[i]) else t.add(xs[i], ys[i])
        return t
    }
}

/**
 * 한 사람이 한 라운드에 그린 획 (점 버퍼는 미리 만들어 라운드마다 다시 씀).
 * 점은 상자 좌표(0..1). 손을 뗐다 다시 대면 새 조각(segment)으로 이어서 모은다 (조각 사이는 잇지 않음).
 * 점을 넣을 때마다 바로 채점 값을 갱신한다:
 *  - 평균 거리 = 그린 점들에서 목표 꺾은선까지 거리의 평균
 *  - 덮은 비율 = 목표 위 확인 점(COVER_SAMPLES 개)마다 가장 가까운 그린 점 거리 → 덮음 점수 평균
 * 방향은 보지 않는다: 거꾸로 그려도 같은 점수 (자동 완료도 끝점·시작점 둘 다 인정).
 */
class Trace(val capacity: Int = GameConfig.MAX_POINTS) {
    val xs = FloatArray(capacity)
    val ys = FloatArray(capacity)
    /** 이 점이 새 조각의 첫 점인지 */
    val segStart = BooleanArray(capacity)
    var count = 0
        private set

    var shape: Shape? = null
        private set
    private val samples = FloatArray(GameConfig.COVER_SAMPLES * 2)
    private val minDist = FloatArray(GameConfig.COVER_SAMPLES)
    private var sumDist = 0.0
    private var penNew = true
    /** 버퍼가 꽉 차서 버린 점이 있었는지 */
    var overflow = false
        private set

    fun reset(s: Shape) {
        shape = s
        s.sample(GameConfig.COVER_SAMPLES, samples)
        minDist.fill(Float.MAX_VALUE)
        count = 0
        sumDist = 0.0
        penNew = true
        overflow = false
    }

    /** 손가락을 뗌 → 다음 점은 새 조각 */
    fun penUp() { penNew = true }

    /** 새 조각 시작 (손가락이 닿음). 넣었으면 true */
    fun penDown(x: Float, y: Float): Boolean {
        penNew = true
        return add(x, y)
    }

    /**
     * 점 추가. 앞 점과 MIN_STEP 보다 가까우면 버리고(false), MAX_STEP 보다 멀면 사이를 채운다.
     * 새 조각의 첫 점은 거리와 상관없이 넣는다. 버퍼가 차면 false.
     */
    fun add(x0: Float, y0: Float): Boolean {
        if (shape == null) return false
        val lim = GameConfig.OUTSIDE_LIMIT
        val x = x0.coerceIn(-lim, 1f + lim)
        val y = y0.coerceIn(-lim, 1f + lim)
        if (count >= capacity) { overflow = true; return false }
        if (penNew || count == 0) {
            push(x, y, true)
            penNew = false
            return true
        }
        val lx = xs[count - 1]
        val ly = ys[count - 1]
        val d = Shape.hypot(x - lx, y - ly)
        if (d < GameConfig.MIN_STEP) return false
        val steps = kotlin.math.ceil(d / GameConfig.MAX_STEP).toInt().coerceAtLeast(1)
        for (k in 1..steps) {
            if (count >= capacity) { overflow = true; return k > 1 }
            val f = k.toFloat() / steps
            push(lx + (x - lx) * f, ly + (y - ly) * f, false)
        }
        return true
    }

    private fun push(x: Float, y: Float, newSeg: Boolean) {
        val s = shape ?: return
        xs[count] = x
        ys[count] = y
        segStart[count] = newSeg
        count++
        sumDist += s.distance(x, y)
        for (k in minDist.indices) {
            val dx = samples[k * 2] - x
            val dy = samples[k * 2 + 1] - y
            val d = sqrt(dx * dx + dy * dy)
            if (d < minDist[k]) minDist[k] = d
        }
    }

    /** 평균 거리 (점이 없으면 무한대) */
    val meanDist: Float get() = if (count == 0) Float.POSITIVE_INFINITY else (sumDist / count).toFloat()

    /** 덮은 비율 0..1 */
    val coverage: Float
        get() {
            if (count == 0) return 0f
            var s = 0f
            for (d in minDist) s += TraceScoring.coverCredit(d)
            return s / minDist.size
        }

    /** 정확도 0..100 */
    val accuracy: Float get() = TraceScoring.accuracy(meanDist, coverage)

    val lastX: Float get() = if (count == 0) Float.NaN else xs[count - 1]
    val lastY: Float get() = if (count == 0) Float.NaN else ys[count - 1]

    /** 마지막 점이 끝점(또는 거꾸로 그린 경우의 시작점) 가까이인지 */
    fun nearEnd(): Boolean {
        val s = shape ?: return false
        if (count == 0) return false
        val x = lastX
        val y = lastY
        return Shape.hypot(x - s.endX, y - s.endY) <= GameConfig.END_TOL ||
            Shape.hypot(x - s.startX, y - s.startY) <= GameConfig.END_TOL
    }

    /**
     * 자동 완료 때 끝점까지 이어 준다: 마지막 점이 끝점(거꾸로면 시작점)에서 SNAP_TOL 안이면 그 점까지 선을 채운다
     * (끝점 바로 앞에서 완료돼도 남은 꼬리 때문에 덮음이 깎이지 않게). 근처가 아니면 그대로.
     */
    fun snapToEnd() {
        val s = shape ?: return
        if (count == 0 || penNew) return
        val de = Shape.hypot(lastX - s.endX, lastY - s.endY)
        val ds = Shape.hypot(lastX - s.startX, lastY - s.startY)
        if (de <= GameConfig.SNAP_TOL && de <= ds) add(s.endX, s.endY)
        else if (ds <= GameConfig.SNAP_TOL) add(s.startX, s.startY)
    }

    /** 확인 점이 모두 COVER_FULL 안으로 덮였는지 (빠짐없이 다 따라감) */
    fun fullyCovered(): Boolean {
        if (count == 0) return false
        for (d in minDist) if (d > GameConfig.COVER_FULL) return false
        return true
    }

    /** 자동 완료 조건: (덮은 비율 ≥ AUTO_COVERAGE 이고 끝점 근처) 또는 빠짐없이 다 덮음 */
    fun isComplete(): Boolean =
        (coverage >= GameConfig.AUTO_COVERAGE && nearEnd()) || fullyCovered()
}
