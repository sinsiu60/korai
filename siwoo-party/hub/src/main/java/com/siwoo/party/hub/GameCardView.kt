package com.siwoo.party.hub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.IconCache
import com.siwoo.party.common.ui.Theme

/**
 * 게임 카드 (Canvas): 왼쪽 아이콘, 오른쪽 이름·한 줄 소개, 아래 작은 배지
 * [인원] [동시/차례 · 파티 존은 말로 함/몸으로 함] [🎲 랜덤 포함(꺼지면 회색)].
 * 오른쪽 위 상태 배지: 지금 인원으로 못 함(어둡게) / 이어하기 / 기기 조건 부족.
 * 왼쪽 위 모서리 별: 즐겨찾기. 아이콘은 IconCache 비트맵 (스크롤 때 다시 그리지 않음).
 * party = true 면 파티 존 네온 스타일. 허브 목록(RecyclerView)에서는 [bind] 로 다른 게임을 다시 붙인다.
 */
class GameCardView(context: Context, module: GameModule, private val party: Boolean = false) : View(context) {

    var module: GameModule = module
        private set

    /** 즐겨찾기 (왼쪽 위 별) */
    var favorite = false
        set(v) { if (field != v) { field = v; invalidate() } }

    var resumable = false
        set(v) { field = v; fitName(); invalidate() }
    /** 못 하는 이유 (null 이면 가능) */
    var unavailable: String? = null
        set(v) { field = v; alpha = if (v != null) 0.45f else 1f; fitName(); invalidate() }
    /** 기기 조건 경고 (예: "⚠ 동시 터치 부족") */
    var warning: String? = null
        set(v) { field = v; fitName(); invalidate() }
    /** 랜덤에 포함 (주사위 배지 색) */
    var randomOn = true
        set(v) { field = v; invalidate() }

    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = if (party) Theme.Party.TEXT_SUB else Theme.TEXT_SUB }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var tagLayout: StaticLayout? = null
    private var shader: Shader? = null
    private var nameShown: CharSequence = module.name
    private var icon: Bitmap? = null
    private val starPath = android.graphics.Path()

    /** 아래 배지 글자: 인원, 방식(또는 파티 태그), 시간 */
    private var pills: Array<String> = emptyArray()
    private var pillColors = IntArray(0)
    private var pillX = FloatArray(0)
    private var pillW = FloatArray(0)
    /** 주사위 배지와 겹치지 않게 들어가는 배지 수 */
    private var pillCount = 0

    init {
        isClickable = true
        isLongClickable = true
        makePills()
    }

    /** 다른 게임을 붙임 (목록 재사용) — 상태 배지는 부르는 쪽이 다시 정한다 */
    fun bind(m: GameModule) {
        if (m === module) return
        module = m
        nameShown = m.name
        icon = null
        makePills()
        if (width > 0) layoutContent(width, height)
        invalidate()
    }

    private fun makePills() {
        val m = module
        contentDescription = m.name
        pills = buildList {
            add(m.playersLabel)
            if (party && m.partyTags.isNotEmpty()) m.partyTags.forEach { add(it.label) } else add(m.mode.short)
            add(m.minutes)
        }.toTypedArray()
        pillColors = IntArray(pills.size) { k ->
            when {
                k == 0 -> 0x55000000
                k == pills.size - 1 -> 0x33000000
                party -> if (pills[k] == "몸으로 함") 0xAA1E8FB0.toInt() else 0xAAB0287E.toInt()
                else -> 0x55000000
            }
        }
        pillX = FloatArray(pills.size)
        pillW = FloatArray(pills.size)
        pillCount = pills.size
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutContent(w, h)

    private fun layoutContent(w: Int, h: Int) {
        val c = module.color
        shader = if (party) LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Theme.Party.CARD_LIGHT, Theme.Party.BG_BOTTOM, Shader.TileMode.CLAMP)
        else LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Theme.shade(c, 0.75f), Theme.CARD, Shader.TileMode.CLAMP)
        val tx = textLeft(h.toFloat())
        val textW = (w - tx - h * 0.08f).coerceAtLeast(1f)
        namePaint.textSize = h * 0.2f
        tagPaint.textSize = h * 0.125f
        badgePaint.textSize = h * 0.12f
        fitName()
        tagLayout = StaticLayout.Builder.obtain(module.tagline, 0, module.tagline.length, tagPaint, textW.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        // 배지 위치 (글자 폭은 크기가 바뀔 때만 잰다)
        var x = tx
        val gap = h * 0.05f
        val limit = w - h * 0.19f * 1.05f - h * 0.16f
        pillCount = 0
        for (k in pills.indices) {
            pillW[k] = badgePaint.measureText(pills[k]) + h * 0.14f
            pillX[k] = x
            x += pillW[k] + gap
            if (pillX[k] + pillW[k] <= limit) pillCount = k + 1 else break
        }
        icon = IconCache.get(module, (h * 0.7f * 0.9f).toInt())
        // 즐겨찾기 별 (왼쪽 위 모서리)
        starPath.reset()
        val sr = h * 0.11f
        val scx = h * 0.12f
        val scy = h * 0.12f
        for (k in 0 until 10) {
            val a = Math.PI * (k * 36.0 - 90.0) / 180.0
            val rr = if (k % 2 == 0) sr else sr * 0.45f
            val x = scx + (rr * Math.cos(a)).toFloat()
            val y = scy + (rr * Math.sin(a)).toFloat()
            if (k == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
    }

    private fun textLeft(h: Float) = h * 0.9f

    /** 지금 상태 배지 글자 (없으면 null) */
    private fun statusBadge(): String? = when {
        unavailable != null -> unavailable
        resumable -> "이어하기"
        warning != null -> warning
        else -> null
    }

    /** 이름이 오른쪽 위 상태 배지와 겹치지 않게 줄임 (크기·배지가 바뀔 때만) */
    private fun fitName() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val badge = statusBadge()
        val badgeW = if (badge != null) badgePaint.measureText(badge) + h * 0.14f + h * 0.3f else 0f
        val avail = (w - textLeft(h) - h * 0.08f - badgeW).coerceAtLeast(h * 0.5f)
        nameShown = TextUtils.ellipsize(module.name, namePaint, avail, TextUtils.TruncateAt.END)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h * 0.14f
        val sw = Theme.dpf(context, 2f)
        rect.set(sw, sw, w - sw, h - sw)
        bg.shader = shader
        c.drawRoundRect(rect, r, r, bg)
        bg.shader = null
        stroke.strokeWidth = sw
        stroke.color = when {
            resumable -> Theme.GOLD
            party -> Theme.Party.NEON_PINK
            else -> 0x44FFFFFF
        }
        c.drawRoundRect(rect, r, r, stroke)
        // 아이콘 판
        val isz = h * 0.7f
        val icx = h * 0.1f + isz / 2f
        val icy = h / 2f
        bg.color = 0x33000000
        c.drawRoundRect(icx - isz / 2f, icy - isz / 2f, icx + isz / 2f, icy + isz / 2f, r, r, bg)
        icon?.let { c.drawBitmap(it, icx - it.width / 2f, icy - it.height / 2f, iconPaint) }
        if (favorite) {
            bg.color = Theme.GOLD
            c.drawPath(starPath, bg)
            stroke.color = Theme.INK
            stroke.strokeWidth = sw * 0.6f
            c.drawPath(starPath, stroke)
        }

        val tx = textLeft(h)
        c.drawText(nameShown, 0, nameShown.length, tx, h * 0.28f, namePaint)
        tagLayout?.let {
            c.save()
            c.translate(tx, h * 0.35f)
            it.draw(c)
            c.restore()
        }
        // 아래 배지
        val bh = h * 0.19f
        val by = h - bh - h * 0.08f
        for (k in 0 until pillCount) {
            bg.color = pillColors[k]
            c.drawRoundRect(pillX[k], by, pillX[k] + pillW[k], by + bh, bh / 2f, bh / 2f, bg)
            badgePaint.color = if (k == pills.size - 1) Theme.TEXT_SUB else Theme.GOLD_LIGHT
            c.drawText(pills[k], pillX[k] + pillW[k] / 2f, by + bh * 0.74f, badgePaint)
        }
        // 랜덤 주사위 (오른쪽 아래)
        val ds = bh * 1.05f
        val dx = w - ds - h * 0.1f
        val dy = by + (bh - ds) / 2f
        drawDie(c, dx, dy, ds, randomOn)
        // 상태 배지 (오른쪽 위)
        val badge = statusBadge()
        if (badge != null) {
            val bw = badgePaint.measureText(badge) + h * 0.14f
            val sh = h * 0.2f
            val bx = w - bw - h * 0.06f
            val sy = h * 0.06f
            bg.color = when {
                unavailable != null -> Theme.RED_DARK
                resumable -> Theme.GOLD
                else -> 0xFFE67E22.toInt()
            }
            c.drawRoundRect(bx, sy, bx + bw, sy + sh, sh / 2f, sh / 2f, bg)
            badgePaint.color = if (resumable && unavailable == null) Theme.INK else Theme.TEXT
            c.drawText(badge, bx + bw / 2f, sy + sh * 0.72f, badgePaint)
        }
    }

    /** 랜덤 포함 표시 주사위 (켬: 금색, 끔: 회색) */
    private fun drawDie(c: Canvas, x: Float, y: Float, s: Float, on: Boolean) {
        bg.color = if (on) Theme.GOLD else 0xFF59607E.toInt()
        c.drawRoundRect(x, y, x + s, y + s, s * 0.22f, s * 0.22f, bg)
        bg.color = if (on) Theme.INK else 0xFF8A90AA.toInt()
        val pr = s * 0.09f
        c.drawCircle(x + s * 0.3f, y + s * 0.3f, pr, bg)
        c.drawCircle(x + s * 0.5f, y + s * 0.5f, pr, bg)
        c.drawCircle(x + s * 0.7f, y + s * 0.7f, pr, bg)
        if (!on) {
            stroke.color = 0xFFB0B4C8.toInt()
            stroke.strokeWidth = s * 0.08f
            c.drawLine(x, y + s, x + s, y, stroke)
        }
    }
}
