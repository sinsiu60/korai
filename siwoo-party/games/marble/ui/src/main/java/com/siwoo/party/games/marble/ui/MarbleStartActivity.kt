package com.siwoo.party.games.marble.ui

import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.*

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.games.marble.GameConfig
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.marble.logic.GameEngine
import com.siwoo.party.games.marble.model.GameState
import com.siwoo.party.games.marble.model.PlayerSetup

/**
 * 허브에서 여는 시우 마블 시작 화면 (플레이어는 허브에서 받으므로 따로 설정하지 않는다).
 * - 같은 사람들의 끝나지 않은 게임이 있으면: [이어하기] / [새 게임]
 * - 새 게임: 게임 방식 고르기 (기본 15라운드, 모임 시간이 길어지지 않게) + 현실 벌칙 모드
 * - [시작] → 순서 추첨 연출 → 게임 화면 (결과는 FORWARD_RESULT 로 허브에 바로 간다)
 */
class MarbleStartActivity : Activity() {

    private lateinit var storage: GameStorage
    private lateinit var settings: PartySettings
    lateinit var popups: PopupLayer
        private set
    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private var players: List<PlayerInfo> = emptyList()
    private var turnLimit = GameConfig.PARTY_DEFAULT_TURN_LIMIT
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = GameStorage(this)
        settings = PartySettings(this)
        val list = Party.players(intent)
        if (list == null || list.size !in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) {
            finish()
            return
        }
        players = list
        Immersive.apply(window)
        root = FrameLayout(this)
        root.background = Theme.background()
        root.addView(TitleArt(this), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val col = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(TitleText(this), LinearLayout.LayoutParams(Theme.dp(this, 360f), Theme.dp(this, 84f)).apply { topMargin = Theme.dp(this@MarbleStartActivity, 12f) })
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        Transitions.enter(this, root)
        root.post {
            val saved = storage.ongoingFor(players)
            if (saved != null) showContinue(saved) else showMode()
        }
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
        cancel()
    }

    /** 허브로 (게임 없이) */
    private fun cancel() {
        if (leaving) return
        leaving = true
        setResult(RESULT_CANCELED)
        Transitions.finish(this)
    }

    // ─────────────────────────────────────────────
    // 이어하기 / 게임 방식
    // ─────────────────────────────────────────────

    private fun showContinue(saved: GameState) {
        val card = Ui.darkCard(this, padDp = 22f).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(Theme.text(this, "진행 중인 게임이 있어요", 22f, Theme.GOLD, bold = true))
        val round = if (saved.turnLimit > 0) "${saved.round} / ${saved.turnLimit} 라운드" else "${saved.round} 라운드 · 최후 1인"
        card.addView(Theme.text(this, round + " · " + saved.players.joinToString(", ") { it.name + if (it.bankrupt) "(파산)" else "" }, 14f, Theme.TEXT_SUB), Ui.wrapGap(this, 6f))
        var popup: PopupLayer.Popup? = null
        val cont = Ui.button(this, "이어하기", Theme.GREEN_DARK, 20f) {
            popup?.dismiss(animate = false)
            openGame()
        }
        val fresh = Ui.button(this, "새 게임", Theme.RED_DARK) {
            Ui.confirm(popups, "새 게임을 시작할까요?", "진행 중인 게임은 지워집니다.", "새 게임", Theme.RED_DARK) {
                popup?.dismiss(animate = false)
                storage.clear()
                showMode()
            }
        }
        card.addView(cont, LinearLayout.LayoutParams(Theme.dp(this, 280f), Theme.dp(this, 56f)).apply { topMargin = Theme.dp(this@MarbleStartActivity, 16f) })
        card.addView(fresh, LinearLayout.LayoutParams(Theme.dp(this, 280f), Theme.dp(this, 46f)).apply { topMargin = Theme.dp(this@MarbleStartActivity, 8f) })
        popup = popups.show(card, cancelable = true, onDismiss = { if (!leaving && !popups.isShowing) cancelLater() })
    }

    /** 팝업을 [취소]/바깥 터치로 닫으면 허브로 (다른 팝업으로 넘어간 경우는 제외) */
    private fun cancelLater() {
        handler.post { if (!popups.isShowing && !starting) cancel() }
    }

    private var starting = false

