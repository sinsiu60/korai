package com.siwoo.party.games.oldmaid

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Deck
import kotlin.random.Random

/** 같은 숫자 두 장 (버린 짝) */
class CardPair(val a: Card, val b: Card) {
    val rank: Int get() = a.rank
    override fun toString(): String = "$a+$b"
}

/** 한 장 가져온 결과 */
class DrawResult(
    /** 가져온 사람 / 빼앗긴 사람 */
    val taker: Int,
    val target: Int,
    /** 가져온 카드 */
    val card: Card,
    /** 가져오기 전 상대 손패에서의 자리 */
    val fromIndex: Int,
    /** 짝이 되어 버렸으면 (내 손패에 있던 카드 a + 가져온 카드 b), 아니면 null */
    val pair: CardPair?,
    /** 이번에 손패를 다 비워 끝난 사람 (빼앗긴 사람 먼저) */
    val finished: List<Int>,
    val gameOver: Boolean,
)

/**
 * 도둑잡기 규칙 (순수 Kotlin). 난수는 밖에서 주입.
 *
 * - 52장 + 조커 1장 = 53장을 0번부터 돌아가며 모두 나눈다 ([dealt] 에 나눈 그대로 보관)
 * - [discardInitialPairs]: 각자 같은 숫자 두 장씩 버림 (3장이면 한 장 남고, 4장이면 두 쌍)
 * - 차례: 지금 사람([current])이 다음 사람([target], 아직 카드가 있는 사람)의 손패에서 한 장 가져온다.
 *   가져오기 전 상대 손패는 [beginTurn] 에서 섞인다. 짝이 생기면 바로 버린다.
 * - 손패를 다 비운 순서대로 순위. 조커만 남은 사람이 꼴찌 (도둑)
 */
class OldMaidGame private constructor(val playerCount: Int, private val random: Random, preset: List<List<Card>>?) {

