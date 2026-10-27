package com.siwoo.party.games.traceshape

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.traceshape.ui.ShapeArt
import com.siwoo.party.games.traceshape.ui.TraceShapeActivity

/** 도형 따라 그리기: 모음집 등록 정보 */
object TraceShapeModule : GameModule {
    override val id = "traceshape"
    override val name = "도형 따라 그리기"
    override val tagline = "점선 도형을 손가락으로 쭉! 정확하고 빠르게"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "3분"
    override val color = 0xFF1E9C8F.toInt()
    override val activityClass = TraceShapeActivity::class.java

    private const val NAVY = 0xFF0E1640.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DASH_COLOR = 0xD9FFFFFF.toInt()
    // 그림용 도형·도구 (그릴 때 만들지 않게 미리)
    private val star = Shapes.make(ShapeKind.STAR)
    private val heart = Shapes.make(ShapeKind.HEART)
    private val circle = Shapes.make(ShapeKind.CIRCLE)
    private val zigzag = Shapes.make(ShapeKind.ZIGZAG)
    private val spiral = Shapes.make(ShapeKind.SPIRAL)
    private val path = Path()
    private val tmp = FloatArray(4)
    /** 상자 좌표(0..1) 기준 점선 — 캔버스를 상자 크기로 늘려 그린다 */
    private val DASH = DashPathEffect(floatArrayOf(0.04f, 0.032f), 0f)
    private const val DONE = "완료!"
    private const val X10 = "×1.0"
    private const val X15 = "×1.5"
    private val LIMIT = "${GameConfig.ROUND_MS / 1000}초"
    private val RESULT_SHAPES = arrayOf(star, zigzag, spiral)
    private val RESULT_U = floatArrayOf(1f, 1f, 0.6f)
    private val RESULTS = arrayOf("92% × 1.4 = 129", "85% × 1.1 = 94", "60% × 1.0 = 60")
    private val RESULT_COLORS = intArrayOf(Theme.GOLD, WHITE, Theme.TEXT_SUB)

