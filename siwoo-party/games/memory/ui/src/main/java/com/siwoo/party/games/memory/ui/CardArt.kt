package com.siwoo.party.games.memory.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.siwoo.party.common.ui.Theme
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 짝 맞추기 카드 그림 (판·허브 아이콘·규칙 카드가 같이 쓴다).
 * - 앞면 그림 12종은 [-1, 1] 정규 좌표의 Path 로 처음 한 번만 만들고,
 *   그릴 때는 canvas.translate/scale 만 한다 → 그리는 중 객체 할당 없음.
 * - 뒷면: 금색 바탕 + 마름모 격자 무늬 + 가운데 메달.
 * 그림 순서는 logic 의 Symbol 순서와 같다 (별, 하트, 달, 해, 번개, 물방울, 나뭇잎, 다이아, 왕관, 음표, 불꽃, 구름).
 */
object CardArt {

    const val SYMBOL_COUNT = 12

    /** 그림 색 (서로 다르게) */
    val COLORS = intArrayOf(
        0xFFFFC107.toInt(), // 별: 노랑
        0xFFE53935.toInt(), // 하트: 빨강
        0xFF3949AB.toInt(), // 달: 남색
        0xFFFB8C00.toInt(), // 해: 주황
        0xFF8E24AA.toInt(), // 번개: 보라
        0xFF039BE5.toInt(), // 물방울: 하늘
        0xFF43A047.toInt(), // 나뭇잎: 초록
        0xFF00BFA5.toInt(), // 다이아: 청록
        0xFFEC407A.toInt(), // 왕관: 분홍
        0xFF37474F.toInt(), // 음표: 먹색
        0xFFF4511E.toInt(), // 불꽃: 다홍
        0xFF64B5F6.toInt(), // 구름: 연하늘
    )

    /** 테두리(외곽선)·세부 무늬용 진한 색 */
    private val DARK = IntArray(SYMBOL_COUNT) { Theme.shade(COLORS[it], 0.6f) }

    private val shapes = Array(SYMBOL_COUNT) { Path() }
    /** 세부 무늬 (하이라이트·잎맥·보석·불꽃 속 등) */
    private val details = Array(SYMBOL_COUNT) { Path() }
    /** 세부 무늬 색 */
    private val detailColor = IntArray(SYMBOL_COUNT)
    /** 세부 무늬를 선으로 그리는지 (아니면 채움) */
    private val detailStroke = BooleanArray(SYMBOL_COUNT)
    /** 외곽선을 그리는지 (겹친 조각으로 만든 그림은 안쪽 선이 보여서 생략) */
    private val outline = BooleanArray(SYMBOL_COUNT) { true }

    private val tmpRect = RectF()

