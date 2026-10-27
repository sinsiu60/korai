package com.siwoo.party.games.horserace

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 정해 둔 말 번호(0~5)를 차례로 내놓는 난수 (다 쓰면 seed 난수) */
class ScriptedRandom(vararg horses: Int, seed: Int = 1) : Random() {
    private val q = ArrayDeque(horses.toList())
    private val r = Random(seed)
    fun push(vararg h: Int) { q.addAll(h.toList()) }
    override fun nextBits(bitCount: Int): Int = r.nextBits(bitCount)
    override fun nextInt(from: Int, until: Int): Int = q.removeFirstOrNull() ?: r.nextInt(from, until)
}

class HorseRaceGameTest {

    // ── 배당 ──

    @Test fun `1착은 3배 2착은 1점5배 나머지는 0`() {
        assertEquals(30, Payout.of(10, 1))
        assertEquals(15, Payout.of(10, 2))
        assertEquals(0, Payout.of(10, 0))
        assertEquals(0, Payout.of(10, 3))
        assertEquals(300, Payout.of(100, 1))
        assertEquals(45, Payout.of(30, 2))
        assertEquals(75, Payout.of(50, 2))
        assertEquals(0, Payout.of(0, 1))
    }

    @Test fun `10단위 금액은 1점5배도 정확하고 혹시 홀수면 내림`() {
        for (stake in 10..300 step 10) assertEquals(stake * 3 / 2, Payout.of(stake, 2))
        for (stake in 10..300 step 10) assertEquals(0, Payout.of(stake, 2) * 2 - stake * 3)
        assertEquals(22, Payout.of(15, 2)) // 22.5 → 22
    }

    @Test fun `여러 말에 건 칩의 받는 칩 합`() {
        val bets = intArrayOf(0, 30, 0, 20, 10, 0)
        // 1착 1번말(30×3=90) + 2착 3번말(20×1.5=30) + 4번말 꽝
        assertEquals(120, Payout.total(bets, 1, 3))
        assertEquals(0, Payout.total(bets, 0, 5))
        assertEquals(45, Payout.total(bets, 5, 1))
    }

    // ── 베팅 검사 ──

    @Test fun `베팅은 10의 배수 남은 칩 이하 여러 마리 가능`() {
        val g = HorseRaceGame(2, Random(1))
        assertEquals(Phase.BETTING, g.phase)
        assertEquals(0, g.currentBettor)
        assertEquals(BetResult.NOT_UNIT, g.placeBet(2, 15))
        assertEquals(BetResult.NOT_UNIT, g.placeBet(2, 0))
        assertEquals(BetResult.NOT_UNIT, g.placeBet(2, -10))
        assertEquals(BetResult.BAD_HORSE, g.placeBet(6, 10))
        assertEquals(BetResult.BAD_HORSE, g.placeBet(-1, 10))
        assertEquals(BetResult.NOT_ENOUGH_CHIPS, g.placeBet(2, 110))
        assertEquals(100, g.chips[0])
        assertEquals(BetResult.OK, g.placeBet(2, 30))
        assertEquals(BetResult.OK, g.placeBet(4, 50))
        assertEquals(BetResult.OK, g.placeBet(2, 10)) // 같은 말에 더 걸면 더해짐
        assertEquals(10, g.chips[0])
        assertEquals(40, g.bets[0][2])
        assertEquals(50, g.bets[0][4])
        assertEquals(90, g.betTotal(0))
        assertEquals(BetResult.NOT_ENOUGH_CHIPS, g.placeBet(1, 20))
        assertEquals(BetResult.OK, g.placeBet(1, 10)) // 남은 칩 전부
        assertEquals(0, g.chips[0])
        assertEquals(BetResult.NOT_ENOUGH_CHIPS, g.placeBet(1, 10))
        // 차례가 아닌 사람은 못 건다
        assertEquals(BetResult.NOT_BETTING, g.validate(1, 0, 10))
    }

