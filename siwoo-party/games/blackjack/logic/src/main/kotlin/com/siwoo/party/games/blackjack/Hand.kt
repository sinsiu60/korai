package com.siwoo.party.games.blackjack

import com.siwoo.party.common.cards.Card

/**
 * 블랙잭 손패 하나 (플레이어 또는 딜러). 합은 카드가 바뀔 때마다 다시 센다.
 * - A = 1 또는 11: hard = A 를 모두 1 로 센 합, soft = A 하나를 11 로 셀 수 있으면(21 이하) hard + 10
 * - best = 21 을 넘지 않는 가장 큰 합 (넘으면 hard)
 */
class Hand {
    private val list = ArrayList<Card>(12)

    val cards: List<Card> get() = list
    val size: Int get() = list.size
    val isEmpty: Boolean get() = list.isEmpty()

    /** A 를 모두 1 로 센 합 */
    var hard = 0
        private set
    private var aces = 0

    fun add(card: Card) {
        list.add(card)
        hard += points(card)
        if (card.rank == Card.ACE) aces++
    }

    fun clear() {
        list.clear()
        hard = 0
        aces = 0
    }

    /** A 하나를 11 로 세도 21 이하인지 */
    val isSoft: Boolean get() = aces > 0 && hard + 10 <= GameConfig.BUST_OVER

    /** 가장 좋은 합 */
    val best: Int get() = if (isSoft) hard + 10 else hard

    val isBust: Boolean get() = hard > GameConfig.BUST_OVER

    /** 처음 두 장이 21 */
    val isBlackjack: Boolean get() = list.size == 2 && best == GameConfig.BUST_OVER

    /** 화면 표시: soft 면 "7/17", 아니면 "17" (21 은 그냥 "21") */
    fun label(): String = label(hard, isSoft)

    companion object {
        /** 카드 점수: A = 1 (soft 계산은 Hand), 2~10 = 숫자, J·Q·K = 10 */
        fun points(card: Card): Int = when {
            card.rank == Card.ACE -> 1
            card.rank >= 10 -> 10
            else -> card.rank
        }

        /** 앞에서 n 장만 센 합 표시 (연출 중 아직 도착하지 않은 카드는 빼고 보여 줄 때) */
        fun labelOf(cards: List<Card>, n: Int = cards.size): String {
            var h = 0
            var a = 0
            for (i in 0 until minOf(n, cards.size)) {
                h += points(cards[i])
                if (cards[i].rank == Card.ACE) a++
            }
            return label(h, a > 0 && h + 10 <= GameConfig.BUST_OVER)
        }

        /** 앞에서 n 장의 best 합 */
        fun bestOf(cards: List<Card>, n: Int = cards.size): Int {
            var h = 0
            var a = 0
            for (i in 0 until minOf(n, cards.size)) {
                h += points(cards[i])
                if (cards[i].rank == Card.ACE) a++
            }
            return if (a > 0 && h + 10 <= GameConfig.BUST_OVER) h + 10 else h
        }

        private fun label(hard: Int, soft: Boolean): String =
            if (soft && hard + 10 < GameConfig.BUST_OVER) hard.toString() + "/" + (hard + 10)
            else (if (soft) hard + 10 else hard).toString()
    }
}
