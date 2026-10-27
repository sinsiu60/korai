package com.siwoo.party.games.simon.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.simon.GameConfig
import com.siwoo.party.games.simon.Phase
import com.siwoo.party.games.simon.SimonModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 패턴 기억 릴레이 화면: 2·3·4인 끝까지 진행 후 순위, 틀림·시간 초과 탈락, 일시정지 중 시간 멈춤 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SimonUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { SimonActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(400) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int = 3): SimonActivity {
        SimonActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, SimonModule, players)
        return Robolectric.buildActivity(SimonActivity::class.java, i).setup().get()
    }

    private fun tap(a: SimonActivity, color: Int) {
        val pt = PointF()
        a.board.padCenter(color, pt)
        val x = pt.x + 7f // 가운데에서 조금 비켜 눌러도 그 버튼
        val y = pt.y - 4f
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun waitTurn(a: SimonActivity) = waitFor("차례 시작") { !a.busy && a.game.accepting }

    /** 지금 차례: 순서를 그대로 누르고 add 를 더한다 */
    private fun goodTurn(a: SimonActivity, add: Int) {
        waitTurn(a)
        val p = a.game.current
        val seq = a.game.sequence.toList()
        for ((k, c) in seq.withIndex()) {
            tap(a, c)
            assertEquals("${k + 1} / ${seq.size}", a.board.progress)
            idle(30)
        }
        assertEquals(Phase.ADD, a.game.phase)
        assertTrue(a.board.adding)
        assertTrue(a.turnCard.hintText.startsWith("+1"))
        assertTrue(a.turnCard.hintStrong)
        tap(a, add)
        assertTrue(a.busy)
        assertEquals(seq.size + 1, a.game.length)
        assertEquals((seq.size + 1).toString(), a.board.lengthText)
        assertTrue(a.game.alive[p])
    }

    /** 지금 차례: (순서가 있으면) 첫 색을 틀리게 → 탈락. 탈락한 사람을 돌려준다 */
    private fun wrongTurn(a: SimonActivity): Int {
        waitTurn(a)
        val p = a.game.current
        val right = a.game.expected
        assertTrue("순서가 있어야 틀릴 수 있다", right >= 0)
        tap(a, (right + 1) % GameConfig.COLORS)
        assertFalse(a.game.alive[p])
        assertTrue(a.busy)
        assertEquals((right + 1) % GameConfig.COLORS, a.board.errorPad)
        assertEquals(0.5f, a.strip.tiles[p].alpha, 0.01f)
        return p
    }

    /** 지금 차례: 아무것도 누르지 않고 5초 → 시간 초과 탈락 */
    private fun timeoutTurn(a: SimonActivity): Int {
        waitTurn(a)
        val p = a.game.current
        idle(GameConfig.PRESS_LIMIT_MS - 600)
        assertTrue("아직 시간 남음", a.game.alive[p])
        assertTrue(a.timerBar.running)
        idle(800)
        assertFalse("5초 지나면 탈락", a.game.alive[p])
        assertTrue(a.busy)
        return p
    }

    private fun finish(a: SimonActivity, expectedRanks: IntArray) {
        waitFor("결과 반환") { shadowOf(a).resultCode == Activity.RESULT_OK }
        val data = shadowOf(a).resultIntent
        assertArrayEquals(IntArray(a.players.size) { it }, data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(expectedRanks, data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun twoPlayersRelayUntilOneMisses() {
        val a = launch(four.take(2), seed = 1)
        waitTurn(a)
        val first = a.game.current
        assertEquals(a.players[first].name, a.turnCard.nameText)
        assertTrue("첫 차례는 바로 새 색", a.board.adding)
        assertEquals("0", a.board.lengthText)
        goodTurn(a, 2)
        val second = a.game.current
        assertEquals(1 - first, second)
        goodTurn(a, 0)
        goodTurn(a, 3)
        assertEquals(listOf(2, 0, 3), a.game.sequence)
        val loser = wrongTurn(a)
        assertEquals(second, loser)
        assertTrue(a.game.gameOver)
        val ranks = IntArray(2)
        ranks[first] = 1
        ranks[loser] = 2
        finish(a, ranks)
    }

    @Test
    fun threePlayersWrongPressKeepsSequenceForNext() {
        val a = launch(four.take(3), seed = 2)
        goodTurn(a, 1)
        goodTurn(a, 1)
        val out1 = wrongTurn(a)
        waitTurn(a)
        // 탈락해도 순서는 그대로, 다음 살아 있는 사람이 처음부터
        assertEquals(listOf(1, 1), a.game.sequence)
        assertEquals(a.game.nextAlive(out1), a.game.current)
        assertEquals("0 / 2", a.board.progress)
        assertEquals(-1, a.board.errorPad)
        assertFalse(a.board.adding)
        goodTurn(a, 2)
        val out2 = wrongTurn(a)
        assertTrue(a.game.gameOver)
        val w = a.game.winner
        val ranks = IntArray(3)
        ranks[w] = 1
        ranks[out2] = 2
        ranks[out1] = 3
        finish(a, ranks)
    }

    @Test
    fun fourPlayersWithTimeoutAndSkips() {
        val a = launch(four, seed = 5)
        goodTurn(a, 0)
        val out1 = timeoutTurn(a)          // 순서를 누르지 않고 5초 → 탈락
        waitTurn(a)
        assertEquals(a.game.nextAlive(out1), a.game.current)
        goodTurn(a, 3)
        goodTurn(a, 2)
        // 한 바퀴 돌면 탈락한 사람은 건너뜀
        waitTurn(a)
        assertTrue(a.game.alive[a.game.current])
        val out2 = wrongTurn(a)
        waitTurn(a)
        // 새 색 추가 단계에서 시간 초과
        val p = a.game.current
        for (c in a.game.sequence.toList()) { tap(a, c); idle(30) }
        assertEquals(Phase.ADD, a.game.phase)
        idle(GameConfig.PRESS_LIMIT_MS + 300)
        assertFalse(a.game.alive[p])
        assertTrue(a.game.gameOver)
        val w = a.game.winner
        val ranks = IntArray(4)
        ranks[w] = 1
        ranks[p] = 2
        ranks[out2] = 3
        ranks[out1] = 4
        finish(a, ranks)
    }

    @Test
    fun pauseStopsTheFiveSecondClock() {
        val a = launch(four.take(3), seed = 8)
        waitTurn(a)
        val p = a.game.current
        idle(2000)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        tap(a, 1)
        assertEquals("일시정지 중 버튼 무시", 0, a.game.length)
        idle(8000)
        assertTrue("일시정지 중에는 시간 초과 없음", a.game.alive[p])
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.game.alive[p])
        tap(a, 1)
        assertEquals(1, a.game.length)
        assertEquals(listOf(1), a.game.sequence)
    }

    @Test
    fun touchesOutsidePadsAndDuringEffectsAreIgnored() {
        val a = launch(four.take(2), seed = 4)
        // 시작 배너 중
        tap(a, 0)
        assertEquals(0, a.game.length)
        waitTurn(a)
        // 가운데 원판 터치
        val c = PointF()
        a.board.hubCenter(c)
        assertEquals(-1, a.board.padAt(c.x, c.y))
        assertEquals(-1, a.board.padAt(c.x + 1f, c.y - a.board.height * 0.3f)) // 칸막이 위
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, c.x, c.y, 0))
        assertEquals(0, a.game.length)
        for (i in 0 until GameConfig.COLORS) {
            val pt = PointF()
            a.board.padCenter(i, pt)
            assertEquals(i, a.board.padAt(pt.x, pt.y))
        }
        tap(a, 3)
        assertEquals(1, a.game.length)
        assertTrue(a.board.level(3) > 0.5f)
        // 다음 차례 전환 중에는 무시
        tap(a, 2)
        assertEquals(1, a.game.length)
        assertTrue(a.tones.created <= GameConfig.COLORS)
    }

    @Test
    fun soundOffMakesNoTones() {
        val s = PartySettings(app)
        s.sound = false
        try {
            val a = launch(four.take(2), seed = 6)
            waitTurn(a)
            tap(a, 0)
            assertEquals(1, a.game.length)
            assertFalse(a.beeper.enabled)
            assertEquals("소리 끔 → 음을 만들지 않음", 0, a.tones.created)
        } finally {
            s.sound = true
        }
    }
}
