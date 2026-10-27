package com.siwoo.party.games.traceshape

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 도형 종류 (label = 화면에 쓰는 이름) */
enum class ShapeKind(val label: String) {
    CIRCLE("원"),
    STAR("별"),
    HEART("하트"),
    ZIGZAG("지그재그"),
    TRIANGLE("삼각형"),
    SPIRAL("나선"),
    WAVE("물결"),
    INFINITY("8자"),
}

/**
 * 목표 도형 = 꺾은선 (상자 0..1 × 0..1 좌표, y 는 아래로). 첫 점 = 시작점, 마지막 점 = 끝점.
 * 닫힌 도형은 마지막 점이 첫 점과 같다 (한 바퀴 돌아 시작점으로 돌아옴).
 */
class Shape(val kind: ShapeKind, val xs: FloatArray, val ys: FloatArray, val closed: Boolean) {
    init {
        require(xs.size == ys.size && xs.size >= 2) { "점 2개 이상" }
    }

    val size: Int get() = xs.size
    /** 꼭짓점까지 누적 길이 */
    private val cum = FloatArray(xs.size)
    /** 전체 길이 (상자 한 변 = 1) */
    val length: Float

    init {
        var s = 0f
        for (i in 1 until xs.size) {
            s += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            cum[i] = s
        }
        length = s
    }

    val startX: Float get() = xs[0]
    val startY: Float get() = ys[0]
    val endX: Float get() = xs[xs.size - 1]
    val endY: Float get() = ys[ys.size - 1]

    /** 길이 비율 u(0..1) 지점 → out[0], out[1] (out[2], out[3] = 진행 방향 단위 벡터) */
    fun pointAt(u: Float, out: FloatArray) {
        val d = u.coerceIn(0f, 1f) * length
        var i = 1
        while (i < xs.size - 1 && cum[i] < d) i++
        val seg = cum[i] - cum[i - 1]
        val f = if (seg <= 0f) 0f else ((d - cum[i - 1]) / seg).coerceIn(0f, 1f)
        val dx = xs[i] - xs[i - 1]
        val dy = ys[i] - ys[i - 1]
        out[0] = xs[i - 1] + dx * f
        out[1] = ys[i - 1] + dy * f
        if (out.size >= 4) {
            val l = hypot(dx, dy)
            out[2] = if (l > 0f) dx / l else 1f
            out[3] = if (l > 0f) dy / l else 0f
        }
    }

    /**
     * 경로 위에 고르게 m 개 점 (x0, y0, x1, y1 …).
     * 열린 도형은 시작·끝 포함, 닫힌 도형은 끝(=시작)을 한 번만.
     */
    fun sample(m: Int, out: FloatArray = FloatArray(m * 2)): FloatArray {
        val tmp = FloatArray(2)
        for (k in 0 until m) {
            val u = if (closed) k.toFloat() / m else if (m == 1) 0f else k.toFloat() / (m - 1)
            pointAt(u, tmp)
            out[k * 2] = tmp[0]
            out[k * 2 + 1] = tmp[1]
        }
        return out
    }

    /** 점 (x, y) 에서 꺾은선까지 가장 가까운 거리 */
    fun distance(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (i in 1 until xs.size) {
            val d2 = segDist2(x, y, xs[i - 1], ys[i - 1], xs[i], ys[i])
            if (d2 < best) best = d2
        }
        return sqrt(best)
    }

    companion object {
        /** 점-선분 거리 제곱 */
        fun segDist2(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax
            val dy = by - ay
            val l2 = dx * dx + dy * dy
            var t = if (l2 <= 0f) 0f else ((px - ax) * dx + (py - ay) * dy) / l2
            t = t.coerceIn(0f, 1f)
            val qx = ax + dx * t - px
            val qy = ay + dy * t - py
            return qx * qx + qy * qy
        }

        fun hypot(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)
    }
}

/** 도형 만들기 (모두 상자 안 여백 MARGIN 안쪽에 비율을 지켜 가운데 맞춤) */
object Shapes {
    /** 상자 가장자리 여백 */
    const val MARGIN = 0.07f

    fun make(kind: ShapeKind): Shape = when (kind) {
        ShapeKind.CIRCLE -> circle()
        ShapeKind.STAR -> star()
        ShapeKind.HEART -> heart()
        ShapeKind.ZIGZAG -> zigzag()
        ShapeKind.TRIANGLE -> triangle()
        ShapeKind.SPIRAL -> spiral()
        ShapeKind.WAVE -> wave()
        ShapeKind.INFINITY -> infinity()
    }

    /** 라운드 수만큼 서로 다른 도형을 고른다 (난수로 섞어 앞에서부터) */
    fun pick(count: Int, random: Random): List<ShapeKind> {
        require(count in 1..ShapeKind.values().size) { "도형 종류보다 많을 수 없음" }
        return ShapeKind.values().toMutableList().shuffled(random).take(count)
    }

