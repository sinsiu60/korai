package com.siwoo.party.games.tuho

import com.siwoo.party.common.aim.Gauge
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 던진 결과 */
enum class Outcome(val points: Int, val inside: Boolean) {
    /** 가운데 입구 (2점) */
    MOUTH(GameConfig.MOUTH_POINTS, true),
    /** 가까운 귀 (왼쪽, 3점) */
    EAR_NEAR(GameConfig.EAR_POINTS, true),
    /** 먼 귀 (오른쪽, 3점) */
    EAR_FAR(GameConfig.EAR_POINTS, true),
    /** 입구·귀 테두리에 맞고 튕김 (0점) */
    RIM(0, false),
    /** 항아리 몸통·목·귀 관 옆면에 맞고 튕김 (0점) */
    BODY(0, false),
    /** 바닥에 떨어짐 (0점) */
    FLOOR(0, false);

    /** 튕기는 결과인지 (테두리·몸통) */
    val bounces: Boolean get() = this == RIM || this == BODY
}

/** 구멍 (입구·귀) 하나: 가운데 x, 위 끝 높이, 구멍 반폭, 관 아래 끝 */
class Opening(val outcome: Outcome, val cx: Float, val top: Float, val half: Float, val bottom: Float) {
    /** 관 바깥 반폭 (구멍 + 테두리) */
    val outer: Float get() = half + GameConfig.RIM
}

/**
 * 한 번 던진 화살의 모든 것 (계산 결과 — 화면은 이것만 보고 그린다).
 * 비행: x(t) = RELEASE_X + vx·t, y(t) = RELEASE_Y + vy·t − g·t²/2 (t 초, 0..[t]).
 * 끝([t] 초)에 [outcome] 이 일어난 자리 ([x], [y]), 그때 속도 ([hitVx], [hitVy]).
 * 튕기면(테두리·몸통) 그 자리에서 ([bounceVx], [bounceVy]) 로 다시 날아 [bounceT] 초 뒤 바닥.
 */
class Shot(
    val angleDeg: Float,
    val speed: Float,
    val outcome: Outcome,
    /** 사건 시각 (초, 던진 뒤) */
    val t: Double,
    val x: Double,
    val y: Double,
) {
    val vx: Double = speed * cos(Math.toRadians(angleDeg.toDouble()))
    val vy: Double = speed * sin(Math.toRadians(angleDeg.toDouble()))
    val hitVx: Double get() = vx
    val hitVy: Double = vy - GameConfig.GRAVITY * t
    val points: Int get() = outcome.points

    val bounceVx: Double = if (outcome.bounces) -GameConfig.BOUNCE_X * vx else 0.0
    val bounceVy: Double = if (outcome.bounces) GameConfig.BOUNCE_Y * abs(hitVy) else 0.0
    /** 튕긴 뒤 바닥까지 시간 (초, 튕기지 않으면 0) */
    val bounceT: Double = if (outcome.bounces) TuhoField.fallTime(bounceVy, y) else 0.0

    /** 비행 중 화살 끝 위치 */
    fun xAt(time: Double): Double = GameConfig.RELEASE_X + vx * time
    fun yAt(time: Double): Double = GameConfig.RELEASE_Y + vy * time - GameConfig.GRAVITY * time * time / 2.0
    /** 비행 중 세로 속도 */
    fun vyAt(time: Double): Double = vy - GameConfig.GRAVITY * time

    /** 튕긴 뒤 위치 (bt = 튕긴 뒤 초) */
    fun bounceXAt(bt: Double): Double = x + bounceVx * bt
    fun bounceYAt(bt: Double): Double = y + bounceVy * bt - GameConfig.GRAVITY * bt * bt / 2.0

    /** 처음부터 완전히 멈출 때까지 (초) */
    val totalT: Double get() = t + bounceT
}

/**
 * 투호 마당 (순수 기하). 항아리 모양과 포물선 판정.
 *
 * 판정 규칙 (화살 끝 한 점):
 *  1. 구멍(입구·두 귀)마다 그 위 끝 높이를 **내려가며** 지나는 순간의 x 를 구한다 (식으로 바로).
 *     구멍 안(가운데 ± 반폭, 경계 포함)이면 들어감, 그 바깥 테두리 두께 안이면 테두리(튕김).
 *  2. 몸통(타원)·목·귀 관·팔(직사각형)에 먼저 닿으면 몸통(튕김) — 짧은 간격으로 찾은 뒤 이분법으로 정확한 시각.
 *  3. 바닥(y = 0)에 닿으면 바닥.
 *  가장 먼저 일어난 일이 결과. (구멍은 관의 위 끝이라, 구멍으로 들어간 화살이 관 벽에 "먼저" 닿는 일은 없다)
 */
