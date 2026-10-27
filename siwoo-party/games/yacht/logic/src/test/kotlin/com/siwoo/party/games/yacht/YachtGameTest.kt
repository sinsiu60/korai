package com.siwoo.party.games.yacht

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 정해 둔 눈을 차례로 내주는 난수 (굴릴 때마다 고정 안 된 주사위 수만큼 꺼내 씀) */
class ScriptedRandom(vararg faces: Int) : Random() {
    private val queue = ArrayDeque(faces.toList())
    fun push(vararg faces: Int) { queue.addAll(faces.toList()) }
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = queue.removeFirstOrNull() ?: from
}

class ScoringTest {

    private fun s(cat: Category, vararg d: Int) = Scoring.score(cat, d)

    @Test fun `상단 1~6 은 그 눈의 합, 없으면 0`() {
        val d = intArrayOf(1, 1, 3, 6, 6)
        assertEquals(2, Scoring.score(Category.ACES, d))
        assertEquals(0, Scoring.score(Category.DUALS, d))
        assertEquals(3, Scoring.score(Category.TRIPLES, d))
        assertEquals(0, Scoring.score(Category.QUADS, d))
        assertEquals(0, Scoring.score(Category.PENTAS, d))
        assertEquals(12, Scoring.score(Category.HEXAS, d))
        assertEquals(8, s(Category.QUADS, 4, 4, 2, 2, 1))
        assertEquals(25, s(Category.PENTAS, 5, 5, 5, 5, 5))
        assertEquals(4, s(Category.DUALS, 2, 2, 1, 3, 4))
        assertEquals(0, s(Category.ACES, 2, 3, 4, 5, 6))
    }

    @Test fun `초이스는 5개 합 (항상 0보다 큼)`() {
        assertEquals(19, s(Category.CHOICE, 1, 2, 4, 6, 6))
        assertEquals(5, s(Category.CHOICE, 1, 1, 1, 1, 1))
        assertEquals(30, s(Category.CHOICE, 6, 6, 6, 6, 6))
    }

    @Test fun `포카드는 같은 눈 4개 이상이면 5개 합, 아니면 0`() {
        assertEquals(22, s(Category.FOUR_KIND, 5, 5, 5, 5, 2))
        assertEquals(0, s(Category.FOUR_KIND, 5, 5, 5, 2, 2))
        assertEquals(0, s(Category.FOUR_KIND, 1, 2, 3, 4, 5))
    }

    @Test fun `야추도 포카드로 인정 - 5개 합`() {
        assertEquals(15, s(Category.FOUR_KIND, 3, 3, 3, 3, 3))
        assertEquals(30, s(Category.FOUR_KIND, 6, 6, 6, 6, 6))
    }

    @Test fun `풀하우스는 3개+2개면 5개 합, 아니면 0`() {
        assertEquals(13, s(Category.FULL_HOUSE, 3, 3, 3, 2, 2))
        assertEquals(28, s(Category.FULL_HOUSE, 6, 5, 6, 5, 6))
        assertEquals(0, s(Category.FULL_HOUSE, 3, 3, 3, 3, 2)) // 4+1
        assertEquals(0, s(Category.FULL_HOUSE, 3, 3, 2, 2, 1)) // 2+2+1
        assertEquals(0, s(Category.FULL_HOUSE, 1, 2, 3, 4, 5))
    }

    @Test fun `풀하우스 규칙 선택 - 5개 같음은 풀하우스가 아님(0점), 설정을 바꾸면 인정`() {
        assertFalse(GameConfig.YACHT_COUNTS_AS_FULL_HOUSE)
        assertEquals(0, s(Category.FULL_HOUSE, 4, 4, 4, 4, 4))
        assertEquals(20, Scoring.score(Category.FULL_HOUSE, intArrayOf(4, 4, 4, 4, 4), yachtIsFullHouse = true))
        // 설정과 상관없이 진짜 3+2 는 그대로
        assertEquals(13, Scoring.score(Category.FULL_HOUSE, intArrayOf(3, 3, 3, 2, 2), yachtIsFullHouse = true))
    }

    @Test fun `스몰 스트레이트는 4개 연속이면 15`() {
        assertEquals(15, s(Category.SMALL_STRAIGHT, 1, 2, 3, 4, 6))
        assertEquals(15, s(Category.SMALL_STRAIGHT, 6, 5, 4, 3, 1))
        assertEquals(0, s(Category.SMALL_STRAIGHT, 1, 2, 3, 5, 6))
        assertEquals(0, s(Category.SMALL_STRAIGHT, 2, 2, 2, 2, 2))
    }

