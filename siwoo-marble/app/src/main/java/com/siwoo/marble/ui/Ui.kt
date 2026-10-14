package com.siwoo.marble.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 화면 코드에서 공통으로 쓰는 색·크기·위젯 생성 도우미 (XML 레이아웃 없이 코드로 구성) */
object Ui {
    const val BG = 0xFFF4EFE6.toInt()
    const val PANEL = 0xFFFFFFFF.toInt()
    const val DARK = 0xFF263238.toInt()
    const val TEXT = 0xFF212121.toInt()
    const val SUB_TEXT = 0xFF616161.toInt()
    const val GREEN = 0xFF2E7D32.toInt()
    const val BLUE = 0xFF1565C0.toInt()
    const val RED = 0xFFC62828.toInt()
    const val ORANGE = 0xFFEF6C00.toInt()
    const val GRAY = 0xFF78909C.toInt()
    const val GOLD = 0xFFFFB300.toInt()

    fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun oval(color: Int, strokeColor: Int = Color.WHITE, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** 손가락이 큰 사람도 누르기 쉬운 큰 버튼 */
    fun bigButton(ctx: Context, text: String, color: Int, heightDp: Float = 60f, textSp: Float = 20f): Button =
        Button(ctx).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(ctx, heightDp)
            minimumHeight = dp(ctx, heightDp)
            stateListAnimator = null
            background = RippleDrawable(
                ColorStateList.valueOf(0x40FFFFFF),
                rounded(color, dp(ctx, 14f).toFloat()),
                null,
            )
            val ph = dp(ctx, 12f)
            setPadding(ph, dp(ctx, 6f), ph, dp(ctx, 6f))
        }

    fun setButtonColor(b: Button, color: Int) {
        b.background = RippleDrawable(
            ColorStateList.valueOf(0x40FFFFFF),
            rounded(color, dp(b.context, 14f).toFloat()),
            null,
        )
    }

    fun text(ctx: Context, text: CharSequence, sp: Float, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun vertical(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun horizontal(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun matchWrap(topMarginPx: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = topMarginPx }

    fun weighted(weight: Float = 1f, marginPx: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            leftMargin = marginPx; rightMargin = marginPx
        }

    fun colorDot(ctx: Context, color: Int, sizeDp: Float): View = View(ctx).apply {
        background = oval(color, Color.WHITE, dp(ctx, 2f))
        layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
    }

    /** 밝은 색 위에서는 검은 글씨, 어두운 색 위에서는 흰 글씨 */
    fun textOn(color: Int): Int {
        val l = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        return if (l > 160) TEXT else Color.WHITE
    }
}
