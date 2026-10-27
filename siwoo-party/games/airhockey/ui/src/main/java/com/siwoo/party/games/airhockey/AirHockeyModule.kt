package com.siwoo.party.games.airhockey

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.airhockey.ui.AirHockeyActivity
import com.siwoo.party.games.airhockey.ui.HockeyArt

/** 에어하키: 모음집 등록 정보 (2인 전용, 동시 터치 — 손가락 둘) */
object AirHockeyModule : GameModule {
    override val id = "airhockey"
    override val name = "에어하키"
    override val tagline = "말렛을 휘둘러 퍽을 상대 골문에 꽂아라!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val needsMultiTouch = 2
    override val minutes = "3분"
    override val color = 0xFF1E88E5.toInt()
    override val activityClass = AirHockeyActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val rect = RectF()
    private val crown = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = HockeyArt.SURFACE
        canvas.drawCircle(cx, cy, r, paint)
        // 네온 테두리 + 가운데 선
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.09f
        paint.color = HockeyArt.NEON
        canvas.drawCircle(cx, cy, r * 0.9f, paint)
        paint.strokeWidth = r * 0.05f
        paint.color = HockeyArt.CENTER
        canvas.drawLine(cx + r * 0.08f, cy - r * 0.86f, cx + r * 0.08f, cy + r * 0.86f, paint)
        rect.set(cx + r * 0.08f - r * 0.3f, cy - r * 0.3f, cx + r * 0.08f + r * 0.3f, cy + r * 0.3f)
        canvas.drawArc(rect, 0f, 360f, false, paint)
        // 퍽이 날아가는 줄
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.07f
        paint.color = 0xCCFFE08A.toInt()
        canvas.drawLine(cx - r * 0.12f, cy - r * 0.02f, cx + r * 0.22f, cy - r * 0.22f, paint)
        canvas.drawLine(cx - r * 0.05f, cy + r * 0.14f, cx + r * 0.25f, cy - r * 0.04f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        HockeyArt.mallet(canvas, paint, cx - r * 0.38f, cy + r * 0.24f, r * 0.3f, RED)
        HockeyArt.puck(canvas, paint, cx + r * 0.45f, cy - r * 0.3f, r * 0.17f, 1f)
    }

    /** 규칙 그림 공통: 카드 가운데에 판. 판 단위 1 의 픽셀을 돌려주고 (ox, oy) 는 rect.left/top 에 남긴다 */
    private fun table(c: Canvas, w: Float, h: Float, p: Paint): Float {
        val u = minOf(w / (GameConfig.TABLE_W + 2f * HockeyArt.POCKET_DEPTH + 1f), h / (GameConfig.TABLE_H + 1.5f))
        val ox = (w - GameConfig.TABLE_W * u) / 2f
        val oy = (h - GameConfig.TABLE_H * u) / 2f
        HockeyArt.table(c, p, ox, oy, u, RED, BLUE, rect, false)
        rect.set(ox, oy, ox + GameConfig.TABLE_W * u, oy + GameConfig.TABLE_H * u)
        return u
    }

