package com.siwoo.party.games.jegi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.jegi.Flight
import com.siwoo.party.games.jegi.GameConfig
import com.siwoo.party.games.jegi.JegiGame
import com.siwoo.party.games.jegi.Phase
import kotlin.math.max
import kotlin.math.min

/**
 * 마당 (옆에서 본 장면): 하늘 · 돌담 · 감나무 · 흙마당 (배경은 크기가 바뀔 때 한 번 비트맵으로) +
 * 차기 시작선(점선) · 발 높이의 빛나는 판정 띠 · 제기와 그림자 · 한복 아이 · 큰 개수 · 판정 글자.
 * 제기 자리는 [game] 의 비행([Flight])을 [now](게임 시간)로 계산하고, 아이 미끄러짐·차기·글자 연출도 [now] 기준이라
 * 일시정지하면 모두 그 자리에 선다. onDraw 에서 객체를 만들지 않는다.
 */
class YardView(context: Context) : View(context) {

    /** 터치한 가로 자리 (마당 좌표) 를 알려 줌 */
    fun interface TapListener {
        fun onYardTap(worldX: Float)
    }

    var listener: TapListener? = null
    var game: JegiGame? = null

    /** 그릴 게임 시각 (activity 가 프레임마다 넣는다) */
    var now = 0L

    /** 판정 띠·차기 시작선 보이기 */
    var bandShown = false
    /** 아이 손에 든 제기 (던지기 전) */
    var jegiInHand = true
    var jegiShown = true

    private val d = Theme.dpf(context, 1f)

    // 크기·좌표
    private var u = 1f
    private var ox = 0f
    private var groundPx = 0f
    private var bg: Bitmap? = null

    // 아이
    private var kidColor = Theme.GOLD
    private var kidFrom = GameConfig.FOOT_START
    private var kidTo = GameConfig.FOOT_START
    private var slideStart = 0L
    private var slideMs = 1L
    private var kickStart = -100000L
    private var kickMs = 1L
    private var joy = false
    private var sad = false

    // 개수
    private var countText = "0"
    private var popStart = -100000L
    private var popMs = 1L

