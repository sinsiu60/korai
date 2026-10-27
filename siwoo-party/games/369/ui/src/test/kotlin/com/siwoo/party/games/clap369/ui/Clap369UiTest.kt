package com.siwoo.party.games.clap369.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.clap369.Clap369
import com.siwoo.party.games.clap369.Clap369Module
import com.siwoo.party.games.clap369.Fail
import com.siwoo.party.games.clap369.Key
import com.siwoo.party.games.clap369.Phase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

/**
 * 369 박수 화면: 끝까지 진행 → 탈락 순서 순위, 짝 개수 판정(너무 많이/모자람/틀린 버튼),
 * 탈락한 사람 건너뛰기, 일시정지 중 시간 멈춤 + 돌아오면 그 차례 다시. 버튼은 실제 터치(DOWN)로 누른다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class Clap369UiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>): Clap369Activity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), Clap369Module, players)
        return Robolectric.buildActivity(Clap369Activity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(20) }
        error("$what 가 되지 않음")
    }

    /** 새 차례가 화면에 나올 때까지 */
    private fun waitInput(a: Clap369Activity) = waitFor("차례") {
        a.game.phase == Phase.INPUT && a.pad.active && a.shownTurnNumber == a.game.number && a.shownTurnPlayer == a.game.current
    }

    private val pt = PointF()

    private fun press(a: Clap369Activity, key: Key) {
        a.pad.buttonCenter(key, pt)
        val t = SystemClock.uptimeMillis()
        a.pad.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.pad.dispatchTouchEvent(MotionEvent.obtain(t, t + 5, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    /** 지금 숫자를 맞게 입력하고 다음 차례까지 기다림 */
    private fun sayRight(a: Clap369Activity) {
        waitInput(a)
        val n = a.game.number
        val need = Clap369.claps(n)
        if (need == 0) press(a, Key.NUMBER) else repeat(need) { press(a, Key.CLAP); idle(30) }
        assertSame("$n 완료", Phase.SETTLE, a.game.phase)
        waitFor("다음 숫자") { a.game.number == n + 1 && a.game.phase == Phase.INPUT }
    }

    @Test
    fun threePlayersPlayUntilOneLeftAndRanksFollowEliminationOrder() {
        val a = launch(four.take(3))
        assertFalse("시작 전엔 버튼 꺼짐", a.pad.active)
        press(a, Key.NUMBER)
        assertSame(Phase.IDLE, a.game.phase)
        // 1 ~ 12 정확히 (3 6 9 는 짝)
        repeat(12) { sayRight(a) }
        assertEquals(13, a.game.number)
        assertEquals(0, a.game.current) // 13번째 = 가람
        assertEquals(Clap369.limitMs(13), a.game.deadline - a.game.turnStart)
        // 가람: 13 에서 [숫자] → 탈락
        waitInput(a)
        press(a, Key.NUMBER)
        assertFalse(a.game.alive[0])
        assertFalse(a.pad.active)
        // 나래가 13 부터 이어감
        waitInput(a)
        assertEquals(13, a.game.number)
        assertEquals(1, a.game.current)
        sayRight(a) // 나래 13 (짝)
        assertEquals("가람은 건너뜀", 2, a.game.current)
        sayRight(a) // 다온 14
        assertEquals(1, a.game.current)
        // 나래: 15 에서 아무것도 안 함 → 시간 초과 탈락 → 다온 우승
        waitInput(a)
        waitFor("시간 초과") { !a.game.alive[1] }
        assertTrue(a.game.gameOver)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals("먼저 탈락 3위, 나중 탈락 2위, 남은 사람 1위", intArrayOf(3, 2, 1), data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun clapCountMustBeExactAndTurnsSkipEliminatedPlayers() {
        val a = launch(four)
        repeat(2) { sayRight(a) } // 1, 2
        // 다온: 3 에서 짝 2번 → 너무 많이 → 탈락
        waitInput(a)
        assertEquals(2, a.game.current)
        press(a, Key.CLAP)
        assertSame(Phase.SETTLE, a.game.phase)
        press(a, Key.CLAP)
        assertFalse(a.game.alive[2])
        assertEquals(listOf(2), a.game.outOrder)
        // 라온이 3 부터
        waitInput(a)
        assertEquals(3, a.game.current)
        assertEquals(3, a.game.number)
        sayRight(a) // 라온 3
        sayRight(a) // 가람 4
        sayRight(a) // 나래 5
        assertEquals("다온 건너뜀", 3, a.game.current)
        assertEquals(6, a.game.number)
        // 라온: 6 에서 [짝] 대신 [숫자] → 탈락
        waitInput(a)
        press(a, Key.NUMBER)
        assertFalse(a.game.alive[3])
        waitInput(a)
        assertEquals(0, a.game.current)
        assertEquals(6, a.game.number)
        // 가람 6, 나래 7, 가람 8, 나래: 9 에서 [짝] 대신 [숫자] → 탈락 → 끝
        sayRight(a) // 가람 6
        sayRight(a) // 나래 7
        sayRight(a) // 가람 8
        waitInput(a)
        assertEquals(1, a.game.current)
        press(a, Key.NUMBER)
        assertTrue(a.game.gameOver)
        waitFor("결과") { a.isFinishing }
        assertArrayEquals(intArrayOf(1, 2, 4, 3), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun tooFewClapsWithinTimeEliminates() {
        val a = launch(four.take(2))
        repeat(32) { sayRight(a) } // 1 ~ 32
        waitInput(a)
        assertEquals(33, a.game.number)
        press(a, Key.CLAP) // 1번만
        assertSame(Phase.INPUT, a.game.phase)
        assertEquals(1, a.game.clapCount)
        waitFor("마감") { a.game.gameOver }
        assertEquals(1, a.game.outOrder.size)
        val loser = a.game.outOrder[0]
        assertEquals(0, loser) // 33 번째 = 가람 (홀수 차례)
        waitFor("결과") { a.isFinishing }
        assertArrayEquals(intArrayOf(2, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun pauseFreezesTheTurnAndResumeRestartsIt() {
        val a = launch(four.take(2))
        sayRight(a)
        waitInput(a)
        assertEquals(2, a.game.number)
        idle(600) // 차례 시계가 조금 흐른 뒤 일시정지
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        press(a, Key.CLAP) // 일시정지 중 입력 무시 (틀린 버튼이지만 탈락 아님)
        idle(10_000)
        assertTrue("일시정지 중엔 시간 초과 없음", a.game.alive[1])
        assertSame(Phase.INPUT, a.game.phase)
        val before = a.game.turnStart
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("돌아오면 그 차례를 처음부터 (게임 시계 기준)", a.game.turnStart >= before + 500)
        assertEquals(a.game.turnStart + Clap369.limitMs(2), a.game.deadline)
        assertTrue(a.bar.running)
        // 이제 시간이 흐르면 탈락
        waitFor("시간 초과") { !a.game.alive[1] }
        assertTrue(a.game.gameOver)
    }

    @Test
    fun moduleInfo() {
        assertEquals("clap369", Clap369Module.id)
        assertEquals(3, Clap369Module.rules.size)
        assertEquals(listOf(com.siwoo.party.common.ui.PartyTag.TALK), Clap369Module.partyTags)
        assertSame(com.siwoo.party.common.ui.GameCategory.PARTY, Clap369Module.category)
        assertEquals(0, Clap369Module.requiredTouches(4))
        assertTrue(Clap369Module.supports(2) && Clap369Module.supports(4))
        assertSame(Fail.TOO_MANY, Fail.valueOf("TOO_MANY"))
    }
}