    @Test fun `중복 눈이 섞인 스트레이트 판정`() {
        assertEquals(15, s(Category.SMALL_STRAIGHT, 1, 2, 3, 3, 4))
        assertEquals(15, s(Category.SMALL_STRAIGHT, 2, 3, 4, 5, 5))
        assertEquals(15, s(Category.SMALL_STRAIGHT, 3, 4, 5, 6, 6))
        assertEquals(15, s(Category.SMALL_STRAIGHT, 4, 3, 6, 5, 3))
        assertEquals(0, s(Category.LARGE_STRAIGHT, 1, 2, 3, 3, 4))
        assertEquals(0, s(Category.LARGE_STRAIGHT, 3, 4, 5, 6, 6))
        // 12345 는 라지이자 스몰
        assertEquals(30, s(Category.LARGE_STRAIGHT, 1, 2, 3, 4, 5))
        assertEquals(15, s(Category.SMALL_STRAIGHT, 1, 2, 3, 4, 5))
        assertEquals(30, s(Category.LARGE_STRAIGHT, 6, 2, 4, 3, 5))
        assertEquals(15, s(Category.SMALL_STRAIGHT, 6, 2, 4, 3, 5))
        // 12356 은 둘 다 아님
        assertEquals(0, s(Category.SMALL_STRAIGHT, 1, 2, 3, 5, 6))
        assertEquals(0, s(Category.LARGE_STRAIGHT, 1, 2, 3, 5, 6))
    }

    @Test fun `라지 스트레이트는 5개 연속이면 30`() {
        assertEquals(30, s(Category.LARGE_STRAIGHT, 2, 3, 4, 5, 6))
        assertEquals(0, s(Category.LARGE_STRAIGHT, 1, 2, 3, 4, 6))
        assertEquals(0, s(Category.LARGE_STRAIGHT, 1, 1, 1, 1, 1))
    }

    @Test fun `야추는 5개 같으면 50, 아니면 0`() {
        assertEquals(50, s(Category.YACHT, 1, 1, 1, 1, 1))
        assertEquals(50, s(Category.YACHT, 6, 6, 6, 6, 6))
        assertEquals(0, s(Category.YACHT, 6, 6, 6, 6, 5))
        assertTrue(Scoring.isYacht(intArrayOf(2, 2, 2, 2, 2)))
        assertFalse(Scoring.isYacht(intArrayOf(2, 2, 2, 2, 1)))
    }

    @Test fun `칸은 12개, 상단 6 + 하단 6`() {
        assertEquals(GameConfig.CATEGORIES, Category.ALL.size)
        assertEquals(6, Category.ALL.count { it.upper })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), Category.ALL.filter { it.upper }.map { it.face })
    }
}

class YachtGameTest {

    /** 굴리기 전 (눈 지정) */
    private fun game(players: Int, r: ScriptedRandom) = YachtGame(players, r)

    /** 지금 차례 사람이 d 를 굴려 cat 에 적음 */
    private fun turn(g: YachtGame, r: ScriptedRandom, cat: Category, vararg d: Int): Record.Done {
        r.push(*d)
        assertTrue(g.roll())
        return g.record(cat) as Record.Done
    }

    @Test fun `한 차례 최대 3번 굴림`() {
        val g = YachtGame(2, Random(1))
        assertEquals(3, g.rollsLeft)
        assertTrue(g.roll())
        assertTrue(g.roll())
        assertTrue(g.roll())
        assertEquals(0, g.rollsLeft)
        assertFalse(g.canRoll)
        assertFalse("네 번째 굴림은 안 됨", g.roll())
        assertEquals(3, g.rollsUsed)
    }

    @Test fun `굴리기 전에는 기록·고정 불가`() {
        val g = YachtGame(2, Random(1))
        val r = g.record(Category.CHOICE)
        assertTrue(r is Record.Rejected && r.reason == Reject.NOT_ROLLED)
        assertFalse(g.toggleHold(0))
        assertFalse(g.held[0])
        assertFalse(g.isFilled(0, Category.CHOICE))
        assertEquals(YachtGame.EMPTY, g.preview(Category.CHOICE))
        assertEquals(0, g.current)
    }

