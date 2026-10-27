package com.siwoo.party.games.highlow

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Deck
import kotlin.random.Random

/** 고르기: 다음 카드가 기준 카드보다 높다 / 낮다 */
enum class Guess { HIGHER, LOWER }

/** 진행 단계: 고르는 중 → (저장·실패) 차례 끝 → [HighLowGame.nextTurn] → 다음 사람 고르는 중 … → 게임 끝 */
enum class Phase { GUESSING, TURN_OVER, OVER }

/** 차례가 어떻게 끝났는지 */
enum class TurnEnd { NONE, SAVED, FAILED }

/**
 * 한 번 고른 결과.
 * @param base 고르기 전 기준 카드, drawn 새로 공개된 카드 (성공하면 다음 기준 카드)
 * @param streak 이번 차례 연속 성공 수 (실패면 0), pot 지금 이번 차례 점수 (실패면 0)
 * @param lostPot 실패로 날아간 점수 (성공이면 0)
 * @param reshuffled 이 카드를 뽑기 전에 덱이 비어 버린 카드를 다시 섞었는지
 */
class GuessResult(
    val player: Int,
    val guess: Guess,
    val base: Card,
    val drawn: Card,
    val success: Boolean,
    val tie: Boolean,
    val streak: Int,
    val pot: Long,
    val lostPot: Long,
    val reshuffled: Boolean,
)

/** [멈추고 저장] 결과: saved 점수가 total 로 */
class SaveResult(val player: Int, val saved: Long, val total: Long, val streak: Int)

/**
 * 하이로우 규칙 (순수 Kotlin). 난수는 밖에서 주입한다.
 *
 * - 카드 숫자 A=1(가장 낮음) … K=13(가장 높음), 무늬는 상관없음, 조커 없음(52장).
 * - 차례가 시작되면 카드 1장을 공개(기준 카드). 다음 카드가 [높다]/[낮다] 를 고른다.
 *   같은 숫자가 나오면 실패. 성공하면 새 카드가 다음 기준 카드가 된다.
 * - 이번 차례 점수(pot): 성공 k번 → 2^(k-1) (1, 2, 4, 8 …). 성공이 한 번이라도 있으면 [멈추고 저장] 가능.
 *   실패하면 저장 안 한 점수는 0 이 되고 차례가 끝난다.
 * - 차례는 사람마다 돌아가며 1인당 [turnsPerPlayer] 번 (5 × 인원). 끝나면 저장 점수 합으로 경쟁 순위(동점 같은 순위).
 * - 덱이 비면 버린 카드(지금 보이는 기준 카드 제외)를 모두 다시 섞어 덱으로.
 */
