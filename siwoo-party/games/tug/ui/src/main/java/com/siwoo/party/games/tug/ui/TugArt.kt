package com.siwoo.party.games.tug.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.sin

/**
 * 줄다리기 그림 도구 (게임 화면·규칙 카드·아이콘이 같이 쓴다). 모두 Canvas 도형, 할당 없음.
 * - 밧줄: 꼬임 무늬가 있는 굵은 줄 (흔들림 = 사인파)
 * - 깃발: 밧줄 가운데 매단 빨간 삼각 깃발
 * - 사람: 원(머리) + 선(몸·팔·다리) 도형
 */
object TugArt {
    const val ROPE = 0xFFD9B26F.toInt()
    const val ROPE_DARK = 0xFF8C6239.toInt()
    const val FLAG = 0xFFE53935.toInt()
    const val FLAG_DARK = 0xFF8E1B1B.toInt()

    private val path = Path()

    /**
     * 밧줄: x0..x1 구간, 기본 높이 y, 굵기 thick.
     * amp(흔들림 높이), wave(파장 px), phase(흔들림 위상), twist(꼬임 무늬가 밀린 거리 px).
     * 양 끝은 고정(흔들림 0)이고 가운데로 갈수록 크게 출렁인다.
     */
    fun rope(c: Canvas, p: Paint, x0: Float, x1: Float, y: Float, thick: Float, amp: Float, wave: Float, phase: Float, twist: Float) {
        val len = x1 - x0
        if (len <= 0f) return
        path.reset()
        val segs = 40
        var k = 0
        while (k <= segs) {
            val x = x0 + len * k / segs
            val yy = ropeY(x, x0, len, y, amp, wave, phase)
            if (k == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            k++
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        // 그림자 → 바탕 → 밝은 줄
        p.color = 0x55000000
        p.strokeWidth = thick * 1.25f
        c.save()
        c.translate(0f, thick * 0.35f)
        c.drawPath(path, p)
        c.restore()
        p.color = ROPE_DARK
        p.strokeWidth = thick
        c.drawPath(path, p)
        p.color = ROPE
        p.strokeWidth = thick * 0.7f
        c.drawPath(path, p)
        // 꼬임 무늬: 짧은 사선이 twist 만큼 밀려 있다 (줄이 끌려가는 느낌)
        val gap = thick * 1.4f
        var off = twist % gap
        if (off < 0f) off += gap
        p.color = ROPE_DARK
        p.strokeWidth = thick * 0.22f
        p.strokeCap = Paint.Cap.BUTT
        var x = x0 + off
        val h = thick * 0.34f
        while (x < x1) {
            val yy = ropeY(x, x0, len, y, amp, wave, phase)
            c.drawLine(x - h, yy + h, x + h, yy - h, p)
            x += gap
        }
        p.strokeCap = Paint.Cap.ROUND
    }

    /** 밧줄 위 x 지점의 높이 */
    fun ropeY(x: Float, x0: Float, len: Float, y: Float, amp: Float, wave: Float, phase: Float): Float {
        if (amp == 0f) return y
        val f = (x - x0) / len
        val env = sin(f * PI.toFloat())
        return y + amp * env * sin((x - x0) / wave * 2f * PI.toFloat() + phase)
    }

    /** 밧줄 (x, y) 에 매단 빨간 깃발. size = 깃발 높이, flutter = 끝이 좌우로 흔들리는 정도(px) */
    fun pennant(c: Canvas, p: Paint, x: Float, y: Float, size: Float, flutter: Float) {
        val half = size * 0.36f
        path.reset()
        path.moveTo(x - half, y)
        path.lineTo(x + half, y)
        path.lineTo(x + flutter, y + size)
        path.close()
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.save()
        c.translate(size * 0.06f, size * 0.08f)
        c.drawPath(path, p)
        c.restore()
        p.color = FLAG
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.07f
        p.strokeJoin = Paint.Join.ROUND
        p.color = FLAG_DARK
        c.drawPath(path, p)
        // 매듭
        p.style = Paint.Style.FILL
        p.color = FLAG_DARK
        c.drawCircle(x, y, size * 0.2f, p)
        p.color = FLAG
        c.drawCircle(x, y, size * 0.14f, p)
    }

    /**
     * 줄 당기는 사람. (footX, groundY) = 발 가운데, u = 크기 단위(약 1dp).
     * dir +1 = 오른쪽을 보고 당김(왼쪽 팀), −1 = 왼쪽을 봄(오른쪽 팀).
     * lean = 뒤로 젖힌 각도(도), fall = 앞으로 넘어진 각도(도, 0~90), armsUp = 만세(이긴 팀).
     * 팔 끝(손)은 대략 발보다 45u 위 — 밧줄 높이를 거기에 맞춘다.
     */
    fun figure(c: Canvas, p: Paint, footX: Float, groundY: Float, u: Float, dir: Float, lean: Float, fall: Float, color: Int, armsUp: Boolean) {
        c.save()
        c.translate(footX, groundY)
        c.scale(dir, 1f)
        // 넘어질 때는 앞발을 축으로 앞으로
        if (fall > 0f) {
            c.translate(12f * u, 0f)
            c.rotate(fall)
            c.translate(-12f * u, 0f)
        }
        c.rotate(-lean)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        // 그림자 (땅에 닿은 타원 대신 아래쪽 어두운 선)
        p.color = 0x44000000
        p.strokeWidth = 5f * u
        c.drawLine(-12f * u, 1.5f * u, 15f * u, 1.5f * u, p)
        p.color = color
        p.strokeWidth = 4.2f * u
        // 다리: 뒷발·앞발로 버팀
        c.drawLine(0f, -22f * u, -11f * u, 0f, p)
        c.drawLine(0f, -22f * u, 13f * u, -1f * u, p)
        // 몸
        c.drawLine(0f, -22f * u, 0f, -42f * u, p)
        // 팔
        if (armsUp) {
            c.drawLine(0f, -40f * u, -9f * u, -58f * u, p)
            c.drawLine(0f, -40f * u, 9f * u, -58f * u, p)
        } else {
            c.drawLine(0f, -40f * u, 21f * u, -37f * u, p)
            c.drawLine(0f, -38f * u, 17f * u, -33f * u, p)
        }
        // 머리
        p.style = Paint.Style.FILL
        c.drawCircle(0f, -51f * u, 8f * u, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * u
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(0f, -51f * u, 8f * u, p)
        // 눈 (보는 쪽)
        p.style = Paint.Style.FILL
        p.color = Theme.INK
        c.drawCircle(3.5f * u, -52.5f * u, 1.6f * u, p)
        c.restore()
    }

    /** 승리선: (x, top..bottom) 금색 점선 */
    fun winLine(c: Canvas, p: Paint, x: Float, top: Float, bottom: Float, u: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3.2f * u
        p.color = color
        val dash = 7f * u
        var y = top
        while (y < bottom) {
            c.drawLine(x, y, x, minOf(y + dash, bottom), p)
            y += dash * 1.8f
        }
    }
}
