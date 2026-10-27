package com.siwoo.party.games.yut.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.yut.YutBoard

/**
 * 윷놀이 그림 도우미 (판·말·윷가락). 화면 View 와 모음집 아이콘·규칙 카드가 같이 쓴다.
 * 모두 Canvas 코드, 호출하는 쪽의 Paint 를 빌려 쓰고 객체를 만들지 않는다.
 */
object YutArt {
    // ── 색 ──
    /** 한지 바탕 */
    const val HANJI = 0xFFF2E6CB.toInt()
    const val HANJI_DARK = 0xFFE3D0A8.toInt()
    const val HANJI_EDGE = 0xFFC9AE7C.toInt()
    /** 먹색 */
    const val INK = 0xFF2E2823.toInt()
    const val INK_SOFT = 0x8C2E2823.toInt()
    const val INK_FAINT = 0x332E2823
    /** 참먹이 붉은 인주 */
    const val SEAL = 0xFFB3312A.toInt()

    /** 윷가락 배(평평한 면) / 등(둥근 면) */
    const val STICK_FLAT = 0xFFF4DFB4.toInt()
    const val STICK_FLAT_EDGE = 0xFFC79D5E.toInt()
    const val STICK_ROUND = 0xFF9C5B2C.toInt()
    const val STICK_ROUND_LIGHT = 0xFFC07A40.toInt()
    const val STICK_ROUND_DARK = 0xFF6A3B1A.toInt()

    /** 멍석 */
    const val MAT = 0xFFB98E55.toInt()
    const val MAT_DARK = 0xFF7B5532.toInt()
    const val MAT_LINE = 0x40FFFFFF

    private const val BACK_MARK = "빽"

