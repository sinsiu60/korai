package com.siwoo.party.games.oddone.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.games.oddone.Shape

/**
 * 모양 그림 (Canvas 코드). 모든 모양은 반지름 1 상자 안, 가운데 (0,0), 위쪽이 -y 로 한 번만 만들어 둔다.
 * 그릴 때 이동·회전·확대만 하므로 onDraw 에서 할당이 없다.
 */
object ShapeArt {
    private val paths: Array<Path> = Array(Shape.values().size) { build(Shape.values()[it]) }

    fun path(shape: Shape): Path = paths[shape.ordinal]

    /**
     * (cx, cy) 에 반지름 radius 로 rotation(도) 만큼 돌려 그린다.
     * fill 은 color 로 채우고, outline(px) 두께의 어두운 테두리(모든 칸 같은 색).
     */
    fun draw(c: Canvas, shape: Shape, cx: Float, cy: Float, radius: Float, rotation: Float, color: Int, outline: Float, fill: Paint, stroke: Paint) {
        if (radius <= 0f) return
        c.save()
        c.translate(cx, cy)
        c.rotate(rotation)
        c.scale(radius, radius)
        val p = paths[shape.ordinal]
        fill.color = color
        c.drawPath(p, fill)
        stroke.color = OUTLINE
        stroke.strokeWidth = outline / radius
        c.drawPath(p, stroke)
        c.restore()
    }

    /** 페인트 하나로 그리기 (아이콘·규칙 그림용): 채우기 → 테두리, 끝나면 FILL 로 돌려 둔다 */
    fun drawOne(c: Canvas, shape: Shape, cx: Float, cy: Float, radius: Float, rotation: Float, color: Int, outline: Float, p: Paint) {
        if (radius <= 0f) return
        c.save()
        c.translate(cx, cy)
        c.rotate(rotation)
        c.scale(radius, radius)
        val path = paths[shape.ordinal]
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = outline / radius
        p.color = OUTLINE
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    /** 테두리 색 (어두운 남색) — 칸마다 같아서 색 문제에서 힌트가 되지 않는다 */
    const val OUTLINE = 0xE6050A24.toInt()

    private fun build(s: Shape): Path {
        val p = Path()
        when (s) {
            Shape.CIRCLE -> p.addCircle(0f, 0f, 0.9f, Path.Direction.CW)
            Shape.STAR -> {
                for (k in 0 until 10) {
                    val a = -Math.PI / 2 + k * Math.PI / 5
                    val r = if (k % 2 == 0) 1f else 0.45f
                    val x = (r * Math.cos(a)).toFloat()
                    val y = (r * Math.sin(a)).toFloat() + 0.08f
                    if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            Shape.HEART -> {
                p.moveTo(0f, 0.92f)
                p.cubicTo(-1.3f, 0.02f, -0.78f, -1.12f, 0f, -0.48f)
                p.cubicTo(0.78f, -1.12f, 1.3f, 0.02f, 0f, 0.92f)
                p.close()
            }
            Shape.ARROW -> {
                p.moveTo(0f, -1f)
                p.lineTo(0.85f, -0.08f)
                p.lineTo(0.34f, -0.08f)
                p.lineTo(0.34f, 0.95f)
                p.lineTo(-0.34f, 0.95f)
                p.lineTo(-0.34f, -0.08f)
                p.lineTo(-0.85f, -0.08f)
                p.close()
            }
            Shape.TRIANGLE -> {
                p.moveTo(0f, -1f)
                p.lineTo(0.92f, 0.6f)
                p.lineTo(-0.92f, 0.6f)
                p.close()
            }
            Shape.LEAF -> {
                // 잎몸 (끝이 위로) + 오른쪽으로 살짝 휜 잎자루
                p.moveTo(0f, -1f)
                p.cubicTo(0.95f, -0.55f, 0.75f, 0.45f, 0f, 0.62f)
                p.cubicTo(-0.75f, 0.45f, -0.95f, -0.55f, 0f, -1f)
                p.close()
                p.moveTo(-0.07f, 0.55f)
                p.quadTo(0.05f, 0.85f, 0.32f, 1f)
                p.lineTo(0.38f, 0.9f)
                p.quadTo(0.17f, 0.78f, 0.09f, 0.55f)
                p.close()
            }
            Shape.MOON -> {
                // 큰 원에서 오른쪽 위로 비킨 원을 뺀 초승달
                val outer = Path().apply { addCircle(0f, 0f, 0.95f, Path.Direction.CW) }
                val cut = Path().apply { addCircle(0.42f, -0.22f, 0.78f, Path.Direction.CW) }
                p.op(outer, cut, Path.Op.DIFFERENCE)
            }
            Shape.DIAMOND -> {
                p.moveTo(0f, -1f)
                p.lineTo(0.62f, 0f)
                p.lineTo(0f, 1f)
                p.lineTo(-0.62f, 0f)
                p.close()
            }
        }
        return p
    }
}
