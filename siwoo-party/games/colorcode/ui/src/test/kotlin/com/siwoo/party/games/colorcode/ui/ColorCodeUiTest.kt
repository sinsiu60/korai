package com.siwoo.party.games.colorcode.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.colorcode.ColorCodeModule
import com.siwoo.party.games.colorcode.GameConfig
import com.siwoo.party.games.colorcode.Phase
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

/** 색 코드 맞히기 화면: 비밀 화면이 닫힌 뒤 비밀 색이 공개 화면에 없는지, 끝까지 진행해 순위를 돌려주는지, 일시정지·중복 설정 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ColorCodeUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        ColorCodeActivity.randomOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(400) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(seed: Int, duplicates: Boolean = false): ColorCodeActivity {
        ColorCodeSettings.setDuplicates(RuntimeEnvironment.getApplication(), duplicates)
        ColorCodeActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ColorCodeModule, players)
        return Robolectric.buildActivity(ColorCodeActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    /** 넘기기 화면의 버튼 누르기 */
    private fun passHandoff(a: ColorCodeActivity) {
        waitFor("넘기기 화면") { a.handoffPopup != null }
        all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
    }

    private fun touch(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** 색 단추를 실제 터치로 누르기 */
    private fun tapColor(input: CodeInputView, color: Int) {
        val pt = PointF()
        input.buttonCenter(color, pt)
        assertTrue("입력판 배치됨", input.width > 0 && pt.x > 0f)
        touch(input, pt.x, pt.y)
    }

    private fun tapSlot(input: CodeInputView, slot: Int) {
        val pt = PointF()
        input.slotCenter(slot, pt)
        touch(input, pt.x, pt.y)
    }

    private fun codeOf(input: CodeInputView) = IntArray(GameConfig.SLOTS) { input.colorAt(it) }

    /** 공개 화면(비밀 화면 제외)의 모든 입력판 */
    private fun inputsShown(a: Activity): List<CodeInputView> = all(a.window.decorView).filterIsInstance<CodeInputView>()

    private fun waitTurn(a: ColorCodeActivity) = waitFor("차례") { !a.busy || a.game.phase == Phase.OVER }

    /** 지금 차례 사람이 입력판으로 추리 */
    private fun guess(a: ColorCodeActivity, code: IntArray) {
        waitTurn(a)
        assertEquals(0, a.input.filled)
        for (c in code) tapColor(a.input, c)
        assertArrayEquals(code, codeOf(a.input))
        // 입력 중인 색은 지금 판 다음 줄에 미리 보임
        for (i in code.indices) assertEquals(code[i], a.board.pendingAt(i))
        assertTrue(a.okButton.isEnabled)
        a.okButton.performClick()
        idle(50)
    }

    /** 비밀 코드를 입력판 터치로 정하기 (정한 뒤 화면·색이 지워지는지 확인) */
    private fun enterSecret(a: ColorCodeActivity, p: Int, code: IntArray) {
        passHandoff(a)
        val e = a.entry!!
        assertEquals(p, e.player)
        val inp = e.input
        assertEquals("새 비밀 화면은 비어 있음", 0, inp.filled)
        assertFalse(e.doneButton.isEnabled)
        for (c in code) tapColor(inp, c)
        assertArrayEquals(code, codeOf(inp))
        assertTrue(e.doneButton.isEnabled)
        e.doneButton.performClick()
        idle(50)
        assertNull(a.entry)
        assertEquals("닫힌 비밀 입력판은 색이 지워짐", 0, inp.filled)
        assertNull("비밀 화면이 화면에 남지 않음", a.window.decorView.findViewWithTag<View>(SecretEntryScreen.TAG))
        assertFalse("비밀 입력판이 화면에 남지 않음", inputsShown(a).any { it === inp })
    }

    /** 공개 화면 어디에도 비밀 색이 그려지지 않음 */
    private fun assertNoSecretShown(a: ColorCodeActivity) {
        assertNull(a.board.shownTarget(0))
        assertNull(a.board.shownTarget(1))
        assertNull(a.entry)
        assertNull(a.window.decorView.findViewWithTag<View>(SecretEntryScreen.TAG))
        // 화면의 입력판은 공개 추리 입력판 하나뿐 (비밀 입력판 없음)
        val ins = inputsShown(a)
        assertEquals(1, ins.size)
        assertTrue(ins[0] === a.input)
    }

    @Test
    fun secretsClearedThenFirstSolvesSecondMissesFirstWins() {
        val a = launch(3)
        val s0 = intArrayOf(0, 1, 2, 3)
        enterSecret(a, 0, s0)
        // 다음 사람 넘기기 화면이 덮음, 공개 판에는 비밀 없음
        waitFor("다음 넘기기") { a.handoffPopup != null }
        assertNoSecretShown(a)
        passHandoff(a)
        // 사람 1 은 [무작위] 로 정함
        val e1 = a.entry!!
        e1.randomButton.performClick()
        val s1 = codeOf(e1.input)
        assertTrue(a.game.isValid(s1))
        // 칸을 누르면 빠지고, 다시 채울 수 있음
        tapSlot(e1.input, 2)
        assertEquals(-1, e1.input.colorAt(2))
        assertFalse(e1.doneButton.isEnabled)
        tapColor(e1.input, s1[2])
        assertArrayEquals(s1, codeOf(e1.input))
        val inp1 = e1.input
        e1.doneButton.performClick()
        idle(50)
        assertEquals(0, inp1.filled)
        assertArrayEquals(s0, a.game.secretOf(0))
        assertArrayEquals(s1, a.game.secretOf(1))
        assertNoSecretShown(a)
        waitTurn(a)
        assertEquals(0, a.game.current)
        assertNoSecretShown(a)
        assertEquals(0, a.input.filled)
        for (i in 0 until GameConfig.SLOTS) assertEquals(-1, a.board.pendingAt(i))

        // 1번째: 둘 다 빗나감 (사람 0 은 s1 과 겹치지 않는 색 조합이 아니어도 됨 — 정답만 아니면)
        val miss0 = if (s1.contentEquals(intArrayOf(5, 4, 3, 2))) intArrayOf(2, 3, 4, 5) else intArrayOf(5, 4, 3, 2)
        guess(a, miss0)
        assertEquals(1, a.board.count(0))
        guess(a, intArrayOf(3, 2, 1, 0))
        assertEquals(1, a.board.count(1))
        assertEquals(0, a.board.goldAt(1, 0))
        assertEquals(4, a.board.silverAt(1, 0))
        assertNoSecretShown(a)
        // 2번째: 사람 0 이 맞힘 → 사람 1 마지막 기회 → 실패
        guess(a, s1)
        waitFor("마지막 기회") { a.game.lastChance && !a.busy }
        assertEquals(1, a.game.current)
        assertNoSecretShown(a)
        guess(a, intArrayOf(1, 0, 3, 2))
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertArrayEquals("판 0 은 사람 1 의 코드 공개", s1, a.board.shownTarget(0))
        assertArrayEquals(s0, a.board.shownTarget(1))
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(1, 2), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun bothSolveSameTryIsDraw() {
        val a = launch(8)
        val s0 = intArrayOf(5, 3, 1, 0)
        val s1 = intArrayOf(2, 4, 0, 1)
        enterSecret(a, 0, s0)
        enterSecret(a, 1, s1)
        guess(a, s1)
        waitFor("마지막 기회") { a.game.lastChance && !a.busy }
        guess(a, s0)
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(1, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun secondSolvesFirstWinsAndPauseBlocksInput() {
        val a = launch(5)
        val s0 = intArrayOf(0, 2, 4, 5)
        val s1 = intArrayOf(1, 3, 5, 0)
        enterSecret(a, 0, s0)
        enterSecret(a, 1, s1)
        guess(a, intArrayOf(0, 1, 2, 3))
        waitTurn(a)
        assertEquals(1, a.game.current)
        // 일시정지 중엔 입력·확인 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        tapColor(a.input, 0)
        assertEquals(0, a.input.filled)
        a.input.setCode(s0)
        a.okButton.performClick()
        assertEquals(0, a.board.count(1))
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        waitFor("재개") { !a.gamePaused && !PauseMenu.isShowing(a.popups) }
        a.input.clear()
        guess(a, s0) // 후공이 1번째에 맞힘 → 바로 후공 승
        assertEquals(1, a.board.count(1))
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertArrayEquals(intArrayOf(2, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun noDuplicatesRejectsSameColorAndPauseHidesSecret() {
        val a = launch(6, duplicates = false)
        assertFalse(a.game.allowDuplicates)
        passHandoff(a)
        val e = a.entry!!
        tapColor(e.input, 2)
        tapColor(e.input, 2)
        assertEquals("같은 색은 두 번 안 들어감", 1, e.input.filled)
        assertFalse(e.input.canPut(2))
        // 일시정지하면 비밀 입력판을 가림
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertEquals(View.INVISIBLE, e.input.visibility)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("재개") { !a.gamePaused }
        assertEquals(View.VISIBLE, e.input.visibility)
        // [지우기] = 맨 뒤 칸 빼기
        e.clearButton.performClick()
        assertEquals(0, e.input.filled)
        for (c in intArrayOf(0, 1, 2, 3)) tapColor(e.input, c)
        e.doneButton.performClick()
        idle(50)
        passHandoff(a)
        for (c in intArrayOf(5, 4, 3, 2)) tapColor(a.entry!!.input, c)
        a.entry!!.doneButton.performClick()
        waitTurn(a)
        // 추리 입력판도 같은 색 금지
        tapColor(a.input, 1)
        tapColor(a.input, 1)
        assertEquals(1, a.input.filled)
        a.clearButton.performClick()
        assertEquals(0, a.input.filled)
    }

    @Test
    fun duplicatesOnAllowsRepeatsAndTenMissesEachIsDraw() {
        val a = launch(7, duplicates = true)
        assertTrue(a.game.allowDuplicates)
        val s0 = intArrayOf(0, 0, 1, 1)
        val s1 = intArrayOf(2, 2, 2, 3)
        enterSecret(a, 0, s0)
        enterSecret(a, 1, s1)
        assertArrayEquals(s0, a.game.secretOf(0))
        // 둘 다 10번 모두 실패 → 무승부
        repeat(GameConfig.MAX_TRIES) { k ->
            assertEquals(k + 1, a.game.round)
            guess(a, intArrayOf(4, 4, 5, 5))
            guess(a, intArrayOf(5, 5, 4, 4))
            if (k < GameConfig.MAX_TRIES - 1) assertNoSecretShown(a)
        }
        assertEquals(GameConfig.MAX_TRIES, a.board.count(0))
        assertEquals(GameConfig.MAX_TRIES, a.board.count(1))
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertNotNull(a.board.shownTarget(0))
        assertArrayEquals(intArrayOf(1, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun settingsPopupTogglesDuplicates() {
        val a = launch(1)
        val app = RuntimeEnvironment.getApplication()
        idle(200)
        a.pauseGame()
        a.popups.dismissAll()
        idle(200)
        val pop = ColorCodeSettings.show(a.popups)
        idle(300)
        val on = all(pop.content).filterIsInstance<GameButton>().first { it.contentDescription == "중복 켬" }
        on.performClick()
        assertTrue(ColorCodeSettings.duplicates(app))
        val off = all(pop.content).filterIsInstance<GameButton>().first { it.contentDescription == "중복 끔" }
        off.performClick()
        assertFalse(ColorCodeSettings.duplicates(app))
        assertEquals("중복 색 설정", ColorCodeModule.settingsLabel)
    }
}
