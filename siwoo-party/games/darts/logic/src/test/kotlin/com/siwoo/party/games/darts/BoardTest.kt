package com.siwoo.party.games.darts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 영역별 점수 판정: 고리 경계 · 칸 경계 · 각도 감김 · 극좌표 */
class BoardTest {

    private val xy = FloatArray(2)
    private val pol = FloatArray(2)

    /** 숫자 칸 가운데 각도로 반지름 r 위치의 판정 */
    private fun at(r: Float, number: Int = 20): Hit {
        Board.toXY(r, Board.sectorCenterDeg(number), xy)
        return Board.score(xy[0], xy[1])
    }

    private fun up(v: Float) = Math.nextUp(v)

    @Test
    fun `가운데는 불 50`() {
        val h = Board.score(0f, 0f)
        assertSame(Ring.BULL, h.ring)
        assertEquals(50, h.points)
        assertEquals("불! 50", h.label)
    }

    @Test
    fun `불 경계 6_35mm 는 불, 바로 밖은 아우터 불`() {
        assertSame(Ring.BULL, Board.ringAt(GameConfig.R_BULL))
        assertSame(Ring.OUTER_BULL, Board.ringAt(up(GameConfig.R_BULL)))
        assertEquals(50, Board.score(0f, -6.3f).points)
        assertEquals(25, Board.score(0f, -6.4f).points)
        assertEquals(25, Board.score(6.4f, 0f).points)
        assertEquals("아우터 불 25", Board.score(0f, 10f).label)
    }

    @Test
    fun `아우터 불 경계 15_9mm 는 25, 바로 밖은 싱글`() {
        assertSame(Ring.OUTER_BULL, Board.ringAt(GameConfig.R_OUTER_BULL))
        assertSame(Ring.SINGLE_INNER, Board.ringAt(up(GameConfig.R_OUTER_BULL)))
        assertEquals(25, at(15.8f).points)
        val s = at(16.0f)
        assertSame(Ring.SINGLE_INNER, s.ring)
        assertEquals(20, s.points)
        assertEquals("싱글 20", s.label)
    }

    @Test
    fun `트리플 고리 99 ~ 107mm`() {
        assertSame(Ring.SINGLE_INNER, Board.ringAt(Math.nextDown(GameConfig.R_TREBLE_IN)))
        assertSame(Ring.TRIPLE, Board.ringAt(GameConfig.R_TREBLE_IN))
        assertSame(Ring.TRIPLE, Board.ringAt(GameConfig.R_TREBLE_OUT))
        assertSame(Ring.SINGLE_OUTER, Board.ringAt(up(GameConfig.R_TREBLE_OUT)))
        assertEquals(20, at(98.9f).points)
        val t = at(103f)
        assertSame(Ring.TRIPLE, t.ring)
        assertEquals(60, t.points)
        assertEquals(3, t.multiplier)
        assertEquals("트리플 20 = 60", t.label)
        assertEquals(20, at(107.1f).points)
        assertEquals(57, at(103f, 19).points)
    }

    @Test
    fun `더블 고리 162 ~ 170mm, 그 밖은 아웃`() {
        assertSame(Ring.SINGLE_OUTER, Board.ringAt(Math.nextDown(GameConfig.R_DOUBLE_IN)))
        assertSame(Ring.DOUBLE, Board.ringAt(GameConfig.R_DOUBLE_IN))
        assertSame(Ring.DOUBLE, Board.ringAt(GameConfig.R_DOUBLE_OUT))
        assertSame(Ring.OUT, Board.ringAt(up(GameConfig.R_DOUBLE_OUT)))
        assertEquals(16, at(161.9f, 16).points)
        val d = at(166f, 16)
        assertSame(Ring.DOUBLE, d.ring)
        assertEquals(32, d.points)
        assertEquals("더블 16 = 32", d.label)
        val o = at(170.1f, 16)
        assertSame(Ring.OUT, o.ring)
        assertEquals(0, o.points)
        assertEquals("아웃", o.label)
        assertEquals(0, Board.score(500f, -500f).points)
    }

    @Test
    fun `싱글 안쪽 바깥쪽 모두 숫자 그대로`() {
        assertSame(Ring.SINGLE_INNER, at(50f, 7).ring)
        assertSame(Ring.SINGLE_OUTER, at(130f, 7).ring)
        assertEquals(7, at(50f, 7).points)
        assertEquals(7, at(130f, 7).points)
    }

    @Test
    fun `20 은 위, 6 은 오른쪽, 3 은 아래, 11 은 왼쪽`() {
        assertEquals(20, Board.score(0f, -50f).number)
        assertEquals(6, Board.score(50f, 0f).number)
        assertEquals(3, Board.score(0f, 50f).number)
        assertEquals(11, Board.score(-50f, 0f).number)
    }

