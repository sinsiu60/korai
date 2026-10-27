package com.siwoo.party.common.numpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 숫자 입력 규칙: 숫자 야구(서로 다른 3자리, 0 가능) · 업다운(1~100, 범위 좁히기) · 지우기 */
class NumInputTest {

    @Test
    fun `숫자 야구 - 서로 다른 숫자 3개, 0 가능, 다 채워야 확인`() {
        val n = NumInput(3, allowLeadingZero = true, distinctDigits = true, exactDigits = true)
        assertEquals("서로 다른 숫자 3개를 누르세요", n.error)
        assertFalse(n.canConfirm)
        assertTrue(n.type(0))
        assertFalse("같은 숫자 금지", n.canType(0))
        assertFalse(n.type(0))
        assertTrue(n.type(7))
        assertEquals("숫자 3개를 모두 넣으세요", n.error)
        assertTrue(n.type(3))
        assertEquals("073", n.text)
        assertTrue(n.isFull)
        assertFalse("다 차면 더 못 넣음", n.canType(5))
        assertNull(n.error)
        assertTrue(n.canConfirm)
        assertEquals(73, n.value)
        assertTrue(n.backspace())
        assertEquals("07", n.text)
        assertTrue("지운 숫자는 다시 가능", n.canType(3))
        assertFalse(n.canType(7))
        n.clear()
        assertEquals("", n.text)
        assertFalse(n.backspace())
        assertTrue(n.canType(7))
    }

    @Test
    fun `업다운 - 첫 자리 0 금지, 범위 밖이면 이유`() {
        val n = NumInput(3, allowLeadingZero = false, min = 1, max = 100)
        assertEquals("1~100 사이 숫자를 누르세요", n.error)
        assertFalse("첫 자리 0 금지", n.canType(0))
        n.type(5)
        assertTrue("두 번째 자리 0 가능", n.canType(0))
        assertTrue("같은 숫자 가능", n.canType(5))
        assertNull(n.error)
        n.type(0)
        n.type(0)
        assertEquals("500", n.text)
        assertEquals("1~100 사이", n.error)
        assertFalse(n.canConfirm)
        n.backspace()
        assertEquals(50, n.value)
        // 범위가 좁혀짐 (업: 51~100)
        n.setRange(51, 100)
        assertEquals("51~100 사이", n.error)
        n.type(7)
        assertEquals("507", n.text)
        assertEquals("51~100 사이", n.error)
        n.clear()
        n.type(7)
        n.type(7)
        assertNull(n.error)
        n.setRange(80, null)
        assertEquals("80 이상", n.error)
        n.setRange(null, 70)
        assertEquals("70 이하", n.error)
    }

    @Test
    fun `설정 바꾸기와 통째로 넣기`() {
        val n = NumInput(2)
        assertEquals("숫자를 누르세요", n.hint)
        assertEquals("12", n.set("1234"))
        n.configure(4, distinctDigits = true)
        assertEquals("", n.text)
        assertEquals("123", n.set("1a213"))
        n.configure(3, allowLeadingZero = false)
        assertEquals("50", n.set("050"))
        assertEquals(0, NumInput(20).maxDigits.compareTo(9))
        assertFalse(n.canType(10))
        assertFalse(n.canType(-1))
    }
}
