package com.siwoo.party.games.charades.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme

/**
 * 몸으로 말해요 그림 도우미 (아이콘·규칙 카드·설명자 화면). 모두 Canvas 도형, 할당 없음 (Path·Paint 는 부르는 쪽이 넘긴다).
 */
object CharadesArt {
    /** 정답 초록 / 패스 주황 / 시간 끝에 보던 단어 회보라 */
    const val CORRECT = 0xFF2ECC71.toInt()
    const val CORRECT_DARK = 0xFF138A45.toInt()
    const val PASS = 0xFFFF9A2E.toInt()
    const val PASS_DARK = 0xFFC2620A.toInt()
    const val TIMEOUT = 0xFF7A6E9A.toInt()
    const val NO_TALK = 0xFFFF3D57.toInt()

    /** 팀 색·이름 (A팀 네온 분홍, B팀 네온 하늘) */
    val TEAM_COLORS = intArrayOf(Theme.Party.NEON_PINK, Theme.Party.NEON_SKY)
    val TEAM_NAMES = arrayOf("A팀", "B팀")

    /** 카테고리 색 (동물·운동·직업·행동·물건) + 모두 섞기 */
    val CATEGORY_COLORS = intArrayOf(
        0xFFE0832A.toInt(), 0xFF3D7BFF.toInt(), 0xFF1FB5A8.toInt(), 0xFFE04C6A.toInt(), 0xFF8E5BE0.toInt(),
    )
    const val MIXED_COLOR = 0xFFB04CFF.toInt()

    fun categoryColor(k: Int): Int = if (k in CATEGORY_COLORS.indices) CATEGORY_COLORS[k] else MIXED_COLOR

    /**
     * 몸짓하는 사람 (굵은 막대 인형). s = 키, (cx, cy) = 몸 가운데. pose 0: 두 팔 번쩍(펭귄 날갯짓), 1: 한 팔 앞으로·한 다리 들기
     */
    fun drawActor(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, pose: Int, p: Paint) {
        val head = s * 0.13f
        val hy = cy - s * 0.32f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.085f
        p.color = color
        val neck = hy + head
        val hip = cy + s * 0.12f
        c.drawLine(cx, neck, cx, hip, p)
        val sh = neck + s * 0.06f
        if (pose == 0) {
            c.drawLine(cx, sh, cx - s * 0.2f, sh - s * 0.16f, p)
            c.drawLine(cx - s * 0.2f, sh - s * 0.16f, cx - s * 0.3f, sh - s * 0.08f, p)
            c.drawLine(cx, sh, cx + s * 0.2f, sh - s * 0.16f, p)
            c.drawLine(cx + s * 0.2f, sh - s * 0.16f, cx + s * 0.3f, sh - s * 0.08f, p)
            c.drawLine(cx, hip, cx - s * 0.13f, cy + s * 0.42f, p)
            c.drawLine(cx, hip, cx + s * 0.13f, cy + s * 0.42f, p)
        } else {
            c.drawLine(cx, sh, cx + s * 0.3f, sh - s * 0.04f, p)
            c.drawLine(cx, sh, cx - s * 0.16f, sh + s * 0.14f, p)
            c.drawLine(cx - s * 0.16f, sh + s * 0.14f, cx - s * 0.08f, sh + s * 0.24f, p)
            c.drawLine(cx, hip, cx - s * 0.1f, cy + s * 0.42f, p)
            c.drawLine(cx, hip, cx + s * 0.2f, hip + s * 0.08f, p)
            c.drawLine(cx + s * 0.2f, hip + s * 0.08f, cx + s * 0.2f, cy + s * 0.34f, p)
        }
        p.style = Paint.Style.FILL
        c.drawCircle(cx, hy, head, p)
        // 웃는 눈 (어두운 점)
        p.color = Theme.Party.BG_DEEP
        c.drawCircle(cx - head * 0.35f, hy - head * 0.1f, head * 0.14f, p)
        c.drawCircle(cx + head * 0.35f, hy - head * 0.1f, head * 0.14f, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /**
     * "말 금지" 표시: 흰 말풍선 + 빨간 금지 고리(사선). r = 크기 반지름
     */
    fun drawNoTalk(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint, path: Path) {
        p.style = Paint.Style.FILL
        p.color = Theme.PAPER
        c.drawRoundRect(cx - r * 0.78f, cy - r * 0.56f, cx + r * 0.78f, cy + r * 0.42f, r * 0.36f, r * 0.36f, p)
        path.reset()
        path.moveTo(cx - r * 0.34f, cy + r * 0.36f)
        path.lineTo(cx - r * 0.5f, cy + r * 0.74f)
        path.lineTo(cx - r * 0.06f, cy + r * 0.38f)
        path.close()
        c.drawPath(path, p)
        // 말풍선 안 점 세 개 (말)
        p.color = Theme.INK
        for (k in -1..1) c.drawCircle(cx + k * r * 0.3f, cy - r * 0.07f, r * 0.09f, p)
        // 금지 고리
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.16f
        p.color = NO_TALK
        c.drawCircle(cx, cy - r * 0.04f, r * 0.86f, p)
        val d = r * 0.6f
        c.drawLine(cx - d, cy - r * 0.04f - d, cx + d, cy - r * 0.04f + d, p)
        p.style = Paint.Style.FILL
    }

    /** 체크 표시 (s = 폭) */
    fun drawCheck(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.18f
        p.color = color
        c.drawLine(cx - s * 0.36f, cy + s * 0.02f, cx - s * 0.1f, cy + s * 0.28f, p)
        c.drawLine(cx - s * 0.1f, cy + s * 0.28f, cx + s * 0.38f, cy - s * 0.26f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 패스 화살표 (휘어 넘어가는 화살표, s = 폭) */
    fun drawPassArrow(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint, path: Path) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.16f
        p.color = color
        c.drawArc(cx - s * 0.36f, cy - s * 0.22f, cx + s * 0.28f, cy + s * 0.5f, 180f, 160f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx + s * 0.44f, cy + s * 0.02f)
        path.lineTo(cx + s * 0.08f, cy + s * 0.06f)
        path.lineTo(cx + s * 0.32f, cy + s * 0.34f)
        path.close()
        c.drawPath(path, p)
    }

    /** 작은 초시계 (frac = 남은 비율) */
    fun drawWatch(c: Canvas, cx: Float, cy: Float, r: Float, frac: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(cx - r * 0.22f, cy - r * 1.3f, cx + r * 0.22f, cy - r * 0.92f, r * 0.08f, r * 0.08f, p)
        p.color = Theme.PAPER
        c.drawCircle(cx, cy, r, p)
        p.color = Theme.RED
        c.drawArc(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f, -90f, -360f * frac, true, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.14f
        p.color = Theme.GOLD_DARK
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
    }
}
