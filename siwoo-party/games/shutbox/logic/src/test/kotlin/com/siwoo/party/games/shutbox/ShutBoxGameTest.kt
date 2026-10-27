package com.siwoo.party.games.shutbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 정해 둔 눈을 차례로 내는 난수 (다 쓰면 seed 난수) */
class ScriptedDice(vararg faces: Int, seed: Int = 1) : Random() {
    private val q = ArrayDeque(faces.toList())
    private val r = Random(seed)
    fun push(vararg f: Int) { q.addAll(f.toList()) }
    override fun nextBits(bitCount: Int): Int = r.nextBits(bitCount)
    override fun nextInt(from: Int, until: Int): Int = q.removeFirstOrNull() ?: r.nextInt(from, until)
}

class ShutBoxGameTest {

    private fun mask(vararg ns: Int): Int {
        var m = 0
        for (n in ns) m = m or ShutMath.bit(n)
        return m
    }

    private fun tiles(m: Int): List<Int> = (1..9).filter { ShutMath.has(m, it) }

    /** 숫자들을 골라 닫기 */
    private fun shut(g: ShutBoxGame, vararg ns: Int): ShutResult {
        for (n in ns) assertTrue("고르기 $n", g.toggle(n))
        assertTrue(g.canShut)
        return g.shut()!!
    }

    // ── 합 조합 유효성 ──

    @Test
    fun `조합 존재 여부 - 부분합`() {
        val all = ShutMath.FULL
        assertEquals(45, ShutMath.sumOf(all))
        for (t in 1..45) assertTrue("모두 열림 $t", ShutMath.hasCombination(all, t))
        assertFalse(ShutMath.hasCombination(all, 46))
        assertFalse(ShutMath.hasCombination(all, 0))
        // 2, 5 만 열림: 2, 5, 7 만 가능
        val m = mask(2, 5)
        assertTrue(ShutMath.hasCombination(m, 2))
        assertTrue(ShutMath.hasCombination(m, 5))
        assertTrue(ShutMath.hasCombination(m, 7))
        for (t in listOf(1, 3, 4, 6, 8, 9, 10, 12)) assertFalse("2·5 로 $t 불가", ShutMath.hasCombination(m, t))
        // 아무것도 안 열림
        assertFalse(ShutMath.hasCombination(0, 3))
        // 1, 3, 9 열림: 12 = 3+9, 13 = 1+3+9, 11 불가
        val k = mask(1, 3, 9)
        assertTrue(ShutMath.hasCombination(k, 12))
        assertTrue(ShutMath.hasCombination(k, 13))
        assertFalse(ShutMath.hasCombination(k, 11))
        assertFalse(ShutMath.hasCombination(k, 8))
    }

    @Test
    fun `고른 숫자 유효성`() {
        val open = mask(1, 2, 3, 4, 5, 6)
        assertTrue(ShutMath.isValidSelection(open, mask(3, 6), 9))
        assertTrue(ShutMath.isValidSelection(open, mask(2, 3, 4), 9))
        assertFalse("합이 다름", ShutMath.isValidSelection(open, mask(3, 5), 9))
        assertFalse("닫힌 숫자 포함", ShutMath.isValidSelection(open, mask(9), 9))
        assertFalse("빈 선택", ShutMath.isValidSelection(open, 0, 9))
        assertFalse("숫자판 밖 비트", ShutMath.isValidSelection(open or 1, 1 or mask(3, 6), 9))
    }

