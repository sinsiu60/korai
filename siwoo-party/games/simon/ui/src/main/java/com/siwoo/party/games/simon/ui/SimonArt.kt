package com.siwoo.party.games.simon.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/**
 * 사이먼 판 그림 (게임 화면·허브 아이콘·규칙 카드 공용, 할당 없음).
 * 검은 둥근 판 위에 4색 부채꼴 버튼 (0 초록 왼쪽 위, 1 빨강 오른쪽 위, 2 파랑 오른쪽 아래, 3 노랑 왼쪽 아래)
 * + 가운데 십자 칸막이 + 가운데 원판(테두리까지만; 안쪽 얼굴은 부르는 쪽이 그린다).
 */
object SimonArt {
    /** 버튼 원래 색 */
    val PAD = intArrayOf(0xFF2ECC71.toInt(), 0xFFFF4D5E.toInt(), 0xFF3D7BFF.toInt(), 0xFFFFC93C.toInt())
    /** 꺼진 색 (어둡게) */
    val DIM = IntArray(PAD.size) { Theme.shade(PAD[it], 0.5f) }
    /** 켜진 색 (흰빛 쪽으로) */
    val LIT = IntArray(PAD.size) { Theme.shade(PAD[it], 1.45f) }
    /** 판 몸통 */
    const val BEZEL = 0xFF0D1030.toInt()
    /** 판 테두리 */
    const val RIM = 0xFF3A4378.toInt()

    /** 가운데 원판 반지름 비율 (판 반지름 기준) */
    const val HUB = 0.44f
    /** 버튼 바깥 반지름 비율 */
    const val PAD_OUT = 0.92f
    /** 칸막이 두께 비율 */
    const val GAP = 0.08f

    /** 버튼 i 의 시작 각도 (Canvas 각도: 0 = 3시, 시계 방향) */
    fun startAngle(i: Int): Float = (180f + 90f * i) % 360f

    /** 두 색 섞기 (t=0 a, t=1 b) — 할당 없음 */
    fun mix(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val r = ((a shr 16 and 0xFF) + ((b shr 16 and 0xFF) - (a shr 16 and 0xFF)) * t).toInt()
        val g = ((a shr 8 and 0xFF) + ((b shr 8 and 0xFF) - (a shr 8 and 0xFF)) * t).toInt()
        val bl = ((a and 0xFF) + ((b and 0xFF) - (a and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /**
     * 판 그리기: (cx, cy) 중심, 반지름 r, levels[i] = 버튼 i 의 밝기 0..1.
     * rect 는 부르는 쪽이 미리 만들어 둔 작업용 RectF.
     */
    fun drawBoard(c: Canvas, cx: Float, cy: Float, r: Float, levels: FloatArray, p: Paint, rect: RectF) {
        p.style = Paint.Style.FILL
        // 그림자
        p.color = 0x66000000
        c.drawCircle(cx, cy + r * 0.04f, r * 1.02f, p)
        // 몸통 + 테두리
        p.color = BEZEL
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.03f
        p.color = RIM
        c.drawCircle(cx, cy, r - r * 0.015f, p)
        // 버튼 (부채꼴 → 가운데·칸막이로 덮어 고리 모양)
        val pr = r * PAD_OUT
        rect.set(cx - pr, cy - pr, cx + pr, cy + pr)
        p.style = Paint.Style.FILL
        for (i in 0 until PAD.size) {
            p.color = mix(DIM[i], LIT[i], levels[i])
            c.drawArc(rect, startAngle(i), 90f, true, p)
        }
        // 버튼 윤기 (바깥쪽 가는 호)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.045f
        val sr = r * 0.8f
        rect.set(cx - sr, cy - sr, cx + sr, cy + sr)
        for (i in 0 until PAD.size) {
            p.color = 0xFFFFFFFF.toInt()
            p.alpha = (34 + 90 * levels[i]).toInt()
            c.drawArc(rect, startAngle(i) + 16f, 58f, false, p)
        }
        p.alpha = 255
        // 켜진 버튼 바깥 빛
        p.strokeCap = Paint.Cap.BUTT
        for (i in 0 until PAD.size) {
            if (levels[i] <= 0.01f) continue
            p.color = LIT[i]
            p.alpha = (200 * levels[i]).toInt()
            p.strokeWidth = r * 0.07f
            val gr = r * 1.03f
            rect.set(cx - gr, cy - gr, cx + gr, cy + gr)
            c.drawArc(rect, startAngle(i) + 3f, 84f, false, p)
        }
        p.alpha = 255
        // 십자 칸막이
        p.style = Paint.Style.FILL
        p.color = BEZEL
        val g = r * GAP / 2f
        c.drawRect(cx - g, cy - pr - r * 0.01f, cx + g, cy + pr + r * 0.01f, p)
        c.drawRect(cx - pr - r * 0.01f, cy - g, cx + pr + r * 0.01f, cy + g, p)
        // 가운데 원판
        c.drawCircle(cx, cy, r * HUB, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.02f
        p.color = RIM
        c.drawCircle(cx, cy, r * (HUB - 0.01f), p)
        p.style = Paint.Style.FILL
    }

    /** 가운데 얼굴 (아이콘·규칙 그림용 간단판): 남색 원 + 글자 */
    fun drawFace(c: Canvas, cx: Float, cy: Float, r: Float, text: String, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.CARD
        c.drawCircle(cx, cy, r * 0.37f, p)
        p.color = Theme.GOLD_LIGHT
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        p.textSize = r * (if (text.length >= 3) 0.26f else 0.36f)
        c.drawText(text, cx, cy + p.textSize * 0.36f, p)
        p.isFakeBoldText = false
    }
}
