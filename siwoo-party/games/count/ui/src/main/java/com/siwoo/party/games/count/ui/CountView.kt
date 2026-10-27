package com.siwoo.party.games.count.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.count.CountLayout
import com.siwoo.party.games.count.Dot
import com.siwoo.party.games.count.DotShape
import com.siwoo.party.games.count.GameConfig

/**
 * 몇 개였지? 판 (MultiTouchZoneView 위에 겹치는 그림 층, 터치는 아래 구역으로 그대로 전달).
 * - 가운데 그림 칸: 준비(눈 뜨기) → 그림(점·별 섞어 count 개) → 가림(감은 눈) → 공개(같은 그림 + 개수 배지)
 * - 구역마다 같은 내용 칸(CountLayout)을 구역 방향으로 돌려 그림: 이름·라운드·점수, 시간 막대, 보기 4개, 잠금 덮개
 * 움직임은 step(dtMs) 로 진행 (화면이 연출 중·답하는 중에만 프레임을 돌린다). onDraw 는 할당 없음.
 */
class CountView(context: Context) : View(context) {

    private val dp = Theme.dpf(context, 1f)
    val layout = CountLayout()
    private var n = 0

    // ── 플레이어 ──
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var scores: Array<String> = emptyArray()

    // ── 라운드 (setRound 때 글자를 만든다) ──
    private var roundLabel = ""
    private var answerLabel = ""
    private var badgeText = ""
    private val choiceText = Array(GameConfig.CHOICES) { "" }
    private var correct = -1
    private var winner = -1
    private var hasRound = false

    /** 그림 칸 상태 */
    var panel = PANEL_IDLE
        private set

    // ── 그림 (정규 좌표: 칸 높이 = 1) ──
    private var dotCount = 0
    private val nx = FloatArray(GameConfig.COUNT_MAX)
    private val ny = FloatArray(GameConfig.COUNT_MAX)
    private val nr = FloatArray(GameConfig.COUNT_MAX)
    private val shape = IntArray(GameConfig.COUNT_MAX)
    private val dotColor = IntArray(GameConfig.COUNT_MAX)
    /** 그림을 놓는 안쪽 칸 (화면 좌표) */
    private val inner = RectF()

    // ── 구역별 상태 ──
    private var wrong = IntArray(0)
    private var lock = FloatArray(0)
    private var flashChoice = IntArray(0)
    private var flashT = FloatArray(0)
    private var timeFrac = 1f
    private var hurry = false
    /** 보기 등장 연출 0..1 */
    private var popT = 1f
    /** 준비 연출 0..1 */
    private var readyT = 1f

