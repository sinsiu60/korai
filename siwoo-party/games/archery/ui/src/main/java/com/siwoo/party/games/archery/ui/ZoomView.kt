package com.siwoo.party.games.archery.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.archery.ArcheryGame
import com.siwoo.party.games.archery.GameConfig
import com.siwoo.party.games.archery.Shot

/**
 * 결과 확대 화면 (게임 화면 위에 겹침): 경기장의 과녁 자리에서 커다란 과녁이 커지며 나오고,
 * 이번 사람이 쏜 화살(지난 화살은 흐리게, 이번 화살은 퍼지는 고리) + 오른쪽 점수 배지(고리 색, X/10/…/M)와 합계.
 * 과녁은 크기가 정해질 때 한 번 비트맵으로 그려 둔다. 글자는 show() 에서만 만든다 (onDraw 할당 없음).
 * 시각 [now] 는 Activity 가 프레임마다 넣는다. 누르면 [onTap] (보여 주는 중 건너뛰기).
 */
class ZoomView(context: Context) : View(context) {

    var onTap: (() -> Unit)? = null

    private val d = Theme.dpf(context, 1f)
    var now = 0L

    private var game: ArcheryGame? = null
    private var shot: Shot? = null
    private var color = Theme.GOLD

    private var showing = false
    private var startAt = 0L
    private var inMs = 1L
    private var closing = false
    private var closeAt = 0L
    private var outMs = 1L

    // 커지기 시작하는 자리 (경기장 과녁, 이 View 좌표)
    private var fromX = 0f
    private var fromY = 0f
    private var fromR = 0f

    private var titleText = ""
    private var arrowText = ""
    private var totalText = ""
    private var scoreText = ""
    private var nameText = ""

    /** 보이는 점수 글자 (테스트용, 닫히면 "") */
    val shownLabel: String get() = if (showing) scoreText else ""
    val isShowing: Boolean get() = showing

    private var face: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val rect = RectF()

    // 자리 (onSizeChanged)
    private var zx = 0f
    private var zy = 0f
    private var zr = 0f
    private var bx = 0f
    private var by = 0f
    private var br = 0f
    private val panel = RectF()

    init {
        visibility = GONE
    }

    /** 결과 보여 주기 시작 (커지는 시간 inMs) */
    fun show(g: ArcheryGame, s: Shot, playerColor: Int, name: String, sx: Float, sy: Float, sr: Float, startMs: Long, inMs: Long) {
        game = g
        shot = s
        color = playerColor
        fromX = sx
        fromY = sy
        fromR = sr
        scoreText = s.label
        nameText = name
        titleText = (s.arrow + 1).toString() + "번째 화살"
        arrowText = when {
            s.isX -> "엑스 텐 · 10점"
            s.miss -> "과녁 밖 · 0점"
            else -> s.score.toString() + "점"
        }
        totalText = "합계 " + g.totals[s.player] + "점" + if (g.xCount[s.player] > 0) " · X " + g.xCount[s.player] + "개" else ""
        startAt = startMs
        this.inMs = maxOf(1L, inMs)
        closing = false
        showing = true
        now = startMs
        visibility = VISIBLE
        invalidate()
    }

    /** 닫기 시작 (outMs 동안 작아지며 사라짐) */
    fun hide(startMs: Long, outMs: Long) {
        if (!showing || closing) return
        closing = true
        closeAt = startMs
        this.outMs = maxOf(1L, outMs)
        invalidate()
    }

    /** 움직이는 중인지 (커지기·고리 퍼짐·닫히기). 닫히기가 끝나면 숨긴다 */
    fun busy(): Boolean {
        if (!showing) return false
        if (closing) {
            if (now - closeAt >= outMs) {
                showing = false
                closing = false
                visibility = GONE
                return false
            }
            return true
        }
        return now - startAt < inMs + ringMs
    }

    /** 점수 배지 가운데 (이 View 좌표) — 결과 배너·별 자리 */
    fun badgeCenter(pt: android.graphics.PointF) {
        pt.set(bx, by)
    }

    /** 오른쪽(배지) 칸 폭 */
    val badgeColumnWidth: Float get() = panel.right - (zx + zr) - 20f * d

    /** 바로 숨김 (닫히기 연출 끝) */
    fun dismissNow() {
        showing = false
        closing = false
        visibility = GONE
    }

