package com.siwoo.party.games.bomb.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.bomb.BombModule
import com.siwoo.party.games.bomb.BombTopics
import com.siwoo.party.games.bomb.GameConfig
import com.siwoo.party.games.bomb.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 폭탄 돌리기 화면: 주제 카드 → 넘기기(0.8초 연타 방지) → 폭발 → 5라운드 후 순위, 일시정지 때 심지 멈춤, 주제 편집 저장 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BombUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))

    @After fun tearDown() { BombActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(ps: List<PlayerInfo> = players, seed: Int = 5): BombActivity {
        BombActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), BombModule, ps)
        return Robolectric.buildActivity(BombActivity::class.java, i).setup().get()
    }

    /** 주제 카드가 뒤집혀 [불 붙이기!]가 켜질 때까지 → 누르고 → 불붙을 때까지 */
    private fun lightBomb(a: BombActivity) {
        repeat(200) { if (a.startButton?.isEnabled == true) return@repeat; idle(50) }
        val go = a.startButton
        assertNotNull("주제 카드의 [불 붙이기!]", go)
        assertTrue(a.topicCard!!.front)
        assertEquals(Phase.READY, a.game.phase)
        go!!.performClick()
        repeat(200) { if (a.game.phase == Phase.LIVE) return; idle(50) }
        error("불이 붙지 않음 (${a.game.phase})")
    }

    /** 터질 때까지 시간을 흘린다 */
    private fun waitExplode(a: BombActivity) {
        repeat(800) { if (a.game.phase != Phase.LIVE) return; idle(100) }
        error("폭발하지 않음")
    }

    @Test
    fun passWithButtonUntilExplosionsThroughFiveRoundsReturnsRanks() {
        val a = launch()
        val expected = IntArray(players.size)
        for (round in 1..GameConfig.BOMB_ROUNDS) {
            lightBomb(a)
            assertEquals(round, a.game.roundNumber)
            assertTrue(a.passButton.isEnabled)
            assertTrue("불꽃·흔들림 루프", a.bomb.animating)
            // 라운드마다 round 번 넘긴다
            repeat(round) {
                val before = a.game.holder
                a.passButton.performClick()
                assertEquals("다음 순서로", (before + 1) % players.size, a.game.holder)
                assertEquals(players[a.game.holder].name, a.holderView.name)
                assertFalse("넘긴 직후 0.8초 비활성", a.passButton.isEnabled)
                a.passButton.performClick() // 연타 → 무시
                assertEquals((before + 1) % players.size, a.game.holder)
                idle(GameConfig.BOMB_PASS_COOLDOWN_MS + 100)
                if (a.game.phase == Phase.LIVE) assertTrue("0.8초 뒤 다시 켜짐", a.passButton.isEnabled)
            }
            val holder = a.game.holder
            waitExplode(a)
            assertEquals(Phase.EXPLODED, a.game.phase)
            expected[holder]++
            assertArrayEquals("해골은 터질 때 든 사람에게", expected, a.game.skulls)
            assertTrue("이름에 펑! 도장", a.holderView.stamped)
            assertEquals(BombView.State.EXPLODED, a.bomb.state)
            assertFalse(a.passButton.isEnabled)
            if (round < GameConfig.BOMB_ROUNDS) {
                // 다음 라운드는 터진 사람의 다음 사람부터, 새 주제
                repeat(200) { if (a.game.phase == Phase.READY) return@repeat; idle(100) }
                assertEquals(Phase.READY, a.game.phase)
                assertEquals((holder + 1) % players.size, a.game.holder)
                assertFalse(a.holderView.stamped)
            }
        }
        assertEquals(GameConfig.BOMB_ROUNDS, a.game.topicsUsed.toSet().size)
        repeat(200) { if (shadowOf(a).resultIntent != null) return@repeat; idle(100) }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2), data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(Ranking.competition(expected.map { it.toLong() }, higherIsBetter = false), ranks)
        // 해골이 적을수록 순위가 높다(숫자가 작다)
        for (i in players.indices) for (j in players.indices) if (expected[i] < expected[j]) assertTrue(ranks[i] < ranks[j])
    }

    @Test
    fun pauseMenuFreezesFuseAndTicking() {
        val a = launch(seed = 9)
        lightBomb(a)
        val holder = a.game.holder
        @Suppress("DEPRECATION") a.onBackPressed()
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("흔들림 루프 멈춤", a.bomb.animating)
        val frozen = a.game.remainingMs(a.timers.now())
        idle(70_000) // 최대 폭발 시간보다 길게
        assertEquals("일시정지 중에는 안 터짐", Phase.LIVE, a.game.phase)
        assertEquals("심지 시간도 멈춤", frozen, a.game.remainingMs(a.timers.now()))
        @Suppress("DEPRECATION") a.onBackPressed() // 계속하기
        idle(500)
        assertFalse(PauseMenu.isShowing(a.popups))
        assertTrue(a.bomb.animating)
        waitExplode(a)
        assertEquals(1, a.game.skulls[holder])
    }

    @Test
    fun backgroundFreezesFuse() {
        BombActivity.randomOverride = Random(3)
        val c = Robolectric.buildActivity(BombActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), BombModule, players.take(2))).setup()
        val a = c.get()
        lightBomb(a)
        c.pause().stop()
        idle(70_000)
        assertEquals(Phase.LIVE, a.game.phase)
        c.start().resume()
        idle(100)
        assertTrue("돌아오면 일시정지 메뉴", PauseMenu.isShowing(a.popups))
        @Suppress("DEPRECATION") a.onBackPressed()
        waitExplode(a)
        assertEquals(1, a.game.skulls.sum())
    }

    @Test
    fun fourPlayersStartAndStripShowsSkulls() {
        val four = players + PlayerInfo(3, "라온", 4)
        val a = launch(four, seed = 2)
        lightBomb(a)
        val first = a.game.holder
        a.passButton.performClick()
        assertEquals((first + 1) % 4, a.game.holder)
        waitExplode(a)
        assertEquals(1, a.game.skulls.sum())
    }

    /** 모든 하위 뷰 */
    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    @Test
    fun topicEditorSavesToPrefsAndGameUsesThem() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(BombTopics.DEFAULT, BombTopicStore.load(ctx))
        assertEquals("주제 편집", BombModule.settingsLabel)
        val a = launch()
        idle(1500)
        BombModule.showSettings(a.popups)
        idle(500)
        val editor = a.popups.top()!!.content
        val edits = all(editor).filterIsInstance<EditText>()
        assertEquals(30, edits.size)
        // 주제 5개만 남기고 하나 바꾸기: 첫 칸 수정 후 나머지 삭제
        edits[0].setText("  탈것 말고 비행기 종류 ")
        val buttons = all(editor).filterIsInstance<GameButton>()
        val deletes = buttons.filter { it.text == "삭제" }
        for (d in deletes.drop(5)) d.performClick()
        buttons.first { it.text == "저장" }.performClick()
        idle(500)
        val saved = BombTopicStore.load(ctx)
        assertEquals(5, saved.size)
        assertEquals("탈것 말고 비행기 종류", saved[0])
        // 새 게임은 저장한 주제로, 5라운드 동안 겹치지 않음
        a.finish()
        val b = launch(seed = 4)
        lightBomb(b)
        assertTrue(b.game.topic in saved)
        BombTopicStore.save(ctx, emptyList())
        assertEquals("비우면 기본 주제", BombTopics.DEFAULT, BombTopicStore.load(ctx))
    }
}
