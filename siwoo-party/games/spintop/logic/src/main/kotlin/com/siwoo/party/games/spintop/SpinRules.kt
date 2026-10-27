package com.siwoo.party.games.spintop

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.aim.SweetZone
import kotlin.math.exp

/** 팽이 대결 규칙 계산 (순수 함수 — 단위 테스트용) */
object SpinRules {

    /** 줄 감기 연타 수 → 회전력 (MIN_POWER..1). 체감: 1 − e^(−연타/τ), 연타는 MASH_MAX_TAPS 까지만 */
    fun spinPower(taps: Int): Float {
        val n = taps.coerceIn(0, GameConfig.MASH_MAX_TAPS)
        val raw = 1f - exp(-n / GameConfig.MASH_TAU)
        return (GameConfig.MIN_POWER + (1f - GameConfig.MIN_POWER) * raw).coerceIn(GameConfig.MIN_POWER, 1f)
    }

    /** 던지기 게이지 최적 구간 (가운데) */
    val sweetZone = SweetZone(GameConfig.GAUGE_CENTER, GameConfig.GAUGE_HALF, GameConfig.GAUGE_FALLOFF)

    /** 게이지 멈춘 값(0..1) → 안정성 (MIN_STABILITY..1). 가운데 구간이면 1, 끝으로 갈수록 줄어듦 */
    fun stability(gaugeValue: Float): Float {
        val acc = sweetZone.accuracy(gaugeValue.coerceIn(0f, 1f))
        return GameConfig.MIN_STABILITY + (1f - GameConfig.MIN_STABILITY) * acc
    }

    /** dtSec 동안의 기본 회전력 감소 */
    fun baseDecay(dtSec: Float): Float = GameConfig.BASE_DECAY * dtSec

    /**
     * 충돌 한 번의 회전력 감소 = 충돌 세기 × k × (1 − 안정성 × STAB_GUARD) × 맞부딪힘 배수.
     * 맞부딪힘 배수 = CLASH_BASE + 상대 회전력 / (내 회전력 + 상대 회전력) — 회전력이 같으면 1, 센 팽이와 부딪히면 더 깎인다.
     * rim = 테두리 벽이면 k × RIM_FACTOR (배수 없음).
     */
    fun collisionLoss(impactSpeed: Float, stability: Float, rim: Boolean = false, own: Float = 1f, other: Float = 1f): Float {
        if (impactSpeed <= 0f) return 0f
        val guard = 1f - stability.coerceIn(0f, 1f) * GameConfig.STAB_GUARD
        if (rim) return impactSpeed * GameConfig.COLLIDE_K * GameConfig.RIM_FACTOR * guard
        val sum = own.coerceAtLeast(0f) + other.coerceAtLeast(0f)
        val clash = if (sum <= 0f) 1f else GameConfig.CLASH_BASE + other.coerceAtLeast(0f) / sum
        return impactSpeed * GameConfig.COLLIDE_K * guard * clash
    }

    /**
     * 판 순위 (경쟁 순위, 1부터, 동점 같은 순위).
     * fallSteps[i] = 쓰러진 단계 (−1 = 아직 도는 중). 아직 도는 팽이가 쓰러진 팽이보다 위,
     * 도는 팽이끼리는 회전력(spins)이 높은 쪽이 위 (같으면 같은 순위), 쓰러진 팽이끼리는 늦게 쓰러질수록 위 (같은 단계면 같은 순위).
     */
    fun roundRanks(fallSteps: LongArray, spins: FloatArray): IntArray {
        val n = fallSteps.size
        val ranks = IntArray(n)
        for (i in 0 until n) {
            var better = 0
            for (j in 0 until n) if (j != i && beats(j, i, fallSteps, spins)) better++
            ranks[i] = better + 1
        }
        return ranks
    }

    /** a 가 b 보다 앞서는지 */
    private fun beats(a: Int, b: Int, fallSteps: LongArray, spins: FloatArray): Boolean {
        val fa = fallSteps[a]
        val fb = fallSteps[b]
        if (fa < 0 && fb < 0) return spins[a] > spins[b]
        if (fa < 0) return true
        if (fb < 0) return false
        return fa > fb
    }

    /** 판 순위 → 판 점수 */
    fun points(ranks: IntArray): IntArray = IntArray(ranks.size) { GameConfig.roundPoints(ranks[it], ranks.size) }

    /** 합계 점수 → 최종 순위 (경쟁 순위, 높을수록 위) */
    fun finalRanks(totals: IntArray): IntArray = Ranking.competition(totals.map { it.toLong() }, higherIsBetter = true)
}
