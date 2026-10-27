package com.siwoo.party.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomPickTest {

    private val any = DeviceCaps(maxTouches = 10, hasAccelerometer = true)

    private fun e(id: String, on: Boolean = true, players: Boolean = true, touches: Int = 0, sensor: Boolean = false) =
        RandomEntry(id, on, players, touches, sensor)

    @Test fun `스위치를 끈 게임은 빠진다`() {
        val list = listOf(e("a"), e("b", on = false), e("c"))
        assertEquals(listOf("a", "c"), RandomPick.candidates(list, any, null, true))
    }

    @Test fun `지금 인원으로 못 하는 게임은 빠진다`() {
        val list = listOf(e("a"), e("liar", players = false))
        assertEquals(listOf("a"), RandomPick.candidates(list, any, null, true))
    }

    @Test fun `동시 터치 수가 모자라는 기기면 빠진다`() {
        val two = DeviceCaps(maxTouches = 2, hasAccelerometer = true)
        val list = listOf(e("turn"), e("reaction4", touches = 4), e("reaction2", touches = 2))
        assertEquals(listOf("turn", "reaction2"), RandomPick.candidates(list, two, null, true))
        assertEquals(RandomBlock.TOUCHES, RandomPick.blockOf(list[1], two))
    }

    @Test fun `터치 수를 아직 모르면 된다고 본다`() {
        val unknown = DeviceCaps(maxTouches = -1, hasAccelerometer = true)
        assertEquals(listOf("r"), RandomPick.candidates(listOf(e("r", touches = 4)), unknown, null, true))
    }

    @Test fun `센서가 없는 기기면 센서 게임이 빠진다`() {
        val noSensor = DeviceCaps(maxTouches = 5, hasAccelerometer = false)
        val list = listOf(e("forehead", sensor = true), e("b"))
        assertEquals(listOf("b"), RandomPick.candidates(list, noSensor, null, true))
        assertEquals(RandomBlock.SENSOR, RandomPick.blockOf(list[0], noSensor))
        assertEquals(listOf("forehead", "b"), RandomPick.candidates(list, any, null, true))
    }

    @Test fun `연속 방지 켬이면 직전 게임을 뺀다`() {
        val list = listOf(e("a"), e("b"), e("c"))
        assertEquals(listOf("a", "c"), RandomPick.candidates(list, any, "b", true))
        assertEquals(listOf("a", "b", "c"), RandomPick.candidates(list, any, "b", false))
    }

    @Test fun `후보가 직전 게임 하나뿐이면 연속 방지 예외`() {
        val list = listOf(e("a"), e("b", on = false))
        assertEquals(listOf("a"), RandomPick.candidates(list, any, "a", true))
    }

    @Test fun `직전 게임이 이미 후보에 없으면 그대로`() {
        val list = listOf(e("a"), e("b"), e("c", on = false))
        assertEquals(listOf("a", "b"), RandomPick.candidates(list, any, "c", true))
    }

    @Test fun `조건 조합 - 스위치 인원 기기 연속방지가 모두 걸린다`() {
        val dev = DeviceCaps(maxTouches = 2, hasAccelerometer = false)
        val list = listOf(
            e("off", on = false),
            e("three", players = false),
            e("four", touches = 4),
            e("tilt", sensor = true),
            e("last"),
            e("ok1"),
            e("ok2", touches = 2),
        )
        assertEquals(listOf("ok1", "ok2"), RandomPick.candidates(list, dev, "last", true))
        assertEquals(listOf("last", "ok1", "ok2"), RandomPick.eligible(list, dev))
    }

    @Test fun `모두 걸리면 후보 0개`() {
        val list = listOf(e("a", on = false), e("b", players = false))
        assertTrue(RandomPick.candidates(list, any, null, true).isEmpty())
    }

    @Test fun `playable 은 스위치와 상관없이 인원과 기기만 본다`() {
        assertTrue(RandomPick.playable(e("a", on = false), any))
        assertFalse(RandomPick.playable(e("a", players = false), any))
    }

    @Test fun `카테고리 스위치 상태 - 전부 일부 없음`() {
        assertEquals(GroupState.ON, SwitchGroup.state(listOf(true, true)))
        assertEquals(GroupState.OFF, SwitchGroup.state(listOf(false, false)))
        assertEquals(GroupState.PARTIAL, SwitchGroup.state(listOf(true, false)))
    }

    @Test fun `카테고리 스위치 누르면 켬은 전부 끔, 끔과 일부는 전부 켬`() {
        assertFalse(SwitchGroup.toggle(listOf(true, true)))
        assertTrue(SwitchGroup.toggle(listOf(false, false)))
        assertTrue(SwitchGroup.toggle(listOf(true, false)))
    }
}