    private fun text(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textAlign = align
        p.textSize = size
        p.isFakeBoldText = true
        c.drawText(s, x, y + size * 0.36f, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    /** 점선 도형 + 시작점 + 화살촉 + (u1 까지) 색 그림. 상자 (l, t, 한 변 s) — 상자 좌표로 늘려 그림 */
    private fun traced(c: Canvas, p: Paint, shape: Shape, l: Float, t: Float, s: Float, ink: Int, u1: Float, arrow: Boolean = true) {
        c.save()
        c.translate(l, t)
        c.scale(s, s)
        val w = 0.03f
        ShapeArt.build(shape, path, 0f, 0f, 1f, tmp = tmp)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = w
        p.color = DASH_COLOR
        p.pathEffect = DASH
        c.drawPath(path, p)
        p.pathEffect = null
        if (u1 > 0f) {
            ShapeArt.build(shape, path, 0f, 0f, 1f, 0f, u1, tmp)
            p.color = ink
            p.strokeWidth = w * 1.5f
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        p.color = NAVY
        c.drawCircle(shape.startX, shape.startY, 0.05f, p)
        p.color = Theme.GOLD
        c.drawCircle(shape.startX, shape.startY, 0.037f, p)
        if (arrow) {
            ShapeArt.arrow(shape, path, 0.08f, 0.05f, 0f, 0f, 1f, tmp)
            c.drawPath(path, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
        c.restore()
    }

    /** 손가락 끝 (반투명 흰 원) */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x99FFFFFF.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.15f
        p.color = WHITE
        c.drawCircle(x, y, r, p)
    }

    /** 구역 한 칸 (가운데 cx, cy, 폭 w, 높이 h, 뒤집힘, 색) — 왼쪽 점선 하트 상자 + 오른쪽 안내 칸 */
    private fun zone(c: Canvas, p: Paint, cx: Float, cy: Float, w: Float, h: Float, flip: Boolean, color: Int, u1: Float) {
        c.save()
        c.translate(cx, cy)
        if (flip) c.rotate(180f)
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = 50
        c.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, h * 0.12f, h * 0.12f, p)
        p.alpha = 255
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.03f
        p.color = color
        c.drawRoundRect(-w / 2f, -h / 2f, w / 2f, h / 2f, h * 0.12f, h * 0.12f, p)
        val s = h * 0.9f
        val l = -w / 2f + h * 0.08f
        val t = -s / 2f
        traced(c, p, heart, l, t, s, color, u1)
        if (u1 > 0f) {
            heart.pointAt(u1, tmp)
            finger(c, p, l + tmp[0] * s, t + tmp[1] * s, s * 0.08f)
        }
        // 안내 칸 (글자 대신 막대 세 줄)
        val pl = l + s + h * 0.1f
        val pr = w / 2f - h * 0.1f
        p.style = Paint.Style.FILL
        p.color = 0x40000000
        c.drawRoundRect(pl, -h * 0.4f, pr, h * 0.4f, h * 0.08f, h * 0.08f, p)
        for (k in 0..2) {
            val y = -h * 0.22f + k * h * 0.22f
            val half = (pr - pl) * (if (k == 0) 0.3f else 0.38f)
            p.color = if (k == 0) WHITE else 0x80FFFFFF.toInt()
            c.drawRoundRect((pl + pr) / 2f - half, y - h * 0.04f, (pl + pr) / 2f + half, y + h * 0.04f, h * 0.04f, h * 0.04f, p)
        }
        c.restore()
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = color
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 점선 별 + 반쯤 따라 그린 빨간 선 + 손가락
        val s = r * 1.42f
        val l = cx - s / 2f
        val t = cy - s / 2f + r * 0.02f
        traced(canvas, paint, star, l, t, s, Theme.PLAYER_COLORS[0], 0.62f, arrow = false)
        star.pointAt(0.62f, tmp)
        finger(canvas, paint, l + tmp[0] * s, t + tmp[1] * s, s * 0.09f)
        paint.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard(
            "점선 도형을 따라 쭉!",
            "구역마다 똑같은 점선 도형이 나와요\n(원·별·하트·지그재그·나선·물결·8자…).\n금색 점에서 화살표 쪽으로 따라 그려요.\n손을 뗐다 다시 대면 이어서 그려져요.",
        ) { c, w, h, p ->
            zone(c, p, w * 0.5f, h * 0.255f, w * 0.9f, h * 0.45f, true, Theme.PLAYER_COLORS[1], 0f)
            zone(c, p, w * 0.5f, h * 0.745f, w * 0.9f, h * 0.45f, false, Theme.PLAYER_COLORS[0], 0.55f)
        },
        RuleCard(
            "다 그리면 자동 [완료]",
            "점선을 거의 다 따라가 끝점에 닿으면\n자동 완료! 빨리 끝낼수록 속도 보너스\n(${GameConfig.BONUS_FAST_MS / 1000}초 안 ×${GameConfig.BONUS_MAX} → ${GameConfig.BONUS_SLOW_MS / 1000}초 ×1.0).\n제한 $LIMIT — 못 끝내면 그린 만큼만\n점수 (보너스 없음).",
        ) { c, w, h, p ->
            // 다 그린 원 + 완료 도장
            val s = h * 0.84f
            val l = w * 0.08f
            val t = h * 0.08f
            traced(c, p, circle, l, t, s, Theme.GREEN, 1f, arrow = false)
            c.save()
            c.rotate(-12f, l + s / 2f, t + s / 2f)
            p.style = Paint.Style.FILL
            p.color = 0xCC0E1640.toInt()
            c.drawRoundRect(l + s * 0.14f, t + s * 0.36f, l + s * 0.86f, t + s * 0.64f, s * 0.06f, s * 0.06f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.025f
            p.color = Theme.GOLD
            c.drawRoundRect(l + s * 0.14f, t + s * 0.36f, l + s * 0.86f, t + s * 0.64f, s * 0.06f, s * 0.06f, p)
            text(c, p, DONE, l + s / 2f, t + s / 2f, s * 0.17f, Theme.GOLD)
            c.restore()
            // 속도 막대: 빠름 ×1.5 (위) → 느림 ×1.0 (아래)
            val bx = w * 0.66f
            val bw = w * 0.07f
            val top = h * 0.14f
            val bot = h * 0.86f
            p.style = Paint.Style.FILL
            p.color = 0x55000000
            c.drawRoundRect(bx, top, bx + bw, bot, bw / 2f, bw / 2f, p)
            p.color = Theme.GREEN
            c.drawRoundRect(bx, top + (bot - top) * 0.2f, bx + bw, bot, bw / 2f, bw / 2f, p)
            p.color = Theme.GOLD
            c.drawCircle(bx + bw / 2f, top + (bot - top) * 0.2f, bw * 0.7f, p)
            text(c, p, X15, bx + bw * 1.6f, top + h * 0.02f, h * 0.1f, Theme.GOLD, Paint.Align.LEFT)
            text(c, p, X10, bx + bw * 1.6f, bot - h * 0.02f, h * 0.1f, Theme.TEXT_SUB, Paint.Align.LEFT)
            text(c, p, LIMIT, bx + bw * 1.6f, (top + bot) / 2f, h * 0.09f, WHITE, Paint.Align.LEFT)
        },
        RuleCard(
            "정확도 × 속도 = 점수",
            "점선에 가까울수록, 빠짐없이 따라갈수록\n정확도가 높아요 (한 곳 낙서는 0점 가까이,\n방향은 상관없어요). 라운드마다 모두의\n그림을 겹쳐 비교! ${GameConfig.ROUNDS}라운드 합계로 순위\n(같은 점수면 같은 순위)",
        ) { c, w, h, p ->
            for (k in 0..2) {
                val t = h * (0.06f + k * 0.31f)
                val b = t + h * 0.26f
                val col = RESULT_COLORS[k]
                p.style = Paint.Style.FILL
                p.color = Theme.shade(Theme.CARD_LIGHT, 0.8f)
                c.drawRoundRect(w * 0.04f, t, w * 0.96f, b, h * 0.05f, h * 0.05f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * (if (k == 0) 0.016f else 0.008f)
                p.color = if (k == 0) Theme.GOLD else 0x40FFFFFF
                c.drawRoundRect(w * 0.04f, t, w * 0.96f, b, h * 0.05f, h * 0.05f, p)
                // 플레이어 색 점 + 작은 그림 + 계산
                p.style = Paint.Style.FILL
                p.color = Theme.PLAYER_COLORS[k]
                c.drawCircle(w * 0.1f, (t + b) / 2f, h * 0.045f, p)
                val s = (b - t) * 0.9f
                traced(c, p, RESULT_SHAPES[k], w * 0.16f, t + (b - t - s) / 2f, s, Theme.PLAYER_COLORS[k], RESULT_U[k], arrow = false)
                text(c, p, RESULTS[k], w * 0.62f, (t + b) / 2f, h * 0.1f, col)
            }
        },
    )
}
