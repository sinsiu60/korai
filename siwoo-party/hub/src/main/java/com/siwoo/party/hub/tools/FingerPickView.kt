package com.siwoo.party.hub.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * 손가락 추첨 화면 (전체 화면 하나의 View).
 * 손가락마다 색 원이 맥박치고, 2초 그대로면 가운데 3·2·1, 결과:
 * - 1명 뽑기: 다른 원은 줄어 사라지고 당첨 원이 화면 가득 커진다.
 * - 팀 나누기: 원이 팀 색(빨강/파랑)으로 바뀌고 위에 팀 이름.
 * 그리기는 [tick] 이 돌 때(손가락이 있거나 연출 중)만 다시 한다. onDraw 에서 할당 없음.
 */
class FingerPickView(context: Context, val pick: FingerPick) : View(context) {

    /** 연출 속도 배율 (PartySettings.speedFactor, 박싱 없는 Float 필드) */
    var speedFactor = 1f

    interface Listener {
        /** 손가락 수가 바뀜 (루프 시작·효과음) */
        fun onFingers(down: Boolean)
    }

    var listener: Listener? = null

    private val n = FingerPick.MAX_FINGERS
    private val px = FloatArray(n)
    private val py = FloatArray(n)
    /** 나타나기 0→1 */
    private val grow = FloatArray(n)
    /** 손가락을 뗀 원이 사라지는 중 1→0 */
    private val fade = FloatArray(n)
    /** 결과 뒤 팀 색으로 바뀌는 정도 0→1 */
    private var recolor = 0f
    private var pulseMs = 0f
    private var resultMs = 0f
    private var countMs = 0f
    private var shownCount = 0
    private var winR0 = 0f
    private var winRMax = 0f
    private var wasResult = false

    private val baseR = Theme.dpf(context, 46f)
    private val topSafe = Theme.dpf(context, 56f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bigText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val bigOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }
    private val subText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val arcRect = RectF()

    private var hint = ""
    private var hintSub = ""
    private var teamLine0 = ""
    private var teamLine1 = ""

    init {
        bigText.setShadowLayer(Theme.dpf(context, 6f), 0f, Theme.dpf(context, 3f), Theme.GOLD_DARK)
        labelText.setShadowLayer(Theme.dpf(context, 2f), 0f, Theme.dpf(context, 1f), 0xFF000000.toInt())
        subText.textSize = Theme.dpf(context, 15f)
        labelText.textSize = Theme.dpf(context, 16f)
        ring.strokeWidth = Theme.dpf(context, 4f)
        isClickable = true
        refreshHint()
    }

    /** 결과·모드가 바뀌면 안내 글자를 다시 만든다 (그리기 밖에서) */
    fun refreshHint() {
        val team = pick.mode == FingerPick.Mode.TEAMS
        hint = "모두 손가락을 화면에 올려 주세요"
        hintSub = if (team) "2초 동안 그대로 두면 3·2·1 뒤에 두 팀으로 나눠요" else "2초 동안 그대로 두면 3·2·1 뒤에 한 명을 뽑아요"
        teamLine0 = "${TEAM_NAMES[0]} ${pick.teamSize(0)}명"
        teamLine1 = "${TEAM_NAMES[1]} ${pick.teamSize(1)}명"
        invalidate()
    }

