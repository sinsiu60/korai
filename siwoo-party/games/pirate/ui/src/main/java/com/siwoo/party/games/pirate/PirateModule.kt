package com.siwoo.party.games.pirate

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.pirate.ui.PirateActivity
import com.siwoo.party.games.pirate.ui.PirateArt
import com.siwoo.party.games.pirate.ui.PirateSettings

/** 해적 룰렛: 모음집 등록 정보 */
object PirateModule : GameModule {
    override val id = "pirate"
    override val name = "해적 룰렛"
    override val tagline = "칼을 꽂을 때마다 두근두근, 해적이 튀어나오면 탈락!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val minutes = "3분"
    override val color = 0xFFA0522D.toInt()
    override val activityClass = PirateActivity::class.java

    override val settingsLabel: String get() = "함정 개수"

    override fun showSettings(popups: PopupLayer) {
        PirateSettings.show(popups)
    }

    private val path = Path()
    private val path2 = Path()

    /** 칼 하나: 구멍 (x, y) 에서 deg 방향으로 */
    private fun sword(c: Canvas, x: Float, y: Float, deg: Float, s: Float, color: Int, p: Paint) {
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        PirateArt.drawSword(c, s * 2.2f, s, color, Theme.shade(color, 0.6f), p)
        c.restore()
    }

    /** 해적 상반신 (cx, cy) 가운데, 얼굴 반지름 r, 회전 deg */
    private fun pirate(c: Canvas, cx: Float, cy: Float, r: Float, deg: Float, mood: Int, p: Paint) {
        c.save()
        c.translate(cx, cy)
        c.rotate(deg)
        PirateArt.drawPirate(c, r, p, path2, mood)
        c.restore()
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val w = size * 0.62f
        val h = size * 0.4f
        val top = cy + size * 0.02f
        val r = size * 0.14f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawOval(cx - w * 0.55f, top + h - size * 0.03f, cx + w * 0.55f, top + h + size * 0.06f, paint)
        pirate(canvas, cx, top - r * 0.72f, r, 0f, PirateArt.NORMAL, paint)
        PirateArt.drawSimpleBarrel(canvas, cx, top, w, h, paint, path)
        val s = size * 0.028f
        PirateArt.drawSimpleHole(canvas, cx, top + h * 0.55f, s * 1.2f, paint)
        sword(canvas, cx - w * 0.3f, top + h * 0.45f, 200f, s, Theme.PLAYER_COLORS[0], paint)
        sword(canvas, cx + w * 0.3f, top + h * 0.5f, -15f, s, Theme.PLAYER_COLORS[1], paint)
    }

