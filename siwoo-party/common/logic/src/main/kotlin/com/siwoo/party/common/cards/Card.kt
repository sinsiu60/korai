package com.siwoo.party.common.cards

/** 트럼프 카드 무늬. symbol 은 글자 표시용(그림은 UI 가 Path 로 그린다) */
enum class Suit(val symbol: String, val isRed: Boolean, val koName: String) {
    SPADE("♠", false, "스페이드"),
    HEART("♥", true, "하트"),
    DIAMOND("♦", true, "다이아"),
    CLUB("♣", false, "클로버"),
}

/**
 * 트럼프 카드 한 장 (불변 값). 54장 인스턴스를 미리 만들어 두고 [of] 로 같은 객체를 돌려준다
 * → 그리기·비교 중 새 객체가 생기지 않는다.
 *
 * id (저장·전송용, 바뀌지 않음):
 * - 0..51 = 무늬 순서(♠ ♥ ♦ ♣) × 13 + (숫자 - 1)   예) ♠A = 0, ♠K = 12, ♥A = 13, ♣K = 51
 * - 52 = 흑백 조커, 53 = 컬러(빨강) 조커
 *
 * rank: A=1, 2..10, J=11, Q=12, K=13 (조커는 0)
 */
class Card private constructor(val id: Int) {

    /** 무늬 (조커는 null) */
    val suit: Suit? = if (id < STANDARD_COUNT) SUITS[id / 13] else null

    /** 1(A)..13(K), 조커는 0 */
    val rank: Int = if (id < STANDARD_COUNT) id % 13 + 1 else 0

    val isJoker: Boolean get() = id >= STANDARD_COUNT

    /** 컬러(빨강) 조커인지 */
    val isRedJoker: Boolean get() = id == RED_JOKER_ID

    /** 빨간 카드인지 (♥ ♦, 컬러 조커) */
    val isRed: Boolean get() = suit?.isRed ?: isRedJoker

    /** J·Q·K 그림 카드 */
    val isCourt: Boolean get() = rank >= 11

    /** 숫자 글자: "A", "2".."10", "J", "Q", "K", 조커는 "JOKER" */
    val label: String get() = if (isJoker) "JOKER" else RANK_LABELS[rank - 1]

    /** 짧은 이름: "♠A", "♥10", "흑백 조커", "컬러 조커" */
    val shortName: String get() = NAMES[id]

    override fun equals(other: Any?): Boolean = other is Card && other.id == id
    override fun hashCode(): Int = id
    override fun toString(): String = NAMES[id]

    companion object {
        /** 조커 포함 전체 장수 */
        const val COUNT = 54
        /** 조커 뺀 기본 장수 */
        const val STANDARD_COUNT = 52
        const val BLACK_JOKER_ID = 52
        const val RED_JOKER_ID = 53
        const val ACE = 1
        const val JACK = 11
        const val QUEEN = 12
        const val KING = 13

        private val SUITS = Suit.values()

        /** rank 1..13 의 글자 */
        val RANK_LABELS: List<String> = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

        private val NAMES: Array<String> = Array(COUNT) { id ->
            when {
                id == BLACK_JOKER_ID -> "흑백 조커"
                id == RED_JOKER_ID -> "컬러 조커"
                else -> SUITS[id / 13].symbol + RANK_LABELS[id % 13]
            }
        }

        private val CARDS: Array<Card> = Array(COUNT) { Card(it) }

        /** 54장 전체 (id 순서) */
        val ALL: List<Card> = CARDS.asList()

        /** 조커 뺀 52장 (id 순서) */
        val STANDARD: List<Card> = ALL.subList(0, STANDARD_COUNT)

        val BLACK_JOKER: Card get() = CARDS[BLACK_JOKER_ID]
        val RED_JOKER: Card get() = CARDS[RED_JOKER_ID]

        /** id(0..53) → 카드. 범위 밖이면 IllegalArgumentException */
        fun of(id: Int): Card {
            require(id in 0 until COUNT) { "카드 id 는 0..53: $id" }
            return CARDS[id]
        }

        /** 무늬 + 숫자(1..13) → 카드 */
        fun of(suit: Suit, rank: Int): Card {
            require(rank in 1..13) { "rank 는 1..13: $rank" }
            return CARDS[suit.ordinal * 13 + rank - 1]
        }

        /** 숫자 글자 (1..13 → "A".."K") */
        fun rankLabel(rank: Int): String = RANK_LABELS[rank - 1]
    }
}
