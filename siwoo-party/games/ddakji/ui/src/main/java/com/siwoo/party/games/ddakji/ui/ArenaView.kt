package com.siwoo.party.games.ddakji.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.ddakji.DdakjiGame
import com.siwoo.party.games.ddakji.GameConfig
import com.siwoo.party.games.ddakji.Phase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 딱지치기 마당: 흙바닥 + 분필 원 + 바닥의 상대(수비) 딱지 + 위에 든 공격자 딱지 + 힘 막대 게이지(왼쪽) + 각도 부채 게이지(오른쪽).
 * 연출(들어 올렸다 내리치기 → 들썩 → 뒤집힘 → 가져가기)과 게이지는 모두 게임 시계 [now] 로 계산한다.
 * Activity 의 프레임 루프가 now 를 넣고 invalidate — 이 View 는 스스로 반복해서 그리지 않는다. onDraw 할당 없음.
 */
class ArenaView(context: Context) : View(context) {

    fun interface Listener {
        fun onTap()
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: DdakjiGame? = null
    private var colors = IntArray(GameConfig.PLAYERS)

    /** 게임 시계 (프레임마다 Activity 가 넣음) */
    var now = 0L

    /** 게이지가 움직이는 중인지 (공격 차례 입력 단계) */
    var gaugeLive = false
        set(v) { field = v; invalidate() }

    /** 게이지를 보일지 (공격 중 ~ 결과까지) */
    var gaugeShown = false
        set(v) { field = v; invalidate() }

    private var hint = ""
    private var chanceText = ""

    // ── 연출 단계 시작 시각 (-1 = 아직) ──
    private var strikeStart = -1L
    private var strikeMs = 1L
    private var wobbleStart = -1L
    private var wobbleMs = 1L
    private var flipStart = -1L
    private var flipMs = 1L
    private var takeStart = -1L
    private var takeMs = 1L
    private var takeDir = 1f

    // ── 배치 ──
    private var tx = 0f
    private var ty = 0f
    private var size = 0f
    private var hoverX = 0f
    private var hoverY = 0f
    private val bar = RectF()
    private var arcCx = 0f
    private var arcCy = 0f
    private var arcR = 0f
    private val arcBox = RectF()

    // ── 그리기 도구 ──
    private val ground = Paint()
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val hintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xCC2A1A08.toInt(); style = Paint.Style.STROKE }
    private val chancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val tri = Path()
    private val pebX = FloatArray(PEBBLES)
    private val pebY = FloatArray(PEBBLES)
    private val pebR = FloatArray(PEBBLES)

    init {
        val r = Random(11)
        for (k in 0 until PEBBLES) {
            pebX[k] = r.nextFloat()
            pebY[k] = r.nextFloat()
            pebR[k] = 0.8f + r.nextFloat() * 1.8f
        }
    }

    fun bind(g: DdakjiGame, playerColors: IntArray) {
        game = g
        colors = playerColors
        invalidate()
    }

    fun setHint(text: String) {
        if (hint == text) return
        hint = text
        invalidate()
    }

    fun setChance(text: String) {
        if (chanceText == text) return
        chanceText = text
        invalidate()
    }

    // ── 연출 ──

    fun startStrike(ms: Long) { strikeStart = now; strikeMs = maxOf(1L, ms); invalidate() }
    fun startWobble(ms: Long) { wobbleStart = now; wobbleMs = maxOf(1L, ms); invalidate() }
    fun startFlip(ms: Long) { flipStart = now; flipMs = maxOf(1L, ms); invalidate() }
    /** 뒤집힌 딱지를 공격자 쪽(dir: -1 왼쪽, +1 오른쪽)으로 */
    fun startTake(ms: Long, dir: Float) { takeStart = now; takeMs = maxOf(1L, ms); takeDir = dir; invalidate() }