object TuhoField {

    private const val G = GameConfig.GRAVITY.toDouble()

    /** 입구 + 귀 둘 (판정·그림 순서) */
    val mouth = Opening(Outcome.MOUTH, GameConfig.JAR_X, GameConfig.MOUTH_Y, GameConfig.MOUTH_HALF, GameConfig.NECK_BOTTOM)
    val earNear = Opening(
        Outcome.EAR_NEAR, GameConfig.JAR_X - GameConfig.EAR_DX, GameConfig.EAR_NEAR_Y, GameConfig.EAR_HALF,
        GameConfig.EAR_NEAR_Y - GameConfig.EAR_LEN,
    )
    val earFar = Opening(
        Outcome.EAR_FAR, GameConfig.JAR_X + GameConfig.EAR_DX, GameConfig.EAR_FAR_Y, GameConfig.EAR_HALF,
        GameConfig.EAR_FAR_Y - GameConfig.EAR_LEN,
    )
    val openings = listOf(mouth, earNear, earFar)

    /** 결과 → 구멍 번호 (0 입구, 1 가까운 귀, 2 먼 귀, 아니면 −1) */
    fun openingIndex(o: Outcome): Int = when (o) {
        Outcome.MOUTH -> 0
        Outcome.EAR_NEAR -> 1
        Outcome.EAR_FAR -> 2
        else -> -1
    }

    // ── 게이지 → 각도·힘 ──

    /** 각도 게이지 값 0..1 → 각도 (도) */
    fun angleFor(v: Float): Float = Gauge.map(v.coerceIn(0f, 1f), GameConfig.ANGLE_MIN, GameConfig.ANGLE_MAX)

    /** 힘 게이지 값 0..1 → 처음 속도 (m/s) */
    fun speedFor(v: Float): Float = Gauge.map(v.coerceIn(0f, 1f), GameConfig.SPEED_MIN, GameConfig.SPEED_MAX)

    // ── 포물선 (식) ──

    /** 세로 속도 vy 로 높이 h 에서 출발해 바닥(y = 0)까지 시간 */
    fun fallTime(vy: Double, h: Double): Double = (vy + sqrt(vy * vy + 2.0 * G * maxOf(h, 0.0))) / G

    /** 꼭짓점 시각·x·y */
    fun apexT(angleDeg: Float, speed: Float): Double = vyOf(angleDeg, speed) / G
    fun apexX(angleDeg: Float, speed: Float): Double = GameConfig.RELEASE_X + vxOf(angleDeg, speed) * apexT(angleDeg, speed)
    fun apexY(angleDeg: Float, speed: Float): Double {
        val vy = vyOf(angleDeg, speed)
        return GameConfig.RELEASE_Y + vy * vy / (2.0 * G)
    }

    /** 아무것도 안 맞았을 때 바닥에 닿는 x */
    fun floorX(angleDeg: Float, speed: Float): Double =
        GameConfig.RELEASE_X + vxOf(angleDeg, speed) * fallTime(vyOf(angleDeg, speed), GameConfig.RELEASE_Y.toDouble())

    /** 높이 h 를 내려가며 지나는 시각 (그 높이까지 못 올라가면 −1) */
    fun descendT(angleDeg: Float, speed: Float, h: Double): Double {
        val vy = vyOf(angleDeg, speed)
        val disc = vy * vy + 2.0 * G * (GameConfig.RELEASE_Y - h)
        if (disc < 0.0) return -1.0
        return (vy + sqrt(disc)) / G
    }

    /** 높이 h 를 내려가며 지나는 x (못 올라가면 NaN) */
    fun descendX(angleDeg: Float, speed: Float, h: Double): Double {
        val t = descendT(angleDeg, speed, h)
        if (t < 0.0) return Double.NaN
        return GameConfig.RELEASE_X + vxOf(angleDeg, speed) * t
    }

    /**
     * 각도 angleDeg 로 점 (x, y) 를 지나게 하는 속도 (못 지나면 NaN). 테스트·검증용.
     * y − y0 = tanθ·dx − g·dx² / (2·v²·cos²θ)
     */
    fun speedThrough(angleDeg: Float, x: Double, y: Double): Double {
        val th = Math.toRadians(angleDeg.toDouble())
        val dx = x - GameConfig.RELEASE_X
        val c = cos(th)
        val den = 2.0 * c * c * (Math.tan(th) * dx - (y - GameConfig.RELEASE_Y))
        if (dx <= 0.0 || den <= 0.0) return Double.NaN
        return sqrt(G * dx * dx / den)
    }

