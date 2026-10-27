package com.siwoo.party.games.yacht

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 칸 기록이 거절된 이유 */
enum class Reject { NOT_ROLLED, FILLED, GAME_OVER }

/** 칸 기록 결과 */
sealed class Record {
    class Rejected(val reason: Reject) : Record()
    /**
     * 기록됨. bonusEarned = 이번 기록으로 상단 63점을 처음 넘겨 보너스를 받음.
     * 기록 뒤에는 이미 다음 사람 차례로 넘어가 있다 (gameOver 면 끝).
     */
    class Done(val player: Int, val category: Category, val points: Int, val bonusEarned: Boolean, val gameOver: Boolean) : Record()
}

/**
 * 야추 주사위 규칙 (2~4인, 차례대로).
 * - 차례마다 주사위 5개를 최대 3번 굴린다. 첫 굴림 뒤부터 주사위를 고정/해제할 수 있고, 고정 안 된 것만 다시 굴린다
 * - 굴린 뒤 비어 있는 칸 하나를 골라 점수를 적으면 다음 사람 차례 (굴리기 전에는 적을 수 없음)
 * - 12라운드(모두 12칸을 채움) 뒤 총점(상단 63점 이상이면 보너스 35점 포함) 순으로 순위, 같으면 공동 순위
 * 난수는 밖에서 주입한다 → 테스트에서 그대로 재현 가능.
 */
class YachtGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 지금 주사위 눈 (첫 굴림 전에는 앞 사람 것이 남아 있음) */
    val dice = IntArray(GameConfig.DICE) { 1 }
    /** 고정한 주사위 */
    val held = BooleanArray(GameConfig.DICE)
    /** 지금 차례인 사람 */
    var current = 0
        private set
    /** 지금 라운드 (1~12) */
    var round = 1
        private set
    /** 이번 차례에 굴린 횟수 (0~3) */
    var rollsUsed = 0
        private set
    /** 끝난 차례 수 */
    var turnsTaken = 0
        private set

    /** sheet[p][칸] = 적은 점수 (EMPTY = 아직 안 채움) */
    private val sheet = Array(playerCount) { IntArray(GameConfig.CATEGORIES) { EMPTY } }
    /** 지금 주사위로 각 칸에 들어갈 점수 미리보기 (굴리기 전·채운 칸은 EMPTY) */
    private val previews = IntArray(GameConfig.CATEGORIES) { EMPTY }

    val gameOver: Boolean get() = turnsTaken >= GameConfig.ROUNDS * playerCount
    val rollsLeft: Int get() = GameConfig.MAX_ROLLS - rollsUsed
    val hasRolled: Boolean get() = rollsUsed > 0

    /** 더 굴릴 수 있는지 (3번 다 굴렸거나, 굴린 뒤 5개를 모두 고정하면 못 굴림) */
    val canRoll: Boolean get() = !gameOver && rollsUsed < GameConfig.MAX_ROLLS && (rollsUsed == 0 || held.any { !it })

    /** 굴린 주사위가 5개 모두 같은지 */
    val isYacht: Boolean get() = hasRolled && Scoring.isYacht(dice)

    /** 굴리기: 고정 안 된 주사위만 새 눈. 못 굴리면 false */
    fun roll(): Boolean {
        if (!canRoll) return false
        for (i in 0 until GameConfig.DICE) if (!held[i]) dice[i] = random.nextInt(1, 7)
        rollsUsed++
        updatePreviews()
        return true
    }

    /** 주사위 i 고정/해제. 첫 굴림 전·더 굴릴 수 없을 때는 false */
    fun toggleHold(i: Int): Boolean {
        if (i !in 0 until GameConfig.DICE || gameOver) return false
        if (!hasRolled || rollsUsed >= GameConfig.MAX_ROLLS) return false
        held[i] = !held[i]
        return true
    }

    fun score(player: Int, cat: Category): Int = sheet[player][cat.ordinal]
    fun isFilled(player: Int, cat: Category): Boolean = sheet[player][cat.ordinal] != EMPTY

    /** 지금 차례 사람이 cat 에 적으면 받을 점수 (못 적으면 EMPTY) */
    fun preview(cat: Category): Int = previews[cat.ordinal]

    fun canRecord(cat: Category): Boolean = !gameOver && hasRolled && !isFilled(current, cat)

    /** 지금 주사위로 cat 칸에 기록 → 다음 사람 차례 */
    fun record(cat: Category): Record {
        if (gameOver) return Record.Rejected(Reject.GAME_OVER)
        if (!hasRolled) return Record.Rejected(Reject.NOT_ROLLED)
        if (isFilled(current, cat)) return Record.Rejected(Reject.FILLED)
        val p = current
        val hadBonus = hasBonus(p)
        val pts = Scoring.score(cat, dice)
        sheet[p][cat.ordinal] = pts
        val bonusEarned = !hadBonus && hasBonus(p)
        turnsTaken++
        held.fill(false)
        rollsUsed = 0
        previews.fill(EMPTY)
        if (!gameOver) {
            current = (current + 1) % playerCount
            if (current == 0) round++
        }
        return Record.Done(p, cat, pts, bonusEarned, gameOver)
    }

    /** 상단(에이스~헥사) 합 */
    fun upperSubtotal(player: Int): Int {
        var s = 0
        for (k in 0 until UPPER_COUNT) { val v = sheet[player][k]; if (v > 0) s += v }
        return s
    }

    /** 상단 6칸을 모두 채웠는지 */
    fun upperComplete(player: Int): Boolean {
        for (k in 0 until UPPER_COUNT) if (sheet[player][k] == EMPTY) return false
        return true
    }

    fun hasBonus(player: Int): Boolean = upperSubtotal(player) >= GameConfig.UPPER_BONUS_THRESHOLD

    fun bonus(player: Int): Int = if (hasBonus(player)) GameConfig.UPPER_BONUS else 0

    /** 총점 = 채운 칸 합 + 보너스 */
    fun total(player: Int): Int {
        var s = 0
        for (v in sheet[player]) if (v > 0) s += v
        return s + bonus(player)
    }

    /** 최종 순위 (1부터, players 순서, 총점 같으면 공동 순위) */
    fun finalRanks(): IntArray = Ranking.competition(List(playerCount) { total(it).toLong() })

    private fun updatePreviews() {
        for (c in Category.ALL) {
            previews[c.ordinal] = if (isFilled(current, c)) EMPTY else Scoring.score(c, dice)
        }
    }

    companion object {
        /** 아직 안 채운 칸 */
        const val EMPTY = -1
        const val UPPER_COUNT = 6
    }
}
