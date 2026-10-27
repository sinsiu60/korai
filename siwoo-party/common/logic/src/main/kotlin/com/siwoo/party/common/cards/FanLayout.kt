package com.siwoo.party.common.cards

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * 손패 부채꼴 배치 (순수 계산, 픽셀 단위). 결과는 미리 만든 배열 [xs] [ys] [rots] 에 담는다
 * → 매 프레임 다시 계산해도 새 객체가 생기지 않는다 (capacity 보다 많을 때만 배열을 키움).
 *
 * - (xs[i], ys[i]) = i 번째 카드의 가운데, rots[i] = 회전(도, 시계 방향 +; Canvas.rotate 와 같음)
 * - 0 번이 왼쪽·맨 아래, count-1 번이 오른쪽·맨 위 (나중 카드가 위에 그려진다)
 * - 가운데 카드가 baselineY, 바깥 카드일수록 원호를 따라 조금 내려가고 바깥쪽으로 기운다
 * - 카드가 많으면 간격이 줄어 겹친다. 회전한 카드의 가로 폭까지 포함해 availWidth 안에 들어간다
 *   (카드 한 장 폭보다 좁게 주면 들어갈 수 없으니 간격 0 으로 겹쳐 쌓임)
 */
class FanLayout(capacity: Int = 32) {

    var xs = FloatArray(capacity)
        private set
    var ys = FloatArray(capacity)
        private set
    var rots = FloatArray(capacity)
        private set

    /** 마지막 [layout] 의 장수 */
    var count = 0
        private set
    var cardWidth = 0f
        private set
    var cardHeight = 0f
        private set
    /** 카드 가운데끼리 가로 간격 */
    var gap = 0f
        private set
    /** 회전 포함 전체 가로 폭 */
    var spanWidth = 0f
        private set

    /**
     * 배치 계산.
     * @param maxSpreadDeg 양 끝 카드 사이 최대 각도 (0 이면 회전 없이 일렬)
     * @param maxStepDeg 이웃 카드 사이 최대 각도 (적은 장수일 때 너무 벌어지지 않게)
     * @param maxGapFrac 이웃 카드 가운데 간격의 최대값 (카드 폭 배수, 1 이상이면 안 겹침)
     */
    fun layout(
        count: Int,
        availWidth: Float,
        cardW: Float,
        cardH: Float,
        centerX: Float,
        baselineY: Float,
        maxSpreadDeg: Float = 30f,
        maxStepDeg: Float = 5f,
        maxGapFrac: Float = 0.62f,
    ) {
        require(count >= 0) { "count >= 0" }
        ensureCapacity(count)
        this.count = count
        cardWidth = cardW
        cardHeight = cardH
        if (count == 0) {
            gap = 0f; spanWidth = 0f
            return
        }
        if (count == 1) {
            xs[0] = centerX; ys[0] = baselineY; rots[0] = 0f
            gap = 0f; spanWidth = cardW
            return
        }
        val n1 = count - 1
        val stepDeg = min(maxStepDeg, maxSpreadDeg / n1).coerceAtLeast(0f)
        val outerDeg = stepDeg * n1 / 2f
        val ext = halfExtentX(outerDeg, cardW, cardH)
        val fit = (availWidth - 2f * ext) / n1
        gap = max(0f, min(cardW * maxGapFrac, fit))
        spanWidth = gap * n1 + 2f * ext
        val mid = n1 / 2f
        for (i in 0 until count) {
            val k = i - mid
            val off = k * gap
            val rot = k * stepDeg
            xs[i] = centerX + off
            rots[i] = rot
            // 원호: 회전축이 아래쪽에 있는 것처럼 바깥 카드일수록 조금 내려감
            ys[i] = baselineY + abs(off) * tan(toRad(abs(rot)) / 2f)
        }
    }

    /**
     * 배치를 점 (cx, cy) 기준으로 180° 돌린다 — 맞은편(화면 위쪽)에 앉은 사람의 손패용.
     * 카드도 거꾸로(회전 +180°) 되고, 0 번 카드는 오른쪽으로 간다.
     */
    fun turnAround(cx: Float, cy: Float) {
        for (i in 0 until count) {
            xs[i] = 2f * cx - xs[i]
            ys[i] = 2f * cy - ys[i]
            rots[i] = rots[i] + 180f
        }
    }

    /**
     * (x, y) 아래 카드 번호, 없으면 -1. 위에 그려진 카드(번호 큰 쪽)부터 본다.
     * lift[i] = 위로 떠오른 px (선택), 회전을 정확히 반영 (모서리 둥근 부분만 무시).
     */
    fun hit(x: Float, y: Float, lift: FloatArray? = null): Int {
        for (i in count - 1 downTo 0) {
            val dy = if (lift != null && i < lift.size) lift[i] else 0f
            if (contains(x, y, xs[i], ys[i] - dy, rots[i], cardWidth, cardHeight)) return i
        }
        return -1
    }

    private fun ensureCapacity(n: Int) {
        if (n <= xs.size) return
        val c = max(n, xs.size * 2)
        xs = xs.copyOf(c)
        ys = ys.copyOf(c)
        rots = rots.copyOf(c)
    }

    companion object {
        private fun toRad(deg: Float): Float = (deg * Math.PI / 180.0).toFloat()

        /** rotDeg 만큼 돈 w×h 카드의 가로 반폭 */
        fun halfExtentX(rotDeg: Float, w: Float, h: Float): Float {
            val r = toRad(rotDeg)
            return abs(cos(r)) * w / 2f + abs(sin(r)) * h / 2f
        }

        /**
         * 점 (px, py) 가 가운데 (cx, cy), 회전 rotDeg(시계 +), 크기 w×h 인 카드 안에 있는지.
         * CardTableView 터치 판정에도 쓴다.
         */
        fun contains(px: Float, py: Float, cx: Float, cy: Float, rotDeg: Float, w: Float, h: Float): Boolean {
            val dx = px - cx
            val dy = py - cy
            val r = toRad(rotDeg)
            val c = cos(r)
            val s = sin(r)
            // 화면 회전(시계 방향)을 되돌려 카드 기준 좌표로
            val lx = dx * c + dy * s
            val ly = -dx * s + dy * c
            return abs(lx) <= w / 2f && abs(ly) <= h / 2f
        }
    }
}