    @Test
    fun `강조 숫자 = 유효 조합에 하나라도 들어가는 숫자`() {
        // 열림 1,2,3,7,8 / 합 8: {8}, {1,7}, {1,2,... 1+2+... 1+7, 8} → 1,7,8 (2+3+... 2+3=5 아님, 1+2+... 없음)
        val open = mask(1, 2, 3, 7, 8)
        assertEquals(mask(1, 7, 8), ShutMath.highlightMask(open, 8))
        // 합 6: {1,2,3} 만 → 1,2,3
        assertEquals(mask(1, 2, 3), ShutMath.highlightMask(open, 6))
        // 합 4: {1,3} → 1,3
        assertEquals(mask(1, 3), ShutMath.highlightMask(open, 4))
        // 합 14: 없음
        assertEquals(0, ShutMath.highlightMask(mask(1, 2, 3), 14))
        // 모두 열림 / 합 2: {2} 만 (1+1 은 안 됨)
        assertEquals(mask(2), ShutMath.highlightMask(ShutMath.FULL, 2))
        // 모두 열림 / 합 3: {3}, {1,2}
        assertEquals(mask(1, 2, 3), ShutMath.highlightMask(ShutMath.FULL, 3))
        // 무작위 열림 상태에서 브루트포스와 같은지
        val r = Random(5)
        repeat(300) {
            val o = r.nextInt(0, 1024) and ShutMath.FULL
            val t = r.nextInt(1, 13)
            var expected = 0
            for (sub in 1 until 1024) if ((sub and o) == sub && sub and 1 == 0 && ShutMath.sumOf(sub) == t) expected = expected or sub
            assertEquals("open=${tiles(o)} t=$t", expected, ShutMath.highlightMask(o, t))
            assertEquals(expected != 0, ShutMath.hasCombination(o, t))
            // 조합 하나를 찾으면 유효
            val any = ShutMath.anyCombination(o, t)
            assertEquals(expected != 0, any != 0)
            if (any != 0) assertTrue(ShutMath.isValidSelection(o, any, t))
        }
    }

    @Test
    fun `고른 것에 더할 수 있는 숫자 강조`() {
        // 모두 열림, 합 9, 6 을 고름 → 나머지 3 = {3}, {1,2}
        assertEquals(mask(1, 2, 3), ShutMath.extendMask(ShutMath.FULL, mask(6), 9))
        // 이미 합을 맞춤 → 더 없음
        assertEquals(0, ShutMath.extendMask(ShutMath.FULL, mask(4, 5), 9))
        // 5 를 골랐는데 나머지 4 를 1,2 로는 못 만듦
        assertEquals(0, ShutMath.extendMask(mask(1, 2, 5), mask(5), 9))
    }

    // ── 차례 진행 ──

    @Test
    fun `굴리고 고르고 닫기`() {
        val g = ShutBoxGame(2, ScriptedDice(4, 5))
        assertEquals(Phase.ROLL, g.phase)
        assertFalse("굴리기 전엔 못 고름", g.toggle(9))
        val r = g.roll()!!
        assertEquals(9, r.total)
        assertTrue(r.hasMove)
        assertArrayEquals(intArrayOf(4, 5), r.dice)
        assertEquals(Phase.SELECT, g.phase)
        assertNull("다시 굴리기 불가", g.roll())
        assertFalse(g.canShut)
        assertTrue(g.toggle(6))
        assertFalse("6+4 = 10 > 9 → 거절", g.toggle(4))
        assertEquals(6, g.selectedSum)
        assertFalse(g.canShut)
        assertNull(g.shut())
        assertTrue(g.toggle(3))
        assertEquals(9, g.selectedSum)
        assertTrue(g.canShut)
        // 풀었다 다시
        assertTrue(g.toggle(3))
        assertFalse(g.canShut)
        assertTrue(g.toggle(3))
        val s = g.shut()!!
        assertEquals(mask(3, 6), s.closed)
        assertNull(s.turnEnd)
        assertFalse(g.isOpen(3))
        assertFalse(g.isOpen(6))
        assertEquals(36, g.openSum)
        assertEquals(Phase.ROLL, g.phase)
        assertEquals(0, g.selected)
        assertFalse("닫힌 숫자는 못 고름", g.toggle(6))
    }

