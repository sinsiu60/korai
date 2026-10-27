package com.siwoo.party.games.archery

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 10점 과녁 점수 (고리 단위, 가운데 (0,0)).
 * 화살 가운데까지 거리 d 에서 화살 반지름(ARROW_R)을 뺀 값 e 로 잰다 → 화살이 선에 걸치면 안쪽 점수.
 *  - e ≤ X_R → X (10점, 따로 셈)
 *  - e ≤ k (k = 1..10) 인 가장 작은 k → 11 − k 점 (e ≤ 1 → 10점, 1 < e ≤ 2 → 9점 …, 9 < e ≤ 10 → 1점)
 *  - e > 10 → 0점 (M, 빗나감)
 */
object Target {
    const val MISS = 0

    fun distance(x: Float, y: Float): Float = sqrt(x * x + y * y)

    /** 화살 가운데 거리 → 점수 0..10 */
    fun score(d: Float): Int {
        // d − ARROW_R ≤ k  ⇔  d ≤ k + ARROW_R (뺄셈 반올림 오차 없이 경계를 정확히)
        for (k in 1..GameConfig.RINGS) {
            if (d <= k.toFloat() + GameConfig.ARROW_R) return GameConfig.RINGS + 1 - k
        }
        return MISS
    }

    /** X 링인지 (10점 안쪽 가운데) */
    fun isX(d: Float): Boolean = d <= GameConfig.X_R + GameConfig.ARROW_R

    fun scoreAt(x: Float, y: Float): Int = score(distance(x, y))
    fun isXAt(x: Float, y: Float): Boolean = isX(distance(x, y))

    /** 점수 이름: X, 10..1, M */
    fun label(score: Int, x: Boolean): String = LABELS[index(score, x)]

    /** 점수 → 0..11 (0 = M, 1..10, 11 = X) — 화면에서 미리 만든 글자를 고를 때 */
    fun index(score: Int, x: Boolean): Int = if (x && score == GameConfig.RINGS) 11 else score.coerceIn(0, 10)

    /** 점수 고리의 색 이름 (0 흰색 1·2 / 1 검정 3·4 / 2 파랑 5·6 / 3 빨강 7·8 / 4 금색 9·10, 빗나감 −1) */
    fun colorBand(score: Int): Int = if (score <= 0) -1 else (score - 1) / 2

    val LABELS = arrayOf("M", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "X")
}

/**
 * 바람: 방향(도, 0 = 오른쪽 → , 90 = 아래, 180 = 왼쪽, 270 = 위)과 세기(0.1 m/s 단위).
 * 화살은 바람이 부는 쪽으로 세기 × WIND_DRIFT 고리만큼 밀린다.
 */
data class Wind(val dirDeg: Int, val speed10: Int) {
    val speed: Float get() = speed10 / 10f
    private val rad = Math.toRadians(dirDeg.toDouble())
    /** 밀리는 거리 (고리 단위) */
    val driftX: Float = (cos(rad) * speed10 / 10.0 * GameConfig.WIND_DRIFT).toFloat()
    val driftY: Float = (sin(rad) * speed10 / 10.0 * GameConfig.WIND_DRIFT).toFloat()

    /** "2.4" 처럼 m/s 숫자 */
    val speedText: String get() = (speed10 / 10).toString() + "." + (speed10 % 10)

    companion object {
        val CALM = Wind(0, 0)

        /** 무작위 바람 (방향 WIND_STEP_DEG 간격, 세기 WIND_MIN10..WIND_MAX10) */
        fun random(r: Random): Wind {
            val steps = 360 / GameConfig.WIND_STEP_DEG
            val dir = r.nextInt(steps) * GameConfig.WIND_STEP_DEG
            val sp = GameConfig.WIND_MIN10 + r.nextInt(GameConfig.WIND_MAX10 - GameConfig.WIND_MIN10 + 1)
            return Wind(dir, sp)
        }
    }
}

/**
 * 쏜 화살 한 발.
 * @param aimX aimY 놓는 순간의 조준점 (흔들림 포함), @param x y 꽂힌 자리 (바람·흩어짐 포함)
 * @param heldMs 숨 참은 시간, @param swayScale 놓는 순간의 흔들림 배율
 */
data class Shot(
    val player: Int,
    val arrow: Int,
    val aimX: Float,
    val aimY: Float,
    val x: Float,
    val y: Float,
    val score: Int,
    val isX: Boolean,
    val wind: Wind,
    val heldMs: Long,
    val swayScale: Float,
) {
    val miss: Boolean get() = score == Target.MISS
    val label: String get() = Target.label(score, isX)
}