    /** 손가락 끝 (말렛 위) */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xEEFFE0C8.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.18f
        p.color = 0xFF8A5A26.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF0A0F2E.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("내 쪽 말렛을 손가락으로", "왼쪽 반은 1번, 오른쪽 반은 2번 자리예요.\n자기 쪽을 누르고 끌면 말렛이 따라와요.\n가운데 선은 넘어갈 수 없어요.") { c, w, h, p ->
            val u = table(c, w, h, p)
            val ox = rect.left
            val oy = rect.top
            val mx = ox + GameConfig.CENTER_X * u
            // 왼쪽: 손가락이 선을 넘어가도 말렛은 선에서 멈춤
            val ly = oy + 7f * u
            HockeyArt.mallet(c, p, mx - GameConfig.MALLET_R * u, ly, GameConfig.MALLET_R * u, RED)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = u * 0.35f
            p.color = 0xAAFFFFFF.toInt()
            c.drawLine(ox + 5f * u, ly + 3.5f * u, mx - 3.2f * u, ly + 0.8f * u, p)
            p.strokeCap = Paint.Cap.BUTT
            finger(c, p, mx + 2.6f * u, ly, u * 1f)
            // 넘어간 손가락 쪽에 X
            p.style = Paint.Style.STROKE
            p.strokeWidth = u * 0.4f
            p.color = Theme.RED
            val xx = mx + 2.6f * u
            val xy = ly - 2.3f * u
            c.drawLine(xx - u * 0.7f, xy - u * 0.7f, xx + u * 0.7f, xy + u * 0.7f, p)
            c.drawLine(xx - u * 0.7f, xy + u * 0.7f, xx + u * 0.7f, xy - u * 0.7f, p)
            // 오른쪽: 손가락 밑 말렛
            val rx = ox + 25f * u
            val ry = oy + 12f * u
            HockeyArt.mallet(c, p, rx, ry, GameConfig.MALLET_R * u, BLUE)
            finger(c, p, rx + u * 0.3f, ry + u * 0.2f, u * 0.9f)
            HockeyArt.puck(c, p, ox + 21f * u, oy + 5f * u, GameConfig.PUCK_R * u, 0.3f)
        },
        RuleCard("퍽을 쳐서 상대 골문에!", "말렛을 빠르게 휘두를수록 퍽이 세게!\n벽에 맞으면 튕겨 나와요.\n퍽이 상대 골문에 쏙 들어가면 1점!") { c, w, h, p ->
            val u = table(c, w, h, p)
            val ox = rect.left
            val oy = rect.top
            val gx = ox + GameConfig.TABLE_W * u
            val gy = oy + GameConfig.TABLE_H / 2f * u
            // 말렛 → 퍽 → 위 벽에 튕겨 → 오른쪽 골문
            val mx = ox + 9f * u
            val my = oy + 13f * u
            val px = ox + 12f * u
            val py = oy + 11.2f * u
            val bx = ox + 22f * u
            val by = oy + 1f * u
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = u * 0.3f
            p.color = 0xCCFFE08A.toInt()
            var k = 0f
            while (k < 1f) {
                c.drawLine(px + (bx - px) * k, py + (by - py) * k, px + (bx - px) * (k + 0.06f), py + (by - py) * (k + 0.06f), p)
                k += 0.12f
            }
            k = 0f
            while (k < 0.95f) {
                c.drawLine(bx + (gx - bx) * k, by + (gy - by) * k, bx + (gx - bx) * (k + 0.06f), by + (gy - by) * (k + 0.06f), p)
                k += 0.12f
            }
            p.strokeWidth = u * 0.25f
            p.color = 0x99FFFFFF.toInt()
            c.drawLine(mx - 4f * u, my + 2.4f * u, mx - 2f * u, my + 1.2f * u, p)
            c.drawLine(mx - 3.6f * u, my + 3.6f * u, mx - 1.6f * u, my + 2.4f * u, p)
            p.strokeCap = Paint.Cap.BUTT
            HockeyArt.mallet(c, p, mx, my, GameConfig.MALLET_R * u, RED)
            HockeyArt.puck(c, p, gx - 1.3f * u, gy - 0.6f * u, GameConfig.PUCK_R * u, 1f)
            HockeyArt.mallet(c, p, ox + 27f * u, oy + 14f * u, GameConfig.MALLET_R * u, BLUE)
            shout(c, p, "골!", ox + 25.5f * u, oy + 7.6f * u, h * 0.2f, Theme.GOLD)
        },
        RuleCard("먼저 ${GameConfig.WIN_SCORE}점이면 승리", "골을 먹은 쪽에서 퍽을 다시 놓고 시작해요.\n먼저 ${GameConfig.WIN_SCORE}점을 낸 사람이 1위,\n진 사람은 2위!") { c, w, h, p ->
            val u = table(c, w, h, p)
            val ox = rect.left
            val oy = rect.top
            val tw = GameConfig.TABLE_W * u
            val cy = oy + GameConfig.TABLE_H / 2f * u
            shout(c, p, GameConfig.WIN_SCORE.toString(), ox + tw * 0.25f, cy + h * 0.13f, h * 0.38f, RED)
            shout(c, p, "4", ox + tw * 0.75f, cy + h * 0.13f, h * 0.38f, HockeyArt.alpha(BLUE, 0x99))
            p.style = Paint.Style.FILL
            Ui.drawCrown(c, ox + tw * 0.25f, cy - h * 0.2f, h * 0.24f, crown, p)
            shout(c, p, "1위", ox + tw * 0.25f, oy + GameConfig.TABLE_H * u - u * 0.9f, h * 0.12f, Theme.GOLD)
            // 다시 놓인 퍽 (득점당한 오른쪽, 가운데 선 근처)
            HockeyArt.puck(c, p, ox + (GameConfig.CENTER_X + GameConfig.SERVE_OFFSET) * u, cy, GameConfig.PUCK_R * u, 0.2f)
        },
    )
}
