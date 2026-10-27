package com.siwoo.party.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RealPenaltyTest {

    @Test fun rouletteLayoutIsExactly60_30_10() {
        val r = PartyConfig.REAL_ROULETTE
        assertEquals(10, r.size)
        for (g in 0..2) assertEquals(PartyConfig.GRADE_PERCENT[g], r.count { it == g } * 100 / r.size)
    }

    @Test fun defaultsHaveFourPerGrade() {
        assertEquals(3, PartyConfig.DEFAULT_PHRASES.size)
        for (list in PartyConfig.DEFAULT_PHRASES) assertEquals(4, list.size)
    }

    @Test fun rollMatchesSlotAndPicksFromThatGrade() {
        val phrases = listOf(listOf("작1", "작2"), listOf("중1"), listOf("대1", "대2"))
        val random = Random(7)
        val counts = IntArray(3)
        repeat(20_000) {
            val res = RealPenalty.roll(phrases, random)
            assertEquals(PartyConfig.REAL_ROULETTE[res.slot], res.grade)
            assertTrue(res.phrase in phrases[res.grade])
            counts[res.grade]++
        }
        assertEquals(0.60, counts[0] / 20_000.0, 0.02)
        assertEquals(0.30, counts[1] / 20_000.0, 0.02)
        assertEquals(0.10, counts[2] / 20_000.0, 0.02)
    }

    @Test fun emptyGradeFallsBackToDefaults() {
        val res = RealPenalty.roll(listOf(emptyList(), emptyList(), emptyList()), Random(1))
        assertTrue(res.phrase in PartyConfig.DEFAULT_PHRASES[res.grade])
        assertTrue(RealPenalty.largePhrase(emptyList()) in PartyConfig.DEFAULT_PHRASES[2])
    }

    @Test fun cleanTrimsAndLimits() {
        val long = "가".repeat(50)
        val v = RealPenalty.clean(0, listOf("  노래  ", "", "노래", long) + List(20) { "문구$it" })
        assertEquals("노래", v[0])
        assertEquals(PartyConfig.MAX_PHRASE_LEN, v[1].length)
        assertEquals(PartyConfig.MAX_PHRASES_PER_GRADE, v.size)
        assertEquals(PartyConfig.DEFAULT_PHRASES[1], RealPenalty.clean(1, listOf(" ", "")))
    }
}
