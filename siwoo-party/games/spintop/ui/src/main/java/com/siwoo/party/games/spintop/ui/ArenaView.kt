package com.siwoo.party.games.spintop.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.spintop.GameConfig
import com.siwoo.party.games.spintop.Phase
import com.siwoo.party.games.spintop.SpinTopGame
import com.siwoo.party.games.spintop.Top
import kotlin.math.cos
import kotlin.math.sin

/**
 * 경기장 (위에서 본 둥근 그릇). 팽이 위치는 SpinTopGame.arena 의 경기장 단위 좌표(가운데 = 0,0)를 그대로 그린다.
 * - 그릇(나뭇결 테두리 + 가운데로 갈수록 진한 경사)은 크기가 바뀔 때 비트맵으로 한 번 그려 둔다 (release 에서 recycle)
 * - 준비 중: 출발 자리에 준비를 마친 팽이(멈춤) / 아직인 자리(점선 고리), 지금 준비하는 사람 자리에 금색 고리
 * - 대결 중: 무늬가 회전력만큼 빠르게 돌고, 회전력이 낮으면 흔들흔들, 0 이 되면 비틀 → 넘어짐(회색) 연출
 * 시간은 Activity 가 넣는 게임 시계 [now]. 이 View 는 스스로 반복해서 그리지 않는다. onDraw 할당 없음.
 */
