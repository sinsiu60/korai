package com.siwoo.party.games.alkkagi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.alkkagi.AlkkagiGame
import com.siwoo.party.games.alkkagi.GameConfig
import com.siwoo.party.games.alkkagi.Phase
import com.siwoo.party.games.alkkagi.Stone
import com.siwoo.party.games.alkkagi.StoneState
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 알까기 판 (나무 바둑판 + 광택 알 + 조준 표시). 알 위치는 AlkkagiGame 의 판 단위 좌표를 그대로 그린다.
 * - 나무·격자·화점은 크기가 바뀔 때 RGB_565 비트맵으로 한 번 그려 두고(release 에서 recycle) 매 프레임 붙이기만 한다
 * - 알 광택은 플레이어 색마다 RadialGradient 하나를 크기가 바뀔 때 만들고, translate 해서 재사용
 * - 내 알을 누르고 뒤로 끌면: 고무줄 + 날아갈 방향 점선 + 힘 게이지(알 둘레 고리·%) → 놓으면 listener.onFlick
 * 움직임은 화면(Activity)의 프레임 루프가 game.advance 후 invalidate 한다 — 이 View 는 스스로 반복해서 그리지 않는다.
 */
class BoardView(context: Context) : View(context) {

    interface Listener {
        /** 지금 조준을 시작해도 되는지 (내 차례·일시정지 아님) */
        fun canAim(): Boolean
        /** 알 id 를 (dragX, dragY) 만큼(판 단위) 끌었다 놓음 */
        fun onFlick(id: Int, dragX: Float, dragY: Float)
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: AlkkagiGame? = null
    private val colors = IntArray(GameConfig.PLAYERS)

    // ── 배치 (onSizeChanged) ──
    /** 판 단위 1 = unitPx 픽셀, 판 왼쪽 위 = (ox, oy) */
    var unitPx = 1f
        private set
    private var ox = 0f
    private var oy = 0f
    private var thick = 0f
    private var boardBmp: Bitmap? = null
    private val stoneShader = arrayOfNulls<RadialGradient>(GameConfig.PLAYERS)

    // ── 조준 ──
    /** 끌고 있는 알 (−1 = 없음) */
    var aimId = -1
        private set
    private var downX = 0f
    private var downY = 0f
    var dragX = 0f
        private set
    var dragY = 0f
        private set

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val stonePaint = Array(GameConfig.PLAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val labelOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xCC000000.toInt()
    }
    private val rect = RectF()
    private val path = Path()

    init {
        isClickable = true
    }

    /** 게임과 플레이어 색 연결 */
    fun bind(game: AlkkagiGame, playerColors: IntArray) {
        this.game = game
        for (i in colors.indices) colors[i] = playerColors[i]
        if (width > 0) makeShaders()
        invalidate()
    }

    /** 판 단위 (x, y) → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(ox + x * unitPx, oy + y * unitPx)
    }

    /** 알 중심 → 이 View 좌표 */
    fun stoneCenter(id: Int, out: PointF) {
        val s = game?.stones?.get(id) ?: return
        toScreen(s.x, s.y, out)
    }

    /** 조준 취소 (일시정지 등) */
    fun cancelAim() {
        if (aimId < 0) return
        aimId = -1
        invalidate()
    }

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val margin = 12f * d
        thick = 7f * d
        unitPx = minOf((w - 2f * margin) / GameConfig.BOARD_W, (h - 2f * margin - thick) / GameConfig.BOARD_H)
        ox = (w - GameConfig.BOARD_W * unitPx) / 2f
        oy = (h - GameConfig.BOARD_H * unitPx - thick) / 2f
        rim.strokeWidth = 1.2f * d
        ring.strokeWidth = 1.6f * d
        band.strokeWidth = 3f * d
        dots.strokeWidth = 3.2f * d
        frame.strokeWidth = 3f * d
        label.textSize = 15f * d
        labelOutline.textSize = 15f * d
        labelOutline.strokeWidth = 3.5f * d
        makeShaders()
        buildBoard()
    }

