package com.siwoo.party.games.spintop.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.aim.GaugeDrawer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.spintop.GameConfig
import com.siwoo.party.games.spintop.Phase
import com.siwoo.party.games.spintop.SpinRules
import com.siwoo.party.games.spintop.SpinTopGame

/**
 * 오른쪽 조작판 (하나의 View).
 * - 준비: "○○ 차례" + 큰 [줄 감기!] 버튼(감긴 줄 그림·연타 수) + 5초 시간 막대 + 던지기 게이지(공통 GaugeDrawer, 가로) + 회전력·안정성 막대
 *   → 화면 어디를 눌러도(손가락 여러 개도 각각) listener.onPanelTap — 연타·게이지 멈춤 판단은 Activity 가.
 * - 대결: 사람마다 한 줄 (도는 팽이 그림 + 이름 + 회전력 막대 + 상태 글자)
 * 시간은 Activity 가 넣는 게임 시계 [now]. 스스로 반복해서 그리지 않는다. onDraw 할당 없음 (글자는 미리 만든 배열·setter).
 */
class ControlView(context: Context) : View(context) {

    fun interface Listener {
        fun onPanelTap()
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: SpinTopGame? = null
    private var colors = IntArray(GameConfig.MAX_PLAYERS)
    private var turnTitles = emptyArray<String>()
    private var names = emptyArray<String>()
    private val status = Array(GameConfig.MAX_PLAYERS) { "" }

    /** 게임 시계 */
    var now = 0L
    /** 줄 감기 시작 시각 (-1 = 아직) */
    var mashStart = -1L
    /** 마지막 누름 (버튼 눌림 연출) */
    private var pressAt = -10_000L

    private var title = ""
    private var titleColor = Theme.GOLD
    private var sub = ""

    // ── 배치 ──
    private var btnX = 0f
    private var btnY = 0f
    private var btnR = 0f
    private val timeBar = RectF()
    private val powerBar = RectF()
    private val stabBar = RectF()
    private val tmp = RectF()
    private var rowTop = 0f
    private var rowH = 0f

    private val gauge = GaugeDrawer(context).apply {
        orientation = GaugeDrawer.Orientation.HORIZONTAL
        label = GAUGE_LABEL
        zones = listOf(SpinRules.sweetZone)
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val titleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xEE0B1340.toInt()
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val smallRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD_LIGHT }

    fun bind(g: SpinTopGame, playerColors: IntArray, playerNames: List<String>) {
        game = g
        colors = playerColors
        names = Array(playerNames.size) { playerNames[it] }
        turnTitles = Array(playerNames.size) { playerNames[it] + " 차례" }
        invalidate()
    }

    /** 위 제목 (준비할 사람 이름 등) */
    fun setTitle(text: String, color: Int, subText: String) {
        title = text
        titleColor = color
        sub = subText
        fitTitle()
        invalidate()
    }

    /** 긴 이름도 판 폭 안에 들어가게 제목 글자 크기를 줄임 (값이 바뀔 때만) */
    private fun fitTitle() {
        var size = 21f * d
        titlePaint.textSize = size
        val maxW = width - 20f * d
        if (maxW > 0f) {
            while (size > 13f * d && titlePaint.measureText(title) > maxW) {
                size -= 1f * d
                titlePaint.textSize = size
            }
        }
        titleStroke.textSize = size
    }

    /** 준비하는 사람 제목 ("○○ 차례") */
    fun setTurn(player: Int, subText: String) = setTitle(turnTitles[player], colors[player], subText)

    /** 대결 중 상태 글자 (쓰러짐·순위) */
    fun setStatus(i: Int, text: String) {
        if (status[i] == text) return
        status[i] = text
        invalidate()
    }

    fun clearStatus() {
        for (i in status.indices) status[i] = ""
        invalidate()
    }

    /** 지금 보여 주는 제목 (테스트용) */
    val shownTitle: String get() = title

    /** 줄 감기 버튼 가운데 (테스트에서 누름) */
    val buttonX: Float get() = btnX
    val buttonY: Float get() = btnY

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val m = 14f * d
        titleStroke.strokeWidth = 4f * d
        fitTitle()
        subPaint.textSize = 12f * d
        btnText.textSize = 19f * d
        small.textSize = 12f * d
        smallRight.textSize = 12f * d
        gauge.labelSize = 12f * d
        // 준비 화면
        btnR = minOf(w * 0.27f, h * 0.17f)
        btnX = w / 2f
        btnY = 58f * d + btnR
        val tb = btnY + btnR + 10f * d
        timeBar.set(m, tb, w - m, tb + 9f * d)
        val gt = timeBar.bottom + 28f * d
        gauge.layout(m + 4f * d, gt, w - m - 4f * d, gt + 20f * d)
        val sb = h - 14f * d
        stabBar.set(m + 48f * d, sb - 11f * d, w - m - 38f * d, sb)
        powerBar.set(stabBar.left, sb - 34f * d, stabBar.right, sb - 23f * d)
        // 대결 화면
        rowTop = 62f * d
        val n = game?.players ?: GameConfig.MAX_PLAYERS
        rowH = minOf(62f * d, (h - rowTop - 8f * d) / n)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            pressAt = now
            listener?.onPanelTap()
            invalidate()
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        // 판 배경
        p.style = Paint.Style.FILL
        p.color = 0x5522337A
        tmp.set(0f, 0f, w, height.toFloat())
        c.drawRoundRect(tmp, 16f * d, 16f * d, p)
        line.strokeWidth = 1.5f * d
        line.color = 0x55FFC93C
        c.drawRoundRect(tmp, 16f * d, 16f * d, line)
        // 제목
        if (title.isNotEmpty()) {
            titlePaint.color = titleColor
            c.drawText(title, w / 2f, 30f * d, titleStroke)
            c.drawText(title, w / 2f, 30f * d, titlePaint)
        }
        if (sub.isNotEmpty()) c.drawText(sub, w / 2f, 49f * d, subPaint)
        when (g.phase) {
            Phase.READY, Phase.MASH, Phase.GAUGE, Phase.SHOW -> drawPrep(c, g)
            else -> drawBattle(c, g)
        }
    }

