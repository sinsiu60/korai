package com.siwoo.party.games.artillery

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
import com.siwoo.party.games.artillery.ui.ArtilleryActivity
import com.siwoo.party.games.artillery.ui.ArtilleryArt

/** 포탄 대결: 모음집 등록 정보 (2~4인, 차례대로) */
object ArtilleryModule : GameModule {
    override val id = "artillery"
    override val name = "포탄 대결"
    override val tagline = "바람을 읽고 포물선으로 상대 대포를 맞혀라!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "10분"
    override val color = 0xFF3E7BB8.toInt()
    override val activityClass = ArtilleryActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val clip = Path()
    private val crown = Path()
    private val box = RectF()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        canvas.save()
        clip.reset()
        clip.addCircle(cx, cy, r * 0.94f, Path.Direction.CW)
        canvas.clipPath(clip)
        // 하늘 (위아래 두 겹)
        paint.color = ArtilleryArt.SKY_TOP
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = 0xFF8CC6EE.toInt()
        canvas.drawRect(cx - r, cy, cx + r, cy + r, paint)
        // 언덕 둘
        ArtilleryArt.hill(canvas, paint, cx - r * 1.5f, cx + r * 0.2f, cy + r * 0.05f, cy + r * 1.1f, r * 0.08f)
        ArtilleryArt.hill(canvas, paint, cx - r * 0.1f, cx + r * 1.6f, cy + r * 0.25f, cy + r * 1.1f, r * 0.08f)
        // 포물선 점선
        paint.color = 0xEEFFFFFF.toInt()
        for (k in 0..8) {
            val f = k / 8f
            val x = cx - r * 0.42f + r * 0.95f * f
            val y = cy - r * 0.2f - r * 0.95f * f * (1f - f) * 2.2f + r * 0.35f * f
            canvas.drawCircle(x, y, r * 0.035f, paint)
        }
        // 대포 + 폭발
        ArtilleryArt.cannon(canvas, paint, cx - r * 0.55f, cy - r * 0.02f, r * 0.11f, RED, 50f)
        ArtilleryArt.burst(canvas, paint, cx + r * 0.55f, cy + r * 0.22f, r * 0.26f)
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 공통: 하늘 + 땅 (카드 안 둥근 상자) */
    private fun scene(c: Canvas, w: Float, h: Float, p: Paint) {
        p.shader = null
        p.style = Paint.Style.FILL
        box.set(w * 0.03f, h * 0.05f, w * 0.97f, h * 0.95f)
        c.save()
        clip.reset()
        clip.addRoundRect(box, h * 0.06f, h * 0.06f, Path.Direction.CW)
        c.clipPath(clip)
        p.color = ArtilleryArt.SKY_TOP
        c.drawRect(box, p)
        p.color = 0xFF8CC6EE.toInt()
        c.drawRect(box.left, h * 0.5f, box.right, box.bottom, p)
        ArtilleryArt.cloud(c, p, w * 0.2f, h * 0.2f, h * 0.06f, 220)
        ArtilleryArt.cloud(c, p, w * 0.78f, h * 0.15f, h * 0.05f, 200)
        ArtilleryArt.hill(c, p, w * 0.0f, w * 0.45f, h * 0.62f, h * 1.0f, h * 0.025f)
        ArtilleryArt.hill(c, p, w * 0.3f, w * 0.75f, h * 0.38f, h * 1.0f, h * 0.025f)
        ArtilleryArt.hill(c, p, w * 0.6f, w * 1.05f, h * 0.6f, h * 1.0f, h * 0.025f)
        c.restore()
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF10142A.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 포물선 점선 (x0,y0 → x1,y1, 꼭대기 높이 lift) */
    private fun arc(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, lift: Float, n: Int, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        for (k in 0..n) {
            val f = k.toFloat() / n
            c.drawCircle(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f - lift * 4f * f * (1f - f), r, p)
        }
    }

    /** 손가락 */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE0C8.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.18f
        p.color = 0xFF8A5A26.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("끌어서 조준·발사", "내 차례에 전장 아무 곳이나 누르고\n쏘고 싶은 방향의 반대로 끌었다 놓아요.\n끈 방향 = 각도, 끈 길이 = 힘!\n위쪽 판에 각도·힘이 보여요.") { c, w, h, p ->
            scene(c, w, h, p)
            val u = h * 0.035f
            val cx = w * 0.225f
            val cy = h * 0.715f - 1.3f * u
            // 날아갈 방향 점선 (오른쪽 위)
            arc(c, p, cx + u * 2f, cy - u * 2f, w * 0.8f, h * 0.68f, h * 0.32f, 14, h * 0.012f, 0xEEFFFFFF.toInt())
            ArtilleryArt.cannon(c, p, cx, cy, u, RED, 50f)
            // 손가락: 전장 가운데에서 왼쪽 아래로 끎
            val sx = w * 0.52f
            val sy = h * 0.3f
            val fx = sx - w * 0.12f
            val fy = sy + h * 0.2f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.02f
            p.color = 0xCCFFE08A.toInt()
            c.drawLine(sx, sy, fx, fy, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            p.color = 0x88FFFFFF.toInt()
            c.drawCircle(sx, sy, h * 0.025f, p)
            finger(c, p, fx, fy, h * 0.05f)
            // 각도·힘 판
            box.set(w * 0.66f, h * 0.1f, w * 0.94f, h * 0.3f)
            p.color = 0xCC0B1340.toInt()
            c.drawRoundRect(box, h * 0.04f, h * 0.04f, p)
            shout(c, p, "50°  80%", w * 0.8f, h * 0.245f, h * 0.1f, Theme.TEXT)
        },
        RuleCard("바람을 읽어요", "바람은 차례마다 바뀌어요.\n풍향 자루와 화살표 수가 방향·세기!\n바람 쪽으로 포탄이 밀려가요.\n화면 양옆 밖으로 나가면 빗나감.") { c, w, h, p ->
            scene(c, w, h, p)
            val u = h * 0.035f
            val cx = w * 0.225f
            val cy = h * 0.715f - 1.3f * u
            // 바람 없을 때 (흐린 점선) vs 오른쪽 바람 (밝은 점선)
            arc(c, p, cx + u * 2f, cy - u * 2f, w * 0.62f, h * 0.66f, h * 0.36f, 13, h * 0.01f, 0x77FFFFFF)
            arc(c, p, cx + u * 2f, cy - u * 2f, w * 0.82f, h * 0.68f, h * 0.36f, 13, h * 0.013f, Theme.GOLD_LIGHT)
            ArtilleryArt.cannon(c, p, cx, cy, u, BLUE, 55f)
            ArtilleryArt.burst(c, p, w * 0.82f, h * 0.68f, h * 0.07f)
            // 풍향 자루 + 화살표
            ArtilleryArt.windsock(c, p, w * 0.52f, h * 0.12f, h * 0.16f, 4, GameConfig.WIND_LEVELS)
            p.style = Paint.Style.FILL
            for (k in 0 until 5) {
                val x = w * 0.62f + k * h * 0.07f
                val y = h * 0.2f
                crown.reset()
                crown.moveTo(x - h * 0.022f, y - h * 0.03f)
                crown.lineTo(x + h * 0.025f, y)
                crown.lineTo(x - h * 0.022f, y + h * 0.03f)
                crown.close()
                p.color = if (k < 4) Theme.GOLD else 0x55FFFFFF
                c.drawPath(crown, p)
            }
        },
        RuleCard("터지면 피해, 0 이면 탈락", "떨어진 곳 가까이의 대포일수록 크게 다쳐요.\n직격 ${GameConfig.MAX_DAMAGE} · 체력 ${GameConfig.START_HP}, 내 대포도 다쳐요!\n체력 0 이면 탈락, 마지막 1명이 승리.\n늦게 탈락할수록 높은 순위예요.") { c, w, h, p ->
            scene(c, w, h, p)
            val u = h * 0.035f
            // 왼쪽: 폭발 반경 안의 대포
            val ax = w * 0.225f
            val ay = h * 0.715f - 1.3f * u
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = 0xCCFFF4C0.toInt()
            c.drawCircle(ax + u * 2.2f, ay + u, u * 6.5f, p)
            p.style = Paint.Style.FILL
            ArtilleryArt.cannon(c, p, ax, ay, u, BLUE, 130f)
            ArtilleryArt.burst(c, p, ax + u * 2.4f, ay + u * 1f, u * 2.4f)
            ArtilleryArt.hpBar(c, p, ax, ay - u * 5f, h * 0.2f, h * 0.045f, 0.3f, 0.8f)
            shout(c, p, "-50", ax + u * 0.5f, ay - u * 6.4f, h * 0.11f, 0xFFFF5A4A.toInt())
            // 가운데: 부서진 대포 (탈락)
            val bx = w * 0.525f
            val by = h * 0.535f - 1.3f * u
            ArtilleryArt.wreck(c, p, bx, by, u, GREEN)
            shout(c, p, "탈락", bx, by - u * 5.2f, h * 0.09f, Theme.RED)
            // 오른쪽: 승리 (왕관)
            val cx = w * 0.825f
            val cy = h * 0.7f - 1.3f * u
            ArtilleryArt.cannon(c, p, cx, cy, u, RED, 120f)
            p.color = Theme.GOLD
            Ui.drawCrown(c, cx, cy - u * 2.2f, h * 0.15f, crown, p)
        },
    )
}