    /** 새 공격: 딱지들을 제자리로 */
    fun resetShots() {
        strikeStart = -1L; wobbleStart = -1L; flipStart = -1L; takeStart = -1L
        chanceText = ""
        invalidate()
    }

    private fun running(start: Long, ms: Long) = start >= 0L && now - start < ms

    /** 연출이 진행 중인지 (프레임 루프 유지 판단) */
    fun busy(): Boolean = running(strikeStart, strikeMs) || running(wobbleStart, wobbleMs) ||
        running(flipStart, flipMs) || running(takeStart, takeMs)

    /** 내리친 자리 (이 View 좌표) */
    fun impactX(): Float = tx
    fun impactY(): Float = ty

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        ground.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF9A7650.toInt(), 0xFF7A5A3A.toInt(), 0xFF5E4430.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        // 왼쪽 힘 막대
        val bw = 24f * d
        bar.set(16f * d, h * 0.2f, 16f * d + bw, h - 26f * d)
        // 오른쪽 아래 각도 부채 (가로 → 세로 사분원)
        arcR = minOf(h * 0.36f, w * 0.27f)
        arcCx = w - 18f * d
        arcCy = h - 26f * d
        arcBox.set(arcCx - arcR, arcCy - arcR, arcCx + arcR, arcCy + arcR)
        band.strokeWidth = 16f * d
        // 딱지 자리: 막대와 부채 사이 가운데
        val left = bar.right + 10f * d
        val right = arcCx - arcR - 6f * d
        size = minOf((right - left) * 0.27f, h * 0.16f)
        tx = (left + right) / 2f + size * 0.25f
        ty = h * 0.66f
        hoverX = tx + size * 0.35f
        hoverY = h * 0.3f
        hintPaint.textSize = 15f * d
        hintStroke.textSize = 15f * d
        hintStroke.strokeWidth = 3.5f * d
        chancePaint.textSize = 17f * d
        label.textSize = 12f * d
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val g = game ?: return
        c.drawRect(0f, 0f, w, h, ground)
        fill.color = 0x33000000
        for (k in 0 until PEBBLES) c.drawCircle(pebX[k] * w, pebY[k] * h, pebR[k] * d, fill)
        // 분필 원
        line.color = 0xBBFFFFFF.toInt()
        line.strokeWidth = 2.5f * d
        c.drawOval(tx - size * 1.9f, ty - size * 0.9f, tx + size * 1.9f, ty + size * 1.0f, line)

