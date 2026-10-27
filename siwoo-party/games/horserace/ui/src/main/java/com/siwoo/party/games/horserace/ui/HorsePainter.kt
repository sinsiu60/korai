package com.siwoo.party.games.horserace.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.horserace.GameConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 말 + 기수 그리기 (오른쪽을 보고 달림). 경주 화면·베팅 카드·규칙 그림·아이콘이 같이 쓴다.
 * 좌표는 말 가운데 (cx, cy), 크기 s 기준 단위 좌표 (말 전체가 대략 가로 1.15s × 세로 1.0s).
 * 기수 옷·모자·안장 천은 말 번호 색 (경마 번호 색: 1 흰 · 2 검정 · 3 빨강 · 4 파랑 · 5 노랑 · 6 초록).
 * 그리기에서 객체를 만들지 않는다 (Path·RectF 재사용, 번호 글자는 미리 만든 상수).
 */
class HorsePainter {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()

    /**
     * 말 한 마리.
     * @param phase 달리기 단계(라디안). running = false 면 서 있는 자세
     * @param running 다리를 크게 휘두르며 위아래로 들썩임
     */
    fun draw(c: Canvas, cx: Float, cy: Float, s: Float, horse: Int, phase: Float, running: Boolean) {
        val silk = SILK[horse]
        val coat = COAT[horse]
        val dark = MANE[horse]
        val bob = if (running) -abs(sin(phase)) * 0.06f * s else 0f
        c.save()
        c.translate(cx, cy + bob)

        // 땅 그림자 (들썩임과 상관없이 바닥에)
        fill.color = 0x40000000
        rect.set(-0.42f * s, 0.44f * s - bob, 0.40f * s, 0.54f * s - bob)
        c.drawOval(rect, fill)

        // 꼬리 (흔들림)
        val swing = if (running) sin(phase + 1.2f) * 0.06f * s else 0f
        path.reset()
        path.moveTo(-0.33f * s, -0.08f * s)
        path.quadTo(-0.56f * s, -0.06f * s + swing, -0.52f * s, 0.20f * s + swing)
        line.color = dark
        line.strokeWidth = 0.08f * s
        c.drawPath(path, line)

        // 먼 쪽 다리 (어둡게, 몸 뒤)
        val amp = if (running) 0.75f else 0f
        line.color = COAT_FAR[horse]
        leg(c, s, 0.13f, phase + 0.6f, amp, 0.12f)
        leg(c, s, -0.21f, phase + 3.7f, amp, -0.10f)

        // 목
        fill.color = coat
        path.reset()
        path.moveTo(0.12f * s, -0.08f * s)
        path.lineTo(0.28f * s, -0.40f * s)
        path.lineTo(0.42f * s, -0.42f * s)
        path.lineTo(0.36f * s, 0.06f * s)
        path.close()
        c.drawPath(path, fill)
        // 몸통
        rect.set(-0.37f * s, -0.15f * s, 0.32f * s, 0.15f * s)
        c.drawOval(rect, fill)
        // 머리 (코가 아래 앞으로)
        c.save()
        c.rotate(38f, 0.42f * s, -0.38f * s)
        rect.set(0.30f * s, -0.45f * s, 0.62f * s, -0.31f * s)
        c.drawRoundRect(rect, 0.07f * s, 0.07f * s, fill)
        fill.color = COAT_NOSE[horse]
        c.drawCircle(0.585f * s, -0.38f * s, 0.035f * s, fill) // 콧구멍 쪽
        c.restore()
        // 귀
        fill.color = coat
        path.reset()
        path.moveTo(0.33f * s, -0.43f * s)
        path.lineTo(0.35f * s, -0.56f * s)
        path.lineTo(0.41f * s, -0.45f * s)
        path.close()
        c.drawPath(path, fill)
        // 눈
        fill.color = 0xFF1A1010.toInt()
        c.drawCircle(0.42f * s, -0.385f * s, 0.024f * s, fill)
        // 갈기
        line.color = dark
        line.strokeWidth = 0.055f * s
        path.reset()
        path.moveTo(0.34f * s, -0.45f * s)
        path.quadTo(0.24f * s, -0.30f * s, 0.15f * s, -0.12f * s)
        c.drawPath(path, line)

        // 가까운 쪽 다리
        line.color = coat
        leg(c, s, 0.20f, phase, amp, 0.10f)
        leg(c, s, -0.27f, phase + 3.1f, amp, -0.12f)

        // 안장 천 + 번호
        fill.color = silk
        rect.set(-0.17f * s, -0.15f * s, 0.10f * s, 0.07f * s)
        c.drawRoundRect(rect, 0.03f * s, 0.03f * s, fill)
        line.color = 0x66000000
        line.strokeWidth = 0.015f * s
        c.drawRoundRect(rect, 0.03f * s, 0.03f * s, line)
        numPaint.color = SILK_TEXT[horse]
        numPaint.textSize = 0.17f * s
        c.drawText(NUMBERS[horse], -0.035f * s, 0.02f * s, numPaint)

        // 기수: 다리(흰 바지 + 검은 장화)
        line.color = 0xFFF5F5F5.toInt()
        line.strokeWidth = 0.075f * s
        c.drawLine(-0.07f * s, -0.24f * s, 0.03f * s, -0.15f * s, line)
        line.color = 0xFF222222.toInt()
        c.drawLine(0.03f * s, -0.15f * s, 0.00f * s, -0.08f * s, line)
        // 몸통 (앞으로 숙임). 흰 옷은 회색 테두리가 보이게 조금 굵은 바탕을 먼저
        if (horse == 0) {
            line.color = 0x66000000
            line.strokeWidth = 0.16f * s
            c.drawLine(-0.08f * s, -0.26f * s, 0.08f * s, -0.35f * s, line)
        }
        line.color = silk
        line.strokeWidth = 0.14f * s
        c.drawLine(-0.08f * s, -0.26f * s, 0.08f * s, -0.35f * s, line)
        // 팔 (고삐)
        line.color = SILK_ARM[horse]
        line.strokeWidth = 0.05f * s
        c.drawLine(0.06f * s, -0.33f * s, 0.27f * s, -0.30f * s, line)
        line.color = 0xFF3E2723.toInt()
        line.strokeWidth = 0.015f * s
        c.drawLine(0.27f * s, -0.30f * s, 0.46f * s, -0.30f * s, line)
        // 머리 + 모자
        fill.color = 0xFFFFD7B0.toInt()
        c.drawCircle(0.15f * s, -0.43f * s, 0.07f * s, fill)
        fill.color = silk
        rect.set(0.08f * s, -0.51f * s, 0.22f * s, -0.37f * s)
        c.drawArc(rect, 180f, 180f, true, fill)
        rect.set(0.17f * s, -0.455f * s, 0.27f * s, -0.425f * s)
        c.drawRoundRect(rect, 0.015f * s, 0.015f * s, fill)
        if (horse == 1) { // 검은 모자는 흰 줄
            line.color = 0xCCFFFFFF.toInt()
            line.strokeWidth = 0.012f * s
            rect.set(0.08f * s, -0.51f * s, 0.22f * s, -0.37f * s)
            c.drawArc(rect, 180f, 180f, false, line)
        }
        c.restore()
    }

