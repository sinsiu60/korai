package com.siwoo.party.games.curling.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.aim.Slingshot
import com.siwoo.party.common.aim.SlingshotDrawer
import com.siwoo.party.common.aim.SlingshotTouch
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.curling.CurlStone
import com.siwoo.party.games.curling.CurlingGame
import com.siwoo.party.games.curling.CurlingRules
import com.siwoo.party.games.curling.GameConfig
import com.siwoo.party.games.curling.Phase
import com.siwoo.party.games.curling.StoneState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 컬링 시트 (위에서 본 얼음판: 왼쪽 핵 → 오른쪽 하우스). 스톤 위치는 CurlingGame 의 시트 단위 좌표를 그대로 그린다.
 * - 얼음·선·하우스는 크기가 바뀔 때 비트맵으로 한 번 그려 두고(release 에서 recycle) 매 프레임 붙이기만
 * - 조준: 공통 새총(SlingshotTouch + SlingshotDrawer 의 고무줄·힘 고리) + 제한된 방향의 점선(예상 거리) + 휘는 방향 화살표
 * - 스톤이 움직이는 동안: 화면 어디든 톡톡 (손가락 여러 개도 각각) → listener.onSweepTap. 빗자루·스위핑 막대 표시
 * - 엔드 득점 표시: 점수가 된 스톤에 금색 고리·번호, 티에서 가장 가까운 스톤·상대 가장 가까운 스톤까지 재는 선
 * 움직임은 화면(Activity)의 프레임 루프가 game.advance 후 invalidate 한다 — 이 View 는 스스로 반복해서 그리지 않는다.
 */
class SheetView(context: Context) : View(context), SlingshotTouch.Listener {

    interface Listener {
        /** 지금 조준을 시작해도 되는지 (내 차례·일시정지 아님) */
        fun canAim(): Boolean
        /** 새총을 놓음: 방향(화면, 단위 벡터)·힘 0..1 */
        fun onShoot(dirX: Float, dirY: Float, power: Float)
        /** 조준이 아닐 때 화면을 톡 침 (이 View 좌표) */
        fun onSweepTap(x: Float, y: Float)
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: CurlingGame? = null
    private val sideColors = IntArray(GameConfig.MAX_PLAYERS)

    // ── 배치 ──
    /** 시트 단위 1 = unitPx 픽셀, 시트 왼쪽 위 = (ox, oy) */
    var unitPx = 1f
        private set
    var ox = 0f
        private set
    var oy = 0f
        private set
    private var sheetBmp: Bitmap? = null

    // ── 조준 (공통 새총) ──
    val sling = Slingshot(maxDistance = 90f * d, minDistance = 14f * d)
    val touch = SlingshotTouch(sling).also { it.listener = this }
    private val slingDrawer = SlingshotDrawer(context).apply {
        dotCount = 0 // 점선은 제한된 각도로 직접 그림
        ringRadius = 20f * d
    }

    /** 엔드 득점 표시 중 */
    private var showScore = false

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xDD0B1340.toInt()
    }
    private val rect = RectF()

    init {
        isClickable = true
        touch.startFilter = SlingshotTouch.StartFilter { _, _ -> listener?.canAim() == true }
    }

    /** 게임과 편 색 연결 */
    fun bind(game: CurlingGame, colors: IntArray) {
        this.game = game
        for (i in colors.indices) sideColors[i] = colors[i]
        invalidate()
    }

    /** 엔드 득점 표시 켜기/끄기 */
    fun setShowScore(v: Boolean) {
        showScore = v
        invalidate()
    }

    /** 시트 단위 (x, y) → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(ox + x * unitPx, oy + y * unitPx)
    }

    /** 던지는 자리(핵) 화면 좌표 */
    fun hackScreen(out: PointF) = toScreen(GameConfig.HACK_X, GameConfig.HACK_Y, out)

    /** 조준 취소 (일시정지 등) */
    fun cancelAim() {
        touch.abort()
        invalidate()
    }

