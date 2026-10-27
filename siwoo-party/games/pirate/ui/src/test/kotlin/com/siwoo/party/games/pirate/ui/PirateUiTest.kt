package com.siwoo.party.games.pirate.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.pirate.GameConfig
import com.siwoo.party.games.pirate.Phase
import com.siwoo.party.games.pirate.PirateModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 해적 룰렛 화면: 칼 꽂기·무시되는 터치·탈락·다음 판, 끝까지 진행 후 순위, 일시정지, 함정 개수 설정 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class PirateUiTest {

    private val three = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() {
        PirateActivity.randomOverride = null
        app.getSharedPreferences("pirate", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(400) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int = 3): PirateActivity {
        PirateActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, PirateModule, players)
        return Robolectric.buildActivity(PirateActivity::class.java, i).setup().get()
    }

    private fun tap(a: PirateActivity, hole: Int) {
        val pt = PointF()
        a.barrel.holeCenter(hole, pt)
        // 구멍 가운데에서 조금 비켜 눌러도 그 구멍 (넉넉한 판정)
        val x = pt.x + 6f
        val y = pt.y - 5f
        val t = SystemClock.uptimeMillis()
        a.barrel.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.barrel.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun trapHole(a: PirateActivity) = (0 until GameConfig.HOLES).first { a.game.isTrap(it) && !a.game.isUsed(it) }
    private fun safeHole(a: PirateActivity, skip: Int = 0) = (0 until GameConfig.HOLES).filter { !a.game.isTrap(it) && !a.game.isUsed(it) }[skip]

    @Test
    fun threePlayersPlayToTheEndAndReturnRanks() {
        val a = launch(three)
        waitFor("첫 차례") { !a.busy }
        assertEquals(1, a.game.round)
        assertEquals(1, a.game.roundTraps)
        val s = a.game.current
        assertEquals(a.players[s].name, a.turn.nameText)

        // 안전한 구멍 → 칼 연출 중 다른 구멍 터치는 무시
        val h = safeHole(a)
        tap(a, h)
        assertTrue(a.busy)
        assertEquals(s, a.game.holeOwner[h])
        assertTrue(a.barrel.swordAt(h))
        val other = safeHole(a)
        tap(a, other)
        assertEquals("연출 중 터치 무시", -1, a.game.holeOwner[other])
        idle(GameConfig.SWORD_INSERT_MS / 2)
        if (a.busy) { // (Robolectric 시계가 크게 건너뛰면 이미 다음 차례일 수 있음)
            tap(a, other)
            assertEquals("정적 중 터치 무시", -1, a.game.holeOwner[other])
        }
        waitFor("다음 차례") { !a.busy }
        val second = a.game.nextAlive(s)
        assertEquals(second, a.game.current)
        assertEquals(a.players[second].name, a.turn.nameText)

        // 이미 칼이 꽂힌 구멍은 무시
        tap(a, h)
        assertFalse(a.busy)
        assertEquals(second, a.game.current)

        // 두 번째 사람이 함정 → 탈락, 통 초기화 후 2판
        tap(a, trapHole(a))
        assertTrue(a.busy)
        idle(GameConfig.SWORD_INSERT_MS + GameConfig.SUSPENSE_MS + 200)
        assertFalse(a.game.alive[second])
        assertEquals(0.5f, a.strip.tiles[second].alpha, 0.01f)
        waitFor("2판") { a.game.round == 2 && !a.busy }
        assertEquals(GameConfig.HOLES, a.game.emptyHoles)
        assertFalse("통이 비워짐", a.barrel.swordAt(h))
        assertTrue(a.barrel.pirateHome)
        val third = a.game.nextAlive(second)
        assertEquals("탈락자 다음 사람부터", third, a.game.current)
        assertEquals(1, a.game.roundTraps) // 2명 남음 → 1개

        // 2판: 한 번 안전, 그다음 사람이 함정 → 끝
        tap(a, safeHole(a))
        waitFor("2판 둘째 차례") { !a.busy }
        val loser = a.game.current
        tap(a, trapHole(a))
        waitFor("끝") { a.game.phase == Phase.GAME_OVER }
        val winner = a.game.winner
        assertEquals(third, winner)
        waitFor("결과 반환") { shadowOf(a).resultCode == Activity.RESULT_OK }
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2), data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = IntArray(3)
        ranks[second] = 3
        ranks[loser] = 2
        ranks[winner] = 1
        assertArrayEquals(ranks, data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun pauseMenuBlocksTapsUntilResumed() {
        val a = launch(three, seed = 8)
        waitFor("첫 차례") { !a.busy }
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        val h = safeHole(a)
        tap(a, h)
        assertEquals("일시정지 중 터치 무시", -1, a.game.holeOwner[h])
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        idle(400)
        assertFalse(a.gamePaused)
        val cur = a.game.current
        tap(a, h)
        assertEquals(cur, a.game.holeOwner[h])
        assertTrue(a.busy)
    }

    @Test
    fun trapSettingPopupSavesAndGameUsesIt() {
        val a = launch(three)
        assertEquals("함정 개수", PirateModule.settingsLabel)
        PirateModule.showSettings(a.popups)
        idle(300)
        val two = find(a.popups, "함정 2개")
        two.performClick()
        assertEquals(2, PirateSettings.traps(app))
        a.popups.dismissAll()

        val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
        val b = launch(four, seed = 4)
        waitFor("4인 첫 차례") { !b.busy }
        assertEquals(2, b.game.trapSetting)
        assertEquals(2, b.game.roundTraps)
        assertEquals(2, (0 until GameConfig.HOLES).count { b.game.isTrap(it) })

        val c = launch(three.take(2), seed = 4)
        waitFor("2인 첫 차례") { !c.busy }
        assertEquals("2인은 항상 1개", 1, c.game.roundTraps)

        PirateModule.showSettings(a.popups)
        idle(300)
        find(a.popups, "함정 1개").performClick()
        assertEquals(1, PirateSettings.traps(app))
    }

    private fun find(root: View, desc: String): View {
        if (root.contentDescription == desc) return root
        if (root is ViewGroup) for (k in 0 until root.childCount) {
            val f = findOrNull(root.getChildAt(k), desc)
            if (f != null) return f
        }
        error("$desc 없음")
    }

    private fun findOrNull(root: View, desc: String): View? = try { find(root, desc) } catch (e: IllegalStateException) { null }
}