class HighLowGame(
    val playerCount: Int,
    private val random: Random,
    val turnsPerPlayer: Int = GameConfig.TURNS_PER_PLAYER,
    /** 처음 차례 (보통 0) */
    firstPlayer: Int = 0,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원 ${GameConfig.MIN_PLAYERS}~${GameConfig.MAX_PLAYERS}: $playerCount" }
        require(turnsPerPlayer >= 1) { "차례 수 >= 1" }
        require(firstPlayer in 0 until playerCount) { "첫 차례 범위 밖: $firstPlayer" }
    }

    /** 뽑는 덱 (조커 없음) */
    val deck: Deck = Deck.shuffled(random, GameConfig.JOKERS)

    /** 버린 카드 (끝 = 맨 위) */
    val discard = ArrayList<Card>()

    /** 저장한 점수 합 */
    val totals = LongArray(playerCount)

    /** 끝난 차례 수 */
    val turnsDone = IntArray(playerCount)

    /** 시작한 차례 수 (지금 차례 포함) — 화면의 "n/5 차례" */
    val turnsStarted = IntArray(playerCount)

    var current: Int = firstPlayer
        private set

    var phase: Phase = Phase.GUESSING
        private set

    /** 지금 기준 카드 (공개) */
    lateinit var base: Card
        private set

    /** 이번 차례 연속 성공 수 */
    var streak: Int = 0
        private set

    /** 지금까지 다시 섞은 횟수 */
    var reshuffles: Int = 0
        private set

    /** 마지막 뽑기 직전에 다시 섞었는지 (차례 시작 카드 포함) */
    var lastDrawReshuffled: Boolean = false
        private set

    /** 마지막으로 끝난 차례가 어떻게 끝났는지 */
    var lastEnd: TurnEnd = TurnEnd.NONE
        private set

    /** 전체 차례 번호 (1부터) */
    var turnNumber: Int = 1
        private set

    val totalTurns: Int get() = turnsPerPlayer * playerCount

    init {
        startTurn()
    }

    /** 지금 이번 차례 점수 */
    val pot: Long get() = potFor(streak)

    /** 한 번 더 맞히면 될 점수 */
    val nextPot: Long get() = potFor(streak + 1)

    /** [멈추고 저장] 가능 (성공이 한 번 이상) */
    val canStop: Boolean get() = phase == Phase.GUESSING && streak >= 1

    val isOver: Boolean get() = phase == Phase.OVER

    /** 기준 카드보다 높은 숫자 종류 수 (K 면 0) */
    val higherRanks: Int get() = GameConfig.HIGHEST_RANK - base.rank

    /** 기준 카드보다 낮은 숫자 종류 수 (A 면 0) */
    val lowerRanks: Int get() = base.rank - GameConfig.LOWEST_RANK

    /** 덱 남은 장수 */
    val deckSize: Int get() = deck.size

    /** 지금 차례가 이 사람의 마지막 차례인지 */
    val isLastTurnOfPlayer: Boolean get() = turnsStarted[current] >= turnsPerPlayer

    /** 높다/낮다 고르기. 고르는 중이 아니면 IllegalStateException */
    fun guess(g: Guess): GuessResult {
        check(phase == Phase.GUESSING) { "고르는 중이 아님: $phase" }
        val old = base
        val drawn = drawCard()
        val reshuffled = lastDrawReshuffled
        discard.add(old)
        base = drawn
        val tie = drawn.rank == old.rank
        val success = when (g) {
            Guess.HIGHER -> drawn.rank > old.rank
            Guess.LOWER -> drawn.rank < old.rank
        }
        return if (success) {
            streak++
            GuessResult(current, g, old, drawn, true, false, streak, pot, 0L, reshuffled)
        } else {
            val lost = pot
            streak = 0
            endTurn(TurnEnd.FAILED)
            GuessResult(current, g, old, drawn, false, tie, 0, 0L, lost, reshuffled)
        }
    }

    /** [멈추고 저장]: 이번 차례 점수를 합계에 더하고 차례 끝. 성공이 없으면 null */
    fun stop(): SaveResult? {
        if (!canStop) return null
        val saved = pot
        totals[current] += saved
        val s = streak
        streak = 0
        endTurn(TurnEnd.SAVED)
        return SaveResult(current, saved, totals[current], s)
    }

    /**
     * 차례 끝 → 다음 사람 차례 (새 기준 카드 공개). 모든 차례가 끝났으면 게임 끝(false).
     * 차례 끝 상태가 아니면 아무것도 하지 않고 phase == GUESSING 여부를 돌려준다.
     */
    fun nextTurn(): Boolean {
        if (phase != Phase.TURN_OVER) return phase == Phase.GUESSING
        if (turnsDone.all { it >= turnsPerPlayer }) {
            phase = Phase.OVER
            return false
        }
        discard.add(base)
        var next = (current + 1) % playerCount
        // 차례를 다 쓴 사람은 건너뜀 (돌아가며 하므로 보통 바로 다음 사람)
        while (turnsDone[next] >= turnsPerPlayer) next = (next + 1) % playerCount
        current = next
        turnNumber++
        startTurn()
        return true
    }

    /** 저장 점수 합 순위 (경쟁 순위, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(totals.toList(), higherIsBetter = true)

    /** 테스트·연출용: 덱 맨 위에 이 순서로 나오게 올림 (앞이 먼저 나옴) */
    fun stackTop(cards: List<Card>) {
        for (c in cards) deck.remove(c)
        for (c in cards.asReversed()) deck.addToTop(c)
    }

    /** 테스트용: 지금 기준 카드 바꾸기 (덱·버린 더미에서 빼고 원래 기준 카드는 덱 맨 아래로) */
    fun setBaseForTest(card: Card) {
        if (card == base) return
        deck.remove(card)
        discard.remove(card)
        deck.addToBottom(base)
        base = card
    }

    private fun startTurn() {
        base = drawCard()
        streak = 0
        turnsStarted[current]++
        phase = Phase.GUESSING
    }

    private fun endTurn(how: TurnEnd) {
        turnsDone[current]++
        lastEnd = how
        phase = Phase.TURN_OVER
    }

    /** 한 장 뽑기 — 덱이 비었으면 버린 카드를 모두 섞어 덱으로 */
    private fun drawCard(): Card {
        lastDrawReshuffled = false
        if (deck.isEmpty) {
            deck.refillFrom(discard, random, keepTop = 0)
            reshuffles++
            lastDrawReshuffled = true
        }
        return deck.draw()
    }

    companion object {
        /** 연속 성공 k 번일 때 이번 차례 점수: 0, 1, 2, 4, 8 … */
        fun potFor(streak: Int): Long =
            if (streak <= 0) 0L else 1L shl (streak - 1).coerceAtMost(GameConfig.MAX_POT_SHIFT)
    }
}
