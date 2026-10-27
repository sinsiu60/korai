package com.siwoo.party.games.yacht.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.siwoo.party.common.dice.DiceSet
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.yacht.Category
import com.siwoo.party.games.yacht.GameConfig
import com.siwoo.party.games.yacht.YachtGame

/**
 * 점수표 (Canvas 하나): 왼쪽 칸 이름 열 + 플레이어별 열 (2~4).
 * 줄: 이름 머리 / 에이스~헥사 / 소계 "n/63" / 보너스 / 초이스~야추 / 총점 (+ 공개 모드에서 순위)
 * - 지금 차례인 사람 열은 그 사람 색으로 칠하고, 굴린 뒤에는 빈 칸에 들어갈 점수를 흐리게 미리 보여 준다
 * - 그 열의 칸(또는 왼쪽 칸 이름)을 누르면 listener 로 알린다 (규칙 판정은 액티비티·YachtGame)
 * - 공개 모드(reveal): 위에서부터 한 줄씩 밝혀지며 총점이 올라가고, 마지막에 순위 줄 + 1등 열이 금빛으로 빛난다
 * 점수는 YachtGame 에서 바로 읽는다. 숫자는 문자 배열에 직접 적어 그리므로 onDraw 에서 객체를 만들지 않는다.
 */
class ScoreSheetView(context: Context) : View(context) {

    interface Listener {
        fun onCategoryTap(cat: Category)
    }

    var listener: Listener? = null
    /** 칸 터치를 받을지 */
    var tapEnabled = true

    private var game: YachtGame? = null
    private var n = 0
    private val colors = IntArray(GameConfig.MAX_PLAYERS)
    private val names = Array(GameConfig.MAX_PLAYERS) { "" }
    private val shownNames = Array(GameConfig.MAX_PLAYERS) { "" }
    private val nameSizes = FloatArray(GameConfig.MAX_PLAYERS)
    private val nameInk = IntArray(GameConfig.MAX_PLAYERS)
    private var current = -1
    private var previewOn = false

    /** 왼쪽 칸 이름 열 폭 (px) — 붙이기 전에 정한다 */
    var catWidth = Theme.dpf(context, 74f)
        set(v) { field = v; relayout() }

    // ── 공개 모드 ──
    private var revealMode = false
    /** 밝혀진 줄 수 (소수 = 지금 밝혀지는 줄의 진행도) */
    private var revealPos = 0f
    private var revealAnim: ValueAnimator? = null
    private val ranks = IntArray(GameConfig.MAX_PLAYERS)
    private var ranksShown = false
    private var glow = 0f
    private var glowAnim: ValueAnimator? = null

    // ── 기록한 칸 톡 ──
    private var popPlayer = -1
    private var popCat = -1
    private var popF = 0f
    private var popAnim: ValueAnimator? = null

    // ── 누르는 중 ──
    private var pressedRow = -1

    // ── 배치 ──
    private val rowKind = IntArray(MAX_ROWS)
    private val rowTop = FloatArray(MAX_ROWS)
    private val rowBottom = FloatArray(MAX_ROWS)
    private var rows = 0
    private var pad = 0f
    private var headerTop = 0f
    private var headerBottom = 0f
    private var colLeft0 = 0f
    private var colW = 0f
    private var cellText = 0f
    private var iconS = 0f
    private val frame = RectF()
    private val rect = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint().apply { color = 0x1FFFFFFF }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
    private val chars = CharArray(16)

    /** 칸 이름 앞 작은 주사위 (공통 DiceSet 으로 그림) */
    private val icon = DiceSet(this).apply { count = 1 }

    init {
        isClickable = true
        contentDescription = "점수표"
    }

    /** 게임·플레이어 연결 (이름·색) */
    fun bind(g: YachtGame, playerNames: List<String>, playerColors: IntArray) {
        game = g
        n = g.playerCount
        for (i in 0 until n) {
            names[i] = playerNames[i]
            colors[i] = playerColors[i]
            nameInk[i] = Theme.textOn(playerColors[i])
        }
        relayout()
    }

