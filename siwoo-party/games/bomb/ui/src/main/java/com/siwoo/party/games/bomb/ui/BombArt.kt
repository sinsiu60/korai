package com.siwoo.party.games.bomb.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * 폭탄 그림 공통 (허브 아이콘·규칙 카드·주제 카드 뒷면·게임 화면 폭탄).
 * 몸통 중심 (cx, cy), 반지름 r. 뚜껑은 오른쪽 위 40° 방향, 심지는 오른쪽 위로 휘어 올라간다.
 * 심지 끝(불꽃 자리)은 (cx + TIP_X·r, cy + TIP_Y·r).
 * Path·Paint 는 부르는 쪽 것을 재사용 → 할당 없음.
 */
object BombArt {
    const val TIP_X = 1.12f
    const val TIP_Y = -1.42f

    const val BODY = 0xFF2A2E45.toInt()
    const val BODY_DARK = 0xFF121428.toInt()
    const val BODY_LIGHT = 0xFF5A6290.toInt()
    const val CAP = 0xFF6B7499.toInt()
    const val CAP_DARK = 0xFF3B4166.toInt()
    const val FUSE = 0xFFD8B27A.toInt()
    const val FUSE_DARK = 0xFF8A6A3A.toInt()
    const val FLAME_OUT = 0xFFFF6D00.toInt()
    const val FLAME_IN = 0xFFFFE082.toInt()

    /** 그림자 + 몸통 + 광택 + 뚜껑 + 심지. bodyShader 가 있으면 몸통을 그것으로 칠한다 */
    fun drawBomb(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint, path: Path, bodyShader: Shader? = null) {
        p.style = Paint.Style.FILL
        // 바닥 그림자
        p.color = 0x55000000
        c.drawOval(cx - r * 0.85f, cy + r * 0.8f, cx + r * 0.85f, cy + r * 1.08f, p)
        // 심지 (뚜껑 위에서 휘어 올라감)
        path.reset()
        path.moveTo(cx + r * 0.66f, cy - r * 0.8f)
        path.cubicTo(cx + r * 0.88f, cy - r * 1.08f, cx + r * 1.32f, cy - r * 1.02f, cx + r * TIP_X, cy + r * TIP_Y)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.13f
        p.color = FUSE_DARK
        c.drawPath(path, p)
        p.strokeWidth = r * 0.075f
        p.color = FUSE
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        // 뚜껑 (몸통 오른쪽 위로 비스듬히)
        c.save()
        c.rotate(40f, cx, cy)
        p.color = CAP_DARK
        c.drawRoundRect(cx - r * 0.3f, cy - r * 1.13f, cx + r * 0.3f, cy - r * 0.78f, r * 0.08f, r * 0.08f, p)
        p.color = CAP
        c.drawRoundRect(cx - r * 0.3f, cy - r * 1.13f, cx + r * 0.3f, cy - r * 0.98f, r * 0.08f, r * 0.08f, p)
        c.restore()
        // 몸통
        if (bodyShader != null) {
            p.shader = bodyShader
            c.drawCircle(cx, cy, r, p)
            p.shader = null
        } else {
            p.color = BODY
            c.drawCircle(cx, cy, r, p)
            p.color = BODY_DARK
            p.alpha = 110
            c.drawCircle(cx + r * 0.12f, cy + r * 0.14f, r * 0.86f, p)
            p.alpha = 255
        }
        // 광택
        p.color = 0x40FFFFFF
        c.drawOval(cx - r * 0.66f, cy - r * 0.68f, cx - r * 0.12f, cy - r * 0.3f, p)
        p.color = 0xB0FFFFFF.toInt()
        c.drawCircle(cx - r * 0.46f, cy - r * 0.5f, r * 0.09f, p)
    }

    /** 심지 끝 불꽃 (정지 그림용: 아이콘·규칙 카드) */
    fun drawFlame(c: Canvas, x: Float, y: Float, s: Float, p: Paint, path: Path) {
        p.style = Paint.Style.FILL
        star(path, x, y, s, s * 0.45f, 8, 0f)
        p.color = FLAME_OUT
        c.drawPath(path, p)
        star(path, x, y, s * 0.62f, s * 0.3f, 8, 22.5f)
        p.color = FLAME_IN
        c.drawPath(path, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, s * 0.16f, p)
    }

    /** 뾰족한 별 모양 (폭발·불꽃): 바깥 반지름 ro, 안쪽 ri, 꼭짓점 n 개, 시작 각 rotDeg */
    fun star(path: Path, x: Float, y: Float, ro: Float, ri: Float, n: Int, rotDeg: Float) {
        path.reset()
        val start = Math.toRadians(rotDeg.toDouble() - 90.0)
        for (k in 0 until n * 2) {
            val a = start + k * Math.PI / n
            val rad = if (k % 2 == 0) ro else ri
            val px = x + (rad * Math.cos(a)).toFloat()
            val py = y + (rad * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    /** 해골 (가운데 x, y, 크기 s) — 규칙 카드용 */
    fun drawSkull(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFFF2F2F2.toInt()
        c.drawCircle(x, y - s * 0.08f, s * 0.42f, p)
        c.drawRoundRect(x - s * 0.24f, y + s * 0.1f, x + s * 0.24f, y + s * 0.46f, s * 0.06f, s * 0.06f, p)
        p.color = 0xFF1A2040.toInt()
        c.drawCircle(x - s * 0.15f, y - s * 0.06f, s * 0.11f, p)
        c.drawCircle(x + s * 0.15f, y - s * 0.06f, s * 0.11f, p)
        c.drawRect(x - s * 0.02f, y + s * 0.12f, x + s * 0.02f, y + s * 0.2f, p)
        c.drawRect(x - s * 0.11f, y + s * 0.32f, x - s * 0.07f, y + s * 0.46f, p)
        c.drawRect(x + s * 0.07f, y + s * 0.32f, x + s * 0.11f, y + s * 0.46f, p)
    }
}