    /** 다리 하나: 엉덩이(hx, 0.08) 에서 아래로, 달리면 sin 으로 앞뒤 휘두름, 무릎 아래는 조금 접힘 */
    private fun leg(c: Canvas, s: Float, hx: Float, phase: Float, amp: Float, rest: Float) {
        val a = if (amp > 0f) sin(phase) * amp else rest
        val hipX = hx * s
        val hipY = 0.06f * s
        val upper = 0.21f * s
        val kx = hipX + sin(a) * upper
        val ky = hipY + cos(a) * upper
        val b = a - (if (amp > 0f) (0.5f + 0.5f * sin(phase + 1.6f)) * 0.9f else 0f) * (if (hx > 0f) -1f else 1f)
        val lower = 0.2f * s
        val fx = kx + sin(b) * lower
        val fy = ky + cos(b) * lower
        line.strokeWidth = 0.075f * s
        c.drawLine(hipX, hipY, kx, ky, line)
        line.strokeWidth = 0.06f * s
        c.drawLine(kx, ky, fx, fy, line)
        // 발굽
        val keep = line.color
        line.color = 0xFF2B2B2B.toInt()
        line.strokeWidth = 0.065f * s
        c.drawLine(fx, fy, fx + sin(b) * 0.04f * s, fy + cos(b) * 0.04f * s, line)
        line.color = keep
    }

    /** 번호 패 (둥근 네모, 말 색 + 번호) — 레인 왼쪽·카드·결과표 */
    fun badge(c: Canvas, cx: Float, cy: Float, size: Float, horse: Int) {
        val silk = SILK[horse]
        fill.color = silk
        rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        c.drawRoundRect(rect, size * 0.22f, size * 0.22f, fill)
        line.color = if (horse == 1) 0x99FFFFFF.toInt() else 0x55000000
        line.strokeWidth = size * 0.06f
        c.drawRoundRect(rect, size * 0.22f, size * 0.22f, line)
        numPaint.color = SILK_TEXT[horse]
        numPaint.textSize = size * 0.66f
        c.drawText(NUMBERS[horse], cx, cy + numPaint.textSize * 0.36f, numPaint)
    }

    companion object {
        /** 번호 색 (기수 옷) */
        val SILK = intArrayOf(
            0xFFF4F4F4.toInt(), // 1 흰색
            0xFF2A2A2E.toInt(), // 2 검정
            0xFFE53935.toInt(), // 3 빨강
            0xFF1E88E5.toInt(), // 4 파랑
            0xFFFDD835.toInt(), // 5 노랑
            0xFF43A047.toInt(), // 6 초록
        )
        val SILK_NAMES = arrayOf("흰색", "검정", "빨강", "파랑", "노랑", "초록")
        /** 털 색 */
        private val COAT = intArrayOf(
            0xFF9A6B45.toInt(), 0xFF5E3B26.toInt(), 0xFFB5753C.toInt(),
            0xFF7A4B30.toInt(), 0xFFA27A55.toInt(), 0xFF4B3022.toInt(),
        )
        private val MANE = intArrayOf(
            0xFF3E2516.toInt(), 0xFF1E120B.toInt(), 0xFF5A3014.toInt(),
            0xFF2E1A10.toInt(), 0xFF4A3220.toInt(), 0xFF140B06.toInt(),
        )
        private val COAT_FAR = IntArray(GameConfig.HORSES) { Theme.shade(COAT[it], 0.7f) }
        private val COAT_NOSE = IntArray(GameConfig.HORSES) { Theme.shade(COAT[it], 0.75f) }
        private val SILK_ARM = IntArray(GameConfig.HORSES) { Theme.shade(SILK[it], 0.85f) }
        /** 번호 색 위 글씨 색 */
        val SILK_TEXT = IntArray(GameConfig.HORSES) { Theme.textOn(SILK[it]) }
        val NUMBERS = Array(GameConfig.HORSES) { (it + 1).toString() }
    }
}
