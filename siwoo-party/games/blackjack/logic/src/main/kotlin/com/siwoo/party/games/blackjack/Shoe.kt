package com.siwoo.party.games.blackjack

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Deck
import kotlin.random.Random

/**
 * 카드 통(슈): 조커 없는 52장 × decks 벌을 섞어 쓴다 (공통 Deck 위에 같은 카드가 여러 장 들어간 더미).
 * 쓴 카드는 [discard] 로 버린 더미에 모았다가 [reshuffle] 때 모두 되돌려 섞는다.
 */
class Shoe(private val random: Random, val decks: Int = GameConfig.DECKS) {

    private val deck: Deck = Deck.fromIds(IntArray(Card.STANDARD_COUNT * decks) { it % Card.STANDARD_COUNT })

    /** 버린 카드 (끝 = 맨 위) */
    val discards = ArrayList<Card>()

    /** 전체 장수 */
    val total: Int = Card.STANDARD_COUNT * decks

    /** 남은(뽑을 수 있는) 장수 */
    val remaining: Int get() = deck.size

    /** 지금까지 다시 섞은 횟수 (처음 섞기 제외) */
    var reshuffles = 0
        private set

    init {
        require(decks >= 1) { "decks >= 1" }
        deck.shuffle(random)
    }

    /** 한 장 뽑기. 비었으면 버린 카드를 섞어 채운 뒤 뽑는다 */
    fun draw(): Card {
        if (deck.isEmpty) reshuffle()
        return deck.draw()
    }

    /** 맨 위 카드 (뽑지 않음) */
    fun peek(): Card? = deck.peek()

    fun discard(cards: Collection<Card>) {
        discards.addAll(cards)
    }

    /** 라운드 시작 전에 다시 섞어야 하는지 */
    val needsReshuffle: Boolean get() = deck.size < GameConfig.RESHUFFLE_BELOW

    /** 버린 카드를 모두 되돌려 통째로 섞는다. 되돌린 장수 */
    fun reshuffle(): Int {
        val n = deck.refillFrom(discards, random)
        deck.shuffle(random)
        reshuffles++
        return n
    }

    /**
     * 테스트·연출용: cards 를 이 순서대로 다음에 뽑히게 맨 위에 올린다 (통 안의 같은 카드 한 장씩을 옮김 → 장수 그대로).
     * 통에 없는 카드면(이미 나감) 그냥 위에 얹는다.
     */
    fun stackTop(cards: List<Card>) {
        // 먼저 모두 빼고(같은 카드가 여러 번이면 그만큼) 나중에 올린다 — 올린 카드를 다시 빼지 않도록
        for (c in cards) deck.remove(c)
        for (i in cards.indices.reversed()) deck.addToTop(cards[i])
    }
}