    @Test
    fun `주사위 1개는 열린 합 6 이하일 때만`() {
        val g = ShutBoxGame(2, ScriptedDice(6, 6, 6, 6, 5, 6, 4, 3, 3))
        assertFalse(g.canUseOneDie)
        assertNull("열린 합 45 → 1개 불가", g.roll(1))
        assertNull("3개는 없음", g.roll(3))
        assertNull("0개는 없음", g.roll(0))
        g.roll(); shut(g, 9, 3)          // 12 → 열린 합 33
        g.roll(); shut(g, 8, 4)          // 12 → 21
        assertFalse(g.canUseOneDie)
        g.roll(); shut(g, 6, 5)          // 11 → 1,2,7 = 10
        assertEquals(10, g.openSum)
        assertFalse(g.canUseOneDie)
        assertNull(g.roll(1))
        g.roll(); shut(g, 7)             // 7 → 1,2 = 3
        assertEquals(3, g.openSum)
        assertTrue(g.canUseOneDie)
        val r = g.roll(1)!!
        assertEquals(1, g.diceCount)
        assertEquals(1, r.dice.size)
        assertEquals(3, r.total)
        assertFalse("고르는 중엔 1개 굴리기 표시도 꺼짐", g.canUseOneDie)
        val s = shut(g, 1, 2)
        assertTrue(s.turnEnd!!.perfect)
    }

    @Test
    fun `열린 합이 정확히 6 이면 1개 가능 7 이면 불가`() {
        // 12=8+4, 12=7+5, 9=9, 6=6 → 1,2,3 (합 6)
        val g = ShutBoxGame(2, ScriptedDice(6, 6, 6, 6, 4, 5, 3, 3, 6))
        g.roll(); shut(g, 8, 4)
        g.roll(); shut(g, 7, 5)
        g.roll(); shut(g, 9)
        g.roll(); shut(g, 6)
        assertEquals(6, g.openSum)
        assertTrue(g.canUseOneDie)
        // 열린 합 7 (7 하나) → 1개 불가
        val h = ShutBoxGame(2, ScriptedDice(6, 6, 6, 6, 5, 6, 1, 2, 3, 4))
        h.roll(); shut(h, 9, 3)
        h.roll(); shut(h, 8, 4)
        h.roll(); shut(h, 6, 5)
        h.roll(); shut(h, 1, 2)          // 3 → 7 만 남음
        assertEquals(7, h.openSum)
        assertFalse(h.canUseOneDie)
        assertNull(h.roll(1))
        assertEquals(7, h.roll(2)!!.total)
        assertTrue(shut(h, 7).turnEnd!!.perfect)
    }

    // 정해진 차례 하나 (주사위를 넣고 끝까지)
    /** 12=9+3, 12=8+4, 12=7+5 → 1,2,6 남고 12 → 못 닫음: 9점 */
    private fun turn9(g: ShutBoxGame, d: ScriptedDice): TurnEnd {
        d.push(6, 6, 6, 6, 6, 6, 6, 6)
        g.roll(); shut(g, 9, 3)
        g.roll(); shut(g, 8, 4)
        g.roll(); shut(g, 7, 5)
        val r = g.roll()!!
        assertFalse(r.hasMove)
        return g.lastTurnEnd!!
    }

    /** 12=9+3, 12=8+4, 12=7+5, 9=6+2+1 → 완벽 0점 */
    private fun turnPerfect(g: ShutBoxGame, d: ScriptedDice): TurnEnd {
        d.push(6, 6, 6, 6, 6, 6, 4, 5)
        g.roll(); shut(g, 9, 3)
        g.roll(); shut(g, 8, 4)
        g.roll(); shut(g, 7, 5)
        g.roll()
        return shut(g, 6, 2, 1).turnEnd!!
    }

    /** 12=9+3, 12=8+4, 2=2 → 1,5,6,7 남고 2 → 못 닫음: 19점 */
    private fun turn19(g: ShutBoxGame, d: ScriptedDice): TurnEnd {
        d.push(6, 6, 6, 6, 1, 1, 1, 1)
        g.roll(); shut(g, 9, 3)
        g.roll(); shut(g, 8, 4)
        g.roll(); shut(g, 2)
        assertFalse(g.roll()!!.hasMove)
        return g.lastTurnEnd!!
    }

