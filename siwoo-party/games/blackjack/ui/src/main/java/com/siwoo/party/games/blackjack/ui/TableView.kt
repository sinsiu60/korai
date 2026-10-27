package com.siwoo.party.games.blackjack.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.blackjack.GameConfig

/**
 * 테이블 바닥 층 (카드 아래): 초록 펠트·딜러 판·카드 통·라운드 표시·가운데 띠 글자·플레이어 자리
 * (이름·보유 칩·합계 배지·베팅 칩 더미). 카드는 위에 겹친 CardTableView 가, 결과 도장은 MarksView 가 그린다.
 *
 * - 글자는 setter 에서만 만든다 (onDraw 할당 없음). 칩이 떨어져 쌓이는 동안만 프레임이 돈다.
 * - 자리 계산은 [TableGeom] (Activity 가 크기 바뀔 때 update 후 [onGeom]).
 */
class TableView(context: Context, private val geom: TableGeom) : View(context) {

    companion object {
        const val KIND_NORMAL = 0
        const val KIND_GOLD = 1
        const val KIND_BUST = 2
        const val KIND_HIDDEN = 3

        private const val MAX = GameConfig.MAX_PLAYERS
        private const val FELT_IN = 0xFF27A066.toInt()
        private const val FELT_OUT = 0xFF0D5532.toInt()
        private const val RAIL = 0xFF4A2810.toInt()
        private const val WOOD_TOP = 0xFF7A3E1E.toInt()
        private const val WOOD_BOTTOM = 0xFF4A2210.toInt()
        private const val PLATE = 0xE60B1640.toInt()
        private const val SEAT = 0xE61B2A6E.toInt()
        private const val SEAT_ON = 0xF02A3F94.toInt()
        private const val SEAT_OFF = 0xCC111A48.toInt()
        private const val LABEL_BET = "베팅"
        private const val LABEL_DEALER = "딜러"
    }

    private val d = Theme.dpf(context, 1f)

    // ── 상태 (setter 로만 바뀜) ──
    private var n = 2
    private val names = Array(MAX) { "" }
    private val initials = Array(MAX) { "" }
    private val colors = IntArray(MAX) { Theme.GOLD }
    private val nameSize = FloatArray(MAX)
    private val bankText = Array(MAX) { "" }
    private val bankWidth = FloatArray(MAX)
    private val totalText = Array(MAX) { "" }
    private val totalKind = IntArray(MAX)
    private val totalWidth = FloatArray(MAX)
    private val betText = Array(MAX) { "" }
    private val spectator = BooleanArray(MAX)
    private var current = -1
    private var dealerActive = false
    private var dealerText = ""
    private var dealerKind = KIND_NORMAL
    private var dealerWidth = 0f
    private var roundText = ""
    private var shoeText = ""
    private var bandName = ""
    private var bandSuffix = ""
    private var bandSub = ""
    private var bandColor = Theme.GOLD
    private var bandNameW = 0f

    // 칩 더미: 액면별 개수 + 떨어지는 연출
    private val chipCounts = Array(MAX) { IntArray(4) }
    private val chipTotal = IntArray(MAX)
    private val dropFrom = IntArray(MAX)
    private val dropStart = LongArray(MAX)
    private var dropStagger = 70L
    private var dropMs = 260L
    private var ticking = false
    private val tick = Runnable { onTick() }

