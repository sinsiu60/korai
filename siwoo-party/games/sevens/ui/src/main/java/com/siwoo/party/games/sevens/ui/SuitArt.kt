package com.siwoo.party.games.sevens.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.cards.Suit

/**
 * 무늬(♠♥♦♣) 그림: 높이 1 짜리 Path 를 한 번만 만들어 두고 크기·위치는 Canvas 변환으로 맞춘다 (그릴 때 할당 없음).
 * 모양은 공통 카드 그림(CardBitmaps)과 같다.
 */
object SuitArt {

    const val RED = CardBitmaps.RED
    const val BLACK = CardBitmaps.BLACK

    private val spade = Path().apply {
        moveTo(0f, -0.5f)
        cubicTo(0.12f, -0.33f, 0.48f, -0.16f, 0.48f, 0.07f)
        cubicTo(0.48f, 0.27f, 0.25f, 0.34f, 0.06f, 0.2f)
        quadTo(0.08f, 0.4f, 0.2f, 0.5f)
        lineTo(-0.2f, 0.5f)
        quadTo(-0.08f, 0.4f, -0.06f, 0.2f)
        cubicTo(-0.25f, 0.34f, -0.48f, 0.27f, -0.48f, 0.07f)
        cubicTo(-0.48f, -0.16f, -0.12f, -0.33f, 0f, -0.5f)
        close()
    }

    private val heart = Path().apply {
        moveTo(0f, 0.47f)
        cubicTo(-0.16f, 0.3f, -0.5f, 0.08f, -0.5f, -0.17f)
        cubicTo(-0.5f, -0.4f, -0.2f, -0.52f, 0f, -0.28f)
        cubicTo(0.2f, -0.52f, 0.5f, -0.4f, 0.5f, -0.17f)
        cubicTo(0.5f, 0.08f, 0.16f, 0.3f, 0f, 0.47f)
        close()
    }

    private val diamond = Path().apply {
        moveTo(0f, -0.5f)
        quadTo(0.16f, -0.18f, 0.38f, 0f)
        quadTo(0.16f, 0.18f, 0f, 0.5f)
        quadTo(-0.16f, 0.18f, -0.38f, 0f)
        quadTo(-0.16f, -0.18f, 0f, -0.5f)
        close()
    }

    private val club = Path().apply {
        val r = 0.205f
        addCircle(0f, -0.27f, r, Path.Direction.CW)
        addCircle(-0.25f, 0.05f, r, Path.Direction.CW)
        addCircle(0.25f, 0.05f, r, Path.Direction.CW)
        addCircle(0f, -0.02f, 0.12f, Path.Direction.CW)
        moveTo(-0.05f, 0.05f)
        quadTo(-0.06f, 0.38f, -0.2f, 0.5f)
        lineTo(0.2f, 0.5f)
        quadTo(0.06f, 0.38f, 0.05f, 0.05f)
        close()
    }

    /** 별 (조커·"아무 무늬") 높이 1 */
    private val star = Path().apply {
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rad = if (k % 2 == 0) 0.5 else 0.21
            val x = (rad * Math.cos(a)).toFloat()
            val y = (rad * Math.sin(a)).toFloat() + 0.04f
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun color(suit: Suit): Int = if (suit.isRed) RED else BLACK

    fun path(suit: Suit): Path = when (suit) {
        Suit.SPADE -> spade
        Suit.HEART -> heart
        Suit.DIAMOND -> diamond
        Suit.CLUB -> club
    }

    /** 가운데 (cx, cy), 높이 size 로 무늬를 그린다 (paint 색·스타일은 부르는 쪽이 정함) */
    fun draw(c: Canvas, suit: Suit, cx: Float, cy: Float, size: Float, paint: Paint) {
        drawPath(c, path(suit), cx, cy, size, paint)
    }

    fun drawStar(c: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        drawPath(c, star, cx, cy, size, paint)
    }

    private fun drawPath(c: Canvas, p: Path, cx: Float, cy: Float, size: Float, paint: Paint) {
        val sw = paint.strokeWidth
        c.save()
        c.translate(cx, cy)
        c.scale(size, size)
        if (paint.style != Paint.Style.FILL) paint.strokeWidth = sw / size
        c.drawPath(p, paint)
        paint.strokeWidth = sw
        c.restore()
    }
}
