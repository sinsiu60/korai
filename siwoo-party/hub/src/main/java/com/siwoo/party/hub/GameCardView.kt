package com.siwoo.party.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.Theme

/**
 * 허브 게임 카드 (Canvas): 왼쪽 아이콘, 오른쪽 이름·소개·인원/방식/시간.
 * 이어하기 배지, 동시 터치 경고, 지금 인원으로 못 하면 어둡게 + 이유.
 * module 이 null 이면 "곧 추가될 게임" 자리 카드.
 */
class GameCardView(context: Context, val module: GameModule?) : View(context) {

    var resumable = false
        set(v) { field = v; invalidate() }
    /** 못 하는 이유 (null 이면 가능) */
    var unavailable: String? = null
        set(v) { field = v; alpha = if (v != null) 0.45f else 1f; invalidate() }
    var touchWarning = false
        set(v) { field = v; invalidate() }

    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD_LIGHT }
    private val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var tagLayout: StaticLayout? = null
    private var shader: Shader? = null
    private val name = module?.name ?: "곧 추가될 게임"
    private val info = module?.let {
        val n = if (it.playerCounts.first == it.playerCounts.last) "${it.playerCounts.first}인" else "${it.playerCounts.first}~${it.playerCounts.last}인"
        "$n · ${it.mode.label} · ${it.minutes}"
    } ?: "반응 속도 · 줄다리기 · 폭탄 돌리기 …"

    init {
        isClickable = true
        contentDescription = name
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val c = module?.color ?: Theme.GRAY
        shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Theme.shade(c, 0.75f), Theme.CARD, Shader.TileMode.CLAMP)
        namePaint.textSize = h * 0.17f
        infoPaint.textSize = h * 0.1f
        tagPaint.textSize = h * 0.105f
        badgePaint.textSize = h * 0.095f
        val textW = (w - h * 0.95f - h * 0.08f).toInt()
        val tag = module?.tagline ?: "다음 업데이트에서 하나씩 추가됩니다"
        @Suppress("DEPRECATION")
        tagLayout = if (textW > 0) StaticLayout(tag, tagPaint, textW, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false) else null
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h * 0.12f
        val sw = Theme.dpf(context, 2f)
        rect.set(sw, sw, w - sw, h - sw)
        bg.shader = shader
        c.drawRoundRect(rect, r, r, bg)
        bg.shader = null
        stroke.strokeWidth = sw
        stroke.color = if (resumable) Theme.GOLD else 0x44FFFFFF
        c.drawRoundRect(rect, r, r, stroke)
        // 아이콘 판
        val isz = h * 0.72f
        val icx = h * 0.14f + isz / 2f
        val icy = h / 2f
        bg.color = 0x33000000
        c.drawRoundRect(icx - isz / 2f, icy - isz / 2f, icx + isz / 2f, icy + isz / 2f, r, r, bg)
        if (module != null) {
            iconPaint.reset()
            iconPaint.isAntiAlias = true
            module.drawIcon(c, icx, icy, isz * 0.9f, iconPaint)
        } else {
            badgePaint.color = Theme.TEXT_SUB
            badgePaint.textSize = isz * 0.5f
            c.drawText("?", icx, icy + isz * 0.18f, badgePaint)
            badgePaint.textSize = h * 0.095f
        }
        val tx = h * 0.95f
        c.drawText(name, tx, h * 0.3f, namePaint)
        tagLayout?.let {
            c.save()
            c.translate(tx, h * 0.38f)
            it.draw(c)
            c.restore()
        }
        c.drawText(info, tx, h * 0.88f, infoPaint)
        // 배지
        val badge = when {
            unavailable != null -> unavailable
            resumable -> "이어하기"
            touchWarning -> "⚠ 동시 터치 부족"
            else -> null
        }
        if (badge != null) {
            val bw = badgePaint.measureText(badge) + h * 0.12f
            val bh = h * 0.17f
            val bx = w - bw - h * 0.06f
            val by = h * 0.06f
            bg.color = when {
                unavailable != null -> Theme.RED_DARK
                resumable -> Theme.GOLD
                else -> 0xFFE67E22.toInt()
            }
            c.drawRoundRect(bx, by, bx + bw, by + bh, bh / 2f, bh / 2f, bg)
            badgePaint.color = if (resumable && unavailable == null) Theme.INK else Theme.TEXT
            c.drawText(badge, bx + bw / 2f, by + bh * 0.72f, badgePaint)
        }
    }
}
