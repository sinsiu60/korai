package com.siwoo.party.games.codetile.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.secret.SecretHoldView
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.codetile.CodeTileModule
import com.siwoo.party.games.codetile.GameConfig
import com.siwoo.party.games.codetile.Phase
import com.siwoo.party.games.codetile.Tiles
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 숨은 숫자 타일 화면: 비밀 확인·지움(공개 화면에 숨은 숫자 없음)·추리(정답/오답/멈춤/계속)·빈 더미 벌칙, 2·3·4인 끝까지 → 순위, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class CodeTileUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        CodeTileActivity.randomOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(800) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int): CodeTileActivity {
        CodeTileActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, CodeTileModule, players)
        return Robolectric.buildActivity(CodeTileActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    private fun touch(v: View, action: Int, x: Float = v.width / 2f, y: Float = v.height / 2f) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    private fun tapAt(v: View, x: Float, y: Float) {
        touch(v, MotionEvent.ACTION_DOWN, x, y)
        touch(v, MotionEvent.ACTION_UP, x, y)
    }

    private val pt = PointF()

    private fun tapTile(a: CodeTileActivity, p: Int, i: Int) {
        assertTrue("타일 위치", a.table.tileCenter(p, i, pt))
        tapAt(a.table, pt.x, pt.y)
    }

    private fun tapKey(a: CodeTileActivity, k: Int) = tapAt(a.pad, a.pad.keyCenterX(k), a.pad.keyCenterY(k))

    /** 공개 탁자에 숨은 숫자가 하나도 없음 + 비밀 카드가 화면 어디에도 없음 */
    private fun assertPublicOnly(a: CodeTileActivity, secretText: String = "") {
        val g = a.game
        for (p in a.players.indices) {
            assertEquals(g.rowSize(p), a.table.count(p))
            for (i in 0 until g.rowSize(p)) {
                assertEquals("색은 보임", g.isBlackAt(p, i), a.table.isBlack(p, i))
                if (g.isRevealed(p, i)) assertEquals(g.tileAt(p, i).number, a.table.shownNumber(p, i))
                else assertEquals("숨은 타일 숫자는 탁자에 없음 ($p,$i)", -1, a.table.shownNumber(p, i))
            }
        }
        val views = all(a.window.decorView)
        for (v in views) if (v is SecretHoldView) assertFalse("비밀 카드에 내용 없음", v.hasSecret)
        if (secretText.isNotEmpty()) {
            for (v in views) {
                if (v is TextView) assertFalse("글자에 비밀 없음", v.text.toString().contains(secretText))
                assertFalse(v.contentDescription?.toString()?.contains(secretText) == true)
            }
        }
    }

    /** 차례 시작: 넘기기 → 꾹 눌러 확인 → [확인 완료] → 비밀이 지워짐 */
    private fun passSecret(a: CodeTileActivity) {
        val g = a.game
        waitFor("넘기기 화면") { a.handoffPopup != null }
        assertPublicOnly(a)
        val p = g.current
        all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
        val hold = a.secretView!!
        val content = a.lastSecret!!
        val text = content.text
        // 비밀 카드에는 내 줄 숫자 전체 + 뽑은 타일
        val expect = StringBuilder()
        for (i in 0 until g.rowSize(p)) {
            if (i > 0) expect.append(' ')
            expect.append(Tiles.label(g.tileAt(p, i).code))
            if (g.isRevealed(p, i)) expect.append("(공개)")
        }
        if (g.drawn >= 0) expect.append(" / 새 ").append(Tiles.label(g.drawn))
        assertEquals(expect.toString(), text)
        assertFalse("꾹 누르기 전엔 확인 불가", a.confirmButton!!.isEnabled)
        touch(hold, MotionEvent.ACTION_DOWN)
        assertEquals(text, hold.shownText)
        touch(hold, MotionEvent.ACTION_UP)
        assertEquals("", hold.shownText)
        a.confirmButton!!.performClick()
        idle(50)
        assertFalse(a.checkingSecret)
        assertEquals("비밀 카드 내용 지움", "", content.text)
        assertFalse(content.hasTiles)
        assertNull(a.secretView)
        assertPublicOnly(a, text)
        waitFor("추리 시작") { !a.busy }
        assertEquals(Phase.GUESS, g.phase)
        assertEquals(TableView.Pick.OPPONENTS, a.table.pick)
    }

    /** 상대 타일 하나 고르기 (숨은 것 중 첫째) */
    private fun firstTarget(a: CodeTileActivity, avoid: Int = -1): Pair<Int, Int> {
        val g = a.game
        val ps = a.players.indices.map { (g.current + it) % a.players.size }
        for (p in ps) {
            if (p == avoid) continue
            for (i in 0 until g.rowSize(p)) if (g.canTarget(p, i)) return p to i
        }
        error("고를 타일 없음")
    }

    /** 고르고 숫자 누르고 [추리!] */
    private fun guess(a: CodeTileActivity, p: Int, i: Int, n: Int) {
        assertFalse(a.guessButton.isEnabled)
        tapTile(a, p, i)
        assertEquals(p, a.selPlayer)
        assertEquals(i, a.selIndex)
        assertEquals(p, a.table.selectedPlayer)
        assertTrue(a.pad.active)
        tapKey(a, n)
        assertEquals(n, a.selNumber)
        assertTrue(a.guessButton.isEnabled)
        a.guessButton.performClick()
    }

    /** 정답 추리 → 계속/멈춤 단계 또는 끝 */
    private fun guessRight(a: CodeTileActivity, avoid: Int = -1): Pair<Int, Int> {
        val (p, i) = firstTarget(a, avoid)
        val n = a.game.tileAt(p, i).number
        guess(a, p, i, n)
        assertTrue(a.game.isRevealed(p, i))
        assertEquals("뒤집힌 타일은 탁자에 숫자", n, a.table.shownNumber(p, i))
        return p to i
    }

    /** 오답 추리 (진짜 숫자도, 공개된 숫자도 아닌 수) */
    private fun guessWrong(a: CodeTileActivity) {
        val g = a.game
        val (p, i) = firstTarget(a)
        val real = g.tileAt(p, i).number
        val black = g.isBlackAt(p, i)
        val n = (0 until GameConfig.NUMBERS).first { it != real && !g.isPubliclyRevealed(Tiles.code(it, black)) }
        val me = g.current
        val drawn = g.drawn
        val before = g.rowSize(me)
        guess(a, p, i, n)
        assertFalse("오답이면 상대 타일은 그대로", g.isRevealed(p, i))
        assertEquals(-1, a.table.shownNumber(p, i))
        if (drawn >= 0) {
            assertEquals(before + 1, g.rowSize(me))
            val at = g.codes(me).indexOf(drawn)
            assertTrue("뽑은 타일 공개", g.isRevealed(me, at))
            assertEquals(g.codes(me).sorted(), g.codes(me))
            waitFor("다음 차례") { a.handoffPopup != null || g.phase == Phase.OVER }
            assertEquals(Tiles.number(drawn), a.table.shownNumber(me, at))
        } else {
            assertEquals(Phase.PENALTY, g.phase)
            waitFor("벌칙 고르기") { !a.busy }
            assertEquals(TableView.Pick.OWN, a.table.pick)
        }
    }

    /** 끝까지: 현재 사람이 계속 정답 → 모두 탈락시킴 */
    private fun sweep(a: CodeTileActivity) {
        val g = a.game
        while (g.phase != Phase.OVER) {
            waitFor("계속/끝") { !a.busy || g.phase == Phase.OVER }
            if (g.phase == Phase.OVER) break
            if (g.phase == Phase.GUESS) guessRight(a)
            else if (g.phase == Phase.DECIDE) {
                waitFor("계속 버튼") { !a.busy && a.continueButton.visibility == View.VISIBLE }
                a.continueButton.performClick()
            }
        }
    }

    private fun checkResult(a: CodeTileActivity) {
        val g = a.game
        assertEquals(Phase.OVER, g.phase)
        waitFor("결과 반환") { shadowOf(a).resultCode == Activity.RESULT_OK }
        val data = shadowOf(a).resultIntent
        assertArrayEquals(IntArray(a.players.size) { a.players[it].id }, data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(g.ranks(), ranks)
        assertEquals(1, ranks[g.winner])
        assertEquals((1..a.players.size).toList(), ranks.sorted())
    }

    @Test
    fun twoPlayersWrongThenStopThenSweep() {
        val a = launch(four.take(2), seed = 2)
        val g = a.game
        assertEquals(4, g.rowSize(0))
        assertEquals(4, g.rowSize(1))
        // 1차례: 오답 → 뽑은 타일 공개
        passSecret(a)
        val first = g.current
        guessWrong(a)
        assertEquals(1 - first, g.current)
        // 2차례: 정답 → 멈추기 → 뽑은 타일 숨긴 채
        passSecret(a)
        val me = g.current
        val drawn = g.drawn
        guessRight(a)
        waitFor("계속/멈추기") { !a.busy && a.stopButton.visibility == View.VISIBLE }
        assertEquals(View.GONE, a.guessButton.visibility)
        a.stopButton.performClick()
        val at = g.codes(me).indexOf(drawn)
        assertTrue(at >= 0)
        assertFalse("멈추면 숨긴 채", g.isRevealed(me, at))
        assertEquals(-1, a.table.shownNumber(me, at))
        assertEquals(5, g.rowSize(me))
        // 3차례: 끝까지 정답
        passSecret(a)
        val winner = g.current
        sweep(a)
        assertEquals(winner, g.winner)
        checkResult(a)
        assertArrayEquals(IntArray(2) { if (it == winner) 1 else 2 }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun threePlayersPeekAndSweep() {
        val a = launch(four.take(3), seed = 5)
        val g = a.game
        for (p in 0 until 3) assertEquals(4, g.rowSize(p))
        passSecret(a)
        // 차례 중 다시 보기 (넘기기 없이 꾹 누르기)
        assertTrue(a.peekButton.isEnabled)
        a.peekButton.performClick()
        idle(50)
        assertNull("다시 보기는 넘기기 없음", a.handoffPopup)
        val hold = a.secretView!!
        val text = a.lastSecret!!.text
        assertTrue(text.isNotEmpty())
        tapTile(a, firstTarget(a).first, firstTarget(a).second)
        assertEquals("비밀 카드가 떠 있으면 고르기 무시", -1, a.selPlayer)
        touch(hold, MotionEvent.ACTION_DOWN)
        touch(hold, MotionEvent.ACTION_UP)
        a.confirmButton!!.performClick()
        idle(50)
        assertFalse(a.checkingSecret)
        assertEquals("", a.lastSecret!!.text)
        assertPublicOnly(a, text)
        // 이미 공개된 숫자는 패드에서 못 고름 → 정답 하나 뒤 같은 색 그 숫자는 빗금
        val (p, i) = guessRight(a)
        waitFor("계속") { !a.busy }
        a.continueButton.performClick()
        assertEquals(Phase.GUESS, g.phase)
        val (p2, i2) = firstTarget(a)
        tapTile(a, p2, i2)
        val opened = g.tileAt(p, i)
        if (g.isBlackAt(p2, i2) == opened.black) {
            assertTrue(a.pad.isCrossed(opened.number))
            tapKey(a, opened.number)
            assertEquals(-1, a.selNumber)
        }
        sweep(a)
        checkResult(a)
        assertEquals(2, g.eliminationOrder.size)
    }

    @Test
    fun fourPlayersEmptyPilePenaltyToEnd() {
        val a = launch(four, seed = 9)
        val g = a.game
        for (p in 0 until 4) assertEquals(3, g.rowSize(p))
        // 더미가 빌 때까지 오답 (뽑은 타일이 공개된 채 들어감)
        while (g.drawn >= 0) {
            passSecret(a)
            guessWrong(a)
        }
        assertEquals(0, g.pileSize)
        // 빈 더미: 오답 → 내 숨은 타일 하나를 골라 공개
        passSecret(a)
        assertFalse(a.table.showsDrawn)
        val me = g.current
        guessWrong(a)
        val hiddenBefore = g.hiddenCount(me)
        // 남의 타일은 못 고름
        val other = (me + 1) % 4
        tapTile(a, other, 0)
        assertEquals(Phase.PENALTY, g.phase)
        val own = (0 until g.rowSize(me)).first { g.canReveal(it) }
        tapTile(a, me, own)
        assertTrue(g.isRevealed(me, own))
        assertEquals(hiddenBefore - 1, g.hiddenCount(me))
        assertEquals(g.tileAt(me, own).number, a.table.shownNumber(me, own))
        // 다음 사람이 끝까지
        passSecret(a)
        sweep(a)
        checkResult(a)
        assertEquals(3, g.eliminationOrder.size)
        val ranks = g.ranks()
        for ((k, p) in g.eliminationOrder.withIndex()) assertEquals(4 - k, ranks[p])
    }

    @Test
    fun pauseHidesSecretAndBlocksInput() {
        val a = launch(four.take(3), seed = 4)
        val g = a.game
        waitFor("넘기기") { a.handoffPopup != null }
        all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
        val hold = a.secretView!!
        touch(hold, MotionEvent.ACTION_DOWN)
        assertTrue(hold.shownText.isNotEmpty())
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        assertEquals("일시정지하면 비밀 카드 가림", "", hold.shownText)
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        waitFor("계속하기") { !a.gamePaused }
        touch(hold, MotionEvent.ACTION_UP)
        touch(hold, MotionEvent.ACTION_DOWN)
        touch(hold, MotionEvent.ACTION_UP)
        a.confirmButton!!.performClick()
        waitFor("추리 시작") { !a.busy }
        // 추리 중 일시정지 → 고르기·추리 무시
        val (p, i) = firstTarget(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(a.gamePaused)
        tapTile(a, p, i)
        assertEquals(-1, a.selPlayer)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        tapTile(a, p, i)
        assertEquals(p, a.selPlayer)
        tapKey(a, g.tileAt(p, i).number)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        a.guessButton.performClick()
        assertFalse("일시정지 중 추리 무시", g.isRevealed(p, i))
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        a.guessButton.performClick()
        assertTrue(g.isRevealed(p, i))
        assertNotNull(a.table)
    }
}