    /** 지금 차례 열 (-1 = 없음) */
    fun setCurrent(p: Int) {
        if (current != p) { current = p; invalidate() }
    }

    /** 빈 칸 점수 미리보기 표시 (굴린 뒤에만) */
    fun setPreview(on: Boolean) {
        if (previewOn != on) { previewOn = on; invalidate() }
    }

    /** 점수가 바뀌었을 때 */
    fun refresh() = invalidate()

    /** 방금 기록한 칸: 숫자가 톡 커졌다 돌아오며 금빛 */
    fun pop(player: Int, cat: Category, durMs: Long) {
        popPlayer = player
        popCat = cat.ordinal
        popAnim?.cancel()
        popAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = durMs.coerceAtLeast(1)
            addUpdateListener { popF = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { popF = 0f; popPlayer = -1; invalidate() }
            })
            start()
        }
    }

    // ── 공개 모드 ──

    /** 공개 모드로 바꾼다 (순위 줄 추가, 모든 칸 가림) */
    fun enterReveal() {
        revealMode = true
        revealPos = 0f
        ranksShown = false
        current = -1
        previewOn = false
        tapEnabled = false
        relayout()
    }

    /** 공개할 줄 수 (머리·순위 줄 제외, 총점 줄 포함) */
    val revealRowCount: Int get() = (rows - 2).coerceAtLeast(0)

    /** 한 줄씩 밝히기 (줄마다 rowMs) */
    fun startReveal(rowMs: Long) {
        revealAnim?.cancel()
        val count = revealRowCount
        revealAnim = ValueAnimator.ofFloat(0f, count.toFloat()).apply {
            duration = (rowMs * count).coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener { revealPos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** 공개 끝: 모든 줄 + 순위 줄, 1등 열이 빛남 (glowMs 동안 맥박) */
    fun showRanks(r: IntArray, glowMs: Long) {
        revealAnim?.cancel()
        revealPos = rows.toFloat()
        for (i in 0 until n) ranks[i] = r[i]
        ranksShown = true
        glowAnim?.cancel()
        glowAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = glowMs.coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                // 켜진 채로 몇 번 맥박
                glow = 0.6f + 0.4f * (0.5f + 0.5f * kotlin.math.cos(f * GLOW_PULSES * 2f * Math.PI.toFloat()))
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { glow = 1f; invalidate() }
            })
            start()
        }
        invalidate()
    }

    val isRevealing: Boolean get() = revealAnim?.isRunning == true

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun weight(kind: Int): Float = when (kind) {
        SUB, BONUS -> 0.86f
        TOTAL -> 1.3f
        RANK -> 1.25f
        else -> 1f
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        val d = Theme.dpf(context, 1f)
        // 줄 구성
        rows = 0
        rowKind[rows++] = HEADER
        for (k in 0 until YachtGame.UPPER_COUNT) rowKind[rows++] = k
        rowKind[rows++] = SUB
        rowKind[rows++] = BONUS
        for (k in YachtGame.UPPER_COUNT until GameConfig.CATEGORIES) rowKind[rows++] = k
        rowKind[rows++] = TOTAL
        if (revealMode) rowKind[rows++] = RANK
        var total = 0f
        for (r in 0 until rows) total += if (rowKind[r] == HEADER) HEADER_WEIGHT else weight(rowKind[r])
        pad = d * 4f
        stroke.strokeWidth = d * 1.5f
        frame.set(d, d, width - d, height - d)
        val unit = (height - pad * 2f) / total
        var y = pad
        for (r in 0 until rows) {
            val hgt = unit * (if (rowKind[r] == HEADER) HEADER_WEIGHT else weight(rowKind[r]))
            rowTop[r] = y
            rowBottom[r] = y + hgt
            y += hgt
        }
        headerTop = rowTop[0]
        headerBottom = rowBottom[0]
        colLeft0 = pad + catWidth
        colW = (width - pad - colLeft0) / n
        // 글자 크기: 줄 높이에 맞추고, 칸 이름이 열 폭을 넘지 않게
        cellText = minOf(unit * (if (revealMode) 0.66f else 0.62f), d * 17f)
        iconS = minOf(unit * 0.72f, d * 18f)
        labelPaint.textSize = cellText
        val labelMax = catWidth - iconS - d * 12f
        var widest = 0f
        for (c in Category.ALL) {
            val wl = labelPaint.measureText(c.label) + if (c.upper) 0f else iconS + d * 4f
            if (wl > widest) widest = wl
        }
        if (widest > labelMax + iconS + d * 4f) labelPaint.textSize = cellText * (labelMax + iconS + d * 4f) / widest
        cellPaint.textSize = cellText
        previewPaint.textSize = cellText
        totalPaint.textSize = minOf(cellText * 1.18f, colW * 0.42f)
        subPaint.textSize = cellText * 0.82f
        while (subPaint.measureText(SUB_SAMPLE) > colW - d * 6f && subPaint.textSize > cellText * 0.55f) subPaint.textSize *= 0.92f
        titlePaint.textSize = cellText * 0.86f
        // 이름: 열 폭에 맞게 줄이고, 그래도 길면 자른다
        val base = minOf(unit * HEADER_WEIGHT * 0.5f, d * 17f)
        val maxW = colW - d * 6f
        for (i in 0 until n) {
            var s = base
            namePaint.textSize = s
            while (namePaint.measureText(names[i]) > maxW && s > base * 0.72f) { s *= 0.93f; namePaint.textSize = s }
            var shown = names[i]
            while (shown.length > 1 && namePaint.measureText(shown) > maxW) shown = shown.dropLast(2) + ELLIPSIS
            shownNames[i] = shown
            nameSizes[i] = s
        }
        invalidate()
    }

    fun colLeft(p: Int): Float = colLeft0 + p * colW

    private fun rowOf(cat: Int): Int {
        for (r in 0 until rows) if (rowKind[r] == cat) return r
        return -1
    }

    /** p 열 cat 칸 가운데 (이 뷰 좌표) */
    fun cellCenter(p: Int, cat: Category, out: PointF) {
        val r = rowOf(cat.ordinal)
        out.set(colLeft(p) + colW / 2f, (rowTop[r] + rowBottom[r]) / 2f)
    }

    private fun categoryRowAt(x: Float, y: Float): Int {
        if (current < 0) return -1
        val inLabel = x < colLeft0
        val inCol = x >= colLeft(current) && x < colLeft(current) + colW
        if (!inLabel && !inCol) return -1
        for (r in 0 until rows) if (y >= rowTop[r] && y < rowBottom[r] && rowKind[r] < GameConfig.CATEGORIES) return r
        return -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!tapEnabled) return false
                pressedRow = categoryRowAt(e.x, e.y)
                if (pressedRow >= 0) invalidate()
                return pressedRow >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedRow >= 0 && categoryRowAt(e.x, e.y) != pressedRow) { pressedRow = -1; invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val r = pressedRow
                pressedRow = -1
                invalidate()
                if (r >= 0 && tapEnabled && categoryRowAt(e.x, e.y) == r) {
                    performClick()
                    listener?.onCategoryTap(Category.ALL[rowKind[r]])
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pressedRow = -1; invalidate(); return true }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    // ── 그리기 ──

    /** 숫자(+앞 글자·뒤 글자)를 chars 에 적고 그린다 */
    private fun drawNum(c: Canvas, v: Int, prefix: Char, suffix: CharArray?, x: Float, y: Float, p: Paint) {
        var len = 0
        if (prefix != NO_CHAR) chars[len++] = prefix
        var x0 = if (v < 0) -v else v
        if (v < 0) chars[len++] = '-'
        val start = len
        if (x0 == 0) chars[len++] = '0'
        while (x0 > 0) { chars[len++] = '0' + (x0 % 10); x0 /= 10 }
        var i = start
        var j = len - 1
        while (i < j) { val t = chars[i]; chars[i] = chars[j]; chars[j] = t; i++; j-- }
        if (suffix != null) for (ch in suffix) chars[len++] = ch
        c.drawText(chars, 0, len, x, y, p)
    }

    /** 공개 모드에서 r 번째 줄이 밝혀졌는지 (총점 줄은 늘 보이며 올라감) */
    private fun revealed(r: Int): Boolean = !revealMode || ranksShown || rowKind[r] == TOTAL || r - 1 < revealPos

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        if (rows == 0) return
        val d = Theme.dpf(context, 1f)
        val rad = d * 12f
        // 바탕 카드
        fill.color = 0xF21A2766.toInt()
        c.drawRoundRect(frame, rad, rad, fill)
        // 머리 줄 바탕
        fill.color = 0x33000000
        rect.set(frame.left, frame.top, frame.right, headerBottom)
        c.drawRoundRect(rect, rad, rad, fill)
        // 줄 바탕 (소계·보너스·총점은 진하게, 칸 줄은 번갈아 옅게)
        var zebra = false
        for (r in 1 until rows) {
            val k = rowKind[r]
            val color = when (k) {
                SUB, BONUS -> 0x26000000
                TOTAL -> 0x40000000
                RANK -> 0x2AFFC93C
                else -> { zebra = !zebra; if (zebra) 0x0DFFFFFF else 0 }
            }
            if (color != 0) {
                fill.color = color
                c.drawRect(frame.left + d, rowTop[r], frame.right - d, rowBottom[r], fill)
            }
        }
        // 지금 차례 열: 그 사람 색으로 칠함
        if (current in 0 until n && !revealMode) {
            val l = colLeft(current)
            fill.color = colors[current]
            fill.alpha = 0x38
            c.drawRect(l, headerTop, l + colW, rowBottom[rows - 1], fill)
            fill.alpha = 255
            rect.set(l + d, headerTop + d, l + colW - d, headerBottom - d)
            c.drawRoundRect(rect, d * 6f, d * 6f, fill)
            stroke.color = colors[current]
            stroke.strokeWidth = d * 2f
            rect.set(l, headerTop, l + colW, rowBottom[rows - 1])
            c.drawRoundRect(rect, d * 6f, d * 6f, stroke)
        }
        // 공개 끝: 1등 열 금빛
        if (ranksShown) {
            for (p in 0 until n) if (ranks[p] == 1) {
                val l = colLeft(p)
                fill.color = Theme.GOLD
                fill.alpha = (0x48 * glow).toInt()
                c.drawRect(l, headerTop, l + colW, rowBottom[rows - 1], fill)
                fill.alpha = 255
                stroke.color = Theme.GOLD
                stroke.strokeWidth = d * (2f + 1.5f * glow)
                rect.set(l + d, headerTop + d, l + colW - d, rowBottom[rows - 1] - d)
                c.drawRoundRect(rect, d * 6f, d * 6f, stroke)
            }
        }
        // 구분선: 열 사이, 줄 사이, 구역 사이(금색)
        line.strokeWidth = d
        for (p in 0 until n) c.drawLine(colLeft(p), headerTop + d * 3f, colLeft(p), rowBottom[rows - 1] - d * 3f, line)
        for (r in 1 until rows) {
            val k = rowKind[r]
            val prev = rowKind[r - 1]
            val section = k == SUB || k == TOTAL || prev == BONUS || r == 1
            if (section) {
                line.color = 0x80FFC93C.toInt()
                line.strokeWidth = d * 1.2f
            } else {
                line.color = 0x1AFFFFFF
                line.strokeWidth = d
            }
            c.drawLine(frame.left + d * 4f, rowTop[r], frame.right - d * 4f, rowTop[r], line)
        }
        line.color = 0x1FFFFFFF
        // 테두리
        stroke.color = Theme.CARD_BORDER
        stroke.strokeWidth = d * 1.2f
        c.drawRoundRect(frame, rad, rad, stroke)

        // 머리: 제목 + 이름
        val hy = (headerTop + headerBottom) / 2f
        c.drawText(if (revealMode) TITLE_FINAL else TITLE, pad + d * 5f, hy + titlePaint.textSize * 0.36f, titlePaint)
        for (p in 0 until n) {
            namePaint.textSize = nameSizes[p]
            namePaint.color = if (p == current && !revealMode) nameInk[p] else colors[p]
            c.drawText(shownNames[p], colLeft(p) + colW / 2f, hy + nameSizes[p] * 0.36f, namePaint)
        }

        // 줄마다
        for (r in 1 until rows) {
            val k = rowKind[r]
            val top = rowTop[r]
            val bottom = rowBottom[r]
            val cy = (top + bottom) / 2f
            val base = cy + cellText * 0.36f
            // 왼쪽 칸 이름
            drawLabel(c, k, cy, base, d)
            if (!revealed(r)) continue
            for (p in 0 until n) {
                val cx = colLeft(p) + colW / 2f
                when {
                    k < GameConfig.CATEGORIES -> drawCategoryCell(c, g, p, k, r, cx, top, bottom, base, d)
                    k == SUB -> {
                        val s = g.upperSubtotal(p)
                        subPaint.color = if (s >= GameConfig.UPPER_BONUS_THRESHOLD) Theme.GREEN else Theme.TEXT_SUB
                        drawNum(c, s, NO_CHAR, SLASH_63, cx, cy + subPaint.textSize * 0.36f, subPaint)
                    }
                    k == BONUS -> {
                        if (g.hasBonus(p)) {
                            cellPaint.color = Theme.GOLD
                            drawNum(c, GameConfig.UPPER_BONUS, '+', null, cx, base, cellPaint)
                        } else if (g.upperComplete(p)) {
                            cellPaint.color = Theme.GRAY
                            drawNum(c, 0, NO_CHAR, null, cx, base, cellPaint)
                        }
                    }
                    k == TOTAL -> {
                        val v = if (revealMode) runningTotal(g, p) else g.total(p)
                        totalPaint.color = if (ranksShown && ranks[p] == 1) Theme.GOLD_LIGHT else Theme.GOLD
                        drawNum(c, v, NO_CHAR, null, cx, cy + totalPaint.textSize * 0.36f, totalPaint)
                    }
                    k == RANK -> if (ranksShown) {
                        val rk = ranks[p].coerceIn(1, RANK_LABELS.size)
                        totalPaint.color = if (rk == 1) Theme.GOLD else Theme.TEXT
                        c.drawText(RANK_LABELS[rk - 1], cx, cy + totalPaint.textSize * 0.36f, totalPaint)
                    }
                }
            }
        }
        // 공개 중: 지금 밝혀지는 줄에 금빛 띠
        if (revealMode && !ranksShown && revealPos < revealRowCount) {
            val r = revealPos.toInt() + 1
            if (r in 1 until rows) {
                val f = revealPos - revealPos.toInt()
                fill.color = Theme.GOLD
                fill.alpha = (0x70 * (1f - f)).toInt()
                c.drawRect(frame.left + d * 2f, rowTop[r], frame.right - d * 2f, rowBottom[r], fill)
                fill.alpha = 255
            }
        }
        // 누르는 중인 줄
        if (pressedRow >= 0 && current >= 0) {
            fill.color = 0x40FFFFFF
            c.drawRect(frame.left + d * 2f, rowTop[pressedRow], colLeft0, rowBottom[pressedRow], fill)
            c.drawRect(colLeft(current), rowTop[pressedRow], colLeft(current) + colW, rowBottom[pressedRow], fill)
        }
    }

    /** 공개 중 총점: 밝혀진 줄까지의 합 (보너스 줄이 밝혀지면 보너스 포함) */
    private fun runningTotal(g: YachtGame, p: Int): Int {
        var s = 0
        for (r in 1 until rows) {
            val k = rowKind[r]
            if (k == TOTAL || !revealed(r)) continue
            if (k < GameConfig.CATEGORIES) { val v = g.score(p, Category.ALL[k]); if (v > 0) s += v }
            else if (k == BONUS) s += g.bonus(p)
        }
        return s
    }

    private fun drawLabel(c: Canvas, k: Int, cy: Float, base: Float, d: Float) {
        val x = pad + d * 5f
        when {
            k < YachtGame.UPPER_COUNT -> {
                icon.values[0] = k + 1
                icon.layoutRow(x + iconS / 2f, cy, iconS, 0f)
                icon.draw(c)
                labelPaint.color = Theme.TEXT
                c.drawText(Category.ALL[k].label, x + iconS + d * 5f, base, labelPaint)
            }
            k < GameConfig.CATEGORIES -> {
                labelPaint.color = if (k == Category.YACHT.ordinal) Theme.GOLD else Theme.TEXT
                c.drawText(Category.ALL[k].label, x, base, labelPaint)
            }
            k == SUB -> { labelPaint.color = Theme.TEXT_SUB; c.drawText(LABEL_SUB, x, base, labelPaint) }
            k == BONUS -> { labelPaint.color = Theme.TEXT_SUB; c.drawText(LABEL_BONUS, x, base, labelPaint) }
            k == TOTAL -> { labelPaint.color = Theme.GOLD; c.drawText(LABEL_TOTAL, x, base, labelPaint) }
            k == RANK -> { labelPaint.color = Theme.GOLD; c.drawText(LABEL_RANK, x, base, labelPaint) }
        }
    }

    private fun drawCategoryCell(c: Canvas, g: YachtGame, p: Int, k: Int, r: Int, cx: Float, top: Float, bottom: Float, base: Float, d: Float) {
        val cat = Category.ALL[k]
        val v = g.score(p, cat)
        if (v != YachtGame.EMPTY) {
            val popping = p == popPlayer && k == popCat && popF > 0f
            if (popping) {
                fill.color = Theme.GOLD
                fill.alpha = (0xA0 * popF).toInt()
                c.drawRect(colLeft(p) + d * 2f, top + d, colLeft(p) + colW - d * 2f, bottom - d, fill)
                fill.alpha = 255
                c.save()
                val sc = 1f + 0.6f * popF
                c.scale(sc, sc, cx, (top + bottom) / 2f)
            }
            cellPaint.color = if (v == 0) Theme.GRAY else Theme.TEXT
            drawNum(c, v, NO_CHAR, null, cx, base, cellPaint)
            if (popping) c.restore()
        } else if (previewOn && p == current && !revealMode) {
            val pv = g.preview(cat)
            if (pv >= 0) {
                previewPaint.color = if (pv == 0) 0x66B8C3F0 else 0x99FFE08A.toInt()
                drawNum(c, pv, NO_CHAR, null, cx, base, previewPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        popAnim?.cancel()
        revealAnim?.cancel()
        glowAnim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX_ROWS = 20
        private const val HEADER = 100
        private const val SUB = 101
        private const val BONUS = 102
        private const val TOTAL = 103
        private const val RANK = 104
        private const val HEADER_WEIGHT = 1.4f
        private const val GLOW_PULSES = 3f
        private const val NO_CHAR = ' '
        private val SLASH_63 = ("/" + GameConfig.UPPER_BONUS_THRESHOLD).toCharArray()
        private const val SUB_SAMPLE = "88/63"
        private const val ELLIPSIS = "…"
        private const val TITLE = "점수표"
        private const val TITLE_FINAL = "최종 점수"
        private const val LABEL_SUB = "소계"
        private val LABEL_BONUS = "보너스 +" + GameConfig.UPPER_BONUS
        private const val LABEL_TOTAL = "총점"
        private const val LABEL_RANK = "순위"
        private val RANK_LABELS = arrayOf("1등", "2등", "3등", "4등")
    }
}
