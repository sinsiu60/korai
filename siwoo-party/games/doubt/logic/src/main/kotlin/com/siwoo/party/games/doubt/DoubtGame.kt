package com.siwoo.party.games.doubt

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Deck
import kotlin.random.Random

/** 지금 단계 */
enum class Phase {
    /** 차례인 사람이 카드를 고르는 중 */
    PLAY,
    /** 낸 직후 — 다른 사람이 [의심!] 을 누를 수 있다 */
    DOUBT,
    /** 끝 */
    OVER,
}

/** 한 번 낸 것 (뒷면으로 낸 카드 + 선언) */
class Play(
    val player: Int,
    /** 선언한 숫자 (1=A .. 13=K) — 순서로 정해져 있다 */
    val rank: Int,
    /** 실제로 낸 카드 (뒷면) */
    val cards: List<Card>,
    /** 이걸 내서 손패가 비었는지 (의심 창이 지나야 확정) */
    val emptiedHand: Boolean,
) {
    val count: Int get() = cards.size
    /** 선언이 진짜인지 (낸 카드가 모두 선언한 숫자) */
    val truthful: Boolean get() = DoubtGame.isTruthful(cards, rank)
}

/** 의심 창이 끝난 결과 */
class Resolution(
    val play: Play,
    /** 의심한 사람 (-1 = 아무도 안 함) */
    val doubter: Int,
    /** 거짓말이 들켰는지 (의심했고 거짓) */
    val caught: Boolean,
    /** 더미를 가져간 사람 (-1 = 아무도) */
    val taker: Int,
    /** 가져간 카드 (더미 전체, 아래 → 위 순서) */
    val taken: List<Card>,
    /** 이번에 다 비워 끝난 것이 확정된 사람 (-1 = 없음) */
    val finished: Int,
    /** finished 의 순위 (0 = 없음) */
    val finishedRank: Int,
    val gameOver: Boolean,
)

/**
 * 의심 카드 규칙 (순수 Kotlin). 난수는 밖에서 주입.
 *
 * - 조커 없는 52장을 0번부터 돌아가며 모두 나눈다 (2인 26장, 3인 18·17·17장, 4인 13장)
 * - 선언할 숫자는 A → 2 → … → K → A 순서로 정해져 있다 ([requiredRank]). 차례마다 하나씩 넘어간다
 * - 차례: 손패에서 1~4장을 뒷면으로 내며 "[requiredRank] N장" 이라고 선언 ([play]) — 다른 숫자를 섞으면 거짓말
 * - 낸 직후 다른 사람(아직 끝나지 않은 사람)이 의심할 수 있다 ([doubt]), 아무도 안 하면 [pass]
 *   - 공개해서 거짓이면 낸 사람이, 진짜면 의심한 사람이 더미 전체를 가져간다
 * - 손패를 다 비운 사람은 의심 창이 지나고 들키지 않았을 때만 끝 (들키면 더미를 가져가고 계속)
 * - 끝난 순서대로 순위, 한 사람만 남으면 그 사람이 꼴찌 (2인은 한 명이 끝나면 바로 끝)
 * - 차례 제한: [maxTurns] 차례가 끝나면 남은 사람은 손패가 적은 순서로 순위 ([stalled], 같은 장수는 같은 순위)
 */