    @Test fun `무르기는 칩을 돌려주고 최소 10칩은 걸어야 차례를 마친다`() {
        val g = HorseRaceGame(3, Random(1))
        assertFalse(g.canFinishBetting())
        assertFalse(g.finishBetting())
        assertEquals(0, g.currentBettor)
        g.placeBet(0, 60)
        assertTrue(g.clearBets())
        assertEquals(100, g.chips[0])
        assertEquals(0, g.betTotal(0))
        assertFalse(g.clearBets())
        g.placeBet(5, 10)
        assertTrue(g.finishBetting())
        assertEquals(1, g.currentBettor)
        // 앞사람의 베팅은 이제 못 무른다 (지금 차례 사람 것만)
        assertFalse(g.clearBets())
        assertEquals(10, g.bets[0][5])
        g.placeBet(0, 10); g.finishBetting()
        g.placeBet(1, 10); g.finishBetting()
        assertEquals(Phase.RACING, g.phase)
        assertEquals(-1, g.currentBettor)
        assertEquals(BetResult.NOT_BETTING, g.placeBet(1, 10))
        assertEquals(30, g.horseTotal(0) + g.horseTotal(1) + g.horseTotal(5))
    }

    @Test fun `칩이 10 미만인 사람은 베팅 차례를 건너뛰고 구경한다`() {
        // 1레이스: 0번 말이 1착, 1번 말이 2착이 되게
        val rnd = ScriptedRandom(*IntArray(10) { 0 }, *IntArray(10) { 1 })
        val g = HorseRaceGame(3, rnd)
        g.placeBet(0, 10); g.finishBetting()       // 가: 0번 말 10
        g.placeBet(5, 100); g.finishBetting()      // 나: 5번 말 올인 → 0칩
        g.placeBet(1, 20); g.finishBetting()       // 다: 1번 말(2착) 20
        while (g.phase == Phase.RACING) g.raceStep()
        val r = g.lastResult!!
        assertEquals(0, r.first)
        assertEquals(1, r.second)
        assertArrayEquals(intArrayOf(120, 0, 110), g.chips)
        assertArrayEquals(intArrayOf(10, 100, 20), r.stake)
        assertArrayEquals(intArrayOf(30, 0, 30), r.payout)
        assertEquals(-100, r.net(1))
        assertFalse(g.canBet(1))
        assertFalse(g.nextRace())
        // 2레이스는 1번 사람부터지만 칩이 없어 건너뜀 → 2, 0
        assertEquals(listOf(2, 0), g.bettingOrder)
        assertEquals(2, g.currentBettor)
    }

    @Test fun `베팅 순서는 레이스마다 한 칸씩 돈다`() {
        val g = HorseRaceGame(4, Random(3))
        for (race in 1..3) {
            assertEquals(race, g.raceNumber)
            val start = (race - 1) % 4
            assertEquals(List(4) { (start + it) % 4 }, g.bettingOrder)
            repeat(4) { g.placeBet(it % 6, 10); g.finishBetting() }
            while (g.phase == Phase.RACING) g.raceStep()
            g.nextRace()
        }
    }

    // ── 레이스 ──

    @Test fun `10칸에 먼저 닿은 말이 1착 두 번째로 닿은 말이 2착`() {
        // 0번 말 9칸 → 1번 말 10칸(1착) → 이미 들어온 1번 말(헛굴림) → 0번 말 10칸(2착)
        val race = Race(ScriptedRandom(*IntArray(9) { 0 }, *IntArray(10) { 1 }, 1, 0))
        var last: RaceStep? = null
        var stretch = 0
        repeat(9) {
            last = race.step()
            if (last!!.comment == CommentKind.LAST_STRETCH) stretch++
        }
        assertEquals(1, stretch)
        assertEquals("1번 말 결승 코앞!", last!!.commentText)
        assertEquals(9, race.positions[0])
        assertEquals(0, race.soleLeader())
        repeat(9) { race.step() }
        assertEquals(9, race.positions[1])
        val f = race.step()
        assertEquals(2, f.roll)
        assertEquals(1, f.place)
        assertEquals(CommentKind.FIRST, f.comment)
        assertEquals("2번 말 1착!", f.commentText)
        assertFalse(race.isOver)
        val w = race.step()
        assertFalse(w.moved)
        assertEquals(CommentKind.WASTED, w.comment)
        assertEquals(10, race.positions[1])
        val s = race.step()
        assertEquals(2, s.place)
        assertEquals(CommentKind.SECOND, s.comment)
        assertTrue(race.isOver)
        assertEquals(1, race.first)
        assertEquals(0, race.second)
        assertEquals(1, race.placeOf(1))
        assertEquals(2, race.placeOf(0))
        assertEquals(0, race.placeOf(3))
        assertEquals(21, race.rolls)
    }