    /** 플레이어 색마다 광택 그라데이션 하나 (원점 기준 — 그릴 때 translate) */
    private fun makeShaders() {
        val r = GameConfig.STONE_R * unitPx
        for (i in colors.indices) {
            val c = colors[i]
            val g = RadialGradient(
                -r * 0.35f, -r * 0.42f, r * 1.45f,
                intArrayOf(StoneArt.shade(c, 1.75f), StoneArt.shade(c, 1.12f), c, StoneArt.shade(c, 0.5f)),
                floatArrayOf(0f, 0.22f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            stoneShader[i] = g
            stonePaint[i].shader = g
        }
    }

    /** 나무 윗면 + 나뭇결 + 격자 + 화점 + 앞쪽 옆면을 비트맵 하나에 */
    private fun buildBoard() {
        release()
        val k = unitPx
        val bw = ceil(GameConfig.BOARD_W * k).toInt().coerceAtLeast(1)
        val topH = GameConfig.BOARD_H * k
        val bh = ceil(topH + thick).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 앞쪽 옆면 (어두운 나무)
        p.shader = LinearGradient(0f, topH, 0f, topH + thick, StoneArt.WOOD_SIDE, StoneArt.shade(StoneArt.WOOD_SIDE, 0.7f), Shader.TileMode.CLAMP)
        c.drawRect(0f, topH, bw.toFloat(), bh.toFloat(), p)
        // 윗면
        p.shader = LinearGradient(
            0f, 0f, bw.toFloat(), topH,
            intArrayOf(0xFFE8BD78.toInt(), StoneArt.WOOD, 0xFFD9A55C.toInt(), 0xFFE4B672.toInt()),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, bw.toFloat(), topH, p)
        p.shader = null
        // 나뭇결: 가로로 흐르는 물결선 (고정 난수 → 늘 같은 무늬)
        val rnd = Random(17)
        p.style = Paint.Style.STROKE
        val grain = Path()
        var y = rnd.nextFloat() * k
        while (y < topH) {
            p.strokeWidth = (0.6f + rnd.nextFloat() * 1.6f) * d
            p.color = if (rnd.nextInt(3) == 0) 0x1EFFF0D0 else (0x16 + rnd.nextInt(0x16) shl 24) or 0x6B3A10
            grain.reset()
            grain.moveTo(0f, y)
            var x = 0f
            val amp = (0.1f + rnd.nextFloat() * 0.35f) * k
            while (x < bw) {
                val seg = (2f + rnd.nextFloat() * 3f) * k
                grain.quadTo(x + seg / 2f, y + (if (rnd.nextBoolean()) amp else -amp), x + seg, y + (rnd.nextFloat() - 0.5f) * 0.2f * k)
                x += seg
            }
            c.drawPath(grain, p)
            y += (0.25f + rnd.nextFloat() * 0.9f) * k
        }
        // 격자 (가장자리에서 한 칸 안쪽부터)
        p.color = 0xD93A2410.toInt()
        p.strokeWidth = maxOf(1f, 0.9f * d)
        val step = GameConfig.GRID_STEP
        val gx1 = (GameConfig.BOARD_W - step) * k
        val gy1 = (GameConfig.BOARD_H - step) * k
        var g = step
        while (g <= GameConfig.BOARD_W - step + 0.01f) { c.drawLine(g * k, step * k, g * k, gy1, p); g += step }
        g = step
        while (g <= GameConfig.BOARD_H - step + 0.01f) { c.drawLine(step * k, g * k, gx1, g * k, p); g += step }
        // 바깥 격자선은 조금 굵게
        p.strokeWidth = maxOf(1.5f, 1.8f * d)
        c.drawRect(step * k, step * k, gx1, gy1, p)
        // 화점
        p.style = Paint.Style.FILL
        val cx = GameConfig.BOARD_W / 2f
        val cy = GameConfig.BOARD_H / 2f
        for (sx in -1..1) for (sy in -1..1) {
            if (sx != 0 && sy == 0 || sx == 0 && sy != 0) continue
            c.drawCircle((cx + sx * 3f * step) * k, (cy + sy * 2f * step) * k, 0.16f * k, p)
        }
        // 윗면 가장자리: 위·왼쪽 밝게, 아래 어둡게 (입체감)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = 0x55FFF4DC
        c.drawLine(0f, d, bw.toFloat(), d, p)
        c.drawLine(d, 0f, d, topH, p)
        p.color = 0x40000000
        c.drawLine(0f, topH - d, bw.toFloat(), topH - d, p)
        c.drawLine(bw - d, 0f, bw - d, topH, p)
        boardBmp = bmp
    }

    /** 판 비트맵 해제 (onGameDestroy·떼어질 때) */
    fun release() {
        boardBmp?.recycle()
        boardBmp = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (boardBmp == null && width > 0) buildBoard()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return false
        val ux = (e.x - ox) / unitPx
        val uy = (e.y - oy) / unitPx
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (listener?.canAim() != true) return false
                val id = hit(g, ux, uy)
                // 상대 알·빈 곳은 반응 없음
                if (id < 0 || !g.canFlick(id)) return false
                aimId = id
                downX = ux
                downY = uy
                dragX = 0f
                dragY = 0f
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (aimId < 0) return false
                dragX = ux - downX
                dragY = uy - downY
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (aimId < 0) return false
                dragX = ux - downX
                dragY = uy - downY
                val id = aimId
                aimId = -1
                invalidate()
                listener?.onFlick(id, dragX, dragY)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAim()
                return true
            }
        }
        return aimId >= 0
    }

