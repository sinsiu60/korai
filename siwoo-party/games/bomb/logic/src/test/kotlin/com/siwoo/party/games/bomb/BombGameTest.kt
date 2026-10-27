package com.siwoo.party.games.bomb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BombGameTest {

    /** 한 라운드: 시작 → 불붙이기(t0) → passes 번 넘기기(1초 간격) → 펑. 터진 사람을 돌려준다 */
    private fun playRound(g: BombGame, t0: Long, passes: Int): Int {
        g.startRound()
        g.light(t0)
        for (k in 1..passes) assertTrue(g.pass(t0 + k * 1000L))
        return g.explode()
    }

    @Test fun `기본 주제는 30개, 중복·빈 글자 없음, 길이 제한 이내`() {
        assertEquals(30, BombTopics.DEFAULT.size)
        assertEquals(30, BombTopics.DEFAULT.toSet().size)
        assertTrue(BombTopics.DEFAULT.all { it.isNotBlank() && it.length <= GameConfig.MAX_TOPIC_LEN })
        assertTrue(BombTopics.DEFAULT.size <= GameConfig.MAX_TOPICS)
        assertTrue("예시 주제 포함", BombTopics.DEFAULT.containsAll(listOf("과일 이름", "동물 이름", "나라 이름", "ㄱ으로 시작하는 단어")))
    }

    @Test fun `폭발 시간은 항상 20~60초`() {
        val r = Random(11)
        var min = Long.MAX_VALUE
        var max = Long.MIN_VALUE
        repeat(20_000) {
            val ms = BombGame.randomFuseMs(r)
            assertTrue(ms in GameConfig.FUSE_MIN_MS..GameConfig.FUSE_MAX_MS)
            min = minOf(min, ms); max = maxOf(max, ms)
        }
        assertTrue("고르게 퍼짐", min < 21_000 && max > 59_000)
        // 라운드 시작 때도 같은 범위
        val g = BombGame(3, random = Random(2))
        repeat(GameConfig.BOMB_ROUNDS) {
            val plan = g.startRound()
            assertTrue(plan.fuseMs in GameConfig.FUSE_MIN_MS..GameConfig.FUSE_MAX_MS)
            g.light(0); g.explode()
        }
    }

    @Test fun `넘기기는 0_8초 동안 다시 안 됨`() {
        val g = BombGame(3, random = Random(1))
        g.startRound()
        assertFalse("불붙기 전에는 못 넘김", g.pass(0))
        g.light(1000)
        val first = g.holder
        assertTrue(g.pass(1000))
        assertEquals(BombGame.nextHolder(first, 3), g.holder)
        assertFalse("바로 다시 누르면 무시", g.pass(1001))
        assertFalse("0.799초 뒤도 무시", g.pass(1000 + GameConfig.BOMB_PASS_COOLDOWN_MS - 1))
        assertFalse(g.canPass(1000 + GameConfig.BOMB_PASS_COOLDOWN_MS - 1))
        assertEquals("무시된 넘기기는 차례를 안 바꿈", BombGame.nextHolder(first, 3), g.holder)
        assertTrue(g.canPass(1000 + GameConfig.BOMB_PASS_COOLDOWN_MS))
        assertTrue("0.8초 지나면 됨", g.pass(1000 + GameConfig.BOMB_PASS_COOLDOWN_MS))
        assertEquals(2, g.passes)
        assertEquals(800L, GameConfig.BOMB_PASS_COOLDOWN_MS)
    }

    @Test fun `다음 사람은 순서대로, 마지막 다음은 처음`() {
        assertEquals(1, BombGame.nextHolder(0, 4))
        assertEquals(3, BombGame.nextHolder(2, 4))
        assertEquals(0, BombGame.nextHolder(3, 4))
        assertEquals(0, BombGame.nextHolder(1, 2))
        val g = BombGame(4, random = Random(5))
        g.startRound(); g.light(0)
        val start = g.holder
        val seen = ArrayList<Int>()
        for (k in 1..8) { g.pass(k * 1000L); seen.add(g.holder) }
        assertEquals((1..8).map { (start + it) % 4 }, seen)
    }

    @Test fun `라운드 첫 사람은 직전에 터진 사람의 다음 사람`() {
        val g = BombGame(3, random = Random(9))
        val p1 = g.startRound()
        assertTrue(p1.firstHolder in 0..2)
        g.light(0)
        g.pass(1000) // 다음 사람이 들고 있다가 펑
        val victim = g.explode()
        assertEquals((p1.firstHolder + 1) % 3, victim)
        val p2 = g.startRound()
        assertEquals((victim + 1) % 3, p2.firstHolder)
        assertEquals(p2.firstHolder, g.holder)
    }

    @Test fun `5라운드 집계, 해골은 터질 때 든 사람에게`() {
        val g = BombGame(3, random = Random(4))
        val expected = IntArray(3)
        val passCounts = intArrayOf(0, 2, 1, 4, 5)
        for ((r, n) in passCounts.withIndex()) {
            assertFalse(g.gameOver)
            val plan = g.startRound()
            assertEquals(r + 1, g.roundNumber)
            val holderAtEnd = (plan.firstHolder + n) % 3
            val v = playRoundFrom(g, n)
            assertEquals(holderAtEnd, v)
            expected[v]++
            assertEquals(Phase.EXPLODED, g.phase)
            assertEquals(r + 1, g.roundNumber)
        }
        assertTrue(g.gameOver)
        assertEquals(5, g.roundsDone)
        assertArrayEquals(expected, g.skulls)
        assertEquals(5, g.skulls.sum())
        assertEquals(expectedRanks(expected), g.finalRanks().toList())
    }

    /** startRound 를 이미 부른 뒤: 불붙이고 n 번 넘기고 펑 */
    private fun playRoundFrom(g: BombGame, n: Int): Int {
        g.light(0)
        for (k in 1..n) assertTrue(g.pass(k * 1000L))
        return g.explode()
    }

    /** 해골 적은 순 경쟁 순위 (테스트용 따로 계산) */
    private fun expectedRanks(skulls: IntArray): List<Int> = skulls.map { s -> 1 + skulls.count { it < s } }

    @Test fun `순위는 해골 적은 순, 같으면 공동 순위`() {
        // 4인: 해골 A2 B0 C2 D1 → B 1등, D 2등, A·C 공동 3등
        val g = BombGame(4, random = Random(3))
        val victims = intArrayOf(0, 2, 3, 0, 2)
        for (v in victims) forceVictim(g, v)
        assertArrayEquals(intArrayOf(2, 0, 2, 1), g.skulls)
        assertArrayEquals(intArrayOf(3, 1, 3, 2), g.finalRanks())
        // 2인 동점 없음 3:2
        val h = BombGame(2, random = Random(3))
        for (v in intArrayOf(0, 1, 0, 1, 0)) forceVictim(h, v)
        assertArrayEquals(intArrayOf(2, 1), h.finalRanks())
        // 3인 1:2:2 → 1, 2, 2
        val k = BombGame(3, random = Random(3))
        for (v in intArrayOf(1, 2, 1, 2, 0)) forceVictim(k, v)
        assertArrayEquals(intArrayOf(1, 2, 2), k.finalRanks())
    }

    /** 원하는 사람이 들고 있을 때 터지도록 넘긴다 */
    private fun forceVictim(g: BombGame, victim: Int) {
        g.startRound()
        g.light(0)
        var t = 0L
        while (g.holder != victim) { t += 1000; assertTrue(g.pass(t)) }
        assertEquals(victim, g.explode())
    }

    @Test fun `째깍 간격과 흔들림은 남은 비율이 줄수록 빨라짐`() {
        var prevTick = Long.MAX_VALUE
        var prevShake = -1f
        for (k in 100 downTo 0) {
            val r = k / 100.0
            val tick = BombGame.tickIntervalMs(r)
            val shake = BombGame.shakeLevel(r)
            assertTrue("째깍 간격은 줄거나 같음 ($r)", tick <= prevTick)
            assertTrue("흔들림은 커지거나 같음 ($r)", shake >= prevShake)
            prevTick = tick; prevShake = shake
        }
        assertEquals(GameConfig.TICK_SLOW_MS, BombGame.tickIntervalMs(1.0))
        assertEquals(GameConfig.TICK_FAST_MS, BombGame.tickIntervalMs(0.0))
        assertTrue("전체적으로 확실히 빨라짐", BombGame.tickIntervalMs(0.1) < BombGame.tickIntervalMs(0.5))
        assertTrue(BombGame.tickIntervalMs(0.5) < BombGame.tickIntervalMs(0.9))
        assertTrue(BombGame.shakeLevel(0.1) > BombGame.shakeLevel(0.5))
        assertTrue(BombGame.shakeLevel(0.5) > BombGame.shakeLevel(0.9))
        assertEquals(GameConfig.SHAKE_MIN.toFloat(), BombGame.shakeLevel(1.0), 1e-6f)
        assertEquals(1f, BombGame.shakeLevel(0.0), 1e-6f)
        // 범위 밖 값은 잘라서
        assertEquals(GameConfig.TICK_SLOW_MS, BombGame.tickIntervalMs(1.5))
        assertEquals(GameConfig.TICK_FAST_MS, BombGame.tickIntervalMs(-0.2))
    }

    @Test fun `남은 비율은 게임 시간에 따라 줄고 시간이 되면 폭발`() {
        val g = BombGame(2, random = Random(8))
        val plan = g.startRound()
        assertEquals(1.0, g.remainingRatio(0), 1e-9)
        g.light(10_000)
        assertEquals(1.0, g.remainingRatio(10_000), 1e-9)
        assertEquals(0.5, g.remainingRatio(10_000 + plan.fuseMs / 2), 0.001)
        assertFalse(g.isDue(10_000 + plan.fuseMs - 1))
        assertTrue(g.isDue(10_000 + plan.fuseMs))
        assertEquals(0.0, g.remainingRatio(10_000 + plan.fuseMs + 5000), 1e-9)
    }

    @Test fun `주제가 5개 이상이면 한 게임 안에서 겹치지 않음`() {
        for (seed in 0 until 200) {
            val g = BombGame(2, BombTopics.DEFAULT, Random(seed))
            repeat(GameConfig.BOMB_ROUNDS) { playRound(g, 0, it) }
            assertEquals(GameConfig.BOMB_ROUNDS, g.topicsUsed.toSet().size)
        }
        // 딱 5개면 5개 모두 한 번씩
        val five = listOf("가", "나", "다", "라", "마")
        for (seed in 0 until 50) {
            val g = BombGame(3, five, Random(seed))
            repeat(GameConfig.BOMB_ROUNDS) { playRound(g, 0, 1) }
            assertEquals(five.toSet(), g.topicsUsed.toSet())
        }
    }

    @Test fun `주제가 적으면 다시 섞되 연달아 같은 주제는 피함`() {
        val two = listOf("가", "나")
        for (seed in 0 until 50) {
            val d = TopicDeck(two, Random(seed))
            var prev = ""
            repeat(10) {
                val t = d.next()
                assertTrue(t != prev)
                prev = t
            }
        }
        val one = TopicDeck(listOf("하나"), Random(1))
        assertEquals("하나", one.next())
        assertEquals("하나", one.next())
    }

    @Test fun `주제 정리·저장 형식`() {
        val raw = listOf("  과일 이름 ", "", "과일 이름", "   ", "가".repeat(40), "동물")
        val s = BombTopics.sanitize(raw)
        assertEquals(listOf("과일 이름", "가".repeat(GameConfig.MAX_TOPIC_LEN), "동물"), s)
        assertEquals("비면 기본 주제", BombTopics.DEFAULT, BombTopics.sanitize(listOf(" ", "")))
        val many = (1..100).map { "주제$it" }
        assertEquals(GameConfig.MAX_TOPICS, BombTopics.sanitize(many).size)
        // JSON 왕복
        val list = listOf("과일 이름", "\"기\"로 끝나는 단어", "ㄱ으로 시작")
        assertEquals(list, BombTopics.decode(BombTopics.encode(list)))
        assertEquals(BombTopics.DEFAULT, BombTopics.decode(null))
        assertEquals(BombTopics.DEFAULT, BombTopics.decode("[깨진"))
        assertEquals(BombTopics.DEFAULT, BombTopics.decode("{\"a\":1}"))
        assertEquals(BombTopics.DEFAULT, BombTopics.decode("[]"))
    }

    @Test fun `진행 중에는 새 라운드 못 시작, 5라운드 뒤 끝`() {
        val g = BombGame(2, random = Random(1))
        g.startRound()
        assertTrue(runCatching { g.startRound() }.isFailure)
        g.light(0)
        assertTrue(runCatching { g.startRound() }.isFailure)
        g.explode()
        repeat(4) { playRound(g, 0, 0) }
        assertTrue(g.gameOver)
        assertTrue(runCatching { g.startRound() }.isFailure)
    }
}
