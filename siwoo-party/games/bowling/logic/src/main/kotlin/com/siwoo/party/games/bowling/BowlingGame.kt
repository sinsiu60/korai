package com.siwoo.party.games.bowling

import com.siwoo.party.common.Ranking

/** 진행 단계: 조준(스와이프 기다림) → 굴러가는 중 → 멈춤(결과 보여 주는 중) → … → 끝 */
enum class Phase { AIM, ROLLING, SETTLED, OVER }

/** 한 투구 결과 종류 (배너) */
enum class RollMark { STRIKE, SPARE, GUTTER, MISS, PINS }

/** 마지막 투구 결과 (다시 쓰는 가변 객체) */
class RollOutcome {
    var player = 0
    /** 굴린 프레임 · 그 프레임 안 투구 번호 (0부터) */
    var frame = 0
    var rollInFrame = 0
    /** 쓰러뜨린 핀 수 · 굴리기 전 서 있던 핀 수 */
    var knocked = 0
    var standingBefore = 0
    var mark = RollMark.PINS
    /** 이 투구로 그 사람의 프레임이 끝났는지 */
    var frameEnded = false
}

/** 스와이프 → 공 굴리기 값 (순수 계산) */
object Aim {
    /** 스와이프가 앞 방향에서 벗어난 각도(+ = 오른쪽 = 화면 아래) → 공 진행 각도 (±MAX_ANGLE_DEG 로 자름) */
    fun angleDeg(deviationDeg: Float): Float =
        (deviationDeg * GameConfig.AIM_SCALE).coerceIn(-GameConfig.MAX_ANGLE_DEG, GameConfig.MAX_ANGLE_DEG)

    /** 세기 0..1 → 공 빠르기 */
    fun speed(power: Float): Float =
        GameConfig.MIN_SPEED + (GameConfig.MAX_SPEED - GameConfig.MIN_SPEED) * power.coerceIn(0f, 1f)

    /** 휘어짐 −1..1 → 훅 */
    fun hook(spin: Float): Float = spin.coerceIn(-1f, 1f)
}

/**
 * 볼링 한 판 (2~4인, 차례대로). 한 사람이 한 프레임을 다 굴리면 다음 사람 — 마지막 사람의 마지막 프레임이 끝나면 끝.
 * 물리는 Lane(고정 1/60초 단계). 시간은 밖에서 advance(ms) 로 넣는다.
 */
class BowlingGame(val players: Int, val frames: Int) {
    init {
        require(players in 1..GameConfig.MAX_PLAYERS) { "players" }
        require(frames >= 1) { "frames" }
    }

    val cards = Array(players) { ScoreCard(frames) }
    val lane = Lane()

    /** 지금 굴리는 사람 */
    var current = 0
        private set
    var phase = Phase.AIM
        private set
    /** 지금까지 굴린 수 (모두 합쳐) */
    var throws = 0
        private set

    val outcome = RollOutcome()

    val card: ScoreCard get() = cards[current]

    /** 스와이프 결과로 굴리기 (startY = 레인 높이) */
    fun throwBall(startY: Float, deviationDeg: Float, power: Float, spin: Float): Boolean =
        launch(startY, Aim.angleDeg(deviationDeg), Aim.speed(power), Aim.hook(spin))

    /** 직접 값으로 굴리기 */
    fun launch(startY: Float, angleDeg: Float, speed: Float, hook: Float): Boolean {
        if (phase != Phase.AIM) return false
        outcome.standingBefore = lane.standingCount
        if (!lane.launch(startY, angleDeg, speed, hook)) return false
        throws++
        phase = Phase.ROLLING
        return true
    }

    /** dtMs 만큼 진행. 아직 굴러가는 중이면 true (멈추면 점수를 매기고 SETTLED) */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.ROLLING) return false
        if (lane.advance(dtMs)) return true
        settle()
        return false
    }

    /** 테스트·건너뛰기용: 물리 없이 서 있는 핀 n 개를 쓰러뜨린 것으로 굴림 처리 */
    fun forceRoll(knocked: Int): Boolean {
        if (phase != Phase.AIM) return false
        outcome.standingBefore = lane.standingCount
        lane.forceKnock(knocked.coerceIn(0, outcome.standingBefore))
        throws++
        phase = Phase.ROLLING
        settle()
        return true
    }

    private fun settle() {
        val c = cards[current]
        val o = outcome
        o.player = current
        o.frame = c.frame
        o.rollInFrame = c.rollInFrame
        o.knocked = lane.knockedThisRoll.coerceAtMost(c.standing)
        val before = c.standing
        o.standingBefore = before
        o.mark = when {
            c.freshRack && o.knocked == GameConfig.PINS -> RollMark.STRIKE
            !c.freshRack && o.knocked == before -> RollMark.SPARE
            o.knocked == 0 && lane.gutterBall -> RollMark.GUTTER
            o.knocked == 0 -> RollMark.MISS
            else -> RollMark.PINS
        }
        val f = c.frame
        c.roll(o.knocked)
        o.frameEnded = c.frame != f
        phase = Phase.SETTLED
    }

    /**
     * 결과를 다 보여 준 뒤 다음 투구 준비: 쓰러진 핀 치우기 → (프레임이 끝났으면 다음 사람) → 새로 세울 차례면 10개 세움.
     * 반환: 사람이 바뀌었으면 true. 모두 끝났으면 phase = OVER.
     */
    fun nextTurn(): Boolean {
        if (phase != Phase.SETTLED) return false
        lane.removeFallen()
        var changed = false
        if (outcome.frameEnded) {
            changed = true
            if (allDone()) {
                phase = Phase.OVER
                return true
            }
            current = (current + 1) % players
            lane.rack()
        } else if (cards[current].freshRack) {
            lane.rack() // 마지막 프레임 스트라이크·스페어 뒤 보너스 투구
        }
        phase = Phase.AIM
        return changed
    }

    fun allDone(): Boolean {
        for (c in cards) if (!c.done) return false
        return true
    }

    /** 사람별 확정 점수 */
    fun total(p: Int): Int = cards[p].total

    /** 점수 순 순위 (동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(List(players) { cards[it].total.toLong() })
}