    /** 손가락 아래(가까운) 판 위의 알 (내 알·상대 알 상관없이 가장 가까운 것, 없으면 −1) */
    private fun hit(g: AlkkagiGame, ux: Float, uy: Float): Int {
        val reach = maxOf(GameConfig.STONE_R * 1.35f, 22f * d / unitPx)
        var best = -1
        var bd = reach * reach
        for (s in g.stones) {
            if (!s.onBoard) continue
            val dx = s.x - ux
            val dy = s.y - uy
            val dd = dx * dx + dy * dy
            if (dd <= bd) {
                bd = dd
                best = s.id
            }
        }
        return best
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val k = unitPx
        val bw = GameConfig.BOARD_W * k
        val bh = GameConfig.BOARD_H * k
        // 판 그림자 + 판
        fill.color = 0x66000000
        rect.set(ox + 3f * d, oy + 6f * d, ox + bw + 3f * d, oy + bh + thick + 6f * d)
        c.drawRoundRect(rect, 6f * d, 6f * d, fill)
        val bmp = boardBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, ox, oy, bmpPaint)

        // 차례인 사람 색 테두리
        val turn = g.current
        frame.color = colors[if (g.phase == Phase.OVER) g.winner else turn]
        frame.alpha = if (g.phase == Phase.AIM) 255 else 150
        rect.set(ox - 2.5f * d, oy - 2.5f * d, ox + bw + 2.5f * d, oy + bh + thick + 2.5f * d)
        c.drawRoundRect(rect, 4f * d, 4f * d, frame)

