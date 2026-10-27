package com.siwoo.party.games.yut.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.yut.Phase
import com.siwoo.party.games.yut.PieceState
import com.siwoo.party.games.yut.YutBoard
import com.siwoo.party.games.yut.YutModule
import com.siwoo.party.games.yut.YutResult
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
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 윷놀이 화면: 윷가락 터치 → 칩·말·도착 칸 터치로 2인(말 2개) 판을 끝까지 → 순위,
 * 3인 1위 확정 뒤 [여기서 종료]·[끝까지 진행], 흔들기 센서 등록/해제, 일시정지 중 연출 멈춤, 설정 저장.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class YutUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val app: Context get() = RuntimeEnvironment.getApplication()

    private val DO = booleanArrayOf(false, true, false, false)
    private val GAE = booleanArrayOf(false, true, true, false)
    private val YUT = booleanArrayOf(true, true, true, true)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        YutActivity.randomOverride = null
        app.getSharedPreferences(YutSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("party", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int = 3): YutActivity {
        YutActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, YutModule, players)
        return Robolectric.buildActivity(YutActivity::class.java, i).setup().get().also { it.beeper.enabled = false }
    }

    private val pt = PointF()

    private fun tap(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun tapSticks(a: YutActivity) = tap(a.sticks, a.sticks.width / 2f, a.sticks.height * 0.6f)
    private fun tapNode(a: YutActivity, n: Int) { a.board.nodeCenter(n, pt); tap(a.board, pt.x + 3f, pt.y - 2f) }
    private fun tapWaiting(a: YutActivity, p: Int, k: Int) { a.panel.slotCenter(p, k, pt); tap(a.panel, pt.x, pt.y) }
    private fun tapChip(a: YutActivity, i: Int) { a.queueView.chipCenter(i, pt); tap(a.queueView, pt.x, pt.y) }

    private fun throwWith(a: YutActivity, sticks: BooleanArray) {
        waitFor("던질 차례") { a.acceptingThrow }
        a.nextSticks = sticks
        tapSticks(a)
    }

    /** 대기열에서 쓸 수 있는 첫 칩 → 가장 앞선 말(없으면 대기 말) → 빛나는 칸 터치 */
    private fun moveByTouch(a: YutActivity) {
        val g = a.game
        assertEquals("대기열 칩 = 게임 대기열", g.queue.size, a.queueView.chipCount)
        for (i in g.queue.indices) assertEquals(g.queue[i], a.queueView.chipAt(i))
        val qi = g.queue.indices.first { g.isUsable(g.queue[it]) }
        tapChip(a, qi)
        assertEquals(qi, a.selectedChip)
        val p = g.current
        val onBoard = (0 until g.pieces).filter { g.state(p, it) == PieceState.BOARD && g.plan(qi, it) != null }
        val k = onBoard.maxByOrNull { g.progress(p, it) }
            ?: (0 until g.pieces).first { g.state(p, it) == PieceState.WAITING && g.plan(qi, it) != null }
        if (g.state(p, k) == PieceState.BOARD) {
            // 이미 (자동으로) 고른 묶음이면 다시 누르지 않음 (참먹이에 선 말은 누르면 바로 골인 확정이라)
            if (!(a.plan != null && g.piecesAt(p, g.node(p, k)).contains(a.selectedPiece))) tapNode(a, g.node(p, k))
        } else tapWaiting(a, p, k)
        val pl = a.plan
        assertNotNull("말을 고르면 이동 계획", pl)
        assertTrue("도착 칸 발광 미리보기", a.board.hasPreview)
        val dest = if (pl!!.goal) YutBoard.START else pl.dest
        assertEquals(dest, a.board.previewNode)
        assertEquals(pl.goal, a.board.previewGoal)
        tapNode(a, dest)
        assertTrue("확정 → 이동 연출", a.busy)
        assertFalse(a.board.hasPreview)
    }

    @Test
    fun twoPlayersWithTwoPiecesPlayToTheEndByTouch() {
        // 설정: 말 2개 (+ 연출 빠르게)
        YutSettings.setPieces(app, 2)
        app.getSharedPreferences("party", Context.MODE_PRIVATE).edit().putInt("speed", 2).commit()
        val a = launch(four.take(2), seed = 11)
        val g = a.game
        assertEquals(2, g.pieces)
        assertEquals(2, g.waitingCount(0))
        assertFalse("시작 배너 동안 던지기 막힘", a.acceptingThrow)
        var throws = 0
        var moves = 0
        var sawCapture = false
        var sawStack = false
        var guard = 0
        while (!a.isFinishing) {
            check(guard++ < 20000) { "끝나지 않음" }
            when {
                a.acceptingThrow -> {
                    val before = g.queue.size
                    tapSticks(a)
                    assertTrue("던지는 중", a.busy)
                    assertTrue(a.sticks.isTossing)
                    throws++
                    // 던지는 중에 다시 눌러도 무시
                    tapSticks(a)
                    assertTrue(g.queue.size <= before + 1)
                }
                a.acceptingMove -> {
                    val waitingBefore = (0 until 2).sumOf { g.waitingCount(it) }
                    moveByTouch(a)
                    moves++
                    // 판 위 겹침·잡기 확인
                    for (n in 0 until YutBoard.NODES) for (p in 0 until 2) if (g.piecesAt(p, n).size >= 2) sawStack = true
                    if ((0 until 2).sumOf { g.waitingCount(it) } > waitingBefore) sawCapture = true
                }
                else -> idle(50)
            }
        }
        assertTrue("여러 번 던짐 $throws", throws >= 4)
        assertTrue(moves >= 4)
        assertEquals(Phase.OVER, g.phase)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        val winner = (0 until 2).first { g.goalCount(it) == 2 }
        assertEquals(1, ranks[winner])
        assertEquals(2, ranks[1 - winner])
        assertArrayEquals(g.ranks, ranks)
        println("윷 2인: 던지기 $throws, 이동 $moves, 업기 $sawStack, 잡기 $sawCapture")
    }

    @Test
    fun threePlayersStopAfterFirstFinisher() {
        YutSettings.setPieces(app, 2)
        val a = launch(four.take(3))
        val g = a.game
        waitFor("첫 던지기") { a.acceptingThrow }
        assertEquals(0, g.current)
        // 가람: 하나 골인, 하나는 날윷(19) / 나래: 7 / 다온: 방(모 쪽, 진행 17)
        g.setupPiece(0, 0, -2)
        g.setupPiece(0, 1, 19)
        g.setupPiece(1, 0, 7)
        g.setupPiece(2, 0, 22, YutBoard.ROUTE_MO)
        a.board.sync(g)
        a.panel.sync(g)
        throwWith(a, DO)
        waitFor("이동") { a.acceptingMove }
        tapNode(a, 19)
        assertTrue(a.board.previewGoal)
        tapNode(a, YutBoard.START)
        waitFor("1위 확정 팝업") { a.endChoice != null }
        assertEquals(1, g.finishRank[0])
        assertFalse(a.acceptingThrow)
        a.stopButton!!.performClick()
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals("골인 수 → 진행 거리 순", intArrayOf(1, 3, 2), ranks)
        assertTrue(g.stoppedEarly)
    }

    @Test
    fun threePlayersContinueAfterFirstFinisherSkipsFinishedPlayer() {
        YutSettings.setPieces(app, 2)
        val a = launch(four.take(3))
        val g = a.game
        waitFor("첫 던지기") { a.acceptingThrow }
        g.setupPiece(0, 0, -2)
        g.setupPiece(0, 1, 18)
        a.board.sync(g)
        a.panel.sync(g)
        throwWith(a, GAE)
        waitFor("이동") { a.acceptingMove }
        tapNode(a, 18)
        tapNode(a, YutBoard.START)
        waitFor("1위 확정 팝업") { a.endChoice != null }
        a.continueButton!!.performClick()
        waitFor("다음 사람 던지기") { a.acceptingThrow }
        assertNull(a.endChoice)
        assertEquals(1, g.current)
        assertEquals(Phase.THROW, g.phase)
        assertFalse(a.isFinishing)
        assertEquals(PieceState.DONE, a.panel.stateShown(0, 1))
    }

    @Test
    fun stackCaptureAndExtraThrowThroughTouch() {
        val a = launch(four.take(2))
        val g = a.game
        waitFor("첫 던지기") { a.acceptingThrow }
        g.setupPiece(0, 0, 2)
        g.setupPiece(1, 0, 4)
        g.setupPiece(1, 1, 4)
        a.board.sync(g)
        // 윷 → 한 번 더 던지기 (대기열에 쌓임)
        throwWith(a, YUT)
        waitFor("한 번 더") { a.acceptingThrow }
        assertEquals(listOf(YutResult.YUT), g.queue)
        assertEquals(1, a.queueView.chipCount)
        throwWith(a, GAE)
        waitFor("이동") { a.acceptingMove }
        assertEquals(2, a.queueView.chipCount)
        // 개 칩 → 2번 칸 말 → 4번 칸 (상대 묶음 잡기)
        tapChip(a, 1)
        tapNode(a, 2)
        assertEquals(4, a.board.previewNode)
        tapNode(a, 4)
        waitFor("잡기 뒤 한 번 더") { a.acceptingThrow }
        assertEquals(0, g.current)
        assertEquals(2 + 2, g.waitingCount(1))
        assertEquals("남은 윷은 그대로", listOf(YutResult.YUT), g.queue)
        waitFor("패널로 돌아옴") { a.panel.stateShown(1, 0) == PieceState.WAITING }
        // 도 → 대기열 [윷, 도] → 윷으로 새 말을 4번 칸 내 말 위에 (업기) → 도로 묶음이 함께 5번 칸
        throwWith(a, DO)
        waitFor("이동") { a.acceptingMove }
        tapChip(a, 0)
        tapWaiting(a, 0, 1)
        assertEquals(4, a.board.previewNode)
        tapNode(a, 4)
        waitFor("이동") { a.acceptingMove }
        assertEquals("업기", 2, g.stackSize(0, 0))
        assertEquals(2, a.board.countShown(4))
        assertEquals(listOf(YutResult.DO), g.queue)
        tapNode(a, 4)
        assertEquals(5, a.board.previewNode)
        tapNode(a, 5)
        waitFor("차례 끝") { a.acceptingThrow && g.current == 1 }
        assertEquals(5, g.node(0, 0))
        assertEquals(5, g.node(0, 1))
        assertEquals(2, a.board.countShown(5))
    }

    @Test
    fun shakeSensorRegisteredOnlyWhileThrowableAndUnregisteredOnPause() {
        YutSettings.setFlag(app, YutSettings.KEY_SHAKE, true)
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadow: ShadowSensorManager = shadowOf(sm)
        val accel = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadow.addSensor(accel)
        YutActivity.randomOverride = Random(3)
        val ctl = Robolectric.buildActivity(YutActivity::class.java, Party.gameIntent(app, YutModule, four.take(2))).setup()
        val a = ctl.get()
        a.beeper.enabled = false
        assertTrue(a.shakeEnabled)
        assertFalse("시작 배너 중에는 등록 안 함", shadow.hasListener(a.shakeListener))
        waitFor("던질 차례") { a.acceptingThrow }
        assertTrue("던질 수 있을 때 등록", shadow.hasListener(a.shakeListener))
        assertTrue(a.sensorRegistered)
        // 일시정지 → 해제, 다시 → 등록
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지 중 해제", shadow.hasListener(a.shakeListener))
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitFor("다시 등록") { shadow.hasListener(a.shakeListener) }
        // 약한 움직임은 무시
        val soft = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER)
        soft.values[0] = 0f; soft.values[1] = 0f; soft.values[2] = 9.8f
        shadow.sendSensorEventToListeners(soft)
        assertTrue(a.acceptingThrow)
        // 세게 흔들면 던짐 → 던지는 순간 해제
        a.nextSticks = GAE
        val hard = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER)
        hard.values[0] = 30f; hard.values[1] = 12f; hard.values[2] = 9.8f
        shadow.sendSensorEventToListeners(hard)
        assertTrue("흔들어 던짐", a.busy)
        assertTrue(a.sticks.isTossing)
        assertFalse("던지는 중 해제", shadow.hasListener(a.shakeListener))
        // 이동 고르는 동안에도 해제
        waitFor("이동") { a.acceptingMove }
        assertFalse(shadow.hasListener(a.shakeListener))
        assertFalse(a.sensorRegistered)
        // 다시 던질 차례가 되면 등록 → Activity 종료 시 해제
        val k = (0 until a.game.pieces).first { a.game.plan(0, it) != null }
        tapWaiting(a, 0, k)
        tapNode(a, a.plan!!.dest)
        waitFor("다음 던지기") { a.acceptingThrow }
        assertTrue(shadow.hasListener(a.shakeListener))
        ctl.pause().stop().destroy()
        assertFalse("종료 시 해제", shadow.hasListener(a.shakeListener))

        // 설정을 끄면 등록하지 않음
        YutSettings.setFlag(app, YutSettings.KEY_SHAKE, false)
        val b = launch(four.take(2))
        waitFor("던질 차례") { b.acceptingThrow }
        assertFalse(b.sensorRegistered)
        assertFalse(shadow.hasListener(b.shakeListener))
    }

    @Test
    fun pauseStopsAnimationsAndInput() {
        val a = launch(four.take(2))
        val g = a.game
        throwWith(a, GAE)
        idle(100)
        assertTrue(a.sticks.isTossing)
        assertTrue("던지기 연출 중 프레임 루프", a.sticks.isAnimating)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(a.gamePaused)
        assertFalse("일시정지 → 연출 멈춤", a.sticks.isAnimating)
        assertFalse(a.board.isAnimating)
        idle(4000)
        assertTrue("시계가 멈춰 던지기가 끝나지 않음", a.sticks.isTossing)
        assertEquals("결과가 아직 대기열에 안 올라옴", 0, a.queueView.chipCount)
        assertFalse(a.acceptingMove)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("다시 → 던지기 연출 이어서") { a.sticks.isAnimating }
        assertFalse(a.gamePaused)
        waitFor("이동") { a.acceptingMove }
        assertEquals(1, a.queueView.chipCount)
        // 미리보기 발광 중 일시정지 → 루프 멈춤, 터치 무시
        assertTrue(a.board.hasPreview) // 대기 말 하나뿐 → 자동 선택
        assertTrue(a.board.isAnimating)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(a.board.isAnimating)
        tapNode(a, 2)
        idle(200)
        assertEquals("일시정지 중 확정 안 됨", Phase.MOVE, g.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("다시 → 발광 이어서") { a.board.isAnimating }
        // 이동 연출 중 일시정지 → 말이 멈춤
        tapNode(a, 2)
        idle(60)
        assertTrue(a.board.isMoving)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(a.board.isAnimating)
        idle(3000)
        assertTrue("일시정지 중에는 이동 연출이 진행되지 않음", a.board.isMoving)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("다음 차례") { a.acceptingThrow && g.current == 1 }
        assertEquals(0, a.board.ownerShown(2))
    }

    @Test
    fun settingsPopupSavesAndGameUsesThem() {
        val a = launch(four.take(2))
        assertEquals(4, a.game.pieces)
        val popup = YutSettings.show(a.popups)
        idle(300)
        fun find(v: View, desc: String): View? {
            if (v.contentDescription == desc) return v
            if (v is android.view.ViewGroup) for (k in 0 until v.childCount) find(v.getChildAt(k), desc)?.let { return it }
            return null
        }
        find(popup.content, "말 3개")!!.performClick()
        find(popup.content, "낙")!!.performClick()
        find(popup.content, "빽도 대기말 올리기")!!.performClick()
        find(popup.content, "흔들어 던지기")!!.performClick()
        assertEquals(3, YutSettings.pieces(app))
        assertTrue(YutSettings.nak(app))
        assertTrue(YutSettings.backdoEnter(app))
        assertTrue(YutSettings.shake(app))
        find(popup.content, "흔들어 던지기")!!.performClick()
        assertFalse(YutSettings.shake(app))
        assertEquals("윷놀이 설정", YutModule.settingsLabel)
        val b = launch(four.take(3))
        assertEquals(3, b.game.pieces)
        assertTrue(b.game.rules.nak)
        assertTrue(b.game.rules.backdoEnter)
        assertFalse(b.shakeEnabled)
    }
}
