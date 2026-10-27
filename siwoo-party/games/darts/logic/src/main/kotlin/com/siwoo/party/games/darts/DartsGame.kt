package com.siwoo.party.games.darts

import com.siwoo.party.common.Ranking

/** 경기 방식 */
enum class DartsMode(val label: String) {
    /** 라운드 점수제: 8라운드 × 3발 합계가 높은 순 (기본) */
    ROUNDS("라운드 점수제"),
    /** 301: 301 에서 빼서 정확히 0 을 먼저 만든 순 (더블 아웃 없음, 0 밑으로 가면 버스트) */
    X01("301"),
}

/** 진행 단계 */
enum class Phase {
    /** 다트를 던질 차례 */
    THROW,
    /** 이번 차례 다트를 다 던짐 (3발·버스트·완료) — [DartsGame.endTurn] 을 기다림 */
    TURN_OVER,
    /** 경기 끝 */
    OVER,
}

/** 다트 한 발의 결과 */
class ThrowResult(
    val player: Int,
    /** 이번 차례 몇 번째 다트 (0..2) */
    val dart: Int,
    val hit: Hit,
    /** 점수에 실제로 들어간 값 (버스트면 0) */
    val counted: Int,
    /** 301: 0 밑으로 내려가 버스트 (이번 차례 점수 원래대로, 차례 끝) */
    val bust: Boolean,
    /** 301: 정확히 0 → 완료 */
    val finished: Boolean,
    /** 이 다트로 차례가 끝났는지 (3발째·버스트·완료) */
    val turnOver: Boolean,
    /** 이번 차례 합계 (버스트면 0) */
    val turnTotal: Int,
)

/**
 * 다트 규칙 (순수 Kotlin). 화면은 조준점 좌표(mm)로 [throwDart] → 결과를 보여 주고 → 회수 연출 뒤 [endTurn].
 *
 * - 차례마다 3발. 차례 순서는 [first] 부터 번호 순으로 돈다.
 * - [DartsMode.ROUNDS]: 모든 사람이 [rounds] 번씩 던지면 끝. 합계가 높은 순 (같으면 같은 순위).
 * - [DartsMode.X01]: [start] 에서 뺀다. 0 밑으로 내려가면 버스트 → 그 차례 다트 점수 모두 취소(차례 시작 점수로 되돌림)하고 차례 끝.
 *   정확히 0 → 완료 (끝낸 순서대로 1등, 2등 …), 끝낸 사람은 건너뛴다. 남은 사람이 1명 이하가 되면 끝 (남은 사람은 꼴찌).
 *   남은 사람들이 모두 [maxRounds] 번 던져도 못 끝내면 끝 → 끝낸 사람 다음 순위를 남은 점수가 적은 순으로 (같으면 같은 순위).
 */