        val att = g.attacker
        val def = g.defender
        drawTarget(c, colors[def])
        drawAttacker(c, colors[att])
        if (gaugeShown) {
            drawPower(c, g)
            drawAngle(c, g)
        }
        if (hint.isNotEmpty()) {
            c.drawText(hint, w / 2f, 24f * d, hintStroke)
            c.drawText(hint, w / 2f, 24f * d, hintPaint)
        }
        if (chanceText.isNotEmpty()) {
            // 아래쪽 (힘 막대와 각도 부채 사이) — 들어 올리는 딱지에 가리지 않게
            val cx = (bar.right + arcCx - arcR) / 2f
            val cy = h - 12f * d
            hintStroke.textSize = chancePaint.textSize
            c.drawText(chanceText, cx, cy, hintStroke)
            hintStroke.textSize = hintPaint.textSize
            c.drawText(chanceText, cx, cy, chancePaint)
        }
    }

    /** 바닥의 수비 딱지: 내리치면 들썩 → (뒤집히면) 공중제비 → 공격자 쪽으로 날아감 */
    private fun drawTarget(c: Canvas, color: Int) {
        var x = tx
        var y = ty
        var sq = REST_SQUASH
        var tilt = 0f
        var alpha = 255
        var s = size
        if (takeStart >= 0L) {
            val f = ((now - takeStart).toFloat() / takeMs).coerceIn(0f, 1f)
            if (f >= 1f) return
            val e = f * f
            x = tx + (if (takeDir < 0f) -tx - size else width - tx + size) * e
            y = ty - size * 1.2f - sin(f * PI.toFloat()) * size * 0.8f
            sq = -REST_SQUASH
            s = size * (1f - 0.35f * f)
            tilt = takeDir * 25f * f
            alpha = (255 * (1f - 0.4f * f)).toInt()
        } else if (flipStart >= 0L) {
            val f = ((now - flipStart).toFloat() / flipMs).coerceIn(0f, 1f)
            val lift = if (f < 1f) sin(f * PI.toFloat()) * size * 1.6f else 0f
            y = ty - lift
            sq = cos(f * PI.toFloat()) * REST_SQUASH
            if (abs(sq) < 0.05f) sq = if (f < 0.5f) 0.05f else -0.05f
            tilt = 18f * sin(f * PI.toFloat())
            if (f >= 1f) { y = ty; sq = -REST_SQUASH; tilt = 0f }
        } else if (wobbleStart >= 0L) {
            val f = ((now - wobbleStart).toFloat() / wobbleMs).coerceIn(0f, 1f)
            val damp = 1f - f
            y = ty - abs(sin(f * PI.toFloat() * 4f)) * size * 0.28f * damp
            tilt = sin(f * PI.toFloat() * 6f) * 9f * damp
            sq = REST_SQUASH + sin(f * PI.toFloat() * 5f) * 0.12f * damp
        }
        // 그림자
        fill.color = 0x44000000
        val sh = s * (1f - (ty - y) / (size * 4f)).coerceIn(0.5f, 1f)
        c.drawOval(x - sh * 1.05f, ty + size * 0.1f, x + sh * 1.05f, ty + size * 0.75f, fill)
        DdakjiArt.draw(c, art, x, y, s, sq, color, tilt, size * 0.1f, alpha)
    }

    /** 공격자 딱지: 위에 들고 있다가 들어 올려 내리친다 */
    private fun drawAttacker(c: Canvas, color: Int) {
        var x = hoverX
        var y = hoverY
        var sq = 0.75f
        var tilt = -10f
        var shadow = 0.5f
        if (strikeStart >= 0L) {
            val f = ((now - strikeStart).toFloat() / strikeMs).coerceIn(0f, 1f)
            if (f < RISE) {
                val e = f / RISE
                val o = 1f - (1f - e) * (1f - e)
                y = hoverY - size * 0.7f * o
                tilt = -10f - 22f * o
                x = hoverX + size * 0.15f * o
            } else {
                val e = (f - RISE) / (1f - RISE)
                val q = e * e
                val restX = tx + size * 0.55f
                val restY = ty - size * 0.32f
                x = hoverX + size * 0.15f + (restX - hoverX - size * 0.15f) * q
                y = hoverY - size * 0.7f + (restY - hoverY + size * 0.7f) * q
                tilt = -32f + 40f * q
                sq = 0.75f + (REST_SQUASH - 0.75f) * q
                shadow = 0.5f + 0.5f * q
            }
        }
        fill.color = 0x33000000
        c.drawOval(x - size * shadow, ty + size * 0.15f, x + size * shadow, ty + size * 0.6f, fill)
        DdakjiArt.draw(c, art, x, y, size, sq, color, tilt, size * 0.1f)
    }

    private fun drawPower(c: Canvas, g: DdakjiGame) {
        val live = gaugeLive && g.phase == Phase.POWER
        val v = if (live) g.powerAt(now) else if (g.phase == Phase.POWER) 0f else g.power
        val active = g.phase == Phase.POWER
        val r = bar.width() / 2f
        fill.color = 0xAA1A1030.toInt()
        c.drawRoundRect(bar, r, r, fill)
        val bh = bar.height()
        // 채움
        val fy = bar.bottom - bh * v
        fill.color = if (active) 0xCCFF6B3D.toInt() else 0x88FF6B3D.toInt()
        c.drawRect(bar.left + 4f * d, fy, bar.right - 4f * d, bar.bottom - 4f * d, fill)
        // 최적 구간 (채움 위에 반투명 금색 + 테두리 — 늘 보이게)
        val s0 = bar.bottom - bh * (GameConfig.POWER_SWEET + GameConfig.POWER_SWEET_HALF)
        val s1 = bar.bottom - bh * (GameConfig.POWER_SWEET - GameConfig.POWER_SWEET_HALF)
        fill.color = 0x40FFC93C
        c.drawRect(bar.left, s0 - bh * 0.06f, bar.right, s1 + bh * 0.06f, fill)
        fill.color = 0x99FFC93C.toInt()
        c.drawRect(bar.left, s0, bar.right, s1, fill)
        line.color = Theme.GOLD
        line.strokeWidth = 2f * d
        c.drawRect(bar.left, s0, bar.right, s1, line)
        line.color = if (active) Theme.GOLD_LIGHT else 0x88FFFFFF.toInt()
        line.strokeWidth = 2f * d
        c.drawRoundRect(bar, r, r, line)
        // 표시 삼각형
        fill.color = if (active) Theme.TEXT else Theme.GOLD
        tri.reset()
        tri.moveTo(bar.right + 2f * d, fy)
        tri.lineTo(bar.right + 12f * d, fy - 7f * d)
        tri.lineTo(bar.right + 12f * d, fy + 7f * d)
        tri.close()
        c.drawPath(tri, fill)
        label.color = if (active) Theme.GOLD else Theme.TEXT_SUB
        c.drawText(POWER_LABEL, bar.centerX() + 4f * d, bar.top - 9f * d, label)
    }

    private fun drawAngle(c: Canvas, g: DdakjiGame) {
        val live = gaugeLive && g.phase == Phase.ANGLE
        val active = g.phase == Phase.ANGLE
        val done = g.phase == Phase.STRIKE || g.phase == Phase.OVER
        val v = if (live) g.angleAt(now) else if (done) g.angle else 0f
        band.color = 0xAA1A1030.toInt()
        c.drawArc(arcBox, 180f, 90f, false, band)
        band.color = 0x66FFC93C
        c.drawArc(arcBox, 180f + 90f * (GameConfig.ANGLE_SWEET - GameConfig.ANGLE_SWEET_HALF - 0.06f), 90f * (GameConfig.ANGLE_SWEET_HALF + 0.06f) * 2f, false, band)
        band.color = Theme.GOLD
        c.drawArc(arcBox, 180f + 90f * (GameConfig.ANGLE_SWEET - GameConfig.ANGLE_SWEET_HALF), 90f * GameConfig.ANGLE_SWEET_HALF * 2f, false, band)
        // 바늘
        if (active || done) {
            val a = (PI + PI / 2.0 * v).toFloat()
            line.color = if (active) Theme.TEXT else Theme.GOLD
            line.strokeWidth = 4f * d
            c.drawLine(arcCx, arcCy, arcCx + cos(a) * (arcR + 12f * d), arcCy + sin(a) * (arcR + 12f * d), line)
        }
        fill.color = if (active) Theme.GOLD_LIGHT else 0xAAFFFFFF.toInt()
        c.drawCircle(arcCx, arcCy, 6f * d, fill)
        label.color = if (active) Theme.GOLD else Theme.TEXT_SUB
        c.drawText(ANGLE_LABEL, arcCx - arcR * 0.45f, arcCy - 8f * d, label)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            listener?.onTap()
            return true
        }
        return event.actionMasked == MotionEvent.ACTION_UP || super.onTouchEvent(event)
    }

    companion object {
        private const val PEBBLES = 40
        private const val REST_SQUASH = 0.5f
        /** 내리치기 중 들어 올리는 비율 */
        private const val RISE = 0.4f
        private const val POWER_LABEL = "① 힘"
        private const val ANGLE_LABEL = "② 각도"
    }
}
