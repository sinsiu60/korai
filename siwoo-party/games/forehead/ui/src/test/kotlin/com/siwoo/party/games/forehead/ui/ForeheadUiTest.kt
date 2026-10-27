package com.siwoo.party.games.forehead.ui

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.forehead.Answer
import com.siwoo.party.games.forehead.ForeheadModule
import com.siwoo.party.games.forehead.ForeheadWords
import com.siwoo.party.games.forehead.GameConfig
import com.siwoo.party.games.forehead.Phase
import com.siwoo.party.games.forehead.TiltMath
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 이마 퀴즈 화면: 3인 한 바퀴를 터치 조작으로 끝까지 / 센서(가속도 이벤트)로 끝까지 → 순위,
 * 센서는 준비·카운트·플레이 중에만 등록 (차례 사이·결과·일시정지·종료 때 해제), 일시정지 중 시간 멈춤, 설정 저장.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ForeheadUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val three = four.take(3)
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        ForeheadActivity.randomOverride = null
        app.getSharedPreferences(ForeheadStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, stepMs: Long = 50, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(stepMs) }
        error("$what 가 되지 않음")
    }

    private fun launch(ps: List<PlayerInfo>, seed: Int = 5): ForeheadActivity {
        ForeheadActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, ForeheadModule, ps)
        return Robolectric.buildActivity(ForeheadActivity::class.java, i).setup().get().also { it.beeper.enabled = false }
    }

    private fun tap(a: ForeheadActivity, left: Boolean) {
        val v = a.playView
        val t = SystemClock.uptimeMillis()
        val x = if (left) v.width * 0.25f else v.width * 0.75f
        val e = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, v.height / 2f, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
        val u = MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, v.height / 2f, 0)
        v.dispatchTouchEvent(u)
        u.recycle()
    }

    private fun setup(a: ForeheadActivity, category: Int, laps: Int) {
        waitFor("카테고리 선택") { a.startButton != null }
        assertEquals(ForeheadActivity.Step.SETUP, a.step)
        assertEquals(GameConfig.CATEGORY_COUNT + 1, a.categoryButtons.size)
        a.categoryButtons[if (category < 0) GameConfig.CATEGORY_COUNT else category].performClick()
        a.lapButtons[laps - 1].performClick()
        a.startButton!!.performClick()
        assertEquals(Phase.READY, a.game.phase)
        assertEquals(laps, a.game.laps)
        assertEquals(category, a.game.category)
    }

    /** 시간 끝까지 흘리기 */
    private fun runOutClock(a: ForeheadActivity) {
        waitFor("시간 끝", stepMs = 500) { a.step == ForeheadActivity.Step.TIME_UP || a.step == ForeheadActivity.Step.REVIEW }
    }

    private fun assertResult(a: ForeheadActivity, expected: IntArray) {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(expected, ranks)
    }

    @Test
    fun touchModeFullGameThreePlayers() {
        val a = launch(three)
        assertFalse("센서 없는 기기는 터치 조작", a.hasSensor)
        assertTrue(a.touchMode)
        setup(a, 0, 1)
        val correctPer = intArrayOf(2, 4, 1)
        for (turn in 0 until 3) {
            waitFor("안내 화면") { a.readyButton != null }
            assertEquals(ForeheadActivity.Step.INTRO, a.step)
            assertEquals(turn, a.game.current)
            a.readyButton!!.performClick()
            assertEquals(ForeheadActivity.Step.COUNT, a.step)
            waitFor("플레이") { a.step == ForeheadActivity.Step.PLAY }
            assertEquals(PlayView.Mode.WORD, a.playView.mode)
            assertTrue(a.timerBar.running)
            val shown = ArrayList<String>()
            repeat(correctPer[turn]) {
                shown.add(a.playView.word)
                tap(a, left = true)
                assertEquals(PlayView.Mode.FLASH, a.playView.mode)
                assertTrue(a.playView.flashCorrect)
                // 정답 화면 도중엔 터치 무시
                tap(a, left = true)
                waitFor("다음 단어") { a.playView.mode == PlayView.Mode.WORD }
            }
            shown.add(a.playView.word)
            tap(a, left = false)
            assertEquals(PlayView.Mode.FLASH, a.playView.mode)
            assertFalse("오른쪽 = 패스", a.playView.flashCorrect)
            waitFor("다음 단어") { a.playView.mode == PlayView.Mode.WORD }
            assertTrue(shown.all { it in ForeheadWords.DEFAULT[0] })
            assertEquals(correctPer[turn], a.game.correctCount)
            assertEquals(1, a.game.passCount)
            runOutClock(a)
            waitFor("단어 공개") { a.nextButton != null }
            assertEquals(ForeheadActivity.Step.REVIEW, a.step)
            val res = a.game.turnResults
            assertEquals(shown, res.take(shown.size).map { it.word })
            assertEquals(Answer.TIMEOUT, res.last().answer)
            assertEquals(res.size, a.reviewView!!.chipCount)
            assertEquals(correctPer[turn], a.game.scores[turn])
            a.nextButton!!.performClick()
        }
        assertEquals(Phase.DONE, a.game.phase)
        assertEquals("한 게임 안 단어 중복 없음", a.game.shownWords.size, a.game.shownWords.toSet().size)
        assertResult(a, intArrayOf(2, 1, 3))
    }

    @Test
    fun pauseStopsClockAndIgnoresTaps() {
        val a = launch(three)
        setup(a, GameConfig.MIXED, 1)
        waitFor("안내") { a.readyButton != null }
        a.readyButton!!.performClick()
        waitFor("플레이") { a.step == ForeheadActivity.Step.PLAY }
        idle(5000)
        val before = a.timerBar.remainingMs
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        val word = a.playView.word
        tap(a, true)
        assertEquals("일시정지 중 터치 무시", 0, a.game.correctCount)
        idle(70_000)
        assertEquals("일시정지 중 시간 그대로", ForeheadActivity.Step.PLAY, a.step)
        assertEquals(word, a.playView.word)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        idle(1000)
        assertTrue(a.timerBar.remainingMs in 1 until before)
        assertTrue("다시 이어서 남은 시간 > 50초", a.timerBar.remainingMs > 50_000)
        tap(a, true)
        assertEquals(1, a.game.correctCount)
    }

    // ── 센서 ──

    private lateinit var shadowSm: ShadowSensorManager

    private fun addAccelerometer() {
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowSm = shadowOf(sm)
        shadowSm.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
    }

    /** 지금 화면 회전에서 pitch(도)만큼 기울인 가속도 이벤트를 n 번 (사이에 stepMs) */
    private fun tilt(a: ForeheadActivity, pitch: Float, n: Int = 8, stepMs: Long = 30) {
        val r = Math.toRadians(pitch.toDouble())
        val up = (cos(r) * TiltMath.GRAVITY).toFloat()
        val z = (sin(r) * TiltMath.GRAVITY).toFloat()
        repeat(n) {
            val e = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER)
            when (a.displayRotation) {
                0 -> { e.values[0] = 0f; e.values[1] = up }
                1 -> { e.values[0] = up; e.values[1] = 0f }
                2 -> { e.values[0] = 0f; e.values[1] = -up }
                else -> { e.values[0] = -up; e.values[1] = 0f }
            }
            e.values[2] = z
            shadowSm.sendSensorEventToListeners(e)
            if (stepMs > 0) idle(stepMs)
        }
    }

    @Test
    fun sensorFullGameRegisteredOnlyWhilePlaying() {
        addAccelerometer()
        val ctl = Robolectric.buildActivity(ForeheadActivity::class.java, Party.gameIntent(app, ForeheadModule, three).also {
            ForeheadActivity.randomOverride = Random(9)
        }).setup()
        val a = ctl.get()
        a.beeper.enabled = false
        assertTrue(a.hasSensor)
        assertFalse(a.touchMode)
        setup(a, 3, 1)
        val correctPer = intArrayOf(1, 3, 3)
        for (turn in 0 until 3) {
            waitFor("안내 화면") { a.readyButton != null }
            assertFalse("안내 화면(차례 사이)에는 해제", a.sensorRegistered)
            assertFalse(shadowSm.hasListener(a.sensorListener))
            a.readyButton!!.performClick()
            assertEquals(ForeheadActivity.Step.READY, a.step)
            assertTrue("이마에 대기 → 등록", shadowSm.hasListener(a.sensorListener))
            // 기울어진 채로는 시작 안 함
            tilt(a, -70f, n = 30)
            assertEquals(ForeheadActivity.Step.READY, a.step)
            // 똑바로 세워 0.6초 이상 → 3초 카운트
            tilt(a, 3f, n = 40, stepMs = 30)
            assertEquals(ForeheadActivity.Step.COUNT, a.step)
            assertTrue(a.sensorRegistered)
            waitFor("플레이") { a.step == ForeheadActivity.Step.PLAY }
            repeat(correctPer[turn]) {
                tilt(a, -75f)
                assertEquals("아래로 = 정답", PlayView.Mode.FLASH, a.playView.mode)
                assertTrue(a.playView.flashCorrect)
                // 그대로 기울인 채로는 한 번만
                waitFor("다음 단어") { a.playView.mode == PlayView.Mode.WORD }
                tilt(a, -80f)
                assertEquals(PlayView.Mode.WORD, a.playView.mode)
                tilt(a, 0f)
            }
            assertEquals(correctPer[turn], a.game.correctCount)
            tilt(a, 75f)
            assertEquals(PlayView.Mode.FLASH, a.playView.mode)
            assertFalse("위로 = 패스", a.playView.flashCorrect)
            assertEquals(1, a.game.passCount)
            tilt(a, 0f)
            if (turn == 0) {
                // 일시정지 → 해제, 다시 → 등록
                @Suppress("DEPRECATION") a.onBackPressed()
                idle(100)
                assertTrue(PauseMenu.isShowing(a.popups))
                assertFalse("일시정지 중 해제", shadowSm.hasListener(a.sensorListener))
                assertFalse(a.sensorRegistered)
                @Suppress("DEPRECATION") a.onBackPressed()
                waitFor("다시 등록 (paused=${a.gamePaused}, step=${a.step})") { shadowSm.hasListener(a.sensorListener) }
                // 백그라운드 (onPause) 도 해제
                ctl.pause()
                assertFalse("백그라운드 해제", shadowSm.hasListener(a.sensorListener))
                ctl.resume()
                idle(100)
                assertTrue(PauseMenu.isShowing(a.popups))
                assertFalse("메뉴가 떠 있는 동안 해제", shadowSm.hasListener(a.sensorListener))
                @Suppress("DEPRECATION") a.onBackPressed()
                waitFor("메뉴 닫힘 → 다시 등록 (paused=${a.gamePaused}, step=${a.step})") { shadowSm.hasListener(a.sensorListener) }
            }
            runOutClock(a)
            assertFalse("시간 끝 → 해제", shadowSm.hasListener(a.sensorListener))
            waitFor("단어 공개") { a.nextButton != null }
            assertFalse("결과 화면 해제", a.sensorRegistered)
            assertEquals(correctPer[turn], a.game.scores[turn])
            a.nextButton!!.performClick()
        }
        waitFor("끝") { a.isFinishing }
        assertFalse(shadowSm.hasListener(a.sensorListener))
        assertResult(a, intArrayOf(3, 1, 1))
        ctl.pause().stop().destroy()
        assertFalse("종료 시 해제", a.sensorRegistered)
    }

    @Test
    fun touchSettingDisablesSensorAndDestroyReleases() {
        addAccelerometer()
        ForeheadStore.setTouch(app, true)
        val a = launch(three)
        assertTrue(a.hasSensor)
        assertTrue("설정이 켜져 있으면 터치", a.touchMode)
        setup(a, 1, 1)
        waitFor("안내") { a.readyButton != null }
        a.readyButton!!.performClick()
        waitFor("플레이") { a.step == ForeheadActivity.Step.PLAY }
        assertFalse("터치 조작이면 센서 등록 안 함", shadowSm.hasListener(a.sensorListener))
        // 시작 화면에서 조작 바꾸기 → 저장
        ForeheadStore.setTouch(app, false)
        val ctl = Robolectric.buildActivity(ForeheadActivity::class.java, Party.gameIntent(app, ForeheadModule, three)).setup()
        val b = ctl.get()
        b.beeper.enabled = false
        waitFor("카테고리") { b.controlButton != null }
        assertFalse(b.touchMode)
        b.controlButton!!.performClick()
        assertTrue(b.touchMode)
        assertTrue(ForeheadStore.touch(app))
        b.controlButton!!.performClick()
        assertFalse(ForeheadStore.touch(app))
        b.startButton!!.performClick()
        waitFor("안내") { b.readyButton != null }
        b.readyButton!!.performClick()
        assertTrue(shadowSm.hasListener(b.sensorListener))
        ctl.pause().stop().destroy()
        assertFalse("종료 시 해제", shadowSm.hasListener(b.sensorListener))
    }

    @Test
    fun savedWordsAndLapsAreUsed() {
        val mine = ForeheadWords.DEFAULT.toMutableList()
        mine[2] = listOf("가", "나", "다", "라", "마", "바")
        ForeheadStore.saveWords(app, mine)
        ForeheadStore.setLaps(app, 2)
        ForeheadStore.setCategory(app, 2)
        val a = launch(three)
        waitFor("카테고리") { a.startButton != null }
        a.startButton!!.performClick()
        assertEquals(2, a.game.laps)
        assertEquals(6, a.game.totalTurns)
        assertEquals("직업", a.game.categoryName)
        waitFor("안내") { a.readyButton != null }
        a.readyButton!!.performClick()
        waitFor("플레이") { a.step == ForeheadActivity.Step.PLAY }
        assertTrue(a.playView.word in mine[2])
        assertNotNull(a.playView.shownText)
    }
}