    /** [다시] 등으로 결과를 지웠을 때 */
    fun clearResult() {
        wasResult = false
        recolor = 0f
        resultMs = 0f
        for (k in 0 until n) if (pick.slotId[k] < 0) { fade[k] = 0f; grow[k] = 0f }
        refreshHint()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val wasLifted = pick.phase == FingerPick.Phase.RESULT && pick.allLifted
                val slot = pick.down(e.getPointerId(i), now)
                if (wasLifted && pick.phase != FingerPick.Phase.RESULT) clearResult()
                if (slot >= 0) {
                    px[slot] = e.getX(i)
                    py[slot] = e.getY(i)
                    grow[slot] = 0f
                    fade[slot] = 0f
                    listener?.onFingers(true)
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val slot = pick.slotOf(e.getPointerId(i))
                if (slot >= 0) { px[slot] = e.getX(i); py[slot] = e.getY(i) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val slot = pick.up(e.getPointerId(i), now)
                if (slot >= 0) {
                    px[slot] = e.getX(i)
                    py[slot] = e.getY(i)
                    if (!pick.participant[slot]) fade[slot] = 1f
                    listener?.onFingers(false)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAll()
            }
        }
        invalidate()
        return true
    }

    fun cancelAll() {
        val now = SystemClock.uptimeMillis()
        for (k in 0 until n) if (pick.slotId[k] >= 0) {
            pick.up(pick.slotId[k], now)
            if (!pick.participant[k]) fade[k] = 1f
        }
        listener?.onFingers(false)
        invalidate()
    }

    /** 한 프레임 진행. 아직 움직일 것이 있으면 true */
    fun tick(dt: Long): Boolean {
        val d = dt.toFloat()
        var moving = false
        pulseMs += d
        if (pulseMs > PULSE_MS * 1000f) pulseMs -= PULSE_MS * 1000f
        val growMs = (GROW_MS * speedFactor).coerceAtLeast(1f)
        val fadeMs = (FADE_MS * speedFactor).coerceAtLeast(1f)
        for (k in 0 until n) {
            if (pick.slotId[k] >= 0 && grow[k] < 1f) { grow[k] = (grow[k] + d / growMs).coerceAtMost(1f); moving = true }
            if (fade[k] > 0f) { fade[k] = (fade[k] - d / fadeMs).coerceAtLeast(0f); moving = true }
        }
        val result = pick.phase == FingerPick.Phase.RESULT
        if (result && !wasResult) startResult()
        wasResult = result
        if (result) {
            resultMs += d
            val total = (if (pick.mode == FingerPick.Mode.ONE) WIN_GROW_MS else RECOLOR_MS) * speedFactor
            recolor = (resultMs / total.coerceAtLeast(1f)).coerceIn(0f, 1f)
            if (recolor < 1f) moving = true
        }
        val c = pick.countNumber(SystemClock.uptimeMillis())
        if (c != shownCount) { shownCount = c; countMs = 0f } else countMs += d
        if (c > 0 && countMs < COUNT_POP_MS) moving = true
        invalidate()
        return moving
    }

    private fun startResult() {
        resultMs = 0f
        recolor = 0f
        val w = pick.winner
        if (w >= 0) {
            winR0 = baseR
            winRMax = hypot(max(px[w], width - px[w]), max(py[w], height - py[w])) + baseR
        }
        refreshHint()
    }

    /** 당첨 칸의 화면 좌표 (연출용) */
    fun slotX(slot: Int) = px[slot]
    fun slotY(slot: Int) = py[slot]

    fun colorOf(slot: Int): Int = FINGER_COLORS[slot % FINGER_COLORS.size]

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = minOf(w, h).toFloat()
        bigText.textSize = s * 0.34f
        bigOutline.textSize = bigText.textSize
        bigOutline.strokeWidth = bigText.textSize * 0.08f
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val phase = pick.phase
        val wave = sin(pulseMs / PULSE_MS * 2.0 * PI).toFloat()
        val ripple = (pulseMs % PULSE_MS) / PULSE_MS

        if (phase == FingerPick.Phase.RESULT && pick.mode == FingerPick.Mode.ONE && pick.winner >= 0) {
            drawWinner(c, w, h, wave)
            return
        }

        // 손가락이 하나도 없을 때: 안내 (가만히)
        if (pick.count == 0 && phase != FingerPick.Phase.RESULT) {
            drawIdle(c, w, h)
        }

        // 뗀 손가락: 줄어들며 사라짐
        for (k in 0 until n) if (pick.slotId[k] < 0 && fade[k] > 0f && !pick.participant[k]) {
            fill.color = colorOf(k)
            fill.alpha = (200 * fade[k]).toInt()
            c.drawCircle(px[k], py[k], baseR * fade[k], fill)
        }

        val team = phase == FingerPick.Phase.RESULT && pick.mode == FingerPick.Mode.TEAMS
        val fast = phase == FingerPick.Phase.COUNTDOWN
        for (k in 0 until n) {
            val held = pick.slotId[k] >= 0
            if (team) {
                if (pick.participant[k]) drawTeamFinger(c, k, wave)
                else if (held) drawLate(c, k)
                continue
            }
            if (!held) continue
            if (phase == FingerPick.Phase.RESULT) { drawLate(c, k); continue }
            val g = easeOut(grow[k])
            val beat = if (fast) 0.09f else 0.05f
            val r = baseR * g * (1f + beat * wave)
            val col = colorOf(k)
            // 퍼지는 물결 고리
            ring.color = col
            ring.alpha = (150 * (1f - ripple) * g).toInt()
            ring.strokeWidth = Theme.dpf(context, 3f)
            c.drawCircle(px[k], py[k], r * (1.05f + 0.75f * ripple), ring)
            // 빛
            fill.color = col
            fill.alpha = 70
            c.drawCircle(px[k], py[k], r * 1.28f, fill)
            fill.alpha = 255
            c.drawCircle(px[k], py[k], r, fill)
            ring.color = Color.WHITE
            ring.alpha = 230
            ring.strokeWidth = Theme.dpf(context, 4f)
            c.drawCircle(px[k], py[k], r * 0.82f, ring)
        }

        when (phase) {
            FingerPick.Phase.IDLE -> if (pick.count == 1) {
                subText.color = Theme.GOLD_LIGHT
                c.drawText("한 명 더 올려 주세요!", w / 2f, topSafe + subText.textSize * 1.4f, subText)
            }
            FingerPick.Phase.WAITING -> {
                // 위쪽 가운데: 기다림 원호
                val f = pick.waitFraction(SystemClock.uptimeMillis())
                val r = Theme.dpf(context, 16f)
                val cy = topSafe + r + Theme.dpf(context, 6f)
                arcRect.set(w / 2f - r, cy - r, w / 2f + r, cy + r)
                ring.color = 0x44FFFFFF
                ring.strokeWidth = Theme.dpf(context, 5f)
                c.drawCircle(w / 2f, cy, r, ring)
                ring.color = Theme.GOLD
                c.drawArc(arcRect, -90f, 360f * f, false, ring)
                subText.color = Theme.GOLD_LIGHT
                c.drawText("그대로 두세요…", w / 2f, cy + r + subText.textSize * 1.4f, subText)
            }
            FingerPick.Phase.COUNTDOWN -> drawCount(c, w, h)
            else -> drawTeamHeader(c, w) // RESULT (else: 할당 없는 when)
        }
    }

