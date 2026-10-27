package com.siwoo.party.games.speed

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Deck
import kotlin.math.abs
import kotlin.random.Random

/** 카드 내기 결과 */
enum class PlayResult {
    /** 냈음 (빈 칸은 자기 더미에서 채움) */
    PLAYED,
    /** 냈고, 이걸로 카드를 다 냄 → 승리 */
    WON,
    /** 그 칸이 비어 있음 */
    EMPTY_SLOT,
    /** 가운데 맨 위 카드와 1 차이가 아님 (먼저 다른 카드가 놓였을 때 포함) → 제자리로 */
    NOT_ADJACENT,
    /** 이미 끝난 판 */
    OVER,
}

/** [다시] 버튼 결과 */
enum class AgainResult {
    /** 아직 낼 수 있는 카드가 있음 → 무시 */
    NOT_STUCK,
    /** 눌렀고 상대를 기다림 */
    WAITING,
    /** 이미 눌러 둔 상태 */
    ALREADY,
    /** 둘 다 눌러서 가운데에 새 카드를 뒤집었음 */
    FLIPPED,
}

/** [다시] 때 그 사람이 뒤집은 카드가 어디서 왔는지 */
enum class FlipSource {
    /** 자기 뽑을 더미 (내 카드가 한 장 줄어듦) */
    OWN,
    /** 자기 더미가 비어서 가운데 더미 밑바닥 카드를 다시 씀 */
    RESERVE,
    /** 가운데에도 여분이 없어 상대 더미에서 (정상 판에서는 생기지 않음) */
    OTHER,
    /** 뒤집을 카드가 아무 데도 없음 */
    NONE,
}

/**
 * 스피드 카드 규칙 (2인 동시 진행, 순수 Kotlin).
 *
 * 나누기: 조커 뺀 52장을 섞어 26장씩. 각자 앞에 4장을 펼쳐 놓고(칸) 나머지 22장이 자기 뽑을 더미.
 * 시작할 때 각자 자기 더미 맨 위 1장을 가운데 자기 쪽 더미(플레이어 p → 가운데 p)에 뒤집는다 → 뽑을 더미 21장.
 *
 * 내기: 칸의 카드를 가운데 더미 맨 위 카드보다 숫자가 1 크거나 작으면 그 위에 놓는다 (K 와 A 는 이어짐, 무늬 상관없음).
 * 낸 칸은 곧바로 자기 뽑을 더미에서 채운다 (더미가 비면 빈 칸으로 남음). 두 사람이 동시에 하므로
 * 같은 더미에 거의 동시에 놓으면 먼저 처리된 쪽이 놓이고, 나중 것은 맨 위가 바뀌어 NOT_ADJACENT → 제자리.
 *
 * 막힘: 두 사람 모두 칸의 어떤 카드도 두 더미 어디에도 낼 수 없으면 [stuck].
 * 그때 양쪽이 [다시]를 모두 누르면 각자 자기 뽑을 더미에서 1장을 가운데 자기 쪽 더미에 뒤집는다.
 * 자기 더미가 비었으면 가운데 더미(더 두꺼운 쪽)의 밑바닥 카드를 대신 뒤집는다(맨 위는 그대로).
 *
 * 승리: 칸과 뽑을 더미의 카드를 모두 먼저 없앤 사람 (1위), 상대 2위.
 */