    // ── 크기·비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val m = 4f * d
        unitPx = minOf((w - 2f * m) / GameConfig.SHEET_L, (h - 2f * m) / GameConfig.SHEET_W)
        ox = (w - GameConfig.SHEET_L * unitPx) / 2f
        oy = (h - GameConfig.SHEET_W * unitPx) / 2f
        label.textSize = 13f * d
        outline.textSize = 13f * d
        outline.strokeWidth = 3.5f * d
        dash.strokeWidth = 1.6f * d
        dash.pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f)
        buildSheet()
    }

    /** 얼음 + 자국 + 선 + 하우스 + 핵 + 테두리를 비트맵 하나에 */
    private fun buildSheet() {
        release()
        val k = unitPx
        val bw = (GameConfig.SHEET_L * k).toInt().coerceAtLeast(1)
        val bh = (GameConfig.SHEET_W * k).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.shader = LinearGradient(0f, 0f, 0f, bh.toFloat(),
            intArrayOf(0xFFDCEBF6.toInt(), CurlArt.ICE, 0xFFF4F9FD.toInt(), CurlArt.ICE, 0xFFD6E7F3.toInt()),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), q)
        q.shader = null
        // 얼음 자국(페블): 고정 난수 → 늘 같은 무늬
        val rnd = Random(23)
        q.style = Paint.Style.FILL
        repeat((bw * bh / (90f * d * d)).toInt().coerceAtMost(3000)) {
            q.color = if (rnd.nextBoolean()) 0x18FFFFFF else 0x10305080
            c.drawCircle(rnd.nextFloat() * bw, rnd.nextFloat() * bh, (0.5f + rnd.nextFloat()) * d, q)
        }
        val cy = GameConfig.TEE_Y * k
        // 하우스
        CurlArt.house(c, q, GameConfig.TEE_X * k, cy, GameConfig.HOUSE_R * k)
        // 선들
        q.style = Paint.Style.STROKE
        q.color = CurlArt.LINE
        q.strokeWidth = maxOf(1f, 1.2f * d)
        c.drawLine((GameConfig.HACK_X - 1f) * k, cy, GameConfig.BACK_X * k, cy, q) // 가운데 줄
        c.drawLine(GameConfig.TEE_X * k, 0f, GameConfig.TEE_X * k, bh.toFloat(), q) // 티 라인
        q.strokeWidth = maxOf(1.5f, 2f * d)
        c.drawLine(GameConfig.BACK_X * k, 0f, GameConfig.BACK_X * k, bh.toFloat(), q) // 백 라인
        q.color = CurlArt.HOG
        q.strokeWidth = maxOf(2f, 3.2f * d)
        c.drawLine(GameConfig.HOG_X * k, 0f, GameConfig.HOG_X * k, bh.toFloat(), q) // 호그 라인
        // 백 라인 뒤 (아웃 구역) 살짝 어둡게
        q.style = Paint.Style.FILL
        q.color = 0x22203A6A
        c.drawRect((GameConfig.BACK_X + GameConfig.STONE_R) * k, 0f, bw.toFloat(), bh.toFloat(), q)
        // 핵 (발 받침 고무 두 개)
        q.color = 0xFF1E2230.toInt()
        val hx = (GameConfig.HACK_X - 2.3f) * k
        rect.set(hx - 0.45f * k, cy - 1.3f * k, hx + 0.45f * k, cy - 0.35f * k)
        c.drawRoundRect(rect, 0.2f * k, 0.2f * k, q)
        rect.set(hx - 0.45f * k, cy + 0.35f * k, hx + 0.45f * k, cy + 1.3f * k)
        c.drawRoundRect(rect, 0.2f * k, 0.2f * k, q)
        // 옆 테두리
        q.color = 0xFF2B3C8F.toInt()
        c.drawRect(0f, 0f, bw.toFloat(), 0.22f * k, q)
        c.drawRect(0f, bh - 0.22f * k, bw.toFloat(), bh.toFloat(), q)
        sheetBmp = bmp
    }

    fun release() {
        sheetBmp?.recycle()
        sheetBmp = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (sheetBmp == null && width > 0) buildSheet()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = listener ?: return false
        // 조준 (새총): 끄는 중이거나 조준할 수 있을 때 누름
        if (touch.dragging || (e.actionMasked == MotionEvent.ACTION_DOWN && l.canAim())) {
            if (touch.onTouchEvent(e)) {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        // 그 밖의 누름은 모두 스위핑 연타 (손가락마다)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                l.onSweepTap(e.getX(i), e.getY(i))
            }
        }
        return true
    }

    override fun onAimStart(s: Slingshot) = invalidate()
    override fun onAimMove(s: Slingshot) = invalidate()
    override fun onAimCancel(s: Slingshot) = invalidate()
    override fun onShoot(s: Slingshot) {
        invalidate()
        listener?.onShoot(s.dirX, s.dirY, s.power)
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val k = unitPx
        val bw = GameConfig.SHEET_L * k
        val bh = GameConfig.SHEET_W * k
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        rect.set(ox + 2f * d, oy + 4f * d, ox + bw + 2f * d, oy + bh + 4f * d)
        c.drawRoundRect(rect, 4f * d, 4f * d, p)
        val bmp = sheetBmp
        if (bmp != null && !bmp.isRecycled) c.drawBitmap(bmp, ox, oy, bmpPaint)

        // 차례인 편 색 테두리
        if (g.phase == Phase.AIM || g.phase == Phase.MOVING) {
            stroke.strokeWidth = 3f * d
            stroke.color = sideColors[g.currentSide]
            stroke.alpha = if (g.phase == Phase.AIM) 255 else 130
            rect.set(ox - 1.5f * d, oy - 1.5f * d, ox + bw + 1.5f * d, oy + bh + 1.5f * d)
            c.drawRoundRect(rect, 3f * d, 3f * d, stroke)
            stroke.alpha = 255
        }

        if (showScore) drawMeasure(c, g)

        val r = GameConfig.STONE_R * k
        val stones = g.stones
        for (s in stones) {
            when (s.state) {
                StoneState.IN_PLAY -> CurlArt.stone(c, p, ox + s.x * k, oy + s.y * k, r, sideColors[s.side], s.handle)
                StoneState.FADING -> {
                    val a = (255 * (1f - s.fade)).toInt()
                    CurlArt.stone(c, p, ox + s.x * k, oy + s.y * k, r * (1f - 0.2f * s.fade), sideColors[s.side], s.handle, a)
                }
                else -> Unit
            }
        }
        if (g.phase == Phase.AIM) drawAim(c, g, r)
        if (g.phase == Phase.MOVING) drawSweep(c, g, r)
        if (showScore) drawCounting(c, g, r)
    }

    /** 던질 스톤(핵 위) + 새총 + 제한된 방향 점선 + 휘는 방향 */
    private fun drawAim(c: Canvas, g: CurlingGame, r: Float) {
        val hx = ox + GameConfig.HACK_X * unitPx
        val hy = oy + GameConfig.HACK_Y * unitPx
        val s = g.currentStone
        val color = sideColors[s.side]
        val can = listener?.canAim() == true
        if (can && !sling.active) {
            // 던질 수 있다는 표시 (움직이지 않는 고리)
            stroke.strokeWidth = 2f * d
            stroke.color = 0xCCFFFFFF.toInt()
            c.drawCircle(hx, hy, r * 1.45f, stroke)
        }
        CurlArt.stone(c, p, hx, hy, r, color, 0f)
        if (!sling.active) return
        slingDrawer.draw(c, sling, hx, hy)
        if (sling.cancelled) return
        // 점선: 제한된 각도로, 길이 = 곧게 갔을 때 예상 거리 (컬·스위핑 없이)
        val a = CurlingGame.aimAngle(sling.dirX, sling.dirY)
        val v = CurlingGame.launchSpeed(sling.power)
        val dist = minOf(v * v / (2f * GameConfig.FRICTION), GameConfig.SHEET_L - GameConfig.HACK_X)
        val ux = cos(a)
        val uy = sin(a)
        val step = 1.6f * unitPx
        val len = dist * unitPx
        var t = r * 1.8f
        p.style = Paint.Style.FILL
        p.color = 0xCC1A2A5A.toInt()
        while (t < len) {
            c.drawCircle(hx + ux * t, hy + uy * t, 2.4f * d, p)
            t += step
        }
        // 끝에서 휘는 방향 화살표
        val ex = hx + ux * len
        val ey = hy + uy * len
        CurlArt.curlArrow(c, p, ex - 2.6f * unitPx, ey, 2.6f * unitPx, 1.4f * unitPx, g.curlDir, 2.6f * d, Theme.GOLD_DARK)
        // 힘 %
        val pct = PERCENT[(sling.power * 100f + 0.5f).toInt().coerceIn(0, 100)]
        val ty = hy - r * 2.4f
        label.color = Theme.GOLD
        c.drawText(pct, hx, ty, outline)
        c.drawText(pct, hx, ty, label)
    }

    /** 스위핑: 안내 + 세기 막대(스톤 위) + 빗자루 둘 (스톤 앞) */
    private fun drawSweep(c: Canvas, g: CurlingGame, r: Float) {
        val sh = g.sheet
        val t = sh.thrown ?: return
        if (!t.inPlay) return
        val x = ox + t.x * unitPx
        val y = oy + t.y * unitPx
        val lv = sh.sweep
        if (sh.sweepable) {
            // 세기 막대
            val w = r * 3f
            val by = if (t.y < 3f) y + r * 1.7f else y - r * 1.9f
            rect.set(x - w / 2f, by - 3.5f * d, x + w / 2f, by + 3.5f * d)
            p.style = Paint.Style.FILL
            p.color = 0x99000000.toInt()
            c.drawRoundRect(rect, 3.5f * d, 3.5f * d, p)
            if (lv > 0f) {
                rect.right = rect.left + w * lv
                p.color = Theme.GOLD
                c.drawRoundRect(rect, 3.5f * d, 3.5f * d, p)
            }
            rect.set(x - w / 2f, by - 3.5f * d, x + w / 2f, by + 3.5f * d)
            stroke.strokeWidth = 1.2f * d
            stroke.color = Theme.GOLD_DARK
            c.drawRoundRect(rect, 3.5f * d, 3.5f * d, stroke)
        }
        if (lv > 0.01f && t.moving) {
            // 빗자루 둘: 진행 방향 앞에서 좌우로 엇갈리게 (단계 수로 흔들림)
            val sp = t.speed
            val ux = t.vx / sp
            val uy = t.vy / sp
            val wob = if ((sh.steps / 4) % 2 == 0L) 1f else -1f
            val ahead = r * 2.1f
            val bx = x + ux * ahead
            val byy = y + uy * ahead
            val len = r * 1.5f
            CurlArt.broom(c, p, bx - uy * r * 0.55f * wob, byy + ux * r * 0.55f * wob, len, -0.6f, -0.8f)
            CurlArt.broom(c, p, bx + ux * r * 0.8f + uy * r * 0.55f * wob, byy + uy * r * 0.8f - ux * r * 0.55f * wob, len, -0.5f, 0.85f)
        }
        if (sh.sweepable) {
            // 안내 (시트 위쪽 가운데)
            val tx = ox + (GameConfig.HOG_X + GameConfig.TEE_X) / 2f * unitPx
            val ty = oy + 1.6f * unitPx + label.textSize * 0.35f
            label.color = if (lv > 0.5f) Theme.GOLD else Theme.TEXT
            c.drawText(SWEEP_HINT, tx, ty, outline)
            c.drawText(SWEEP_HINT, tx, ty, label)
        }
    }

    /** 득점 재기: 티 → 가장 가까운 스톤 (금색), 상대 가장 가까운 스톤까지 원 (점선) */
    private fun drawMeasure(c: Canvas, g: CurlingGame) {
        val res = g.lastResult
        val tx = ox + GameConfig.TEE_X * unitPx
        val ty = oy + GameConfig.TEE_Y * unitPx
        if (res.rival >= 0) {
            val o = g.stones[res.rival]
            val rd = CurlingRules.distance(o.x, o.y) * unitPx
            dash.color = 0xEE20305A.toInt()
            c.drawCircle(tx, ty, rd, dash)
            stroke.strokeWidth = 2f * d
            stroke.color = 0xEEFFFFFF.toInt()
            c.drawLine(tx, ty, ox + o.x * unitPx, oy + o.y * unitPx, stroke)
        }
        if (res.shot >= 0) {
            val s = g.stones[res.shot]
            stroke.strokeWidth = 3f * d
            stroke.color = Theme.GOLD
            c.drawLine(tx, ty, ox + s.x * unitPx, oy + s.y * unitPx, stroke)
        }
    }

    /** 점수가 된 스톤: 금색 고리 + 번호 */
    private fun drawCounting(c: Canvas, g: CurlingGame, r: Float) {
        val res = g.lastResult
        stroke.strokeWidth = 2.6f * d
        stroke.color = Theme.GOLD
        val list = res.counting
        for (n in list.indices) {
            val s: CurlStone = g.stones[list[n]]
            val x = ox + s.x * unitPx
            val y = oy + s.y * unitPx
            c.drawCircle(x, y, r * 1.35f, stroke)
            val txt = NUM[(n + 1).coerceAtMost(NUM.size - 1)]
            label.color = Theme.GOLD
            val ly = y - r * 1.55f
            c.drawText(txt, x, ly, outline)
            c.drawText(txt, x, ly, label)
        }
    }

    companion object {
        private val PERCENT = Array(101) { "$it%" }
        private val NUM = Array(13) { "$it" }
        private const val SWEEP_HINT = "화면을 연타하면 스위핑!"
    }
}