    fun vxOf(angleDeg: Float, speed: Float): Double = speed * cos(Math.toRadians(angleDeg.toDouble()))
    fun vyOf(angleDeg: Float, speed: Float): Double = speed * sin(Math.toRadians(angleDeg.toDouble()))

    // ── 단단한 부분 (몸통·목·귀 관·팔) ──

    /** 점이 항아리 단단한 부분 안인지 (관의 위 끝 높이는 포함 안 함 — 위 끝은 구멍·테두리 판정이 맡음) */
    fun insideSolid(x: Double, y: Double): Boolean {
        // 몸통 타원
        val ex = (x - GameConfig.JAR_X) / GameConfig.BODY_RX
        val ey = (y - GameConfig.BODY_CY) / GameConfig.BODY_RY
        if (ex * ex + ey * ey <= 1.0) return true
        for (i in 0 until openings.size) {
            val o = openings[i]
            if (y < o.top && y >= o.bottom && abs(x - o.cx) <= o.outer) return true
        }
        // 귀와 목을 잇는 팔
        if (inArm(earNear, x, y) || inArm(earFar, x, y)) return true
        return false
    }

    private fun inArm(ear: Opening, x: Double, y: Double): Boolean {
        val top = ear.top - GameConfig.ARM_TOP
        val bottom = ear.top - GameConfig.ARM_BOTTOM
        if (y > top || y < bottom) return false
        val neckEdge = mouth.cx + (if (ear.cx < mouth.cx) -mouth.outer else mouth.outer)
        val earEdge = ear.cx + (if (ear.cx < mouth.cx) ear.outer else -ear.outer)
        return x >= minOf(neckEdge, earEdge) && x <= maxOf(neckEdge, earEdge)
    }

    // ── 판정 ──

    /** 게이지 값 둘로 던지기 */
    fun judgeGauge(angleValue: Float, powerValue: Float): Shot = judge(angleFor(angleValue), speedFor(powerValue))

    /** 각도·속도로 던졌을 때 결과 (늘 같은 결과 — 바람·난수 없음) */
    fun judge(angleDeg: Float, speed: Float): Shot {
        val vx = vxOf(angleDeg, speed)
        val vy = vyOf(angleDeg, speed)
        val y0 = GameConfig.RELEASE_Y.toDouble()
        // 3. 바닥
        var bestT = fallTime(vy, y0)
        var best = Outcome.FLOOR
        var bestX = GameConfig.RELEASE_X + vx * bestT
        var bestY = 0.0
        // 1. 구멍 위 끝을 내려가며 지남
        for (i in 0 until openings.size) {
            val o = openings[i]
            val t = descendT(angleDeg, speed, o.top.toDouble())
            if (t < 0.0 || t >= bestT) continue
            val x = GameConfig.RELEASE_X + vx * t
            val d = abs(x - o.cx)
            val kind = when {
                d <= o.half -> o.outcome
                d <= o.outer -> Outcome.RIM
                else -> continue
            }
            bestT = t
            best = kind
            bestX = x
            bestY = o.top.toDouble()
        }
        // 2. 단단한 부분 (먼저 닿았으면 그것이 결과)
        val dt = GameConfig.SAMPLE_DT.toDouble()
        var prev = 0.0
        var t = dt
        while (t < bestT) {
            val x = GameConfig.RELEASE_X + vx * t
            val y = y0 + vy * t - G * t * t / 2.0
            if (insideSolid(x, y)) {
                // 이분법으로 닿는 순간
                var lo = prev
                var hi = t
                repeat(40) {
                    val mid = (lo + hi) / 2.0
                    val mx = GameConfig.RELEASE_X + vx * mid
                    val my = y0 + vy * mid - G * mid * mid / 2.0
                    if (insideSolid(mx, my)) hi = mid else lo = mid
                }
                if (hi < bestT) {
                    bestT = hi
                    best = Outcome.BODY
                    bestX = GameConfig.RELEASE_X + vx * hi
                    bestY = y0 + vy * hi - G * hi * hi / 2.0
                }
                break
            }
            prev = t
            t += dt
        }
        return Shot(angleDeg, speed, best, bestT, bestX, bestY)
    }
}
