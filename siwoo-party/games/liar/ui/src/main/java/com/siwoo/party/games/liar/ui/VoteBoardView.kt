package com.siwoo.party.games.liar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.ui.Theme

/**
 * 개표판: 위쪽에 후보 이름표(플레이어 색)가 나란히 있고, 아래 가운데 투표함에서 표가 한 장씩 올라와
 * 뒤집히며(뒷면 "?" → 앞면 받은 사람 이름) 그 사람 이름 아래로 날아가 쌓인다. 이름표 아래에 "N표".
 * 개표가 끝나면 최다 득표 칸에 금색 테두리.
 * 움직이는 동안만 postOnAnimation 으로 다시 그린다 (대기 중엔 그리지 않음). onDraw 할당 없음.
 */
class VoteBoardView(context: Context) : View(context) {

    private var n = 0
    /** 칸 k 의 플레이어 번호 */
    private var ids = IntArray(0)
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    /** 칸마다 쌓인 표 수 */
    private var stacks = IntArray(0)
    private var top = BooleanArray(0)
    /** 표 이름 글자 크기 (칸마다, 이름 길이에 맞춤) */
    private var ballotNameSize = FloatArray(0)
    private var plateNameSize = FloatArray(0)

    // 날아가는 표 (한 번에 한 장)
    private var flyCol = -1
    private var flyStart = 0L
    private var flipMs = 1L
    private var flyMs = 1L
    var animating = false
        private set

