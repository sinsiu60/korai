package com.siwoo.party.games.forehead

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ForeheadGameTest {

    private val g = TiltMath.GRAVITY

    /** rotation 화면에서 pitch(도) 만큼 기울인 가속도 (x, y, z) */
    private fun accel(pitchDeg: Float, rotation: Int, scale: Float = 1f): FloatArray {
        val r = Math.toRadians(pitchDeg.toDouble())
        val up = (cos(r) * g * scale).toFloat()
        val z = (sin(r) * g * scale).toFloat()
        return when (rotation) {
            0 -> floatArrayOf(0f, up, z)
            1 -> floatArrayOf(up, 0f, z)
            2 -> floatArrayOf(0f, -up, z)
            else -> floatArrayOf(-up, 0f, z)
        }
    }

    private fun classify(pitch: Float, rotation: Int = 1): Tilt {
        val a = accel(pitch, rotation)
        return TiltMath.classify(a[0], a[1], a[2], rotation)
    }

    private fun TiltDetector.feed(pitch: Float, now: Long, rotation: Int = 1): TiltAction? {
        val a = accel(pitch, rotation)
        return update(a[0], a[1], a[2], rotation, now)
    }

    // ── 기울기 판정 ──

    @Test fun `똑바로 세우면 가운데, 아래로 기울이면 정답 쪽, 위로 기울이면 패스 쪽`() {
        assertEquals(Tilt.NEUTRAL, classify(0f))
        assertEquals(Tilt.NEUTRAL, classify(20f))
        assertEquals(Tilt.NEUTRAL, classify(-20f))
        assertEquals(Tilt.DOWN, classify(-60f))
        assertEquals(Tilt.DOWN, classify(-90f))
        assertEquals(Tilt.UP, classify(60f))
        assertEquals(Tilt.UP, classify(90f))
        assertEquals(Tilt.BETWEEN, classify(35f))
        assertEquals(Tilt.BETWEEN, classify(-35f))
    }

    @Test fun `임계값 경계 50도 동작, 25도 안쪽 가운데, 155도 넘으면 거꾸로라 무시`() {
        assertEquals(Tilt.DOWN, TiltMath.classifyPitch(-GameConfig.TILT_TRIGGER_DEG))
        assertEquals(Tilt.BETWEEN, TiltMath.classifyPitch(-GameConfig.TILT_TRIGGER_DEG + 0.5f))
        assertEquals(Tilt.UP, TiltMath.classifyPitch(GameConfig.TILT_TRIGGER_DEG))
        assertEquals(Tilt.BETWEEN, TiltMath.classifyPitch(GameConfig.TILT_TRIGGER_DEG - 0.5f))
        assertEquals(Tilt.NEUTRAL, TiltMath.classifyPitch(GameConfig.TILT_NEUTRAL_DEG - 0.5f))
        assertEquals(Tilt.BETWEEN, TiltMath.classifyPitch(GameConfig.TILT_NEUTRAL_DEG))
        assertEquals(Tilt.INVALID, TiltMath.classifyPitch(170f))
        assertEquals(Tilt.INVALID, TiltMath.classifyPitch(-170f))
        assertEquals(Tilt.INVALID, classify(180f))
    }

    @Test fun `가로 방향 90도와 270도는 x 부호가 반대 - 같은 자세는 같은 판정`() {
        // 90° 화면: 똑바로 세우면 x = +g
        assertEquals(Tilt.NEUTRAL, TiltMath.classify(g, 0f, 0f, 1))
        assertEquals(Tilt.INVALID, TiltMath.classify(-g, 0f, 0f, 1)) // 270° 자세를 90° 로 읽으면 거꾸로
        // 270° 화면: 똑바로 세우면 x = -g
        assertEquals(Tilt.NEUTRAL, TiltMath.classify(-g, 0f, 0f, 3))
        assertEquals(Tilt.INVALID, TiltMath.classify(g, 0f, 0f, 3))
        // 화면이 바닥을 보면(z = -g) 어느 방향이든 정답 쪽, 천장(z = +g)은 패스 쪽
        for (rot in 0..3) {
            assertEquals(Tilt.DOWN, TiltMath.classify(0f, 0f, -g, rot))
            assertEquals(Tilt.UP, TiltMath.classify(0f, 0f, g, rot))
            assertEquals(Tilt.DOWN, classify(-70f, rot))
            assertEquals(Tilt.UP, classify(70f, rot))
            assertEquals(Tilt.NEUTRAL, classify(5f, rot))
        }
        assertEquals(-90f, TiltMath.pitch(0f, 0f, -g, 1), 0.01f)
        assertEquals(0f, TiltMath.pitch(-g, 0f, 0f, 3), 0.01f)
        assertEquals(TiltMath.up(3f, 5f, 1), -TiltMath.up(3f, 5f, 3), 0f)
    }

    @Test fun `흔들어서 중력 크기가 이상하면 판정하지 않음`() {
        val a = accel(-80f, 1, 2.2f)
        assertEquals(Tilt.INVALID, TiltMath.classify(a[0], a[1], a[2], 1))
        val b = accel(-80f, 1, 0.2f)
        assertEquals(Tilt.INVALID, TiltMath.classify(b[0], b[1], b[2], 1))
    }

    @Test fun `히스테리시스 - 처음엔 가운데를 거쳐야 하고, 한 번 기울이면 한 번만 동작`() {
        val d = TiltDetector(smoothing = 1f, minGapMs = 0)
        // 처음부터 아래로 기울어 있으면 동작 안 함 (잠김)
        assertNull(d.feed(-80f, 0))
        assertFalse(d.armed)
        assertNull(d.feed(0f, 100))
        assertTrue(d.armed)
        assertEquals(TiltAction.CORRECT, d.feed(-60f, 200))
        // 그대로 기울인 채 / 더 기울여도 다시 안 나옴
        assertNull(d.feed(-80f, 300))
        assertNull(d.feed(-55f, 400))
        // 동작 각도 아래(40°)로 조금 돌아왔다 다시 넘어가도 가운데(25°)를 안 지났으면 안 나옴
        assertNull(d.feed(-40f, 500))
        assertNull(d.feed(-60f, 600))
        // 가운데로 돌아오면 다시 가능
        assertNull(d.feed(-10f, 700))
        assertEquals(TiltAction.PASS, d.feed(70f, 800))
        assertNull(d.feed(80f, 900))
        assertNull(d.feed(0f, 1000))
        assertEquals(TiltAction.CORRECT, d.feed(-90f, 1100))
    }

    @Test fun `경계 근처에서 떨려도 동작은 한 번`() {
        val d = TiltDetector(smoothing = 1f, minGapMs = 0)
        d.feed(0f, 0)
        var n = 0
        for (k in 0 until 40) {
            val p = if (k % 2 == 0) -51f else -45f
            if (d.feed(p, 10L + k * 10) != null) n++
        }
        assertEquals(1, n)
    }

    @Test fun `동작 사이 최소 간격`() {
        val d = TiltDetector(smoothing = 1f, minGapMs = 350)
        d.feed(0f, 0)
        assertEquals(TiltAction.CORRECT, d.feed(-70f, 1000))
        d.feed(0f, 1100)
        assertNull("간격 안", d.feed(70f, 1200))
        d.feed(0f, 1300)
        assertEquals(TiltAction.PASS, d.feed(70f, 1400))
    }

    @Test fun `필터가 있으면 한 번 튄 값으로는 동작하지 않음`() {
        val d = TiltDetector()
        repeat(5) { d.feed(0f, it * 20L) }
        assertNull("한 번 튐", d.feed(-90f, 120))
        d.feed(0f, 140)
        var got: TiltAction? = null
        for (k in 0 until 10) got = got ?: d.feed(-90f, 200L + k * 20)
        assertEquals(TiltAction.CORRECT, got)
    }

    @Test fun `똑바로 든 시간 - 가운데일 때만 늘고 기울이면 0`() {
        val d = TiltDetector(smoothing = 1f)
        assertEquals(0L, d.uprightMs(0))
        d.feed(0f, 100)
        d.feed(5f, 400)
        assertEquals(500L, d.uprightMs(600))
        d.feed(40f, 700)
        assertEquals(0L, d.uprightMs(800))
        d.reset()
        assertFalse(d.armed)
        assertEquals(0L, d.uprightMs(900))
    }

    // ── 제시어 ──

    @Test fun `기본 제시어는 6 카테고리 × 30단어, 중복·빈 글자 없음`() {
        assertEquals(listOf("동물", "음식", "직업", "장소", "물건", "동작"), ForeheadWords.CATEGORIES)
        assertEquals(GameConfig.CATEGORY_COUNT, ForeheadWords.DEFAULT.size)
        for (l in ForeheadWords.DEFAULT) {
            assertEquals(GameConfig.DEFAULT_WORDS, l.size)
            assertEquals(l.size, l.toSet().size)
            assertTrue(l.all { it.isNotBlank() && it.length <= GameConfig.MAX_WORD_LEN })
        }
        val all = ForeheadWords.DEFAULT.flatten()
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun `편집한 목록 저장·불러오기, 모자라면 기본 단어로 채움`() {
        val mine = ForeheadWords.DEFAULT.toMutableList()
        mine[0] = listOf(" 얼룩말 ", "", "얼룩말", "카피바라")
        val back = ForeheadWords.decode(ForeheadWords.encode(ForeheadWords.sanitizeAll(mine)))
        assertEquals(GameConfig.MIN_WORDS, back[0].size)
        assertEquals("얼룩말", back[0][0])
        assertEquals("카피바라", back[0][1])
        assertEquals(ForeheadWords.DEFAULT[1], back[1])
        assertEquals(ForeheadWords.DEFAULT, ForeheadWords.decode(null))
        assertEquals(ForeheadWords.DEFAULT, ForeheadWords.decode("{깨진"))
    }

    @Test fun `한 게임 안에서는 같은 단어가 다시 나오지 않음 - 카테고리 단어를 다 쓰면 다른 카테고리에서`() {
        val p = WordPicker(ForeheadWords.DEFAULT, 0, Random(1))
        val seen = HashSet<String>()
        repeat(30) { assertTrue(seen.add(p.next())) }
        assertTrue(seen.all { it in ForeheadWords.DEFAULT[0] })
        // 31번째부터는 다른 카테고리, 여전히 중복 없음
        repeat(150) { assertTrue(seen.add(p.next())) }
        assertEquals(180, seen.size)
        // 전부 다 쓴 뒤에만 다시 섞어 나옴
        assertNotNull(p.next())
    }

    @Test fun `게임 전체 진행 중 나온 단어 중복 없음`() {
        val game = ForeheadGame(4, ForeheadWords.DEFAULT, Random(7))
        game.setup(GameConfig.MIXED, 2)
        do {
            game.beginPlay()
            repeat(15) { game.answer(it % 3 != 0) }
            game.timeUp()
        } while (game.nextTurn())
        assertEquals(game.shownWords.size, game.shownWords.toSet().size)
        assertEquals(8 * 16, game.shownWords.size)
    }

    // ── 점수·순위·판 수 ──

    @Test fun `맞힌 개수가 점수, 시간 끝에 보던 단어는 TIMEOUT`() {
        val game = ForeheadGame(3, ForeheadWords.DEFAULT, Random(2))
        game.setup(1, 1)
        assertEquals(Phase.READY, game.phase)
        assertEquals(0, game.current)
        val w0 = game.beginPlay()
        val w1 = game.answer(true)
        val w2 = game.answer(false)
        game.answer(true)
        assertEquals(2, game.correctCount)
        assertEquals(1, game.passCount)
        assertEquals(2, game.timeUp())
        assertEquals(Phase.REVIEW, game.phase)
        val r = game.turnResults
        assertEquals(listOf(w0, w1, w2), r.take(3).map { it.word })
        assertEquals(listOf(Answer.CORRECT, Answer.PASS, Answer.CORRECT, Answer.TIMEOUT), r.map { it.answer })
        assertTrue(r.take(3).all { it.word in ForeheadWords.DEFAULT[1] })
        assertArrayEquals(intArrayOf(2, 0, 0), game.scores)
        assertTrue(game.nextTurn())
        assertEquals(1, game.current)
        assertTrue(game.turnResults.isEmpty())
        // 정답 화면 도중 시간 끝 → 아직 안 보인 다음 단어는 목록에 없음
        game.beginPlay()
        game.answer(true)
        assertEquals(1, game.timeUp(currentShown = false))
        assertEquals(listOf(Answer.CORRECT), game.turnResults.map { it.answer })
        assertArrayEquals(intArrayOf(2, 1, 0), game.scores)
    }

    private fun play(game: ForeheadGame, perTurn: List<Int>) {
        var k = 0
        do {
            game.beginPlay()
            repeat(perTurn[k]) { game.answer(true) }
            game.answer(false)
            game.timeUp()
            k++
        } while (game.nextTurn())
        assertEquals(perTurn.size, k)
        assertEquals(Phase.DONE, game.phase)
    }

    @Test fun `한 바퀴 - 모두 한 번씩, 합산 순위`() {
        val game = ForeheadGame(3, ForeheadWords.DEFAULT, Random(3))
        game.setup(GameConfig.MIXED, 1)
        assertEquals(3, game.totalTurns)
        play(game, listOf(4, 7, 2))
        assertArrayEquals(intArrayOf(4, 7, 2), game.scores)
        assertArrayEquals(intArrayOf(2, 1, 3), game.ranks())
    }

    @Test fun `두 바퀴 - 차례 순서 0 1 2 0 1 2, 바퀴 번호, 차례별 점수 합산`() {
        val game = ForeheadGame(3, ForeheadWords.DEFAULT, Random(4))
        game.setup(2, 2)
        assertEquals(6, game.totalTurns)
        val order = ArrayList<Int>()
        val laps = ArrayList<Int>()
        val per = listOf(1, 2, 3, 3, 2, 1)
        var k = 0
        do {
            order.add(game.current); laps.add(game.lap)
            game.beginPlay()
            repeat(per[k++]) { game.answer(true) }
            game.timeUp()
        } while (game.nextTurn())
        assertEquals(listOf(0, 1, 2, 0, 1, 2), order)
        assertEquals(listOf(1, 1, 1, 2, 2, 2), laps)
        assertArrayEquals(intArrayOf(4, 4, 4), game.scores)
        assertEquals(listOf(1, 3), game.turnScores[0])
        assertArrayEquals("모두 동점이면 모두 1등", intArrayOf(1, 1, 1), game.ranks())
    }

    @Test fun `동점은 같은 순위 - 4인`() {
        val game = ForeheadGame(4, ForeheadWords.DEFAULT, Random(5))
        game.setup(0, 1)
        play(game, listOf(5, 3, 5, 1))
        assertArrayEquals(intArrayOf(1, 3, 1, 4), game.ranks())
    }

    @Test fun `판 수 설정은 1~2바퀴로 제한, 카테고리 번호가 이상하면 모두 섞기`() {
        val a = ForeheadGame(3, ForeheadWords.DEFAULT)
        a.setup(9, 5)
        assertEquals(GameConfig.LAPS_MAX, a.laps)
        assertEquals(GameConfig.MIXED, a.category)
        assertEquals("모두 섞기", a.categoryName)
        val b = ForeheadGame(4, ForeheadWords.DEFAULT)
        b.setup(5, 0)
        assertEquals(GameConfig.LAPS_MIN, b.laps)
        assertEquals(4, b.totalTurns)
        assertEquals("동작", b.categoryName)
    }
}
