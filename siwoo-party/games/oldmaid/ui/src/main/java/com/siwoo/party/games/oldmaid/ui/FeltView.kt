package com.siwoo.party.games.oldmaid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 도둑잡기 카드 테이블 그림. 두 겹으로 쓴다:
 * - labels = false (CardTableView 아래): 초록 펠트 + 나무·금색 테두리 + 버린 더미 자리(점선)
 * - labels = true (CardTableView 위, 터치 안 받음): "버린 짝 N쌍", 자리 이름표(색 동그라미 + 이름 + 장수), 안내 한 줄
 *   → 날아가는 카드가 이름표·안내를 가리지 않는다
 * 글자·크기는 setter 에서만 만든다 → onDraw 할당 없음. 바뀔 때만 다시 그린다.
 */
class FeltView(context: Context, private val labels: Boolean) : View(context) {

    private val density = resources.displayMetrics.density
    private val felt = RectF()
    private val tmp = RectF()
    private var feltShader: Shader? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x88FFE08A.toInt()
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT; textSize = 13f * density }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD; textSize = 12f * density }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; textSize = 11f * density }
    private val pilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xCCFFFFFF.toInt(); textSize = 11.5f * density }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT; textSize = 15f * density }
    private val hintSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xDDFFFFFF.toInt(); textSize = 12f * density }

    // ── 이름표 ──
    private class Plate {
        var on = false
        var x = 0f
        var y = 0f
        var name = ""
        var initial = ""
        var count = ""
        var color = 0
        var nameW = 0f
        var countW = 0f
        var current = false
    }

    private val plates = Array(4) { Plate() }

    // ── 버린 더미 ──
    private var pileOn = false
    private var pileX = 0f
    private var pileY = 0f
    private var pileW = 0f
    private var pileH = 0f
    private var pileText = ""

    // ── 안내 ──
    private var hint = ""
    private var hintSub = ""
    private var hintX = 0f
    private var hintY = 0f
    private var hintMaxW = 0f

    /** 이름표 i 를 (x, y) 가운데에 (count = "13장" 등) */
    fun setPlate(i: Int, x: Float, y: Float, name: String, color: Int, count: String, current: Boolean = false) {
        val p = plates[i]
        p.on = true
        p.x = x; p.y = y
        p.name = name
        p.initial = name.take(1)
        p.count = count
        p.color = color
        p.current = current
        p.nameW = namePaint.measureText(name)
        p.countW = if (count.isEmpty()) 0f else countPaint.measureText(count)
        invalidate()
    }

    /** 이름표 장수 글자만 바꿈 */
    fun setPlateCount(i: Int, count: String) {
        val p = plates[i]
        if (p.count == count) return
        p.count = count
        p.countW = if (count.isEmpty()) 0f else countPaint.measureText(count)
        invalidate()
    }

    fun hidePlates() {
        for (p in plates) p.on = false
        invalidate()
    }

    /** 이름표 폭 (자리 계산용) */
    fun plateWidth(name: String, count: String): Float =
        PLATE_H_DP * density + 10f * density + namePaint.measureText(name) + 8f * density + countPaint.measureText(count) + 12f * density

    /** 버린 더미 자리 (카드 크기 w×h) */
    fun setPile(x: Float, y: Float, w: Float, h: Float) {
        pileOn = true
        pileX = x; pileY = y; pileW = w; pileH = h
        invalidate()
    }

    fun setPileText(text: String) {
        if (pileText == text) return
        pileText = text
        invalidate()
    }

    /** 안내 (x, y = 첫 줄 가운데 기준선 근처), maxW 안에 맞게 글자 크기 조절 */
    fun setHint(text: String, sub: String, x: Float, y: Float, maxW: Float) {
        hint = text
        hintSub = sub
        hintX = x; hintY = y; hintMaxW = maxW
        fitHint()
        invalidate()
    }

    fun setHintText(text: String, sub: String) {
        if (hint == text && hintSub == sub) return
        hint = text
        hintSub = sub
        fitHint()
        invalidate()
    }

    val hintText: String get() = hint

    private fun fitHint() {
        hintPaint.textSize = 15f * density
        while (hint.isNotEmpty() && hintMaxW > 0f && hintPaint.measureText(hint) > hintMaxW && hintPaint.textSize > 9f * density) hintPaint.textSize *= 0.92f
        hintSubPaint.textSize = 12f * density
        while (hintSub.isNotEmpty() && hintMaxW > 0f && hintSubPaint.measureText(hintSub) > hintMaxW && hintSubPaint.textSize > 8f * density) hintSubPaint.textSize *= 0.92f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = 3f * density
        felt.set(inset, inset, w - inset, h - inset)
        feltShader = RadialGradient(w / 2f, h * 0.45f, maxOf(w, h) * 0.62f,
            intArrayOf(FELT_LIGHT, FELT, FELT_DARK), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        if (felt.width() <= 0f) return
        if (labels) drawLabels(c) else drawTable(c)
    }

    private fun drawTable(c: Canvas) {
        val r = 24f * density
        // 그림자 + 나무 테두리 + 펠트
        fill.shader = null
        fill.color = 0x66000000
        c.drawRoundRect(felt.left + 2f * density, felt.top + 4f * density, felt.right + 2f * density, felt.bottom + 4f * density, r, r, fill)
        fill.color = RIM
        c.drawRoundRect(felt, r, r, fill)
        val rim = 5f * density
        tmp.set(felt.left + rim, felt.top + rim, felt.right - rim, felt.bottom - rim)
        fill.shader = feltShader
        c.drawRoundRect(tmp, r - rim, r - rim, fill)
        fill.shader = null
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 1.6f * density
        c.drawRoundRect(tmp, r - rim, r - rim, stroke)
        stroke.color = 0x33FFE08A
        stroke.strokeWidth = 1f * density
        val in2 = 7f * density
        tmp.inset(in2, in2)
        c.drawRoundRect(tmp, r - rim - in2 * 0.6f, r - rim - in2 * 0.6f, stroke)

        if (pileOn) {
            val pw = pileW * 0.5f + 6f * density
            val ph = pileH * 0.5f + 6f * density
            tmp.set(pileX - pw, pileY - ph, pileX + pw, pileY + ph)
            fill.color = 0x22000000
            c.drawRoundRect(tmp, 8f * density, 8f * density, fill)
            c.drawRoundRect(tmp, 8f * density, 8f * density, dash)
        }
    }

    private fun drawLabels(c: Canvas) {
        if (pileOn && pileText.isNotEmpty()) c.drawText(pileText, pileX, pileY + pileH * 0.5f + 21f * density, pilePaint)
        for (p in plates) if (p.on) drawPlate(c, p)

        if (hint.isNotEmpty()) {
            c.drawText(hint, hintX, hintY, hintPaint)
            if (hintSub.isNotEmpty()) c.drawText(hintSub, hintX, hintY + hintSubPaint.textSize * 1.45f, hintSubPaint)
        }
    }

    private fun drawPlate(c: Canvas, p: Plate) {
        val h = PLATE_H_DP * density
        val pad = 8f * density
        val w = h + 2f * density + p.nameW + (if (p.countW > 0f) pad + p.countW else 0f) + pad * 1.5f
        tmp.set(p.x - w / 2f, p.y - h / 2f, p.x + w / 2f, p.y + h / 2f)
        fill.color = 0xCC0B1340.toInt()
        c.drawRoundRect(tmp, h / 2f, h / 2f, fill)
        stroke.color = if (p.current) Theme.GOLD else p.color
        stroke.strokeWidth = (if (p.current) 2.2f else 1.4f) * density
        c.drawRoundRect(tmp, h / 2f, h / 2f, stroke)
        val cr = h * 0.36f
        val cx = tmp.left + h / 2f
        fill.color = p.color
        c.drawCircle(cx, p.y, cr, fill)
        initPaint.color = Theme.textOn(p.color)
        c.drawText(p.initial, cx, p.y + initPaint.textSize * 0.36f, initPaint)
        val tx = cx + cr + 6f * density
        c.drawText(p.name, tx, p.y + namePaint.textSize * 0.36f, namePaint)
        if (p.countW > 0f) c.drawText(p.count, tx + p.nameW + pad, p.y + countPaint.textSize * 0.36f, countPaint)
    }

    companion object {
        const val PLATE_H_DP = 26f
        private const val FELT_LIGHT = 0xFF2A8A6E.toInt()
        private const val FELT = 0xFF17624F.toInt()
        private const val FELT_DARK = 0xFF0C3A31.toInt()
        private const val RIM = 0xFF5A3418.toInt()
    }
}
