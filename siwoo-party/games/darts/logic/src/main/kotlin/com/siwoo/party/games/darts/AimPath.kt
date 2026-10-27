package com.siwoo.party.games.darts

import com.siwoo.party.common.aim.Sway
import com.siwoo.party.common.aim.SwayShape
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 흔들리는 조준점 (공통 [Sway] 가로 8자 + 다트마다 무작위 시작 위치·기울기 + 라운드마다 조금씩 커짐).
 * 좌표는 다트판 mm (가운데 (0, 0)). 같은 seed · 같은 [newDart] 값 · 같은 시각이면 늘 같은 위치 (결정적).
 *
 * 사용: 다트마다 [newDart] (seed 로 만든 난수에서 시작 위치·기울기를 뽑음) → 매 프레임 [point] (다트 시작 뒤 지난 ms).
 * 던질 때 [scatter] 로 작은 빗나감을 더한다.
 */
class AimPath(val seed: Long) {

    private val random = Random(seed)
    val sway = Sway(
        shape = SwayShape.FIGURE8,
        amplitudeX = GameConfig.SWAY_AMP_X,
        amplitudeY = GameConfig.SWAY_AMP_Y,
        periodMs = GameConfig.SWAY_PERIOD_MS,
        jitter = GameConfig.SWAY_JITTER,
        jitterPeriodMs = GameConfig.SWAY_JITTER_PERIOD_MS,
        seed = seed,
    )

    /** 이번 다트 8자 기울기 (도) */
    var rotationDeg = 0f
        private set
    /** 이번 다트 흔들림 배율 (라운드가 갈수록 커짐) */
    var scale = 1f
        private set
    private var cosR = 1f
    private var sinR = 0f
    private val tmp = FloatArray(2)

    /** 새 다트: 시작 위치(위상)·기울기를 뽑고 라운드에 맞는 크기로 */
    fun newDart(round: Int) {
        sway.phaseMs = (random.nextFloat() * GameConfig.SWAY_PERIOD_MS).toLong()
        rotationDeg = (random.nextFloat() * 2f - 1f) * GameConfig.AIM_ROTATE_MAX_DEG
        scale = scaleFor(round)
        val r = Math.toRadians(rotationDeg.toDouble())
        cosR = cos(r).toFloat()
        sinR = sin(r).toFloat()
    }

    /** 다트 시작 뒤 tMs 지난 조준점 (out[0] = x, out[1] = y mm) — 할당 없음 */
    fun point(tMs: Long, out: FloatArray) {
        sway.offset(tMs, scale, tmp)
        out[0] = tmp[0] * cosR - tmp[1] * sinR
        out[1] = tmp[0] * sinR + tmp[1] * cosR
    }

    /** 8자 궤적 미리 보기 (떨림 없이 한 바퀴, n 점 → xs/ys) — 다트 시작 때 한 번 */
    fun trail(xs: FloatArray, ys: FloatArray) {
        val n = xs.size
        val j = sway.jitter
        sway.jitter = 0f
        for (k in 0 until n) {
            val t = GameConfig.SWAY_PERIOD_MS * k / n
            sway.offset(t, scale, tmp)
            xs[k] = tmp[0] * cosR - tmp[1] * sinR
            ys[k] = tmp[0] * sinR + tmp[1] * cosR
        }
        sway.jitter = j
    }

    /** 던질 때 빗나감: 반지름 [GameConfig.SCATTER_MM] 원 안 고르게 (out 에 더함) */
    fun scatter(out: FloatArray) {
        val a = random.nextFloat() * 2.0 * Math.PI
        val r = sqrt(random.nextFloat()) * GameConfig.SCATTER_MM
        out[0] += (r * cos(a)).toFloat()
        out[1] += (r * sin(a)).toFloat()
    }

    companion object {
        /** 라운드 → 흔들림 배율 (1라운드 1배, 라운드마다 +3%, 최대 1.21배) */
        fun scaleFor(round: Int): Float =
            minOf(GameConfig.SWAY_GROWTH_MAX, 1f + GameConfig.SWAY_GROWTH_PER_ROUND * (maxOf(1, round) - 1))
    }
}
