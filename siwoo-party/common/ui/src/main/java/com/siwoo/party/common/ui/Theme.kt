package com.siwoo.party.common.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 색상 팔레트와 공통 그리기 도우미.
 * 테마: 짙은 남색 그라데이션 배경 + 금색 포인트, 둥근 카드(16dp) + 그림자.
 * 모든 그래픽은 코드(Drawable/Canvas)로 만든다 — 이미지 파일 없음.
 */
object Theme {
    // ── 배경 ──
    const val BG_TOP = 0xFF1B2A6B.toInt()
    const val BG_BOTTOM = 0xFF0B1340.toInt()
    const val BG_DEEP = 0xFF070D2E.toInt()
    const val DIM = 0xB3000814.toInt()

    // ── 카드 ──
    const val CARD = 0xFF22337A.toInt()
    const val CARD_LIGHT = 0xFF2E4396.toInt()
    const val CARD_BORDER = 0x33FFFFFF
    const val PAPER = 0xFFFFF8E7.toInt()     // 밝은 카드(구매 카드 등)
    const val INK = 0xFF1A2040.toInt()        // 밝은 카드 위 글씨

    // ── 포인트 ──
    const val GOLD = 0xFFFFC93C.toInt()
    const val GOLD_LIGHT = 0xFFFFE08A.toInt()
    const val GOLD_DARK = 0xFFB8860B.toInt()
    const val RED = 0xFFFF4D5E.toInt()
    const val RED_DARK = 0xFFB71C2C.toInt()
    const val GREEN = 0xFF2ECC71.toInt()
    const val GREEN_DARK = 0xFF168F4A.toInt()
    const val BLUE = 0xFF3D7BFF.toInt()
    const val GRAY = 0xFF6B7499.toInt()

    // ── 글씨 ──
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_SUB = 0xFFB8C3F0.toInt()

    // ── 플레이어 색 (겹치지 않게 고름) ──
    val PLAYER_COLORS = intArrayOf(
        0xFFFF5252.toInt(), // 빨강
        0xFF448AFF.toInt(), // 파랑
        0xFF00D27A.toInt(), // 초록
        0xFFFFD600.toInt(), // 노랑
        0xFFB46CFF.toInt(), // 보라
        0xFFFF9F1C.toInt(), // 주황
    )
    val PLAYER_COLOR_NAMES = arrayOf("빨강", "파랑", "초록", "노랑", "보라", "주황")

    fun playerColor(colorIndex: Int): Int = PLAYER_COLORS[colorIndex % PLAYER_COLORS.size]

    // ── 크기 ──
    fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    fun dpf(ctx: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)

    // ── Drawable ──
    fun background(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOTTOM))

    /** 둥근 카드 (그림자는 View.elevation 으로) */
    fun card(ctx: Context, color: Int = CARD, radiusDp: Float = 16f, stroke: Int = CARD_BORDER, strokeDp: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(ctx, radiusDp)
            if (strokeDp > 0f) setStroke(dp(ctx, strokeDp).coerceAtLeast(1), stroke)
        }

    fun verticalCard(ctx: Context, top: Int, bottom: Int, radiusDp: Float = 16f): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = dpf(ctx, radiusDp)
            setStroke(dp(ctx, 1f).coerceAtLeast(1), CARD_BORDER)
        }

    fun oval(color: Int, strokeColor: Int = Color.WHITE, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** 색을 밝게/어둡게 (f > 1 밝게) */
    fun shade(c: Int, f: Float): Int {
        fun ch(v: Int) = if (f >= 1f) (v + (255 - v) * (f - 1f)).toInt().coerceIn(0, 255) else (v * f).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(c), ch(Color.red(c)), ch(Color.green(c)), ch(Color.blue(c)))
    }

    /** 밝은 색 위에서는 어두운 글씨, 어두운 색 위에서는 흰 글씨 */
    fun textOn(color: Int): Int {
        val l = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        return if (l > 165) INK else Color.WHITE
    }

    // ── 위젯 ──
    fun text(ctx: Context, s: CharSequence, sp: Float, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }

    fun vertical(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun horizontal(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight)

    const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    fun colorDot(ctx: Context, color: Int, sizeDp: Float): View = View(ctx).apply {
        background = oval(color, Color.WHITE, dp(ctx, 2f))
        layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
    }
}
