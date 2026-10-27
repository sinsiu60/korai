package com.siwoo.party.games.onecard

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Deck
import com.siwoo.party.common.cards.Suit
import kotlin.random.Random

/** 차례 단계 */
enum class Phase {
    /** 지금 차례인 사람이 카드를 내거나 뽑는다 */
    PLAY,
    /** 7 을 냈다 → 무늬를 고른다 (같은 사람) */
    CHOOSE_SUIT,
    /** 손패 1장 → [원카드!] 를 기다린다 (같은 사람, 3초) */
    ONE_CARD,
    OVER,
}

/** 낸 카드의 효과 */
enum class Effect { NONE, ATTACK, SKIP, REVERSE, AGAIN, CHANGE_SUIT }

/**
 * 카드 한 장을 낸 결과.
 * @param attackTotal 지금 쌓인 공격 장수 (공격 카드가 아니면 0)
 * @param rank 이 카드로 손패를 다 내서 나갔으면 그 순위 (아니면 0)
 */
class Played(
    val player: Int,
    val card: Card,
    val effect: Effect,
    val attackTotal: Int,
    val rank: Int,
)

/**
 * 뽑은 결과.
 * @param requested 뽑아야 했던 장수 (공격이면 쌓인 장수, 아니면 1)
 * @param cards 실제로 뽑은 카드 (더미가 모두 떨어지면 requested 보다 적을 수 있음)
 * @param refilled 이번에 버린 더미를 섞어 뽑을 더미로 만든 횟수
 */
class Drawn(
    val player: Int,
    val cards: List<Card>,
    val requested: Int,
    val wasAttack: Boolean,
    val refilled: Int,
)

/**
 * 원카드 규칙 (순수 Kotlin). 난수는 밖에서 주입한다.
 *
 * 규칙 정리
 * - 1인당 7장, 52장 + 조커 2장. 첫 카드는 펼치기만 하고 효과는 없다 (조커면 아무 카드나).
 * - 낼 수 있는 카드: 지금 무늬(activeSuit)와 같은 무늬, 맨 위 카드와 같은 숫자, 조커는 언제나.
 *   맨 위가 조커이고 공격이 끝났으면(activeSuit == null) 아무 카드나.
 * - 공격: A 3장, 2 2장, 조커 5장. 세기 2 < A < 조커.
 *   공격을 받은 사람은 같거나 센 공격 카드로만 막을 수 있고(장수 누적), 그 카드도 무늬나 숫자가 맞아야 한다(조커는 언제나).
 *   예) ♠2 공격 → ♠A·♥2·조커로 막기 가능, ♥A 는 무늬·숫자 모두 달라 불가. ♠A 공격 → 2 로는 못 막음. 조커 공격 → 조커로만.
 *   못 막으면 쌓인 장수만큼 뽑고 차례가 끝난다 (공격 해제, 맨 위 카드 기준으로 다음 사람이 이어 냄).
 * - J: 다음 사람 건너뛰기 (2명이면 낸 사람이 한 번 더). Q: 방향 바꾸기 (2명이면 효과 없이 상대 차례).
 *   K: 한 번 더. 7: 무늬 바꾸기 (다음 사람은 고른 무늬·숫자 7·조커).
 * - 낼 카드가 없으면(또는 내기 싫으면) 1장 뽑고 차례 끝. 단 더미가 모두 떨어졌으면 낼 카드가 있는 사람은 내야 한다.
 * - 낸 뒤 손패가 1장이면 3초 안에 [원카드!] — 못 누르면 1장 벌칙.
 * - 손패를 다 내면 나간 순서대로 순위. 마지막 카드가 특수 카드여도 효과는 그대로 (공격은 다음 사람에게).
 *   남은 사람이 1명이면 그 사람이 꼴찌로 끝.
 * - 뽑을 더미가 떨어지면 버린 더미(맨 위 1장 제외)를 섞어 다시 쓴다. 둘 다 없으면 있는 만큼만 뽑는다.
 *   더미가 모두 떨어진 채로 남은 사람 모두가 한 바퀴 동안 아무것도 못 뽑고 못 내면 → 남은 사람은 손패가 적은 순서로 순위.
 */
