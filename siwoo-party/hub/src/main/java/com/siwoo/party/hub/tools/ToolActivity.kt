package com.siwoo.party.hub.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.SessionCodec
import com.siwoo.party.common.ui.Beeper
import com.siwoo.party.common.ui.FxView
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Immersive
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Transitions
import com.siwoo.party.common.ui.Ui

/**
 * 도구 화면 바탕 (게임 아님: 점수·결과 화면 없음).
 * 남색 배경 + 위쪽 막대 [← 허브] 제목 [도구별 버튼…], 연출 층(fx)·팝업 층.
 * 뒤로 가기 → 팝업이 있으면 닫고, 아니면 허브로.
 * 플레이어: 허브가 넘긴 Party.EXTRA_PLAYERS → 없으면 저장된 모임 → 없으면 기본 이름.
 */
abstract class ToolActivity : Activity() {

    lateinit var settings: PartySettings
        private set
    val beeper = Beeper()
    lateinit var players: List<PlayerInfo>
        private set
    lateinit var root: FrameLayout
        private set
    lateinit var popups: PopupLayer
        private set
    lateinit var fx: FxView
        private set
    /** 위쪽 막대 (도구별 버튼을 덧붙인다) */
    lateinit var topBar: LinearLayout
        private set
    lateinit var backButton: GameButton
        private set

    protected abstract val toolTitle: String

    /** 본문을 만든다 (위쪽 막대 아래 / overlayTop 이면 화면 전체에 깔고 막대가 위에 뜬다) */
    protected abstract fun buildBody(): View

    /** true 면 본문이 화면 전체(막대 뒤까지)를 차지한다 (손가락 추첨) */
    protected open val overlayTop: Boolean = false

    protected open fun onToolPause() {}
    protected open fun onToolResume() {}

    fun t(ms: Long): Long = settings.t(ms)

    fun now(): Long = SystemClock.uptimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = PartySettings(this)
        beeper.enabled = settings.sound
        players = Party.players(intent)?.takeIf { it.isNotEmpty() }
            ?: SessionStore(this).load()?.players?.toList()
            ?: DEFAULT_NAMES.mapIndexed { i, n -> PlayerInfo(i, n, i) }
        Immersive.apply(window)
        root = FrameLayout(this)
        root.background = Theme.background()
        topBar = Theme.horizontal(this).apply {
            val p = Theme.dp(this@ToolActivity, 10f)
            setPadding(p, Theme.dp(this@ToolActivity, 6f), p, 0)
        }
        backButton = Ui.button(this, "← 허브", Theme.GRAY, 15f) { goBack() }
        topBar.addView(backButton, Theme.lp(Theme.dp(this, 84f), Theme.dp(this, 40f)))
        topBar.addView(Theme.text(this, toolTitle, 19f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER_VERTICAL },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { leftMargin = Theme.dp(this@ToolActivity, 12f) })
        val body = buildBody()
        if (overlayTop) {
            root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
            root.addView(topBar, FrameLayout.LayoutParams(MATCH, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        } else {
            val col = Theme.vertical(this)
            col.addView(topBar, Theme.lp(Theme.MATCH, Theme.WRAP))
            col.addView(body, Theme.lp(Theme.MATCH, 0, 1f))
            root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        fx = FxView(this)
        root.addView(fx, FrameLayout.LayoutParams(MATCH, MATCH))
        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        Transitions.enter(this, root)
    }

    /** 위쪽 막대 오른쪽 빈칸 (도구 버튼을 오른쪽으로 민다) */
    protected fun topSpacer() {
        topBar.addView(View(this), Theme.lp(0, 1, 1f))
    }

    protected fun addTop(v: View, widthDp: Float, leftDp: Float = 8f) {
        topBar.addView(v, Theme.lp(Theme.dp(this, widthDp), Theme.dp(this, 40f)).apply { leftMargin = Theme.dp(this@ToolActivity, leftDp) })
    }

    fun goBack() {
        beeper.step()
        Transitions.finish(this)
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (::popups.isInitialized && popups.handleBack()) return
        goBack()
    }

    override fun onResume() {
        super.onResume()
        beeper.enabled = settings.sound
        Immersive.apply(window)
        onToolResume()
    }

    override fun onPause() {
        onToolPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(window)
    }

    override fun onDestroy() {
        beeper.release()
        super.onDestroy()
    }

    companion object {
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        val DEFAULT_NAMES = listOf("플레이어1", "플레이어2")

        /** 허브 → 도구 화면 (지금 모임 플레이어 이름·색을 넘김) */
        fun intent(ctx: Context, cls: Class<out ToolActivity>, players: List<PlayerInfo>): Intent =
            Intent(ctx, cls).putExtra(Party.EXTRA_PLAYERS, SessionCodec.encodePlayers(players))

        fun open(host: Activity, cls: Class<out ToolActivity>, players: List<PlayerInfo>) {
            Transitions.start(host, intent(host, cls, players))
        }
    }
}

/**
 * 연출 프레임 루프 (postOnAnimation). step(dtMs) 가 true 를 돌려주는 동안만 돈다.
 * pause() 중에는 멈추고, 그 사이 start() 요청은 resume() 때 이어서 돈다.
 */
class FrameLoop(private val view: View, private val step: Step) : Runnable {
    /** 한 프레임 (박싱 없는 SAM) */
    fun interface Step {
        fun step(dtMs: Long): Boolean
    }

    var running = false
        private set
    private var paused = false
    private var wanted = false
    private var last = 0L

    fun start() {
        if (paused) { wanted = true; return }
        if (running) return
        running = true
        last = SystemClock.uptimeMillis()
        view.postOnAnimation(this)
    }

    fun stop() {
        running = false
        wanted = false
        view.removeCallbacks(this)
    }

    fun pause() {
        if (running) {
            running = false
            view.removeCallbacks(this)
            wanted = true
        }
        paused = true
    }

    fun resume() {
        paused = false
        if (wanted) {
            wanted = false
            start()
        }
    }

    override fun run() {
        if (!running) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - last).coerceIn(0L, MAX_DT)
        last = now
        if (step.step(dt)) view.postOnAnimation(this) else running = false
    }

    companion object {
        const val MAX_DT = 50L
    }
}
