package com.siwoo.party.games.tuho

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.aim.Gauge

/**
 * 진행 단계.
 * WAIT(차례 안내 중) → ANGLE(각도 게이지 왕복, 터치로 멈춤) → POWER(힘 게이지 왕복, 터치로 멈춤)
 * → FLIGHT(포물선 비행) → RESULT(결과 표시) → 다음 차례 WAIT … → OVER
 */
enum class Phase { WAIT, ANGLE, POWER, FLIGHT, RESULT, OVER }

/** 이번 순번에 구멍에 꽂혀 있는 화살 (그림용) */
class StuckArrow(
    val player: Int,
    /** 구멍 번호 (0 입구, 1 가까운 귀, 2 먼 귀) */
    val opening: Int,
    /** 그 구멍의 몇 번째 화살인지 (0부터 — 그릴 때 조금씩 비켜 꽂음) */
    val slot: Int,
    /** 들어갈 때 기울기 (도, 수직 아래 = 0, 오른쪽으로 기울면 +) */
    val tiltDeg: Float,
)

/**
 * 투호 규칙 (순수 Kotlin). 시간(ms)은 밖에서 넣는다 (일시정지 시간이 빠진 게임 시계).
 *
 *  - 차례: 한 사람씩 화살 한 개씩 돌아가며 (던진 순번 k → 사람 k % 인원, 그 사람의 k / 인원 번째 화살).
 *    모두 [GameConfig.ARROWS] 개를 던지면 끝.
 *  - 던지기: 각도 게이지를 멈추고 → 힘 게이지를 멈추면 → [TuhoField.judge] 로 결과가 바로 정해진다 (결정적, 난수 없음).
 *  - 점수: 입구 2점, 귀 3점, 실패 0점. 합계로 경쟁 순위 (동점 같은 순위).
 *  - 꽂힌 화살: 같은 "순번"(모두의 k 번째 화살) 동안 구멍에 남아 있고, 다음 순번이 시작될 때 치운다.
 */
class TuhoGame(val players: Int) {

    init {
        require(players in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    var phase = Phase.WAIT
        private set

    /** 지금까지 던진 순번 (0부터) */
    var throwIndex = 0
        private set

    val totalThrows: Int get() = players * GameConfig.ARROWS

    /** 지금 던질 사람 */
    val current: Int get() = playerOf(throwIndex, players)

    /** 지금 사람의 몇 번째 화살인지 (0부터) = 지금 순번 */
    val arrow: Int get() = arrowOf(throwIndex, players)

    /** 화살마다 점수 (−1 = 아직 안 던짐) */
    val scores = Array(players) { IntArray(GameConfig.ARROWS) { -1 } }
    /** 화살마다 결과 (null = 아직) */
    val outcomes = Array(players) { arrayOfNulls<Outcome>(GameConfig.ARROWS) }
    val totals = IntArray(players)

    /** 각도·힘 왕복 게이지 (공통 Gauge) */
    val angleGauge = Gauge(GameConfig.ANGLE_PERIOD_MS)
    val powerGauge = Gauge(GameConfig.POWER_PERIOD_MS)

    /** 이번 던지기에서 멈춘 게이지 값·각도·속도 */
    var angleValue = 0f
        private set
    var powerValue = 0f
        private set
    val angleDeg: Float get() = TuhoField.angleFor(angleValue)
    val speed: Float get() = TuhoField.speedFor(powerValue)

    /** 지금(또는 마지막) 던진 화살 */
    var shot: Shot? = null
        private set

    /** 이번 순번에 꽂힌 화살들 */
    val stuck = ArrayList<StuckArrow>()

    /** 방금 next() 로 새 순번이 시작됐는지 (꽂힌 화살을 치웠는지) */
    var newRound = true
        private set

    val angleLive: Boolean get() = phase == Phase.ANGLE && angleGauge.running
    val powerLive: Boolean get() = phase == Phase.POWER && powerGauge.running

    /** 차례 안내가 끝나면: 각도 게이지 움직이기 */
    fun beginAim(nowMs: Long): Boolean {
        if (phase != Phase.WAIT) return false
        phase = Phase.ANGLE
        shot = null
        angleValue = 0f
        powerValue = 0f
        angleGauge.start(nowMs)
        powerGauge.reset()
        return true
    }

    /** 각도 멈춤 → 힘 게이지 시작. 멈춘 각도(도), 받을 수 없으면 NaN */
    fun stopAngle(nowMs: Long): Float {
        if (!angleLive) return Float.NaN
        angleValue = angleGauge.stop(nowMs)
        phase = Phase.POWER
        powerGauge.start(nowMs)
        return angleDeg
    }

    /** 힘 멈춤 → 결과 계산, 비행 시작. 받을 수 없으면 null */
    fun stopPower(nowMs: Long): Shot? {
        if (!powerLive) return null
        powerValue = powerGauge.stop(nowMs)
        val s = TuhoField.judge(angleDeg, speed)
        shot = s
        phase = Phase.FLIGHT
        return s
    }

    /** 화살이 결과 자리에 닿음 (비행 연출 끝) → 점수 기록 */
    fun land(): Shot? {
        if (phase != Phase.FLIGHT) return null
        val s = shot ?: return null
        val p = current
        scores[p][arrow] = s.points
        outcomes[p][arrow] = s.outcome
        totals[p] += s.points
        val idx = TuhoField.openingIndex(s.outcome)
        if (idx >= 0) {
            var slot = 0
            for (i in 0 until stuck.size) if (stuck[i].opening == idx) slot++
            stuck.add(StuckArrow(p, idx, slot, tiltOf(s)))
        }
        phase = Phase.RESULT
        return s
    }

    /** 다음 차례로. 더 던질 게 있으면 true (WAIT), 다 던졌으면 false (OVER) */
    fun next(): Boolean {
        if (phase != Phase.RESULT) return phase != Phase.OVER
        throwIndex++
        if (throwIndex >= totalThrows) {
            phase = Phase.OVER
            newRound = false
            return false
        }
        newRound = throwIndex % players == 0
        if (newRound) stuck.clear()
        phase = Phase.WAIT
        return true
    }

    /** 한 구멍에 꽂힌 화살 수 (이번 순번) */
    fun stuckCount(opening: Int): Int {
        var n = 0
        for (i in 0 until stuck.size) if (stuck[i].opening == opening) n++
        return n
    }

    /** 사람마다 들어간 화살 수 (입구·귀) */
    fun hits(player: Int): Int = outcomes[player].count { it != null && it.inside }

    /** 최종 순위 (합계 점수, 경쟁 순위) */
    fun ranks(): IntArray = ranksOf(totals)

    companion object {
        /** 순번 k 를 던지는 사람 (한 개씩 돌아가며) */
        fun playerOf(throwIndex: Int, players: Int): Int = throwIndex % players

        /** 순번 k 가 그 사람의 몇 번째 화살인지 (0부터) */
        fun arrowOf(throwIndex: Int, players: Int): Int = throwIndex / players

        /** 합계 → 경쟁 순위 (높은 점수가 1위, 동점 같은 순위) */
        fun ranksOf(totals: IntArray): IntArray = Ranking.competition(totals.map { it.toLong() }, higherIsBetter = true)

        /** 들어갈 때 기울기: 비행 방향에서 수직 기준, ±25° 안으로 (꽂힌 모양) */
        fun tiltOf(s: Shot): Float {
            val deg = Math.toDegrees(Math.atan2(s.hitVx, -s.hitVy)).toFloat()
            return deg.coerceIn(-25f, 25f)
        }
    }
}
