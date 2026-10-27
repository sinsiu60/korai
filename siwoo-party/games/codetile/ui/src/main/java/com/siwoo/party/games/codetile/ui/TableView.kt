package com.siwoo.party.games.codetile.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.codetile.CodeTileGame
import com.siwoo.party.games.codetile.GameConfig
import com.siwoo.party.games.codetile.Phase

/**
 * 공개 탁자 (모두가 보는 화면): 플레이어마다 한 줄씩, 정렬된 타일을 그린다.
 *  - 숨은 타일은 색(검정/흰색)만, 공개된 타일은 숫자. **숨은 숫자는 이 View 에 아예 들어오지 않는다**
 *    (sync 가 game.publicNumber 만 복사 → 숨은 칸은 -1)
 *  - 지금 차례 줄은 밝게 + 금색 테두리, 오른쪽 끝 "뽑은 타일" 칸에 뽑은 타일(색만)
 *  - 고른 상대 타일은 위로 들리며 금색 테두리, 탈락한 줄은 흐리게 + "탈락" 도장
 *  - 연출: flip(정답 공개 뒤집기) · insert(뽑은 타일이 제자리에 쏙) · shake(오답 도리도리) — 진행 중에만 다시 그림
 * 타일을 누르면 onTileTap(플레이어, 번째). onDraw 할당 없음.
 */
class TableView(context: Context) : View(context) {

    /** 누를 수 있는 타일 종류 */
    enum class Pick { NONE, OPPONENTS, OWN }

    var onTileTap: ((Int, Int) -> Unit)? = null
    /** 지금 차례 줄 오른쪽 "뽑은 타일" 칸을 그릴지 (끝나면 끔) */
    var showSlot = true
        set(v) { if (field != v) { field = v; invalidate() } }
    var pick = Pick.NONE
        set(v) { if (field != v) { field = v; invalidate() } }

    private var n = 0
    private var names: Array<String> = emptyArray()
    private var initials: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var labelColors = IntArray(0)
    private var initColors = IntArray(0)
    private val count = IntArray(GameConfig.MAX_PLAYERS)
    private val black = Array(GameConfig.MAX_PLAYERS) { BooleanArray(GameConfig.TILE_COUNT) }
    /** 공개된 숫자 (숨은 타일 = -1) */
    private val shown = Array(GameConfig.MAX_PLAYERS) { IntArray(GameConfig.TILE_COUNT) { -1 } }
    private val out = BooleanArray(GameConfig.MAX_PLAYERS)
    private var current = -1
    private var hasDrawn = false
    private var drawnBlack = false
    private var selP = -1
    private var selI = -1

    // ── 연출 ──
    private var animKind = ANIM_NONE
    private var animP = -1
    private var animI = -1
    private var animF = 1f
    private var anim: ValueAnimator? = null

