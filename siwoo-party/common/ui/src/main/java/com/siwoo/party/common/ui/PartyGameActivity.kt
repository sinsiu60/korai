package com.siwoo.party.common.ui

import android.app.Activity
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo

/**
 * 모음집 게임 화면의 공통 바탕. 게임은 이것을 상속해 onBuild 에서 stage 에 화면을 올린다.
 *
 * 화면 층: 남색 배경 root → stage(게임) → fx(배너·파티클) → popups(팝업)
 * 제공하는 것
 * - players: 허브에서 받은 플레이어 (없거나 인원이 안 맞으면 바로 닫힘)
 * - settings / beeper(효과음 설정 반영) / t(ms)(연출 속도 배율)
 * - timers: 일시정지되는 타이머. 게임 진행용 지연은 모두 timers.after 로 → 일시정지·백그라운드에서 자동으로 멈춤
 * - 뒤로 가기 → 공통 일시정지 메뉴, 백그라운드에 갔다 오면 일시정지 메뉴로 복귀
 * - banner / countdown / handoff / finishWithRanks
 * 게임은 onGamePause / onGameResume 에서 물리 루프·센서·애니메이션처럼 timers 밖의 것만 멈추면 된다.
 */
abstract class PartyGameActivity : Activity() {

    abstract val module: GameModule

    lateinit var players: List<PlayerInfo>
        private set
    lateinit var settings: PartySettings
        private set
    val beeper = Beeper()
    val handler = Handler(Looper.getMainLooper())
    val timers = GameTimers(handler)
    lateinit var root: FrameLayout
        private set
    lateinit var stage: FrameLayout
        private set
    lateinit var fx: FxView
        private set
    lateinit var popups: PopupLayer
        private set

    /** 일시정지 메뉴가 열려 있거나 백그라운드 */
    val gamePaused: Boolean get() = timers.paused
    private var menuOnResume = false
    private var leaving = false
    private val tmpLoc = IntArray(2)
    private val tmpLoc2 = IntArray(2)

    /** 허브로 나갈 때 확인 문구 */
    open val exitNote: String = "지금 판은 점수 없이 끝납니다."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = PartySettings(this)
        beeper.enabled = settings.sound
        val list = Party.players(intent)
        if (list == null || module.unavailableReason(list.size) != null) {
            finish()
            return
        }
        players = list
        Immersive.apply(window)
        root = FrameLayout(this)
        root.background = Theme.background()
        stage = FrameLayout(this)
        root.addView(stage, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        fx = FxView(this)
        root.addView(fx, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        Transitions.enter(this, root)
        onBuild(stage, savedInstanceState)
    }

    /** 게임 화면 만들기 (stage 는 화면 전체) */
    protected abstract fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?)

    /** 물리 루프·센서·반복 애니메이션 멈춤 (timers 는 자동으로 멈춤) */
    protected open fun onGamePause() {}

    protected open fun onGameResume() {}

    /** 끝날 때 정리 (센서 해제·비트맵 recycle 등) */
    protected open fun onGameDestroy() {}

    fun t(ms: Long): Long = settings.t(ms)

    fun playerColor(index: Int): Int = Theme.playerColor(players[index].colorIndex)

    // ── 일시정지 ──

    fun pauseGame() {
        if (timers.paused) return
        timers.pause()
        onGamePause()
    }

    fun resumeGame() {
        if (!timers.paused || leaving) return
        timers.resume()
        onGameResume()
    }

    fun showPauseMenu() {
        if (leaving || PauseMenu.isShowing(popups)) return
        pauseGame()
        PauseMenu.show(popups, module.name, module.rules, exitNote, onResume = { resumeGame() }) { exitToHub() }
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (!::popups.isInitialized) { finish(); return }
        if (PauseMenu.isShowing(popups)) { popups.handleBack(); return }
        // 게임 안의 팝업(닫을 수 있는 것)은 닫고, 아니면 일시정지
        val top = popups.top()
        if (top != null && top.cancelable) { top.dismiss(); return }
        showPauseMenu()
    }