    init {
        buildStar(shapes[0])
        detailColor[0] = 0xAAFFF8E1.toInt()
        details[0].addCircle(-0.18f, -0.12f, 0.12f, Path.Direction.CW)

        buildHeart(shapes[1])
        detailColor[1] = 0x99FFFFFF.toInt()
        details[1].addCircle(-0.48f, -0.38f, 0.16f, Path.Direction.CW)

        crescent(shapes[2], -0.08f, 0.04f, 0.88f, 0.18f, -0.16f, 0.74f)
        detailColor[2] = 0x66FFFFFF
        details[2].addCircle(-0.62f, 0.1f, 0.1f, Path.Direction.CW)
        details[2].addCircle(-0.42f, 0.52f, 0.07f, Path.Direction.CW)

        buildSun(shapes[3])
        detailColor[3] = 0xFFFFD54F.toInt()
        details[3].addCircle(0f, 0f, 0.34f, Path.Direction.CW)

        buildBolt(shapes[4])
        detailColor[4] = 0x88FFFFFF.toInt()
        details[4].moveTo(0.12f, -0.82f)
        details[4].lineTo(-0.22f, -0.1f)
        detailStroke[4] = true

        buildDrop(shapes[5])
        detailColor[5] = 0xAAFFFFFF.toInt()
        oval(details[5], -0.32f, 0.12f, 0.12f, 0.24f)

        buildLeaf(shapes[6])
        detailColor[6] = 0xFF1B5E20.toInt()
        details[6].moveTo(-0.92f, 0.92f)
        details[6].lineTo(0.55f, -0.55f)
        details[6].moveTo(-0.15f, 0.15f)
        details[6].lineTo(-0.15f, -0.38f)
        details[6].moveTo(-0.15f, 0.15f)
        details[6].lineTo(0.38f, 0.15f)
        details[6].moveTo(0.18f, -0.18f)
        details[6].lineTo(0.18f, -0.58f)
        details[6].moveTo(0.18f, -0.18f)
        details[6].lineTo(0.58f, -0.18f)
        detailStroke[6] = true

        buildDiamond(shapes[7])
        detailColor[7] = 0xCCFFFFFF.toInt()
        details[7].moveTo(-0.95f, -0.18f)
        details[7].lineTo(0.95f, -0.18f)
        details[7].moveTo(-0.5f, -0.62f)
        details[7].lineTo(-0.3f, -0.18f)
        details[7].lineTo(0f, 0.88f)
        details[7].lineTo(0.3f, -0.18f)
        details[7].lineTo(0.5f, -0.62f)
        details[7].moveTo(0f, -0.62f)
        details[7].lineTo(-0.3f, -0.18f)
        details[7].moveTo(0f, -0.62f)
        details[7].lineTo(0.3f, -0.18f)
        detailStroke[7] = true

        buildCrown(shapes[8])
        detailColor[8] = 0xFFFFE082.toInt()
        details[8].addCircle(-0.92f, -0.56f, 0.13f, Path.Direction.CW)
        details[8].addCircle(0f, -0.8f, 0.14f, Path.Direction.CW)
        details[8].addCircle(0.92f, -0.56f, 0.13f, Path.Direction.CW)
        details[8].addRect(-0.84f, 0.42f, 0.84f, 0.52f, Path.Direction.CW)
        details[8].addCircle(0f, 0.2f, 0.11f, Path.Direction.CW)

        buildNote(shapes[9])
        outline[9] = false
        detailColor[9] = 0x77FFFFFF
        oval(details[9], -0.5f, 0.5f, 0.1f, 0.06f)

        buildFire(shapes[10])
        detailColor[10] = 0xFFFFCA28.toInt()
        buildFireCore(details[10])

        buildCloud(shapes[11])
        outline[11] = false
        detailColor[11] = 0xCCFFFFFF.toInt()
        details[11].addCircle(0.02f, -0.25f, 0.2f, Path.Direction.CW)
        details[11].addCircle(-0.42f, 0.0f, 0.13f, Path.Direction.CW)
    }

    // ── 그림 만들기 (처음 한 번) ──

