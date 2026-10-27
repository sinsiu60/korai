package com.siwoo.party.games.snakeladder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.dice.DiceSet
import com.siwoo.party.common.ui.Theme

/**
 * 오른쪽 차례 패널: "지금 차례" + 큰 이름(플레이어 색 말) + 주사위 1개(공통 DiceSet) + 안내 + 맵 정보.
 * rollable 일 때 주사위를 누르면 listener. 글자는 setter 에서만 바뀌고 onDraw 는 그리기만 한다.
 */
class TurnPanelView(context: Context) : View(context) {

    fun interface Listener {
        fun onDiceTapped()
    }

    var listener: Listener? = null
    val dice = DiceSet(this).apply { count = 1 }

    /** 굴릴 수 있음 (주사위 금색 테두리) */
    var rollable = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private var name = ""
    private var color = Theme.GOLD
    private var dark = Theme.GOLD_DARK
    private var hint = ""
    private var info1 = ""
    private var info2 = ""
    private var nameSize = 0f

    private val bg = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD_LIGHT }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }

    init {
        contentDescription = "주사위"
        isClickable = true
    }

    fun setPlayer(name: String, color: Int) {
        this.name = name
        this.color = color
        dark = Theme.shade(color, 0.5f)
        fitName()
        invalidate()
    }

    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        invalidate()
    }

    /** 아래 두 줄 (맵 이름 · 규칙) */
    fun setInfo(line1: String, line2: String) {
        info1 = line1
        info2 = line2
        invalidate()
    }

    val currentName: String get() = name
    val currentHint: String get() = hint

    private fun fitName() {
        if (width == 0) return
        val maxW = width * 0.62f
        var s = Theme.dpf(context, 22f)
        namePaint.textSize = s
        while (s > Theme.dpf(context, 11f) && namePaint.measureText(name) > maxW) {
            s -= Theme.dpf(context, 1f)
            namePaint.textSize = s
        }
        nameSize = s
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        subPaint.textSize = Theme.dpf(context, 12f)
        hintPaint.textSize = Theme.dpf(context, 13f)
        infoPaint.textSize = Theme.dpf(context, 11f)
        stroke.strokeWidth = Theme.dpf(context, 3f)
        val s = minOf(w * 0.42f, h * 0.3f)
        dice.layoutRow(w / 2f, h * 0.53f, s, 0f)
        fitName()
    }

    /** 주사위 가운데 y (테스트용 터치 위치) */
    val diceY: Float get() = height * 0.53f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val r = dice.size * 0.9f
                if (rollable && !dice.rolling && kotlin.math.abs(e.x - width / 2f) < r && kotlin.math.abs(e.y - diceY) < r) {
                    performClick()
                    listener?.onDiceTapped()
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = Theme.dpf(context, 16f)
        bg.set(0f, 0f, w, h)
        fill.style = Paint.Style.FILL
        fill.color = Theme.CARD
        c.drawRoundRect(bg, r, r, fill)
        // 지금 차례
        c.drawText(TURN, w / 2f, h * 0.085f, subPaint)
        val ny = h * 0.2f
        val tr = Theme.dpf(context, 9f)
        namePaint.textSize = nameSize
        val nw = namePaint.measureText(name)
        val tx = w / 2f - nw / 2f - tr * 1.4f
        SnakeArt.drawToken(c, tx + tr * 0.4f, ny - nameSize * 0.34f, tr, color, dark, false, fill)
        namePaint.color = color
        c.drawText(name, w / 2f + tr * 0.6f, ny, namePaint)
        // 주사위 받침
        val ds = dice.size
        val cy = diceY
        fill.color = Theme.BG_DEEP
        c.drawRoundRect(w / 2f - ds * 0.9f, cy - ds * 0.9f, w / 2f + ds * 0.9f, cy + ds * 0.9f, ds * 0.3f, ds * 0.3f, fill)
        if (rollable) {
            stroke.color = Theme.GOLD
            c.drawRoundRect(w / 2f - ds * 0.9f, cy - ds * 0.9f, w / 2f + ds * 0.9f, cy + ds * 0.9f, ds * 0.3f, ds * 0.3f, stroke)
        }
        dice.draw(c)
        c.drawText(hint, w / 2f, h * 0.82f, hintPaint)
        c.drawText(info1, w / 2f, h * 0.91f, infoPaint)
        c.drawText(info2, w / 2f, h * 0.97f, infoPaint)
    }

    override fun onDetachedFromWindow() {
        dice.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TURN = "지금 차례"
    }
}