    @Test
    fun `시계 방향 칸 순서가 정식 다트판과 같다`() {
        val expected = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)
        for (i in 0 until 20) {
            Board.toXY(60f, i * 18f, xy)
            assertEquals("칸 $i", expected[i], Board.score(xy[0], xy[1]).number)
            assertEquals(i * 18f, Board.sectorCenterDeg(expected[i]), 0f)
        }
    }

    @Test
    fun `칸 경계는 시계 방향 다음 칸, 351도에서 20 으로 감긴다`() {
        assertEquals(20, Board.sectorNumber(0f))
        assertEquals(20, Board.sectorNumber(8.99f))
        assertEquals(1, Board.sectorNumber(9f))
        assertEquals(1, Board.sectorNumber(26.99f))
        assertEquals(18, Board.sectorNumber(27f))
        assertEquals(5, Board.sectorNumber(350.99f))
        assertEquals(20, Board.sectorNumber(351f))
        assertEquals(20, Board.sectorNumber(359.99f))
        // 360 이상·음수 각도도 감김
        assertEquals(20, Board.sectorNumber(360f))
        assertEquals(1, Board.sectorNumber(369f))
        assertEquals(5, Board.sectorNumber(-10f))
        assertEquals(20, Board.sectorNumber(-9f))
    }

    @Test
    fun `20 과 5 사이, 20 과 1 사이 좌표 판정`() {
        // 위쪽에서 살짝 왼쪽 → 5, 살짝 오른쪽 → 1
        Board.toXY(60f, 351.5f, xy)
        assertEquals(20, Board.score(xy[0], xy[1]).number)
        Board.toXY(60f, 350.5f, xy)
        assertEquals(5, Board.score(xy[0], xy[1]).number)
        Board.toXY(60f, 8.5f, xy)
        assertEquals(20, Board.score(xy[0], xy[1]).number)
        Board.toXY(60f, 9.5f, xy)
        assertEquals(1, Board.score(xy[0], xy[1]).number)
    }

    @Test
    fun `극좌표 변환 왕복`() {
        Board.polar(0f, -10f, pol)
        assertEquals(10f, pol[0], 1e-4f)
        assertEquals(0f, pol[1], 1e-4f)
        Board.polar(10f, 0f, pol)
        assertEquals(90f, pol[1], 1e-4f)
        Board.polar(0f, 10f, pol)
        assertEquals(180f, pol[1], 1e-4f)
        Board.polar(-10f, 0f, pol)
        assertEquals(270f, pol[1], 1e-4f)
        Board.polar(-1f, -1f, pol)
        assertEquals(315f, pol[1], 1e-3f)
        assertEquals(Math.sqrt(2.0).toFloat(), pol[0], 1e-4f)
        // 각도는 늘 0 이상 360 미만
        for (k in 0 until 720) {
            val a = k * 0.5f
            Board.toXY(123f, a, xy)
            Board.polar(xy[0], xy[1], pol)
            assertEquals(123f, pol[0], 1e-3f)
            assertTrue(pol[1] >= 0f && pol[1] < 360f)
            val diff = Math.abs(pol[1] - (a % 360f))
            assertTrue("각도 $a → ${pol[1]}", diff < 1e-2f || Math.abs(diff - 360f) < 1e-2f)
        }
    }

    @Test
    fun `모든 칸 모든 고리 가운데 점수`() {
        for (n in 1..20) {
            Board.center(Ring.TRIPLE, n, xy)
            assertEquals(n * 3, Board.score(xy[0], xy[1]).points)
            Board.center(Ring.DOUBLE, n, xy)
            assertEquals(n * 2, Board.score(xy[0], xy[1]).points)
            Board.center(Ring.SINGLE_INNER, n, xy)
            assertEquals(n, Board.score(xy[0], xy[1]).points)
            Board.center(Ring.SINGLE_OUTER, n, xy)
            assertSame(Board.hit(Ring.SINGLE_OUTER, n), Board.score(xy[0], xy[1]))
        }
        Board.center(Ring.OUTER_BULL, 20, xy)
        assertSame(Board.OUTER_BULL, Board.score(xy[0], xy[1]))
        Board.center(Ring.OUT, 20, xy)
        assertSame(Board.OUT, Board.score(xy[0], xy[1]))
    }

    @Test
    fun `판정은 미리 만든 같은 객체`() {
        assertSame(Board.score(0f, -103f), Board.score(0.5f, -103.5f))
        assertSame(Board.hit(Ring.TRIPLE, 20), Board.score(0f, -103f))
        assertEquals("T20", Board.hit(Ring.TRIPLE, 20).short)
        assertEquals("D16", Board.hit(Ring.DOUBLE, 16).short)
        assertEquals("BULL", Board.BULL.short)
    }
}
