package com.siwoo.party.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreRulesTest {

    @Test fun twoPlayers() {
        assertArrayEquals(intArrayOf(3, 0), ScoreRules.points(intArrayOf(1, 2)))
        assertArrayEquals(intArrayOf(0, 3), ScoreRules.points(intArrayOf(2, 1)))
    }

    @Test fun threePlayers() {
        assertArrayEquals(intArrayOf(4, 2, 0), ScoreRules.points(intArrayOf(1, 2, 3)))
        assertArrayEquals(intArrayOf(0, 4, 2), ScoreRules.points(intArrayOf(3, 1, 2)))
    }

    @Test fun fourPlayers() {
        assertArrayEquals(intArrayOf(4, 2, 1, 0), ScoreRules.points(intArrayOf(1, 2, 3, 4)))
        assertArrayEquals(intArrayOf(1, 0, 4, 2), ScoreRules.points(intArrayOf(3, 4, 1, 2)))
    }

    @Test fun tiesShareThatRanksPoints() {
        // 2인 무승부: 둘 다 1위 점수
        assertArrayEquals(intArrayOf(3, 3), ScoreRules.points(intArrayOf(1, 1)))
        // 3인 공동 1위 2명 → 각 4점, 3위 0점
        assertArrayEquals(intArrayOf(4, 4, 0), ScoreRules.points(intArrayOf(1, 1, 3)))
        // 3인 공동 2위 → 각 2점
        assertArrayEquals(intArrayOf(4, 2, 2), ScoreRules.points(intArrayOf(1, 2, 2)))
        // 4인 공동 1위 2명 → 4, 4 / 3위 1 / 4위 0
        assertArrayEquals(intArrayOf(4, 4, 1, 0), ScoreRules.points(intArrayOf(1, 1, 3, 4)))
        // 4인 공동 2위 3명 → 모두 2점
        assertArrayEquals(intArrayOf(4, 2, 2, 2), ScoreRules.points(intArrayOf(1, 2, 2, 2)))
        // 4인 공동 3위 → 각 1점
        assertArrayEquals(intArrayOf(4, 2, 1, 1), ScoreRules.points(intArrayOf(1, 2, 3, 3)))
        // 전원 공동 1위
        assertArrayEquals(intArrayOf(4, 4, 4, 4), ScoreRules.points(intArrayOf(1, 1, 1, 1)))
    }

    @Test(expected = IllegalArgumentException::class) fun onePlayerRejected() {
        ScoreRules.points(intArrayOf(1))
    }

    @Test(expected = IllegalArgumentException::class) fun fivePlayersRejected() {
        ScoreRules.points(intArrayOf(1, 2, 3, 4, 5))
    }

    @Test(expected = IllegalArgumentException::class) fun rankOutOfRangeRejected() {
        ScoreRules.points(intArrayOf(1, 3))
    }

    @Test fun lastPlace() {
        assertEquals(listOf(2), ScoreRules.lastPlace(intArrayOf(1, 2, 3)))
        assertEquals(listOf(1, 3), ScoreRules.lastPlace(intArrayOf(1, 3, 2, 3)))
        assertTrue(ScoreRules.lastPlace(intArrayOf(1, 1)).isEmpty())
    }

    @Test fun competitionRanking() {
        assertArrayEquals(intArrayOf(2, 1, 2, 4), Ranking.competition(listOf(5L, 9L, 5L, 1L)))
        assertArrayEquals(intArrayOf(1, 3, 1), Ranking.competition(listOf(1L, 7L, 1L), higherIsBetter = false))
    }
}