    private val plate = RectF()
    private val box = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val ballotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }

    // 배치 (layout 에서 계산)
    private var colW = 0f
    private var startX = 0f
    private var plateTop = 0f
    private var plateH = 0f
    private var countY = 0f
    private var stackTop = 0f
    private var bw = 0f
    private var bh = 0f
    private var flipCx = 0f
    private var flipCy = 0f

    private val frame = object : Runnable {
        override fun run() {
            if (!animating) return
            if (SystemClock.uptimeMillis() - flyStart >= flipMs + flyMs) land() else postOnAnimation(this)
            invalidate()
        }
    }

    /** 후보 칸 설정 (표 모두 비움) */
    fun setCandidates(playerIds: List<Int>, playerNames: List<String>, playerColors: List<Int>) {
        stop()
        n = playerIds.size
        ids = playerIds.toIntArray()
        names = playerNames.toTypedArray()
        colors = playerColors.toIntArray()
        stacks = IntArray(n)
        top = BooleanArray(n)
        ballotNameSize = FloatArray(n)
        plateNameSize = FloatArray(n)
        contentDescription = "개표"
        layoutBoard()
        invalidate()
    }

    /** 칸 번호 (없으면 -1) */
    fun columnOf(player: Int): Int = ids.indexOf(player)

    /** 그 사람이 받은 (쌓인) 표 */
    fun landed(player: Int): Int { val k = columnOf(player); return if (k < 0) 0 else stacks[k] }
    val totalLanded: Int get() = stacks.sum()

    /** 표 한 장: 투표함에서 올라와 뒤집히고(flipMs) target 이름 아래로 날아가 쌓인다(flyMs) */
    fun revealBallot(target: Int, flipMs: Long, flyMs: Long) {
        if (animating) land()
        val k = columnOf(target)
        if (k < 0) return
        flyCol = k
        this.flipMs = flipMs.coerceAtLeast(1)
        this.flyMs = flyMs.coerceAtLeast(1)
        flyStart = SystemClock.uptimeMillis()
        animating = true
        postOnAnimation(frame)
        invalidate()
    }

    private fun land() {
        if (!animating) return
        animating = false
        removeCallbacks(frame)
        if (flyCol >= 0) stacks[flyCol]++
        flyCol = -1
        invalidate()
    }

    /** 날고 있는 표를 바로 내려놓는다 */
    fun finishAll() = land()

    private fun stop() {
        animating = false
        removeCallbacks(frame)
        flyCol = -1
    }

    /** 최다 득표 칸 표시 */
    fun setTop(players: List<Int>) {
        for (k in 0 until n) top[k] = ids[k] in players
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBoard()
    }

    private fun layoutBoard() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || n == 0) return
        colW = minOf(w / n, w * 0.26f)
        startX = (w - colW * n) / 2f
        plateTop = h * 0.03f
        plateH = h * 0.15f
        countY = plateTop + plateH + h * 0.09f
        stackTop = countY + h * 0.04f
        bh = h * 0.105f
        bw = minOf(colW * 0.78f, bh * 3.4f)
        countPaint.textSize = h * 0.07f
        boxPaint.textSize = h * 0.055f
        box.set(w / 2f - w * 0.11f, h * 0.82f, w / 2f + w * 0.11f, h * 0.985f)
        flipCx = w / 2f
        flipCy = box.top - bh * 0.75f
        for (k in 0 until n) {
            namePaint.textSize = plateH * 0.42f
            while (namePaint.measureText(names[k]) > colW * 0.82f && namePaint.textSize > Theme.dpf(context, 8f)) namePaint.textSize *= 0.92f
            plateNameSize[k] = namePaint.textSize
            ballotPaint.textSize = bh * 0.52f
            while (ballotPaint.measureText(names[k]) > bw * 0.78f && ballotPaint.textSize > Theme.dpf(context, 7f)) ballotPaint.textSize *= 0.92f
            ballotNameSize[k] = ballotPaint.textSize
        }
    }

    private fun colCx(k: Int): Float = startX + colW * (k + 0.5f)
    private fun slotCy(j: Int): Float = stackTop + bh * 0.5f + j * bh * 1.18f

    override fun onDraw(c: Canvas) {
        if (n == 0 || colW <= 0f) return
        val h = height.toFloat()
        val r = plateH * 0.3f
        // 후보 칸
        for (k in 0 until n) {
            val cx = colCx(k)
            plate.set(cx - colW * 0.44f, plateTop, cx + colW * 0.44f, plateTop + plateH)
            if (top[k]) {
                // 최다 득표: 칸 전체 금색 테두리
                stroke.color = Theme.GOLD
                stroke.strokeWidth = Theme.dpf(context, 3f)
                c.drawRoundRect(plate.left - bh * 0.15f, plate.top - bh * 0.1f, plate.right + bh * 0.15f, slotCy(2) + bh * 0.7f, r, r, stroke)
            }
            fill.color = colors[k]
            c.drawRoundRect(plate, r, r, fill)
            namePaint.textSize = plateNameSize[k]
            namePaint.color = Theme.textOn(colors[k])
            c.drawText(names[k], cx, plate.centerY() + namePaint.textSize * 0.36f, namePaint)
            c.drawText(COUNT_TEXT[stacks[k].coerceAtMost(COUNT_TEXT.size - 1)], cx, countY, countPaint)
            // 받은 표 (살짝 엇갈리게 쌓기)
            for (j in 0 until stacks[k]) drawBallotFront(c, k, cx, slotCy(j), if (j % 2 == 0) -3f else 3f, 1f)
        }
        // 투표함
        fill.color = Theme.BG_DEEP
        c.drawRoundRect(box, box.height() * 0.18f, box.height() * 0.18f, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = Theme.dpf(context, 2f)
        c.drawRoundRect(box, box.height() * 0.18f, box.height() * 0.18f, stroke)
        fill.color = 0xFF000000.toInt()
        c.drawRoundRect(box.centerX() - box.width() * 0.3f, box.top + box.height() * 0.14f, box.centerX() + box.width() * 0.3f, box.top + box.height() * 0.28f, h * 0.01f, h * 0.01f, fill)
        c.drawText(BOX_TEXT, box.centerX(), box.bottom - box.height() * 0.22f, boxPaint)
        // 날아가는 표
        if (animating && flyCol >= 0) {
            val e = (SystemClock.uptimeMillis() - flyStart).toFloat()
            if (e < flipMs) {
                val f = e / flipMs
                // 투표함에서 올라오며 뒤집힘 (가로 폭이 줄었다 늘어남)
                val rise = minOf(1f, f * 2.2f)
                val y = box.top + (flipCy - box.top) * rise
                val sx = Math.abs(Math.cos(Math.PI * f)).toFloat().coerceAtLeast(0.04f)
                c.save()
                c.scale(sx * 1.25f, 1.25f, flipCx, y)
                if (f < 0.5f) drawBallotBack(c, flipCx, y) else drawBallotFront(c, flyCol, flipCx, y, 0f, 1f)
                c.restore()
            } else {
                val f = ((e - flipMs) / flyMs).coerceIn(0f, 1f)
                val q = 1f - (1f - f) * (1f - f)
                val tx = colCx(flyCol)
                val ty = slotCy(stacks[flyCol])
                val x = flipCx + (tx - flipCx) * q
                val y = flipCy + (ty - flipCy) * q - Math.sin(Math.PI * q).toFloat() * bh
                val s = 1.25f - 0.25f * q
                c.save()
                c.scale(s, s, x, y)
                drawBallotFront(c, flyCol, x, y, if (stacks[flyCol] % 2 == 0) -3f * q else 3f * q, 1f)
                c.restore()
            }
        }
    }

    /** 표 앞면: 종이 + 왼쪽 색 띠 + 받은 사람 이름 */
    private fun drawBallotFront(c: Canvas, k: Int, cx: Float, cy: Float, deg: Float, a: Float) {
        c.save()
        c.rotate(deg, cx, cy)
        val l = cx - bw / 2f
        val t = cy - bh / 2f
        val rr = bh * 0.18f
        fill.color = 0x55000000
        c.drawRoundRect(l + bh * 0.05f, t + bh * 0.08f, l + bw + bh * 0.05f, t + bh + bh * 0.08f, rr, rr, fill)
        fill.color = Theme.PAPER
        c.drawRoundRect(l, t, l + bw, t + bh, rr, rr, fill)
        fill.color = colors[k]
        c.drawRoundRect(l, t, l + bh * 0.3f, t + bh, rr, rr, fill)
        c.drawRect(l + bh * 0.15f, t, l + bh * 0.3f, t + bh, fill)
        ballotPaint.textSize = ballotNameSize[k]
        ballotPaint.color = Theme.INK
        c.drawText(names[k], cx + bh * 0.15f, cy + ballotPaint.textSize * 0.36f, ballotPaint)
        c.restore()
    }

    /** 표 뒷면: 남색 종이 + 금색 "?" */
    private fun drawBallotBack(c: Canvas, cx: Float, cy: Float) {
        val l = cx - bw / 2f
        val t = cy - bh / 2f
        val rr = bh * 0.18f
        fill.color = Theme.CARD_LIGHT
        c.drawRoundRect(l, t, l + bw, t + bh, rr, rr, fill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = bh * 0.06f
        c.drawRoundRect(l, t, l + bw, t + bh, rr, rr, stroke)
        ballotPaint.textSize = bh * 0.62f
        ballotPaint.color = Theme.GOLD
        c.drawText(BACK_TEXT, cx, cy + ballotPaint.textSize * 0.36f, ballotPaint)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    companion object {
        private val COUNT_TEXT = arrayOf("0표", "1표", "2표", "3표", "4표", "5표")
        private const val BOX_TEXT = "투표함"
        private const val BACK_TEXT = "?"
    }
}