class DartsGame(
    val players: Int,
    val mode: DartsMode = DartsMode.ROUNDS,
    val first: Int = 0,
    val rounds: Int = GameConfig.ROUNDS,
    val start: Int = GameConfig.X01_START,
    val maxRounds: Int = GameConfig.X01_MAX_ROUNDS,
) {
    init {
        require(players in 1..GameConfig.MAX_PLAYERS)
        require(first in 0 until players)
    }

    /** 라운드제 합계 점수 */
    val scores = IntArray(players)
    /** 301 남은 점수 */
    val remaining = IntArray(players) { start }
    /** 지금까지 마친 차례 수 */
    val turnsTaken = IntArray(players)
    /** 301 완료 순서 (0 = 1등으로 끝냄, -1 = 아직) */
    val finishOrder = IntArray(players) { -1 }
    var finishedCount = 0
        private set

    var current = first
        private set
    var phase = Phase.THROW
        private set

    /** 이번 차례 던진 다트 수 */
    var dartInTurn = 0
        private set
    /** 이번 차례 합계 (버스트면 0) */
    var turnTotal = 0
        private set
    /** 이번 차례 다트 판정 (dartInTurn 개까지 유효) */
    val turnHits = arrayOfNulls<Hit>(GameConfig.DARTS_PER_TURN)
    /** 이번 차례 꽂힌 자리 (mm) */
    val turnX = FloatArray(GameConfig.DARTS_PER_TURN)
    val turnY = FloatArray(GameConfig.DARTS_PER_TURN)
    /** 이번 차례가 버스트로 끝났는지 */
    var turnBust = false
        private set
    /** 301 차례 시작 때 남은 점수 (버스트하면 이 값으로 돌아감) */
    var turnStartRemaining = start
        private set
    /** 최대 라운드에 걸려서 끝났는지 (301) */
    var endedByRoundLimit = false
        private set

    /** 지금 몇 라운드째 (1부터) — 지금 차례 사람 기준 */
    val round: Int get() = minOf(turnsTaken[current] + 1, totalRounds)

    /** 이 방식의 라운드 수 (라운드제 8, 301 최대 15) */
    val totalRounds: Int get() = if (mode == DartsMode.ROUNDS) rounds else maxRounds

    val over: Boolean get() = phase == Phase.OVER

    fun finished(p: Int): Boolean = finishOrder[p] >= 0

    /** 화면에 보일 점수 (라운드제 합계 / 301 남은 점수) */
    fun shownScore(p: Int): Int = if (mode == DartsMode.ROUNDS) scores[p] else remaining[p]

    /**
     * (x, y) mm 에 다트가 꽂힘 → 판정·점수 반영. 던질 단계가 아니면 null.
     */
    fun throwDart(x: Float, y: Float): ThrowResult? {
        if (phase != Phase.THROW) return null
        val p = current
        val d = dartInTurn
        val hit = Board.score(x, y)
        turnHits[d] = hit
        turnX[d] = x
        turnY[d] = y
        dartInTurn = d + 1
        var counted = hit.points
        var bust = false
        var done = false
        if (mode == DartsMode.ROUNDS) {
            scores[p] += counted
            turnTotal += counted
        } else {
            val left = remaining[p] - counted
            when {
                left < 0 -> {
                    // 버스트: 이번 차례 점수 모두 취소
                    bust = true
                    counted = 0
                    remaining[p] = turnStartRemaining
                    turnTotal = 0
                    turnBust = true
                }
                left == 0 -> {
                    remaining[p] = 0
                    turnTotal += counted
                    done = true
                    finishOrder[p] = finishedCount
                    finishedCount++
                }
                else -> {
                    remaining[p] = left
                    turnTotal += counted
                }
            }
        }
        val turnOver = bust || done || dartInTurn >= GameConfig.DARTS_PER_TURN
        if (turnOver) phase = Phase.TURN_OVER
        return ThrowResult(p, d, hit, counted, bust, done, turnOver, turnTotal)
    }

    /**
     * 차례 마침 → 다음 사람 (또는 경기 끝). 경기가 끝났으면 true.
     */
    fun endTurn(): Boolean {
        if (phase != Phase.TURN_OVER) return phase == Phase.OVER
        turnsTaken[current]++
        if (checkOver()) {
            phase = Phase.OVER
            return true
        }
        // 다음 사람 (301 에서 끝낸 사람은 건너뜀)
        var n = current
        do {
            n = (n + 1) % players
        } while (finished(n))
        current = n
        beginTurn()
        return false
    }

    private fun beginTurn() {
        phase = Phase.THROW
        dartInTurn = 0
        turnTotal = 0
        turnBust = false
        for (i in turnHits.indices) turnHits[i] = null
        turnStartRemaining = remaining[current]
    }

    private fun checkOver(): Boolean {
        if (mode == DartsMode.ROUNDS) {
            for (p in 0 until players) if (turnsTaken[p] < rounds) return false
            return true
        }
        val left = players - finishedCount
        if (left <= 0) return true
        if (players > 1 && left <= 1) return true
        for (p in 0 until players) if (!finished(p) && turnsTaken[p] < maxRounds) return false
        endedByRoundLimit = true
        return true
    }

    /** players 순서의 순위 (1부터, 동점 같은 순위) */
    fun ranks(): IntArray = when (mode) {
        DartsMode.ROUNDS -> Ranking.competition(scores.map { it.toLong() })
        DartsMode.X01 -> x01Ranks(remaining, finishOrder)
    }

    companion object {
        /**
         * 301 순위: 끝낸 사람은 끝낸 순서 (1, 2, …), 나머지는 그 다음부터 남은 점수가 적은 순 (같으면 같은 순위).
         */
        fun x01Ranks(remaining: IntArray, finishOrder: IntArray): IntArray {
            val n = remaining.size
            val done = finishOrder.count { it >= 0 }
            return IntArray(n) { i ->
                if (finishOrder[i] >= 0) finishOrder[i] + 1
                else done + 1 + (0 until n).count { j -> finishOrder[j] < 0 && remaining[j] < remaining[i] }
            }
        }
    }
}
