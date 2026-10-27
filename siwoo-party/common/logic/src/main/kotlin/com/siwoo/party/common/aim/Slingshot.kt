package com.siwoo.party.common.aim

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 드래그 새총 (컬링·구슬치기·미니 골프·포탄 등): 끌기 시작점 → 지금 손가락 위치로 방향·힘을 정한다.
 * 단위는 자유 (보통 화면 px — maxDistance·minDistance 를 dp 로 계산해 넣을 것).
 *
 *  - 방향 [dirX]/[dirY]: 단위 벡터. [pullBack] = true(기본)면 새총처럼 당긴 반대쪽, false 면 끈 쪽.
 *  - 힘 [power] 0..1 = 끈 거리 / [maxDistance] (넘으면 1, [powerEasing] 으로 곡선을 줄 수 있음).
 *  - 끈 거리가 [minDistance] 보다 짧으면 [cancelled] — 손을 떼도 쏘지 않는다 (잘못 터치 방지).
 *  - 안내 점선: [guidePoints] / 포물선 안내: [arcPoints] (할당 없음, out 배열에 x,y 를 번갈아 채움).
 *
 * 예)
 * ```
 * val sling = Slingshot(maxDistance = 120f * dp, minDistance = 18f * dp)
 * sling.begin(ballX, ballY); sling.move(x, y)           // 터치 이동마다 (또는 SlingshotTouch 사용)
 * if (sling.release(x, y)) shoot(sling.dirX * sling.power * maxSpeed, sling.dirY * sling.power * maxSpeed)
 * ```
 */
class Slingshot(
    var maxDistance: Float,
    var minDistance: Float = maxDistance * 0.15f,
    /** true: 당긴 반대쪽으로 쏨 (새총), false: 끈 쪽으로 */
    var pullBack: Boolean = true,
    var powerEasing: Easing = Easing.LINEAR,
) {
    /** 끄는 중인지 (begin ~ release/cancel) */
    var active = false
        private set

    var startX = 0f
        private set
    var startY = 0f
        private set
    var currentX = 0f
        private set
    var currentY = 0f
        private set

    /** 끌기 시작 (손가락 down 또는 고정된 기준점) */
    fun begin(x: Float, y: Float) {
        startX = x
        startY = y
        currentX = x
        currentY = y
        active = true
    }

    /** 손가락 이동 */
    fun move(x: Float, y: Float) {
        currentX = x
        currentY = y
    }

    /**
     * 손 뗌 → 쏠 수 있으면 true (최소 거리 이상). 방향·힘 값은 다음 begin 전까지 그대로 남는다.
     * 끄는 중이 아니었으면 false.
     */
    fun release(x: Float, y: Float): Boolean {
        if (!active) return false
        move(x, y)
        active = false
        return !cancelled
    }

    /** 지금 위치로 손 뗌 */
    fun release(): Boolean = release(currentX, currentY)

    /** 쏘지 않고 끝냄 (값을 처음으로) */
    fun cancel() {
        active = false
        currentX = startX
        currentY = startY
    }

    /** 끈 양 (지금 − 시작) */
    val dragX: Float get() = currentX - startX
    val dragY: Float get() = currentY - startY

    /** 끈 거리 */
    val distance: Float get() = sqrt(dragX * dragX + dragY * dragY)

    /** 최대 거리로 자른 끈 거리 */
    val clampedDistance: Float get() = minOf(distance, maxDistance)

    /** 최소 거리보다 짧음 → 취소 */
    val cancelled: Boolean get() = distance < minDistance

    /** 힘 0..1 */
    val power: Float
        get() {
            if (maxDistance <= 0f) return 0f
            return powerEasing.apply((distance / maxDistance).coerceIn(0f, 1f)).coerceIn(0f, 1f)
        }

    /** 쏘는 방향 (단위 벡터, 거리가 0 이면 0) */
    val dirX: Float
        get() {
            val d = distance
            if (d <= 0f) return 0f
            return if (pullBack) -dragX / d else dragX / d
        }
    val dirY: Float
        get() {
            val d = distance
            if (d <= 0f) return 0f
            return if (pullBack) -dragY / d else dragY / d
        }

    /** 쏘는 방향 각도 (라디안, atan2(dirY, dirX) — 화면 좌표면 y 가 아래쪽) */
    val angle: Float get() = atan2(dirY, dirX)

    /** 고무줄을 당긴 점 (시작점 + 최대 거리로 자른 끈 양) — 새총 고무줄·당긴 공 그리기용 */
    val pullX: Float
        get() {
            val d = distance
            return if (d <= maxDistance || d <= 0f) currentX else startX + dragX / d * maxDistance
        }
    val pullY: Float
        get() {
            val d = distance
            return if (d <= maxDistance || d <= 0f) currentY else startY + dragY / d * maxDistance
        }

    /**
     * 안내 점선: origin 에서 쏘는 방향으로 spacing 간격의 점 count 개 (첫 점은 origin + spacing).
     * out 에 x0,y0,x1,y1,… 을 채우고 채운 점 수를 돌려준다 (out 크기가 작으면 거기까지, 방향이 없으면 0).
     */
    fun guidePoints(originX: Float, originY: Float, spacing: Float, count: Int, out: FloatArray): Int {
        val dx = dirX
        val dy = dirY
        if (dx == 0f && dy == 0f) return 0
        val n = minOf(count, out.size / 2)
        for (k in 0 until n) {
            val s = spacing * (k + 1)
            out[2 * k] = originX + dx * s
            out[2 * k + 1] = originY + dy * s
        }
        return n
    }

    /** 안내 점선을 길이로: origin 에서 length 까지 count 개를 고르게 (보통 length = 최대 길이 × power) */
    fun guideLine(originX: Float, originY: Float, length: Float, count: Int, out: FloatArray): Int {
        if (count <= 0) return 0
        return guidePoints(originX, originY, length / count, count, out)
    }

    /**
     * 포물선 안내 (포탄 등): 처음 속도 = 방향 × power × maxSpeed, 아래로 gravity (단위/초², 화면 좌표면 +).
     * stepSec 간격의 위치 count 개 (첫 점은 stepSec 뒤)를 out 에 채우고 채운 점 수를 돌려준다.
     */
    fun arcPoints(originX: Float, originY: Float, maxSpeed: Float, gravity: Float, stepSec: Float, count: Int, out: FloatArray): Int {
        val vx = dirX * power * maxSpeed
        val vy = dirY * power * maxSpeed
        if (vx == 0f && vy == 0f) return 0
        val n = minOf(count, out.size / 2)
        for (k in 0 until n) {
            val t = stepSec * (k + 1)
            out[2 * k] = originX + vx * t
            out[2 * k + 1] = originY + vy * t + 0.5f * gravity * t * t
        }
        return n
    }
}
