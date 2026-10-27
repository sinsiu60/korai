package com.siwoo.party.games.memory

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 카드 앞면 그림 12종 (화면은 ordinal 로 그림을 고른다) */
enum class Symbol(val label: String) {
    STAR("별"), HEART("하트"), MOON("달"), SUN("해"), BOLT("번개"), DROP("물방울"),
    LEAF("나뭇잎"), DIAMOND("다이아"), CROWN("왕관"), NOTE("음표"), FIRE("불꽃"), CLOUD("구름"),
}

/** 카드 고르기 결과 */
sealed class Pick {
    /** 무시 (뒤집힌 카드·맞춘 카드·같은 카드 다시·세 번째 카드·게임 끝) */
    object Ignored : Pick()
    /** 첫 장 뒤집음 */
    class First(val card: Int) : Pick()
    /** 같은 그림: player 가 한 쌍 획득, 같은 사람이 한 번 더. gameOver 면 마지막 쌍 */
    class Match(val player: Int, val first: Int, val second: Int, val gameOver: Boolean) : Pick()
    /** 다른 그림: 보여 준 뒤 hideMismatch() 로 다시 덮고 다음 사람 차례 */
    class Mismatch(val player: Int, val first: Int, val second: Int) : Pick()
}

/**
 * 짝 맞추기 규칙.
 * - 카드 24장(그림 12종 × 2장)을 섞어 6×4 로 엎어 둔다
 * - 차례인 사람이 카드 2장을 차례로 뒤집는다
 *   같은 그림 → 그 쌍을 가져가고 한 번 더 / 다른 그림 → 다시 덮고(hideMismatch) 다음 사람
 * - 12쌍을 모두 찾으면 끝, 순위는 모은 쌍 수가 많은 순 (같으면 공동 순위)
 * 난수는 밖에서 주입 → 테스트에서 같은 배치를 재현할 수 있다.
 */
class MemoryGame(val playerCount: Int, random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 자리별 그림 (index = 줄 × COLS + 열) */
    val deck: List<Symbol> = Symbol.values().flatMap { listOf(it, it) }.shuffled(random)

    /** 지금 앞면이 보이는 카드 (맞춘 카드 포함) */
    val faceUp = BooleanArray(GameConfig.CARDS)
    /** 맞춘 카드의 주인 (-1 = 아직) */
    val owner = IntArray(GameConfig.CARDS) { -1 }
    /** 플레이어별 모은 쌍 수 */
    val pairs = IntArray(playerCount)

    /** 지금 차례 */
    var current = 0
        private set
    /** 이번 차례에 뒤집은 첫 장 (-1 = 없음) */
    var firstPick = -1
        private set
    /** 다른 그림 두 장이 보이는 중 (hideMismatch 전까지 고르기 무시) */
    var mismatchShowing = false
        private set
    private var mismatchA = -1
    private var mismatchB = -1

    val matchedPairs: Int get() = pairs.sum()
    val gameOver: Boolean get() = matchedPairs >= GameConfig.PAIRS

    fun symbolAt(card: Int): Symbol = deck[card]

    fun isMatched(card: Int): Boolean = owner[card] >= 0

    /** 지금 차례인 사람이 card 를 고름 */
    fun pick(card: Int): Pick {
        if (gameOver || mismatchShowing) return Pick.Ignored
        if (card !in 0 until GameConfig.CARDS) return Pick.Ignored
        if (faceUp[card] || owner[card] >= 0) return Pick.Ignored
        faceUp[card] = true
        val a = firstPick
        if (a < 0) {
            firstPick = card
            return Pick.First(card)
        }
        firstPick = -1
        return if (deck[a] == deck[card]) {
            owner[a] = current
            owner[card] = current
            pairs[current]++
            Pick.Match(current, a, card, gameOver)
        } else {
            mismatchShowing = true
            mismatchA = a
            mismatchB = card
            Pick.Mismatch(current, a, card)
        }
    }

    /** 다른 그림 두 장을 다시 덮고 다음 사람 차례로. 다음 차례 번호를 돌려준다 */
    fun hideMismatch(): Int {
        if (!mismatchShowing) return current
        faceUp[mismatchA] = false
        faceUp[mismatchB] = false
        mismatchA = -1
        mismatchB = -1
        mismatchShowing = false
        current = (current + 1) % playerCount
        return current
    }

    /** 최종 순위 (1부터, 동점 허용): 모은 쌍 수가 많은 순 */
    fun finalRanks(): IntArray = Ranking.competition(pairs.map { it.toLong() })
}
