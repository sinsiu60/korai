package com.siwoo.party.games.ddakji

import com.siwoo.party.common.aim.Gauge
import com.siwoo.party.common.aim.SweetZone
import kotlin.math.pow
import kotlin.random.Random

enum class Phase { POWER, ANGLE, STRIKE, OVER }

/** 한 번 내리친 결과 */
class Strike(val attacker: Int, val power: Float, val angle: Float, val chance: Float, val flipped: Boolean)

/**
 * 딱지치기 규칙 (2인 전용).
 * 각자 딱지 DDAKJI_COUNT 장. 공격자는 힘 게이지 → 각도 게이지를 차례로 멈추고, 두 정확도로 정한 확률만큼 상대 딱지가 뒤집힌다.
 * 뒤집으면 그 딱지를 가져오고 한 번 더 공격, 못 뒤집으면 공격 교대. 딱지를 모두 잃은 사람이 진다.
 * 두 게이지는 공통 왕복 게이지([Gauge], common/aim)이고 최적 구간도 공통 [SweetZone] 으로 계산한다.
 * 시간(게임 시계 ms)과 난수는 밖에서 주입한다. [stopAngle] 로 판정하고, 화면 연출이 끝나면 [resolve] 로 결과를 반영한다.
 */
class DdakjiGame(firstAttacker: Int, private val random: Random) {

    val counts = IntArray(GameConfig.PLAYERS) { GameConfig.DDAKJI_COUNT }
    var attacker = firstAttacker.coerceIn(0, GameConfig.PLAYERS - 1)
        private set
    val defender: Int get() = 1 - attacker
    var phase = Phase.POWER
        private set
    var power = 0f
        private set
    var angle = 0f
        private set
    var last: Strike? = null
        private set
    var winner = -1
        private set
    var strikes = 0
        private set
    var flips = 0
        private set

    /** 힘 게이지 (0 → 1 → 0, 최적 구간 = POWER_ZONE) — 화면도 이 게이지의 구간을 그린다 */
    val powerGauge = Gauge(GameConfig.POWER_PERIOD_MS, zones = listOf(POWER_ZONE))
    /** 각도 게이지 (최적 구간 = ANGLE_ZONE) */
    val angleGauge = Gauge(GameConfig.ANGLE_PERIOD_MS, zones = listOf(ANGLE_ZONE))

    /** 공격 시작: 힘 게이지부터 */
    fun startTurn(nowMs: Long) {
        check(phase != Phase.OVER && phase != Phase.STRIKE) { "지금은 시작 못 함: $phase" }
        phase = Phase.POWER
        powerGauge.start(nowMs)
        angleGauge.reset()
    }

    fun powerAt(nowMs: Long): Float = powerGauge.valueAt(nowMs)
    fun angleAt(nowMs: Long): Float = angleGauge.valueAt(nowMs)

    /** 지금 움직이는 게이지 값 (힘 단계면 힘, 각도 단계면 각도) */
    fun gaugeAt(nowMs: Long): Float = if (phase == Phase.ANGLE) angleAt(nowMs) else powerAt(nowMs)

    /** 힘 게이지 멈춤 → 각도 게이지 시작 */
    fun stopPower(nowMs: Long): Float {
        check(phase == Phase.POWER)
        power = powerGauge.stop(nowMs)
        phase = Phase.ANGLE
        angleGauge.start(nowMs)
        return power
    }

    /** 각도 게이지 멈춤 → 확률 계산 + 뒤집힘 판정 (아직 딱지 수는 그대로) */
    fun stopAngle(nowMs: Long): Strike {
        check(phase == Phase.ANGLE)
        angle = angleGauge.stop(nowMs)
        val p = chance(power, angle)
        val s = Strike(attacker, power, angle, p, random.nextFloat() < p)
        last = s
        strikes++
        phase = Phase.STRIKE
        return s
    }

    /** 연출이 끝난 뒤 결과 반영: 뒤집었으면 딱지를 가져오고 공격 유지, 아니면 공격 교대 */
    fun resolve() {
        check(phase == Phase.STRIKE)
        val s = last ?: return
        if (s.flipped) {
            flips++
            counts[attacker]++
            counts[defender]--
            if (counts[defender] <= 0) {
                winner = attacker
                phase = Phase.OVER
                return
            }
        } else {
            attacker = defender
        }
        phase = Phase.POWER
    }

    /** 순위 (players 순서): 이긴 사람 1, 진 사람 2 */
    fun ranks(): IntArray = IntArray(GameConfig.PLAYERS) { if (winner < 0) 1 else if (it == winner) 1 else 2 }

    companion object {
        /** 힘 최적 구간 */
        val POWER_ZONE = SweetZone(GameConfig.POWER_SWEET, GameConfig.POWER_SWEET_HALF, GameConfig.POWER_FALLOFF)
        /** 각도 최적 구간 */
        val ANGLE_ZONE = SweetZone(GameConfig.ANGLE_SWEET, GameConfig.ANGLE_SWEET_HALF, GameConfig.ANGLE_FALLOFF)

        /** 0 → 1 → 0 왕복 (period 마다 한 번) — 공통 [Gauge.triangle] */
        fun gauge(elapsedMs: Long, periodMs: Long): Float = Gauge.triangle(elapsedMs, periodMs)

        /** 최적 구간 안이면 1, 멀어질수록 줄어 falloff 만큼 벗어나면 0 — 공통 [SweetZone.accuracy] */
        fun accuracy(v: Float, sweet: Float, half: Float, falloff: Float): Float = SweetZone.accuracy(v, sweet, half, falloff)

        fun powerAccuracy(power: Float): Float = POWER_ZONE.accuracy(power)

        fun angleAccuracy(angle: Float): Float = ANGLE_ZONE.accuracy(angle)

        /** 뒤집힐 확률 [CHANCE_MIN, CHANCE_MAX] */
        fun chance(power: Float, angle: Float): Float {
            val q = (powerAccuracy(power) + angleAccuracy(angle)) / 2f
            val p = GameConfig.CHANCE_MIN + (GameConfig.CHANCE_MAX - GameConfig.CHANCE_MIN) * q.toDouble().pow(GameConfig.CHANCE_CURVE.toDouble()).toFloat()
            return p.coerceIn(GameConfig.CHANCE_MIN, GameConfig.CHANCE_MAX)
        }
    }
}