    private fun drawPrep(c: Canvas, g: SpinTopGame) {
        val who = g.preparer
        val mashing = g.phase == Phase.READY || g.phase == Phase.MASH
        val col = colors[who]
        // ── 줄 감기 버튼 ──
        val since = now - pressAt
        val pressed = mashing && since >= 0L && since < PRESS_MS
        val r = if (pressed) btnR * 0.93f else btnR
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawCircle(btnX, btnY + 4f * d, btnR, p)
        p.color = if (mashing) Theme.shade(col, 0.6f) else 0xFF3A3F5C.toInt()
        c.drawCircle(btnX, btnY, r, p)
        p.color = if (mashing) col else 0xFF545A7A.toInt()
        c.drawCircle(btnX, btnY - 3f * d, r * 0.9f, p)
        p.color = 0x33FFFFFF
        c.drawCircle(btnX - r * 0.2f, btnY - r * 0.3f, r * 0.45f, p)
        // 감긴 줄 (연타할수록 많이 감김)
        val turns = 0.6f + 3.2f * (g.taps[who].toFloat() / GameConfig.MASH_MAX_TAPS).coerceAtMost(1f)
        SpinArt.coil(c, p, btnX, btnY + r * 0.38f, r * 0.55f, turns, 3f * d)
        btnText.color = if (mashing) Theme.textOn(col) else Theme.TEXT_SUB
        c.drawText(BTN_LABEL, btnX, btnY - r * 0.08f, btnText)
        line.strokeWidth = 3f * d
        line.color = if (mashing) Theme.GOLD else 0x66FFFFFF
        c.drawCircle(btnX, btnY, r, line)
        // 연타 수 (버튼 오른쪽 위 꼬리표)
        val taps = g.taps[who].coerceIn(0, GameConfig.MASH_MAX_TAPS)
        val tx = btnX + btnR * 0.95f
        val ty = btnY - btnR * 0.8f
        p.color = Theme.GOLD
        tmp.set(tx - 20f * d, ty - 11f * d, tx + 20f * d, ty + 11f * d)
        c.drawRoundRect(tmp, 11f * d, 11f * d, p)
        small.color = Theme.INK
        small.textAlign = Paint.Align.CENTER
        c.drawText(TAPS[taps], tx, ty + 4.5f * d, small)
        small.textAlign = Paint.Align.LEFT
        small.color = Theme.TEXT
        // ── 5초 시간 막대 ──
        val left = when {
            g.phase == Phase.READY -> GameConfig.MASH_MS
            g.phase == Phase.MASH && mashStart >= 0L -> (GameConfig.MASH_MS - (now - mashStart)).coerceIn(0L, GameConfig.MASH_MS)
            else -> 0L
        }
        val f = left.toFloat() / GameConfig.MASH_MS
        p.color = 0xAA1A1030.toInt()
        c.drawRoundRect(timeBar, 4.5f * d, 4.5f * d, p)
        if (f > 0f) {
            p.color = if (f < 0.3f) Theme.RED else Theme.GREEN
            tmp.set(timeBar.left, timeBar.top, timeBar.left + timeBar.width() * f, timeBar.bottom)
            c.drawRoundRect(tmp, 4.5f * d, 4.5f * d, p)
        }
        // ── 던지기 게이지 ──
        val gv = when (g.phase) {
            Phase.GAUGE -> g.gauge.valueAt(now)
            Phase.SHOW -> g.gaugeValues[who]
            else -> 0f
        }
        gauge.draw(c, gv, active = g.phase == Phase.GAUGE && g.gauge.running,
            showNeedle = g.phase == Phase.GAUGE || g.phase == Phase.SHOW)
        // ── 회전력·안정성 막대 ──
        val power = if (g.phase == Phase.MASH) SpinRules.spinPower(g.taps[who]) else g.powers[who]
        val stab = if (g.phase == Phase.SHOW) g.stabilities[who] else 0f
        bar(c, powerBar, POWER_LABEL, power, if (g.phase == Phase.READY) 0f else power, Theme.RED)
        bar(c, stabBar, STAB_LABEL, stab, stab, Theme.BLUE)
    }