    var popMs = GameConfig.POP_MS.toFloat()
    var flashMs = GameConfig.TAP_FLASH_MS.toFloat()
    var readyMs = GameConfig.READY_MS.toFloat()

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Theme.GOLD }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val choicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.RED }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.INK }
    private val rect = RectF()
    private val starPath = Path()
    private val triPath = Path()
    private var blueFace: LinearGradient? = null
    private var greenFace: LinearGradient? = null
    private var paperFace: LinearGradient? = null
    private val blueBase = Theme.shade(Theme.BLUE, 0.45f)
    private val greenBase = Theme.GREEN_DARK
    private val grayBase = Theme.shade(Theme.GRAY, 0.5f)
    private val dotDark = IntArray(GameConfig.DOT_COLORS) { Theme.shade(PALETTE[it], 0.6f) }
    private var lockTextW = 0f
    private var tall = false

    init {
        isClickable = false
        isFocusable = false
        // 단위 별 (반지름 1, 위로 뾰족)
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rad = if (k % 2 == 0) 1.18f else 0.5f
            val x = (rad * Math.cos(a)).toFloat()
            val y = (rad * Math.sin(a)).toFloat()
            if (k == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        // 단위 세모
        for (k in 0 until 3) {
            val a = -Math.PI / 2 + k * Math.PI * 2 / 3
            val x = (1.2f * Math.cos(a)).toFloat()
            val y = (1.2f * Math.sin(a) + 0.15f).toFloat()
            if (k == 0) triPath.moveTo(x, y) else triPath.lineTo(x, y)
        }
        triPath.close()
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names = playerNames.toTypedArray()
        colors = playerColors
        scores = Array(n) { "0점" }
        wrong = IntArray(n)
        lock = FloatArray(n)
        flashChoice = IntArray(n) { -1 }
        flashT = FloatArray(n)
        relayout()
    }

    fun setScore(i: Int, score: Int) { scores[i] = "${score}점"; invalidate() }

    /** 그림 칸 안쪽의 가로/세로 비 (그림 배치용) */
    val flashAspect: Float get() = if (inner.height() > 0f) inner.width() / inner.height() else 1.5f

    /** 새 라운드: 보기·그림을 받고 준비 연출 시작 (보기 숫자는 아직 가림) */
    fun setRound(number: Int, total: Int, count: Int, choices: IntArray, correctIndex: Int, dots: List<Dot>) {
        roundLabel = "$number / $total"
        answerLabel = "정답 ${count}개"
        badgeText = count.toString()
        for (k in 0 until GameConfig.CHOICES) choiceText[k] = choices[k].toString()
        correct = correctIndex
        winner = -1
        hasRound = true
        dotCount = minOf(dots.size, GameConfig.COUNT_MAX)
        for (k in 0 until dotCount) {
            val d = dots[k]
            nx[k] = d.x; ny[k] = d.y; nr[k] = d.r
            shape[k] = d.shape.ordinal
            dotColor[k] = d.color % GameConfig.DOT_COLORS
        }
        wrong.fill(0)
        lock.fill(0f)
        flashChoice.fill(-1)
        flashT.fill(0f)
        timeFrac = 1f
        hurry = false
        panel = PANEL_READY
        readyT = 0f
        invalidate()
    }

    /** 그림 보이기 */
    fun showDots() { panel = PANEL_SHOW; readyT = 1f; invalidate() }

    /** 그림 가리고 보기 숫자 보이기 */
    fun hideDots() { panel = PANEL_HIDDEN; popT = 0f; invalidate() }

    /** 정답 공개: 그림 다시 + 개수 (winnerZone = 맞힌 구역, -1 = 아무도 못 맞힘) */
    fun reveal(winnerZone: Int) {
        panel = PANEL_REVEAL
        winner = winnerZone
        lock.fill(0f)
        invalidate()
    }

    /** 그림이 지금 보이는지 */
    val dotsVisible: Boolean get() = panel == PANEL_SHOW || panel == PANEL_REVEAL

    fun markWrong(i: Int, choice: Int) { wrong[i] = wrong[i] or (1 shl choice); invalidate() }

    fun flash(i: Int, choice: Int) { flashChoice[i] = choice; flashT[i] = 1f; invalidate() }

    fun setLock(i: Int, frac: Float) {
        if (lock[i] == frac) return
        lock[i] = frac
        invalidate()
    }

    fun isLockShown(i: Int): Boolean = lock[i] > 0f

    fun setTime(frac: Float, hurrying: Boolean) {
        if (frac == timeFrac && hurrying == hurry) return
        timeFrac = frac
        hurry = hurrying
        invalidate()
    }

    /** 연출 진행. 아직 움직이는 게 있으면 true */
    fun step(dtMs: Float): Boolean {
        var busy = false
        if (popT < 1f) { popT = (popT + dtMs / popMs.coerceAtLeast(1f)).coerceAtMost(1f); busy = true }
        if (panel == PANEL_READY && readyT < 1f) { readyT = (readyT + dtMs / readyMs.coerceAtLeast(1f)).coerceAtMost(1f); busy = true }
        for (i in 0 until n) {
            if (flashT[i] > 0f) { flashT[i] = (flashT[i] - dtMs / flashMs.coerceAtLeast(1f)).coerceAtLeast(0f); busy = true }
        }
        if (busy) invalidate()
        return busy
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        val zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        layout.layout(zones, width.toFloat(), height.toFloat(), 8f * dp)
        tall = layout.tall
        val f = layout.flash
        val frame = minOf(f.width, f.height) * 0.05f
        inner.set(f.left + frame, f.top + frame, f.right - frame, f.bottom - frame)
        val hh = layout.headerH
        namePaint.textSize = minOf(hh * 0.62f, 17f * dp)
        scorePaint.textSize = namePaint.textSize
        midPaint.textSize = namePaint.textSize * 0.88f
        statusPaint.textSize = minOf(layout.cellW * 0.12f, 24f * dp)
        val c0 = layout.choices[0]
        choicePaint.textSize = minOf(c0.height * 0.5f, c0.width * 0.42f)
        lockPaint.textSize = minOf(c0.height * 0.3f, 16f * dp)
        lockTextW = lockPaint.measureText(LOCK_TEXT)
        badgePaint.textSize = minOf(f.height * 0.13f, 26f * dp)
        blueFace = LinearGradient(0f, c0.top, 0f, c0.bottom, Theme.shade(Theme.BLUE, 1.35f), Theme.shade(Theme.BLUE, 0.85f), Shader.TileMode.CLAMP)
        greenFace = LinearGradient(0f, c0.top, 0f, c0.bottom, Theme.shade(Theme.GREEN, 1.3f), Theme.GREEN_DARK, Shader.TileMode.CLAMP)
        paperFace = LinearGradient(0f, inner.top, 0f, inner.bottom, 0xFFFFFDF4.toInt(), 0xFFF3E6C4.toInt(), Shader.TileMode.CLAMP)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (n == 0 || layout.contents.isEmpty()) return
        drawPanel(c)
        for (i in 0 until n) {
            val b = layout.contents[i]
            c.save()
            c.translate(b.centerX, b.centerY)
            c.rotate(layout.rotations[i])
            drawHeader(c, i)
            if (hasRound) {
                if (tall) drawStatus(c, i)
                drawChoices(c, i)
                if (lock[i] > 0f && panel == PANEL_HIDDEN) drawLock(c, i)
            }
            c.restore()
        }
    }

    // ── 가운데 그림 칸 ──

    private fun drawPanel(c: Canvas) {
        val f = layout.flash
        val rad = minOf(f.width, f.height) * 0.09f
        p.shader = null
        p.style = Paint.Style.FILL
        // 그림자 + 금색 테두리 액자
        p.color = 0x66000000
        rect.set(f.left + 3f * dp, f.top + 5f * dp, f.right + 3f * dp, f.bottom + 5f * dp)
        c.drawRoundRect(rect, rad, rad, p)
        p.color = Theme.GOLD_DARK
        rect.set(f.left, f.top, f.right, f.bottom)
        c.drawRoundRect(rect, rad, rad, p)
        p.color = Theme.GOLD
        rect.set(f.left + 2f * dp, f.top + 2f * dp, f.right - 2f * dp, f.bottom - 2f * dp)
        c.drawRoundRect(rect, rad, rad, p)
        val ir = rad * 0.7f
        if (dotsVisible) {
            p.color = 0xFFFFFFFF.toInt()
            p.shader = paperFace
            c.drawRoundRect(inner, ir, ir, p)
            p.shader = null
            drawDots(c)
            if (panel == PANEL_REVEAL) drawBadges(c)
        } else {
            p.color = 0xFF0B1238.toInt()
            c.drawRoundRect(inner, ir, ir, p)
            val cx = inner.centerX()
            val cy = inner.centerY()
            val s = inner.height()
            drawEye(c, cx, cy, s * 0.22f)
        }
    }

    /** 눈: 준비 = 점점 뜨는 눈 + 퍼지는 고리, 가림 = 감은 눈 (위아래 대칭이라 어느 쪽에서 봐도 같음) */
    private fun drawEye(c: Canvas, cx: Float, cy: Float, s: Float) {
        val open = when (panel) {
            PANEL_READY -> (0.25f + readyT * 0.75f)
            else -> 0f
        }
        line.color = if (panel == PANEL_READY) Theme.GOLD else Theme.TEXT_SUB
        line.strokeWidth = s * 0.12f
        if (open <= 0f) {
            // 감은 눈 + 속눈썹 (위·아래 모두)
            rect.set(cx - s, cy - s * 0.5f, cx + s, cy + s * 0.5f)
            c.drawArc(rect, 20f, 140f, false, line)
            c.drawArc(rect, 200f, 140f, false, line)
            c.drawLine(cx, cy + s * 0.5f, cx, cy + s * 0.75f, line)
            c.drawLine(cx, cy - s * 0.5f, cx, cy - s * 0.75f, line)
            c.drawLine(cx - s * 0.45f, cy + s * 0.43f, cx - s * 0.6f, cy + s * 0.62f, line)
            c.drawLine(cx + s * 0.45f, cy - s * 0.43f, cx + s * 0.6f, cy - s * 0.62f, line)
            c.drawLine(cx + s * 0.45f, cy + s * 0.43f, cx + s * 0.6f, cy + s * 0.62f, line)
            c.drawLine(cx - s * 0.45f, cy - s * 0.43f, cx - s * 0.6f, cy - s * 0.62f, line)
            return
        }
        // 퍼지는 고리
        line.alpha = (200 * (1f - readyT)).toInt()
        c.drawCircle(cx, cy, s * (1.1f + readyT * 0.9f), line)
        line.alpha = 255
        val h = s * 0.62f * open
        rect.set(cx - s, cy - h, cx + s, cy + h)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawOval(rect, p)
        c.drawOval(rect, line)
        p.color = 0xFF1E3A8A.toInt()
        val pr = minOf(s * 0.38f, h * 0.95f)
        c.drawCircle(cx, cy, pr, p)
        p.color = 0xFF0A0F2E.toInt()
        c.drawCircle(cx, cy, pr * 0.5f, p)
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(cx + pr * 0.35f, cy - pr * 0.35f, pr * 0.22f, p)
    }

    private fun drawDots(c: Canvas) {
        val sc = inner.height()
        val ox = inner.left + (inner.width() - flashAspect * sc) / 2f
        val oy = inner.top
        line.strokeWidth = 1.5f * dp
        for (k in 0 until dotCount) {
            val x = ox + nx[k] * sc
            val y = oy + ny[k] * sc
            val r = nr[k] * sc
            p.color = PALETTE[dotColor[k]]
            line.color = dotDark[dotColor[k]]
            when (shape[k]) {
                SHAPE_CIRCLE -> {
                    c.drawCircle(x, y, r, p)
                    c.drawCircle(x, y, r, line)
                }
                SHAPE_SQUARE -> {
                    val h = r * 0.86f
                    rect.set(x - h, y - h, x + h, y + h)
                    c.drawRoundRect(rect, r * 0.25f, r * 0.25f, p)
                    c.drawRoundRect(rect, r * 0.25f, r * 0.25f, line)
                }
                else -> {
                    c.save()
                    c.translate(x, y)
                    c.scale(r, r)
                    val path = if (shape[k] == SHAPE_STAR) starPath else triPath
                    c.drawPath(path, p)
                    line.strokeWidth = 1.5f * dp / r
                    c.drawPath(path, line)
                    line.strokeWidth = 1.5f * dp
                    c.restore()
                }
            }
            // 반짝 점
            p.color = 0x66FFFFFF
            c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.22f, p)
        }
    }

    /** 개수 배지: 아래쪽 사람용(바로) + 위쪽 사람이 있으면 180° 돌린 것 하나 더 */
    private fun drawBadges(c: Canvas) {
        val f = layout.flash
        val r = badgePaint.textSize * 1.05f
        if (n == 2) {
            drawBadge(c, f.centerX, f.bottom - r * 0.2f, r, 0f)
        } else {
            drawBadge(c, f.right - r * 0.6f, f.bottom - r * 0.4f, r, 0f)
            drawBadge(c, f.left + r * 0.6f, f.top + r * 0.4f, r, 180f)
        }
    }

    private fun drawBadge(c: Canvas, x: Float, y: Float, r: Float, rot: Float) {
        c.save()
        c.rotate(rot, x, y)
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawCircle(x + 2f * dp, y + 3f * dp, r, p)
        p.color = Theme.GOLD_DARK
        c.drawCircle(x, y, r, p)
        p.color = Theme.GOLD
        c.drawCircle(x, y, r * 0.86f, p)
        c.drawText(badgeText, x, y + badgePaint.textSize * 0.36f, badgePaint)
        c.restore()
    }

    // ── 구역 내용 ──

    private fun RectF.setTo(b: com.siwoo.party.games.count.Box) { set(b.left, b.top, b.right, b.bottom) }

    private fun drawHeader(c: Canvas, i: Int) {
        val l = layout
        val x = l.cellW * 0.48f
        val ty = l.headerY + namePaint.textSize * 0.36f
        namePaint.color = colors[i]
        c.drawText(names[i], -x, ty, namePaint)
        c.drawText(scores[i], x, ty, scorePaint)
        if (hasRound) {
            val reveal = panel == PANEL_REVEAL
            midPaint.color = if (reveal) Theme.GOLD else Theme.TEXT_SUB
            c.drawText(if (reveal && !tall) answerLabel else roundLabel, 0f, l.headerY + midPaint.textSize * 0.36f, midPaint)
        }
        val b = l.timeBar
        val r = b.height / 2f
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0x55000000
        rect.setTo(b)
        c.drawRoundRect(rect, r, r, p)
        if (hasRound && panel == PANEL_HIDDEN && timeFrac > 0f) {
            p.color = when {
                hurry -> Theme.RED
                timeFrac < 0.5f -> Theme.GOLD
                else -> Theme.GREEN
            }
            rect.set(b.left, b.top, b.left + b.width * timeFrac, b.bottom)
            c.drawRoundRect(rect, r, r, p)
        }
    }

    /** 세로 칸(2인)의 가운데 안내 글자 */
    private fun drawStatus(c: Canvas, i: Int) {
        val text: String
        when (panel) {
            PANEL_READY -> { text = STATUS_READY; statusPaint.color = Theme.GOLD }
            PANEL_SHOW -> { text = STATUS_SHOW; statusPaint.color = Theme.GOLD }
            PANEL_HIDDEN -> { text = STATUS_ASK; statusPaint.color = Theme.TEXT }
            PANEL_REVEAL -> {
                text = if (winner == i) STATUS_WIN else answerLabel
                statusPaint.color = Theme.GOLD
            }
            else -> return
        }
        c.drawText(text, 0f, layout.statusY + statusPaint.textSize * 0.36f, statusPaint)
    }

    private fun drawChoices(c: Canvas, i: Int) {
        val depth = 4f * dp
        val hidden = panel == PANEL_READY || panel == PANEL_SHOW
        val revealed = panel == PANEL_REVEAL
        val e = popT
        val sc = 0.7f + 0.3f * (1f - (1f - e) * (1f - e))
        for (k in 0 until GameConfig.CHOICES) {
            val b = layout.choices[k]
            val rad = minOf(b.height * 0.28f, 14f * dp)
            val isWrong = (wrong[i] shr k) and 1 == 1
            val isRight = revealed && k == correct
            val dimmed = revealed && !isRight
            c.save()
            if (!hidden && e < 1f) c.scale(sc, sc, b.centerX, b.centerY)
            p.shader = null
            p.alpha = 255
            if (hidden) {
                // 아직 가린 보기: 어두운 빈 판
                p.color = 0x40FFFFFF
                rect.set(b.left, b.top, b.right, b.bottom)
                c.drawRoundRect(rect, rad, rad, p)
                p.color = 0x50000000
                rect.set(b.left + 2f * dp, b.top + 2f * dp, b.right - 2f * dp, b.bottom - 2f * dp)
                c.drawRoundRect(rect, rad, rad, p)
                c.restore()
                continue
            }
            // 두께
            p.color = when {
                isRight -> greenBase
                isWrong -> grayBase
                else -> blueBase
            }
            if (dimmed) p.alpha = 110
            rect.set(b.left, b.top + depth, b.right, b.bottom)
            c.drawRoundRect(rect, rad, rad, p)
            // 윗면
            if (isRight) {
                p.color = 0xFFFFFFFF.toInt()
                p.shader = greenFace
            } else if (isWrong) {
                p.color = Theme.GRAY
            } else {
                p.color = 0xFFFFFFFF.toInt()
                p.shader = blueFace
            }
            if (dimmed) p.alpha = 110
            rect.set(b.left, b.top, b.right, b.bottom - depth)
            c.drawRoundRect(rect, rad, rad, p)
            p.shader = null
            p.alpha = 255
            // 광택
            p.color = 0x30FFFFFF
            rect.set(b.left + rad * 0.5f, b.top + depth * 0.6f, b.right - rad * 0.5f, b.top + (b.height - depth) * 0.42f)
            c.drawRoundRect(rect, rad * 0.7f, rad * 0.7f, p)
            // 번쩍
            if (flashChoice[i] == k && flashT[i] > 0f) {
                p.color = 0xFFFFFFFF.toInt()
                p.alpha = (150 * flashT[i]).toInt()
                rect.set(b.left, b.top, b.right, b.bottom - depth)
                c.drawRoundRect(rect, rad, rad, p)
                p.alpha = 255
            }
            // 숫자
            choicePaint.alpha = if (dimmed || isWrong) 140 else 255
            val cy = b.top + (b.height - depth) / 2f
            c.drawText(choiceText[k], b.centerX, cy + choicePaint.textSize * 0.36f, choicePaint)
            choicePaint.alpha = 255
            if (isWrong) {
                line.color = Theme.RED
                line.strokeWidth = 4f * dp
                val d = minOf(b.width, b.height) * 0.26f
                c.drawLine(b.centerX - d, cy - d, b.centerX + d, cy + d, line)
                c.drawLine(b.centerX + d, cy - d, b.centerX - d, cy + d, line)
            }
            if (isRight && winner == i) {
                line.color = Theme.GOLD
                line.strokeWidth = 3f * dp
                rect.set(b.left - 2f * dp, b.top - 2f * dp, b.right + 2f * dp, b.bottom + 2f * dp)
                c.drawRoundRect(rect, rad, rad, line)
            }
            c.restore()
        }
    }

    /** 잠금 덮개: 보기 위 어두운 판 + 자물쇠 + 글자 + 줄어드는 빨간 막대 */
    private fun drawLock(c: Canvas, i: Int) {
        val l = layout
        val first = l.choices[0]
        val last = l.choices[GameConfig.CHOICES - 1]
        val left = minOf(first.left, l.choices[2].left)
        val rad = minOf(first.height * 0.28f, 14f * dp)
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0xC0101530.toInt()
        rect.set(left - 3f * dp, first.top - 3f * dp, last.right + 3f * dp, last.bottom + 3f * dp)
        c.drawRoundRect(rect, rad, rad, p)
        line.color = Theme.RED
        line.strokeWidth = 2f * dp
        c.drawRoundRect(rect, rad, rad, line)
        val cy = rect.centerY() - lockPaint.textSize * 0.3f
        val s = lockPaint.textSize
        val lx = rect.centerX() - lockTextW / 2f - s * 0.9f
        p.color = Theme.RED
        rect.set(lx - s * 0.45f, cy - s * 0.25f, lx + s * 0.45f, cy + s * 0.45f)
        c.drawRoundRect(rect, s * 0.12f, s * 0.12f, p)
        line.strokeWidth = s * 0.14f
        rect.set(lx - s * 0.3f, cy - s * 0.65f, lx + s * 0.3f, cy + s * 0.05f)
        c.drawArc(rect, 180f, 180f, false, line)
        val mid = (left + last.right) / 2f
        c.drawText(LOCK_TEXT, mid, cy + s * 0.36f, lockPaint)
        val bw = (last.right - left) * 0.6f
        val by = cy + s * 1.1f
        p.color = 0x55FFFFFF
        rect.set(mid - bw / 2f, by, mid + bw / 2f, by + 5f * dp)
        c.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, p)
        p.color = Theme.RED
        rect.set(mid - bw / 2f, by, mid - bw / 2f + bw * lock[i], by + 5f * dp)
        c.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, p)
    }

    companion object {
        const val PANEL_IDLE = 0
        const val PANEL_READY = 1
        const val PANEL_SHOW = 2
        const val PANEL_HIDDEN = 3
        const val PANEL_REVEAL = 4

        private val SHAPE_CIRCLE = DotShape.CIRCLE.ordinal
        private val SHAPE_STAR = DotShape.STAR.ordinal
        private val SHAPE_SQUARE = DotShape.SQUARE.ordinal

        /** 그림 색 (밝은 종이 위에서 잘 보이게) */
        val PALETTE = intArrayOf(
            0xFFF03A4E.toInt(), // 빨강
            0xFF2F6BFF.toInt(), // 파랑
            0xFF1DB863.toInt(), // 초록
            0xFFFF8F1C.toInt(), // 주황
            0xFF9B4DE0.toInt(), // 보라
            0xFF14B5C8.toInt(), // 청록
        )

        private const val LOCK_TEXT = "틀렸어요! 잠금"
        private const val STATUS_READY = "잘 보세요!"
        private const val STATUS_SHOW = "세어 보세요!"
        private const val STATUS_ASK = "몇 개였지?"
        private const val STATUS_WIN = "정답! +1"
    }
}