        // 판 위 알 → 떨어지는 알 (떨어지는 알이 위에)
        val showMovable = g.phase == Phase.AIM && aimId < 0 && listener?.canAim() == true
        for (s in g.stones) {
            if (s.state != StoneState.ON_BOARD) continue
            if (showMovable && s.owner == turn) {
                ring.color = 0xCCFFFFFF.toInt()
                c.drawCircle(ox + s.x * k, oy + s.y * k, GameConfig.STONE_R * k * 1.22f, ring)
            }
            drawStone(c, s, 1f, 255, true)
        }
        for (s in g.stones) {
            if (s.state != StoneState.FALLING) continue
            val f = s.fall
            drawStone(c, s, (1f - f) * (1f - 0.25f * f), (255 * (1f - f * f)).toInt(), false)
        }
        if (aimId >= 0) drawAim(c, g.stones[aimId])
    }

    private fun drawStone(c: Canvas, s: Stone, scale: Float, alpha: Int, withShadow: Boolean) {
        val r = GameConfig.STONE_R * unitPx
        c.save()
        c.translate(ox + s.x * unitPx, oy + s.y * unitPx)
        if (scale < 1f) c.scale(scale, scale)
        if (withShadow) c.drawCircle(r * 0.1f, r * 0.2f, r * 1.02f, shadow)
        val p = stonePaint[s.owner]
        p.alpha = alpha
        c.drawCircle(0f, 0f, r, p)
        rim.color = StoneArt.shade(colors[s.owner], 0.42f)
        rim.alpha = alpha
        c.drawCircle(0f, 0f, r - rim.strokeWidth / 2f, rim)
        // 반짝이는 점
        gloss.alpha = 200 * alpha / 255
        rect.set(-r * 0.58f, -r * 0.62f, -r * 0.12f, -r * 0.34f)
        c.drawOval(rect, gloss)
        c.restore()
    }

    /** 고무줄 + 방향 점선(화살표) + 힘 게이지 */
    private fun drawAim(c: Canvas, s: Stone) {
        val k = unitPx
        val r = GameConfig.STONE_R * k
        val cx = ox + s.x * k
        val cy = oy + s.y * k
        val dist = sqrt(dragX * dragX + dragY * dragY)
        val valid = dist >= GameConfig.ALKKAGI_MIN_DRAG
        val pw = AlkkagiGame.power(dist)
        val gc = gaugeColor(pw)
        if (dist > 0.01f) {
            // 고무줄: 알 → 손가락 (최대 끌기 거리까지만)
            val cl = minOf(dist, GameConfig.ALKKAGI_MAX_DRAG) / dist
            val fx = cx + dragX * cl * k
            val fy = cy + dragY * cl * k
            band.color = 0x99FFFFFF.toInt()
            c.drawLine(cx, cy, fx, fy, band)
            fill.color = 0xCCFFFFFF.toInt()
            c.drawCircle(fx, fy, r * 0.32f, fill)
            // 날아갈 방향 점선 (힘이 셀수록 길게)
            val ux = -dragX / dist
            val uy = -dragY / dist
            val x0 = cx + ux * r * 1.5f
            val y0 = cy + uy * r * 1.5f
            val len = (1.2f + 9f * pw) * k
            val x1 = x0 + ux * len
            val y1 = y0 + uy * len
            dots.color = if (valid) gc else 0x88FFFFFF.toInt()
            StoneArt.dotted(c, dots, x0, y0, x1, y1, 0.42f * k)
            // 화살촉
            val ah = r * 0.75f
            path.reset()
            path.moveTo(x1 + ux * ah, y1 + uy * ah)
            path.lineTo(x1 - uy * ah * 0.6f, y1 + ux * ah * 0.6f)
            path.lineTo(x1 + uy * ah * 0.6f, y1 - ux * ah * 0.6f)
            path.close()
            fill.color = dots.color
            c.drawPath(path, fill)
        }
        // 힘 게이지: 알 둘레 고리
        val gr = r * 1.5f
        rect.set(cx - gr, cy - gr, cx + gr, cy + gr)
        ring.color = 0x66000000
        ring.strokeWidth = 5f * d
        c.drawCircle(cx, cy, gr, ring)
        if (valid) {
            ring.color = gc
            c.drawArc(rect, -90f, 360f * pw, false, ring)
        }
        ring.strokeWidth = 1.6f * d
        // 글자: 힘 % 또는 "취소"
        val text = if (valid) PERCENT[(pw * 100f + 0.5f).toInt()] else CANCEL
        label.color = if (valid) gc else 0xFFE0E0E0.toInt()
        val ty = if (cy - gr - 8f * d > 16f * d) cy - gr - 7f * d else cy + gr + 18f * d
        c.drawText(text, cx, ty, labelOutline)
        c.drawText(text, cx, ty, label)
    }

    /** 힘 0..1 → 초록 → 노랑 → 빨강 */
    private fun gaugeColor(p: Float): Int {
        val r: Int
        val g: Int
        if (p < 0.5f) {
            val t = p / 0.5f
            r = (0x3D + (0xFF - 0x3D) * t).toInt()
            g = (0xE0 + (0xD0 - 0xE0) * t).toInt()
        } else {
            val t = (p - 0.5f) / 0.5f
            r = 0xFF
            g = (0xD0 + (0x45 - 0xD0) * t).toInt()
        }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40
    }

    companion object {
        private const val CANCEL = "짧으면 취소"
        private val PERCENT = Array(101) { "$it%" }
    }
}