    private fun drawIdle(c: Canvas, w: Float, h: Float) {
        // 가운데 은은한 동심원 + 손가락 자리 표시
        val cx = w / 2f
        val cy = h * 0.52f
        ring.strokeWidth = Theme.dpf(context, 2f)
        for (k in 1..3) {
            ring.color = Theme.GOLD
            ring.alpha = 70 - k * 15
            c.drawCircle(cx, cy, baseR * (0.9f + k * 0.7f), ring)
        }
        for (k in 0 until 5) {
            val a = (-PI / 2 + (k - 2) * 0.42).toFloat()
            val x = cx + kotlin.math.cos(a) * baseR * 1.6f
            val y = cy + sin(a) * baseR * 1.6f
            fill.color = colorOf(k)
            fill.alpha = 150
            c.drawCircle(x, y, baseR * 0.32f, fill)
        }
        fill.color = Theme.GOLD
        fill.alpha = 255
        c.drawCircle(cx, cy, baseR * 0.42f, fill)
        labelText.textSize = Theme.dpf(context, 22f)
        labelText.color = Theme.GOLD
        c.drawText(hint, cx, cy + baseR * 2.6f, labelText)
        subText.color = Theme.TEXT_SUB
        c.drawText(hintSub, cx, cy + baseR * 2.6f + subText.textSize * 1.6f, subText)
        labelText.textSize = Theme.dpf(context, 16f)
    }