    // 판정 글자
    private var judgeText = ""
    private var judgeColor = Theme.GOLD
    private var judgeStart = -100000L
    private var judgeMs = 1L

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bandFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val countOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0B1340.toInt()
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val judgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val judgeOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0B1340.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = 0xCCFFFFFF.toInt() }

    // ── 좌표 ──

    /** 마당 x → 화면 px */
    fun screenX(wx: Float): Float = ox + wx * u

    /** 마당 높이 → 화면 px */
    fun screenY(wy: Float): Float = groundPx - wy * u

    /** 화면 px → 마당 x */
    fun worldX(px: Float): Float = (px - ox) / u

    /** 지금 그려지는 아이(발) 가로 자리 (마당 좌표) */
    fun kidX(): Float {
        val f = ((now - slideStart).toFloat() / slideMs).coerceIn(0f, 1f)
        val e = 1f - (1f - f) * (1f - f)
        return kidFrom + (kidTo - kidFrom) * e
    }

    /** 지금 그려지는 제기 높이 (마당 좌표, 테스트 확인용) */
    fun shownJegiY(): Float = game?.jegiY(now) ?: 0f

    // ── 상태 바꾸기 (activity 가 부름) ──

    fun setKidColor(color: Int) {
        kidColor = color
        invalidate()
    }

    /** 아이를 x 에 바로 세움 (새 도전) */
    fun resetKid(x: Float) {
        kidFrom = x
        kidTo = x
        slideStart = now
        kickStart = -100000L
        joy = false
        sad = false
        judgeStart = -100000L
        invalidate()
    }

    /** 발이 터치한 자리로 미끄러짐 */
    fun slideTo(x: Float, ms: Long) {
        val cur = kidX()
        kidFrom = cur
        kidTo = x
        slideStart = now
        slideMs = max(1L, ms)
        invalidate()
    }

    /** 다리를 들어 참 */
    fun kick(ms: Long) {
        kickStart = now
        kickMs = max(1L, ms)
        invalidate()
    }

    fun setMood(happy: Boolean, unhappy: Boolean) {
        joy = happy
        sad = unhappy
        invalidate()
    }

    fun setCount(n: Int, popMsIn: Long) {
        countText = if (n in NUMS.indices) NUMS[n] else n.toString()
        if (popMsIn > 0) {
            popStart = now
            popMs = popMsIn
        }
        invalidate()
    }

    /** 발 위에 떠오르는 판정 글자 */
    fun judge(text: String, color: Int, ms: Long) {
        judgeText = text
        judgeColor = color
        judgeStart = now
        judgeMs = max(1L, ms)
        invalidate()
    }

    /** 연출이 아직 진행 중인지 (프레임 루프를 계속 돌릴지) */
    fun busy(): Boolean =
        now - slideStart < slideMs || now - kickStart < kickMs || now - popStart < popMs || now - judgeStart < judgeMs

    /** 제기의 화면 자리 (연출 효과 위치) */
    fun jegiScreen(out: FloatArray) {
        val g = game
        if (g == null) {
            out[0] = 0f; out[1] = 0f
            return
        }
        out[0] = screenX(g.jegiX(now))
        out[1] = screenY(g.jegiY(now))
    }

    // ── 크기 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val below = h * BELOW_FRAC
        u = min(w / GameConfig.WORLD_W, (h - below) / GameConfig.WORLD_H)
        ox = (w - GameConfig.WORLD_W * u) / 2f
        groundPx = h - below
        countPaint.textSize = 15f * u
        countOutline.textSize = 15f * u
        countOutline.strokeWidth = 15f * u * 0.14f
        unitPaint.textSize = 5.5f * u
        judgePaint.textSize = 6.5f * u
        judgeOutline.textSize = 6.5f * u
        judgeOutline.strokeWidth = 6.5f * u * 0.18f
        labelPaint.textSize = 3.6f * u
        dash.strokeWidth = 0.7f * u
        dash.pathEffect = DashPathEffect(floatArrayOf(2f * u, 2f * u), 0f)
        buildBackground(w, h)
    }

    /** 배경 (하늘·담·감나무·흙마당) — 크기가 바뀔 때만 한 장 그려 둔다 */
    private fun buildBackground(w: Int, h: Int) {
        bg?.recycle()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, groundPx, 0xFF7EC8F2.toInt(), 0xFFDDF1FB.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), groundPx, p)
        p.shader = null
        // 구름
        p.color = 0xCCFFFFFF.toInt()
        cloud(c, p, screenX(62f), screenY(80f), u)
        cloud(c, p, screenX(30f), screenY(64f), u * 0.75f)
        // 먼 산
        p.color = 0xFF9CC7A8.toInt()
        val wallTop = groundPx - WALL_H * u
        c.drawCircle(screenX(20f), wallTop + 30f * u, 38f * u, p)
        p.color = 0xFF8BBA98.toInt()
        c.drawCircle(screenX(96f), wallTop + 40f * u, 46f * u, p)
        // 돌담
        JegiArt.wall(c, p, 0f, w.toFloat(), groundPx, WALL_H * u, u)
        // 감나무 가지
        // 오른쪽 위 모서리에서 왼쪽으로 뻗게 좌우를 뒤집어 그림
        c.save()
        c.scale(-1f, 1f, w / 2f, 0f)
        JegiArt.persimmon(c, p, w - screenX(GameConfig.WORLD_W + 6f), screenY(86f), u)
        c.restore()
        // 흙마당
        p.shader = LinearGradient(0f, groundPx, 0f, h.toFloat(), 0xFFE2C48E.toInt(), 0xFFC9A46A.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, groundPx, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFB98F57.toInt()
        c.drawRect(0f, groundPx, w.toFloat(), groundPx + 0.8f * u, p)
        // 자갈
        p.color = 0x66FFFFFF
        for (k in 0 until 14) {
            val x = screenX(4f + k * 8.7f + (k % 3) * 1.3f)
            val y = groundPx + (2f + (k * 7 % 5) * 1.3f) * u
            c.drawCircle(x, y, (0.5f + (k % 3) * 0.25f) * u, p)
        }
        bg = b
    }

    private fun cloud(c: Canvas, p: Paint, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, 5f * s, p)
        c.drawCircle(x + 6f * s, y + 1.5f * s, 4f * s, p)
        c.drawCircle(x - 6f * s, y + 1.8f * s, 3.6f * s, p)
        c.drawRect(x - 6f * s, y + 1.5f * s, x + 6f * s, y + 5.4f * s, p)
    }

    fun release() {
        bg?.recycle()
        bg = null
    }

    /** 테스트용: 배경이 비트맵으로 준비됐는지 */
    val backgroundCached: Boolean get() = bg != null

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val b = bg ?: return
        c.drawBitmap(b, 0f, 0f, null)
        val g = game
        val kx = kidX()
        val sx = screenX(kx)

        // 큰 개수 (왼쪽 위, 제기 뒤)
        drawCount(c)

        val flying = g != null && g.phase == Phase.FLYING
        if (bandShown && g != null && flying) drawReadyLine(c, kx)

        // 제기 그림자
        if (g != null && jegiShown && !jegiInHand) {
            val jy = g.jegiY(now)
            val jx = screenX(g.jegiX(now))
            val k = (1f - jy / GameConfig.APEX_Y).coerceIn(0.25f, 1f)
            fill.color = 0x44000000
            rect.set(jx - 3.6f * u * k, groundPx - 1f * u * k, jx + 3.6f * u * k, groundPx + 1f * u * k)
            c.drawOval(rect, fill)
        }

        // 아이
        val kf = kickPose()
        val armsUp = if (joy) 1f else 0.25f + 0.6f * kf
        JegiArt.kid(c, art, sx, groundPx, u, kidColor, kf, armsUp, joy, sad)
        // 판정 띠는 아이 위에 반투명으로
        if (bandShown && g != null && flying) drawBand(c, g, sx)

        // 제기
        if (g != null && jegiShown) {
            if (jegiInHand) {
                // 오른손 근처
                JegiArt.jegi(c, art, sx + 15f * u, groundPx - 25f * u, u * JEGI_SCALE, 1f, 0.2f, 0f, false)
            } else {
                val f = g.flight
                val jx = screenX(g.jegiX(now))
                val jyw = g.jegiY(now)
                val jy = screenY(jyw)
                val lying = jyw <= 0.01f && now >= f.groundTime
                // 술 방향: 오르는 중 → 아래로, 떨어지는 중 → 위로 (꼭대기 부근에서 부드럽게)
                val vy = jyw - g.jegiY(now - 60L)
                val up = (-vy * 0.6f).coerceIn(-1f, 1f).let { if (it > -0.15f && it < 0.15f) (if (it >= 0f) 0.15f else -0.15f) else it }
                val tilt = ((g.jegiX(now) - g.jegiX(now - 80L)) * -0.5f).coerceIn(-1f, 1f)
                JegiArt.jegi(c, art, jx, if (lying) groundPx - 2f * u else jy, u * JEGI_SCALE, up, tilt, now * 0.012f, lying)
            }
        }

        // 판정 글자
        val jf = (now - judgeStart).toFloat() / judgeMs
        if (jf in 0f..1f && judgeText.isNotEmpty()) {
            val a = if (jf < 0.7f) 255 else ((1f - jf) / 0.3f * 255).toInt().coerceIn(0, 255)
            // 아이 머리 위
            val y = screenY(JUDGE_Y + 6f * jf)
            val x = sx.coerceIn(28f * u + ox, ox + (GameConfig.WORLD_W - 28f) * u)
            judgeOutline.alpha = a
            judgePaint.color = judgeColor
            judgePaint.alpha = a
            c.drawText(judgeText, x, y, judgeOutline)
            c.drawText(judgeText, x, y, judgePaint)
        }
    }

    /** 차는 다리 들기 (0 → 1 → 0) */
    private fun kickPose(): Float {
        val f = (now - kickStart).toFloat() / kickMs
        if (f < 0f || f > 1f) return 0f
        return if (f < 0.3f) f / 0.3f else 1f - (f - 0.3f) / 0.7f
    }

    /** 차기 시작선 (이 아래로 내려와야 차기 — 위에서 터치하면 발만 옮김). 글자는 아이 반대쪽에 */
    private fun drawReadyLine(c: Canvas, kidWorldX: Float) {
        val ry = screenY(READY_Y)
        dash.color = 0xB3FFFFFF.toInt()
        c.drawLine(ox + 2f * u, ry, ox + (GameConfig.WORLD_W - 2f) * u, ry, dash)
        if (kidWorldX < GameConfig.WORLD_W / 2f) {
            labelPaint.textAlign = Paint.Align.RIGHT
            c.drawText(READY_LABEL, ox + (GameConfig.WORLD_W - 2f) * u, ry - 1.2f * u, labelPaint)
        } else {
            labelPaint.textAlign = Paint.Align.LEFT
            c.drawText(READY_LABEL, ox + 2f * u, ry - 1.2f * u, labelPaint)
        }
    }

    /** 발 높이 판정 띠 (구간이 좁아지면 띠도 얇아짐) */
    private fun drawBand(c: Canvas, g: JegiGame, sx: Float) {
        // 발 높이 판정 띠 (구간이 좁아지면 띠도 얇아짐)
        val half = Flight.bandHalf(g.window())
        val top = screenY(GameConfig.KICK_Y + half)
        val bot = screenY(GameConfig.KICK_Y - half)
        val l = sx - GameConfig.REACH * u
        val r = sx + GameConfig.REACH * u
        rect.set(l - 1.5f * u, top - 1.5f * u, r + 1.5f * u, bot + 1.5f * u)
        bandFill.color = 0x26FFE08A
        c.drawRoundRect(rect, 3f * u, 3f * u, bandFill)
        rect.set(l, top, r, bot)
        bandFill.color = 0x40FFC93C
        c.drawRoundRect(rect, 2f * u, 2f * u, bandFill)
        stroke.color = Theme.GOLD
        stroke.strokeWidth = 0.8f * u
        c.drawRoundRect(rect, 2f * u, 2f * u, stroke)
        // 딱 좋은 높이
        stroke.color = 0xEEFFFFFF.toInt()
        stroke.strokeWidth = 0.5f * u
        val ky = screenY(GameConfig.KICK_Y)
        c.drawLine(l + 1f * u, ky, r - 1f * u, ky, stroke)
    }

    private fun drawCount(c: Canvas) {
        val pf = (now - popStart).toFloat() / popMs
        val sc = if (pf in 0f..1f) 1f + 0.35f * (1f - pf) else 1f
        val x = ox + 5f * u
        val y = 19f * u
        c.save()
        c.scale(sc, sc, x, y - 6f * u)
        countPaint.color = Theme.GOLD
        c.drawText(countText, x, y, countOutline)
        c.drawText(countText, x, y, countPaint)
        val tw = countPaint.measureText(countText)
        c.drawText(UNIT, x + tw + 1.5f * u, y, unitPaint)
        c.restore()
    }

    // ── 터치 ──

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> listener?.onYardTap(worldX(e.x))
            MotionEvent.ACTION_POINTER_DOWN -> listener?.onYardTap(worldX(e.getX(e.actionIndex)))
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        /** 땅 아래 흙마당 비율 */
        private const val BELOW_FRAC = 0.07f
        /** 돌담 높이 (칸) */
        private const val WALL_H = 19f
        /** 제기를 마당 칸보다 크게 (잘 보이게) */
        private const val JEGI_SCALE = 1.45f
        /** 판정 글자 높이 (칸, 아이 머리 위) */
        private const val JUDGE_Y = 57f
        /** 차기 시작선 높이 (칸): 딱 좋은 순간 KICK_ZONE_MS 전의 높이 */
        val READY_Y: Float = GameConfig.KICK_Y + Flight.FALL_SPEED * GameConfig.KICK_ZONE_MS
        private const val READY_LABEL = "↓ 여기부터 차기"
        private const val UNIT = "개"
        private val NUMS = Array(GameConfig.MAX_KICKS + 1) { it.toString() }
    }
}
