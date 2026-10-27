package com.siwoo.party.games.gomoku

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.gomoku.ui.GoArt
import com.siwoo.party.games.gomoku.ui.GomokuActivity
import com.siwoo.party.games.gomoku.ui.GomokuSettings

/** 오목: 모음집 등록 정보 (2인 전용, 차례대로) */
object GomokuModule : GameModule {
    override val id = "gomoku"
    override val name = "오목"
    override val tagline = "흑과 백, 다섯 알을 먼저 한 줄로 잇는 사람이 승리!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "10분"
    override val color = 0xFF6D4C2E.toInt()
    override val activityClass = GomokuActivity::class.java

    /** 허브 소개 팝업의 [무르기 설정] → 켜기/끄기 (SharedPreferences "gomoku") */
    override val settingsLabel: String get() = "무르기 설정"

    override fun showSettings(popups: PopupLayer) {
        GomokuSettings.show(popups)
    }

    private val arrow = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF2B3C8F.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 바둑판 (4×4 칸 조각) + 대각선 다섯 알 중 흑 넷 + 백 둘, 금색 줄
        val half = r * 0.64f
        val step = half * 2f / 4f
        GoArt.board(canvas, paint, cx - half, cy - half, cx + half, cy + half, step)
        val sr = step * 0.44f
        val x0 = cx - half + step * 0.5f
        val y0 = cy - half + step * 0.5f
        GoArt.stone(canvas, paint, x0 + step * 3f, y0, sr, false)
        GoArt.stone(canvas, paint, x0, y0 + step * 2f, sr, false)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = sr * 0.55f
        paint.color = 0xCCFFC93C.toInt()
        canvas.drawLine(x0, y0, x0 + step * 3f, y0 + step * 3f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        for (k in 0..3) GoArt.stone(canvas, paint, x0 + step * k, y0 + step * k, sr, true)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sr * 0.2f
        paint.color = Theme.GOLD
        for (k in 0..3) canvas.drawCircle(x0 + step * k, y0 + step * k, sr * 1.12f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF2A1A08.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 규칙 그림 공통: 카드 가득 바둑판 조각, 칸 간격을 돌려줌 */
    private fun board(c: Canvas, w: Float, h: Float, p: Paint): Float {
        val step = h / 6.4f
        GoArt.board(c, p, -step * 0.3f, h * 0.06f, w + step * 0.3f, h * 0.06f + step * 6f, step)
        return step
    }

    /** 교차점 (i, j) 의 화면 좌표 */
    private fun px(i: Int, step: Float): Float = -step * 0.3f + step * 0.5f + i * step
    private fun py(j: Int, h: Float, step: Float): Float = h * 0.06f + step * 0.5f + j * step

    override val rules: List<RuleCard> = listOf(
        RuleCard("두 번 눌러 돌 놓기", "내 차례에 놓을 곳을 누르면\n반투명 돌이 미리 보여요.\n같은 자리를 한 번 더 누르면 확정!\n(다른 곳을 누르면 미리보기가 옮겨 가요)") { c, w, h, p ->
            val s = board(c, w, h, p)
            val r = s * 0.45f
            GoArt.stone(c, p, px(2, s), py(2, h, s), r, true)
            GoArt.stone(c, p, px(3, s), py(3, h, s), r, false)
            GoArt.stone(c, p, px(3, s), py(2, h, s), r, true)
            GoArt.stone(c, p, px(2, s), py(3, h, s), r, false)
            // 미리보기: 안내선 + 반투명 흑돌 + 금색 고리
            val x = px(5, s)
            val y = py(2, h, s)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.07f
            p.color = 0x99FFC93C.toInt()
            c.drawLine(0f, y, w, y, p)
            c.drawLine(x, h * 0.06f, x, h * 0.06f + s * 6f, p)
            GoArt.stone(c, p, x, y, r, true, 130)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.09f
            p.color = Theme.GOLD
            c.drawCircle(x, y, r * 1.2f, p)
            // 손가락 + "한 번 더!"
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C8.toInt()
            c.drawCircle(x + s * 0.55f, y + s * 0.75f, r * 0.62f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = 0xFF8A5A26.toInt()
            c.drawCircle(x + s * 0.55f, y + s * 0.75f, r * 0.62f, p)
            shout(c, p, "한 번 더!", w * 0.72f, h * 0.84f, h * 0.13f, Theme.GOLD)
        },
        RuleCard("다섯 알을 한 줄로", "가로·세로·대각선으로\n내 돌 다섯 개를 먼저 이으면 승리!\n여섯 개 이상 이어도 이겨요 (금지수 없음).\n판이 가득 차면 무승부 (둘 다 1위).") { c, w, h, p ->
            val s = board(c, w, h, p)
            val r = s * 0.45f
            GoArt.stone(c, p, px(2, s), py(1, h, s), r, false)
            GoArt.stone(c, p, px(4, s), py(4, h, s), r, false)
            GoArt.stone(c, p, px(6, s), py(2, h, s), r, false)
            GoArt.stone(c, p, px(7, s), py(5, h, s), r, false)
            // 흑 다섯 (대각선) + 금색 줄·고리
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = r * 0.55f
            p.color = 0xCCFFC93C.toInt()
            c.drawLine(px(1, s), py(5, h, s), px(5, s), py(1, h, s), p)
            p.strokeCap = Paint.Cap.BUTT
            for (k in 0..4) GoArt.halo(c, p, px(1 + k, s), py(5 - k, h, s), r, Theme.GOLD_LIGHT)
            for (k in 0..4) GoArt.stone(c, p, px(1 + k, s), py(5 - k, h, s), r, true)
            for (k in 0..4) GoArt.ring(c, p, px(1 + k, s), py(5 - k, h, s), r, Theme.GOLD)
            shout(c, p, "오목!", w * 0.8f, h * 0.24f, h * 0.15f, Theme.GOLD)
        },
        RuleCard("무르기는 한 번", "방금 둔 돌이 마음에 안 들면\n상대가 두기 전에 [무르기]!\n한 사람 한 판에 ${GameConfig.UNDO_PER_PLAYER}번 (설정에서 끄기 가능)\n흑(먼저 두는 사람)은 무작위로 정해요.") { c, w, h, p ->
            val s = board(c, w, h, p)
            val r = s * 0.45f
            GoArt.stone(c, p, px(2, s), py(2, h, s), r, true)
            GoArt.stone(c, p, px(3, s), py(3, h, s), r, false)
            GoArt.stone(c, p, px(2, s), py(3, h, s), r, false)
            // 물리는 흑돌 (반투명) + 둥근 되돌리기 화살표
            val x = px(4, s)
            val y = py(2, h, s)
            GoArt.stone(c, p, x, y, r, true, 110)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.3f
            p.color = Theme.GOLD
            val ar = s * 0.95f
            c.drawArc(x - ar, y - ar, x + ar, y + ar, 200f, 250f, false, p)
            p.style = Paint.Style.FILL
            arrow.reset()
            val ax = x + ar * Math.cos(Math.toRadians(200.0)).toFloat()
            val ay = y + ar * Math.sin(Math.toRadians(200.0)).toFloat()
            arrow.moveTo(ax - r * 0.65f, ay - r * 0.1f)
            arrow.lineTo(ax + r * 0.55f, ay - r * 0.2f)
            arrow.lineTo(ax - r * 0.05f, ay + r * 0.8f)
            arrow.close()
            c.drawPath(arrow, p)
            // [무르기] 버튼 모양
            val bx = w * 0.76f
            val by = h * 0.72f
            val bw = w * 0.17f
            val bh = h * 0.1f
            p.color = 0xFF1F4FB8.toInt()
            c.drawRoundRect(bx - bw, by - bh + h * 0.025f, bx + bw, by + bh + h * 0.025f, bh * 0.8f, bh * 0.8f, p)
            p.color = Theme.BLUE
            c.drawRoundRect(bx - bw, by - bh, bx + bw, by + bh, bh * 0.8f, bh * 0.8f, p)
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = bh * 1.0f
            c.drawText("무르기", bx, by + bh * 0.36f, p)
            p.textAlign = Paint.Align.LEFT
            p.typeface = Typeface.DEFAULT
        },
    )
}
