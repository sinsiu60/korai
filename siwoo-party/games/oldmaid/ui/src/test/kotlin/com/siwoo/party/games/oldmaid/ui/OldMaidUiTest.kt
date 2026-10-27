package com.siwoo.party.games.oldmaid.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.oldmaid.OldMaidModule
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

/** 도둑잡기 화면: 2인 한 판을 화면으로 끝까지 → 순위(조커 든 사람 꼴찌), 넘기기 뒤 이전 사람 손패 안 보임, 내 카드 꾹 눌러 보기, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class OldMaidUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1))

    @Before fun frames() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { OldMaidActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(4000) { if (cond()) return; idle(16) }
        error("기다리다 시간 초과: $what")
    }

    private fun launch(seed: Int) =
        Robolectric.buildActivity(OldMaidActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), OldMaidModule, players)).also { OldMaidActivity.randomOverride = Random(seed) }.setup()

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    private fun pressHandoff(a: OldMaidActivity) {
        all(a.handoffPopup!!.content).filterIsInstance<GameButton>().single().performClick()
    }

    /** 상대 손패 k 번째 카드를 손가락으로 누름 (겹친 카드는 왼쪽 보이는 부분) */
    private fun tapTargetCard(a: OldMaidActivity, k: Int) {
        val hand = a.game.hands[a.target]
        val s = a.spriteOf(hand[k])!!
        val x = if (k == hand.size - 1) s.x else s.x - a.table.cardWidth * s.scale * 0.35f
        val t = SystemClock.uptimeMillis()
        a.table.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, s.y, 0))
        a.table.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, s.y, 0))
    }

    private fun touch(v: View, action: Int) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, v.width / 2f, v.height / 2f, 0))
    }

    /** 손패 카드 중 앞면이 보이는 것 (버린 더미 제외) */
    private fun faceUpHandCards(a: OldMaidActivity): Int =
        a.table.sprites.count { it.tag == OldMaidActivity.TAG_HAND && it.visible && it.faceUp }

    @Test
    fun twoPlayerGamePlaysToTheEndAndJokerHolderIsLast() {
        val a = launch(3).get()
        val g = a.game
        assertEquals("53장을 모두 나눔", 53, g.dealt.sumOf { it.size })
        val pickRandom = Random(77)
        var turns = 0
        var checkedHand = false
        while (true) {
            waitFor("넘기기 화면 또는 끝") { a.handoffPopup != null || a.phase == OldMaidActivity.Stage.END }
            if (a.phase == OldMaidActivity.Stage.END) break
            // 넘기기 화면: 이전 사람의 비밀 화면은 닫혔고, 손패 앞면은 하나도 보이지 않는다
            assertNull("비밀 화면이 남아 있으면 안 됨", a.secretHandle)
            assertEquals("넘기기 뒤 손패 앞면이 보이면 안 됨", 0, faceUpHandCards(a))
            assertEquals(OldMaidActivity.Stage.HANDOFF, a.phase)
            assertEquals(g.current, a.picker)
            assertEquals(g.target, a.target)
            pressHandoff(a)
            waitFor("고르기") { a.acceptingPick }
            if (!checkedHand && g.hands[a.picker].size >= 2) {
                // 내 카드 보기: 꾹 누를 때만 보이고 [다 봤어요] 뒤 지워짐
                a.handButton.performClick()
                idle(100)
                val hv = a.secretView
                assertNotNull(hv)
                assertFalse("가져오기는 잠시 멈춤", a.acceptingPick)
                assertEquals("", hv!!.shownText)
                touch(hv, MotionEvent.ACTION_DOWN)
                assertEquals(g.sortedHand(a.picker).joinToString(" ") { it.shortName }, hv.shownText)
                touch(hv, MotionEvent.ACTION_UP)
                assertEquals("", hv.shownText)
                a.secretHandle!!.confirmButton!!.performClick()
                idle(100)
                assertNull(a.secretHandle)
                assertFalse("확인이 끝나면 비밀이 지워짐", hv.hasSecret)
                assertTrue(a.acceptingPick)
                checkedHand = true
            }
            val picker = a.picker
            val target = a.target
            val before = g.count(picker) + g.count(target)
            val k = pickRandom.nextInt(g.count(target))
            val card = g.hands[target][k]
            tapTargetCard(a, k)
            assertFalse("고른 카드는 상대 손패에서 빠짐", card in g.hands[target])
            assertEquals(OldMaidActivity.Stage.RESOLVE, a.phase)
            assertEquals("한 번에 한 장만", 1, g.turnCount - turns)
            if (g.count(target) > 0 && !g.gameOver) {
                tapTargetCard(a, 0)
                assertEquals("연출 중 두 번째 터치는 무시", 1, g.turnCount - turns)
            }
            turns++
            val after = g.count(picker) + g.count(target)
            assertTrue(after == before || after == before - 2)
            assertEquals(53, g.hands.sumOf { it.size } + g.pile.size)
        }
        assertTrue(turns > 0)
        assertTrue(checkedHand)
        waitFor("도둑 도장") { a.stamp.stamped }
        idle(200)
        assertEquals(1, faceUpHandCards(a)) // 공개된 조커 한 장
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        val thief = g.thief
        assertEquals("조커를 든 사람이 꼴찌", 2, ranks[thief])
        assertEquals(1, ranks[1 - thief])
        assertTrue(g.hands[thief].single().isJoker)
        assertEquals(52, g.pile.size)
    }

    @Test
    fun fourPlayerGameSkipsFinishedPlayersToTheEnd() {
        val four = players + listOf(PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))
        val a = Robolectric.buildActivity(OldMaidActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), OldMaidModule, four)).also { OldMaidActivity.randomOverride = Random(9) }.setup().get()
        val g = a.game
        val pickRandom = Random(4)
        while (true) {
            waitFor("넘기기 화면 또는 끝") { a.handoffPopup != null || a.phase == OldMaidActivity.Stage.END }
            if (a.phase == OldMaidActivity.Stage.END) break
            assertFalse("끝난 사람은 차례가 오지 않음", g.isFinished(a.picker))
            assertFalse("끝난 사람에게서 가져오지 않음", g.isFinished(a.target))
            assertEquals("가져올 사람 = 다음으로 아직 하는 사람", g.nextPlaying(a.picker), a.target)
            pressHandoff(a)
            waitFor("고르기") { a.acceptingPick }
            // 지금 둘 말고 다른 사람 손패는 화면에 없음
            for (p in four.indices) if (p != a.picker && p != a.target) {
                assertTrue(g.hands[p].none { a.spriteOf(it)!!.visible })
            }
            tapTargetCard(a, pickRandom.nextInt(g.count(a.target)))
            assertEquals(53, g.hands.sumOf { it.size } + g.pile.size)
        }
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertEquals(4, ranks[g.thief])
        assertEquals("1~4등이 한 번씩 (처음 동시 탈출이 없으면)", if (ranks.distinct().size == 4) listOf(1, 2, 3, 4) else ranks.sorted(), ranks.sorted())
        assertArrayEquals(g.finalRanks(), ranks)
    }

    @Test
    fun pauseMenuStopsPickingAndResumes() {
        val c = launch(5)
        val a = c.get()
        waitFor("넘기기") { a.handoffPopup != null }
        pressHandoff(a)
        waitFor("고르기") { a.acceptingPick }
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        assertFalse(a.acceptingPick)
        val n = a.game.turnCount
        tapTargetCard(a, a.game.count(a.target) - 1)
        assertEquals("일시정지 중 터치 무시", n, a.game.turnCount)
        idle(5000)
        assertEquals(OldMaidActivity.Stage.PICK, a.phase)
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitFor("계속하기 뒤 다시 고르기") { a.acceptingPick }
        // 백그라운드 → 돌아오면 일시정지 메뉴
        c.pause().stop()
        idle(1000)
        c.start().resume()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("다시 고르기") { a.acceptingPick }
        tapTargetCard(a, a.game.count(a.target) - 1)
        assertEquals(n + 1, a.game.turnCount)
        assertNotEquals(OldMaidActivity.Stage.PICK, a.phase)
    }
}
