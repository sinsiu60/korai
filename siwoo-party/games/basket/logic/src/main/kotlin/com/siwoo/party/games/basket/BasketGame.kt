package com.siwoo.party.games.basket

import com.siwoo.party.common.Ranking
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.tan

/** 진행 단계: 조준(스와이프 기다림) → 공이 나는 중 → 결과 보여 주는 중 → … → 끝 */
enum class Phase { AIM, FLYING, SETTLED, OVER }

/** 스와이프 → 슛 값 (순수 계산) */
object Aim {
    /** 스와이프 각도(0° = 오른쪽, 90° = 위) → 발사 각도 (LAUNCH_MIN..MAX 로 자름) */
    fun launchAngle(swipeAngleDeg: Float): Float =
        (GameConfig.LAUNCH_BASE_DEG + (swipeAngleDeg - GameConfig.SWIPE_CENTER_DEG) * GameConfig.ANGLE_SCALE)
            .coerceIn(GameConfig.LAUNCH_MIN_DEG, GameConfig.LAUNCH_MAX_DEG)

    /**
     * 놓는 곳에서 (dx, dy) 떨어진 점을 각도 angleDeg 로 지나가는 빠르기 (m/초).
     * v² = g·dx² / (2·cos²θ·(dx·tanθ − dy)). 너무 낮은 각도라 닿을 수 없으면 큰 값.
     */
    fun speedThrough(dx: Float, dy: Float, angleDeg: Float): Float {
        val a = Math.toRadians(angleDeg.toDouble())
        val c = cos(a)
        val rise = dx * tan(a) - dy
        if (rise <= 1e-4 || c <= 1e-4) return MAX_SPEED
        val v2 = GameConfig.GRAVITY * dx * dx / (2.0 * c * c * rise)
        return sqrt(v2).toFloat().coerceAtMost(MAX_SPEED)
    }

    /** 거리 distance 에서 각도 angleDeg 로 림 가운데(조금 뒤)를 지나는 빠르기 (= 세기 SWEET_POWER) */
    fun idealSpeed(distance: Float, angleDeg: Float): Float =
        speedThrough(distance + GameConfig.AIM_BEYOND, GameConfig.RIM_Y - GameConfig.RELEASE_Y, angleDeg)

    /** 세기 0..1 → 빠르기 (거리·각도에 맞춰 보정) */
    fun speed(distance: Float, angleDeg: Float, power: Float): Float =
        idealSpeed(distance, angleDeg) * (1f + (power.coerceIn(0f, 1f) - GameConfig.SWEET_POWER) * GameConfig.SPEED_SPREAD)

    /** 빠르기를 주는 세기 (speed 의 역계산, 테스트·안내용) */
    fun powerFor(distance: Float, angleDeg: Float, speed: Float): Float =
        GameConfig.SWEET_POWER + (speed / idealSpeed(distance, angleDeg) - 1f) / GameConfig.SPEED_SPREAD

    private const val MAX_SPEED = 30f
}

/**
 * 점수 규칙 (순수 계산).
 * - 기본: 3점 라인(THREE_PT_LINE)보다 먼 자리면 3점, 아니면 2점.
 * - 연속 성공(그 사람의 슛만 셈)이 FIRE_STREAK(2) 이상이면 "불꽃" — 불꽃 상태에서 넣은 골은 +FIRE_BONUS(1).
 *   즉 1·2번째 연속 골은 기본 점수, 3번째부터 골마다 +1. 못 넣으면 연속 0 → 불꽃 꺼짐.
 */
object Scoring {
    fun basePoints(distance: Float): Int = if (distance > GameConfig.THREE_PT_LINE) GameConfig.POINTS_3 else GameConfig.POINTS_2

    fun isThree(distance: Float): Boolean = distance > GameConfig.THREE_PT_LINE

    fun onFire(streak: Int): Boolean = streak >= GameConfig.FIRE_STREAK

    /** 연속 streakBefore 인 상태에서 넣으면 받는 보너스 */
    fun bonus(streakBefore: Int): Int = if (onFire(streakBefore)) GameConfig.FIRE_BONUS else 0

    /** 이번 슛 점수 (못 넣으면 0) */
    fun points(distance: Float, streakBefore: Int, made: Boolean): Int =
        if (made) basePoints(distance) + bonus(streakBefore) else 0

    fun nextStreak(streakBefore: Int, made: Boolean): Int = if (made) streakBefore + 1 else 0
}

/** 마지막 슛 결과 (다시 쓰는 가변 객체) */
class ShotOutcome {
    var player = 0
    var round = 0
    var distance = 0f
    var made = false
    /** 림·보드에 안 닿고 들어감 */
    var clean = false
    /** 보드 맞고 들어감 */
    var bank = false
    /** 아무 데도 안 닿고 빗나감 */
    var airball = false
    var base = 0
    var bonus = 0
    var points = 0
    var streakBefore = 0
    var streakAfter = 0
    /** 이 슛으로 불꽃이 막 붙음 */
    var fireStarted = false
    /** 불꽃이 꺼짐 (불꽃 상태에서 놓침) */
    var fireLost = false
}

