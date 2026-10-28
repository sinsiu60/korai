package com.siwoo.party.games.gonggi.ui

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme

/**
 * 공기놀이 그림 도구 (Canvas 도형만). 경로·셰이더는 처음 한 번만 만들고 그릴 때는 할당 없음 (UI 스레드 전용).
 *  - 공깃돌: 반질반질한 색 돌 (방사형 그라데이션 + 반짝임). 셰이더는 단위 원 좌표라 translate/scale 로 크기만 바꾼다.
 *  - 손: 오른손, 손가락이 위(돗자리 쪽)를 향함. 손바닥(엄지 오른쪽) / 손등(좌우 뒤집고 손톱) 두 모습 + 플레이어 색 소매.
 */
object GonggiArt {

    /** 공깃돌 색 (0번 = 던질 돌) */
    val STONE_COLORS = intArrayOf(
        0xFFE8434F.toInt(), // 빨강
        0xFFFFC233.toInt(), // 노랑
        0xFF35C26B.toInt(), // 초록
        0xFF3F8CFF.toInt(), // 파랑
        0xFFB065F0.toInt(), // 보라
    )
    const val SKIN = 0xFFF6D2B0.toInt()
    const val SKIN_DARK = 0xFFD9A47E.toInt()
    const val SKIN_LINE = 0xFF9A6444.toInt()
    const val NAIL = 0xFFFFF4EE.toInt()
    const val STRAW = 0xFFE9CF8E.toInt()
    const val STRAW_DARK = 0xFFC9A55E.toInt()
    const val INK = 0xFF2A1A0C.toInt()

    private val stonePaints = Array(STONE_COLORS.size) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val c = STONE_COLORS[i]
            shader = RadialGradient(
                -0.35f, -0.42f, 1.55f,
                intArrayOf(Theme.shade(c, 1.55f), c, Theme.shade(c, 0.58f)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.08f }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    /**
     * 공깃돌 (중심 x,y, 가로 반지름 rad). 비스듬한 시점이라 세로는 조금 납작하게.
     * alpha 0..255 (흐리게 그릴 때)
     */
    fun stone(c: Canvas, x: Float, y: Float, rad: Float, colorIndex: Int, alpha: Int = 255) {
        val sp = stonePaints[colorIndex % stonePaints.size]
        sp.alpha = alpha
        c.save()
        c.translate(x, y)
        c.scale(rad, rad * 0.84f)
        c.drawCircle(0f, 0f, 1f, sp)
        rimPaint.color = Theme.shade(STONE_COLORS[colorIndex % STONE_COLORS.size], 0.45f)
        rimPaint.alpha = alpha
        c.drawCircle(0f, 0f, 0.97f, rimPaint)
        // 가운데 이음매 (플라스틱 공깃돌 느낌)
        r.set(-0.78f, -0.32f, 0.78f, 0.62f)
        rimPaint.alpha = alpha / 3
        c.drawArc(r, 15f, 150f, false, rimPaint)
        // 반짝임
        shinePaint.alpha = alpha * 4 / 5
        r.set(-0.62f, -0.7f, -0.1f, -0.32f)
        c.drawOval(r, shinePaint)
        c.restore()
    }

    /** 바닥 그림자 (x,y 바닥 점, 가로 반지름 rad, 진하기 0..1) */
    fun shadow(c: Canvas, x: Float, y: Float, rad: Float, strength: Float) {
        shadowPaint.color = ((0x66 * strength.coerceIn(0f, 1f)).toInt() shl 24)
        r.set(x - rad, y - rad * 0.42f, x + rad, y + rad * 0.5f)
        c.drawOval(r, shadowPaint)
    }

    // ── 손 (단위: 손바닥 반폭 = 1, 손바닥 가운데 = 0,0, 손가락은 −y) ──

    private val handPath = Path()
    private val creasePath = Path()
    private val nailRects = arrayOf(RectF(), RectF(), RectF(), RectF(), RectF())
    private val handFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val sleevePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // 손바닥
        handPath.addRoundRect(RectF(-1f, -0.95f, 1f, 1.05f), 0.75f, 0.75f, Path.Direction.CW)
        // 손가락 (새끼 → 검지, 엄지는 오른쪽)
        val fx = floatArrayOf(-0.72f, -0.24f, 0.24f, 0.72f)
        val top = floatArrayOf(-1.7f, -2.05f, -2.2f, -1.98f)
        for (k in 0 until 4) {
            handPath.addRoundRect(RectF(fx[k] - 0.22f, top[k], fx[k] + 0.22f, -0.5f), 0.22f, 0.22f, Path.Direction.CW)
            nailRects[k].set(fx[k] - 0.14f, top[k] + 0.06f, fx[k] + 0.14f, top[k] + 0.42f)
        }
        // 엄지: 둥근 막대를 비스듬히
        val thumb = Path()
        thumb.addRoundRect(RectF(-0.24f, -1.15f, 0.24f, 0.1f), 0.24f, 0.24f, Path.Direction.CW)
        val m = Matrix()
        m.postRotate(38f)
        m.postTranslate(1.0f, 0.15f)
        thumb.transform(m)
        handPath.addPath(thumb)
        // 엄지 손톱 (대략)
        nailRects[4].set(1.42f, -0.62f, 1.7f, -0.3f)
        // 손바닥 금
        creasePath.moveTo(-0.85f, -0.35f)
        creasePath.quadTo(0f, -0.1f, 0.75f, -0.5f)
        creasePath.moveTo(-0.85f, 0.05f)
        creasePath.quadTo(-0.1f, 0.15f, 0.45f, -0.15f)
        creasePath.moveTo(0.55f, 0.85f)
        creasePath.quadTo(0.2f, 0.2f, 0.62f, -0.3f)
    }