    private fun buildStar(p: Path) {
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val r = if (k % 2 == 0) 1f else 0.42f
            val x = (r * cos(a)).toFloat()
            val y = (r * sin(a)).toFloat() + 0.08f
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    private fun buildHeart(p: Path) {
        // 아래 꼭짓점 → 왼쪽 볼록 → 오른쪽 볼록 → 다시 아래
        p.moveTo(0f, 0.92f)
        p.lineTo(-0.9f, -0.06f)
        tmpRect.set(-0.98f, -0.84f, 0.02f, 0.16f)
        p.arcTo(tmpRect, 150f, 190f)
        tmpRect.set(-0.02f, -0.84f, 0.98f, 0.16f)
        p.arcTo(tmpRect, 200f, 190f)
        p.close()
    }

    /** 초승달: (cx1,cy1) 반지름 r1 원에서 (cx2,cy2) 반지름 r2 원을 파낸 모양 */
    private fun crescent(p: Path, cx1: Float, cy1: Float, r1: Float, cx2: Float, cy2: Float, r2: Float) {
        val dx = cx2 - cx1
        val dy = cy2 - cy1
        val d = sqrt(dx * dx + dy * dy)
        val theta = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val alpha = Math.toDegrees(acos(((d * d + r1 * r1 - r2 * r2) / (2 * d * r1)).toDouble())).toFloat()
        val beta = Math.toDegrees(acos(((d * d + r2 * r2 - r1 * r1) / (2 * d * r2)).toDouble())).toFloat()
        tmpRect.set(cx1 - r1, cy1 - r1, cx1 + r1, cy1 + r1)
        p.arcTo(tmpRect, theta + alpha, 360f - 2 * alpha, true)
        tmpRect.set(cx2 - r2, cy2 - r2, cx2 + r2, cy2 + r2)
        p.arcTo(tmpRect, theta + 180f + beta, -2 * beta)
        p.close()
    }

    private fun buildSun(p: Path) {
        p.addCircle(0f, 0f, 0.5f, Path.Direction.CW)
        for (k in 0 until 8) {
            val a = k * Math.PI / 4
            val w = 0.17
            p.moveTo((0.62 * cos(a - w)).toFloat(), (0.62 * sin(a - w)).toFloat())
            p.lineTo((1.0 * cos(a)).toFloat(), (1.0 * sin(a)).toFloat())
            p.lineTo((0.62 * cos(a + w)).toFloat(), (0.62 * sin(a + w)).toFloat())
            p.close()
        }
    }

    private fun buildBolt(p: Path) {
        p.moveTo(0.25f, -1f)
        p.lineTo(-0.6f, 0.12f)
        p.lineTo(-0.04f, 0.12f)
        p.lineTo(-0.3f, 1f)
        p.lineTo(0.62f, -0.18f)
        p.lineTo(0.06f, -0.18f)
        p.lineTo(0.4f, -1f)
        p.close()
    }

    private fun buildDrop(p: Path) {
        p.moveTo(0f, -0.98f)
        p.cubicTo(0.22f, -0.6f, 0.68f, -0.15f, 0.68f, 0.3f)
        tmpRect.set(-0.68f, -0.38f, 0.68f, 0.98f)
        p.arcTo(tmpRect, 0f, 180f)
        p.cubicTo(-0.68f, -0.15f, -0.22f, -0.6f, 0f, -0.98f)
        p.close()
    }

    private fun buildLeaf(p: Path) {
        p.moveTo(-0.72f, 0.72f)
        p.cubicTo(-0.95f, -0.25f, -0.25f, -0.95f, 0.85f, -0.85f)
        p.cubicTo(0.95f, 0.25f, 0.25f, 0.95f, -0.72f, 0.72f)
        p.close()
    }

    private fun buildDiamond(p: Path) {
        p.moveTo(-0.5f, -0.62f)
        p.lineTo(0.5f, -0.62f)
        p.lineTo(0.95f, -0.18f)
        p.lineTo(0f, 0.88f)
        p.lineTo(-0.95f, -0.18f)
        p.close()
    }

    private fun buildCrown(p: Path) {
        p.moveTo(-0.84f, 0.66f)
        p.lineTo(-0.92f, -0.5f)
        p.lineTo(-0.45f, -0.02f)
        p.lineTo(0f, -0.74f)
        p.lineTo(0.45f, -0.02f)
        p.lineTo(0.92f, -0.5f)
        p.lineTo(0.84f, 0.66f)
        p.close()
    }

    private fun buildNote(p: Path) {
        // 기울어진 타원 머리 (점으로 근사 — 회전 행렬 없이)
        val n = 28
        val cx = -0.3f
        val cy = 0.58f
        val ang = Math.toRadians(-24.0)
        for (k in 0 until n) {
            val t = k * 2 * Math.PI / n
            val ex = 0.4 * cos(t)
            val ey = 0.28 * sin(t)
            val x = (cx + ex * cos(ang) - ey * sin(ang)).toFloat()
            val y = (cy + ex * sin(ang) + ey * cos(ang)).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        // 기둥
        p.addRect(0.0f, -0.92f, 0.13f, 0.55f, Path.Direction.CW)
        // 꼬리
        p.moveTo(0.0f, -0.92f)
        p.cubicTo(0.15f, -0.55f, 0.78f, -0.5f, 0.62f, 0.05f)
        p.cubicTo(0.56f, -0.28f, 0.32f, -0.38f, 0.1f, -0.42f)
        p.close()
    }

    private fun buildFire(p: Path) {
        p.moveTo(0.05f, -1f)
        p.cubicTo(0.35f, -0.58f, 0.8f, -0.28f, 0.76f, 0.3f)
        p.cubicTo(0.73f, 0.76f, 0.38f, 0.96f, 0f, 0.96f)
        p.cubicTo(-0.38f, 0.96f, -0.73f, 0.76f, -0.76f, 0.3f)
        p.cubicTo(-0.78f, -0.06f, -0.56f, -0.28f, -0.42f, -0.55f)
        p.cubicTo(-0.36f, -0.28f, -0.22f, -0.16f, -0.12f, -0.2f)
        p.cubicTo(-0.18f, -0.52f, -0.1f, -0.78f, 0.05f, -1f)
        p.close()
    }

    private fun buildFireCore(p: Path) {
        p.moveTo(0f, -0.12f)
        p.cubicTo(0.2f, 0.12f, 0.42f, 0.32f, 0.4f, 0.58f)
        p.cubicTo(0.38f, 0.82f, 0.2f, 0.9f, 0f, 0.9f)
        p.cubicTo(-0.2f, 0.9f, -0.38f, 0.82f, -0.4f, 0.58f)
        p.cubicTo(-0.42f, 0.32f, -0.2f, 0.12f, 0f, -0.12f)
        p.close()
    }

    private fun buildCloud(p: Path) {
        p.addCircle(-0.55f, 0.22f, 0.36f, Path.Direction.CW)
        p.addCircle(-0.15f, -0.08f, 0.48f, Path.Direction.CW)
        p.addCircle(0.36f, -0.12f, 0.42f, Path.Direction.CW)
        p.addCircle(0.66f, 0.24f, 0.32f, Path.Direction.CW)
        tmpRect.set(-0.55f, 0.12f, 0.66f, 0.56f)
        p.addRoundRect(tmpRect, 0.2f, 0.2f, Path.Direction.CW)
    }

    private fun oval(p: Path, cx: Float, cy: Float, rx: Float, ry: Float) {
        tmpRect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        p.addOval(tmpRect, Path.Direction.CW)
    }

    // ── 그리기 (할당 없음) ──

    /** 그림 sym 을 (cx, cy) 중심, 반지름 half 크기로 */
    fun drawSymbol(c: Canvas, sym: Int, cx: Float, cy: Float, half: Float, paint: Paint) {
        c.save()
        c.translate(cx, cy)
        c.scale(half, half)
        paint.shader = null
        paint.style = Paint.Style.FILL
        // 살짝 그림자
        paint.color = 0x33000000
        c.save()
        c.translate(0.05f, 0.07f)
        c.drawPath(shapes[sym], paint)
        c.restore()
        paint.color = COLORS[sym]
        c.drawPath(shapes[sym], paint)
        if (detailStroke[sym]) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.08f
            paint.strokeCap = Paint.Cap.ROUND
        }
        paint.color = detailColor[sym]
        c.drawPath(details[sym], paint)
        if (outline[sym]) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.07f
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = DARK[sym]
            c.drawPath(shapes[sym], paint)
        }
        paint.style = Paint.Style.FILL
        c.restore()
    }