    private fun drawCount(c: Canvas, w: Float, h: Float) {
        val k = shownCount
        if (k <= 0) return
        val f = (countMs / COUNT_POP_MS).coerceIn(0f, 1f)
        val s = 1.5f - 0.5f * easeOut(f)
        c.save()
        c.scale(s, s, w / 2f, h / 2f)
        val y = h / 2f + bigText.textSize * 0.36f
        bigText.color = Theme.GOLD
        c.drawText(COUNT_TEXT[k], w / 2f, y, bigOutline)
        c.drawText(COUNT_TEXT[k], w / 2f, y, bigText)
        c.restore()
    }

    private fun drawWinner(c: Canvas, w: Float, h: Float, wave: Float) {
        val win = pick.winner
        val col = colorOf(win)
        val e = easeInOut(recolor)
        // 나머지 원: 줄어 사라짐 (커지기 앞부분 동안)
        val shrink = (1f - recolor * 2.5f).coerceIn(0f, 1f)
        if (shrink > 0f) for (k in 0 until n) if (k != win && pick.participant[k]) {
            fill.color = colorOf(k)
            fill.alpha = (255 * shrink).toInt()
            c.drawCircle(px[k], py[k], baseR * shrink, fill)
        }
        val r = winR0 + (winRMax - winR0) * e
        fill.color = col
        fill.alpha = 255
        c.drawCircle(px[win], py[win], r, fill)
        // 손가락 자리 고리
        val on = Theme.textOn(col)
        ring.color = on
        ring.alpha = 200
        ring.strokeWidth = Theme.dpf(context, 5f)
        c.drawCircle(px[win], py[win], baseR * (1f + 0.05f * wave), ring)
        ring.alpha = 90
        c.drawCircle(px[win], py[win], baseR * 1.45f, ring)
        if (recolor >= 1f) {
            // 손가락 반대쪽 높이에 "당첨!"
            val ty = if (py[win] > h / 2f) h * 0.36f else h * 0.78f
            bigText.color = on
            val old = bigText.textSize
            bigText.textSize = old * 0.62f
            bigOutline.textSize = bigText.textSize
            c.drawText(WIN_TEXT, w / 2f, ty, bigOutline)
            c.drawText(WIN_TEXT, w / 2f, ty, bigText)
            bigText.textSize = old
            bigOutline.textSize = old
            subText.color = on
            c.drawText(if (pick.allLifted) AGAIN_TEXT else LIFT_TEXT, w / 2f, ty + subText.textSize * 1.9f, subText)
        }
    }

    private fun drawTeamFinger(c: Canvas, k: Int, wave: Float) {
        val t = pick.team[k].coerceAtLeast(0)
        val col = lerpColor(colorOf(k), TEAM_COLORS[t], easeInOut(recolor))
        val r = baseR * (1f + 0.04f * wave)
        fill.color = col
        fill.alpha = 80
        c.drawCircle(px[k], py[k], r * 1.3f, fill)
        fill.alpha = 255
        c.drawCircle(px[k], py[k], r, fill)
        ring.color = Color.WHITE
        ring.alpha = 230
        ring.strokeWidth = Theme.dpf(context, 4f)
        c.drawCircle(px[k], py[k], r * 0.82f, ring)
        if (recolor >= 1f) {
            labelText.color = TEAM_LIGHT[t]
            c.drawText(TEAM_NAMES[t], px[k], py[k] - r * 1.45f, labelText)
        }
    }

    private fun drawLate(c: Canvas, k: Int) {
        ring.color = Theme.GRAY
        ring.alpha = 160
        ring.strokeWidth = Theme.dpf(context, 3f)
        c.drawCircle(px[k], py[k], baseR * 0.6f, ring)
    }