    // ── 배치 (layout·sync 때 계산) ──
    private val bands = Array(GameConfig.MAX_PLAYERS) { RectF() }
    private var labelW = 0f
    private var tileW = 0f
    private var tileH = 0f
    private var gap = 0f
    private var slotW = 0f
    private var twoLines = false
    private var perLine = 1
    private var lineGap = 0f
    private var stampBase = 0f
    private var pad = 0f
    private var downP = -1
    private var downI = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.RED }
    private val tmp = RectF()

    init {
        contentDescription = DESC
        isClickable = true
    }

    /** 플레이어 이름·색 (처음 한 번) */
    fun setPlayers(names: List<String>, colors: IntArray) {
        n = names.size
        this.names = Array(n) { names[it] }
        initials = Array(n) { names[it].take(1) }
        this.colors = colors.copyOf()
        labelColors = IntArray(n) { Theme.shade(colors[it], 0.45f) }
        initColors = IntArray(n) { Theme.textOn(colors[it]) }
        relayout()
        invalidate()
    }

    /** 게임 상태에서 공개 정보만 복사 (숨은 숫자는 -1) */
    fun sync(g: CodeTileGame) {
        for (p in 0 until n) {
            val c = minOf(g.rowSize(p), GameConfig.TILE_COUNT)
            count[p] = c
            for (i in 0 until GameConfig.TILE_COUNT) {
                if (i < c) {
                    black[p][i] = g.isBlackAt(p, i)
                    shown[p][i] = g.publicNumber(p, i)
                } else {
                    black[p][i] = false
                    shown[p][i] = -1
                }
            }
            out[p] = g.isOut(p)
        }
        relayout()
        invalidate()
    }

    /** 게임이 끝난 뒤에만: 모든 타일 숫자 공개 (진행 중엔 아무것도 하지 않음) */
    fun revealAll(g: CodeTileGame) {
        if (g.phase != Phase.OVER) return
        for (p in 0 until n) for (i in 0 until count[p]) shown[p][i] = g.tileAt(p, i).number
        invalidate()
    }

    /** 지금 차례와 뽑은 타일(색만, 없으면 null) */
    fun setTurn(player: Int, drawnIsBlack: Boolean?) {
        current = player
        hasDrawn = drawnIsBlack != null
        drawnBlack = drawnIsBlack == true
        invalidate()
    }

    fun select(p: Int, i: Int) {
        selP = p
        selI = i
        invalidate()
    }

    val selectedPlayer: Int get() = selP
    val selectedIndex: Int get() = selI

    // ── 테스트·확인용 (공개 정보만) ──
    fun count(p: Int): Int = count[p]
    /** 그려지는 숫자 (숨은 타일 = -1) */
    fun shownNumber(p: Int, i: Int): Int = shown[p][i]
    fun isBlack(p: Int, i: Int): Boolean = black[p][i]
    val showsDrawn: Boolean get() = hasDrawn

    /** 타일 가운데 (이 View 좌표) */
    fun tileCenter(p: Int, i: Int, out: PointF): Boolean {
        if (p !in 0 until n || i !in 0 until count[p] || tileW <= 0f) return false
        out.set(tileLeft(p, i) + tileW / 2f, tileTop(p, i) + tileH / 2f)
        return true
    }

    // ── 연출 ──

    /** 정답: 숨은 면 → 숫자 면으로 뒤집기 (sync 로 숫자가 들어온 뒤 부를 것) */
    fun flip(p: Int, i: Int, ms: Long) = start(ANIM_FLIP, p, i, ms)

    /** 뽑은 타일이 제자리에 쏙 (sync 뒤) */
    fun insert(p: Int, i: Int, ms: Long) = start(ANIM_INSERT, p, i, ms)

    /** 오답: 고른 타일이 도리도리 */
    fun shake(p: Int, i: Int, ms: Long) = start(ANIM_SHAKE, p, i, ms)

    val animating: Boolean get() = anim != null

    private fun start(kind: Int, p: Int, i: Int, ms: Long) {
        anim?.cancel()
        animKind = kind
        animP = p
        animI = i
        animF = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms.coerceAtLeast(1)
            addUpdateListener {
                animF = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (anim === animation) {
                        anim = null
                        animKind = ANIM_NONE
                        animF = 1f
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    fun pause() { anim?.pause() }
    fun resume() { anim?.resume() }

    // ── 배치 ──

    private fun relayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || n == 0) return
        pad = Theme.dpf(context, 6f)
        val bandGap = Theme.dpf(context, 6f)
        val bandH = (h - bandGap * (n - 1)) / n
        for (p in 0 until n) {
            val t = p * (bandH + bandGap)
            bands[p].set(0f, t, w, t + bandH)
        }
        labelW = minOf(Theme.dpf(context, 74f), w * 0.2f)
        // 타일 크기: 줄 높이에 맞추되, 가장 긴 줄이 들어가게 줄임
        var maxCount = 1
        for (p in 0 until n) maxCount = maxOf(maxCount, count[p])
        val avail = w - labelW - pad * 3
        // 한 줄: 줄 높이에 맞추되 가장 긴 줄이 들어가게 줄임
        val maxTh = Theme.dpf(context, 64f)
        val th1 = minOf(bandH * 0.72f, maxTh)
        val tw1 = minOf(th1 * TileArt.ASPECT, avail / (maxCount * 1.14f + 1.5f))
        // 두 줄 (줄 높이가 넉넉한 2~3인에서 타일이 많을 때): 더 크게 그릴 수 있으면 두 줄로
        val per2 = (maxCount + 1) / 2
        val th2 = minOf(bandH * 0.42f, maxTh)
        val tw2 = minOf(th2 * TileArt.ASPECT, avail / (per2 * 1.14f + 1.5f))
        twoLines = maxCount > 6 && tw2 > tw1 * 1.15f
        perLine = if (twoLines) per2 else maxCount
        val tw = if (twoLines) tw2 else tw1
        val th = tw / TileArt.ASPECT
        val g = tw * 0.14f
        val slot = tw * 1.5f
        lineGap = if (twoLines) minOf(th * 0.18f, (bandH - th * 2f) / 3f) else 0f
        tileW = tw
        tileH = th
        gap = g
        slotW = slot
        namePaint.textSize = minOf(bandH * 0.2f, Theme.dpf(context, 13f))
        initPaint.textSize = minOf(bandH * 0.26f, Theme.dpf(context, 16f))
        slotPaint.textSize = minOf(bandH * 0.13f, Theme.dpf(context, 10f))
        stampBase = minOf(bandH * 0.42f, Theme.dpf(context, 30f))
        stampPaint.textSize = stampBase
        stroke.strokeWidth = Theme.dpf(context, 2f)
        dash.strokeWidth = Theme.dpf(context, 1.5f)
        dash.pathEffect = DashPathEffect(floatArrayOf(Theme.dpf(context, 4f), Theme.dpf(context, 3f)), 0f)
        while (slotPaint.measureText(SLOT) > slotW * 0.92f && slotPaint.textSize > Theme.dpf(context, 6f)) slotPaint.textSize *= 0.92f
        // 이름이 칸보다 길면 줄임
        for (p in 0 until n) {
            while (namePaint.measureText(names[p]) > labelW - pad * 2 && namePaint.textSize > Theme.dpf(context, 8f)) namePaint.textSize *= 0.92f
        }
    }

    /** 이 줄이 두 줄로 나뉘는지 */
    private fun split(p: Int): Boolean = twoLines && count[p] > perLine
    private fun lineOf(p: Int, i: Int): Int = if (split(p)) i / perLine else 0
    private fun tileLeft(p: Int, i: Int): Float =
        bands[p].left + labelW + pad * 2 + (i - lineOf(p, i) * perLine) * (tileW + gap)
    private fun tileTop(p: Int, i: Int): Float =
        if (!split(p)) bands[p].centerY() - tileH / 2f
        else if (lineOf(p, i) == 0) bands[p].centerY() - lineGap / 2f - tileH
        else bands[p].centerY() + lineGap / 2f
    private fun slotLeft(p: Int): Float = bands[p].right - pad - slotW

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hit(e.x, e.y)
                downP = hitP
                downI = hitI
            }
            MotionEvent.ACTION_UP -> {
                hit(e.x, e.y)
                if (hitP >= 0 && hitP == downP && hitI == downI) onTileTap?.invoke(hitP, hitI)
                downP = -1
                downI = -1
            }
            MotionEvent.ACTION_CANCEL -> { downP = -1; downI = -1 }
        }
        return true
    }

    private var hitP = -1
    private var hitI = -1

    /** (x, y) 아래 타일 → hitP / hitI (없으면 -1). 손가락 크기를 생각해 위아래로 넉넉히 */
    private fun hit(x: Float, y: Float) {
        hitP = -1
        hitI = -1
        for (p in 0 until n) {
            val b = bands[p]
            if (y < b.top || y > b.bottom) continue
            for (i in 0 until count[p]) {
                val l = tileLeft(p, i) - gap / 2f
                if (split(p)) {
                    // 두 줄이면 가운데를 기준으로 위/아래 줄
                    val upper = y < b.centerY()
                    if (upper != (lineOf(p, i) == 0)) continue
                }
                if (x >= l && x < l + tileW + gap) {
                    hitP = p
                    hitI = i
                    return
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        if (n == 0 || tileW <= 0f) return
        val r = Theme.dpf(context, 12f)
        for (p in 0 until n) {
            val b = bands[p]
            val cur = p == current
            // 줄 바탕
            fill.color = if (cur) Theme.CARD_LIGHT else Theme.CARD
            c.drawRoundRect(b, r, r, fill)
            // 왼쪽 이름 칸 (플레이어 색 띠)
            fill.color = labelColors[p]
            tmp.set(b.left, b.top, b.left + labelW, b.bottom)
            c.drawRoundRect(tmp, r, r, fill)
            c.drawRect(tmp.right - r, tmp.top, tmp.right, tmp.bottom, fill)
            val cy = b.top + b.height() * 0.4f
            val cr = minOf(b.height() * 0.2f, labelW * 0.24f)
            fill.color = colors[p]
            c.drawCircle(b.left + labelW / 2f, cy, cr, fill)
            initPaint.color = initColors[p]
            c.drawText(initials[p], b.left + labelW / 2f, cy - (initPaint.descent() + initPaint.ascent()) / 2f, initPaint)
            c.drawText(names[p], b.left + labelW / 2f, b.top + b.height() * 0.84f, namePaint)
            if (cur) {
                stroke.color = Theme.GOLD
                c.drawRoundRect(b.left + stroke.strokeWidth / 2f, b.top + stroke.strokeWidth / 2f,
                    b.right - stroke.strokeWidth / 2f, b.bottom - stroke.strokeWidth / 2f, r, r, stroke)
            }
            // 타일
            val dim = out[p]
            for (i in 0 until count[p]) drawTileAt(c, p, i, if (dim) 120 else 255)
            // 뽑은 타일 칸 (지금 차례만)
            if (cur && !dim && showSlot) {
                val sl = slotLeft(p)
                val st = b.top + pad * 0.6f
                dash.color = Theme.GOLD
                tmp.set(sl, st, sl + slotW, b.bottom - pad * 0.6f)
                c.drawRoundRect(tmp, r * 0.6f, r * 0.6f, dash)
                c.drawText(SLOT, tmp.centerX(), tmp.top + slotPaint.textSize * 1.1f, slotPaint)
                if (hasDrawn) {
                    // 글자 아래 남은 높이에 맞춘 조금 작은 타일
                    val top = tmp.top + slotPaint.textSize * 1.45f
                    val th = minOf(tileH, tmp.bottom - pad * 0.5f - top)
                    val tw = th * TileArt.ASPECT
                    TileArt.draw(c, tmp.centerX() - tw / 2f, top + (tmp.bottom - pad * 0.5f - top - th) / 2f, tw, th, drawnBlack, -1)
                } else {
                    c.drawText(NONE, tmp.centerX(), tmp.centerY() + slotPaint.textSize * 0.9f, slotPaint)
                }
            }
            if (dim) {
                // 탈락 도장: 줄 오른쪽 빈자리에 비스듬히 (자리가 좁으면 작게)
                val last = if (count[p] == 0) -1 else if (split(p)) perLine - 1 else count[p] - 1
                val end = if (last < 0) b.left + labelW else tileLeft(p, last) + tileW
                val free = b.right - pad - end
                stampPaint.textSize = stampBase
                val full = stampPaint.measureText(OUT) + pad * 4
                if (full > free) stampPaint.textSize = maxOf(stampBase * 0.55f, stampBase * free / full)
                val tw = stampPaint.measureText(OUT) / 2f + pad
                val sx = minOf(b.right - pad - tw, maxOf(end + pad + tw, b.right - pad * 2 - tw))
                val sy = b.centerY()
                c.save()
                c.rotate(-12f, sx, sy)
                stroke.color = Theme.RED
                tmp.set(sx - tw, sy - stampPaint.textSize * 0.75f, sx + tw, sy + stampPaint.textSize * 0.65f)
                c.drawRoundRect(tmp, pad, pad, stroke)
                c.drawText(OUT, sx, sy + stampPaint.textSize * 0.35f, stampPaint)
                c.restore()
            }
        }
    }

    private fun drawTileAt(c: Canvas, p: Int, i: Int, alpha: Int) {
        var l = tileLeft(p, i)
        var t = tileTop(p, i)
        val num = shown[p][i]
        val isAnim = animKind != ANIM_NONE && animP == p && animI == i
        val selected = p == selP && i == selI
        if (selected) t -= tileH * 0.1f
        if (isAnim && animKind == ANIM_SHAKE) {
            l += kotlin.math.sin(animF * Math.PI * 6).toFloat() * tileW * 0.18f * (1f - animF)
        }
        if (isAnim && (animKind == ANIM_FLIP || animKind == ANIM_INSERT)) {
            // 뒤집기: 가로로 줄었다 펴짐 (절반 전엔 숨은 면) / 끼우기: 위에서 떨어지며 들어옴 (공개 타일이면 착지 뒤 뒤집힘)
            var face = num
            var sx = 1f
            if (animKind == ANIM_FLIP) {
                sx = kotlin.math.abs(kotlin.math.cos(animF * Math.PI)).toFloat()
                if (animF < 0.5f) face = -1
                t -= tileH * 0.18f * kotlin.math.sin(animF * Math.PI).toFloat()
            } else {
                val drop = minOf(1f, animF * 2f)
                t -= tileH * 0.8f * (1f - drop) * (1f - drop)
                if (num >= 0) {
                    if (animF < 0.5f) face = -1
                    else {
                        val f2 = (animF - 0.5f) * 2f
                        sx = kotlin.math.abs(kotlin.math.cos(f2 * Math.PI)).toFloat()
                        if (f2 < 0.5f) face = -1
                    }
                }
            }
            val w = tileW * maxOf(sx, 0.04f)
            TileArt.draw(c, l + (tileW - w) / 2f, t, w, tileH, black[p][i], face, alpha)
            return
        }
        TileArt.draw(c, l, t, tileW, tileH, black[p][i], num, alpha)
        if (selected) TileArt.outline(c, l, t, tileW, tileH, Theme.GOLD, Theme.dpf(context, 3f))
        else if (pick == Pick.OWN && p == current && num < 0) TileArt.outline(c, l, t, tileW, tileH, Theme.RED, Theme.dpf(context, 2f))
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val DESC = "공개 탁자"
        private const val OUT = "탈락"
        private const val SLOT = "뽑은 타일"
        private const val NONE = "없음"
        private const val ANIM_NONE = 0
        private const val ANIM_FLIP = 1
        private const val ANIM_INSERT = 2
        private const val ANIM_SHAKE = 3
    }
}
