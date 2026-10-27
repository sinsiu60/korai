package com.siwoo.party.games.darts.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.darts.GameConfig

/**
 * 다트 그림 도구 (할당 없음 — 아이콘·규칙 카드·화면에서 같이 씀).
 *  - [board]: 정식 다트판 (나무 테두리 · 검은 숫자 고리 · 20칸 싱글/트리플/더블 · 불). 반지름 R = [GameConfig.R_FRAME] 기준 비율.
 *  - [dart]: 판에 꽂힌(또는 날아가는) 다트 한 개 — 앞에서 비스듬히 본 모양 (촉 → 배럴 → 샤프트 → 날개).
 */
object DartArt {

    // 정식 색 (검정·크림 싱글, 빨강·초록 고리)
    const val BLACK = 0xFF1B1B1D.toInt()
    const val CREAM = 0xFFF1E2BE.toInt()
    const val RED = 0xFFD62F2F.toInt()
    const val GREEN = 0xFF1C9A4C.toInt()
    const val RING_BG = 0xFF121214.toInt()
    const val WOOD = 0xFF6A4220.toInt()
    const val WOOD_DARK = 0xFF3D2410.toInt()
    const val WIRE = 0xFFC8CDD6.toInt()

    private val oval = RectF()
    private val NUMS = Array(21) { it.toString() }

    /** 칸 i (0 = 20, 시계 방향) 의 싱글 색 / 고리 색 — 20 은 검정 + 빨강, 1 은 크림 + 초록 */
    fun singleColor(i: Int): Int = if (i % 2 == 0) BLACK else CREAM
    fun ringColor(i: Int): Int = if (i % 2 == 0) RED else GREEN