class ArenaView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var game: SpinTopGame? = null
    private var colors = IntArray(GameConfig.MAX_PLAYERS)

    /** 게임 시계 (프레임마다 Activity 가 넣음) */
    var now = 0L

    /** 경기장 단위 1 = unit 픽셀, 가운데 (cx, cy) */
    var unit = 1f
        private set
    var cx = 0f
        private set
    var cy = 0f
        private set
    private var bowlR = 0f
    private var bowlBmp: Bitmap? = null

    // ── 쓰러지는 연출 (팽이마다 시작 시각, -1 = 없음) ──
    private val fallStart = LongArray(GameConfig.MAX_PLAYERS) { -1L }
    private var fallMs = GameConfig.FALL_MS

    // ── 글자 ──
    private var roundText = ""
    private var timeText = ""

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = Theme.GOLD }
    private val labelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = 0xEE0B1340.toInt(); style = Paint.Style.STROKE
    }

    fun bind(g: SpinTopGame, playerColors: IntArray) {
        game = g
        colors = playerColors
        invalidate()
    }

    fun setRoundText(text: String) {
        if (roundText == text) return
        roundText = text
        invalidate()
    }

    fun setTimeText(text: String) {
        if (timeText == text) return
        timeText = text
        invalidate()
    }

    /** 팽이 i 가 쓰러지기 시작 (연출 시간 ms) */
    fun startFall(i: Int, ms: Long) {
        fallStart[i] = now
        fallMs = maxOf(1L, ms)
        invalidate()
    }

    /** 새 판: 쓰러짐 연출 지우기 */
    fun resetFalls() {
        fallStart.fill(-1L)
        invalidate()
    }

    /** 쓰러지는 연출이 진행 중인지 (프레임 루프 유지 판단) */
    fun animating(): Boolean {
        for (i in fallStart.indices) if (fallStart[i] >= 0L && now - fallStart[i] < fallMs) return true
        return false
    }

    /** 경기장 좌표 → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(cx + x * unit, cy + y * unit)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val m = 6f * d
        bowlR = (minOf(w, h) / 2f - m)
        cx = w / 2f
        cy = h / 2f
        // 그릇 테두리 두께만큼 빼고 경기장 반지름
        unit = bowlR * 0.9f / GameConfig.ARENA_R
        label.textSize = 15f * d
        labelStroke.textSize = 15f * d
        labelStroke.strokeWidth = 3.5f * d
        buildBowl(w, h)
    }

    private fun buildBowl(w: Int, h: Int) {
        bowlBmp?.recycle()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        // 그림자
        q.color = 0x66000000
        c.drawCircle(cx + 3f * d, cy + 5f * d, bowlR, q)
        // 나무 테두리
        q.color = SpinArt.WOOD_DARK
        c.drawCircle(cx, cy, bowlR, q)
        q.shader = RadialGradient(cx - bowlR * 0.2f, cy - bowlR * 0.25f, bowlR * 1.2f, SpinArt.WOOD, SpinArt.WOOD_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, bowlR * 0.97f, q)
        q.shader = null
        // 나뭇결 고리
        q.style = Paint.Style.STROKE
        q.strokeWidth = 1.2f * d
        q.color = 0x33FFFFFF
        c.drawCircle(cx, cy, bowlR * 0.94f, q)
        q.color = 0x22000000
        c.drawCircle(cx, cy, bowlR * 0.925f, q)
        // 바닥: 가운데로 갈수록 깊어 진함
        q.style = Paint.Style.FILL
        val ar = GameConfig.ARENA_R * unit
        q.shader = RadialGradient(cx, cy, ar, intArrayOf(SpinArt.BOWL_DEEP, SpinArt.mix(SpinArt.BOWL, SpinArt.BOWL_DEEP, 0.4f), SpinArt.BOWL),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, ar, q)
        q.shader = null
        // 경사 표시 (동심원) + 가운데 점
        q.style = Paint.Style.STROKE
        q.strokeWidth = 1f * d
        for (k in 1..4) {
            q.color = 0x26000000
            c.drawCircle(cx, cy, ar * k / 5f, q)
        }
        q.style = Paint.Style.FILL
        q.color = 0x40000000
        c.drawCircle(cx, cy, 3f * d, q)
        // 안쪽 테두리 (금색 선)
        q.style = Paint.Style.STROKE
        q.strokeWidth = 2.5f * d
        q.color = Theme.GOLD
        c.drawCircle(cx, cy, ar + 1.5f * d, q)
        // 바닥 안쪽 그림자 (테두리 밑)
        q.strokeWidth = 5f * d
        q.color = 0x33000000
        c.drawCircle(cx, cy, ar - 2.5f * d, q)
        bowlBmp = bmp
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        bowlBmp?.let { c.drawBitmap(it, 0f, 0f, bmpPaint) }
        val r = GameConfig.TOP_R * unit
        val arena = g.arena
        val prep = g.phase == Phase.READY || g.phase == Phase.MASH || g.phase == Phase.GAUGE || g.phase == Phase.SHOW || g.phase == Phase.LAUNCH
        val tops = g.tops
        // 쓰러진 팽이 먼저 (아래에)
        for (i in tops.indices) {
            val t = tops[i]
            if (t.fallen) drawFallen(c, t, r)
        }
        for (i in tops.indices) {
            val t = tops[i]
            if (t.fallen) continue
            val sx = cx + t.x * unit
            val sy = cy + t.y * unit
            if (prep && !t.launched) {
                if (g.prepped[i]) {
                    SpinArt.top(c, p, sx, sy, r, colors[i], 0.4f)
                } else {
                    ring.strokeWidth = 2f * d
                    ring.color = colors[i]
                    ring.alpha = 170
                    var a = 0f
                    while (a < 360f) {
                        c.drawArc(sx - r, sy - r, sx + r, sy + r, a, 18f, false, ring)
                        a += 36f
                    }
                    ring.alpha = 255
                }
                if (i == g.preparer && g.phase != Phase.LAUNCH) {
                    ring.strokeWidth = 3f * d
                    ring.color = Theme.GOLD
                    c.drawCircle(sx, sy, r + 5f * d, ring)
                }
                continue
            }
            // 도는 팽이: 회전력이 낮으면 흔들 (세차 운동)
            var ox = 0f
            var oy = 0f
            var squash = 1f
            var tilt = 0f
            if (t.power < GameConfig.WOBBLE_LEVEL && t.spinning) {
                val k = 1f - t.power / GameConfig.WOBBLE_LEVEL
                val ph = now * 0.012f
                ox = cos(ph) * r * 0.16f * k
                oy = sin(ph) * r * 0.16f * k
                squash = 1f - 0.18f * k
                tilt = Math.toDegrees(ph.toDouble()).toFloat()
            }
            SpinArt.top(c, p, sx + ox, sy + oy, r, colors[i], t.angle, squash = squash, tilt = tilt, blur = t.power)
            // 판이 끝났을 때 남은 팽이 = 승자 금색 고리
            if (arena.ended && t.spinning) {
                ring.strokeWidth = 3.5f * d
                ring.color = Theme.GOLD
                c.drawCircle(sx, sy, r + 5f * d, ring)
            }
        }
        if (roundText.isNotEmpty()) {
            c.drawText(roundText, 10f * d, 22f * d, labelStroke)
            c.drawText(roundText, 10f * d, 22f * d, label)
        }
        if (timeText.isNotEmpty()) {
            val x = width - 10f * d - label.measureText(timeText)
            c.drawText(timeText, x, 22f * d, labelStroke)
            c.drawText(timeText, x, 22f * d, label)
        }
    }

    /** 쓰러지는 연출: 앞 절반 크게 비틀, 뒤 절반 기울며 넘어가 회색으로 → 누운 팽이 */
    private fun drawFallen(c: Canvas, t: Top, r: Float) {
        val i = t.id
        val sx = cx + t.x * unit
        val sy = cy + t.y * unit
        val tipDeg = Math.toDegrees(t.tipAngle.toDouble()).toFloat()
        val st = fallStart[i]
        val f = if (st < 0L) 1f else ((now - st).toFloat() / fallMs).coerceIn(0f, 1f)
        if (f >= 1f) {
            SpinArt.fallen(c, p, sx + cos(t.tipAngle) * r * 0.35f, sy + sin(t.tipAngle) * r * 0.35f, r, colors[i], tipDeg)
            return
        }
        if (f < 0.5f) {
            // 비틀비틀: 흔들림이 점점 커짐
            val k = f / 0.5f
            val ph = f * 40f
            val amp = r * (0.15f + 0.25f * k)
            SpinArt.top(c, p, sx + cos(ph) * amp, sy + sin(ph) * amp, r, colors[i], t.angle + f * 6f,
                gray = 0.3f * k, squash = 1f - 0.25f * k, tilt = Math.toDegrees(ph.toDouble()).toFloat())
        } else {
            // 넘어감: 넘어가는 방향으로 납작해지며 회색
            val k = (f - 0.5f) / 0.5f
            val off = r * 0.35f * k
            SpinArt.top(c, p, sx + cos(t.tipAngle) * off, sy + sin(t.tipAngle) * off, r, colors[i], t.angle,
                gray = 0.3f + 0.7f * k, squash = 0.75f - 0.3f * k, tilt = tipDeg)
        }
    }

    fun release() {
        bowlBmp?.recycle()
        bowlBmp = null
    }
}