    @Test
    fun `조합이 없으면 차례 끝 - 남은 합이 점수`() {
        val d = ScriptedDice()
        val g = ShutBoxGame(2, d)
        val p = g.current
        val e = turn9(g, d)
        assertEquals(Phase.TURN_OVER, g.phase)
        assertEquals(TurnEndReason.NO_MOVE, e.reason)
        assertFalse(e.perfect)
        assertEquals(9, e.score)
        assertEquals(p, e.player)
        assertEquals(1, e.round)
        assertEquals(9, g.score(p, 1))
        // 끝난 차례에서는 아무것도 못 함
        assertNull(g.roll())
        assertFalse(g.toggle(1))
        assertNull(g.shut())
        assertFalse(g.canUseOneDie)
        // 다음 사람: 새 상자
        assertTrue(g.advance())
        assertFalse("두 번 넘기기 불가", g.advance())
        assertEquals(1, g.current)
        assertEquals(Phase.ROLL, g.phase)
        assertEquals(ShutMath.FULL, g.open)
        assertEquals(45, g.openSum)
        assertEquals(ShutBoxGame.EMPTY, g.score(1, 1))
    }

    @Test
    fun `조합 없음 - 합은 남은 합 이하지만 만들 수 없는 경우`() {
        // 1,2,6 남기고 5 (2+3) → 1+2=3, 6, 7, 8, 9 뿐 → 못 닫음
        val g = ShutBoxGame(2, ScriptedDice(6, 6, 6, 6, 6, 6, 2, 3))
        g.roll(); shut(g, 9, 3)
        g.roll(); shut(g, 8, 4)
        g.roll(); shut(g, 7, 5)
        val r = g.roll()!!
        assertEquals(5, r.total)
        assertFalse(r.hasMove)
        assertEquals(0, g.comboMask)
        assertEquals(9, g.lastTurnEnd!!.score)
    }

    @Test
    fun `모두 닫으면 0점 완벽`() {
        val d = ScriptedDice()
        val g = ShutBoxGame(3, d)
        val e = turnPerfect(g, d)
        assertTrue(e.perfect)
        assertEquals(TurnEndReason.PERFECT, e.reason)
        assertEquals(0, e.score)
        assertEquals(0, g.open)
        assertEquals(0, g.score(0, 1))
        assertEquals(Phase.TURN_OVER, g.phase)
        assertEquals(0, g.total(0))
    }

    @Test
    fun `고르는 중 강조 마스크`() {
        val g = ShutBoxGame(2, ScriptedDice(1, 2))
        assertEquals("굴리기 전엔 강조 없음", 0, g.comboMask)
        g.roll() // 3 → {3}, {1,2}
        assertEquals(mask(1, 2, 3), g.comboMask)
        assertEquals(mask(1, 2, 3), g.hintMask)
        g.toggle(1)
        assertEquals("1 을 고르면 2 만 더 가능", mask(2), g.hintMask)
        assertEquals(mask(1, 2, 3), g.comboMask)
        assertFalse("1+3 > 3 거절", g.toggle(3))
        g.toggle(2)
        assertEquals(0, g.hintMask)
        assertTrue(g.canShut)
        g.clearSelection()
        assertEquals(0, g.selected)
    }

    @Test
    fun `라운드마다 먼저 하는 사람이 돈다`() {
        val g4 = ShutBoxGame(4)
        assertEquals(listOf(0, 1, 2, 3), (0 until 4).map { g4.playerAt(1, it) })
        assertEquals(listOf(1, 2, 3, 0), (0 until 4).map { g4.playerAt(2, it) })
        assertEquals(listOf(2, 3, 0, 1), (0 until 4).map { g4.playerAt(3, it) })
        val g2 = ShutBoxGame(2)
        assertEquals(listOf(0, 1, 0), (1..3).map { g2.startingPlayer(it) })
        // 실제 진행 순서도 같다
        val d = ScriptedDice()
        val g = ShutBoxGame(3, d)
        val order = ArrayList<Int>()
        while (!g.gameOver) {
            order.add(g.current)
            turn9(g, d)
            g.advance()
        }
        assertEquals(listOf(0, 1, 2, 1, 2, 0, 2, 0, 1), order)
    }

