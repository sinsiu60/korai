package com.siwoo.party.common.cards

import kotlin.random.Random

/**
 * 카드 더미 (뽑는 덱). 난수는 밖에서 주입한다 (테스트는 Random(seed) 로 결정적).
 * 위 = 뽑히는 쪽. 새로 만든 덱은 id 순서(정렬)라서 쓰기 전에 [shuffle] 할 것.
 *
 * 버린 더미(discard pile)는 게임이 MutableList<Card> 로 들고, 마지막 원소를 맨 위로 본다
 * → 덱이 떨어지면 [refillFrom] 으로 맨 위 몇 장만 남기고 섞어 덱 밑에 넣는다.
 */
class Deck private constructor(private val cards: ArrayList<Card>) {

    /** 기본 52장 + 조커 jokerCount(0..2)장, 정렬된 상태 */
    constructor(jokerCount: Int = 0) : this(ArrayList<Card>(Card.COUNT)) {
        reset(jokerCount)
    }

    val size: Int get() = cards.size
    val isEmpty: Boolean get() = cards.isEmpty()
    val isNotEmpty: Boolean get() = cards.isNotEmpty()

    /** 새 덱으로 되돌림 (52장 + 조커, 정렬된 상태) */
    fun reset(jokerCount: Int = 0) {
        require(jokerCount in 0..2) { "조커는 0..2장: $jokerCount" }
        cards.clear()
        // 저장 순서: 앞 = 맨 아래, 끝 = 맨 위. 정렬된 덱은 위에서부터 ♠A, ♠2 … 가 나오게 거꾸로 넣는다
        for (id in Card.STANDARD_COUNT + jokerCount - 1 downTo 0) cards.add(Card.of(id))
    }

    /** 섞기 (Fisher–Yates, 주입된 난수) */
    fun shuffle(random: Random) {
        cards.shuffle(random)
    }

    /** 맨 위 한 장 뽑기. 비었으면 IllegalStateException — 먼저 [isEmpty] 확인 또는 [drawOrNull] */
    fun draw(): Card {
        check(cards.isNotEmpty()) { "덱이 비었습니다" }
        return cards.removeAt(cards.size - 1)
    }

    /** 맨 위 한 장 뽑기, 비었으면 null */
    fun drawOrNull(): Card? = if (cards.isEmpty()) null else cards.removeAt(cards.size - 1)

    /** 위에서 n 장 뽑기 (뽑은 순서). 남은 장수가 모자라면 남은 만큼만 */
    fun draw(n: Int): List<Card> {
        require(n >= 0) { "n >= 0" }
        val k = minOf(n, cards.size)
        val out = ArrayList<Card>(k)
        repeat(k) { out.add(cards.removeAt(cards.size - 1)) }
        return out
    }

    /** 맨 위 카드 보기 (뽑지 않음) */
    fun peek(): Card? = cards.lastOrNull()

    fun addToTop(card: Card) {
        cards.add(card)
    }

    fun addToBottom(card: Card) {
        cards.add(0, card)
    }

    /** 여러 장을 맨 아래에 (cards 의 첫 원소가 그중 가장 위) */
    fun addToBottom(more: Collection<Card>) {
        cards.addAll(0, more.reversed())
    }

    /** 특정 카드 빼기 (있으면 true) */
    fun remove(card: Card): Boolean = cards.remove(card)

    operator fun contains(card: Card): Boolean = cards.contains(card)

    /**
     * 버린 더미로 덱 다시 채우기: pile 의 맨 위(끝) keepTop 장은 남기고 나머지를 꺼내
     * 섞은 뒤 덱 맨 아래에 넣는다. pile 은 그만큼 줄어든다. 옮긴 장수를 돌려준다.
     * (원카드: refillFrom(discard, random, keepTop = 1) — 바닥에 보이는 카드는 그대로)
     */
    fun refillFrom(pile: MutableList<Card>, random: Random, keepTop: Int = 0): Int {
        require(keepTop >= 0) { "keepTop >= 0" }
        val moveCount = pile.size - keepTop
        if (moveCount <= 0) return 0
        val moved = ArrayList<Card>(moveCount)
        for (i in 0 until moveCount) moved.add(pile[i])
        pile.subList(0, moveCount).clear()
        moved.shuffle(random)
        cards.addAll(0, moved)
        return moveCount
    }

    /** 위 → 아래 순서 목록 (복사본) */
    fun toList(): List<Card> = cards.asReversed().toList()

    /** 저장용 id 배열 (위 → 아래) */
    fun toIds(): IntArray {
        val n = cards.size
        return IntArray(n) { cards[n - 1 - it].id }
    }

    companion object {
        /** [toIds] 로 저장한 덱 복원 */
        fun fromIds(ids: IntArray): Deck {
            val list = ArrayList<Card>(maxOf(ids.size, Card.COUNT))
            for (i in ids.indices.reversed()) list.add(Card.of(ids[i]))
            return Deck(list)
        }

        /** 섞인 새 덱 */
        fun shuffled(random: Random, jokerCount: Int = 0): Deck = Deck(jokerCount).also { it.shuffle(random) }
    }
}