    @Test fun `이미 채운 칸은 다시 못 씀`() {
        val r = ScriptedRandom()
        val g = game(2, r)
        turn(g, r, Category.CHOICE, 1, 2, 3, 4, 6) // 가람
        turn(g, r, Category.ACES, 1, 1, 1, 2, 2)  // 나래
        r.push(6, 6, 6, 6, 6)
        g.roll()
        assertEquals("채운 칸은 미리보기 없음", YachtGame.EMPTY, g.preview(Category.CHOICE))
        val rej = g.record(Category.CHOICE)
        assertTrue(rej is Record.Rejected && rej.reason == Reject.FILLED)
        assertEquals(16, g.score(0, Category.CHOICE))
        assertEquals(0, g.current)
        // 다른 칸은 됨
        assertEquals(50, (g.record(Category.YACHT) as Record.Done).points)
    }

    @Test fun `고정한 주사위는 다시 굴려도 그대로`() {
        val r = ScriptedRandom(2, 5, 5, 1, 5)
        val g = game(2, r)
        g.roll()
        assertArrayEquals(intArrayOf(2, 5, 5, 1, 5), g.dice)
        assertTrue(g.toggleHold(1)); assertTrue(g.toggleHold(2)); assertTrue(g.toggleHold(4))
        r.push(3, 6) // 고정 안 된 0·3번만 새 눈
        g.roll()
        assertArrayEquals(intArrayOf(3, 5, 5, 6, 5), g.dice)
        // 해제 → 다시 굴림
        assertTrue(g.toggleHold(4))
        assertFalse(g.held[4])
        r.push(4, 4, 5)
        g.roll()
        assertArrayEquals(intArrayOf(4, 5, 5, 4, 5), g.dice)
        // 3번 다 굴리면 고정 못 함
        assertFalse(g.toggleHold(0))
        assertEquals(23, g.preview(Category.FULL_HOUSE))
        // 기록하면 고정은 풀리고 다음 사람
        g.record(Category.FULL_HOUSE)
        assertTrue(g.held.none { it })
        assertEquals(1, g.current)
        assertEquals(0, g.rollsUsed)
    }

    @Test fun `5개 모두 고정하면 더 못 굴림`() {
        val g = YachtGame(2, Random(3))
        g.roll()
        for (i in 0 until 5) g.toggleHold(i)
        assertFalse(g.canRoll)
        assertFalse(g.roll())
        g.toggleHold(2)
        assertTrue(g.canRoll)
    }

    @Test fun `미리보기는 지금 주사위 기준 점수`() {
        val r = ScriptedRandom(3, 3, 3, 2, 2)
        val g = game(2, r)
        g.roll()
        assertEquals(13, g.preview(Category.FULL_HOUSE))
        assertEquals(9, g.preview(Category.TRIPLES))
        assertEquals(0, g.preview(Category.YACHT))
        assertEquals(13, g.preview(Category.CHOICE))
        assertFalse(g.isYacht)
        r.push(3, 3)
        g.toggleHold(0); g.toggleHold(1); g.toggleHold(2)
        g.roll()
        assertTrue(g.isYacht)
        assertEquals(50, g.preview(Category.YACHT))
        assertEquals(15, g.preview(Category.FOUR_KIND))
        assertEquals(0, g.preview(Category.FULL_HOUSE))
    }

