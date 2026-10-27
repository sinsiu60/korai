package com.siwoo.party.games.spotdiff.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.spotdiff.GameConfig
import com.siwoo.party.games.spotdiff.Scene
import com.siwoo.party.games.spotdiff.SpotGame
import com.siwoo.party.games.spotdiff.SpotLayout

/**
 * 틀린 그림 찾기 화면 하나 (그리기 + 동시 터치).
 * - 가운데: 머리줄(장면·찾은 개수·남은 시간), 두 그림(왼쪽 원래 / 오른쪽 차이), 안내줄(답 시간 막대).
 * - 가장자리: 플레이어 구역 + [찾았다!] 버저 (위쪽 구역은 180° 돌려 그림). 구역 어디를 눌러도 그 사람 버저.
 * - 손가락마다 down 순서대로 listener 에 알린다 (먼저 누른 사람 판정은 게임 규칙이 한다).
 * 그림은 장면이 바뀌거나 크기가 바뀔 때만 Bitmap 에 한 번 그리고, 동그라미·X 는 그 위에 할당 없이 그린다.
 * 애니메이션(답 시간·잠금·동그라미 등장·X·점수 떠오름)이 있을 때만 다시 그린다.
 */
class SpotView(context: Context) : View(context) {

    interface Listener {
        fun onBuzzer(player: Int)
        /** pic: 0 왼쪽, 1 오른쪽 그림. (sx, sy) 그림 좌표 */
        fun onPictureTap(pic: Int, sx: Float, sy: Float)
    }

    /** 게임 시간 (일시정지 제외) */
    fun interface Clock { fun now(): Long }

    var listener: Listener? = null
    var clock: Clock = Clock { SystemClock.uptimeMillis() }
    var frozen = false
        set(v) { field = v; if (!v) invalidate() }
    /** 연출 길이 (속도 배율 적용된 값) */
    var popMs = GameConfig.CIRCLE_POP_MS
    var flashMs = GameConfig.FLASH_MS

    var layout: SpotLayout? = null
        private set
    private var names: List<String> = emptyList()
    private var colors = IntArray(0)
    private var n = 0
    private var scene: Scene? = null
    private val renderer = SceneRenderer()
    private var bmp = arrayOfNulls<Bitmap>(2)
    private val bmpCanvas = Canvas()

    // ── 상태 (값이 바뀔 때만 문자열을 만든다) ──
    private var scoreText = arrayOf<String>()
    private var lockStart = LongArray(0)
    private var lockEnd = LongArray(0)
    private var flashUp = BooleanArray(0)
    private var flashStart = LongArray(0)
    private var winner = BooleanArray(0)
    private var answerer = -1
    private var answerStart = 0L
    private var answerEnd = 0L
    private var buzzOpen = false
    private val foundBy = IntArray(GameConfig.DIFFS_PER_SCENE) { -1 }
    private val foundAt = LongArray(GameConfig.DIFFS_PER_SCENE)
    private var wrongX = 0f
    private var wrongY = 0f
    private var wrongStart = Long.MIN_VALUE
    private var sceneText = ""
    private var timeText = ""
    private var timeFrac = 1f
    private var statusText = ""
    private var statusColor = Theme.TEXT_SUB