    private fun drawTeamHeader(c: Canvas, w: Float) {
        if (recolor < 1f) return
        // 위쪽 가운데 어두운 알약 (손가락 원 위에 겹쳐도 읽히게 맨 나중에 그린다)
        labelText.textSize = Theme.dpf(context, 21f)
        val y = topSafe + Theme.dpf(context, 30f)
        val gap = Theme.dpf(context, 36f)
        val dotR = Theme.dpf(context, 9f)
        val w0 = labelText.measureText(teamLine0)
        val w1 = labelText.measureText(teamLine1)
        val total = dotR * 2 + Theme.dpf(context, 8f) + w0 + gap + dotR * 2 + Theme.dpf(context, 8f) + w1
        var x = w / 2f - total / 2f
        val ph = Theme.dpf(context, 20f)
        arcRect.set(x - ph, y - labelText.textSize - Theme.dpf(context, 4f), x + total + ph, y + Theme.dpf(context, 12f))
        fill.color = PILL
        c.drawRoundRect(arcRect, arcRect.height() / 2f, arcRect.height() / 2f, fill)
        ring.color = Theme.GOLD
        ring.alpha = 200
        ring.strokeWidth = Theme.dpf(context, 2f)
        c.drawRoundRect(arcRect, arcRect.height() / 2f, arcRect.height() / 2f, ring)
        fill.alpha = 255
        fill.color = TEAM_COLORS[0]
        c.drawCircle(x + dotR, y - dotR * 0.8f, dotR, fill)
        x += dotR * 2 + Theme.dpf(context, 8f)
        labelText.color = TEAM_LIGHT[0]
        c.drawText(teamLine0, x + w0 / 2f, y, labelText)
        x += w0 + gap
        fill.color = TEAM_COLORS[1]
        c.drawCircle(x + dotR, y - dotR * 0.8f, dotR, fill)
        x += dotR * 2 + Theme.dpf(context, 8f)
        labelText.color = TEAM_LIGHT[1]
        c.drawText(teamLine1, x + w1 / 2f, y, labelText)
        labelText.textSize = Theme.dpf(context, 16f)
        subText.color = Theme.TEXT_SUB
        c.drawText(if (pick.allLifted) AGAIN_TEXT else LIFT_TEXT, w / 2f, height - subText.textSize * 0.9f, subText)
    }

    companion object {
        /** 연출 시간 (× 속도 배율) */
        const val GROW_MS = 220L
        const val FADE_MS = 260L
        const val WIN_GROW_MS = 1100L
        const val RECOLOR_MS = 600L
        /** 맥박 한 번 (배율 없음: 손가락이 있는 동안 계속) */
        const val PULSE_MS = 900f
        const val COUNT_POP_MS = 380f

        /** 손가락 색 10가지 (칸 번호 순) */
        val FINGER_COLORS = intArrayOf(
            0xFFFF5252.toInt(), 0xFF448AFF.toInt(), 0xFF00D27A.toInt(), 0xFFFFD600.toInt(), 0xFFB46CFF.toInt(),
            0xFFFF9F1C.toInt(), 0xFFFF6FB5.toInt(), 0xFF2EE6E6.toInt(), 0xFFB5F23D.toInt(), 0xFFF2F2F2.toInt(),
        )
        val TEAM_COLORS = intArrayOf(0xFFFF3D4F.toInt(), 0xFF2F6BFF.toInt())
        val TEAM_LIGHT = intArrayOf(0xFFFF9AA4.toInt(), 0xFF9DBBFF.toInt())
        val TEAM_NAMES = arrayOf("빨강 팀", "파랑 팀")
        val COUNT_TEXT = arrayOf("", "1", "2", "3")
        const val PILL = 0xDD0A0F2E.toInt()
        const val WIN_TEXT = "당첨!"
        const val LIFT_TEXT = "손가락을 모두 떼면 다시 할 수 있어요"
        const val AGAIN_TEXT = "다시 손가락을 올리면 새로 뽑아요"

        fun easeOut(f: Float): Float { val g = 1f - f; return 1f - g * g * g }
        fun easeInOut(f: Float): Float = if (f < 0.5f) 4f * f * f * f else { val g = -2f * f + 2f; 1f - g * g * g / 2f }

        fun lerpColor(a: Int, b: Int, f: Float): Int = Color.argb(
            255,
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }
}