    /** 원: 위에서 시작해 시계 방향 한 바퀴 */
    private fun circle(): Shape {
        val n = 72
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        for (k in 0..n) {
            val a = -PI / 2 + 2 * PI * k / n
            xs[k] = cos(a).toFloat()
            ys[k] = sin(a).toFloat()
        }
        return fit(ShapeKind.CIRCLE, xs, ys, true)
    }

    /** 별: 위 꼭짓점에서 시작해 시계 방향 (바깥·안쪽 번갈아 10개) */
    private fun star(): Shape {
        val xs = FloatArray(11)
        val ys = FloatArray(11)
        for (k in 0..10) {
            val a = -PI / 2 + PI * k / 5
            val r = if (k % 2 == 0) 1.0 else 0.42
            xs[k] = (r * cos(a)).toFloat()
            ys[k] = (r * sin(a)).toFloat()
        }
        return fit(ShapeKind.STAR, xs, ys, true)
    }

    /** 하트: 위 가운데 오목한 곳에서 시작해 오른쪽 볼록 → 아래 끝 → 왼쪽 볼록 → 다시 위 */
    private fun heart(): Shape {
        val n = 80
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        for (k in 0..n) {
            val t = 2 * PI * k / n
            val s = sin(t)
            xs[k] = (16 * s * s * s).toFloat()
            ys[k] = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)).toFloat()
        }
        xs[n] = xs[0]; ys[n] = ys[0]
        return fit(ShapeKind.HEART, xs, ys, true)
    }

    /** 지그재그: 왼쪽 아래에서 오른쪽으로 봉우리 3개 */
    private fun zigzag(): Shape {
        val n = 6
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        for (k in 0..n) {
            xs[k] = k / n.toFloat()
            ys[k] = if (k % 2 == 0) 0.75f else 0f
        }
        return fit(ShapeKind.ZIGZAG, xs, ys, false)
    }

    /** 삼각형: 위 꼭짓점 → 오른쪽 아래 → 왼쪽 아래 → 위 */
    private fun triangle(): Shape {
        val h = (sqrt(3.0) / 2).toFloat()
        return fit(ShapeKind.TRIANGLE, floatArrayOf(0.5f, 1f, 0f, 0.5f), floatArrayOf(0f, h, h, 0f), true)
    }

    /** 나선: 가운데에서 시작해 시계 방향으로 2바퀴 돌며 바깥으로 */
    private fun spiral(): Shape {
        val n = 120
        val turns = 2.0
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        for (k in 0..n) {
            val u = k.toDouble() / n
            val r = 0.08 + 0.92 * u
            val a = -PI / 2 + 2 * PI * turns * u
            xs[k] = (r * cos(a)).toFloat()
            ys[k] = (r * sin(a)).toFloat()
        }
        return fit(ShapeKind.SPIRAL, xs, ys, false)
    }

    /** 물결: 왼쪽에서 오른쪽으로 두 번 출렁 */
    private fun wave(): Shape {
        val n = 64
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        for (k in 0..n) {
            val u = k.toDouble() / n
            xs[k] = u.toFloat()
            ys[k] = (-0.3 * sin(2 * PI * 2 * u)).toFloat()
        }
        return fit(ShapeKind.WAVE, xs, ys, false)
    }

    /** 8자(옆으로 누운 무한대): 오른쪽 끝에서 시작해 오른쪽 고리 → 가운데 교차 → 왼쪽 고리 → 오른쪽 끝 */
    private fun infinity(): Shape {
        val n = 96
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        for (k in 0..n) {
            val t = 2 * PI * k / n
            val s = sin(t)
            val d = 1 + s * s
            xs[k] = (cos(t) / d).toFloat()
            ys[k] = (1.6 * s * cos(t) / d).toFloat()
        }
        xs[n] = xs[0]; ys[n] = ys[0]
        return fit(ShapeKind.INFINITY, xs, ys, true)
    }

    /** 비율을 지켜 상자 [MARGIN, 1-MARGIN] 안 가운데로 */
    private fun fit(kind: ShapeKind, xs: FloatArray, ys: FloatArray, closed: Boolean): Shape {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE
        var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in xs.indices) {
            x0 = min(x0, xs[i]); x1 = max(x1, xs[i])
            y0 = min(y0, ys[i]); y1 = max(y1, ys[i])
        }
        val span = 1f - 2 * MARGIN
        val s = span / max(x1 - x0, y1 - y0)
        val ox = 0.5f - (x0 + x1) / 2f * s
        val oy = 0.5f - (y0 + y1) / 2f * s
        for (i in xs.indices) {
            xs[i] = xs[i] * s + ox
            ys[i] = ys[i] * s + oy
        }
        if (closed) {
            // 닫힘 오차 정리
            val last = xs.size - 1
            if (abs(xs[last] - xs[0]) < 1e-4f && abs(ys[last] - ys[0]) < 1e-4f) { xs[last] = xs[0]; ys[last] = ys[0] }
        }
        return Shape(kind, xs, ys, closed)
    }
}