    fun release() {
        face?.recycle()
        face = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pw = minOf(w * 0.9f, h * 1.6f)
        val ph = h * 0.88f
        panel.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        zr = ph * 0.43f
        zx = panel.left + 18f * d + zr
        zy = panel.centerY()
        val rightL = zx + zr + 14f * d
        bx = (rightL + panel.right - 12f * d) / 2f
        br = minOf(ph * 0.17f, (panel.right - rightL) * 0.3f)
        by = panel.top + ph * 0.42f
        scorePaint.textSize = br * 0.9f
        namePaint.textSize = 17f * d
        subPaint.textSize = 13f * d
        titlePaint.textSize = 15f * d
        ring.strokeWidth = 3f * d
        face?.recycle()
        face = null
        val size = (zr * 2f).toInt()
        if (size > 0) {
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            ArcheryArt.drawFace(Canvas(b), p, zr, zr, zr)
            face = b
        }
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val s = shot ?: return
        if (!showing) return
        // 진행: 0 → 1 (커짐), 닫힐 때 1 → 0
        val fIn = ((now - startAt).toFloat() / inMs).coerceIn(0f, 1f)
        val eIn = 1f - (1f - fIn) * (1f - fIn) * (1f - fIn)
        val k = if (closing) 1f - ((now - closeAt).toFloat() / outMs).coerceIn(0f, 1f) else eIn
        // 어둡게 + 판
        p.style = Paint.Style.FILL
        p.color = Theme.DIM
        p.alpha = (0xB3 * k).toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.alpha = 255
        val pa = (255 * k).toInt()
        p.color = Theme.CARD
        p.alpha = (pa * 0.92f).toInt()
        c.drawRoundRect(panel, 22f * d, 22f * d, p)
        ring.color = Theme.GOLD
        ring.alpha = pa
        c.drawRoundRect(panel, 22f * d, 22f * d, ring)
        ring.alpha = 255
        // 과녁 (경기장 자리 → 확대 자리)
        val cx = fromX + (zx - fromX) * k
        val cy = fromY + (zy - fromY) * k
        val r = fromR + (zr - fromR) * k
        val bmp = face
        rect.set(cx - r, cy - r, cx + r, cy + r)
        if (bmp != null) c.drawBitmap(bmp, null, rect, bmpPaint) else ArcheryArt.drawFace(c, p, cx, cy, r)
        // 화살 (지난 것은 흐리게)
        val u = r / GameConfig.FACE_R
        val ns = maxOf(6f * d, u * 0.9f)
        val list = g.shots[s.player]
        c.save()
        c.clipRect(panel)
        for (i in list.indices) {
            val a = list[i] ?: continue
            if (a === s) continue
            ArcheryArt.drawNock(c, p, cx + a.x * u, cy + a.y * u, ns * 0.8f, color, 110 * pa / 255)
        }
        val ax = cx + s.x * u
        val ay = cy + s.y * u
        ArcheryArt.drawNock(c, p, ax, ay, ns, color, pa)
        // 퍼지는 고리 (커진 뒤 RING_MS 동안)
        val fr = ((now - startAt - inMs).toFloat() / ringMs).coerceIn(0f, 1f)
        if (!closing && fr in 0.001f..0.999f) {
            ring.color = Theme.GOLD_LIGHT
            ring.alpha = (255 * (1f - fr)).toInt()
            c.drawCircle(ax, ay, ns * (1.2f + 2.5f * fr), ring)
            ring.alpha = 255
        }
        c.restore()
        // 오른쪽: 이름 · 몇 번째 · 점수 배지 · 합계
        val ta = pa
        namePaint.color = color
        namePaint.alpha = ta
        c.drawText(nameText, bx, panel.top + 30f * d, namePaint)
        titlePaint.alpha = ta
        c.drawText(titleText, bx, panel.top + 52f * d, titlePaint)
        val pop = if (closing) 1f else (((now - startAt - inMs * 2 / 3).toFloat() / popMs).coerceIn(0f, 1f))
        val bs = br * (if (pop < 0.7f) pop / 0.7f * 1.15f else 1.15f - 0.15f * (pop - 0.7f) / 0.3f)
        if (bs > 0.5f) {
            p.color = 0x55000000
            p.alpha = 0x55 * pa / 255
            c.drawCircle(bx + 3f * d, by + 4f * d, bs, p)
            p.color = ArcheryArt.scoreColor(s.score)
            p.alpha = pa
            c.drawCircle(bx, by, bs, p)
            ring.color = if (s.isX) Theme.GOLD else 0xFFFFFFFF.toInt()
            ring.alpha = pa
            c.drawCircle(bx, by, bs, ring)
            ring.alpha = 255
            scorePaint.color = ArcheryArt.scoreInk(s.score)
            scorePaint.alpha = pa
            scorePaint.textSize = bs * 0.9f
            c.drawText(scoreText, bx, by + bs * 0.32f, scorePaint)
        }
        subPaint.color = Theme.TEXT
        subPaint.alpha = ta
        c.drawText(arrowText, bx, by + br * 1.15f + 22f * d, subPaint)
        subPaint.color = Theme.GOLD_LIGHT
        subPaint.alpha = ta
        c.drawText(totalText, bx, by + br * 1.15f + 42f * d, subPaint)
        subPaint.alpha = 255
        p.alpha = 255
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!showing) return false
        if (e.actionMasked == MotionEvent.ACTION_UP) onTap?.invoke()
        return true
    }

    /** 이번 화살 고리가 퍼지는 시간 (커지는 시간에 비례 → 속도 배율이 함께 적용됨) */
    private val ringMs: Long get() = inMs * 17 / 10
    /** 점수 배지가 튀어나오는 시간 */
    private val popMs: Long get() = maxOf(1L, inMs * 85 / 100)
}
