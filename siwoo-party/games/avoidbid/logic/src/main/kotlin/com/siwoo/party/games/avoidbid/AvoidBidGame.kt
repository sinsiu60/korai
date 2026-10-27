package com.siwoo.party.games.avoidbid

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 넘기기 결과: player 가 칩 1개를 카드 위에 내고 next 차례가 됨 */
data class Passed(val player: Int, val card: Int, val pot: Int, val chipsLeft: Int, val next: Int)

/**
 * 가져가기 결과: player 가 card 와 그 위의 칩 chips 개를 가져감.
 * 같은 사람이 이어서 nextCard 를 보고 다시 고른다 (nextCard = -1 이면 덱이 비어 게임 끝).
 * joinedRun = 가져간 카드가 이미 가진 카드와 연속 숫자로 이어졌는지 (화면 반짝임용).
 */
data class Took(val player: Int, val card: Int, val chips: Int, val nextCard: Int, val joinedRun: Boolean) {
    val gameOver: Boolean get() = nextCard < 0
}

/**
 * 피하기 경매 규칙 (순수 Kotlin).
 * - 카드 3~35 (33장) 중 9장을 아무도 모르게 빼고 24장을 덱으로 쓴다. 1인당 칩 11개.
 * - 덱 맨 위 카드가 공개된다. 차례인 사람은
 *   [가져가기]: 카드와 그 위의 칩을 모두 가져가고, 같은 사람이 다음 카드를 공개해 다시 고른다.
 *   [넘기기]: 칩 1개를 카드 위에 올리고 다음 사람 차례. 칩이 0개면 넘길 수 없다(반드시 가져가기).
 * - 덱이 비면(24장을 모두 가져가면) 끝. 점수 = 카드 점수(연속 묶음은 가장 낮은 숫자만) - 남은 칩, 낮은 순으로 순위.
 * - 칩 수는 모두에게 공개한다 (원래 규칙은 비밀이지만 한 기기로 하는 파티용으로 단순하게).
 *
 * deckOrder: 쓰는 카드 순서 (0번이 맨 위 → 처음 공개), removedCards: 빼 둔 카드.
 * 테스트는 이 생성자로 순서를 정하고, 게임은 [deal] 로 섞어 만든다.
 */
class AvoidBidGame(val playerCount: Int, deckOrder: List<Int>, removedCards: List<Int>, startPlayer: Int = 0) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원 $playerCount" }
        require(deckOrder.isNotEmpty()) { "빈 덱" }
        require(startPlayer in 0 until playerCount)
    }

    /** 빼 둔 카드 (작은 순, 게임 중엔 비공개) */
    val removed: List<Int> = removedCards.sorted()

    /** 공개 카드 뒤에 남은 덱 (0번이 다음에 나올 카드) */
    private val deck = ArrayList<Int>(deckOrder.drop(1))

    /** 지금 공개된 카드 (-1 = 덱이 비어 게임 끝) */
    var card: Int = deckOrder[0]
        private set

    /** 공개 카드 위에 쌓인 칩 */
    var pot: Int = 0
        private set

    /** 지금 차례 */
    var current: Int = startPlayer
        private set

    /** 각자 남은 칩 */
    val chips = IntArray(playerCount) { GameConfig.START_CHIPS }

    private val taken = Array(playerCount) { ArrayList<Int>() }

    /** 공개 카드를 빼고 덱에 남은 장수 */
    val deckRemaining: Int get() = deck.size

    val gameOver: Boolean get() = card < 0

    /** 지금 차례인 사람이 넘길 수 있는지 (칩이 있어야 함) */
    fun canPass(): Boolean = !gameOver && chips[current] >= GameConfig.PASS_COST

    /** p 가 가져간 카드 (작은 순) */
    fun cardsOf(p: Int): List<Int> = taken[p].sorted()

    /** p 의 지금 점수 (낮을수록 좋음) */
    fun score(p: Int): Int = Scoring.score(taken[p], chips[p])

    /** 칩 1개를 내고 넘긴다. 칩이 없거나 게임이 끝났으면 IllegalStateException */
    fun pass(): Passed {
        check(!gameOver) { "게임 끝" }
        check(canPass()) { "칩이 없어 넘길 수 없음" }
        val p = current
        chips[p] -= GameConfig.PASS_COST
        pot += GameConfig.PASS_COST
        current = (current + 1) % playerCount
        return Passed(p, card, pot, chips[p], current)
    }

    /** 카드와 그 위 칩을 가져간다. 같은 사람이 이어서 다음 카드를 고른다. */
    fun take(): Took {
        check(!gameOver) { "게임 끝" }
        val p = current
        val c = card
        val got = pot
        val joined = taken[p].any { it == c - 1 || it == c + 1 }
        taken[p].add(c)
        chips[p] += got
        pot = 0
        card = if (deck.isEmpty()) -1 else deck.removeAt(0)
        return Took(p, c, got, card, joined)
    }

    /** 각자 점수 */
    fun scores(): IntArray = IntArray(playerCount) { score(it) }

    /** 순위 (players 순서, 1부터). 점수가 낮을수록 높은 순위, 같은 점수는 같은 순위 */
    fun finalRanks(): IntArray = Ranking.competition(List(playerCount) { score(it).toLong() }, higherIsBetter = false)

    companion object {
        /** 3~35 를 섞어 9장을 빼고 24장으로 덱을 만든다 */
        fun deal(playerCount: Int, random: Random, startPlayer: Int = 0): AvoidBidGame {
            val all = (GameConfig.LOWEST_CARD..GameConfig.HIGHEST_CARD).shuffled(random)
            return AvoidBidGame(playerCount, all.drop(GameConfig.REMOVED_CARDS), all.take(GameConfig.REMOVED_CARDS), startPlayer)
        }
    }
}