    /**
     * 카드 앞면: (cx, cy) 중심, 폭 w × 높이 h. 크림색 종이 + 그림 색 테두리 + 그림.
     * highlight > 0 이면 금색 빛 테두리 (짝 맞춤 반짝임).
     */
    fun drawFront(c: Canvas, sym: Int, cx: Float, cy: Float, w: Float, h: Float, paint: Paint, highlight: Float = 0f) {
        val r = minOf(w, h) * 0.14f
        paint.shader = null
        paint.style = Paint.Style.FILL
        if (highlight > 0f) {
            paint.color = Theme.GOLD_LIGHT
            paint.alpha = (200 * highlight).toInt()
            val g = minOf(w, h) * 0.09f * highlight
            c.drawRoundRect(cx - w / 2 - g, cy - h / 2 - g, cx + w / 2 + g, cy + h / 2 + g, r + g, r + g, paint)
            paint.alpha = 255
        }
        paint.color = Theme.PAPER
        c.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, r, r, paint)
        paint.style = Paint.Style.STROKE
        val sw = minOf(w, h) * 0.045f
        paint.strokeWidth = sw
        paint.color = if (highlight > 0f) Theme.GOLD else COLORS[sym]
        c.drawRoundRect(cx - w / 2 + sw * 1.4f, cy - h / 2 + sw * 1.4f, cx + w / 2 - sw * 1.4f, cy + h / 2 - sw * 1.4f, r * 0.7f, r * 0.7f, paint)
        paint.style = Paint.Style.FILL
        drawSymbol(c, sym, cx, cy, minOf(w * 0.36f, h * 0.3f), paint)
    }

    /**
     * 카드 뒷면: 금색 바탕(shader 가 있으면 그 그라데이션 — 카드 중심 기준 좌표) + 안쪽 테두리 + 마름모 격자 + 가운데 메달.
     */
    fun drawBack(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, paint: Paint, shader: Shader?) {
        val r = minOf(w, h) * 0.14f
        paint.style = Paint.Style.FILL
        if (shader != null) {
            // shader 는 카드 중심 (0,0) 기준으로 만들어져 있다
            c.save()
            c.translate(cx, cy)
            paint.color = 0xFFFFFFFF.toInt() // 그라데이션이 반투명해지지 않게 불투명으로
            paint.shader = shader
            c.drawRoundRect(-w / 2, -h / 2, w / 2, h / 2, r, r, paint)
            paint.shader = null
            c.restore()
        } else {
            paint.color = Theme.GOLD
            c.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, r, r, paint)
        }
        val inset = minOf(w, h) * 0.1f
        val l = cx - w / 2 + inset
        val t = cy - h / 2 + inset
        val rr = cx + w / 2 - inset
        val b = cy + h / 2 - inset
        // 마름모 격자 (안쪽 테두리 안으로 잘라서)
        c.save()
        c.clipRect(l, t, rr, b)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = minOf(w, h) * 0.025f
        paint.color = 0x66B8860B
        val step = (rr - l) / 3f
        var x = l - (b - t)
        while (x < rr) {
            c.drawLine(x, t, x + (b - t), b, paint)
            c.drawLine(x + (b - t), t, x, b, paint)
            x += step
        }
        c.restore()
        paint.color = 0xFF8D6E00.toInt()
        paint.strokeWidth = minOf(w, h) * 0.035f
        c.drawRoundRect(l, t, rr, b, r * 0.6f, r * 0.6f, paint)
        // 가운데 메달 + 네 갈래 반짝이
        paint.style = Paint.Style.FILL
        val m = minOf(w, h) * 0.2f
        paint.color = 0xFF8D6E00.toInt()
        c.drawCircle(cx, cy, m, paint)
        paint.color = Theme.GOLD_LIGHT
        c.drawCircle(cx, cy, m * 0.8f, paint)
        paint.color = 0xFFB8860B.toInt()
        tmpRect.set(cx - m * 0.18f, cy - m * 0.62f, cx + m * 0.18f, cy + m * 0.62f)
        c.drawOval(tmpRect, paint)
        tmpRect.set(cx - m * 0.62f, cy - m * 0.18f, cx + m * 0.62f, cy + m * 0.18f)
        c.drawOval(tmpRect, paint)
    }
}