    @Test fun `같은 난수면 같은 레이스`() {
        for (seed in 1..30) {
            val a = Race(Random(seed)).runToEnd()
            val b = Race(Random(seed)).runToEnd()
            assertTrue(a.isOver)
            assertEquals(a.finishOrder, b.finishOrder)
            assertEquals(a.rolls, b.rolls)
            assertArrayEquals(a.positions, b.positions)
            // 1착·2착은 10칸, 나머지는 10칸 미만, 서로 다른 말
            assertEquals(GameConfig.TRACK, a.positions[a.first])
            assertEquals(GameConfig.TRACK, a.positions[a.second])
            assertTrue(a.first != a.second)
            for (h in 0 until GameConfig.HORSES) if (h != a.first && h != a.second) assertTrue(a.positions[h] < GameConfig.TRACK)
        }
    }

    @Test fun `해설 선두로 치고 나감과 따라붙음`() {
        val race = Race(ScriptedRandom(2, 2, 2, 4, 4, 4, 4))
        val s1 = race.step()
        assertEquals(CommentKind.TAKES_LEAD, s1.comment)
        assertTrue(s1.commentText.startsWith("3번 말"))
        assertEquals(CommentKind.NONE, race.step().comment) // 이미 선두
        race.step()
        race.step(); race.step()
        val tie = race.step() // 4번 → 3칸, 선두와 나란
        assertEquals(CommentKind.CATCHES_UP, tie.comment)
        assertEquals("5번 말 따라붙습니다!", tie.commentText)
        assertEquals(-1, race.soleLeader())
        val lead = race.step()
        assertEquals(CommentKind.TAKES_LEAD, lead.comment)
        assertEquals(4, race.soleLeader())
    }

    // ── 순위 ──

    @Test fun `5레이스 뒤 칩 순 순위 동점 허용`() {
        val g = HorseRaceGame(4, Random(9))
        var races = 0
        while (!g.gameOver) {
            assertEquals(Phase.BETTING, g.phase)
            // 모두 1번 말(0)에 최소 금액
            while (g.currentBettor >= 0) { assertEquals(BetResult.OK, g.placeBet(0, 10)); g.finishBetting() }
            while (g.phase == Phase.RACING) g.raceStep()
            races++
            g.nextRace()
        }
        assertEquals(GameConfig.RACES, races)
        assertFalse(g.endedEarly)
        // 모두 똑같이 걸었으니 칩이 같고 모두 공동 1등
        assertEquals(1, g.chips.distinct().size)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), g.finalRanks())
    }

    @Test fun `순위는 경쟁 순위`() {
        val g = HorseRaceGame(4, Random(1))
        g.chips[0] = 150; g.chips[1] = 40; g.chips[2] = 150; g.chips[3] = 90
        assertArrayEquals(intArrayOf(1, 4, 1, 3), g.ranks())
    }

    @Test fun `베팅할 사람이 1명 이하면 일찍 끝난다`() {
        val rnd = ScriptedRandom(*IntArray(10) { 0 }, *IntArray(10) { 1 })
        val g = HorseRaceGame(2, rnd)
        g.placeBet(0, 100); g.finishBetting()  // 1착 → 300
        g.placeBet(4, 100); g.finishBetting()  // 꽝 → 0
        while (g.phase == Phase.RACING) g.raceStep()
        assertArrayEquals(intArrayOf(300, 0), g.chips)
        assertTrue(g.nextRace())
        assertTrue(g.gameOver)
        assertTrue(g.endedEarly)
        assertEquals(1, g.raceNumber)
        assertArrayEquals(intArrayOf(1, 2), g.finalRanks())
    }

    @Test fun `칩 총량은 베팅과 배당만큼만 바뀐다`() {
        val g = HorseRaceGame(3, Random(77))
        while (!g.gameOver) {
            val before = g.chips.copyOf()
            while (g.currentBettor >= 0) {
                val p = g.currentBettor
                g.placeBet(p % 6, 10)
                if (g.chips[p] >= 20) g.placeBet((p + 3) % 6, 20)
                g.finishBetting()
            }
            while (g.phase == Phase.RACING) g.raceStep()
            val r = g.lastResult!!
            for (p in 0 until 3) assertEquals(before[p] - r.stake[p] + r.payout[p], g.chips[p])
            g.nextRace()
        }
    }
}