    /**
     * 손 (손바닥 가운데 cx,cy, 손바닥 반폭 rad). back = 손등 (좌우 뒤집고 손톱).
     * 손목·소매는 bottom 까지 내려 그린다.
     */
    fun hand(c: Canvas, cx: Float, cy: Float, rad: Float, back: Boolean, sleeveColor: Int, bottom: Float) {
        // 손목 + 소매
        val skin = if (back) Theme.shade(SKIN, 0.9f) else SKIN
        handLine.color = SKIN_LINE
        handLine.strokeWidth = rad * 0.16f
        c.drawRect(cx - rad * 0.62f, cy + rad * 0.75f, cx + rad * 0.62f, cy + rad * 1.7f, handLine)
        handFill.color = skin
        c.drawRect(cx - rad * 0.62f, cy + rad * 0.75f, cx + rad * 0.62f, cy + rad * 1.7f, handFill)
        sleevePaint.color = sleeveColor
        r.set(cx - rad * 0.86f, cy + rad * 1.55f, cx + rad * 0.86f, maxOf(bottom, cy + rad * 1.8f) + rad)
        c.drawRoundRect(r, rad * 0.3f, rad * 0.3f, sleevePaint)
        sleevePaint.color = Theme.shade(sleeveColor, 0.7f)
        c.drawRect(cx - rad * 0.86f, cy + rad * 1.55f, cx + rad * 0.86f, cy + rad * 1.75f, sleevePaint)
        c.save()
        c.translate(cx, cy)
        c.scale(if (back) -rad else rad, rad)
        // 외곽선: 굵은 선으로 먼저 → 안을 채움 (겹친 손가락 안쪽 선은 가려짐)
        handLine.color = SKIN_LINE
        handLine.strokeWidth = 0.16f
        c.drawPath(handPath, handLine)
        handFill.color = skin
        c.drawPath(handPath, handFill)
        handLine.strokeWidth = 0.06f
        if (back) {
            // 손톱 (흰 바탕 + 테두리) — 손등임을 알아보게
            handFill.color = NAIL
            handLine.color = SKIN_LINE
            handLine.strokeWidth = 0.05f
            for (n in nailRects) {
                c.drawRoundRect(n, 0.12f, 0.12f, handFill)
                c.drawRoundRect(n, 0.12f, 0.12f, handLine)
            }
            // 손가락 마디 주름 + 손등 뼈 줄
            handLine.color = SKIN_DARK
            handLine.strokeWidth = 0.07f
            for (k in 0 until 4) {
                val x = -0.72f + k * 0.48f
                c.drawLine(x - 0.12f, -0.66f, x + 0.12f, -0.66f, handLine)
                c.drawLine(x - 0.1f, -1.2f, x + 0.1f, -1.2f, handLine)
                c.drawLine(x * 0.6f, -0.4f, x * 0.45f, 0.55f, handLine)
            }
        } else {
            handLine.color = SKIN_DARK
            c.drawPath(creasePath, handLine)
        }
        c.restore()
    }

    // ── 글자 (외곽선 + 채움) ──

    private val shoutFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val shoutStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }

    /** 외곽선 글자 (가운데 x, 글자 바닥 y) */
    fun shout(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        shoutFill.textSize = size
        shoutFill.color = color
        shoutStroke.textSize = size
        shoutStroke.strokeWidth = size * 0.16f
        shoutStroke.color = INK
        c.drawText(text, x, y, shoutStroke)
        c.drawText(text, x, y, shoutFill)
    }

    // ── 돗자리 (사다리꼴) — 규칙 카드·아이콘용 간단한 모양 ──

    private val matPath = Path()

    /** 돗자리 (위 폭 topW, 아래 폭 botW, 가운데 cx, 위 y0 ~ 아래 y1) + 짚 결 + 테두리 */
    fun mat(c: Canvas, p: Paint, cx: Float, y0: Float, y1: Float, topW: Float, botW: Float, edge: Int) {
        matPath.rewind()
        matPath.moveTo(cx - topW / 2f, y0)
        matPath.lineTo(cx + topW / 2f, y0)
        matPath.lineTo(cx + botW / 2f, y1)
        matPath.lineTo(cx - botW / 2f, y1)
        matPath.close()
        p.style = Paint.Style.FILL
        p.color = STRAW
        c.drawPath(matPath, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = (y1 - y0) * 0.012f
        p.color = STRAW_DARK
        val n = 9
        for (k in 1 until n) {
            val f = k / n.toFloat()
            val y = y0 + (y1 - y0) * f
            val w = topW + (botW - topW) * f
            c.drawLine(cx - w / 2f, y, cx + w / 2f, y, p)
        }
        p.strokeWidth = (y1 - y0) * 0.05f
        p.color = edge
        c.drawPath(matPath, p)
        p.style = Paint.Style.FILL
    }
}