    @Test
    fun `3라운드 합계와 순위 - 낮을수록 1등`() {
        val d = ScriptedDice()
        val g = ShutBoxGame(3, d)
        // 1라운드 (0,1,2): 9, 0, 19
        assertEquals(0, g.current); turn9(g, d); g.advance()
        assertEquals(1, g.current); turnPerfect(g, d); g.advance()
        assertEquals(2, g.current); turn19(g, d)
        assertTrue(g.advance())
        assertEquals(2, g.round)
        assertTrue(g.isRoundStart)
        // 2라운드 (1,2,0): 19, 9, 0
        turn19(g, d); g.advance()
        turn9(g, d); g.advance()
        turnPerfect(g, d); g.advance()
        assertEquals(3, g.round)
        // 3라운드 (2,0,1): 0, 9, 19
        turnPerfect(g, d); g.advance()
        turn9(g, d); g.advance()
        assertEquals(Phase.TURN_OVER, turn19(g, d).let { g.phase })
        assertFalse(g.gameOver)
        assertTrue(g.advance())
        assertTrue(g.gameOver)
        assertFalse(g.advance())
        assertNull(g.roll())
        assertEquals(listOf(9, 0, 9), (1..3).map { g.score(0, it) })
        assertEquals(listOf(0, 19, 19), (1..3).map { g.score(1, it) })
        assertEquals(listOf(19, 9, 0), (1..3).map { g.score(2, it) })
        assertEquals(18, g.total(0))
        assertEquals(38, g.total(1))
        assertEquals(28, g.total(2))
        assertArrayEquals(intArrayOf(1, 3, 2), g.finalRanks())
    }

    @Test
    fun `동점은 공동 순위`() {
        val d = ScriptedDice()
        val g = ShutBoxGame(2, d)
        // 1R: 9, 9 / 2R: (1,0) 0, 0 / 3R: 19, 19
        repeat(2) { turn9(g, d); g.advance() }
        repeat(2) { turnPerfect(g, d); g.advance() }
        repeat(2) { turn19(g, d); g.advance() }
        assertTrue(g.gameOver)
        assertEquals(28, g.total(0))
        assertEquals(28, g.total(1))
        assertArrayEquals(intArrayOf(1, 1), g.finalRanks())
    }

    @Test
    fun `무작위 판을 끝까지 - 점수 일관성`() {
        for (n in 2..4) for (seed in 1..20) {
            val g = ShutBoxGame(n, Random(seed * 31 + n))
            var turns = 0
            var perfects = 0
            while (!g.gameOver) {
                when (g.phase) {
                    Phase.ROLL -> {
                        val one = g.canUseOneDie && seed % 2 == 0
                        val r = g.roll(if (one) 1 else 2)!!
                        assertEquals(if (one) 1 else 2, r.dice.size)
                        assertTrue(r.total in (if (one) 1..6 else 2..12))
                        assertEquals(ShutMath.hasCombination(g.open, r.total), r.hasMove)
                    }
                    Phase.SELECT -> {
                        val c = ShutMath.anyCombination(g.open, g.rollTotal)
                        assertTrue(c != 0)
                        for (k in 1..9) if (ShutMath.has(c, k)) assertTrue(g.toggle(k))
                        g.shut()!!
                    }
                    Phase.TURN_OVER -> {
                        val e = g.lastTurnEnd!!
                        assertEquals(if (e.perfect) 0 else g.openSum, e.score)
                        if (e.perfect) perfects++
                        turns++
                        g.advance()
                    }
                    Phase.GAME_OVER -> Unit
                }
            }
            assertEquals(n * GameConfig.ROUNDS, turns)
            val totals = List(n) { p -> (1..3).sumOf { g.score(p, it) } }
            for (p in 0 until n) {
                for (r in 1..3) assertTrue(g.score(p, r) in 0..45)
                assertEquals(totals[p], g.total(p))
            }
            val ranks = g.finalRanks()
            for (a in 0 until n) for (b in 0 until n) {
                if (totals[a] < totals[b]) assertTrue(ranks[a] < ranks[b])
                if (totals[a] == totals[b]) assertEquals(ranks[a], ranks[b])
            }
            assertNotNull(g.lastTurnEnd)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `1명은 안 됨`() {
        ShutBoxGame(1)
    }
}
