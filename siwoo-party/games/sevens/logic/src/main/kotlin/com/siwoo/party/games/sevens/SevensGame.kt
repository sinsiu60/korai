package com.siwoo.party.games.sevens

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Deck
import com.siwoo.party.common.cards.Suit
import kotlin.random.Random

/**
 * 카드 한 장을 낸 결과.
 * @param auto 이 카드 덕분에 자리가 나서 공개 카드(탈락자 카드)에서 자동으로 놓인 카드 (놓인 순서)
 * @param rank 이 카드로 손패를 다 내서 나갔으면 그 순위 (아니면 0)
 * @param lastStanding 이번 일로 판이 끝나 혼자 남은 사람이 자동으로 순위를 받았으면 그 사람 (아니면 -1)
 */
class Placed(
    val player: Int,
    val card: Card,
    val auto: List<Card>,
    val rank: Int,
    val lastStanding: Int,
)

/**
 * 탈락 결과.
 * @param placed 공개하자마자 판에 놓인 카드 (놓인 순서). 탈락은 "놓을 카드가 없을 때"만 일어나므로
 *   지금 규칙에서는 늘 비어 있다 — 규칙이 바뀌어도 화면이 그대로 처리하도록 남겨 둔다.
 * @param pooled 놓을 자리가 없어 공개 카드로 남은 것 (자리가 나는 대로 자동으로 놓인다)
 * @param lastStanding 이번 탈락으로 혼자 남은 사람 (아니면 -1)
 */
class Eliminated(
    val player: Int,
    val placed: List<Card>,
    val pooled: List<Card>,
    val lastStanding: Int,
)

/**
 * 7 이어붙이기 규칙 (순수 Kotlin). 난수는 밖에서 주입한다.
 *
 * 규칙 정리 (기획의 애매한 부분은 이렇게 정했다)
 * - 조커 없는 52장을 0번 사람부터 한 장씩 돌려 모두 나눈다 → 2명 26·26, 3명 18·17·17, 4명 13장씩 (남는 장은 앞사람).
 * - 판은 무늬별 줄 4개(♠ ♥ ♦ ♣). 줄은 그 무늬의 7 이 놓여야 열린다 (7 은 언제든 낼 수 있음).
 *   열린 줄은 7 에서 아래로 6 → A, 위로 8 → K 쪽 끝에 바로 이어지는 카드만 놓을 수 있다. 차례에 1장.
 * - ♠7 을 가진 사람이 시작하면서 ♠7 을 자동으로 낸다 (그 사람의 첫 수). 다음 사람부터 차례로.
 * - 패스: 토큰이 남아 있으면 낼 카드가 있어도 언제든 토큰 1개로 패스할 수 있다 (1인 3개, 작전용).
 * - 차례가 왔는데 낼 카드도 토큰도 없으면 탈락. 그 사람 카드는 모두 공개된다:
 *   바로 놓을 수 있는 카드는 곧장 판에 놓이고, 나머지는 "공개 카드"로 남았다가 자리가 나는 대로 자동으로 놓인다.
 *   → 줄이 영영 막히는 일이 없다 (판 밖 카드는 살아 있는 사람 손이나 공개 카드에만 있으므로,
 *     판이 끝나지 않았으면 언제나 누군가는 낼 카드가 있다).
 * - 손패를 다 낸 순서대로 1등, 2등…. 남은 사람이 1명이 되면 그 사람이 다음 순위를 받고 끝.
 *   탈락한 사람은 그 뒤 순위이고, 늦게 탈락한 사람이 더 높다 (먼저 탈락한 사람이 꼴찌).
 */