    /**
     * 다트판 전체: 가운데 (cx, cy), 그림 반지름 [scale] = px/mm.
     * numbers = 숫자 글자, wires = 철사(은색 선). 글자 Paint 는 [text] (null 이면 숫자 생략).
     */
    fun board(c: Canvas, p: Paint, cx: Float, cy: Float, scale: Float, wires: Boolean, text: Paint?) {
        p.style = Paint.Style.FILL
        p.shader = null
        // 나무 테두리 (바깥 → 안쪽 한 겹 더 어둡게)
        p.color = WOOD_DARK
        c.drawCircle(cx, cy, GameConfig.R_FRAME * scale, p)
        p.color = WOOD
        c.drawCircle(cx, cy, (GameConfig.R_FRAME - 4f) * scale, p)
        // 검은 숫자 고리
        p.color = RING_BG
        c.drawCircle(cx, cy, GameConfig.R_NUMBER_RING * scale, p)
        // 20칸 (바깥 고리부터 안쪽으로 부채꼴을 덮어 그림)
        for (i in 0 until 20) {
            val start = -90f + i * GameConfig.SECTOR_DEG - GameConfig.SECTOR_DEG / 2f
            wedge(c, p, cx, cy, GameConfig.R_DOUBLE_OUT * scale, start, ringColor(i))
            wedge(c, p, cx, cy, GameConfig.R_DOUBLE_IN * scale, start, singleColor(i))
            wedge(c, p, cx, cy, GameConfig.R_TREBLE_OUT * scale, start, ringColor(i))
            wedge(c, p, cx, cy, GameConfig.R_TREBLE_IN * scale, start, singleColor(i))
        }
        p.color = GREEN
        c.drawCircle(cx, cy, GameConfig.R_OUTER_BULL * scale, p)
        p.color = RED
        c.drawCircle(cx, cy, GameConfig.R_BULL * scale, p)
        if (wires) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = maxOf(1f, 0.9f * scale)
            p.color = WIRE
            p.alpha = 210
            c.drawCircle(cx, cy, GameConfig.R_BULL * scale, p)
            c.drawCircle(cx, cy, GameConfig.R_OUTER_BULL * scale, p)
            c.drawCircle(cx, cy, GameConfig.R_TREBLE_IN * scale, p)
            c.drawCircle(cx, cy, GameConfig.R_TREBLE_OUT * scale, p)
            c.drawCircle(cx, cy, GameConfig.R_DOUBLE_IN * scale, p)
            c.drawCircle(cx, cy, GameConfig.R_DOUBLE_OUT * scale, p)
            val r0 = GameConfig.R_OUTER_BULL * scale
            val r1 = GameConfig.R_DOUBLE_OUT * scale
            for (i in 0 until 20) {
                val a = Math.toRadians((-90f + i * GameConfig.SECTOR_DEG - GameConfig.SECTOR_DEG / 2f).toDouble())
                val ca = Math.cos(a).toFloat()
                val sa = Math.sin(a).toFloat()
                c.drawLine(cx + ca * r0, cy + sa * r0, cx + ca * r1, cy + sa * r1, p)
            }
            p.alpha = 255
            p.style = Paint.Style.FILL
        }
        if (text != null) {
            text.textAlign = Paint.Align.CENTER
            val rn = GameConfig.R_NUMBERS * scale
            for (i in 0 until 20) {
                val a = Math.toRadians((-90f + i * GameConfig.SECTOR_DEG).toDouble())
                val x = cx + Math.cos(a).toFloat() * rn
                val y = cy + Math.sin(a).toFloat() * rn + text.textSize * 0.36f
                c.drawText(NUMS[GameConfig.SECTORS[i]], x, y, text)
            }
        }
    }

    private fun wedge(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, start: Float, color: Int) {
        p.color = color
        oval.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(oval, start, GameConfig.SECTOR_DEG, true, p)
    }

    /**
     * 꽂힌 다트: 촉 끝이 (tipX, tipY), 몸통은 촉에서 [angleDeg] 방향(0 = 위, 시계 방향 +)으로 [len] px.
     * [shadow] 0..1 = 판 위 그림자 진하기 (날아가는 중엔 0). alpha 0..255.
     */
    fun dart(c: Canvas, p: Paint, path: Path, tipX: Float, tipY: Float, len: Float, angleDeg: Float, color: Int, alpha: Int, shadow: Float) {
        if (len <= 0f || alpha <= 0) return
        val a = alpha.coerceIn(0, 255)
        c.save()
        c.translate(tipX, tipY)
        if (shadow > 0f) {
            // 그림자: 오른쪽 아래로 짧게
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFF000000.toInt()
            p.alpha = (110 * shadow * a / 255f).toInt()
            p.strokeWidth = len * 0.07f
            c.drawLine(0f, 0f, len * 0.42f, len * 0.3f, p)
            p.strokeWidth = len * 0.16f
            p.alpha = (60 * shadow * a / 255f).toInt()
            c.drawLine(len * 0.42f, len * 0.3f, len * 0.6f, len * 0.43f, p)
        }
        c.rotate(angleDeg)
        p.style = Paint.Style.FILL
        // 날개 (뒤쪽, 가장 먼저 그림): 연(마름모) 둘이 겹친 모양
        path.reset()
        path.moveTo(0f, -len * 0.6f)
        path.lineTo(-len * 0.24f, -len * 0.86f)
        path.lineTo(-len * 0.1f, -len * 1.02f)
        path.lineTo(0f, -len * 0.94f)
        path.lineTo(len * 0.1f, -len * 1.02f)
        path.lineTo(len * 0.24f, -len * 0.86f)
        path.close()
        p.color = color
        p.alpha = a
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = len * 0.03f
        p.color = 0xFF10131F.toInt()
        p.alpha = a
        c.drawPath(path, p)
        // 날개 가운데 결 (밝게)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = a * 2 / 3
        p.strokeWidth = len * 0.022f
        c.drawLine(0f, -len * 0.64f, 0f, -len * 0.93f, p)
        // 샤프트
        p.strokeCap = Paint.Cap.ROUND
        p.color = Theme.shade(color, 0.6f)
        p.alpha = a
        p.strokeWidth = len * 0.055f
        c.drawLine(0f, -len * 0.38f, 0f, -len * 0.66f, p)
        // 배럴 (텅스텐: 어두운 은색 + 홈)
        p.style = Paint.Style.FILL
        p.color = 0xFF555B66.toInt()
        p.alpha = a
        c.drawRoundRect(-len * 0.055f, -len * 0.42f, len * 0.055f, -len * 0.12f, len * 0.04f, len * 0.04f, p)
        p.color = 0xFFA9B0BC.toInt()
        p.alpha = a
        c.drawRect(-len * 0.025f, -len * 0.4f, -len * 0.005f, -len * 0.14f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = len * 0.014f
        p.color = 0xFF2A2E36.toInt()
        p.alpha = a
        var y = -len * 0.36f
        while (y < -len * 0.16f) {
            c.drawLine(-len * 0.055f, y, len * 0.055f, y, p)
            y += len * 0.05f
        }
        // 촉 (은색 바늘)
        p.strokeWidth = len * 0.03f
        p.color = 0xFFE3E7EE.toInt()
        p.alpha = a
        c.drawLine(0f, 0f, 0f, -len * 0.13f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.restore()
    }

    /** 숫자 글자용 Paint 설정 (굵은 흰 글자) */
    fun numberPaint(p: Paint, size: Float) {
        p.isAntiAlias = true
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = 0xFFF5F5F5.toInt()
        p.textSize = size
        p.textAlign = Paint.Align.CENTER
    }
}
