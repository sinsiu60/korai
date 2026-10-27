package com.siwoo.party.games.onecard

import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OneCardGameTest {

    private fun s(r: Int) = Card.of(Suit.SPADE, r)
    private fun h(r: Int) = Card.of(Suit.HEART, r)
    private fun d(r: Int) = Card.of(Suit.DIAMOND, r)
    private fun c(r: Int) = Card.of(Suit.CLUB, r)
    private val bj = Card.BLACK_JOKER
    private val rj = Card.RED_JOKER

    /** 손패를 길게 채워 원카드 단계가 끼어들지 않게 */
    private val filler = listOf(d(3), d(6), d(10), d(12))

    @Test fun `처음에는 7장씩 나누고 첫 카드를 펼친다`() {
        val g = OneCardGame(4, Random(1))
        for (p in 0 until 4) assertEquals(7, g.hands[p].size)
        assertEquals(1, g.discard.size)
        assertEquals(54 - 28 - 1, g.deck.size)
        assertEquals(54, g.totalCards)
        assertEquals(0, g.current)
        assertEquals(Phase.PLAY, g.phase)
        assertEquals(g.top.suit, g.activeSuit)
        // 손패 정렬 (id 순)
        val ids = g.hands[0].map { it.id }
        assertEquals(ids.sorted(), ids)
    }

    @Test fun `같은 무늬 같은 숫자 조커만 낼 수 있다`() {
        val g = OneCardGame.custom(listOf(listOf(s(5), h(9), d(9), c(4), bj) + filler, filler), top = h(5))
        assertTrue("같은 숫자", g.canPlay(s(5)))
        assertTrue("같은 무늬", g.canPlay(h(9)))
        assertFalse("무늬·숫자 다름", g.canPlay(d(9)))
        assertFalse(g.canPlay(c(4)))
        assertTrue("조커는 언제나", g.canPlay(bj))
        assertEquals(listOf(s(5), h(9), bj), g.playableIndices().map { g.hands[0][it] })
        // 맨 위가 조커 (공격 끝) → 아무 카드나
        val g2 = OneCardGame.custom(listOf(listOf(c(4)) + filler, filler), top = rj, activeSuit = null)
        assertTrue(g2.canPlay(c(4)))
        assertTrue(g2.canPlay(d(13)))
        // 다른 사람 차례에는 낼 카드 없음
        assertTrue(g.playableIndices(1).isEmpty())
    }

    @Test fun `공격은 같거나 센 공격 카드로 막고 장수가 쌓인다`() {
        // 3명: 0 이 ♠2 → 1 이 ♠A 로 막음 → 2 가 조커로 막음 → 0 은 못 막고 10장
        val deck = List(12) { Card.of(Suit.CLUB, it % 13 + 1) }
        val g = OneCardGame.custom(
            listOf(listOf(s(2), h(10)) + filler, listOf(s(1), h(1), h(2)) + filler, listOf(bj, s(3)) + filler),
            top = s(9), deckTopFirst = deck,
        )
        val p1 = g.play(s(2))
        assertEquals(Effect.ATTACK, p1.effect)
        assertEquals(2, p1.attackTotal)
        assertEquals(1, g.current)
        // 1 의 방어: ♠2 위 → ♥2(같은 숫자)·♠A(같은 무늬, 더 셈) 가능, ♥A(무늬·숫자 다름) 불가, 일반 카드 불가
        assertTrue(g.canPlay(h(2)))
        assertTrue(g.canPlay(s(1)))
        assertFalse(g.canPlay(h(1)))
        assertFalse(g.canPlay(d(3)))
        g.play(s(1))
        assertEquals(5, g.pendingAttack)
        assertEquals(2, g.current)
        // ♠A 위: 2 는 약해서 불가, 조커 가능
        assertFalse(g.canPlay(s(2)))
        assertFalse(g.canPlay(s(3)))
        assertTrue(g.canPlay(bj))
        g.play(bj)
        assertEquals(10, g.pendingAttack)
        assertEquals(0, g.current)
        // 조커 공격: 조커만 막을 수 있음
        assertFalse(g.canPlay(h(10)))
        assertFalse(g.canPlay(s(1)))
        assertTrue(g.canPlay(rj))
        val before = g.hands[0].size
        val dr = g.draw()
        assertTrue(dr.wasAttack)
        assertEquals(10, dr.requested)
        assertEquals(10, dr.cards.size)
        assertEquals(before + 10, g.hands[0].size)
        assertEquals(0, g.pendingAttack)
        assertEquals("뽑으면 차례 끝", 1, g.current)
        // 공격이 끝난 조커 위 → 아무 카드나
        assertNull(g.activeSuit)
        assertTrue(g.canPlay(h(1)))
    }

    @Test fun `2 공격은 A 로 넘기고 A 공격은 2 로 못 넘긴다`() {
        val g = OneCardGame.custom(listOf(listOf(h(1)) + filler, listOf(h(2), c(1)) + filler), top = h(7), pendingAttack = 0)
        g.play(h(1))
        assertEquals(1, g.current)
        assertFalse("A 공격에 2 불가", g.canPlay(h(2)))
        assertTrue("같은 숫자 A", g.canPlay(c(1)))
        val g2 = OneCardGame.custom(listOf(listOf(h(2)) + filler, listOf(h(1), c(2), c(1)) + filler), top = h(7))
        g2.play(h(2))
        assertTrue("2 공격에 같은 무늬 A", g2.canPlay(h(1)))
        assertTrue("2 공격에 같은 숫자 2", g2.canPlay(c(2)))
        assertFalse("2 공격에 다른 무늬 A", g2.canPlay(c(1)))
    }

    @Test fun `J 는 다음 사람 건너뛰기`() {
        val g = OneCardGame.custom(listOf(listOf(h(11)) + filler, filler, filler), top = h(4))
        val r = g.play(h(11))
        assertEquals(Effect.SKIP, r.effect)
        assertEquals("1 을 건너뛰고 2", 2, g.current)
        // 2명: J 를 낸 사람이 한 번 더
        val g2 = OneCardGame.custom(listOf(listOf(h(11)) + filler, filler), top = h(4))
        g2.play(h(11))
        assertEquals(0, g2.current)
        assertEquals(Phase.PLAY, g2.phase)
    }

    @Test fun `Q 는 방향 바꾸기`() {
        val g = OneCardGame.custom(listOf(listOf(h(12)) + filler, filler, filler, listOf(h(3)) + filler), top = h(4))
        g.play(h(12))
        assertEquals(-1, g.direction)
        assertEquals("거꾸로 → 3", 3, g.current)
        g.play(h(3))
        assertEquals(2, g.current)
        // 2명: 방향만 바뀌고 상대 차례 (효과 없음과 같다)
        val g2 = OneCardGame.custom(listOf(listOf(h(12)) + filler, filler), top = h(4))
        g2.play(h(12))
        assertEquals(-1, g2.direction)
        assertEquals(1, g2.current)
    }

    @Test fun `K 는 한 번 더`() {
        val g = OneCardGame.custom(listOf(listOf(h(13), s(13)) + filler, filler, filler), top = h(4))
        val r = g.play(h(13))
        assertEquals(Effect.AGAIN, r.effect)
        assertEquals(0, g.current)
        g.play(s(13))
        assertEquals(0, g.current)
        // 낼 카드가 없으면 1장 뽑고 끝
        val g2 = OneCardGame.custom(listOf(listOf(h(13), c(9)) + filler, filler, filler), top = h(4), deckTopFirst = listOf(c(2)))
        g2.play(h(13))
        assertFalse(g2.hasPlayable())
        val dr = g2.draw()
        assertEquals(listOf(c(2)), dr.cards)
        assertEquals(1, g2.current)
    }

    @Test fun `7 은 무늬 바꾸기`() {
        val g = OneCardGame.custom(listOf(listOf(h(7)) + filler, listOf(c(9), h(9), s(7), bj) + filler), top = h(4))
        val r = g.play(h(7))
        assertEquals(Effect.CHANGE_SUIT, r.effect)
        assertEquals(Phase.CHOOSE_SUIT, g.phase)
        assertEquals("무늬를 고를 때까지 같은 사람", 0, g.current)
        g.chooseSuit(Suit.CLUB)
        assertEquals(Suit.CLUB, g.activeSuit)
        assertEquals(1, g.current)
        assertTrue(g.canPlay(c(9)))
        assertFalse("원래 무늬는 안 됨", g.canPlay(h(9)))
        assertTrue("숫자 7 은 가능", g.canPlay(s(7)))
        assertTrue(g.canPlay(bj))
    }

    @Test fun `원카드를 제때 누르면 벌칙 없음`() {
        val g = OneCardGame.custom(listOf(listOf(h(5), c(9)), filler), top = h(4), deckTopFirst = listOf(d(10), d(11)))
        g.play(h(5))
        assertEquals(Phase.ONE_CARD, g.phase)
        assertEquals(0, g.current)
        g.declareOneCard()
        assertEquals(1, g.hands[0].size)
        assertEquals(1, g.current)
        assertEquals(2, g.deck.size)
    }

    @Test fun `원카드를 못 누르면 1장 추가`() {
        val g = OneCardGame.custom(listOf(listOf(h(5), c(9)), filler), top = h(4), deckTopFirst = listOf(d(10), d(11)))
        g.play(h(5))
        val dr = g.oneCardTimeout()
        assertEquals(listOf(d(10)), dr.cards)
        assertEquals(2, g.hands[0].size)
        assertEquals(1, g.current)
        assertEquals(Phase.PLAY, g.phase)
    }

    @Test fun `K 로 1장 남으면 원카드 후 한 번 더`() {
        val g = OneCardGame.custom(listOf(listOf(h(13), h(3)), filler), top = h(4))
        g.play(h(13))
        assertEquals(Phase.ONE_CARD, g.phase)
        g.declareOneCard()
        assertEquals(0, g.current)
        val r = g.play(h(3))
        assertEquals(1, r.rank)
        assertTrue(g.isOver)
    }

    @Test fun `뽑을 더미가 떨어지면 버린 더미를 섞어 쓴다`() {
        val below = listOf(c(2), c(3), c(4), c(5), c(6))
        val g = OneCardGame.custom(listOf(listOf(s(9)) + filler, filler), top = h(8), deckTopFirst = listOf(d(12)), discardBelow = below)
        assertEquals(1 + 5, g.drawableCount)
        g.draw() // 덱 마지막 1장
        assertEquals(0, g.deck.size)
        assertEquals(1, g.current)
        // 덱이 비었으니 버린 더미(맨 위 제외 5장)를 섞어 채운 뒤 1장
        val dr = g.draw()
        assertEquals(1, dr.refilled)
        assertEquals(1, g.refills)
        assertEquals("맨 위 카드는 그대로", h(8), g.top)
        assertEquals(1, g.discard.size)
        assertEquals(4, g.deck.size)
        assertTrue(dr.cards.single() in below)
    }

    @Test fun `더미가 모두 비면 있는 만큼만 뽑고 모두 못 내면 손패 수로 끝`() {
        // 덱·버린 더미 모두 없음, 둘 다 낼 카드 없음
        val g = OneCardGame.custom(listOf(listOf(c(3), c(4)) + filler, listOf(c(5))), top = h(8), activeSuit = Suit.HEART)
        // 뽑을 게 없으면 0장 뽑고 차례만 넘어감
        val dr = g.draw()
        assertTrue(dr.cards.isEmpty())
        assertFalse(g.isOver)
        assertEquals(1, g.current)
        g.draw()
        assertTrue(g.isOver)
        assertTrue(g.stalled)
        assertArrayEquals("손패 적은 1 이 1등", intArrayOf(2, 1), g.finalRanks())
        // 뽑을 카드가 없을 때 낼 카드가 있으면 내야 한다
        val g2 = OneCardGame.custom(listOf(listOf(h(5)) + filler, filler), top = h(8))
        assertEquals(0, g2.drawableCount)
        assertFalse(g2.canDraw)
        val g3 = OneCardGame.custom(listOf(listOf(h(5)) + filler, filler), top = h(8), deckTopFirst = listOf(c(2)))
        assertTrue("더미가 있으면 낼 카드가 있어도 뽑기 가능", g3.canDraw)
    }

    @Test fun `다 낸 순서대로 순위 마지막 1명은 꼴찌`() {
        val g = OneCardGame.custom(
            listOf(listOf(h(5), h(6)), listOf(h(9)), listOf(h(10), h(3), c(3), c(4))),
            top = h(4),
        )
        g.play(h(5)); g.declareOneCard() // 0: 1장
        assertEquals(1, g.current)
        val r1 = g.play(h(9)) // 1: 다 냄 → 1등
        assertEquals(1, r1.rank)
        assertTrue(g.isFinished(1))
        assertEquals(2, g.current)
        g.play(h(10))
        assertEquals("1 은 건너뜀", 0, g.current)
        val r2 = g.play(h(6))
        assertEquals(2, r2.rank)
        assertTrue(g.isOver)
        assertEquals(listOf(1, 0, 2), g.finishOrder)
        assertArrayEquals(intArrayOf(2, 1, 3), g.finalRanks())
    }

    @Test fun `마지막 카드가 공격이면 다음 사람이 받는다`() {
        val g = OneCardGame.custom(listOf(listOf(h(2)), filler, filler), top = h(4))
        val r = g.play(h(2))
        assertEquals(1, r.rank)
        assertEquals(1, g.current)
        assertEquals(2, g.pendingAttack)
        assertFalse(g.isOver)
        // 마지막 J: 나간 사람 다음(1)을 건너뛰고 2
        val g2 = OneCardGame.custom(listOf(listOf(h(11)), filler, filler), top = h(4))
        g2.play(h(11))
        assertEquals(2, g2.current)
        // 마지막 K: 나갔으니 다음 사람
        val g3 = OneCardGame.custom(listOf(listOf(h(13)), filler, filler), top = h(4))
        g3.play(h(13))
        assertEquals(1, g3.current)
    }

    @Test fun `낼 수 없는 카드는 거절`() {
        val g = OneCardGame.custom(listOf(listOf(c(9)) + filler, filler), top = h(4))
        try {
            g.play(c(9)); error("거절돼야 함")
        } catch (e: IllegalArgumentException) {
            assertEquals(0, g.current)
        }
    }

    /** 무작위로 끝까지: 항상 끝나고 카드 54장이 보존되며 순위가 올바르다 */
    @Test fun `무작위 판 시뮬레이션은 끝나고 카드가 보존된다`() {
        var stalls = 0
        var refills = 0
        var maxHand = 0
        for (seed in 0 until 400) {
            val n = 2 + seed % 3
            val rnd = Random(seed * 31 + 7)
            val g = OneCardGame(n, Random(seed))
            var steps = 0
            while (!g.isOver) {
                steps++
                check(steps < 20_000) { "seed $seed 끝나지 않음" }
                when (g.phase) {
                    Phase.PLAY -> {
                        val ok = g.playableIndices()
                        // 가끔은 낼 수 있어도 뽑는다 (사람처럼)
                        if (ok.isNotEmpty() && (rnd.nextInt(10) != 0 || !g.canDraw)) g.play(g.hands[g.current][ok[rnd.nextInt(ok.size)]])
                        else g.draw()
                    }
                    Phase.CHOOSE_SUIT -> g.chooseSuit(Suit.values()[rnd.nextInt(4)])
                    Phase.ONE_CARD -> if (rnd.nextBoolean()) g.declareOneCard() else g.oneCardTimeout()
                    Phase.OVER -> Unit
                }
                assertEquals("seed $seed 카드 보존", 54, g.totalCards)
                for (p in 0 until n) if (g.isFinished(p) && !g.isOver) assertEquals(0, g.hands[p].size)
                for (p in 0 until n) maxHand = maxOf(maxHand, g.hands[p].size)
            }
            if (g.stalled) stalls++
            refills += g.refills
            val ranks = g.finalRanks()
            assertEquals(n, ranks.size)
            if (!g.stalled) {
                assertEquals((1..n).toList(), ranks.sorted())
                assertEquals(n, g.finishOrder.size)
                assertEquals(1, ranks[g.finishOrder[0]])
                assertEquals(n, ranks[g.finishOrder[n - 1]])
            } else {
                assertTrue(ranks.all { it in 1..n })
            }
        }
        assertTrue("재셔플이 실제로 일어남", refills > 0)
        assertTrue("손패 수로 끝나는 판은 드물다: $stalls", stalls < 20)
        println("시뮬레이션: 재셔플 $refills, 손패 수 종료 $stalls, 최대 손패 $maxHand")
    }
}