class SevensGame private constructor(
    val playerCount: Int,
    hands: List<List<Card>>,
    tokenCount: Int,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원은 2~4명: $playerCount" }
        require(hands.size == playerCount) { "손패 수와 인원이 다름" }
    }

    private val handsM: List<MutableList<Card>> = List(playerCount) { p -> hands[p].sortedBy { it.id }.toMutableList() }

    /** 각자 손패 (무늬 ♠♥♦♣ → 숫자 순 정렬) */
    val hands: List<List<Card>> get() = handsM

    private val tokensM = IntArray(playerCount) { tokenCount }

    /** p 의 남은 패스 토큰 */
    fun tokens(p: Int): Int = tokensM[p]

    /** 무늬별 줄의 가장 낮은 / 높은 숫자 (0 = 아직 7 이 없어 닫힌 줄) */
    private val lowM = IntArray(4)
    private val highM = IntArray(4)

    private val finishM = ArrayList<Int>()
    private val outM = ArrayList<Int>()

    /** 다 낸(또는 마지막에 혼자 남은) 순서 */
    val finishOrder: List<Int> get() = finishM

    /** 탈락한 순서 */
    val outOrder: List<Int> get() = outM

    private val poolM = ArrayList<Card>()

    /** 공개 카드: 탈락자의 카드 중 아직 판에 못 놓인 것 */
    val pool: List<Card> get() = poolM

    /** 공개 카드의 원래 주인 (card.id → 사람, 없으면 -1) */
    private val poolOwnerM = IntArray(Card.STANDARD_COUNT) { -1 }

    fun poolOwner(card: Card): Int = poolOwnerM[card.id]

    /** 지금 차례 */
    var current = 0
        private set

    var isOver = false
        private set

    /** 시작할 때 자동으로 7 을 낸 사람 (-1 = 없음, 맞춤 판에서 시작 7 이 아무 손에도 없을 때) */
    var openingPlayer = -1
        private set

    /** 판에 놓인 장수 */
    var boardCount = 0
        private set

    // ── 판 ──

    fun low(suit: Suit): Int = lowM[suit.ordinal]
    fun high(suit: Suit): Int = highM[suit.ordinal]

    /** 그 무늬 줄이 열렸는지 (7 이 놓였는지) */
    fun isOpen(suit: Suit): Boolean = lowM[suit.ordinal] > 0

    fun isOnBoard(card: Card): Boolean {
        val s = card.suit ?: return false
        val lo = lowM[s.ordinal]
        return lo > 0 && card.rank in lo..highM[s.ordinal]
    }

    /** 판에 지금 놓을 수 있는 카드인지 (누구 손에 있는지와 상관없이) */
    fun isPlaceable(card: Card): Boolean {
        val s = card.suit ?: return false
        val lo = lowM[s.ordinal]
        if (lo == 0) return card.rank == GameConfig.OPEN_RANK
        return card.rank == lo - 1 || card.rank == highM[s.ordinal] + 1
    }

    // ── 사람 상태 ──

    fun isFinished(p: Int): Boolean = finishM.contains(p)
    fun isOut(p: Int): Boolean = outM.contains(p)
    fun isActive(p: Int): Boolean = !isFinished(p) && !isOut(p)

    /** 아직 겨루는 사람 수 */
    val activeCount: Int get() = (0 until playerCount).count { isActive(it) }

    /** 지금 차례 사람이 낼 수 있는 카드인지 */
    fun canPlay(card: Card): Boolean = !isOver && handsM[current].contains(card) && isPlaceable(card)

    /** p 의 손패 중 낼 수 있는 카드 번호 */
    fun playableIndices(p: Int = current): List<Int> {
        val out = ArrayList<Int>()
        if (isOver || !isActive(p)) return out
        val h = handsM[p]
        for (i in h.indices) if (isPlaceable(h[i])) out.add(i)
        return out
    }

    fun hasPlayable(p: Int = current): Boolean = isActive(p) && handsM[p].any { isPlaceable(it) }

    /** 지금 차례 사람이 패스할 수 있는지 (토큰이 남았으면 언제든) */
    val canPass: Boolean get() = !isOver && tokensM[current] > 0

    /** 지금 차례 사람이 낼 카드도 토큰도 없어 탈락해야 하는지 */
    val mustEliminate: Boolean get() = !isOver && isActive(current) && tokensM[current] == 0 && !hasPlayable(current)

    // ── 진행 ──

    /** 지금 차례 사람이 card 를 낸다 */
    fun play(card: Card): Placed {
        require(canPlay(card)) { "낼 수 없는 카드: $card" }
        val p = current
        handsM[p].remove(card)
        place(card)
        val auto = autoFill()
        var rank = 0
        if (handsM[p].isEmpty()) {
            finishM.add(p)
            rank = finishM.size
        }
        val last = advance(p)
        return Placed(p, card, auto, rank, last)
    }

    /** 지금 차례 사람이 토큰 1개를 써서 패스. 남은 토큰 수를 돌려준다 */
    fun pass(): Int {
        check(canPass) { "패스 토큰이 없습니다" }
        val p = current
        tokensM[p]--
        advance(p)
        return tokensM[p]
    }

    /** 낼 카드도 토큰도 없는 지금 차례 사람을 탈락시키고 카드를 공개한다 */
    fun eliminate(): Eliminated {
        check(mustEliminate) { "탈락 조건이 아닙니다" }
        val p = current
        outM.add(p)
        val mine = ArrayList(handsM[p])
        handsM[p].clear()
        for (c in mine) {
            poolM.add(c)
            poolOwnerM[c.id] = p
        }
        val placed = autoFill()
        val pooled = mine.filter { poolM.contains(it) }
        val last = advance(p)
        return Eliminated(p, placed, pooled, last)
    }

    /**
     * 최종 순위 (players 순서, 1부터). 다 낸 순서대로 1, 2, …, 그다음 탈락자 (늦게 탈락할수록 높음).
     * 판이 끝나기 전에 부르면 아직 겨루는 사람은 다 낸 사람 바로 뒤 같은 순위로 둔다.
     */
    fun finalRanks(): IntArray {
        val ranks = IntArray(playerCount)
        for ((i, p) in finishM.withIndex()) ranks[p] = i + 1
        val active = activeCount
        for (p in 0 until playerCount) if (isActive(p)) ranks[p] = finishM.size + 1
        for ((i, p) in outM.withIndex()) ranks[p] = finishM.size + active + (outM.size - i)
        return ranks
    }

    // ── 내부 ──

    private fun place(card: Card) {
        val s = card.suit!!.ordinal
        if (lowM[s] == 0) {
            lowM[s] = card.rank
            highM[s] = card.rank
        } else if (card.rank == lowM[s] - 1) {
            lowM[s] = card.rank
        } else {
            highM[s] = card.rank
        }
        boardCount++
    }

    /** 공개 카드 중 놓을 수 있는 것을 더 없을 때까지 놓는다 (놓인 순서) */
    private fun autoFill(): List<Card> {
        val out = ArrayList<Card>()
        while (true) {
            var found: Card? = null
            for (c in poolM) if (isPlaceable(c)) { found = c; break }
            val c = found ?: break
            poolM.remove(c)
            poolOwnerM[c.id] = -1
            place(c)
            out.add(c)
        }
        return out
    }

    /** from 다음 겨루는 사람에게 차례. 1명만 남으면 그 사람이 다음 순위를 받고 끝 (그 사람 번호, 아니면 -1) */
    private fun advance(from: Int): Int {
        val active = (0 until playerCount).filter { isActive(it) }
        if (active.size <= 1) {
            isOver = true
            if (active.size == 1) {
                finishM.add(active[0])
                current = active[0]
                return active[0]
            }
            return -1
        }
        var p = from
        do { p = (p + 1) % playerCount } while (!isActive(p))
        current = p
        return -1
    }

    /** 시작 7(♠7) 을 가진 사람이 자동으로 내고, 다음 사람 차례로 */
    private fun openWithStartSeven(fallbackFirst: Int) {
        val seven = Card.of(GameConfig.START_SUIT, GameConfig.OPEN_RANK)
        val holder = handsM.indexOfFirst { it.contains(seven) }
        if (holder < 0) {
            current = fallbackFirst
            return
        }
        openingPlayer = holder
        handsM[holder].remove(seven)
        place(seven)
        var p = holder
        do { p = (p + 1) % playerCount } while (!isActive(p))
        current = p
    }

    companion object {
        /** 새 판: 52장을 섞어 모두 나누고, ♠7 가진 사람이 자동으로 내며 시작 */
        fun create(playerCount: Int, random: Random): SevensGame {
            val deck = Deck.shuffled(random, jokerCount = 0)
            val hands = List(playerCount) { ArrayList<Card>() }
            var k = 0
            while (deck.isNotEmpty) {
                hands[k % playerCount].add(deck.draw())
                k++
            }
            val g = SevensGame(playerCount, hands, GameConfig.PASS_TOKENS)
            g.openWithStartSeven(0)
            return g
        }

        /** 나눠 받는 장수 (앞사람부터 1장 더) */
        fun dealSize(playerCount: Int, player: Int): Int {
            val base = Card.STANDARD_COUNT / playerCount
            val extra = Card.STANDARD_COUNT % playerCount
            return base + if (player < extra) 1 else 0
        }

        /**
         * 테스트·연출용 맞춤 판.
         * @param board 이미 판에 놓인 카드 (무늬마다 7 에서 이어져 있어야 함)
         * @param openWithSeven true 면 ♠7 가진 사람이 자동으로 내고 시작 (board 에 ♠7 이 없을 때만)
         * @param first 시작 7 을 안 낼 때 첫 차례
         * @param tokens 사람마다 토큰 (null 이면 모두 기본 개수)
         */
        fun custom(
            hands: List<List<Card>>,
            board: List<Card> = emptyList(),
            openWithSeven: Boolean = true,
            first: Int = 0,
            tokens: IntArray? = null,
        ): SevensGame {
            val g = SevensGame(hands.size, hands, GameConfig.PASS_TOKENS)
            tokens?.let { for (p in it.indices) g.tokensM[p] = it[p] }
            // 7 에서 가까운 순서로 놓는다
            val sorted = board.sortedWith(compareBy({ it.suit!!.ordinal }, { kotlin.math.abs(it.rank - GameConfig.OPEN_RANK) }))
            for (c in sorted) {
                require(g.isPlaceable(c)) { "판에 이어지지 않는 카드: $c" }
                g.place(c)
            }
            val seven = Card.of(GameConfig.START_SUIT, GameConfig.OPEN_RANK)
            if (openWithSeven && !g.isOnBoard(seven)) g.openWithStartSeven(first) else g.current = first
            return g
        }
    }
}