    // ── 그리기 도구 ──
    private val d = Theme.dpf(context, 1f)
    private val rect = RectF()
    private val pt = FloatArray(2)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(Theme.dpf(context, 6f), Theme.dpf(context, 4f)), 0f)
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val buzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var nameSize = FloatArray(0)

    init {
        isClickable = true
        contentDescription = "틀린 그림 찾기"
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        names = playerNames
        colors = playerColors
        n = playerNames.size
        scoreText = Array(n) { "0점" }
        lockStart = LongArray(n)
        lockEnd = LongArray(n) { Long.MIN_VALUE }
        flashUp = BooleanArray(n)
        flashStart = LongArray(n) { Long.MIN_VALUE }
        winner = BooleanArray(n)
        nameSize = FloatArray(n)
        relayout()
    }

    // ── 상태 바꾸기 ──

    /** 새 장면: 그림을 Bitmap 에 다시 그린다 */
    fun setScene(s: Scene, index: Int, total: Int) {
        scene = s
        sceneText = "장면 " + (index + 1) + "/" + total
        for (i in foundBy.indices) foundBy[i] = -1
        wrongStart = Long.MIN_VALUE
        answerer = -1
        render()
        invalidate()
    }

    fun setScore(i: Int, score: Int, delta: Int) {
        scoreText[i] = "" + score + "점"
        flashUp[i] = delta > 0
        flashStart[i] = clock.now()
        invalidate()
    }

    /** 버저를 누를 수 있는 때인지 (아니면 버저를 흐리게) */
    fun setBuzzOpen(open: Boolean) {
        buzzOpen = open
        invalidate()
    }

    fun setAnswering(player: Int, startMs: Long, endMs: Long) {
        answerer = player
        answerStart = startMs
        answerEnd = endMs
        invalidate()
    }

    fun endAnswer() {
        answerer = -1
        invalidate()
    }

    fun markFound(diff: Int, player: Int) {
        foundBy[diff] = player
        foundAt[diff] = clock.now()
        invalidate()
    }

    /** 오답: (sx, sy) 그림 좌표에 X (시간 초과면 NaN → X 없음), untilMs 까지 잠금 */
    fun showWrong(player: Int, sx: Float, sy: Float, untilMs: Long) {
        val now = clock.now()
        if (!sx.isNaN()) {
            wrongX = sx
            wrongY = sy
            wrongStart = now
        }
        lockStart[player] = now
        lockEnd[player] = untilMs
        invalidate()
    }

    fun setTimeLeft(ms: Long, totalMs: Long) {
        val sec = ((ms + 999) / 1000).coerceAtLeast(0)
        timeText = "" + sec + "초"
        timeFrac = (ms.toFloat() / totalMs).coerceIn(0f, 1f)
        invalidate()
    }

    fun setStatus(s: String, color: Int = Theme.TEXT_SUB) {
        statusText = s
        statusColor = color
        invalidate()
    }

    fun setWinner(i: Int, v: Boolean) {
        winner[i] = v
        invalidate()
    }

    /** 그림 Bitmap 해제 (onGameDestroy) */
    fun release() {
        for (k in 0..1) {
            bmp[k]?.recycle()
            bmp[k] = null
        }
    }

    // ── 시험·연출용 좌표 ──

    fun buzzerCenter(i: Int, out: PointF) {
        val l = layout ?: return
        out.set(l.buzzX[i], l.buzzY[i])
    }

    /** 그림 좌표 → 이 View 좌표 */
    fun sceneToView(pic: Int, sx: Float, sy: Float, out: PointF) {
        val l = layout ?: return
        l.toScreen(pic, sx, sy, pt)
        out.set(pt[0], pt[1])
    }

    // ── 배치·그림 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        val l = SpotLayout(n, width.toFloat(), height.toFloat(), d)
        layout = l
        namePaint.textSize = 17f * d
        scorePaint.textSize = 24f * d
        text.textSize = 15f * d
        // 이름이 구역보다 길면 줄인다
        for (i in 0 until n) {
            val z = l.panels[i]
            val maxW = (if (z.height >= z.width * 1.2f) z.width else z.width - l.buzzR[i] * 2f - 16f * d) - 12f * d
            var size = 17f * d
            namePaint.textSize = size
            while (size > 10f * d && namePaint.measureText(names[i]) > maxW) {
                size -= 1f * d
                namePaint.textSize = size
            }
            nameSize[i] = size
        }
        render()
        invalidate()
    }

    /** 장면 두 장을 Bitmap 에 그린다 (장면·크기가 바뀔 때만) */
    private fun render() {
        val l = layout ?: return
        val s = scene ?: return
        val bw = l.picW.toInt().coerceAtLeast(1)
        val bh = l.picH.toInt().coerceAtLeast(1)
        for (k in 0..1) {
            var b = bmp[k]
            if (b == null || b.width != bw || b.height != bh || b.isRecycled) {
                b?.recycle()
                b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                bmp[k] = b
            }
            bmpCanvas.setBitmap(b)
            renderer.drawScene(bmpCanvas, s, k == 1, 0f, 0f, bw / GameConfig.SCENE_W)
            bmpCanvas.setBitmap(null)
        }
    }

    // ── 터치: 손가락마다 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val l = layout ?: return true
        val idx = e.actionIndex
        val x = e.getX(idx)
        val y = e.getY(idx)
        val p = l.panelAt(x, y)
        if (p >= 0) {
            listener?.onBuzzer(p)
            return true
        }
        val pic = l.pictureAt(x, y)
        if (pic >= 0) {
            l.toScene(pic, x, y, pt)
            listener?.onPictureTap(pic, pt[0], pt[1])
        }
        return true
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val l = layout ?: return
        val now = clock.now()
        var anim = false
        for (i in 0 until n) if (drawPanel(c, l, i, now)) anim = true
        drawHeader(c, l)
        if (drawPictures(c, l, now)) anim = true
        if (drawStatus(c, l, now)) anim = true
        if (anim && !frozen) postInvalidateOnAnimation()
    }

    /** 플레이어 구역 + 버저. 애니메이션 중이면 true */
    private fun drawPanel(c: Canvas, l: SpotLayout, i: Int, now: Long): Boolean {
        val z = l.panels[i]
        val color = colors[i]
        val r = 16f * d
        rect.set(z.left + 3f * d, z.top + 3f * d, z.right - 3f * d, z.bottom - 3f * d)
        fill.color = color
        fill.alpha = if (answerer == i) 90 else 38
        c.drawRoundRect(rect, r, r, fill)
        fill.alpha = 255
        stroke.strokeWidth = 3f * d
        stroke.color = if (winner[i]) Theme.GOLD else color
        c.drawRoundRect(rect, r, r, stroke)
        if (winner[i]) {
            stroke.alpha = 90
            stroke.strokeWidth = 8f * d
            c.drawRoundRect(rect, r, r, stroke)
            stroke.alpha = 255
        }

        var anim = false
        val locked = now < lockEnd[i]
        val bx = l.buzzX[i]
        val by = l.buzzY[i]
        val br = l.buzzR[i]
        // 이름·점수 (그 사람 쪽으로 회전)
        c.save()
        c.rotate(z.rotation, z.centerX, z.centerY)
        // 회전된 좌표계에서 구역 왼쪽 위 = (z.left, z.top) 와 같은 크기
        val tall = z.height >= z.width * 1.2f
        val tx = if (tall) z.centerX else z.left + (z.width - br * 2f - 16f * d) / 2f + 4f * d
        val nameY = if (tall) z.top + 14f * d + nameSize[i] else z.centerY - 4f * d
        namePaint.textSize = nameSize[i]
        namePaint.color = color
        c.drawText(names[i], tx, nameY, namePaint)
        // 점수: 바뀐 직후엔 금색(+)/빨강(-)으로 커졌다가 돌아온다
        val ft = now - flashStart[i]
        val scoreY = nameY + 24f * d * 1.15f
        if (ft in 0 until flashMs) {
            val f = ft.toFloat() / flashMs
            scorePaint.textSize = 24f * d * (1f + 0.35f * (1f - f))
            scorePaint.color = if (flashUp[i]) Theme.GOLD else Theme.RED
            anim = true
        } else {
            scorePaint.textSize = 24f * d
            scorePaint.color = Theme.TEXT
        }
        c.drawText(scoreText[i], tx, scoreY, scorePaint)
        scorePaint.textSize = 24f * d
        scorePaint.color = Theme.TEXT
        c.restore()

        // 버저 (동그라미라 회전 무관, 글자만 회전)
        val active = answerer == i
        val dim = !buzzOpen || (answerer >= 0 && !active)
        fill.color = 0x66000000
        c.drawCircle(bx, by + 3f * d, br, fill)
        val base = when {
            locked -> Theme.GRAY
            else -> color
        }
        fill.color = Theme.shade(base, 0.62f)
        c.drawCircle(bx, by + 1.5f * d, br, fill)
        fill.color = base
        c.drawCircle(bx, by - 1f * d, br * 0.9f, fill)
        fill.color = Theme.shade(base, 1.25f)
        c.drawCircle(bx - br * 0.3f, by - br * 0.35f, br * 0.18f, fill)
        stroke.strokeWidth = 2.5f * d
        stroke.color = if (active) Theme.GOLD else 0xCCFFFFFF.toInt()
        c.drawCircle(bx, by - 1f * d, br * 0.9f, stroke)
        if (dim && !active) {
            fill.color = 0x88070D2E.toInt()
            c.drawCircle(bx, by, br + 1f * d, fill)
        }
        // 답하는 중: 금색 고리가 줄어듦
        if (active) {
            val f = if (answerEnd > answerStart) ((answerEnd - now).toFloat() / (answerEnd - answerStart)).coerceIn(0f, 1f) else 0f
            rect.set(bx - br - 4f * d, by - br - 4f * d, bx + br + 4f * d, by + br + 4f * d)
            stroke.color = Theme.GOLD
            stroke.strokeWidth = 4f * d
            c.drawArc(rect, -90f, 360f * f, false, stroke)
            anim = true
        }
        // 잠금: 남은 시간 고리
        if (locked) {
            val span = (lockEnd[i] - lockStart[i]).coerceAtLeast(1)
            val f = ((lockEnd[i] - now).toFloat() / span).coerceIn(0f, 1f)
            rect.set(bx - br - 4f * d, by - br - 4f * d, bx + br + 4f * d, by + br + 4f * d)
            stroke.color = Theme.RED
            stroke.strokeWidth = 4f * d
            c.drawArc(rect, -90f, 360f * f, false, stroke)
            anim = true
        }
        c.save()
        c.rotate(z.rotation, bx, by)
        buzzPaint.textSize = (br * 0.36f).coerceAtMost(17f * d)
        buzzPaint.color = if (locked) 0xFFE0E4F5.toInt() else Theme.textOn(base)
        c.drawText(if (locked) LOCK_LABEL else BUZZ_LABEL, bx, by + buzzPaint.textSize * 0.3f, buzzPaint)
        c.restore()
        return anim
    }

    private fun drawHeader(c: Canvas, l: SpotLayout) {
        rect.set(l.headerLeft, l.headerTop, l.headerRight, l.headerBottom)
        fill.color = 0xB3070D2E.toInt()
        val hr = (l.headerBottom - l.headerTop) / 2f
        c.drawRoundRect(rect, hr, hr, fill)
        val cy = (l.headerTop + l.headerBottom) / 2f
        // 장면 번호
        text.textAlign = Paint.Align.LEFT
        text.textSize = 14f * d
        text.color = Theme.GOLD
        c.drawText(sceneText, l.headerLeft + 12f * d, cy + text.textSize * 0.36f, text)
        // 찾은 개수: 동그라미 5개
        val dr = 6f * d
        val gap = 18f * d
        val x0 = (l.headerLeft + l.headerRight) / 2f - gap * 2f
        for (k in 0 until GameConfig.DIFFS_PER_SCENE) {
            val x = x0 + k * gap
            val who = foundBy[k]
            if (who >= 0) {
                fill.color = colors[who]
                c.drawCircle(x, cy, dr, fill)
                stroke.color = Theme.TEXT
                stroke.strokeWidth = 1.5f * d
                c.drawCircle(x, cy, dr, stroke)
            } else if (who == SpotGame.REVEALED) {
                fill.color = Theme.GRAY
                c.drawCircle(x, cy, dr, fill)
            } else {
                stroke.color = Theme.TEXT_SUB
                stroke.strokeWidth = 1.5f * d
                c.drawCircle(x, cy, dr, stroke)
            }
        }
        // 남은 시간: 막대 + 초
        if (timeText.isNotEmpty()) {
            val right = l.headerRight - 12f * d
            text.textAlign = Paint.Align.RIGHT
            text.color = if (timeFrac < 0.2f) Theme.RED else Theme.TEXT
            c.drawText(timeText, right, cy + text.textSize * 0.36f, text)
            val barR = right - 36f * d
            val barL = barR - (l.headerRight - l.headerLeft) * 0.16f
            rect.set(barL, cy - 3f * d, barR, cy + 3f * d)
            fill.color = 0x33FFFFFF
            c.drawRoundRect(rect, 3f * d, 3f * d, fill)
            rect.right = barL + (barR - barL) * timeFrac
            fill.color = if (timeFrac < 0.2f) Theme.RED else Theme.GREEN
            c.drawRoundRect(rect, 3f * d, 3f * d, fill)
        }
        text.textAlign = Paint.Align.LEFT
    }

    /** 두 그림 + 동그라미·X. 애니메이션 중이면 true */
    private fun drawPictures(c: Canvas, l: SpotLayout, now: Long): Boolean {
        var anim = false
        val s = scene
        val r = 8f * d
        for (k in 0..1) {
            val x = l.picLeft[k]
            rect.set(x - 3f * d, l.picTop - 3f * d, x + l.picW + 3f * d, l.picTop + l.picH + 3f * d)
            fill.color = if (answerer >= 0) colors[answerer] else 0xFFFFF8E7.toInt()
            c.drawRoundRect(rect, r, r, fill)
            val b = bmp[k]
            if (b != null && s != null && !b.isRecycled) {
                c.drawBitmap(b, x, l.picTop, bmpPaint)
            } else {
                rect.set(x, l.picTop, x + l.picW, l.picTop + l.picH)
                fill.color = Theme.CARD
                c.drawRect(rect, fill)
                text.textAlign = Paint.Align.CENTER
                text.textSize = 40f * d
                text.color = Theme.TEXT_SUB
                c.drawText("?", x + l.picW / 2f, l.picTop + l.picH / 2f + 14f * d, text)
                text.textAlign = Paint.Align.LEFT
            }
        }
        if (s == null) return false
        // 찾은 차이: 빨간 동그라미 (두 그림 모두), 시간 끝 공개는 노란 점선
        for (di in 0 until GameConfig.DIFFS_PER_SCENE) {
            val who = foundBy[di]
            if (who == -1) continue
            val diff = s.differences[di]
            val t = now - foundAt[di]
            var f = 1f
            if (t < popMs) {
                val e = (t.toFloat() / popMs).coerceIn(0f, 1f)
                f = 1.6f - 0.6f * e
                anim = true
            }
            val rad = diff.radius * l.scale * f
            for (k in 0..1) {
                l.toScreen(k, diff.cx, diff.cy, pt)
                if (who == SpotGame.REVEALED) {
                    dashed.color = Theme.GOLD
                    dashed.strokeWidth = 3.5f * d
                    c.drawCircle(pt[0], pt[1], rad, dashed)
                } else {
                    stroke.color = 0xE6FFFFFF.toInt()
                    stroke.strokeWidth = 5.5f * d
                    c.drawCircle(pt[0], pt[1], rad, stroke)
                    stroke.color = CIRCLE_RED
                    stroke.strokeWidth = 3f * d
                    c.drawCircle(pt[0], pt[1], rad, stroke)
                }
            }
        }
        // 오답 X
        val wt = now - wrongStart
        if (wrongStart != Long.MIN_VALUE && wt in 0 until flashMs) {
            val a = (255 * (1f - wt.toFloat() / flashMs)).toInt()
            val sz = 11f * d
            for (k in 0..1) {
                c.save()
                c.clipRect(l.picLeft[k], l.picTop, l.picLeft[k] + l.picW, l.picTop + l.picH)
                l.toScreen(k, wrongX, wrongY, pt)
                stroke.color = 0xFFFFFFFF.toInt()
                stroke.alpha = a
                stroke.strokeWidth = 7f * d
                c.drawLine(pt[0] - sz, pt[1] - sz, pt[0] + sz, pt[1] + sz, stroke)
                c.drawLine(pt[0] + sz, pt[1] - sz, pt[0] - sz, pt[1] + sz, stroke)
                stroke.color = Theme.RED
                stroke.alpha = a
                stroke.strokeWidth = 4f * d
                c.drawLine(pt[0] - sz, pt[1] - sz, pt[0] + sz, pt[1] + sz, stroke)
                c.drawLine(pt[0] + sz, pt[1] - sz, pt[0] - sz, pt[1] + sz, stroke)
                c.restore()
            }
            stroke.alpha = 255
            anim = true
        }
        return anim
    }

    /** 안내줄 (답하는 중이면 줄어드는 막대). 애니메이션 중이면 true */
    private fun drawStatus(c: Canvas, l: SpotLayout, now: Long): Boolean {
        val cy = (l.statusTop + l.statusBottom) / 2f
        val h = l.statusBottom - l.statusTop
        var anim = false
        rect.set(l.headerLeft, l.statusTop, l.headerRight, l.statusBottom)
        fill.color = 0x80070D2E.toInt()
        c.drawRoundRect(rect, h / 2f, h / 2f, fill)
        if (answerer >= 0 && answerEnd > answerStart) {
            val f = ((answerEnd - now).toFloat() / (answerEnd - answerStart)).coerceIn(0f, 1f)
            val w = (l.headerRight - l.headerLeft) * f
            rect.set(l.headerLeft, l.statusTop, l.headerLeft + w, l.statusBottom)
            fill.color = colors[answerer]
            fill.alpha = 150
            c.drawRoundRect(rect, h / 2f, h / 2f, fill)
            fill.alpha = 255
            anim = true
        }
        text.textAlign = Paint.Align.CENTER
        text.textSize = 14f * d
        text.color = if (answerer >= 0) Theme.TEXT else statusColor
        c.drawText(statusText, (l.headerLeft + l.headerRight) / 2f, cy + text.textSize * 0.36f, text)
        text.textAlign = Paint.Align.LEFT
        return anim
    }

    companion object {
        const val BUZZ_LABEL = "찾았다!"
        const val LOCK_LABEL = "잠금"
        const val CIRCLE_RED = 0xFFE8173A.toInt()
    }
}