    constructor(playerCount: Int, random: Random) : this(playerCount, random, null)

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원 2~4: $playerCount" }
    }

    /** 처음 나눈 그대로 (짝 버리기 전), dealt[p][k] = p 가 k 번째로 받은 카드 */
    val dealt: List<List<Card>>

    private val handsM: List<ArrayList<Card>>

    /** 지금 손패 (가져온 카드는 끝에 붙는다) */
    val hands: List<List<Card>> get() = handsM

    private val pileM = ArrayList<Card>()

    /** 버린 카드 (짝 순서대로, 2장씩) */
    val pile: List<Card> get() = pileM

    /** 손패를 다 비워 끝난 순서 */
    val finishOrder = ArrayList<Int>()

    /** 순위 (끝나지 않았으면 0). 처음 짝 버리기로 동시에 끝난 사람들은 같은 순위 */
    val ranks = IntArray(playerCount)

    var current = 0
        private set

    var initialDiscardDone = false
        private set

    var gameOver = false
        private set

    /** 도둑 (끝났을 때 조커를 든 사람), 아직이면 -1 */
    var thief = -1
        private set

    /** 가져온 횟수 */
    var turnCount = 0
        private set

    init {
        val d = List(playerCount) { ArrayList<Card>() }
        if (preset != null) {
            for (p in 0 until playerCount) d[p].addAll(preset[p])
        } else {
            val deck = Deck(GameConfig.JOKERS)
            deck.shuffle(random)
            var p = 0
            while (deck.isNotEmpty) {
                d[p].add(deck.draw())
                p = (p + 1) % playerCount
            }
        }
        dealt = d.map { it.toList() }
        handsM = d
    }

    fun isFinished(p: Int): Boolean = ranks[p] != 0

    /** 아직 카드를 들고 있는 사람 수 */
    val playingCount: Int get() = (0 until playerCount).count { !isFinished(it) }

    /** from 다음부터 돌아가며 첫 번째로 아직 하는 사람 (from 자신은 맨 마지막에 본다) */
    fun nextPlaying(from: Int): Int {
        for (k in 1..playerCount) {
            val i = (from + k) % playerCount
            if (!isFinished(i)) return i
        }
        return -1
    }

    /** 지금 차례 사람이 카드를 가져올 사람 */
    val target: Int get() = nextPlaying(current)

    /** 카드 장수 */
    fun count(p: Int): Int = handsM[p].size

    /** 조커를 든 사람 */
    fun jokerHolder(): Int = handsM.indexOfFirst { h -> h.any { it.isJoker } }

    /**
     * 처음 짝 버리기 (한 번만). 결과[p] = p 가 버린 짝들.
     * 손패가 빈 사람은 그 자리에서 끝 (같은 순위). 0번부터 아직 하는 사람이 첫 차례.
     */
    fun discardInitialPairs(): List<List<CardPair>> {
        check(!initialDiscardDone) { "이미 짝을 버렸음" }
        initialDiscardDone = true
        val out = handsM.map { removePairs(it) }
        for (pairs in out) for (pr in pairs) { pileM.add(pr.a); pileM.add(pr.b) }
        val emptied = (0 until playerCount).filter { handsM[it].isEmpty() }
        finishGroup(emptied)
        current = if (isFinished(0)) nextPlaying(0) else 0
        checkEnd()
        return out
    }

    /**
     * 차례 시작: 가져올 사람(target)의 손패를 섞는다. 화면은 이 순서대로 뒷면을 펼친다.
     * 돌려주는 값 = target
     */
    fun beginTurn(): Int {
        check(initialDiscardDone && !gameOver)
        val t = target
        handsM[t].shuffle(random)
        return t
    }

    /** 지금 차례 사람이 target 손패의 index 번째 카드를 가져온다 */
    fun draw(index: Int): DrawResult {
        check(initialDiscardDone && !gameOver) { "가져올 수 없는 상태" }
        val taker = current
        val t = target
        require(t >= 0 && t != taker)
        val from = handsM[t]
        require(index in from.indices) { "자리 $index / ${from.size}" }
        val card = from.removeAt(index)
        turnCount++
        val mine = handsM[taker]
        var pair: CardPair? = null
        if (!card.isJoker) {
            val k = mine.indexOfFirst { it.rank == card.rank && !it.isJoker }
            if (k >= 0) {
                val m = mine.removeAt(k)
                pair = CardPair(m, card)
                pileM.add(m); pileM.add(card)
            }
        }
        if (pair == null) mine.add(card)
        val finished = ArrayList<Int>(2)
        // 빼앗긴 사람이 먼저 비었다 (마지막 카드를 넘겨준 순간), 그다음 가져간 사람
        if (from.isEmpty()) { finishOne(t); finished.add(t) }
        if (mine.isEmpty()) { finishOne(taker); finished.add(taker) }
        checkEnd()
        if (!gameOver) current = nextPlaying(taker)
        return DrawResult(taker, t, card, index, pair, finished, gameOver)
    }

    private fun finishOne(p: Int) {
        finishOrder.add(p)
        ranks[p] = finishOrder.size
    }

    /** 동시에 끝난 사람들 (같은 순위) */
    private fun finishGroup(ps: List<Int>) {
        if (ps.isEmpty()) return
        val r = finishOrder.size + 1
        for (p in ps) { finishOrder.add(p); ranks[p] = r }
    }

    private fun checkEnd() {
        if (gameOver) return
        if (playingCount <= 1) {
            val last = (0 until playerCount).firstOrNull { !isFinished(it) } ?: return
            thief = last
            ranks[last] = playerCount
            finishOrder.add(last)
            gameOver = true
        }
    }

    /** 끝난 뒤 순위 (players 순서, 1부터) */
    fun finalRanks(): IntArray {
        check(gameOver)
        return ranks.copyOf()
    }

    /** 보기 좋게 정렬한 손패 (숫자 → 무늬, 조커는 맨 끝) */
    fun sortedHand(p: Int): List<Card> = sortCards(handsM[p])

    companion object {
        /** 정해진 손패로 시작 (테스트·연습용). 짝 버리기는 [discardInitialPairs] 로 */
        fun withHands(hands: List<List<Card>>, random: Random): OldMaidGame {
            require(hands.size in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
            return OldMaidGame(hands.size, random, hands)
        }

        /**
         * 손패에서 같은 숫자 두 장씩 빼서 돌려준다 (손패 앞쪽 카드부터 짝지음).
         * 같은 숫자 3장이면 한 장 남고, 4장이면 두 쌍. 조커는 짝이 없다.
         */
        fun removePairs(hand: MutableList<Card>): List<CardPair> {
            val out = ArrayList<CardPair>()
            val waiting = arrayOfNulls<Card>(14)
            for (c in hand) {
                if (c.isJoker) continue
                val w = waiting[c.rank]
                if (w == null) waiting[c.rank] = c
                else { out.add(CardPair(w, c)); waiting[c.rank] = null }
            }
            if (out.isNotEmpty()) {
                val gone = HashSet<Card>()
                for (pr in out) { gone.add(pr.a); gone.add(pr.b) }
                hand.removeAll { it in gone }
            }
            return out
        }

        fun sortCards(cards: List<Card>): List<Card> =
            cards.sortedWith(compareBy<Card>({ if (it.isJoker) 99 else it.rank }, { it.id }))
    }
}
