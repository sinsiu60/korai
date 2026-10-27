package com.siwoo.party.hub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Immersive
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.PlayerSetupPopup
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Transitions
import com.siwoo.party.common.ui.Ui

/**
 * 시작: 로고 "시우 공방"(페이드) → 타이틀 "시우 파티" → [시작하기]
 * → 이전 모임이 있으면 [이전 모임 이어하기] / [새 모임] → 새 모임이면 플레이어 설정 → 허브
 */
class SplashActivity : Activity() {

    private lateinit var root: FrameLayout
    lateinit var popups: PopupLayer
        private set
    private lateinit var logo: View
    private lateinit var title: LinearLayout
    private lateinit var store: SessionStore
    private val handler = Handler(Looper.getMainLooper())
    private var titleShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SessionStore(this)
        val settings = PartySettings(this)
        // 처음 실행: 동시 터치 지원 확인 (4명 동시 터치 게임 카드에 경고용)
        if (settings.touchPoints < 0) settings.touchPoints = MultiTouchZoneView.supportedTouches(this)
        Immersive.apply(window)

        root = FrameLayout(this)
        root.background = Theme.background()
        root.addView(PartyArt(this), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        logo = Theme.vertical(this).apply {
            gravity = Gravity.CENTER
            addView(Theme.text(this@SplashActivity, "시우 공방", 34f, Theme.GOLD_LIGHT, bold = true).apply { letterSpacing = 0.35f; gravity = Gravity.CENTER },
                Theme.lp(Theme.MATCH, Theme.WRAP))
            addView(Theme.text(this@SplashActivity, "SIWOO WORKSHOP", 12f, Theme.TEXT_SUB).apply { letterSpacing = 0.5f; gravity = Gravity.CENTER },
                Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@SplashActivity, 8f) })
        }
        root.addView(logo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        title = Theme.vertical(this).apply { gravity = Gravity.CENTER; visibility = View.INVISIBLE }
        title.addView(TitleText(this), LinearLayout.LayoutParams(Theme.dp(this, 440f), Theme.dp(this, 112f)))
        title.addView(Theme.text(this, "다 같이 하는 파티 게임 모음집", 16f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER },
            Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@SplashActivity, 2f) })
        val start = GameButton(this, Theme.GOLD_DARK, 22f).apply { text = "시작하기" }
        start.setOnClickListener { start.bounce(); onStartTapped() }
        title.addView(start, LinearLayout.LayoutParams(Theme.dp(this, 240f), Theme.dp(this, 60f)).apply { topMargin = Theme.dp(this@SplashActivity, 24f) })
        root.addView(title, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        Transitions.noSystemAnim(this)

        if (savedInstanceState != null || intent.getBooleanExtra(EXTRA_SKIP_LOGO, false)) {
            logo.visibility = View.GONE
            showTitle(animate = false)
            if (intent.getBooleanExtra(EXTRA_OPEN_SETUP, false) && savedInstanceState == null) root.post { newSession() }
        } else {
            // 로고: 페이드인 → 잠시 → 페이드아웃 → 타이틀 (터치하면 바로 넘김)
            logo.alpha = 0f
            logo.animate().alpha(1f).setDuration(500).start()
            handler.postDelayed({ hideLogo() }, PartyConfig.LOGO_MS)
            root.setOnClickListener { hideLogo() }
        }
    }

    private fun hideLogo() {
        if (titleShown) return
        root.setOnClickListener(null)
        logo.animate().cancel()
        logo.animate().alpha(0f).setDuration(350).withEndAction { logo.visibility = View.GONE }.start()
        showTitle(animate = true)
    }

    private fun showTitle(animate: Boolean) {
        if (titleShown) return
        titleShown = true
        title.visibility = View.VISIBLE
        if (!animate) return
        title.alpha = 0f
        title.scaleX = 0.85f
        title.scaleY = 0.85f
        title.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(250).setDuration(600).setInterpolator(OvershootInterpolator(1.4f)).start()
    }

    /** [시작하기]: 이전 모임이 있으면 이어하기/새 모임, 없으면 플레이어 설정 */
    private fun onStartTapped() {
        if (popups.isShowing) return
        val saved = store.load()
        if (saved == null) { newSession(); return }
        val card = Ui.darkCard(this, padDp = 22f).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(Theme.text(this, "이전 모임이 있어요", 22f, Theme.GOLD, bold = true))
        val leader = saved.leaders().joinToString(", ") { it.name }
        val sub = saved.players.joinToString(" · ") { "${it.name} ${saved.score(it.id)}점" } +
            "\n${saved.history.size}판 진행" + if (leader.isNotEmpty()) " · 선두 $leader" else ""
        card.addView(Theme.text(this, sub, 14f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) }, Ui.wrapGap(this, 8f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(this, "이전 모임 이어하기", Theme.GREEN_DARK, 19f) {
            popup?.dismiss(animate = false)
            openHub()
        }, LinearLayout.LayoutParams(Theme.dp(this, 300f), Theme.dp(this, 56f)).apply { topMargin = Theme.dp(this@SplashActivity, 16f) })
        card.addView(Ui.button(this, "새 모임", Theme.BLUE) {
            popup?.dismiss(animate = false)
            newSession()
        }, LinearLayout.LayoutParams(Theme.dp(this, 300f), Theme.dp(this, 48f)).apply { topMargin = Theme.dp(this@SplashActivity, 8f) })
        popup = popups.show(card, cancelable = true)
    }

    private fun newSession() {
        PlayerSetupPopup(popups, null, cancelable = true, onDone = { entries ->
            // 새 모임: 이전 모임의 저장 게임은 지운다
            for (g in GameRegistry.games) g.clearSave(this)
            val players = entries.mapIndexed { i, (name, color) -> PlayerInfo(i, name, color) }
            store.save(Session(players))
            handler.postDelayed({ openHub() }, 250)
        }).show()
    }

    private fun openHub() {
        Transitions.start(this, Intent(this, HubActivity::class.java))
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(window)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (popups.handleBack()) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_SKIP_LOGO = "skipLogo"
        const val EXTRA_OPEN_SETUP = "openSetup"
    }

    /** "시우 파티": 굵은 금색 그라데이션 글씨 + 짙은 외곽선 + 은은한 빛 */
    private class TitleText(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF060A24.toInt()
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0x55FFC93C; style = Paint.Style.STROKE }
        private var base = 0f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val size = h * 0.74f
            fill.textSize = size; outline.textSize = size; glow.textSize = size
            outline.strokeWidth = size * 0.13f
            glow.strokeWidth = size * 0.3f
            base = h * 0.8f
            fill.shader = LinearGradient(0f, base - size, 0f, base, Theme.GOLD_LIGHT, Theme.GOLD_DARK, Shader.TileMode.CLAMP)
        }

        override fun onDraw(c: Canvas) {
            val x = width / 2f
            c.drawText(TEXT, x, base, glow)
            c.drawText(TEXT, x, base, outline)
            c.drawText(TEXT, x, base, fill)
        }

        companion object { private const val TEXT = "시우 파티" }
    }

    /** 배경 장식: 풍선·꽃가루·별 (한 번만 그림, 이미지 없음) */
    private class PartyArt(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val colors = intArrayOf(0x66FF5252, 0x66448AFF, 0x6600D27A, 0x66FFD600, 0x66B46CFF)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            balloon(c, w * 0.1f, h * 0.3f, h * 0.09f, colors[0])
            balloon(c, w * 0.17f, h * 0.55f, h * 0.07f, colors[1])
            balloon(c, w * 0.88f, h * 0.28f, h * 0.085f, colors[3])
            balloon(c, w * 0.82f, h * 0.6f, h * 0.065f, colors[2])
            for (k in 0 until 26) {
                val x = ((k * 97) % 100) / 100f * w
                val y = ((k * 53 + 11) % 100) / 100f * h
                p.color = colors[k % colors.size]
                c.save()
                c.rotate((k * 37 % 90).toFloat(), x, y)
                c.drawRect(x, y, x + h * 0.018f, y + h * 0.008f, p)
                c.restore()
            }
            p.color = 0x66FFC93C
            for (k in 0 until 14) {
                val x = ((k * 61 + 7) % 100) / 100f * w
                val y = ((k * 29 + 41) % 100) / 100f * h
                c.drawCircle(x, y, h * (0.004f + (k % 3) * 0.002f), p)
            }
        }

        private fun balloon(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
            p.color = color
            c.drawOval(x - r * 0.85f, y - r, x + r * 0.85f, y + r, p)
            p.color = 0x44FFFFFF
            c.drawLine(x, y + r, x - r * 0.2f, y + r * 2.6f, p)
            c.drawCircle(x - r * 0.3f, y - r * 0.4f, r * 0.15f, p)
        }
    }
}