/**
 * 농구 슛 한 판 (2~4인, 차례대로). 한 바퀴마다 모두 같은 자리에서 한 번씩 던지고(1번 사람부터), 다음 바퀴는 더 먼 자리.
 * shots 바퀴가 끝나면 합계 점수 순위. 물리는 Court(고정 1/60초 단계), 시간은 밖에서 advance(ms) 로 넣는다.
 */
class BasketGame(val players: Int, val shots: Int = GameConfig.SHOTS) {
    init {
        require(players in 1..GameConfig.MAX_PLAYERS) { "players" }
        require(shots in 1..GameConfig.DISTANCES.size) { "shots" }
    }

    val court = Court()
    val scores = IntArray(players)
    val streaks = IntArray(players)
    val makes = IntArray(players)
    /** 사람별·슛별 결과: 0 = 아직, 1 = 놓침, 2 = 골 (점수판·표시용) */
    val marks = Array(players) { IntArray(shots) }

    var current = 0
        private set
    /** 지금 바퀴 (= 슛 자리, 0부터) */
    var round = 0
        private set
    var phase = Phase.AIM
        private set
    /** 지금까지 던진 수 (모두 합쳐) */
    var throws = 0
        private set

    val outcome = ShotOutcome()

    init {
        readyBall()
    }

    /** 지금 슛 자리 거리 */
    val distance: Float get() = distanceAt(round)

    /** 바퀴 r 의 거리 (shots 가 10보다 적으면 10자리 중 고르게 골라 씀) */
    fun distanceAt(r: Int): Float {
        val n = GameConfig.DISTANCES.size
        val idx = if (shots <= 1) 0 else (r.coerceIn(0, shots - 1) * (n - 1)) / (shots - 1)
        return GameConfig.DISTANCES[idx]
    }

    /** 공을 놓는 곳 */
    val releaseX: Float get() = -distance
    val releaseY: Float get() = GameConfig.RELEASE_Y

    fun onFire(p: Int): Boolean = Scoring.onFire(streaks[p])

    /** 스와이프 결과로 슛 (swipeAngleDeg: 0 = 오른쪽, 90 = 위 / power 0..1) */
    fun shootSwipe(swipeAngleDeg: Float, power: Float): Boolean {
        val a = Aim.launchAngle(swipeAngleDeg)
        return launch(a, Aim.speed(distance, a, power))
    }

    /** 직접 값으로 슛 */
    fun launch(angleDeg: Float, speed: Float): Boolean {
        if (phase != Phase.AIM) return false
        if (!court.launch(releaseX, releaseY, speed, angleDeg)) return false
        throws++
        phase = Phase.FLYING
        return true
    }

    /** dtMs 만큼 진행. 아직 나는 중이면 true (끝나면 점수를 매기고 SETTLED) */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.FLYING) return false
        if (court.advance(dtMs)) return true
        settle(court.made, court.clean, court.made && court.boardHits > 0, court.airball)
        return false
    }

    /** 테스트·건너뛰기용: 물리 없이 넣었다/놓쳤다로 처리 */
    fun forceShot(made: Boolean): Boolean {
        if (phase != Phase.AIM) return false
        throws++
        phase = Phase.FLYING
        settle(made, made, false, !made)
        return true
    }

    private fun settle(made: Boolean, clean: Boolean, bank: Boolean, airball: Boolean) {
        val p = current
        val o = outcome
        val before = streaks[p]
        o.player = p
        o.round = round
        o.distance = distance
        o.made = made
        o.clean = clean
        o.bank = bank
        o.airball = airball
        o.base = if (made) Scoring.basePoints(distance) else 0
        o.bonus = if (made) Scoring.bonus(before) else 0
        o.points = Scoring.points(distance, before, made)
        o.streakBefore = before
        o.streakAfter = Scoring.nextStreak(before, made)
        o.fireStarted = !Scoring.onFire(before) && Scoring.onFire(o.streakAfter)
        o.fireLost = Scoring.onFire(before) && !made
        streaks[p] = o.streakAfter
        scores[p] += o.points
        if (made) makes[p]++
        marks[p][round] = if (made) 2 else 1
        phase = Phase.SETTLED
    }

    /**
     * 결과를 보여 준 뒤 다음 사람으로 (마지막 사람 다음은 다음 바퀴 = 더 먼 자리).
     * 반환: 새 바퀴가 시작됐으면 true. 모두 끝났으면 phase = OVER.
     */
    fun next(): Boolean {
        if (phase != Phase.SETTLED) return false
        var newRound = false
        current++
        if (current >= players) {
            current = 0
            round++
            newRound = true
            if (round >= shots) {
                round = shots - 1
                phase = Phase.OVER
                return true
            }
        }
        phase = Phase.AIM
        readyBall()
        return newRound
    }

    /** 공을 지금 슛 자리 손 위에 */
    private fun readyBall() {
        court.rest(releaseX, releaseY)
    }

    fun total(p: Int): Int = scores[p]

    /** 점수 순 순위 (동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(List(players) { scores[it].toLong() })
}
