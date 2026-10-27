package com.siwoo.party.games.liardice.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme

/**
 * 거짓말 주사위 그림 도우미 (컵·작은 주사위·의심 도장). Paint·Path 는 부르는 쪽이 넘긴다 → 할당 없음.
 */
object LiarDiceArt {

    /** 와일드 1 강조 색 (하늘) */
    const val WILD = 0xFF5CE1FF.toInt()
    /** 컵 가죽 색 */
    const val CUP = 0xFF8C3B2A.toInt()

    /**
     * 주사위 컵 (엎어 놓은 원통): 가운데 cx, 아래 끝 bottom, 아래 폭 w, 높이 h.
     * band = 가운데 띠 색 (플레이어 색), tilt = 기울기(도)
     */
    fun drawCup(c: Canvas, cx: Float, bottom: Float, w: Float, h: Float, band: Int, p: Paint, path: Path, tilt: Float = 0f) {
        c.save()
        c.rotate(tilt, cx, bottom)
        p.style = Paint.Style.FILL
        // 그림자
        p.color = 0x55000000
        c.drawOval(cx - w * 0.55f, bottom - h * 0.08f, cx + w * 0.55f, bottom + h * 0.1f, p)
        // 몸통 (아래가 넓은 사다리꼴, 위는 둥글게)
        val top = bottom - h
        path.reset()
        path.moveTo(cx - w * 0.5f, bottom - h * 0.06f)
        path.lineTo(cx - w * 0.38f, top + h * 0.1f)
        path.quadTo(cx, top - h * 0.06f, cx + w * 0.38f, top + h * 0.1f)
        path.lineTo(cx + w * 0.5f, bottom - h * 0.06f)
        path.close()
        p.color = CUP
        c.drawPath(path, p)
        // 왼쪽 반짝임
        p.color = 0x33FFFFFF
        c.drawRect(cx - w * 0.3f, top + h * 0.16f, cx - w * 0.2f, bottom - h * 0.18f, p)
        // 가운데 띠 (플레이어 색) + 금색 테
        p.color = band
        val by = top + h * 0.42f
        c.drawRect(cx - w * 0.445f, by, cx + w * 0.445f, by + h * 0.16f, p)
        p.color = Theme.GOLD
        c.drawRect(cx - w * 0.445f, by - h * 0.025f, cx + w * 0.445f, by, p)
        c.drawRect(cx - w * 0.445f, by + h * 0.16f, cx + w * 0.445f, by + h * 0.185f, p)
        // 아래 테두리
        p.color = 0xFF5A2016.toInt()
        c.drawRoundRect(cx - w * 0.52f, bottom - h * 0.12f, cx + w * 0.52f, bottom, h * 0.05f, h * 0.05f, p)
        // 위 꼭지 금색 점
        p.color = Theme.GOLD_LIGHT
        c.drawCircle(cx, top + h * 0.06f, w * 0.06f, p)
        c.restore()
    }

    /** 작은 주사위 (규칙 그림·아이콘용): 가운데 (cx, cy), 한 변 s, 눈 face(1~6), ring = 강조 테 색 (0 이면 없음) */
    fun drawDie(c: Canvas, cx: Float, cy: Float, s: Float, face: Int, p: Paint, ring: Int = 0, rot: Float = 0f) {
        c.save()
        c.rotate(rot, cx, cy)
        p.style = Paint.Style.FILL
        val r = s * 0.2f
        if (ring != 0) {
            p.color = ring
            c.drawRoundRect(cx - s * 0.66f, cy - s * 0.66f, cx + s * 0.66f, cy + s * 0.66f, r * 1.4f, r * 1.4f, p)
        }
        p.color = 0x55000000
        c.drawRoundRect(cx - s / 2 + s * 0.05f, cy - s / 2 + s * 0.08f, cx + s / 2 + s * 0.05f, cy + s / 2 + s * 0.08f, r, r, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, r, r, p)
        p.color = if (face == 1) 0xFFD32F2F.toInt() else Theme.INK
        val f = face.coerceIn(1, 6)
        val pr = s * (if (f == 1) 0.13f else 0.095f)
        val pips = PIPS[f - 1]
        var k = 0
        while (k < pips.size) {
            c.drawCircle(cx + (pips[k] - 0.5f) * s, cy + (pips[k + 1] - 0.5f) * s, pr, p)
            k += 2
        }
        c.restore()
    }

    /** 비스듬한 빨간 "의심!" 도장 (글자 크기 size) */
    fun drawDoubtStamp(c: Canvas, cx: Float, cy: Float, size: Float, p: Paint) {
        c.save()
        c.rotate(-12f, cx, cy)
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        p.textSize = size
        val w = p.measureText(DOUBT) / 2f + size * 0.3f
        p.style = Paint.Style.FILL
        p.color = 0x33FF4D5E
        c.drawRoundRect(cx - w, cy - size * 0.85f, cx + w, cy + size * 0.4f, size * 0.2f, size * 0.2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.1f
        p.color = Theme.RED
        c.drawRoundRect(cx - w, cy - size * 0.85f, cx + w, cy + size * 0.4f, size * 0.2f, size * 0.2f, p)
        p.style = Paint.Style.FILL
        c.drawText(DOUBT, cx, cy + size * 0.05f, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
        c.restore()
    }

    const val DOUBT = "의심!"

    private val PIPS = arrayOf(
        floatArrayOf(0.5f, 0.5f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.25f, 0.75f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.22f, 0.73f, 0.22f, 0.27f, 0.5f, 0.73f, 0.5f, 0.27f, 0.78f, 0.73f, 0.78f),
    )
}