    override fun onPause() {
        super.onPause()
        if (!::players.isInitialized || leaving || isFinishing) return
        if (!timers.paused) {
            pauseGame()
            menuOnResume = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::players.isInitialized) return
        beeper.enabled = settings.sound
        if (menuOnResume) {
            menuOnResume = false
            showPauseMenu() // 백그라운드에서 돌아오면 일시정지 상태로
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(window)
    }

    override fun onDestroy() {
        timers.cancelAll()
        handler.removeCallbacksAndMessages(null)
        if (::fx.isInitialized) fx.clearAll()
        beeper.release()
        if (::players.isInitialized) onGameDestroy()
        super.onDestroy()
    }

    // ── 결과 ──

    /** ranks[i] = players[i] 의 순위(1부터, 동점 허용) → 허브로 */
    fun finishWithRanks(ranks: IntArray) {
        if (leaving) return
        require(ranks.size == players.size)
        leaving = true
        timers.pause()
        onGamePause()
        Party.finishWithRanks(this, module.id, IntArray(players.size) { players[it].id }, ranks)
    }

    fun exitToHub() {
        if (leaving) return
        leaving = true
        timers.pause()
        onGamePause()
        setResult(RESULT_CANCELED)
        Transitions.finish(this)
    }

    // ── 연출 도우미 ──

    /** 화면 가운데 큰 문구 (외곽선 + 금색 그림자). tone 에 맞는 효과음 */
    fun banner(text: String, sub: String = "", tone: BannerTone = BannerTone.INFO, ms: Long = 1600, sound: Boolean = true) {
        val w = if (fx.width > 0) fx.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
        val h = if (fx.height > 0) fx.height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
        fx.banner(w / 2f, h / 2f, w * 0.8f, text, sub, tone, t(ms))
        if (sound) when (tone) {
            BannerTone.GOOD -> beeper.good()
            BannerTone.BAD -> beeper.bad()
            BannerTone.INFO -> beeper.turn()
        }
    }

    /** 3 · 2 · 1 · 시작! (게임 타이머 기준이라 일시정지되면 같이 멈춘다) */
    fun countdown(onDone: () -> Unit) {
        val step = t(COUNT_STEP_MS).coerceAtLeast(250)
        val words = arrayOf("3", "2", "1")
        for (k in words.indices) timers.after(step * k) { banner(words[k], ms = step, sound = false); beeper.step() }
        timers.after(step * 3) {
            banner("시작!", tone = BannerTone.GOOD, ms = step)
            onDone()
        }
    }

    /** 다음 사람에게 기기를 넘기는 전체 화면 (이전 화면 내용을 완전히 가린다) */
    fun handoff(playerIndex: Int, title: String, sub: String, okText: String = "확인", onOk: () -> Unit): PopupLayer.Popup =
        Handoff.show(popups, players[playerIndex], title, sub, okText, beeper, onOk)

    /** 뷰 v 안의 점 p(뷰 좌표)를 연출 층 좌표로 */
    fun toFx(v: View, p: PointF) {
        v.getLocationInWindow(tmpLoc)
        fx.getLocationInWindow(tmpLoc2)
        p.x += (tmpLoc[0] - tmpLoc2[0]).toFloat()
        p.y += (tmpLoc[1] - tmpLoc2[1]).toFloat()
    }

    /** 화면 흔들기 (amp px, durMs) */
    fun shakeScreen(ampPx: Float, durMs: Long) {
        val anim = android.animation.ValueAnimator.ofFloat(1f, 0f)
        anim.duration = durMs.coerceAtLeast(1)
        anim.addUpdateListener {
            val a = ampPx * (it.animatedValue as Float)
            stage.translationX = (Math.random().toFloat() - 0.5f) * 2f * a
            stage.translationY = (Math.random().toFloat() - 0.5f) * 2f * a
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                stage.translationX = 0f
                stage.translationY = 0f
            }
        })
        anim.start()
    }

    companion object {
        /** 카운트다운 한 칸 (속도 배율 적용 전) */
        const val COUNT_STEP_MS = 800L
    }
}
