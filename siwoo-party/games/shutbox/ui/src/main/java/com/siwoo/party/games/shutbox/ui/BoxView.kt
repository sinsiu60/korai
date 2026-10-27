package com.siwoo.party.games.shutbox.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.siwoo.party.common.dice.DiceSet
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.shutbox.GameConfig
import com.siwoo.party.games.shutbox.ShutMath
import kotlin.math.PI
import kotlin.math.cos

/**
 * 나무 숫자 상자 하나 (모두 같이 쓰고, 차례마다 새 상자로 되돌린다).
 * 위: 1~9 숫자판이 뒷벽 앞에 서 있다. 닫히면 아래 경첩을 축으로 앞으로 넘어간다(세로 크기 cos 로 줄었다가 뒷면이 누움).
 *     고른 숫자 = 위로 들림 + 금색 테두리, 합을 맞출 수 있는 숫자 = 은은한 금빛 테두리, 안 되는 숫자 = 살짝 어둡게.
 * 아래: 초록 펠트 주사위 판(공통 DiceSet) + 오른쪽 계기판 "선택 7 / 주사위 9" + 짧은 안내.
 * 그리기(onDraw)에서 객체를 만들지 않는다. 글자는 setter 에서만 만든다. 넘어가는 동안만 다시 그린다.
 */
class BoxView(context: Context) : View(context) {

    fun interface Listener {
        fun onTileTap(n: Int)
    }

    var listener: Listener? = null
    /** 숫자판 터치를 받을지 (고르는 중에만) */
    var tapEnabled = false

    /** 주사위 (펠트 판 위) */
    val dice = DiceSet(this)
    /** 주사위 흐리게(굴리기 전) */
    var diceDim = true
        set(v) { if (field != v) { field = v; invalidate() } }

    // ── 상태 (밖에서 넣음) ──
    private var openMask = ShutMath.FULL
    private var selMask = 0
    private var hintMask = 0
    private var selecting = false
    /** 숫자판 넘어감 정도 [1..9] (0 = 서 있음, 1 = 완전히 누움) */
    private val down = FloatArray(GameConfig.TILES + 1)
    private val flipFrom = FloatArray(GameConfig.TILES + 1)
    private val flipTo = FloatArray(GameConfig.TILES + 1)
    private val flipDelay = LongArray(GameConfig.TILES + 1)
    private var flipDur = 1L
    private var flipTotal = 1L
    private var flipEaseIn = true
    private var flipAnim: ValueAnimator? = null

    // ── 계기판 글자 ──
    private var selText = DASH
    private var totalText = DASH
    private var selColor = Theme.TEXT
    private var totalColor = Theme.TEXT
    private var hintText = ""

