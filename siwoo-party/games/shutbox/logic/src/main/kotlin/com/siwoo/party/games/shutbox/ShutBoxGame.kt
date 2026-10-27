package com.siwoo.party.games.shutbox

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 지금 해야 할 일 */
enum class Phase {
    /** 주사위를 굴릴 차례 */
    ROLL,
    /** 주사위 합에 맞게 숫자를 고르는 중 */
    SELECT,
    /** 차례가 끝남 (화면 연출 뒤 advance()) */
    TURN_OVER,
    /** 3라운드 모두 끝 */
    GAME_OVER,
}

/** 차례가 끝난 이유 */
enum class TurnEndReason {
    /** 굴린 합을 만들 조합이 없음 → 남은 숫자 합이 점수 */
    NO_MOVE,
    /** 숫자를 모두 닫음 → 0점 */
    PERFECT,
}

/** 끝난 차례 하나의 기록 */
class TurnEnd(val player: Int, val round: Int, val score: Int, val reason: TurnEndReason) {
    val perfect: Boolean get() = reason == TurnEndReason.PERFECT
}

/** 굴림 결과 */
class RollResult(val dice: IntArray, val total: Int, val hasMove: Boolean)

/** 닫기 결과 */
class ShutResult(val closed: Int, val turnEnd: TurnEnd?)

/**
 * 숫자 닫기 규칙 (2~4인, 차례대로, 3라운드).
 * - 라운드마다 모두 한 번씩 차례를 가진다. 차례마다 1~9 숫자판이 모두 열린 새 상자에서 시작
 * - 주사위 2개를 굴린다 (열린 숫자 합이 6 이하면 1개만 굴려도 됨)
 * - 열린 숫자 몇 개를 골라 합이 주사위 합과 같으면 닫는다 → 다시 굴리기
 * - 굴린 합을 만들 조합이 없으면 차례 끝: 남은 숫자 합이 그 라운드 점수. 모두 닫으면 0점(완벽!)
 * - 라운드마다 먼저 하는 사람이 한 칸씩 돈다 (1라운드 0번, 2라운드 1번 …)
 * - 3라운드 점수 합이 낮을수록 높은 순위, 같으면 공동 순위
 * 난수는 밖에서 주입한다 → 테스트에서 그대로 재현 가능.
 */
class ShutBoxGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    var phase = Phase.ROLL
        private set
    /** 지금 라운드 (1~3) */
    var round = 1
        private set
    /** 이번 라운드에서 몇 번째 차례인지 (0부터) */
    var turnInRound = 0
        private set
    /** 열린 숫자 (비트 마스크) */
    var open = ShutMath.FULL
        private set
    /** 고른 숫자 (비트 마스크, 열린 것 중) */
    var selected = 0
        private set
    /** 마지막으로 굴린 주사위 (diceCount 개만 의미 있음) */
    val dice = IntArray(GameConfig.DICE) { 1 }
    var diceCount = GameConfig.DICE
        private set
    /** 맞춰야 할 합 (굴리기 전 0) */
    var rollTotal = 0
        private set
    /** 마지막으로 끝난 차례 */
    var lastTurnEnd: TurnEnd? = null
        private set

    /** scores[p][r] = r 라운드(0부터) 점수, 아직이면 EMPTY */
    private val scores = Array(playerCount) { IntArray(GameConfig.ROUNDS) { EMPTY } }

    /** round 라운드(1부터)에 먼저 하는 사람 */
    fun startingPlayer(round: Int): Int = (round - 1) % playerCount

    /** round 라운드의 k 번째(0부터) 차례인 사람 */
    fun playerAt(round: Int, k: Int): Int = (startingPlayer(round) + k) % playerCount

    /** 지금 차례인 사람 */
    val current: Int get() = playerAt(round, turnInRound)

    val gameOver: Boolean get() = phase == Phase.GAME_OVER

    /** 열린 숫자 합 (차례가 지금 끝나면 받을 점수) */
    val openSum: Int get() = ShutMath.sumOf(open)
    val selectedSum: Int get() = ShutMath.sumOf(selected)

    /** 지금 주사위 1개만 굴려도 되는지 (열린 합 6 이하) */
    val canUseOneDie: Boolean get() = phase == Phase.ROLL && openSum <= GameConfig.ONE_DIE_MAX_SUM

    /** 고른 숫자 합이 주사위 합과 같아 [닫기]를 누를 수 있는지 */
    val canShut: Boolean get() = phase == Phase.SELECT && ShutMath.isValidSelection(open, selected, rollTotal)

    /** 주사위 합을 만드는 조합에 들어가는 열린 숫자 전부 (고른 것과 상관없이) */
    val comboMask: Int get() = if (phase == Phase.SELECT) ShutMath.highlightMask(open, rollTotal) else 0

    /** 지금 고른 것에 더해 합을 맞출 수 있는 숫자들 (화면 은은한 강조) */
    val hintMask: Int get() = if (phase == Phase.SELECT) ShutMath.extendMask(open, selected, rollTotal) else 0

    fun isOpen(n: Int): Boolean = ShutMath.has(open, n)
    fun isSelected(n: Int): Boolean = ShutMath.has(selected, n)

    /**
     * 주사위 굴리기 (count = 2, 또는 열린 합 6 이하일 때 1).
     * 조합이 없으면 바로 차례가 끝난다 (phase = TURN_OVER, lastTurnEnd 에 기록). 못 굴리면 null.
     */
    fun roll(count: Int = GameConfig.DICE): RollResult? {
        if (phase != Phase.ROLL) return null
        if (count == 1) {
            if (!canUseOneDie) return null
        } else if (count != GameConfig.DICE) return null
        diceCount = count
        var total = 0
        for (i in 0 until GameConfig.DICE) {
            dice[i] = if (i < count) random.nextInt(1, 7) else dice[i]
            if (i < count) total += dice[i]
        }
        rollTotal = total
        selected = 0
        val move = ShutMath.hasCombination(open, total)
        if (move) phase = Phase.SELECT else endTurn(TurnEndReason.NO_MOVE)
        return RollResult(dice.copyOf(count), total, move)
    }

    /**
     * 숫자 n 고르기/풀기. 닫힌 숫자, 고르는 중이 아닐 때, 고르면 합이 주사위 합을 넘을 때는 false.
     */
    fun toggle(n: Int): Boolean {
        if (phase != Phase.SELECT || n !in 1..GameConfig.TILES || !isOpen(n)) return false
        if (isSelected(n)) {
            selected = selected and ShutMath.bit(n).inv()
            return true
        }
        if (selectedSum + n > rollTotal) return false
        selected = selected or ShutMath.bit(n)
        return true
    }

    /** 고른 것 모두 풀기 */
    fun clearSelection() {
        if (phase == Phase.SELECT) selected = 0
    }

    /** 고른 숫자 닫기. 다 닫으면 완벽(0점)으로 차례 끝, 아니면 다시 굴리기. 못 닫으면 null */
    fun shut(): ShutResult? {
        if (!canShut) return null
        val closed = selected
        open = open and closed.inv()
        selected = 0
        rollTotal = 0
        if (open == 0) {
            endTurn(TurnEndReason.PERFECT)
            return ShutResult(closed, lastTurnEnd)
        }
        phase = Phase.ROLL
        return ShutResult(closed, null)
    }

    private fun endTurn(reason: TurnEndReason) {
        val p = current
        val pts = if (reason == TurnEndReason.PERFECT) 0 else openSum
        scores[p][round - 1] = pts
        selected = 0
        lastTurnEnd = TurnEnd(p, round, pts, reason)
        phase = Phase.TURN_OVER
    }

    /** 차례가 끝난 뒤 다음 사람(새 상자)으로. 3라운드가 끝나면 GAME_OVER. 넘어가면 true */
    fun advance(): Boolean {
        if (phase != Phase.TURN_OVER) return false
        if (turnInRound + 1 < playerCount) {
            turnInRound++
        } else if (round < GameConfig.ROUNDS) {
            round++
            turnInRound = 0
        } else {
            phase = Phase.GAME_OVER
            return true
        }
        open = ShutMath.FULL
        selected = 0
        rollTotal = 0
        diceCount = GameConfig.DICE
        phase = Phase.ROLL
        return true
    }

    /** 방금 advance() 로 새 라운드가 시작됐는지 (라운드 첫 차례) */
    val isRoundStart: Boolean get() = turnInRound == 0 && phase == Phase.ROLL && open == ShutMath.FULL

    fun score(player: Int, round: Int): Int = scores[player][round - 1]

    /** 끝난 라운드 점수 합 */
    fun total(player: Int): Int {
        var s = 0
        for (v in scores[player]) if (v > 0) s += v
        return s
    }

    /** 최종 순위 (1부터, players 순서, 합이 낮을수록 높은 순위, 같으면 공동 순위) */
    fun finalRanks(): IntArray = Ranking.competition(List(playerCount) { total(it).toLong() }, higherIsBetter = false)

    companion object {
        /** 아직 안 한 라운드 */
        const val EMPTY = -1
    }
}
