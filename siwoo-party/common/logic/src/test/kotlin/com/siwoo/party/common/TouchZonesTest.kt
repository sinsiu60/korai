package com.siwoo.party.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchZonesTest {
    private val w = 1280f
    private val h = 720f

    @Test fun twoPlayersLeftRight() {
        val z = TouchZones.layout(2, w, h)
        assertEquals(0, TouchZones.zoneAt(z, 10f, 700f))
        assertEquals(1, TouchZones.zoneAt(z, 1270f, 10f))
        assertTrue(z.all { it.rotation == 0f })
    }

    @Test fun threePlayersTopCornersAndBottomCenter() {
        val z = TouchZones.layout(3, w, h)
        assertEquals(0, TouchZones.zoneAt(z, 10f, 10f))
        assertEquals(1, TouchZones.zoneAt(z, 1270f, 10f))
        assertEquals(2, TouchZones.zoneAt(z, 640f, 700f))
        assertEquals(-1, TouchZones.zoneAt(z, 10f, 700f)) // 아래 귀퉁이는 비어 있음
        assertEquals(180f, z[0].rotation)
        assertEquals(180f, z[1].rotation)
        assertEquals(0f, z[2].rotation)
    }

    @Test fun fourPlayersCorners() {
        val z = TouchZones.layout(4, w, h, gap = 20f)
        assertEquals(0, TouchZones.zoneAt(z, 5f, 5f))
        assertEquals(1, TouchZones.zoneAt(z, 1275f, 5f))
        assertEquals(2, TouchZones.zoneAt(z, 5f, 715f))
        assertEquals(3, TouchZones.zoneAt(z, 1275f, 715f))
        assertEquals(-1, TouchZones.zoneAt(z, 640f, 360f)) // 가운데 간격
        assertEquals(listOf(180f, 180f, 0f, 0f), z.map { it.rotation })
    }

    @Test fun zonesNeverOverlap() {
        for (n in 2..4) {
            val z = TouchZones.layout(n, w, h, gap = 8f)
            assertEquals(n, z.size)
            for (a in z) for (b in z) if (a !== b) {
                val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
                assertFalse("$n 인 ${a.index}/${b.index}", overlap)
            }
        }
    }
}