    /**
     * 판 바탕: 한지 사각형 + 먹선(바깥 네모·대각선 둘) + 칸 동그라미 (모서리·방은 큰 원).
     * (left, top) 에서 한 변 size, 칸들은 안쪽 inset 비율만큼 들어간 정사각형 위에 놓인다.
     * fiber: 한지 결 선분 좌표 (x0,y0,x1,y1…, 판 좌표 0~1) — 없으면 null
     */
    fun drawBoard(c: Canvas, left: Float, top: Float, size: Float, inset: Float, p: Paint, fiber: FloatArray?, labels: Paint?) {
        val r = size * 0.06f
        p.style = Paint.Style.FILL
        // 그림자 + 한지
        p.color = 0x55000000
        c.drawRoundRect(left + size * 0.012f, top + size * 0.02f, left + size + size * 0.012f, top + size + size * 0.02f, r, r, p)
        p.color = HANJI_EDGE
        c.drawRoundRect(left, top, left + size, top + size, r, r, p)
        p.color = HANJI
        val e = size * 0.012f
        c.drawRoundRect(left + e, top + e, left + size - e, top + size - e, r * 0.8f, r * 0.8f, p)
        // 한지 결
        if (fiber != null) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = size * 0.0025f
            p.color = HANJI_DARK
            c.save()
            c.translate(left, top)
            c.scale(size, size)
            p.strokeWidth = 0.0025f
            c.drawLines(fiber, p)
            c.restore()
        }
        val l = left + size * inset
        val t = top + size * inset
        val s = size * (1f - inset * 2f)
        // 먹선
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = INK
        p.strokeWidth = size * 0.011f
        c.drawRect(l, t, l + s, t + s, p)
        p.strokeWidth = size * 0.008f
        c.drawLine(l + s, t, l, t + s, p)
        c.drawLine(l, t, l + s, t + s, p)
        // 칸
        val minor = size * 0.026f
        val major = size * 0.052f
        for (n in 0 until YutBoard.NODES) {
            val x = l + YutBoard.X[n] * s
            val y = t + YutBoard.Y[n] * s
            if (YutBoard.isMajor(n)) {
                p.style = Paint.Style.FILL
                p.color = if (n == YutBoard.START) 0xFFF6D9C8.toInt() else HANJI
                c.drawCircle(x, y, major, p)
                p.style = Paint.Style.STROKE
                p.color = if (n == YutBoard.START) SEAL else INK
                p.strokeWidth = size * 0.009f
                c.drawCircle(x, y, major, p)
                p.strokeWidth = size * 0.005f
                c.drawCircle(x, y, major * 0.72f, p)
            } else {
                p.style = Paint.Style.FILL
                p.color = HANJI
                c.drawCircle(x, y, minor, p)
                p.style = Paint.Style.STROKE
                p.color = INK
                p.strokeWidth = size * 0.0075f
                c.drawCircle(x, y, minor, p)
            }
        }
        if (labels != null) {
            labels.textSize = major * 0.62f
            for (n in 0 until YutBoard.NODES) {
                if (!YutBoard.isMajor(n)) continue
                labels.color = if (n == YutBoard.START) SEAL else INK_SOFT
                val name = if (n == YutBoard.START) START_LABEL else YutBoard.majorName(n)
                c.drawText(name, l + YutBoard.X[n] * s, t + YutBoard.Y[n] * s + labels.textSize * 0.36f, labels)
            }
        }
        p.style = Paint.Style.FILL
    }

    const val START_LABEL = "출발"

    /**
     * 말 하나: 그림자 + 어두운 테두리 + 플레이어 색 + 윗빛 + 번호.
     * text: 번호 글자를 그릴 Paint (가운데 정렬, 없으면 번호 생략)
     */
    fun drawPiece(c: Canvas, x: Float, y: Float, r: Float, color: Int, dark: Int, label: String?, p: Paint, text: Paint?) {
        p.style = Paint.Style.FILL
        p.color = 0x50000000
        c.drawOval(x - r * 0.95f, y + r * 0.45f, x + r * 0.95f, y + r * 1.2f, p)
        p.color = dark
        c.drawCircle(x, y + r * 0.12f, r, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, r, p)
        p.color = color
        c.drawCircle(x, y, r * 0.84f, p)
        p.color = 0x40FFFFFF
        c.drawOval(x - r * 0.55f, y - r * 0.7f, x + r * 0.45f, y - r * 0.12f, p)
        if (label != null && text != null) {
            text.textSize = r * 1.05f
            c.drawText(label, x, y + text.textSize * 0.36f, text)
        }
    }

    /**
     * 윷가락 하나 (가운데 cx,cy, 길이 len, 굵기 w, 각도 deg).
     * squash: 긴 축으로 도는 느낌 (0~1, 폭 비율). flat: 배가 보임. marked: 빽도 표시 가락.
     */
    fun drawStick(c: Canvas, cx: Float, cy: Float, len: Float, w: Float, deg: Float, squash: Float, flat: Boolean, marked: Boolean, p: Paint, text: Paint?) {
        c.save()
        c.translate(cx, cy)
        c.rotate(deg)
        val hw = w / 2f * squash.coerceIn(0.3f, 1f)
        val hl = len / 2f
        val rr = hw.coerceAtMost(w * 0.45f)
        p.style = Paint.Style.FILL
        // 그림자
        p.color = 0x40000000
        c.drawRoundRect(-hw + w * 0.1f, -hl + w * 0.15f, hw + w * 0.1f, hl + w * 0.15f, rr, rr, p)
        if (flat) {
            p.color = STICK_FLAT_EDGE
            c.drawRoundRect(-hw, -hl, hw, hl, rr, rr, p)
            p.color = STICK_FLAT
            c.drawRoundRect(-hw * 0.82f, -hl + w * 0.08f, hw * 0.82f, hl - w * 0.08f, rr * 0.8f, rr * 0.8f, p)
            // 먹으로 그린 × 세 개
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = w * 0.09f
            p.color = INK
            val m = hw * 0.45f
            for (k in -1..1) {
                val y = k * hl * 0.5f
                c.drawLine(-m, y - m, m, y + m, p)
                c.drawLine(m, y - m, -m, y + m, p)
            }
            p.style = Paint.Style.FILL
            if (marked) {
                // 빽도 표시: 붉은 동그라미 + "빽"
                p.color = SEAL
                val my = hl * 0.78f
                c.drawCircle(0f, my, hw * 0.75f, p)
                if (text != null && squash > 0.5f) {
                    text.color = 0xFFFFFFFF.toInt()
                    text.textSize = hw * 0.95f
                    c.drawText(BACK_MARK, 0f, my + text.textSize * 0.36f, text)
                }
            }
        } else {
            p.color = STICK_ROUND_DARK
            c.drawRoundRect(-hw, -hl, hw, hl, rr, rr, p)
            p.color = STICK_ROUND
            c.drawRoundRect(-hw * 0.86f, -hl + w * 0.06f, hw * 0.86f, hl - w * 0.06f, rr * 0.9f, rr * 0.9f, p)
            p.color = STICK_ROUND_LIGHT
            c.drawRoundRect(-hw * 0.55f, -hl + w * 0.2f, -hw * 0.05f, hl - w * 0.2f, rr * 0.5f, rr * 0.5f, p)
            // 나뭇결
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.05f
            p.color = STICK_ROUND_DARK
            c.drawLine(hw * 0.3f, -hl * 0.6f, hw * 0.35f, -hl * 0.1f, p)
            c.drawLine(hw * 0.25f, hl * 0.2f, hw * 0.3f, hl * 0.7f, p)
            p.style = Paint.Style.FILL
            if (marked) {
                // 등에도 작은 표시 (어느 가락인지 보이게)
                p.color = SEAL
                c.drawCircle(0f, hl * 0.78f, hw * 0.35f, p)
            }
        }
        c.restore()
    }
}