    @Test fun `보너스 경계값 - 상단 62 는 보너스 없음, 63 은 +35`() {
        // 62 = 3+8+12+12+15+12 (에이스 3, 듀얼 8, 트리플 12, 쿼드 12, 펜타 15, 헥사 12)
        val r = ScriptedRandom()
        val g = game(2, r)
        val p1 = arrayOf(
            Category.ACES to intArrayOf(1, 1, 1, 6, 6),
            Category.DUALS to intArrayOf(2, 2, 2, 2, 6),
            Category.TRIPLES to intArrayOf(3, 3, 3, 3, 6),
            Category.QUADS to intArrayOf(4, 4, 4, 1, 1),
            Category.PENTAS to intArrayOf(5, 5, 5, 1, 1),
        )
        // 63 = 3+6+9+12+15+18
        val p2 = arrayOf(
            Category.ACES to intArrayOf(1, 1, 1, 6, 6),
            Category.DUALS to intArrayOf(2, 2, 2, 6, 6),
            Category.TRIPLES to intArrayOf(3, 3, 3, 6, 6),
            Category.QUADS to intArrayOf(4, 4, 4, 1, 1),
            Category.PENTAS to intArrayOf(5, 5, 5, 1, 1),
        )
        for (k in p1.indices) {
            assertFalse(turn(g, r, p1[k].first, *p1[k].second).bonusEarned)
            assertFalse(turn(g, r, p2[k].first, *p2[k].second).bonusEarned)
        }
        assertEquals(50, g.upperSubtotal(0))
        assertEquals(45, g.upperSubtotal(1))
        val a = turn(g, r, Category.HEXAS, 6, 6, 1, 1, 1)   // 50 + 12 = 62
        val b = turn(g, r, Category.HEXAS, 6, 6, 6, 1, 1)   // 45 + 18 = 63
        assertEquals(62, g.upperSubtotal(0))
        assertEquals(63, g.upperSubtotal(1))
        assertFalse(a.bonusEarned)
        assertTrue(b.bonusEarned)
        assertFalse(g.hasBonus(0))
        assertTrue(g.hasBonus(1))
        assertEquals(0, g.bonus(0))
        assertEquals(35, g.bonus(1))
        assertTrue(g.upperComplete(0))
        assertEquals(62, g.total(0))
        assertEquals(63 + 35, g.total(1))
    }

    @Test fun `0점 기록도 칸을 채운 것으로 친다`() {
        val r = ScriptedRandom()
        val g = game(2, r)
        val d = turn(g, r, Category.YACHT, 1, 2, 3, 5, 6)
        assertEquals(0, d.points)
        assertTrue(g.isFilled(0, Category.YACHT))
        assertEquals(0, g.score(0, Category.YACHT))
        assertEquals(0, g.total(0))
    }

    @Test fun `12라운드 × 인원 차례 뒤에 게임 끝, 라운드·차례가 차례대로 넘어감`() {
        val r = ScriptedRandom()
        val g = game(3, r)
        var last: Record.Done? = null
        for (round in 1..12) {
            for (p in 0 until 3) {
                assertEquals(round, g.round)
                assertEquals(p, g.current)
                assertFalse(g.gameOver)
                last = turn(g, r, Category.ALL[round - 1], 1, 2, 3, 4, 5)
                assertEquals(p, last.player)
            }
        }
        assertTrue(g.gameOver)
        assertTrue(last!!.gameOver)
        assertEquals(36, g.turnsTaken)
        assertEquals(12, g.round)
        assertFalse(g.roll())
        val rej = g.record(Category.CHOICE)
        assertTrue(rej is Record.Rejected && rej.reason == Reject.GAME_OVER)
        // 모두 칸이 다 참
        for (p in 0 until 3) assertTrue(Category.ALL.all { g.isFilled(p, it) })
        // 12345 로 모든 칸: 1+2+3+4+5+0 + 초이스 15 + 0 + 0 + 15 + 30 + 0 = 75
        assertEquals(75, g.total(0))
        assertArrayEquals(intArrayOf(1, 1, 1), g.finalRanks())
    }

    @Test fun `순위는 총점 순, 같으면 공동 순위`() {
        val r = ScriptedRandom()
        val g = game(4, r)
        val miss = intArrayOf(1, 2, 3, 5, 6)
        // 1라운드 야추 칸: 가람 50, 나래 0, 다온 50, 라온 0
        val yacht = arrayOf(intArrayOf(6, 6, 6, 6, 6), miss, intArrayOf(3, 3, 3, 3, 3), miss)
        for (p in 0 until 4) turn(g, r, Category.YACHT, *yacht[p])
        // 2라운드 초이스: 나래만 29, 나머지 17
        for (p in 0 until 4) turn(g, r, Category.CHOICE, *(if (p == 1) intArrayOf(6, 6, 6, 6, 5) else miss))
        // 나머지 10라운드는 모두 같은 눈·같은 칸 (상단 1+2+3+5+6 = 17, 하단 0)
        val rest = Category.ALL.filter { it != Category.YACHT && it != Category.CHOICE }
        for (cat in rest) for (p in 0 until 4) turn(g, r, cat, *miss)
        assertTrue(g.gameOver)
        assertEquals(84, g.total(0))
        assertEquals(46, g.total(1))
        assertEquals(84, g.total(2))
        assertEquals(34, g.total(3))
        assertArrayEquals(intArrayOf(1, 3, 1, 4), g.finalRanks())
    }
}