    private fun showMode() {
        val card = Ui.darkCard(this, padDp = 20f)
        card.addView(Theme.text(this, "게임 방식", 22f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, players.joinToString(" · ") { it.name } + "  (${players.size}인)", 14f, Theme.TEXT_SUB), Ui.wrapGap(this, 4f))
        val modes = Theme.horizontal(this)
        val modeButtons = ArrayList<GameButton>()
        val options = intArrayOf(15, 20, 30, 0)
        val desc = Theme.text(this, "", 13f, Theme.TEXT_SUB)
        fun refresh() {
            for ((k, mb) in modeButtons.withIndex()) mb.color = if (options[k] == turnLimit) Theme.GOLD_DARK else Theme.GRAY
            desc.text = if (turnLimit > 0) "${turnLimit}라운드가 끝나면 총자산 순으로 순위를 정합니다 (약 ${turnLimit * players.size / 2}분)"
            else "마지막 한 명이 남을 때까지 (오래 걸릴 수 있어요)"
        }
        for (v in options) {
            val b = Ui.button(this, if (v == 0) "최후 1인" else "${v}라운드", Theme.GRAY, 16f) { turnLimit = v; refresh() }
            modeButtons.add(b)
            modes.addView(b, Theme.lp(0, Theme.dp(this, 52f), 1f).apply { if (modeButtons.size > 1) leftMargin = Theme.dp(this@MarbleStartActivity, 8f) })
        }
        card.addView(modes, Ui.gap(this, 12f))
        card.addView(desc, Ui.wrapGap(this, 8f))
        val real = GameButton(this, Theme.GRAY, 14f)
        fun refreshReal() {
            real.text = "대형 벌칙 때 현실 벌칙: " + if (storage.realPenaltyMode) "켬" else "끔"
            real.color = if (storage.realPenaltyMode) Theme.RED_DARK else Theme.GRAY
        }
        real.setOnClickListener { storage.realPenaltyMode = !storage.realPenaltyMode; refreshReal() }
        refreshReal()
        card.addView(real, Theme.lp(Theme.MATCH, Theme.dp(this, 42f)).apply { topMargin = Theme.dp(this@MarbleStartActivity, 10f) })
        refresh()
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.buttonRow(this,
            Ui.button(this, "허브로", Theme.GRAY) { popup?.dismiss() },
            Ui.button(this, "시작", Theme.GREEN_DARK, 20f) {
                starting = true
                popup?.dismiss()
                startGame()
            },
            heightDp = 50f,
        ), Ui.gap(this, 14f))
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(this, 520f), onDismiss = { if (!starting) cancelLater() })
    }

    // ─────────────────────────────────────────────
    // 시작 → 순서 추첨 → 게임 화면
    // ─────────────────────────────────────────────

    private fun startGame() {
        val setups = players.map { PlayerSetup(it.name, it.colorIndex, it.id) }
        val state = GameEngine.newGame(setups, turnLimit)
        storage.save(state)
        handler.postDelayed({ showLottery(state) }, 250)
    }

    /** 결과를 허브로 바로 넘기도록 FORWARD_RESULT 로 게임 화면을 연다 */
    private fun openGame() {
        starting = true
        leaving = true
        val i = Intent(this, MainActivity::class.java)
            .putExtras(intent)
            .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        Transitions.start(this, i)
        finish()
    }

    /** 순서 추첨: 각 순번 칸에서 이름이 빠르게 바뀌다가 하나씩 멈춘다 */
    private fun showLottery(state: GameState) {
        val speed = settings.speedFactor
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@MarbleStartActivity, Theme.CARD_LIGHT, Theme.CARD, 22f)
            elevation = Theme.dpf(this@MarbleStartActivity, 14f)
            val p = Theme.dp(this@MarbleStartActivity, 20f)
            setPadding(p * 2, p, p * 2, p)
        }
        val title = Theme.text(this, "순서 추첨!", 26f, Theme.GOLD, bold = true)
        card.addView(title)
        val slots = ArrayList<TextView>()
        for (k in state.players.indices) {
            val row = Theme.horizontal(this)
            row.addView(Theme.text(this, "${k + 1}번", 18f, Theme.TEXT_SUB, bold = true), Theme.lp(Theme.dp(this, 56f), Theme.WRAP))
            val slot = Theme.text(this, "?", 22f, Theme.TEXT, bold = true).apply {
                gravity = Gravity.CENTER
                background = Theme.card(this@MarbleStartActivity, 0x33000000, 12f)
            }
            slots.add(slot)
            row.addView(slot, Theme.lp(Theme.dp(this, 220f), Theme.dp(this, 44f)))
            card.addView(row, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MarbleStartActivity, 8f) })
        }
        popups.show(card, cancelable = false)
        val all = state.players.map { it.name }
        val stopped = BooleanArray(slots.size)
        var tick = 0
        val spin = object : Runnable {
            override fun run() {
                tick++
                for ((k, s) in slots.withIndex()) if (!stopped[k]) s.text = all[(tick + k) % all.size]
                if (stopped.any { !it }) handler.postDelayed(this, 70)
            }
        }
        handler.post(spin)
        for ((k, p) in state.players.withIndex()) {
            handler.postDelayed({
                stopped[k] = true
                val color = Theme.playerColor(p.colorIndex)
                slots[k].text = "● " + p.name
                slots[k].setTextColor(color)
                slots[k].background = Theme.card(this, 0x55000000, 12f, Theme.GOLD, 2f)
                slots[k].scaleX = 1.3f; slots[k].scaleY = 1.3f
                slots[k].animate().scaleX(1f).scaleY(1f).setDuration(250).start()
            }, ((900 + k * 500) * speed).toLong().coerceAtLeast(300L + k * 150L))
        }
        val end = ((900 + state.players.size * 500 + 700) * speed).toLong().coerceAtLeast(1200L)
        handler.postDelayed({ title.text = "게임 시작!" }, end - 300)
        handler.postDelayed({ openGame() }, end + 400)
    }

    /** 제목: 굵은 금색 그라데이션 글씨 + 짙은 외곽선 */
    private class TitleText(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF060A24.toInt()
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0x55FFC93C }
        private var base = 0f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val size = h * 0.72f
            fill.textSize = size; outline.textSize = size; glow.textSize = size
            outline.strokeWidth = size * 0.13f
            glow.strokeWidth = size * 0.3f
            glow.style = Paint.Style.STROKE
            base = h * 0.78f
            fill.shader = LinearGradient(0f, base - size, 0f, base, Theme.GOLD_LIGHT, Theme.GOLD_DARK, Shader.TileMode.CLAMP)
        }

        override fun onDraw(c: Canvas) {
            val x = width / 2f
            c.drawText(TEXT, x, base, glow)
            c.drawText(TEXT, x, base, outline)
            c.drawText(TEXT, x, base, fill)
        }

        companion object { private const val TEXT = "시우 마블" }
    }

    /** 배경 장식: 흩어진 주사위와 금색 별 (한 번만 그림) */
    private class TitleArt(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val s = h * 0.12f
            dice(c, w * 0.12f, h * 0.25f, s, -15f, 5)
            dice(c, w * 0.86f, h * 0.7f, s * 1.1f, 20f, 3)
            dice(c, w * 0.9f, h * 0.18f, s * 0.7f, 8f, 1)
            dice(c, w * 0.1f, h * 0.78f, s * 0.8f, 30f, 6)
            p.color = 0x66FFC93C
            for (k in 0 until 18) {
                val x = ((k * 97) % 100) / 100f * w
                val y = ((k * 53 + 11) % 100) / 100f * h
                c.drawCircle(x, y, h * (0.004f + (k % 3) * 0.002f), p)
            }
        }

        private fun dice(c: Canvas, x: Float, y: Float, s: Float, rot: Float, v: Int) {
            c.save()
            c.translate(x, y)
            c.rotate(rot)
            r.set(-s / 2, -s / 2, s / 2, s / 2)
            p.color = 0x33FFFFFF
            c.drawRoundRect(r, s * 0.2f, s * 0.2f, p)
            p.color = 0x55FFC93C
            val d = s * 0.25f
            val pts = when (v) {
                1 -> floatArrayOf(0f, 0f)
                3 -> floatArrayOf(-d, -d, 0f, 0f, d, d)
                5 -> floatArrayOf(-d, -d, d, -d, 0f, 0f, -d, d, d, d)
                else -> floatArrayOf(-d, -d, d, -d, -d, 0f, d, 0f, -d, d, d, d)
            }
            var k = 0
            while (k < pts.size) { c.drawCircle(pts[k], pts[k + 1], s * 0.08f, p); k += 2 }
            c.restore()
        }
    }
}
