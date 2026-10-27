package com.siwoo.party.games.liardice.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.liardice.LiarDiceModule
import com.siwoo.party.games.liardice.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** 거짓말 주사위 화면: 비밀 확인(지워짐)·부르기 유효성·의심 공개·탈락, 2인 끝까지 → 순위, 일시정지, 1 와일드 설정 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class LiarDiceUiTest {

    private val two = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1))
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        LiarDiceActivity.randomOverride = null
        app.getSharedPreferences(LiarDiceSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int = 3): LiarDiceActivity {
        LiarDiceActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, LiarDiceModule, players)
        return Robolectric.buildActivity(LiarDiceActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    private fun touch(v: View, action: Int) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, v.width / 2f, v.height / 2f, 0))
    }

    private fun tapAt(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun diceText(a: LiarDiceActivity, p: Int) = (0 until a.game.diceLeft[p]).joinToString(" ") { a.game.dice[p][it].toString() }

    /**
     * 라운드 시작 확인: 한 명씩 넘기기 → 꾹 눌러 보기 → [확인 완료].
     * 확인 뒤에는 그 사람 카드 내용이 지워지고(글자·눈), 다음 사람 화면에는 다음 사람 주사위만.
     */
    private fun checkAll(a: LiarDiceActivity) {
        val g = a.game
        val n = g.aliveCount
        var prev: DiceSecret? = null
        var prevText = ""
        repeat(n) { k ->
            waitFor("넘기기 화면 ${k + 1}") { a.handoffPopup != null }
            assertNull("넘기기 화면 단계엔 비밀 카드 없음", a.secretView)
            if (prev != null) {
                assertEquals("이전 사람 카드 지워짐", "", prev!!.text)
                assertFalse(prev!!.hasDice)
            }
            all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
            idle(100)
            val hold = a.secretView!!
            val p = a.checkingPlayer
            assertTrue(p >= 0)
            assertEquals("가려진 상태로 시작", "", hold.shownText)
            assertFalse(a.confirmButton!!.isEnabled)
            val content = a.lastSecret!!
            assertEquals(diceText(a, p), content.text)
            if (prevText.isNotEmpty()) assertTrue("다음 사람 카드는 새 내용", content !== prev)
            touch(hold, MotionEvent.ACTION_DOWN)
            assertEquals(content.text, hold.shownText)
            touch(hold, MotionEvent.ACTION_UP)
            assertEquals("손 떼면 가려짐", "", hold.shownText)
            assertTrue(a.confirmButton!!.isEnabled)
            a.confirmButton!!.performClick()
            idle(50)
            assertEquals("확인 뒤 글자 지움", "", content.text)
            assertFalse(content.hasDice)
            assertNull("확인 뒤 카드 내용 참조도 지움", hold.content)
            prev = content
            prevText = content.text.ifEmpty { "x" }
        }
    }

    @Test
    fun twoPlayersPlayWholeGameThroughUi() {
        val a = launch(two, seed = 11)
        val g = a.game
        assertTrue(g.wildOnes)
        var rounds = 0
        while (g.phase != Phase.OVER) {
            waitFor("라운드 ${rounds + 1} 확인 시작") { a.handoffPopup != null }
            rounds++
            assertEquals(rounds, g.round)
            assertFalse("확인 중 입력 꺼짐", a.bidButton.isEnabled)
            checkAll(a)
            waitFor("첫 차례") { !a.busy }
            val first = g.current
            assertEquals(g.starter, first)
            assertEquals(a.players[first].name, a.nameText.text.toString())
            // 첫 사람은 의심 불가, 기본으로 가장 낮은 수가 골라져 있음
            assertFalse(a.doubtButton.isEnabled)
            assertEquals(1, a.pickCount)
            assertEquals(2, a.pickFace)
            assertTrue(a.bidButton.isEnabled)
            // 와일드: 1 눈은 고를 수 없음 (눌러도 그대로)
            assertFalse(a.face.isAllowed(1))
            tapAt(a.face, a.face.faceCenterX(1), a.face.faceCenterY(1))
            assertEquals(2, a.pickFace)
            // 눈 3 을 터치로 고르고, 개수는 + 버튼으로 2개
            tapAt(a.face, a.face.faceCenterX(3), a.face.faceCenterY(3))
            assertEquals(3, a.pickFace)
            assertFalse("1개 아래로는 못 내림", a.minusButton.isEnabled)
            a.plusButton.performClick()
            assertEquals(2, a.pickCount)
            a.bidButton.performClick()
            assertEquals(com.siwoo.party.games.liardice.Bid(2, 3), g.bid)
            assertEquals(2, a.table.bidCount)
            waitFor("둘째 차례") { !a.busy }
            val second = g.current
            assertNotEquals(first, second)
            // 낮은 수 (2개 · 눈 2) 는 [부르기] 꺼짐, 의심은 가능
            a.pick(2, 2)
            assertEquals(2, a.pickCount)
            assertFalse("직전보다 낮음", a.bidButton.isEnabled)
            a.bidButton.performClick()
            assertEquals("거절됨", com.siwoo.party.games.liardice.Bid(2, 3), g.bid)
            assertTrue(a.doubtButton.isEnabled)
            // 개수 상한 = 탁자 위 전체 주사위
            a.pick(99, 2)
            assertEquals(g.totalDice, a.pickCount)
            assertFalse(a.plusButton.isEnabled)
            // [내 주사위 보기]: 넘기기 없이 내 주사위만, 닫으면 지워짐
            a.peekButton.performClick()
            idle(100)
            assertTrue(a.peeking)
            assertNull(a.handoffPopup)
            val peekContent = a.lastSecret!!
            assertEquals(diceText(a, second), peekContent.text)
            touch(a.secretView!!, MotionEvent.ACTION_DOWN)
            touch(a.secretView!!, MotionEvent.ACTION_UP)
            a.confirmButton!!.performClick()
            idle(50)
            assertFalse(a.peeking)
            assertEquals("", peekContent.text)
            // 의심!
            val expected = g.countFace(3)
            val before = g.diceLeft.copyOf()
            a.doubtButton.performClick()
            assertTrue(a.busy)
            assertEquals(View.GONE, a.pad.visibility)
            val r = g.lastResult!!
            assertEquals(expected, r.actual)
            val loser = if (expected >= 2) second else first
            assertEquals(loser, r.loser)
            assertEquals(before[loser] - 1, g.diceLeft[loser])
            waitFor("판정") { a.table.hasVerdict }
            assertEquals(TableView.Mode.REVEAL, a.table.mode)
            assertEquals(expected, a.table.matchTotal)
            assertEquals("하나씩 다 셈", expected, a.table.counted)
            assertEquals(2, a.table.revealRows)
            if (r.eliminated) waitFor("탈락 도장") { a.strip.tiles[loser].alpha == 0.5f }
            if (!r.gameOver) {
                waitFor("다음 라운드") { g.round == rounds + 1 }
                assertEquals("진 사람부터", loser, g.starter)
                assertEquals(View.VISIBLE, a.pad.visibility)
            }
        }
        assertTrue(rounds in 5..9)
        val w = g.winner
        waitFor("결과 반환") { shadowOf(a).resultCode == Activity.RESULT_OK }
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertEquals(1, ranks[w])
        assertEquals(2, ranks[1 - w])
        assertArrayEquals(g.finalRanks(), ranks)
    }

    @Test
    fun pauseHidesSecretAndBlocksInput() {
        val a = launch(two.plus(PlayerInfo(2, "다온", 2)), seed = 5)
        waitFor("넘기기") { a.handoffPopup != null }
        all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
        val hold = a.secretView!!
        touch(hold, MotionEvent.ACTION_DOWN)
        assertTrue(hold.revealed)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        assertFalse("일시정지하면 카드 가림", hold.revealed)
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        waitFor("계속하기") { !a.gamePaused }
        touch(hold, MotionEvent.ACTION_UP)
        // 모두 확인 → 첫 차례에서 일시정지하면 부르기 무시
        a.confirmButton!!.performClick()
        repeat(2) {
            waitFor("넘기기") { a.handoffPopup != null }
            all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
            idle(100)
            touch(a.secretView!!, MotionEvent.ACTION_DOWN)
            touch(a.secretView!!, MotionEvent.ACTION_UP)
            a.confirmButton!!.performClick()
        }
        waitFor("첫 차례") { !a.busy }
        assertEquals(3, a.table.let { a.game.aliveCount })
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(a.gamePaused)
        a.bidButton.performClick()
        assertNull("일시정지 중 부르기 무시", a.game.bid)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        a.bidButton.performClick()
        assertNotNull(a.game.bid)
    }

    @Test
    fun wildSettingPopupSavesAndGameUsesIt() {
        val a = launch(two)
        assertEquals("1 와일드 설정", LiarDiceModule.settingsLabel)
        LiarDiceModule.showSettings(a.popups)
        idle(300)
        find(a.popups, "1 와일드 끔").performClick()
        assertFalse(LiarDiceSettings.wild(app))
        a.popups.dismissAll()

        val b = launch(two, seed = 4)
        assertFalse(b.game.wildOnes)
        checkAll(b)
        waitFor("첫 차례") { !b.busy }
        assertTrue("와일드 끄면 1 도 부를 수 있음", b.face.isAllowed(1))
        assertEquals(1, b.pickFace)
        b.bidButton.performClick()
        assertEquals(com.siwoo.party.games.liardice.Bid(1, 1), b.game.bid)

        LiarDiceModule.showSettings(a.popups)
        idle(300)
        find(a.popups, "1 와일드 켬").performClick()
        assertTrue(LiarDiceSettings.wild(app))
    }

    private fun find(root: View, desc: String): View = findOrNull(root, desc) ?: error("$desc 없음")

    private fun findOrNull(root: View, desc: String): View? {
        if (root.contentDescription == desc) return root
        if (root is ViewGroup) for (k in 0 until root.childCount) findOrNull(root.getChildAt(k), desc)?.let { return it }
        return null
    }
}
