package com.siwoo.party.games.matches

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchPileTest {

    @Test
    fun `누르면 고르고 다시 누르면 취소`() {
        val p = MatchPile()
        assertEquals(21, p.presentCount)
        assertEquals(Pick.SELECTED, p.toggle(5, 3))
        assertTrue(p.isSelected(5))
        assertEquals(1, p.selectedCount)
        assertEquals(Pick.DESELECTED, p.toggle(5, 3))
        assertFalse(p.isSelected(5))
        assertEquals(0, p.selectedCount)
    }

    @Test
    fun `네 번째는 고를 수 없다`() {
        val p = MatchPile()
        assertEquals(Pick.SELECTED, p.toggle(0, 3))
        assertEquals(Pick.SELECTED, p.toggle(1, 3))
        assertEquals(Pick.SELECTED, p.toggle(2, 3))
        assertEquals(Pick.FULL, p.toggle(3, 3))
        assertFalse(p.isSelected(3))
        assertEquals(3, p.selectedCount)
        // 하나 취소하면 다른 걸 고를 수 있다
        assertEquals(Pick.DESELECTED, p.toggle(1, 3))
        assertEquals(Pick.SELECTED, p.toggle(3, 3))
        assertEquals(0, p.selectedAt(0))
        assertEquals(2, p.selectedAt(1))
        assertEquals(3, p.selectedAt(2))
    }

    @Test
    fun `한도가 남은 수보다 작으면 그만큼만`() {
        val p = MatchPile()
        assertEquals(Pick.SELECTED, p.toggle(0, 2))
        assertEquals(Pick.SELECTED, p.toggle(1, 2))
        assertEquals(Pick.FULL, p.toggle(2, 2))
        // 한도가 커도 최대 3
        assertEquals(Pick.SELECTED, p.toggle(2, 9))
        assertEquals(Pick.FULL, p.toggle(4, 9))
    }

    @Test
    fun `가져가면 자리가 빈다 빈 자리는 못 고른다 새 라운드면 다시 채워짐`() {
        val p = MatchPile()
        p.toggle(7, 3); p.toggle(3, 3)
        assertArrayEquals("고른 순서대로", intArrayOf(7, 3), p.takeSelected())
        assertEquals(0, p.selectedCount)
        assertFalse(p.isPresent(7))
        assertFalse(p.isPresent(3))
        assertEquals(19, p.presentCount)
        assertEquals(Pick.ABSENT, p.toggle(7, 3))
        assertEquals(Pick.ABSENT, p.toggle(-1, 3))
        assertEquals(Pick.ABSENT, p.toggle(21, 3))
        p.toggle(0, 3)
        p.reset()
        assertEquals(21, p.presentCount)
        assertEquals(0, p.selectedCount)
        assertFalse(p.isSelected(0))
    }
}