    // ── 그리기 도구 ──
    private val feltPath = Path()
    private val railPath = Path()
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f)
        strokeWidth = 1.5f * d
    }
    private val namePaint = text(14f, Theme.TEXT, Paint.Align.LEFT)
    private val initPaint = text(11f, Theme.TEXT, Paint.Align.CENTER)
    private val bankPaint = text(15f, Theme.GOLD, Paint.Align.RIGHT)
    private val badgePaint = text(14f, Theme.TEXT, Paint.Align.CENTER)
    private val betLabelPaint = text(10.5f, Theme.TEXT_SUB, Paint.Align.LEFT)
    private val betPaint = text(16f, Theme.TEXT, Paint.Align.LEFT)
    private val bandNamePaint = text(23f, Theme.GOLD, Paint.Align.LEFT)
    private val bandSuffixPaint = text(18f, Theme.TEXT, Paint.Align.LEFT)
    private val bandSubPaint = text(12.5f, Theme.TEXT_SUB, Paint.Align.LEFT).apply { typeface = Typeface.DEFAULT }
    private val roundPaint = text(13f, Theme.GOLD, Paint.Align.CENTER)
    private val shoePaint = text(11f, Theme.GOLD_LIGHT, Paint.Align.CENTER)
    private val dealerPaint = text(16f, Theme.GOLD, Paint.Align.CENTER)
    private val emptyPaint = text(11f, 0x66FFFFFF, Paint.Align.CENTER).apply { typeface = Typeface.DEFAULT }

    private fun text(sp: Float, color: Int, align: Paint.Align): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp * d * resources.configuration.fontScale.coerceIn(0.85f, 1.15f)
        this.color = color
        textAlign = align
        typeface = Typeface.DEFAULT_BOLD
    }

    // ── setter ──

    fun setPlayers(list: List<String>, playerColors: IntArray) {
        n = list.size
        for (i in 0 until n) {
            names[i] = list[i]
            initials[i] = list[i].take(1)
            colors[i] = playerColors[i]
        }
        fitNames()
        invalidate()
    }

    /** 크기·자리 계산이 바뀐 뒤 (Activity 가 geom.update 후) */
    fun onGeom() {
        val g = geom
        if (g.w <= 0f) return
        // 펠트: 위는 화면 밖, 아래는 가운데가 처진 곡선
        feltPath.reset()
        feltPath.moveTo(0f, 0f)
        feltPath.lineTo(g.w, 0f)
        feltPath.lineTo(g.w, g.feltEdgeY)
        feltPath.quadTo(g.w / 2f, 2f * g.feltMidY - g.feltEdgeY, 0f, g.feltEdgeY)
        feltPath.close()
        railPath.reset()
        railPath.moveTo(0f, g.feltEdgeY)
        railPath.quadTo(g.w / 2f, 2f * g.feltMidY - g.feltEdgeY, g.w, g.feltEdgeY)
        feltPaint.shader = RadialGradient(g.w / 2f, g.feltMidY * 0.35f, g.w * 0.6f, FELT_IN, FELT_OUT, Shader.TileMode.CLAMP)
        woodPaint.shader = LinearGradient(0f, g.shoeBox.top, 0f, g.shoeBox.bottom, WOOD_TOP, WOOD_BOTTOM, Shader.TileMode.CLAMP)
        fitNames()
        measureBand()
        invalidate()
    }

    fun setRound(text: String) { roundText = text; invalidate() }
    fun setShoe(text: String) { shoeText = text; invalidate() }

    fun setCurrent(i: Int) { if (current != i) { current = i; invalidate() } }
    fun setDealerActive(on: Boolean) { if (dealerActive != on) { dealerActive = on; invalidate() } }
    fun setSpectator(i: Int, on: Boolean) { spectator[i] = on; invalidate() }

    fun setBank(i: Int, text: String) {
        bankText[i] = text
        fitNames()
        invalidate()
    }

    /** 합계 배지 ("" 이면 숨기고 빈 카드 자리 표시) */
    fun setTotal(i: Int, text: String, kind: Int) {
        totalText[i] = text
        totalKind[i] = kind
        totalWidth[i] = if (text.isEmpty()) 0f else badgePaint.measureText(text) + 22f * d
        invalidate()
    }

    fun setDealerTotal(text: String, kind: Int) {
        dealerText = text
        dealerKind = kind
        dealerWidth = if (text.isEmpty()) 0f else badgePaint.measureText(text) + 22f * d
        invalidate()
    }

    /** 가운데 띠: name(색) + suffix, 아래 작은 sub */
    fun setBand(name: String, color: Int, suffix: String, sub: String) {
        bandName = name
        bandColor = color
        bandSuffix = suffix
        bandSub = sub
        measureBand()
        invalidate()
    }

    /** 칩이 떨어지는 연출 시간 (속도 배율 적용된 값) */
    fun setDropTiming(staggerMs: Long, chipMs: Long) {
        dropStagger = staggerMs.coerceAtLeast(0L)
        dropMs = chipMs.coerceAtLeast(1L)
    }

    /**
     * 베팅 칩 더미 = amount (0 이면 비움), label = 옆 글자. animate 면 원래 있던 개수 뒤의 칩만 위에서 떨어진다.
     */
    fun setBet(i: Int, amount: Int, label: String, animate: Boolean) {
        val before = chipTotal[i]
        val c = chipCounts[i]
        ChipArt.breakdown(amount, c)
        var t = 0
        for (k in 0 until 4) t += c[k]
        chipTotal[i] = t
        betText[i] = label
        if (animate && t > 0) {
            dropFrom[i] = if (amount > 0) minOf(before, t) else 0
            dropStart[i] = SystemClock.uptimeMillis()
            startTick()
        } else {
            dropFrom[i] = t
        }
        invalidate()
    }

    /** 칩 더미 i 의 맨 위 근처 (연출 출발·도착점) */
    fun betTop(i: Int): Float = geom.betBaseY - geom.chipRx * 0.4f - geom.chipTh * 3f

    /** 보유 칩 글자 근처 x */
    fun bankX(i: Int): Float = geom.seats[i].right - 12f * d - bankWidth[i] / 2f

    val isDropping: Boolean get() = ticking

    private fun fitNames() {
        val g = geom
        if (g.w <= 0f) return
        val base = 14f * d * resources.configuration.fontScale.coerceIn(0.85f, 1.15f)
        for (i in 0 until n) {
            bankPaint.textSize = 15f * d
            bankWidth[i] = bankPaint.measureText(bankText[i])
            val max = g.seats[i].width() - 34f * d - bankWidth[i] - 26f * d
            var s = base
            namePaint.textSize = s
            while (namePaint.measureText(names[i]) > max && s > base * 0.6f) {
                s *= 0.92f
                namePaint.textSize = s
            }
            nameSize[i] = s
        }
    }

    private fun measureBand() {
        val g = geom
        if (g.w <= 0f) return
        val max = g.labelRight - g.labelLeft
        var s = 23f * d
        bandNamePaint.textSize = s
        bandSuffixPaint.textSize = s * 0.78f
        while (bandNamePaint.measureText(bandName) + bandSuffixPaint.measureText(bandSuffix) > max && s > 12f * d) {
            s *= 0.92f
            bandNamePaint.textSize = s
            bandSuffixPaint.textSize = s * 0.78f
        }
        bandNameW = bandNamePaint.measureText(bandName)
    }

    // ── 칩 떨어짐 프레임 ──

    private fun startTick() {
        if (ticking) return
        ticking = true
        postOnAnimation(tick)
    }

    private fun onTick() {
        if (!ticking) return
        invalidate()
        val now = SystemClock.uptimeMillis()
        var busy = false
        for (i in 0 until n) {
            val k = chipTotal[i] - dropFrom[i]
            if (k > 0 && now < dropStart[i] + (k - 1) * dropStagger + dropMs) busy = true
        }
        if (busy) postOnAnimation(tick) else ticking = false
    }

    // ── 그리기 (할당 없음) ──

    override fun onDraw(c: Canvas) {
        val g = geom
        if (g.w <= 0f) return
        drawFelt(c, g)
        drawDealer(c, g)
        drawShoe(c, g)
        drawBand(c, g)
        val now = SystemClock.uptimeMillis()
        for (i in 0 until n) drawSeat(c, g, i, now)
    }

    private fun drawFelt(c: Canvas, g: TableGeom) {
        c.drawPath(feltPath, feltPaint)
        stroke.color = RAIL
        stroke.strokeWidth = 7f * d
        c.drawPath(railPath, stroke)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = 2f * d
        c.save()
        c.translate(0f, -4f * d)
        c.drawPath(railPath, stroke)
        c.restore()
        // 라운드
        fill.color = 0x66000000
        c.drawRoundRect(g.roundPill, 13f * d, 13f * d, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 1.5f * d
        c.drawRoundRect(g.roundPill, 13f * d, 13f * d, stroke)
        c.drawText(roundText, g.roundPill.centerX(), g.roundPill.centerY() + roundPaint.textSize * 0.36f, roundPaint)
    }

    private fun drawDealer(c: Canvas, g: TableGeom) {
        val p = g.dealerPlate
        val r = 12f * d
        if (dealerActive) {
            stroke.color = Theme.GOLD
            stroke.alpha = 80
            stroke.strokeWidth = 8f * d
            c.drawRoundRect(p, r, r, stroke)
            stroke.alpha = 255
        }
        fill.color = PLATE
        c.drawRoundRect(p, r, r, fill)
        stroke.color = if (dealerActive) Theme.GOLD else Theme.GOLD_DARK
        stroke.strokeWidth = (if (dealerActive) 2.5f else 1.5f) * d
        c.drawRoundRect(p, r, r, stroke)
        // 나비넥타이
        val bx = p.centerX()
        val by = p.top + 11f * d
        val bw = 9f * d
        val bh = 5f * d
        fill.color = Theme.RED
        feltTmp.reset()
        feltTmp.moveTo(bx, by)
        feltTmp.lineTo(bx - bw, by - bh)
        feltTmp.lineTo(bx - bw, by + bh)
        feltTmp.close()
        feltTmp.moveTo(bx, by)
        feltTmp.lineTo(bx + bw, by - bh)
        feltTmp.lineTo(bx + bw, by + bh)
        feltTmp.close()
        c.drawPath(feltTmp, fill)
        c.drawCircle(bx, by, 2.4f * d, fill)
        c.drawText(LABEL_DEALER, bx, p.top + 33f * d, dealerPaint)
        if (dealerWidth > 0f) drawBadge(c, bx, p.bottom - 14f * d, dealerWidth, dealerText, dealerKind)
    }

    private val feltTmp = Path()

    private fun drawShoe(c: Canvas, g: TableGeom) {
        val b = g.shoeBox
        val r = 8f * d
        c.drawRoundRect(b, r, r, woodPaint)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = 1.5f * d
        c.drawRoundRect(b, r, r, stroke)
        // 카드가 들어가는 입구
        fill.color = 0x88000000.toInt()
        rect.set(b.left + 10f * d, b.top + 6f * d, b.right - 10f * d, b.top + 12f * d)
        c.drawRoundRect(rect, 3f * d, 3f * d, fill)
        c.drawText(shoeText, b.centerX(), b.bottom - 9f * d, shoePaint)
    }

    private fun drawBand(c: Canvas, g: TableGeom) {
        if (bandName.isEmpty() && bandSuffix.isEmpty()) return
        val base = g.bandCy + (if (bandSub.isEmpty()) bandNamePaint.textSize * 0.35f else -2f * d)
        bandNamePaint.color = bandColor
        c.drawText(bandName, g.labelLeft, base, bandNamePaint)
        c.drawText(bandSuffix, g.labelLeft + bandNameW, base, bandSuffixPaint)
        if (bandSub.isNotEmpty()) c.drawText(bandSub, g.labelLeft, base + 19f * d, bandSubPaint)
    }

    private fun drawSeat(c: Canvas, g: TableGeom, i: Int, now: Long) {
        val s = g.seats[i]
        val r = 14f * d
        val on = i == current
        val off = spectator[i]
        if (on) {
            stroke.color = Theme.GOLD
            stroke.alpha = 70
            stroke.strokeWidth = 10f * d
            c.drawRoundRect(s, r, r, stroke)
            stroke.alpha = 255
        }
        fill.color = if (off) SEAT_OFF else if (on) SEAT_ON else SEAT
        c.drawRoundRect(s, r, r, fill)
        stroke.color = if (on) Theme.GOLD else if (off) 0x22FFFFFF else colors[i] and 0x88FFFFFF.toInt()
        stroke.strokeWidth = (if (on) 2.5f else 1.5f) * d
        c.drawRoundRect(s, r, r, stroke)

        // 머리: 색 동그라미 + 이름 / 보유 칩
        val cy = g.headerCy
        val ccx = s.left + 16f * d
        fill.color = if (off) Theme.GRAY else colors[i]
        c.drawCircle(ccx, cy, 9.5f * d, fill)
        initPaint.color = Theme.textOn(fill.color)
        c.drawText(initials[i], ccx, cy + initPaint.textSize * 0.36f, initPaint)
        namePaint.textSize = nameSize[i]
        namePaint.color = if (off) Theme.GRAY else Theme.TEXT
        c.drawText(names[i], ccx + 15f * d, cy + namePaint.textSize * 0.36f, namePaint)
        val bx = s.right - 10f * d
        bankPaint.color = if (off) Theme.GRAY else Theme.GOLD
        c.drawText(bankText[i], bx, cy + bankPaint.textSize * 0.36f, bankPaint)
        ChipArt.draw(c, bx - bankWidth[i] - 10f * d, cy - 1.5f * d, 7f * d, 2.2f * d, 0, chipPaint)

        // 손패 자리 (카드가 없을 때 점선)
        if (totalText[i].isEmpty() && !off) {
            val cw = g.cardW * g.handScale / 2f
            val ch = g.cardH * g.handScale / 2f
            rect.set(s.centerX() - cw, g.handCy - ch, s.centerX() + cw, g.handCy + ch)
            dash.color = 0x55FFFFFF
            c.drawRoundRect(rect, 6f * d, 6f * d, dash)
        }
        if (totalWidth[i] > 0f) drawBadge(c, s.centerX(), g.badgeCy, totalWidth[i], totalText[i], totalKind[i])

        // 베팅 자리 + 칩 더미
        if (off) return
        val px = g.betX(i)
        val rx = g.chipRx
        rect.set(px - rx - 7f * d, g.betBaseY - rx * 0.4f - 9f * d, px + rx + 7f * d, g.betBaseY + rx * 0.4f + 1f * d)
        dash.color = 0x88FFC93C.toInt()
        c.drawOval(rect, dash)
        val total = chipTotal[i]
        val lx = px + rx + 12f * d
        if (total == 0) {
            c.drawText(LABEL_BET, px, g.betBaseY - 1f * d, emptyPaint)
            return
        }
        val counts = chipCounts[i]
        var cols = 0
        for (k in 0 until 4) if (counts[k] > 0) cols++
        val colGap = 2f * rx + 3f * d
        var col = 0
        var gi = 0
        val top0 = g.betBaseY - rx * 0.4f - g.chipTh
        for (k in 0 until 4) {
            val cnt = counts[k]
            if (cnt == 0) continue
            val x = px + (col - (cols - 1) / 2f) * colGap
            for (j in 0 until cnt) {
                var y = top0 - j * g.chipTh
                if (gi >= dropFrom[i]) {
                    val e = now - dropStart[i] - (gi - dropFrom[i]) * dropStagger
                    if (e < 0) { gi++; continue }
                    if (e < dropMs) {
                        val u = 1f - e.toFloat() / dropMs
                        y -= u * u * 34f * d
                    }
                }
                ChipArt.draw(c, x, y, rx, g.chipTh, k, chipPaint)
                gi++
            }
            col++
        }
        val tx = px + (cols - 1) / 2f * colGap + rx + 9f * d
        c.drawText(LABEL_BET, maxOf(lx, tx), g.betBaseY - 17f * d, betLabelPaint)
        c.drawText(betText[i], maxOf(lx, tx), g.betBaseY + 1f * d, betPaint)
    }

    private fun drawBadge(c: Canvas, cx: Float, cy: Float, w: Float, text: String, kind: Int) {
        val hh = 11f * d
        rect.set(cx - w / 2f, cy - hh, cx + w / 2f, cy + hh)
        fill.color = when (kind) {
            KIND_GOLD -> Theme.GOLD
            KIND_BUST -> Theme.RED_DARK
            KIND_HIDDEN -> 0xFF33406E.toInt()
            else -> 0xFF0B1235.toInt()
        }
        c.drawRoundRect(rect, hh, hh, fill)
        stroke.color = when (kind) {
            KIND_GOLD -> Theme.GOLD_LIGHT
            KIND_BUST -> Theme.RED
            else -> 0x55FFFFFF
        }
        stroke.strokeWidth = 1.2f * d
        c.drawRoundRect(rect, hh, hh, stroke)
        badgePaint.color = if (kind == KIND_GOLD) Theme.INK else Theme.TEXT
        c.drawText(text, cx, cy + badgePaint.textSize * 0.36f, badgePaint)
    }

    override fun onDetachedFromWindow() {
        ticking = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