class OneCardGame private constructor(
    val playerCount: Int,
    private val random: Random,
    val deck: Deck,
) {

    /** 새 판: 섞고 7장씩 나눈 뒤 첫 카드를 펼친다 */
    constructor(playerCount: Int, random: Random, firstPlayer: Int = 0) :
        this(playerCount, random, Deck.shuffled(random, GameConfig.JOKERS)) {
        repeat(GameConfig.HAND_SIZE) { for (p in 0 until playerCount) handsM[p].add(deck.draw()) }
        for (h in handsM) h.sortWith(HAND_ORDER)
        val first = deck.draw()
        discardM.add(first)
        activeSuit = first.suit
        current = firstPlayer
    }

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "인원 2~4: $playerCount" }
    }

    private val handsM: List<ArrayList<Card>> = List(playerCount) { ArrayList<Card>(24) }
    private val discardM = ArrayList<Card>(Card.COUNT)
    private val finishedM = BooleanArray(playerCount)
    private val orderM = ArrayList<Int>(playerCount)

    /** 손패 (무늬 ♠♥♦♣ → 숫자 → 조커 순으로 정렬) */
    val hands: List<List<Card>> get() = handsM

    /** 버린 더미 (마지막 = 맨 위) */
    val discard: List<Card> get() = discardM

    /** 맨 위 카드 */
    val top: Card get() = discardM[discardM.size - 1]

    /** 지금 따라야 할 무늬 (7 로 바뀐 무늬 포함). null = 아무 무늬나 (맨 위가 조커, 공격 끝) */
    var activeSuit: Suit? = null
        private set

    /** 쌓인 공격 장수 (0 = 공격 없음) */
    var pendingAttack = 0
        private set

    /** +1 = 번호 순서(오른쪽), -1 = 거꾸로 */
    var direction = 1
        private set

    var current = 0
        private set

    var phase = Phase.PLAY
        private set

    /** 나간 순서 (꼴찌 포함, 끝나면 모두) */
    val finishOrder: List<Int> get() = orderM

    /** 차례가 넘어간 횟수 */
    var turnCount = 0
        private set

    /** 버린 더미를 섞어 다시 쓴 횟수 */
    var refills = 0
        private set

    /** 더미가 모두 떨어져 손패 수로 끝났는지 */
    var stalled = false
        private set

    /** 마지막으로 낸 사람의 다음 차례 이동 칸 수 (0 = 한 번 더, 2 = 건너뛰기) */
    private var nextSteps = 1
    /** 아무것도 못 뽑고 넘어간 연속 차례 수 */
    private var idleTurns = 0

    val isOver: Boolean get() = phase == Phase.OVER

    fun isFinished(p: Int): Boolean = finishedM[p]

    /** 아직 남은 사람 수 */
    val activeCount: Int
        get() {
            var n = 0
            for (f in finishedM) if (!f) n++
            return n
        }

    /** 뽑을 수 있는 장수 (뽑을 더미 + 섞어 쓸 수 있는 버린 더미) */
    val drawableCount: Int get() = deck.size + (discardM.size - 1).coerceAtLeast(0)

    /** 모든 카드 수 (손패 + 덱 + 버린 더미) — 항상 54 */
    val totalCards: Int
        get() {
            var n = deck.size + discardM.size
            for (h in handsM) n += h.size
            return n
        }

    // ── 판정 ──

    /** 지금 차례인 사람이 이 카드를 낼 수 있는지 */
    fun canPlay(card: Card): Boolean {
        if (phase != Phase.PLAY) return false
        val t = top
        if (pendingAttack > 0) {
            val s = attackStrength(card)
            if (s == 0 || s < attackStrength(t)) return false
            if (card.isJoker || card.rank == t.rank) return true
            val suit = activeSuit
            return suit != null && card.suit == suit
        }
        if (card.isJoker) return true
        val suit = activeSuit ?: return true
        return card.suit == suit || (!t.isJoker && card.rank == t.rank)
    }

    /** player 손패 중 지금 낼 수 있는 카드 번호 (지금 차례가 아니면 빈 목록) */
    fun playableIndices(player: Int = current): List<Int> {
        if (player != current || phase != Phase.PLAY) return emptyList()
        val h = handsM[player]
        val out = ArrayList<Int>(h.size)
        for (i in h.indices) if (canPlay(h[i])) out.add(i)
        return out
    }

    fun hasPlayable(): Boolean = playableIndices().isNotEmpty()

    /**
     * 뽑기(차례 넘기기)를 할 수 있는지: 낼 카드가 있어도 뽑을 수 있지만,
     * 뽑을 카드가 하나도 없을 때는 낼 수 있는 카드가 있으면 내야 한다 (헛도는 판 방지).
     */
    val canDraw: Boolean get() = phase == Phase.PLAY && (drawableCount > 0 || !hasPlayable())

    /** 다음 차례가 될 사람 (지금 효과 기준 미리 보기, 낸 뒤 "+N" 표시용) */
    fun peekNext(steps: Int = 1): Int {
        var pos = current
        val s = if (steps == 0 && finishedM[current]) 1 else steps
        repeat(s) { pos = nextActive(pos) }
        return pos
    }

    // ── 행동 ──

    /** 지금 차례인 사람이 card 를 낸다 */
    fun play(card: Card): Played {
        check(phase == Phase.PLAY) { "지금은 낼 수 없음: $phase" }
        val p = current
        val hand = handsM[p]
        require(hand.contains(card)) { "손에 없는 카드: $card" }
        require(canPlay(card)) { "낼 수 없는 카드: $card (맨 위 $top, 무늬 $activeSuit, 공격 $pendingAttack)" }
        hand.remove(card)
        discardM.add(card)
        idleTurns = 0
        nextSteps = 1
        activeSuit = card.suit
        var effect = Effect.NONE
        val atk = attackValue(card)
        if (atk > 0) {
            pendingAttack += atk
            effect = Effect.ATTACK
        } else {
            when (card.rank) {
                Card.JACK -> { nextSteps = 2; effect = Effect.SKIP }
                Card.QUEEN -> { direction = -direction; effect = Effect.REVERSE }
                Card.KING -> { nextSteps = 0; effect = Effect.AGAIN }
                GameConfig.CHANGE_SUIT_RANK -> effect = Effect.CHANGE_SUIT
            }
        }
        var rank = 0
        if (hand.isEmpty()) {
            finishedM[p] = true
            orderM.add(p)
            rank = orderM.size
            if (activeCount <= 1) {
                endGame()
                return Played(p, card, effect, pendingAttack, rank)
            }
        }
        if (effect == Effect.CHANGE_SUIT) phase = Phase.CHOOSE_SUIT else afterPlay()
        return Played(p, card, effect, pendingAttack, rank)
    }

    /** 7 을 낸 뒤 무늬 고르기 */
    fun chooseSuit(suit: Suit) {
        check(phase == Phase.CHOOSE_SUIT) { "무늬를 고를 때가 아님: $phase" }
        activeSuit = suit
        phase = Phase.PLAY
        afterPlay()
    }

    /** [원카드!] 를 제때 눌렀다 */
    fun declareOneCard() {
        check(phase == Phase.ONE_CARD) { "원카드 때가 아님: $phase" }
        phase = Phase.PLAY
        advance()
    }

    /** [원카드!] 를 3초 안에 못 눌렀다 → 벌칙 1장 */
    fun oneCardTimeout(): Drawn {
        check(phase == Phase.ONE_CARD) { "원카드 때가 아님: $phase" }
        val p = current
        val before = refills
        val got = drawCards(p, GameConfig.ONE_CARD_PENALTY)
        phase = Phase.PLAY
        advance()
        return Drawn(p, got, GameConfig.ONE_CARD_PENALTY, false, refills - before)
    }

    /** 뽑기: 공격 중이면 쌓인 장수, 아니면 1장 → 차례 끝 */
    fun draw(): Drawn {
        check(canDraw) { "지금은 뽑을 수 없음: $phase (더미 $drawableCount)" }
        val p = current
        val attack = pendingAttack > 0
        val n = if (attack) pendingAttack else 1
        pendingAttack = 0
        val before = refills
        val got = drawCards(p, n)
        val result = Drawn(p, got, n, attack, refills - before)
        if (got.isEmpty()) idleTurns++ else idleTurns = 0
        if (idleTurns >= activeCount) {
            endByStall()
            return result
        }
        nextSteps = 1
        advance()
        return result
    }

    /** 순위 (players 순서, 1부터). 끝난 뒤에 부를 것 */
    fun finalRanks(): IntArray {
        val ranks = IntArray(playerCount)
        for ((i, p) in orderM.withIndex()) ranks[p] = i + 1
        if (orderM.size < playerCount) {
            // 손패 수로 끝난 경우: 남은 사람은 손패가 적을수록 앞 (같으면 같은 순위)
            val rest = (0 until playerCount).filter { it !in orderM }
            val r = Ranking.competition(rest.map { handsM[it].size.toLong() }, higherIsBetter = false)
            for ((k, p) in rest.withIndex()) ranks[p] = orderM.size + r[k]
        }
        return ranks
    }

    // ── 내부 ──

    private fun afterPlay() {
        val p = current
        if (!finishedM[p] && handsM[p].size == 1) {
            phase = Phase.ONE_CARD
            return
        }
        advance()
    }

    private fun advance() {
        if (activeCount <= 1) {
            endGame()
            return
        }
        current = peekNext(nextSteps)
        nextSteps = 1
        phase = Phase.PLAY
        turnCount++
    }

    private fun nextActive(from: Int): Int {
        var pos = from
        do {
            pos = (pos + direction + playerCount) % playerCount
        } while (finishedM[pos])
        return pos
    }

    private fun endGame() {
        for (p in 0 until playerCount) if (!finishedM[p]) {
            finishedM[p] = true
            orderM.add(p)
        }
        phase = Phase.OVER
    }

    private fun endByStall() {
        stalled = true
        phase = Phase.OVER
    }

    /** p 에게 n 장 (뽑을 더미가 떨어지면 버린 더미를 섞어 채움) */
    private fun drawCards(p: Int, n: Int): List<Card> {
        val out = ArrayList<Card>(n)
        for (k in 0 until n) {
            if (deck.isEmpty && deck.refillFrom(discardM, random, keepTop = 1) > 0) refills++
            val c = deck.drawOrNull() ?: break
            out.add(c)
        }
        val h = handsM[p]
        h.addAll(out)
        h.sortWith(HAND_ORDER)
        return out
    }

    companion object {
        /** 손패 정렬: ♠ ♥ ♦ ♣ 무늬별 A..K, 조커는 끝 */
        val HAND_ORDER: Comparator<Card> = Comparator { a, b -> a.id - b.id }

        /** 공격 장수 (공격 카드가 아니면 0) */
        fun attackValue(card: Card): Int = when {
            card.isJoker -> GameConfig.ATTACK_JOKER
            card.rank == Card.ACE -> GameConfig.ATTACK_ACE
            card.rank == 2 -> GameConfig.ATTACK_TWO
            else -> 0
        }

        /** 공격 세기: 2 = 1, A = 2, 조커 = 3, 공격 카드 아님 = 0 */
        fun attackStrength(card: Card): Int = when {
            card.isJoker -> 3
            card.rank == Card.ACE -> 2
            card.rank == 2 -> 1
            else -> 0
        }

        fun isAttack(card: Card): Boolean = attackStrength(card) > 0

        /**
         * 테스트·연습용 판 (손패·맨 위 카드·뽑을 더미를 직접 정함).
         * @param deckTopFirst 뽑을 더미 (첫 원소가 맨 위)
         * @param discardBelow 맨 위 카드 아래 깔린 버린 카드 (아래 → 위)
         */
        fun custom(
            hands: List<List<Card>>,
            top: Card,
            deckTopFirst: List<Card> = emptyList(),
            random: Random = Random(0),
            current: Int = 0,
            activeSuit: Suit? = top.suit,
            pendingAttack: Int = 0,
            direction: Int = 1,
            discardBelow: List<Card> = emptyList(),
        ): OneCardGame {
            val deck = Deck.fromIds(IntArray(deckTopFirst.size) { deckTopFirst[it].id })
            val g = OneCardGame(hands.size, random, deck)
            for ((p, h) in hands.withIndex()) {
                g.handsM[p].addAll(h)
                g.handsM[p].sortWith(HAND_ORDER)
            }
            g.discardM.addAll(discardBelow)
            g.discardM.add(top)
            g.activeSuit = activeSuit
            g.pendingAttack = pendingAttack
            g.direction = direction
            g.current = current
            return g
        }
    }
}
