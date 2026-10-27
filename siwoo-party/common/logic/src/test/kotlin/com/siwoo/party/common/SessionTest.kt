package com.siwoo.party.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private fun session4() = Session(listOf(
        PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3),
    ))

    @Test fun resultsAccumulate() {
        val s = session4()
        // 플레이어 순서와 다른 순서로 결과가 와도 id 로 반영
        assertArrayEquals(intArrayOf(4, 2, 1, 0), s.applyResult("marble", intArrayOf(2, 0, 3, 1), intArrayOf(1, 2, 3, 4)))
        s.applyResult("marble", intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 3, 4))
        assertEquals(2 + 4, s.score(0))
        assertEquals(0 + 4, s.score(1))
        assertEquals(4 + 1, s.score(2))
        assertEquals(1 + 0, s.score(3))
        assertEquals(2, s.timesPlayed("marble"))
        assertEquals(listOf("가람"), s.leaders().map { it.name })
        assertArrayEquals(intArrayOf(1, 3, 2, 4), s.standings())
    }

    @Test fun noLeaderBeforeAnyPoints() {
        assertTrue(session4().leaders().isEmpty())
    }

    @Test fun tiedLeadersAllGetCrown() {
        val s = Session(listOf(PlayerInfo(0, "A", 0), PlayerInfo(1, "B", 1)))
        s.applyResult("marble", intArrayOf(0, 1), intArrayOf(1, 1))
        assertEquals(2, s.leaders().size)
    }

    @Test(expected = IllegalArgumentException::class) fun unknownPlayerRejected() {
        session4().applyResult("marble", intArrayOf(0, 9), intArrayOf(1, 2))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateColorRejected() {
        Session(listOf(PlayerInfo(0, "A", 1), PlayerInfo(1, "B", 1)))
    }

    @Test fun codecRoundTrip() {
        val s = session4()
        s.applyResult("marble", intArrayOf(0, 1, 2, 3), intArrayOf(2, 1, 4, 3))
        s.replacePlayers(listOf("가람" to 0, "나래" to 1, "마루" to 4))
        val t = SessionCodec.decode(SessionCodec.encode(s))
        assertEquals(listOf("가람", "나래", "마루"), t.players.map { it.name })
        assertEquals(listOf("다온", "라온"), t.former.map { it.name })
        assertEquals(s.scores, t.scores)
        assertEquals(1, t.history.size)
        assertArrayEquals(intArrayOf(2, 1, 4, 3), t.history[0].ranks)
        assertArrayEquals(intArrayOf(2, 4, 0, 1), t.history[0].points)
        assertEquals(s.nextId, t.nextId)
    }

    @Test fun replacePlayersKeepsScoresByName() {
        val s = session4()
        s.applyResult("marble", intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3, 4)) // 가람4 나래2 다온1 라온0
        // 라온 빠지고 마루 합류, 나래는 색만 바꿈
        s.replacePlayers(listOf("나래" to 5, "가람" to 0, "마루" to 3))
        assertEquals(listOf("나래", "가람", "마루"), s.players.map { it.name })
        assertEquals(2, s.score(s.players[0].id))
        assertEquals(4, s.score(s.players[1].id))
        assertEquals(0, s.score(s.players[2].id))
        assertEquals(1, s.players[0].id) // 번호 유지
        assertTrue(s.players[2].id >= 4) // 새 번호
        // 다온이 돌아오면 점수 복구
        s.replacePlayers(listOf("나래" to 5, "가람" to 0, "다온" to 2))
        assertEquals(1, s.score(s.players[2].id))
    }

    @Test fun playersRoundTrip() {
        val list = session4().players
        val back = SessionCodec.decodePlayers(SessionCodec.encodePlayers(list))
        assertEquals(list.map { it.id to it.name }, back.map { it.id to it.name })
        assertEquals(list.map { it.colorIndex }, back.map { it.colorIndex })
    }

    @Test fun `점수 반영 끔이면 기록만 남고 점수는 그대로`() {
        val s = session4()
        val pts = s.applyResult("liar", intArrayOf(0, 1, 2, 3), intArrayOf(1, 2, 3, 4), scored = false)
        assertArrayEquals(intArrayOf(0, 0, 0, 0), pts)
        assertEquals(0, s.score(0))
        assertEquals(1, s.history.size)
        assertEquals(false, s.history[0].scored)
        // 저장해도 유지
        val back = SessionCodec.decode(SessionCodec.encode(s))
        assertEquals(false, back.history[0].scored)
        assertEquals(0, back.score(0))
    }

    @Test fun `최근 게임은 최근 것부터 중복 없이`() {
        val s = session4()
        val ids = intArrayOf(0, 1, 2, 3)
        val r = intArrayOf(1, 2, 3, 4)
        for (g in listOf("a", "b", "a", "c", "c", "d")) s.applyResult(g, ids, r)
        assertEquals(listOf("d", "c", "a"), s.recentGames(3))
        assertEquals(listOf("d", "c", "a", "b"), s.recentGames(9))
    }
}
