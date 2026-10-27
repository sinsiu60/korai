package com.siwoo.party.games.drawing.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme

/** 그림 맞히기 그림 도우미 (아이콘·규칙 카드). Canvas 도형만, 할당 없음 (Path 는 부르는 쪽이 넘김) */
object DrawingArt {
    /** 카테고리 색 (동물·음식·물건·자연·탈것·장소) */
    val CATEGORY_COLORS = intArrayOf(
        0xFFE0832A.toInt(), 0xFFE04C6A.toInt(), 0xFF8E5BE0.toInt(),
        0xFF2E9E44.toInt(), 0xFF3D7BFF.toInt(), 0xFF1FB5A8.toInt(),
    )

    fun categoryColor(k: Int): Int = CATEGORY_COLORS[k.coerceIn(0, CATEGORY_COLORS.size - 1)]

    /** 이젤 위 종이 (가운데 cx,cy / 폭 w / 높이 h) */
    fun drawEasel(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w * 0.06f
        p.color = 0xFF9A6A3A.toInt()
        c.drawLine(cx - w * 0.32f, cy + h * 0.5f, cx - w * 0.12f, cy - h * 0.2f, p)
        c.drawLine(cx + w * 0.32f, cy + h * 0.5f, cx + w * 0.12f, cy - h * 0.2f, p)
        p.style = Paint.Style.FILL
        p.color = DrawCanvasView.PAPER
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h * 0.3f, w * 0.04f, w * 0.04f, p)
        p.color = 0xFF9A6A3A.toInt()
        c.drawRect(cx - w * 0.52f, cy + h * 0.28f, cx + w * 0.52f, cy + h * 0.34f, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** 종이 위 낙서 고양이 (선 그림) */
    fun drawDoodleCat(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint, path: Path) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = s * 0.07f
        p.color = color
        c.drawCircle(cx, cy, s * 0.42f, p)
        path.reset()
        path.moveTo(cx - s * 0.36f, cy - s * 0.2f)
        path.lineTo(cx - s * 0.3f, cy - s * 0.62f)
        path.lineTo(cx - s * 0.08f, cy - s * 0.4f)
        path.moveTo(cx + s * 0.36f, cy - s * 0.2f)
        path.lineTo(cx + s * 0.3f, cy - s * 0.62f)
        path.lineTo(cx + s * 0.08f, cy - s * 0.4f)
        c.drawPath(path, p)
        // 수염
        p.strokeWidth = s * 0.04f
        c.drawLine(cx - s * 0.62f, cy + s * 0.04f, cx - s * 0.2f, cy + s * 0.1f, p)
        c.drawLine(cx + s * 0.62f, cy + s * 0.04f, cx + s * 0.2f, cy + s * 0.1f, p)
        c.drawLine(cx - s * 0.6f, cy + s * 0.2f, cx - s * 0.2f, cy + s * 0.16f, p)
        c.drawLine(cx + s * 0.6f, cy + s * 0.2f, cx + s * 0.2f, cy + s * 0.16f, p)
        p.style = Paint.Style.FILL
        c.drawCircle(cx - s * 0.15f, cy - s * 0.05f, s * 0.06f, p)
        c.drawCircle(cx + s * 0.15f, cy - s * 0.05f, s * 0.06f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
    }

    /** 연필 (끝이 (tx,ty), 각도 deg) */
    fun drawPencil(c: Canvas, tx: Float, ty: Float, len: Float, deg: Float, p: Paint, path: Path) {
        c.save()
        c.rotate(deg, tx, ty)
        val w = len * 0.16f
        p.style = Paint.Style.FILL
        p.color = 0xFFFFC93C.toInt()
        c.drawRect(tx + len * 0.22f, ty - w / 2f, tx + len * 0.9f, ty + w / 2f, p)
        p.color = 0xFFFF8FB1.toInt()
        c.drawRoundRect(tx + len * 0.88f, ty - w / 2f, tx + len, ty + w / 2f, w * 0.2f, w * 0.2f, p)
        p.color = 0xFFF2D2A9.toInt()
        path.reset()
        path.moveTo(tx, ty)
        path.lineTo(tx + len * 0.22f, ty - w / 2f)
        path.lineTo(tx + len * 0.22f, ty + w / 2f)
        path.close()
        c.drawPath(path, p)
        p.color = Theme.INK
        path.reset()
        path.moveTo(tx, ty)
        path.lineTo(tx + len * 0.08f, ty - w * 0.18f)
        path.lineTo(tx + len * 0.08f, ty + w * 0.18f)
        path.close()
        c.drawPath(path, p)
        c.restore()
    }

    /** 사람 (머리 + 몸) */
    fun drawPerson(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(cx - s * 0.5f, cy, cx + s * 0.5f, cy + s * 0.9f, s * 0.4f, s * 0.4f, p)
        p.color = 0xFFFFD2A8.toInt()
        c.drawCircle(cx, cy - s * 0.3f, s * 0.36f, p)
    }
}