class SpeedGame private constructor(
    /** 뽑을 더미 (마지막 = 맨 위) */
    private val draw: Array<ArrayList<Card>>,
    private val slots: Array<Array<Card?>>,
    /** 가운데 더미 (마지막 = 맨 위) */
    private val center: Array<ArrayList<Card>>,
) {
    /** 이긴 사람 (-1 = 아직) */
    var winner = -1
        private set
    val isOver: Boolean get() = winner >= 0

    private val ready = BooleanArray(GameConfig.PLAYERS)

    /** 마지막 [다시] 때 각자 뒤집은 출처 */
    val lastFlip = arrayOf(FlipSource.NONE, FlipSource.NONE)

    /** 마지막 play 에서 빈 칸을 더미로 채웠는지 */
    var lastRefilled = false
        private set

    /** 지금까지 [다시]로 뒤집은 횟수 */
    var flips = 0
        private set

    // ── 읽기 ──

    fun slot(p: Int, i: Int): Card? = slots[p][i]
    fun drawSize(p: Int): Int = draw[p].size
    fun centerSize(k: Int): Int = center[k].size
    fun centerTop(k: Int): Card? = center[k].lastOrNull()
    /** 가운데 더미 k 의 위에서 depth 번째 (0 = 맨 위) */
    fun centerAt(k: Int, depth: Int): Card? = center[k].getOrNull(center[k].size - 1 - depth)

    /** 칸에 있는 카드 수 */
    fun slotCount(p: Int): Int = slots[p].count { it != null }

    /** 남은 카드 = 칸 + 뽑을 더미 */
    fun cardsLeft(p: Int): Int = slotCount(p) + draw[p].size

    /** 모든 곳의 카드 수 (항상 52, 검사용) */
    fun totalCards(): Int = (0 until GameConfig.PLAYERS).sumOf { cardsLeft(it) } + center.sumOf { it.size }

    /** [다시]를 눌러 둔 상태인지 */
    fun isReady(p: Int): Boolean = ready[p]

    /** 칸 i 의 카드를 가운데 k 에 낼 수 있는지 */
    fun canPlay(p: Int, i: Int, k: Int): Boolean {
        val c = slots[p][i] ?: return false
        val top = centerTop(k) ?: return false
        return adjacent(c.rank, top.rank)
    }

    /** 이 사람이 낼 수 있는 카드가 하나라도 있는지 */
    fun hasMove(p: Int): Boolean {
        for (i in 0 until GameConfig.SLOTS) for (k in 0 until GameConfig.CENTER_PILES) if (canPlay(p, i, k)) return true
        return false
    }

    /** 둘 다 낼 카드가 없어 [다시]가 필요한 상태 */
    val stuck: Boolean get() = !isOver && !hasMove(0) && !hasMove(1)

    /** 낼 수 있는 (칸, 가운데) 하나 — 없으면 null. 테스트·도움말용 */
    fun firstMove(p: Int): IntArray? {
        for (i in 0 until GameConfig.SLOTS) for (k in 0 until GameConfig.CENTER_PILES) if (canPlay(p, i, k)) return intArrayOf(i, k)
        return null
    }

    // ── 진행 ──

    /** 칸 i 의 카드를 가운데 k 에 낸다 */
    fun play(p: Int, i: Int, k: Int): PlayResult {
        lastRefilled = false
        if (isOver) return PlayResult.OVER
        val c = slots[p][i] ?: return PlayResult.EMPTY_SLOT
        if (!canPlay(p, i, k)) return PlayResult.NOT_ADJACENT
        center[k].add(c)
        slots[p][i] = null
        val pile = draw[p]
        if (pile.isNotEmpty()) {
            slots[p][i] = pile.removeAt(pile.size - 1)
            lastRefilled = true
        }
        if (cardsLeft(p) == 0) {
            winner = p
            return PlayResult.WON
        }
        return PlayResult.PLAYED
    }

    /** [다시] 누르기. 둘 다 누르면 뒤집는다 */
    fun pressAgain(p: Int): AgainResult {
        if (!stuck) return AgainResult.NOT_STUCK
        if (ready[p]) return AgainResult.ALREADY
        ready[p] = true
        if (!ready.all { it }) return AgainResult.WAITING
        flip()
        return AgainResult.FLIPPED
    }

    /** 각자 가운데 자기 쪽 더미에 한 장씩 뒤집기 */
    private fun flip() {
        ready.fill(false)
        flips++
        for (p in 0 until GameConfig.PLAYERS) {
            val own = draw[p]
            val card: Card?
            if (own.isNotEmpty()) {
                card = own.removeAt(own.size - 1)
                lastFlip[p] = FlipSource.OWN
            } else {
                card = takeReserve()
                if (card != null) {
                    lastFlip[p] = FlipSource.RESERVE
                } else {
                    val other = draw[1 - p]
                    if (other.isNotEmpty()) {
                        lastFlip[p] = FlipSource.OTHER
                        center[p].add(other.removeAt(other.size - 1))
                    } else {
                        lastFlip[p] = FlipSource.NONE
                    }
                    continue
                }
            }
            center[p].add(card)
        }
    }

    /** 가운데 더미 중 두꺼운 쪽의 밑바닥 카드 (맨 위는 남김) */
    private fun takeReserve(): Card? {
        val k = if (center[0].size >= center[1].size) 0 else 1
        return if (center[k].size >= 2) center[k].removeAt(0) else null
    }

    /** players 순서의 순위: 이긴 사람 1, 상대 2 */
    fun ranks(): IntArray = IntArray(GameConfig.PLAYERS) { if (it == winner) 1 else 2 }

    companion object {
        /** K(13) 와 A(1) 도 이어진다 */
        fun adjacent(a: Int, b: Int): Boolean {
            val d = abs(a - b)
            return d == 1 || d == 12
        }

        /** 52장을 섞어 나눈 새 판 (시작 뒤집기까지 끝난 상태) */
        fun deal(random: Random): SpeedGame {
            val deck = Deck.shuffled(random)
            val draw = Array(GameConfig.PLAYERS) { ArrayList<Card>(GameConfig.CARDS_PER_PLAYER) }
            val slots = Array(GameConfig.PLAYERS) { arrayOfNulls<Card>(GameConfig.SLOTS) }
            val center = Array(GameConfig.CENTER_PILES) { ArrayList<Card>(Card.STANDARD_COUNT) }
            for (p in 0 until GameConfig.PLAYERS) {
                val mine = deck.draw(GameConfig.CARDS_PER_PLAYER)
                for (i in 0 until GameConfig.SLOTS) slots[p][i] = mine[i]
                // 나머지가 뽑을 더미 (리스트 끝 = 맨 위)
                for (j in GameConfig.SLOTS until mine.size) draw[p].add(mine[j])
                // 시작 뒤집기: 자기 더미 맨 위 → 가운데 자기 쪽
                center[p].add(draw[p].removeAt(draw[p].size - 1))
            }
            return SpeedGame(draw, slots, center)
        }

        /**
         * 테스트·연출용 판 만들기.
         * slots[p] = 칸 4개(null = 빈 칸), drawTopFirst[p] = 뽑을 더미(첫 장이 맨 위), centerBottomFirst[k] = 가운데(마지막이 맨 위)
         */
        fun custom(slots: List<List<Card?>>, drawTopFirst: List<List<Card>>, centerBottomFirst: List<List<Card>>): SpeedGame {
            require(slots.size == GameConfig.PLAYERS && drawTopFirst.size == GameConfig.PLAYERS && centerBottomFirst.size == GameConfig.CENTER_PILES)
            val s = Array(GameConfig.PLAYERS) { p ->
                require(slots[p].size == GameConfig.SLOTS)
                Array(GameConfig.SLOTS) { slots[p][it] }
            }
            val d = Array(GameConfig.PLAYERS) { p -> ArrayList(drawTopFirst[p].asReversed()) }
            val c = Array(GameConfig.CENTER_PILES) { k -> ArrayList(centerBottomFirst[k]) }
            return SpeedGame(d, s, c)
        }
    }
}