    /** 규칙 그림 공통: 가운데 아래 나무통 + 고리 모양 구멍 몇 개 */
    private fun scene(c: Canvas, w: Float, h: Float, p: Paint, headUp: Boolean) {
        val bw = h * 0.95f
        val bh = h * 0.56f
        val top = h * 0.38f
        val r = bh * 0.3f
        if (headUp) pirate(c, w / 2f, top - r * 0.62f, r * 0.9f, 0f, PirateArt.NERVOUS, p)
        PirateArt.drawSimpleBarrel(c, w / 2f, top, bw, bh, p, path)
        for (k in 0 until 8) {
            val a = Math.PI * 2 * k / 8
            PirateArt.drawSimpleHole(c, w / 2f + (bw * 0.3f * Math.cos(a)).toFloat(), top + bh * 0.55f + (bh * 0.24f * Math.sin(a)).toFloat(), bh * 0.035f, p)
        }
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("빈 구멍에 칼을 꽂아요", "차례가 되면 빈 구멍 하나를 눌러요.\n칼이 천천히 꽂히고… 잠깐의 정적!\n무사하면 칼은 그대로, 다음 사람 차례.") { c, w, h, p ->
            scene(c, w, h, p, headUp = true)
            val bw = h * 0.95f
            val bh = h * 0.56f
            val top = h * 0.38f
            val s = bh * 0.045f
            // 이미 꽂힌 칼 두 자루 + 지금 꽂히는 칼 (움직임 줄)
            sword(c, w / 2f - bw * 0.3f, top + bh * 0.55f, 180f, s, Theme.PLAYER_COLORS[1], p)
            sword(c, w / 2f + bw * 0.21f, top + bh * 0.72f, 30f, s, Theme.PLAYER_COLORS[2], p)
            val hx = w / 2f + bw * 0.3f
            val hy = top + bh * 0.55f
            c.save()
            c.translate(hx, hy)
            c.rotate(-10f)
            PirateArt.drawSword(c, s * 6f, s, Theme.PLAYER_COLORS[0], Theme.shade(Theme.PLAYER_COLORS[0], 0.6f), p)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.5f
            p.color = Theme.GOLD_LIGHT
            c.drawLine(s * 13f, -s * 2.6f, s * 17f, -s * 2.6f, p)
            c.drawLine(s * 13.5f, s * 2.6f, s * 17.5f, s * 2.6f, p)
            c.restore()
        },
        RuleCard("해적이 튀어나오면 탈락!", "판마다 함정 구멍이 몰래 숨어 있어요.\n(2명이면 1개, 3~4명이면 설정에서 1~2개)\n함정을 찌르면 해적이 날아가고 탈락!") { c, w, h, p ->
            scene(c, w, h, p, headUp = false)
            val bh = h * 0.56f
            val top = h * 0.38f
            // 함정 구멍 빨간 빛
            p.style = Paint.Style.FILL
            p.color = 0x99FF4D5E.toInt()
            c.drawCircle(w / 2f, top + bh * 0.79f, bh * 0.1f, p)
            PirateArt.drawSimpleHole(c, w / 2f, top + bh * 0.79f, bh * 0.035f, p)
            sword(c, w / 2f, top + bh * 0.79f, 90f, bh * 0.045f, Theme.PLAYER_COLORS[3], p)
            // 날아가는 해적 + 궤적
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.015f
            p.color = 0x99FFFFFF.toInt()
            path.reset()
            path.moveTo(w / 2f, top - h * 0.02f)
            path.quadTo(w * 0.52f, h * 0.16f, w * 0.66f, h * 0.26f)
            c.drawPath(path, p)
            p.strokeWidth = h * 0.01f
            c.drawLine(w * 0.86f, h * 0.12f, w * 0.93f, h * 0.08f, p)
            c.drawLine(w * 0.88f, h * 0.38f, w * 0.95f, h * 0.4f, p)
            pirate(c, w * 0.76f, h * 0.27f, h * 0.09f, 35f, PirateArt.FLYING, p)
        },
        RuleCard("최후의 1인이 1위", "탈락자가 나오면 통을 비우고 남은 사람끼리 다음 판 (탈락자 다음 사람부터).\n끝까지 살아남으면 1위!\n늦게 탈락할수록 높은 순위예요.") { c, w, h, p ->
            // 시상대: 1위(가운데·왕관) 2위 3위 4위
            val colW = w * 0.17f
            val base = h * 0.9f
            val xs = 0.5f; val hs = 0.42f
            for (k in 0 until 4) {
                val x = w * (if (k == 0) xs else if (k == 1) 0.29f else if (k == 2) 0.71f else 0.88f)
                val ch = h * (if (k == 0) hs else if (k == 1) 0.3f else if (k == 2) 0.2f else 0.12f)
                val cw = if (k == 3) colW * 0.7f else colW
                p.style = Paint.Style.FILL
                p.color = if (k == 0) Theme.GOLD_DARK else Theme.CARD_LIGHT
                c.drawRoundRect(x - cw / 2f, base - ch, x + cw / 2f, base, h * 0.02f, h * 0.02f, p)
                val r = h * (if (k == 0) 0.09f else 0.07f)
                val cy = base - ch - r * 1.15f
                p.color = Theme.PLAYER_COLORS[k]
                c.drawCircle(x, cy, r, p)
                p.color = 0xFFFFFFFF.toInt()
                p.textAlign = Paint.Align.CENTER
                p.textSize = h * 0.09f
                p.isFakeBoldText = true
                c.drawText(if (k == 0) "1" else if (k == 1) "2" else if (k == 2) "3" else "4", x, base - h * 0.03f, p)
                if (k == 0) Ui.drawCrown(c, x, cy - r * 1.05f, r * 1.3f, path, p)
                else {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = r * 0.25f
                    p.strokeCap = Paint.Cap.ROUND
                    p.color = Theme.RED
                    val d = r * 0.5f
                    c.drawLine(x - d, cy - d, x + d, cy + d, p)
                    c.drawLine(x + d, cy - d, x - d, cy + d, p)
                }
            }
        },
    )
}