    private fun bar(c: Canvas, r: RectF, name: String, v: Float, shown: Float, color: Int) {
        c.drawText(name, r.left - 46f * d, r.bottom, small)
        p.color = 0xAA1A1030.toInt()
        val rr = r.height() / 2f
        c.drawRoundRect(r, rr, rr, p)
        if (shown > 0f) {
            p.color = color
            tmp.set(r.left, r.top, r.left + r.width() * shown.coerceIn(0f, 1f), r.bottom)
            c.drawRoundRect(tmp, rr, rr, p)
        }
        line.strokeWidth = 1f * d
        line.color = 0x66FFFFFF
        c.drawRoundRect(r, rr, rr, line)
        c.drawText(PCT[(v * 100f + 0.5f).toInt().coerceIn(0, 100)], width - 12f * d, r.bottom, smallRight)
    }

    private fun drawBattle(c: Canvas, g: SpinTopGame) {
        val w = width.toFloat()
        for (i in 0 until g.players) {
            val t = g.tops[i]
            val top = rowTop + rowH * i
            val mid = top + rowH / 2f
            // 줄 배경
            p.color = if (t.fallen) 0x33000000 else 0x3322337A
            tmp.set(8f * d, top + 3f * d, w - 8f * d, top + rowH - 3f * d)
            c.drawRoundRect(tmp, 10f * d, 10f * d, p)
            val ir = minOf(rowH * 0.3f, 15f * d)
            val ix = 14f * d + ir
            if (t.fallen) SpinArt.fallen(c, p, ix, mid, ir, colors[i], 20f)
            else SpinArt.top(c, p, ix, mid, ir, colors[i], t.angle, blur = t.power)
            // 이름 + 상태
            val tx = ix + ir + 9f * d
            small.color = if (t.fallen) Theme.TEXT_SUB else Theme.TEXT
            c.drawText(names[i], tx, mid - 4f * d, small)
            if (status[i].isNotEmpty()) c.drawText(status[i], w - 14f * d, mid - 4f * d, smallRight)
            // 회전력 막대
            val bl = tx
            val br = w - 16f * d
            tmp.set(bl, mid + 3f * d, br, mid + 11f * d)
            p.color = 0xAA1A1030.toInt()
            c.drawRoundRect(tmp, 4f * d, 4f * d, p)
            val v = if (t.launched) t.power else g.powers[i]
            if (v > 0f) {
                p.color = if (v < GameConfig.WOBBLE_LEVEL) Theme.RED else colors[i]
                tmp.set(bl, mid + 3f * d, bl + (br - bl) * v.coerceIn(0f, 1f), mid + 11f * d)
                c.drawRoundRect(tmp, 4f * d, 4f * d, p)
            }
        }
        small.color = Theme.TEXT
    }

    companion object {
        private const val PRESS_MS = 90L
        private const val BTN_LABEL = "줄 감기!"
        private const val GAUGE_LABEL = "② 던지기 — 가운데서 터치!"
        private const val POWER_LABEL = "회전력"
        private const val STAB_LABEL = "안정성"
        private val TAPS = Array(GameConfig.MASH_MAX_TAPS + 1) { it.toString() + "회" }
        private val PCT = Array(101) { it.toString() + "%" }
    }
}