    // ── 배치 (onSizeChanged) ──
    private val outer = RectF()
    private val inner = RectF()
    private val backWall = RectF()
    private val tray = RectF()
    private val panel = RectF()
    private val tileX = FloatArray(GameConfig.TILES + 1)
    private var tileW = 0f
    private var tileH = 0f
    private var hingeY = 0f
    private var downH = 0f
    private var lift = 0f
    private var corner = 0f
    private var d = 1f

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33000000 }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = INK }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0x40FFE0B0 }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val grain = Path()
    private val numOffset = FloatArray(1)
    private var hintBase = 0f

    init {
        dice.count = GameConfig.DICE
        contentDescription = "숫자 상자"
    }

    // ── 밖에서 부르는 것 ──

    /** 열린 숫자·고른 숫자·강조 숫자 (selecting = 고르는 중이면 안 되는 숫자를 흐리게) */
    fun setState(open: Int, selected: Int, hint: Int, selecting: Boolean) {
        openMask = open
        selMask = selected
        hintMask = hint
        this.selecting = selecting
        invalidate()
    }

    /** 계기판: 고른 합 / 주사위 합 (total < 0 = 굴리는 중 "?", 0 = 아직 안 굴림 "–") */
    fun setReadout(sel: Int, total: Int) {
        totalText = when {
            total < 0 -> QUESTION
            total == 0 -> DASH
            else -> total.toString()
        }
        selText = if (total > 0) sel.toString() else DASH
        selColor = when {
            total <= 0 -> Theme.TEXT_SUB
            sel == total -> Theme.GOLD
            sel == 0 -> Theme.TEXT_SUB
            else -> Theme.TEXT
        }
        totalColor = if (total > 0) Theme.TEXT else Theme.TEXT_SUB
        invalidate()
    }

    /** 계기판 아래 짧은 안내 (폭에 맞게 줄임) */
    fun setHint(text: String) {
        if (hintText == text) return
        hintText = text
        fitHint()
        invalidate()
    }

    /** 주사위 개수 (1 또는 2) */
    fun setDiceCount(n: Int) {
        if (dice.count == n && dice.size > 0f) return
        dice.count = n
        layoutDice()
        invalidate()
    }

    /** mask 숫자판들을 앞으로 넘어뜨린다 (하나씩 staggerMs 늦게) */
    fun flipDown(mask: Int, durMs: Long, staggerMs: Long) {
        finishFlip()
        var k = 0
        for (n in GameConfig.TILES downTo 1) {
            flipFrom[n] = down[n]
            if (ShutMath.has(mask, n)) {
                flipTo[n] = 1f
                flipDelay[n] = staggerMs * k
                k++
            } else {
                flipTo[n] = down[n]
                flipDelay[n] = 0L
            }
        }
        startFlip(durMs, staggerMs * maxOf(0, k - 1), easeIn = true)
    }

    /** 누운 숫자판을 모두 다시 세운다 (새 상자) */
    fun resetUp(durMs: Long, staggerMs: Long) {
        finishFlip()
        var k = 0
        for (n in 1..GameConfig.TILES) {
            flipFrom[n] = down[n]
            flipTo[n] = 0f
            if (down[n] > 0f) {
                flipDelay[n] = staggerMs * k
                k++
            } else flipDelay[n] = 0L
        }
        if (k == 0) return
        startFlip(durMs, staggerMs * (k - 1), easeIn = false)
    }

    /** 넘어가는 중인지 */
    val isFlipping: Boolean get() = flipAnim?.isRunning == true

    /** 숫자판 n 이 얼마나 누웠는지 (0..1, 테스트·스크린샷용) */
    fun downOf(n: Int): Float = down[n]

    /** 숫자판 n 가운데 (이 View 좌표) */
    fun tileCenter(n: Int, out: PointF) {
        out.set(tileX[n] + tileW / 2f, hingeY - tileH / 2f)
    }

    /** 주사위 판 가운데 (이 View 좌표) */
    fun trayCenter(out: PointF) {
        out.set(tray.left + tray.width() * 0.28f, tray.centerY())
    }

    private fun startFlip(durMs: Long, extraMs: Long, easeIn: Boolean) {
        flipDur = durMs.coerceAtLeast(1L)
        flipTotal = flipDur + extraMs
        flipEaseIn = easeIn
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = flipTotal
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { va -> applyFlip(va.animatedFraction * flipTotal); invalidate() }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (flipAnim === animation) {
                    applyFlip(flipTotal.toFloat() + flipTotal)
                    flipAnim = null
                    invalidate()
                }
            }
        })
        flipAnim = a
        a.start()
    }

    private fun applyFlip(elapsed: Float) {
        for (n in 1..GameConfig.TILES) {
            val p = ((elapsed - flipDelay[n]) / flipDur).coerceIn(0f, 1f)
            val e = if (flipEaseIn) p * p else 1f - (1f - p) * (1f - p)
            down[n] = flipFrom[n] + (flipTo[n] - flipFrom[n]) * e
        }
    }

    /** 진행 중인 넘김을 끝 상태로 */
    private fun finishFlip() {
        val a = flipAnim ?: return
        flipAnim = null
        a.removeAllListeners()
        a.removeAllUpdateListeners()
        a.cancel()
        for (n in 1..GameConfig.TILES) down[n] = flipTo[n]
    }

    /** 테스트·일시정지 없이 바로 상태 맞추기 (mask = 누운 숫자판) */
    fun setDownImmediate(mask: Int) {
        finishFlip()
        for (n in 1..GameConfig.TILES) down[n] = if (ShutMath.has(mask, n)) 1f else 0f
        invalidate()
    }

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        d = Theme.dpf(context, 1f)
        val fw = minOf(w, h) * 0.05f
        corner = d * 16f
        outer.set(0f, 0f, w.toFloat(), h.toFloat())
        inner.set(fw, fw, w - fw, h - fw)
        woodPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), WOOD_LIGHT, WOOD_DARK, Shader.TileMode.CLAMP)
        // 숫자판 줄
        val pad = inner.width() * 0.025f
        val gap = inner.width() * 0.012f
        tileW = (inner.width() - pad * 2f - gap * (GameConfig.TILES - 1)) / GameConfig.TILES
        tileH = minOf(inner.height() * 0.42f, tileW * 2.1f)
        val top = inner.top + inner.height() * 0.05f
        hingeY = top + tileH
        downH = tileH * DOWN_RATIO
        lift = tileH * 0.09f
        for (n in 1..GameConfig.TILES) tileX[n] = inner.left + pad + (n - 1) * (tileW + gap)
        backWall.set(inner.left, inner.top, inner.right, hingeY + downH + d * 6f)
        wallPaint.shader = LinearGradient(0f, backWall.top, 0f, backWall.bottom, WALL_TOP, WALL_BOTTOM, Shader.TileMode.CLAMP)
        tilePaint.shader = LinearGradient(0f, top, 0f, hingeY, TILE_TOP, TILE_BOTTOM, Shader.TileMode.CLAMP)
        numPaint.textSize = minOf(tileW * 0.66f, tileH * 0.5f)
        ghostPaint.textSize = numPaint.textSize * 0.8f
        numOffset[0] = numPaint.textSize * 0.36f
        // 주사위 판 (나무 칸막이 아래)
        tray.set(inner.left, backWall.bottom + fw * 0.8f, inner.right, inner.bottom)
        feltPaint.shader = LinearGradient(0f, tray.top, 0f, tray.bottom, FELT_TOP, FELT_BOTTOM, Shader.TileMode.CLAMP)
        val pp = minOf(tray.height() * 0.1f, d * 8f)
        panel.set(tray.left + tray.width() * 0.56f, tray.top + pp, tray.right - pp, tray.bottom - pp)
        val ph = panel.height()
        labelPaint.textSize = minOf(ph * 0.17f, d * 12f)
        bigPaint.textSize = minOf(ph * 0.42f, panel.width() * 0.2f)
        hintBase = minOf(ph * 0.17f, d * 12f)
        fitHint()
        // 나뭇결 (테두리 위 물결 선)
        grain.reset()
        val lines = 7
        for (k in 0 until lines) {
            val y = h * (k + 0.5f) / lines
            grain.moveTo(0f, y)
            var x = 0f
            var up = k % 2 == 0
            val seg = w / 6f
            while (x < w) {
                grain.quadTo(x + seg / 2f, y + (if (up) -fw * 0.35f else fw * 0.35f), x + seg, y)
                x += seg
                up = !up
            }
        }
        grainPaint.strokeWidth = d * 1.2f
        layoutDice()
    }

    private fun layoutDice() {
        if (tray.width() <= 0f) return
        val zone = tray.width() * 0.56f
        val s = minOf(tray.height() * 0.6f, zone / (2 * 1.5f))
        dice.layoutRow(tray.left + zone / 2f, tray.centerY() - s * 0.05f, s, s * 0.4f)
    }

    private fun fitHint() {
        if (hintBase <= 0f || panel.width() <= 0f) return
        var s = hintBase
        hintPaint.textSize = s
        val max = panel.width() - d * 10f
        while (hintPaint.measureText(hintText) > max && s > hintBase * 0.6f) {
            s *= 0.93f
            hintPaint.textSize = s
        }
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) return tapEnabled
        if (e.actionMasked == MotionEvent.ACTION_UP && tapEnabled) {
            val n = tileAt(e.x, e.y)
            if (n > 0) {
                performClick()
                listener?.onTileTap(n)
            }
            return true
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    /** (x, y) 의 숫자판 번호 (없으면 0). 숫자판 사이 틈은 가까운 쪽으로 */
    fun tileAt(x: Float, y: Float): Int {
        if (y < hingeY - tileH - lift * 2f || y > hingeY + downH + d * 8f) return 0
        val half = (tileX[2] - tileX[1]) / 2f
        for (n in 1..GameConfig.TILES) {
            val cx = tileX[n] + tileW / 2f
            if (x >= cx - half && x <= cx + half) return n
        }
        return 0
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        if (inner.width() <= 0f) return
        // 나무 테두리
        c.drawRoundRect(outer, corner, corner, woodPaint)
        c.save()
        c.clipRect(outer)
        c.drawPath(grain, grainPaint)
        c.restore()
        stroke.color = 0x66000000
        stroke.strokeWidth = d * 2f
        c.drawRoundRect(outer, corner, corner, stroke)
        // 뒷벽
        c.drawRect(backWall, wallPaint)
        stroke.color = 0x55000000
        stroke.strokeWidth = d * 1.5f
        c.drawRect(backWall, stroke)
        // 경첩 홈
        fill.color = 0x88000000.toInt()
        c.drawRect(inner.left, hingeY - d, inner.right, hingeY + d * 1.5f, fill)
        // 닫힌 자리에 흐린 숫자
        for (n in 1..GameConfig.TILES) {
            if (down[n] > 0.5f) c.drawText(DIGITS[n], tileX[n] + tileW / 2f, hingeY - tileH * 0.5f + numOffset[0] * 0.8f, ghostPaint)
        }
        // 숫자판
        for (n in 1..GameConfig.TILES) drawTile(c, n)
        // 칸막이 (뒷벽과 주사위 판 사이 나무 띠)
        fill.color = 0x40000000
        c.drawRect(inner.left, backWall.bottom, inner.right, tray.top, fill)
        // 펠트 주사위 판
        c.drawRoundRect(tray, d * 10f, d * 10f, feltPaint)
        stroke.color = 0x55000000
        stroke.strokeWidth = d * 2f
        c.drawRoundRect(tray, d * 10f, d * 10f, stroke)
        stroke.color = 0x30FFFFFF
        stroke.strokeWidth = d
        rect.set(tray.left + d * 5f, tray.top + d * 5f, tray.right - d * 5f, tray.bottom - d * 5f)
        c.drawRoundRect(rect, d * 7f, d * 7f, stroke)
        // 주사위
        dice.draw(c, 0f, if (diceDim && !dice.rolling) DIM_ALPHA else 255)
        // 계기판
        drawPanel(c)
    }

    private fun drawTile(c: Canvas, n: Int) {
        val f = down[n]
        val x = tileX[n]
        val r = tileW * 0.16f
        if (f < 0.5f) {
            // 앞면: 경첩(아래)을 축으로 세로가 줄어든다
            val sy = cos(f * PI.toFloat())
            val isSel = f == 0f && ShutMath.has(selMask, n)
            val isOpen = ShutMath.has(openMask, n)
            val isHint = selecting && isOpen && !isSel && ShutMath.has(hintMask, n)
            val up = if (isSel) lift else 0f
            c.save()
            c.translate(0f, -up)
            c.scale(1f, sy, 0f, hingeY)
            rect.set(x, hingeY - tileH, x + tileW, hingeY)
            // 그림자 (뒷벽에)
            if (isSel) {
                fill.color = 0x66FFC93C
                rect.inset(-d * 3f, -d * 3f)
                c.drawRoundRect(rect, r * 1.3f, r * 1.3f, fill)
                rect.inset(d * 3f, d * 3f)
            }
            c.drawRoundRect(rect, r, r, tilePaint)
            // 위 둥근 장식선
            stroke.color = 0x40804A1E
            stroke.strokeWidth = d * 1.2f
            c.drawLine(x + tileW * 0.2f, hingeY - tileH * 0.86f, x + tileW * 0.8f, hingeY - tileH * 0.86f, stroke)
            c.drawLine(x + tileW * 0.2f, hingeY - tileH * 0.14f, x + tileW * 0.8f, hingeY - tileH * 0.14f, stroke)
            numPaint.color = if (isSel) SEL_INK else INK
            c.drawText(DIGITS[n], x + tileW / 2f, hingeY - tileH * 0.5f + numOffset[0], numPaint)
            // 테두리
            when {
                isSel -> { stroke.color = Theme.GOLD; stroke.strokeWidth = d * 3f }
                isHint -> { stroke.color = HINT_EDGE; stroke.strokeWidth = d * 2.2f }
                else -> { stroke.color = TILE_EDGE; stroke.strokeWidth = d * 1.2f }
            }
            c.drawRoundRect(rect, r, r, stroke)
            // 안 되는 숫자 흐리게 (고르는 중) / 넘어가는 중 그늘
            var shade = if (selecting && isOpen && !isSel && !isHint) 0x50 else 0
            if (f > 0f) shade = maxOf(shade, (f * 2f * 150f).toInt())
            if (shade > 0) {
                fill.color = 0x000000
                fill.alpha = shade
                c.drawRoundRect(rect, r, r, fill)
                fill.alpha = 255
            }
            c.restore()
            // 강조 숫자: 아래 작은 금빛 점
            if (isHint && f == 0f) {
                fill.color = HINT_EDGE
                c.drawCircle(x + tileW / 2f, hingeY + downH * 0.45f, d * 2.6f, fill)
            }
        } else {
            // 뒷면: 앞으로 누우며 경첩 아래로 길어진다
            val k = -cos(f * PI.toFloat())
            val bh = downH * k
            rect.set(x, hingeY, x + tileW, hingeY + bh)
            fill.color = BACK_FACE
            c.drawRoundRect(rect, r * 0.6f, r * 0.6f, fill)
            fill.color = BACK_EDGE
            c.drawRect(x + tileW * 0.12f, hingeY + bh * 0.35f, x + tileW * 0.88f, hingeY + bh * 0.35f + d * 1.2f, fill)
            stroke.color = 0x66000000
            stroke.strokeWidth = d
            c.drawRoundRect(rect, r * 0.6f, r * 0.6f, stroke)
            // 앞쪽 빛 (막 누웠을 때 살짝 밝음)
            if (f < 1f) {
                fill.color = 0xFFFFFFFF.toInt()
                fill.alpha = ((1f - f) * 90f).toInt()
                c.drawRoundRect(rect, r * 0.6f, r * 0.6f, fill)
                fill.alpha = 255
            }
        }
    }

    private fun drawPanel(c: Canvas) {
        fill.color = PANEL_BG
        c.drawRoundRect(panel, d * 10f, d * 10f, fill)
        stroke.color = Theme.GOLD_DARK
        stroke.strokeWidth = d * 1.5f
        c.drawRoundRect(panel, d * 10f, d * 10f, stroke)
        val ph = panel.height()
        val lx = panel.left + panel.width() * 0.27f
        val rx = panel.left + panel.width() * 0.73f
        val labelY = panel.top + ph * 0.24f
        val bigY = panel.top + ph * 0.64f
        c.drawText(LABEL_SEL, lx, labelY, labelPaint)
        c.drawText(LABEL_DICE, rx, labelY, labelPaint)
        bigPaint.color = selColor
        c.drawText(selText, lx, bigY, bigPaint)
        bigPaint.color = Theme.TEXT_SUB
        c.drawText(SLASH, panel.centerX(), bigY, bigPaint)
        bigPaint.color = totalColor
        c.drawText(totalText, rx, bigY, bigPaint)
        if (hintText.isNotEmpty()) c.drawText(hintText, panel.centerX(), panel.bottom - ph * 0.1f, hintPaint)
    }

    override fun onDetachedFromWindow() {
        finishFlip()
        dice.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /** 누운 숫자판 뒷면 높이 (서 있는 높이 대비) */
        private const val DOWN_RATIO = 0.3f
        private const val DIM_ALPHA = 110
        private const val WOOD_LIGHT = 0xFFA8692F.toInt()
        private const val WOOD_DARK = 0xFF6A3A16.toInt()
        private const val WALL_TOP = 0xFF3B200C.toInt()
        private const val WALL_BOTTOM = 0xFF5A3417.toInt()
        private const val TILE_TOP = 0xFFFFF4DA.toInt()
        private const val TILE_BOTTOM = 0xFFE9CC96.toInt()
        private const val TILE_EDGE = 0xFF8A5A2B.toInt()
        private const val HINT_EDGE = 0xFFFFD866.toInt()
        private const val INK = 0xFF4A2810.toInt()
        private const val SEL_INK = 0xFFB3261E.toInt()
        private const val BACK_FACE = 0xFF8B5427.toInt()
        private const val BACK_EDGE = 0xFF6B3D18.toInt()
        private const val FELT_TOP = 0xFF1F7A53.toInt()
        private const val FELT_BOTTOM = 0xFF11503A.toInt()
        private const val PANEL_BG = 0xCC0B1340.toInt()
        private const val LABEL_SEL = "선택"
        private const val LABEL_DICE = "주사위"
        private const val SLASH = "/"
        private const val DASH = "–"
        private const val QUESTION = "?"
        private val DIGITS = Array(GameConfig.TILES + 1) { it.toString() }
    }
}