class DoubtGame private constructor(
    val playerCount: Int,
    preset: List<List<Card>>?,
    random: Random,
    /** 차례 제한 (테스트에서 바꿀 수 있음) */
    val maxTurns: Int = GameConfig.MAX_TURNS,
) {

    constructor(playerCount: Int, random: Random) : this(playerCount, null, random)

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원 2~4: $playerCount" }
    }

    private val handsM: List<ArrayList<Card>> = List(playerCount) { ArrayList<Card>() }

    /** 지금 손패 */
    val hands: List<List<Card>> get() = handsM

    private val pileM = ArrayList<Card>()

    /** 가운데 더미 (뒷면, 아래 → 위) */
    val pile: List<Card> get() = pileM

    /** 손패를 다 비워 끝난 순서 */
    val finishOrder = ArrayList<Int>()

    /** 순위 (끝나지 않았으면 0) */
    val ranks = IntArray(playerCount)

    var current = 0
        private set

    /** 이번 차례에 선언할 숫자 (1=A .. 13=K) */
    var requiredRank = GameConfig.FIRST_RANK
        private set

    var phase = Phase.PLAY
        private set

    /** 마지막으로 낸 것 (의심 창 동안의 판정 대상) */
    var lastPlay: Play? = null
        private set

    /** 끝난 차례 수 */
    var turnCount = 0
        private set

    /** 차례 제한으로 끝났는지 */
    var stalled = false
        private set

    val isOver: Boolean get() = phase == Phase.OVER

    /** 남은 차례 (지금 차례 포함) */
    val turnsLeft: Int get() = (maxTurns - turnCount).coerceAtLeast(0)

    init {
        if (preset != null) {
            for (p in 0 until playerCount) handsM[p].addAll(preset[p])
        } else {
            val deck = Deck(0)
            deck.shuffle(random)
            var p = 0
            while (deck.isNotEmpty) {
                handsM[p].add(deck.draw())
                p = (p + 1) % playerCount
            }
        }
        for (h in handsM) sortInPlace(h)
    }

    fun isFinished(p: Int): Boolean = ranks[p] != 0

    fun count(p: Int): Int = handsM[p].size

    /** 아직 하는 사람 수 */
    val playingCount: Int get() = (0 until playerCount).count { !isFinished(it) }

    /** from 다음부터 돌아가며 첫 번째로 아직 하는 사람 (from 자신은 맨 마지막에 본다) */
    fun nextPlaying(from: Int): Int {
        for (k in 1..playerCount) {
            val i = (from + k) % playerCount
            if (!isFinished(i)) return i
        }
        return -1
    }

    /** 지금 차례 사람이 낼 수 있는 장수 범위 */
    val maxPlayable: Int get() = minOf(GameConfig.MAX_PLAY, handsM[current].size)

    /**
     * 지금 차례 사람이 cards 를 뒷면으로 내며 "[requiredRank] N장" 선언.
     * 1~4장, 모두 손패에 있는 서로 다른 카드여야 한다. → 의심 단계
     */
    fun play(cards: List<Card>): Play {
        check(phase == Phase.PLAY) { "낼 수 없는 단계: $phase" }
        val p = current
        val hand = handsM[p]
        require(cards.size in GameConfig.MIN_PLAY..GameConfig.MAX_PLAY) { "1~4장: ${cards.size}" }
        require(cards.toSet().size == cards.size) { "같은 카드 두 번" }
        for (c in cards) require(c in hand) { "손패에 없음: $c" }
        hand.removeAll(cards.toSet())
        pileM.addAll(cards)
        val pl = Play(p, requiredRank, cards.toList(), hand.isEmpty())
        lastPlay = pl
        phase = Phase.DOUBT
        return pl
    }

    /** p 가 지금 의심할 수 있는지 (낸 사람·끝난 사람은 못 함) */
    fun canDoubt(p: Int): Boolean {
        val pl = lastPlay ?: return false
        return phase == Phase.DOUBT && p in 0 until playerCount && p != pl.player && !isFinished(p)
    }

    /** 지금 의심할 수 있는 사람들 */
    fun doubters(): List<Int> = (0 until playerCount).filter { canDoubt(it) }

    /** doubter 가 [의심!] — 공개해서 거짓이면 낸 사람, 진짜면 의심한 사람이 더미 전체를 가져간다 */
    fun doubt(doubter: Int): Resolution {
        require(canDoubt(doubter)) { "의심할 수 없음: $doubter" }
        val pl = lastPlay!!
        val caught = !pl.truthful
        val taker = if (caught) pl.player else doubter
        val taken = pileM.toList()
        pileM.clear()
        handsM[taker].addAll(taken)
        sortInPlace(handsM[taker])
        return finishTurn(pl, doubter, caught, taker, taken)
    }

    /** 의심 창이 아무 일 없이 지남 */
    fun pass(): Resolution {
        check(phase == Phase.DOUBT) { "의심 단계가 아님" }
        return finishTurn(lastPlay!!, -1, false, -1, emptyList())
    }

    private fun finishTurn(pl: Play, doubter: Int, caught: Boolean, taker: Int, taken: List<Card>): Resolution {
        // 마지막 카드 규칙: 들키지 않았을 때만 끝 확정 (들키면 더미를 가져가 손패가 다시 생긴다)
        var finished = -1
        if (handsM[pl.player].isEmpty() && !isFinished(pl.player)) {
            finishOne(pl.player)
            finished = pl.player
        }
        turnCount++
        requiredRank = nextRank(requiredRank)
        lastPlay = null
        phase = Phase.PLAY
        checkEnd()
        if (!isOver && turnCount >= maxTurns) stall()
        if (!isOver) current = nextPlaying(pl.player)
        return Resolution(pl, doubter, caught, taker, taken, finished, if (finished >= 0) ranks[finished] else 0, isOver)
    }

    private fun finishOne(p: Int) {
        finishOrder.add(p)
        ranks[p] = finishOrder.size
    }

    private fun checkEnd() {
        if (playingCount <= 1) {
            val last = (0 until playerCount).firstOrNull { !isFinished(it) }
            if (last != null) {
                finishOrder.add(last)
                ranks[last] = playerCount
            }
            phase = Phase.OVER
        }
    }

    /** 차례 제한: 남은 사람은 손패가 적은 순서로 (같으면 같은 순위) */
    private fun stall() {
        stalled = true
        val left = (0 until playerCount).filter { !isFinished(it) }
        val r = Ranking.competition(left.map { handsM[it].size.toLong() }, higherIsBetter = false)
        val base = finishOrder.size
        for ((k, p) in left.withIndex()) ranks[p] = base + r[k]
        finishOrder.addAll(left.sortedBy { ranks[it] })
        phase = Phase.OVER
    }

    /** 끝난 뒤 순위 (players 순서, 1부터) */
    fun finalRanks(): IntArray {
        check(isOver)
        return ranks.copyOf()
    }

    companion object {
        /** 정해진 손패로 시작 (테스트·연습용) */
        fun withHands(hands: List<List<Card>>, random: Random = Random(0), maxTurns: Int = GameConfig.MAX_TURNS): DoubtGame {
            require(hands.size in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
            require(hands.flatten().none { it.isJoker }) { "조커 없음" }
            require(maxTurns > 0)
            return DoubtGame(hands.size, hands, random, maxTurns)
        }

        /** 다음 선언 숫자 (K 다음은 A) */
        fun nextRank(rank: Int): Int = if (rank >= Card.KING) Card.ACE else rank + 1

        /** cards 가 모두 rank 인지 (선언이 진짜인지) */
        fun isTruthful(cards: List<Card>, rank: Int): Boolean = cards.isNotEmpty() && cards.all { it.rank == rank }

        /** 처음 나눌 때 p 가 받는 장수 */
        fun dealSize(playerCount: Int, p: Int): Int {
            val base = GameConfig.TOTAL_CARDS / playerCount
            return base + if (p < GameConfig.TOTAL_CARDS % playerCount) 1 else 0
        }

        /** 손패 정렬: 숫자 → 무늬 */
        private fun sortInPlace(h: ArrayList<Card>) {
            h.sortWith(compareBy<Card>({ it.rank }, { it.id }))
        }
    }
}
