package com.siwoo.party.games.liardice

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.liardice.ui.LiarDiceActivity
import com.siwoo.party.games.liardice.ui.LiarDiceArt
import com.siwoo.party.games.liardice.ui.LiarDiceSettings

/** 거짓말 주사위: 모음집 등록 정보 */
object LiarDiceModule : GameModule {
    override val id = "liardice"
    override val name = "거짓말 주사위"
    override val tagline = "컵 속 주사위는 나만 안다! 허세로 부르고, 거짓말이면 의심!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "10분"
    override val color = 0xFFB0413E.toInt()
    override val activityClass = LiarDiceActivity::class.java

    /** 허브 소개 팝업의 [1 와일드 설정] → 켜기/끄기 (SharedPreferences "liardice") */
    override val settingsLabel: String get() = "1 와일드 설정"

    override fun showSettings(popups: PopupLayer) {
        LiarDiceSettings.show(popups)
    }

    private val path = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.38f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF1F7A54.toInt() // 초록 펠트
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.1f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.88f, paint)
        // 기울어진 컵 (왼쪽) + 굴러 나온 주사위 두 개 (오른쪽 아래)
        LiarDiceArt.drawCup(canvas, cx - r * 0.22f, cy + r * 0.3f, r * 0.8f, r * 0.9f, color, paint, path, -22f)
        LiarDiceArt.drawDie(canvas, cx + r * 0.38f, cy + r * 0.38f, r * 0.36f, 5, paint, 0, 12f)
        LiarDiceArt.drawDie(canvas, cx + r * 0.05f, cy + r * 0.56f, r * 0.3f, 1, paint, 0, -8f)
        // 금색 물음표 (오른쪽 위)
        paint.style = Paint.Style.FILL
        paint.color = Theme.GOLD
        paint.textSize = r * 0.62f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("?", cx + r * 0.42f, cy - r * 0.1f, paint)
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
    }

    /** 규칙 그림: 둥근 말풍선 + 주사위 + "× N" */
    private fun bidBubble(c: Canvas, x: Float, y: Float, s: Float, face: Int, count: String, p: Paint, ok: Int) {
        p.style = Paint.Style.FILL
        p.color = Theme.PAPER
        val w = s * 2.7f
        c.drawRoundRect(x - w / 2f, y - s * 0.75f, x + w / 2f, y + s * 0.75f, s * 0.5f, s * 0.5f, p)
        if (ok != 0) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.1f
            p.color = ok
            c.drawRoundRect(x - w / 2f, y - s * 0.75f, x + w / 2f, y + s * 0.75f, s * 0.5f, s * 0.5f, p)
            p.style = Paint.Style.FILL
        }
        LiarDiceArt.drawDie(c, x - s * 0.62f, y, s * 0.9f, face, p)
        p.color = Theme.INK
        p.textSize = s * 0.8f
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        c.drawText(count, x + s * 0.62f, y + s * 0.28f, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    private fun arrow(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.color = Theme.GOLD
        c.drawLine(x0, y0, x1, y1, p)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / len
        val uy = dy / len
        val h = w * 3f
        c.drawLine(x1, y1, x1 - ux * h - uy * h * 0.7f, y1 - uy * h + ux * h * 0.7f, p)
        c.drawLine(x1, y1, x1 - ux * h + uy * h * 0.7f, y1 - uy * h - ux * h * 0.7f, p)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun felt(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF1F7A54.toInt()
        c.drawRoundRect(w * 0.03f, h * 0.05f, w * 0.97f, h * 0.95f, h * 0.08f, h * 0.08f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.02f
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(w * 0.03f, h * 0.05f, w * 0.97f, h * 0.95f, h * 0.08f, h * 0.08f, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("몰래 굴리고 혼자 확인", "각자 주사위 5개를 컵 속에서 굴려요.\n기기를 넘겨받으면 카드를 꾹 눌러\n자기 주사위만 몰래 보세요.") { c, w, h, p ->
            felt(c, w, h, p)
            val base = h * 0.78f
            LiarDiceArt.drawCup(c, w * 0.18f, base, h * 0.36f, h * 0.4f, Theme.PLAYER_COLORS[0], p, path)
            LiarDiceArt.drawCup(c, w * 0.82f, base, h * 0.36f, h * 0.4f, Theme.PLAYER_COLORS[1], p, path)
            // 가운데: 살짝 든 컵 아래 주사위 세 개
            val s = h * 0.16f
            LiarDiceArt.drawDie(c, w * 0.5f - s * 1.25f, base - s * 0.6f, s, 4, p)
            LiarDiceArt.drawDie(c, w * 0.5f, base - s * 0.6f, s, 1, p)
            LiarDiceArt.drawDie(c, w * 0.5f + s * 1.25f, base - s * 0.6f, s, 6, p)
            LiarDiceArt.drawCup(c, w * 0.5f + h * 0.05f, base - h * 0.3f, h * 0.42f, h * 0.42f, Theme.PLAYER_COLORS[2], p, path, 18f)
        },
        RuleCard("더 높게 부르기", "\"탁자 위 전체에 4 눈이 3개 이상!\"처럼 불러요. 다음 사람은 개수를 늘리거나, 같은 개수면 더 높은 눈으로! 1은 모든 눈으로 세요 (와일드 · 설정에서 끌 수 있음).") { c, w, h, p ->
            val s = h * 0.2f
            bidBubble(c, w * 0.22f, h * 0.5f, s, 4, "×3", p, 0)
            arrow(c, w * 0.4f, h * 0.45f, w * 0.55f, h * 0.27f, h * 0.025f, p)
            arrow(c, w * 0.4f, h * 0.55f, w * 0.55f, h * 0.73f, h * 0.025f, p)
            bidBubble(c, w * 0.76f, h * 0.24f, s, 4, "×4", p, Theme.GREEN)
            bidBubble(c, w * 0.76f, h * 0.76f, s, 5, "×3", p, Theme.GREEN)
        },
        RuleCard("의심! 모두 공개", "거짓말 같으면 [의심!] → 모든 주사위를 열어 세요. 실제가 부른 수 이상이면 의심한 사람이, 아니면 부른 사람이 주사위 1개를 잃어요. 다 잃으면 탈락, 마지막 1명이 1위!") { c, w, h, p ->
            felt(c, w, h, p)
            val s = h * 0.17f
            val faces = DEMO_FACES
            for (k in faces.indices) {
                val x = w * 0.14f + k * s * 1.45f
                val y = h * 0.62f
                val ring = when {
                    faces[k] == 4 -> Theme.GOLD
                    faces[k] == 1 -> LiarDiceArt.WILD
                    else -> 0
                }
                LiarDiceArt.drawDie(c, x, y, s, faces[k], p, ring)
                if (ring == 0) {
                    p.color = 0x99070D2E.toInt()
                    c.drawRoundRect(x - s * 0.55f, y - s * 0.55f, x + s * 0.55f, y + s * 0.55f, s * 0.2f, s * 0.2f, p)
                }
            }
            LiarDiceArt.drawDoubtStamp(c, w * 0.3f, h * 0.33f, h * 0.16f, p)
            p.color = Theme.GOLD
            p.textSize = h * 0.2f
            p.isFakeBoldText = true
            p.textAlign = Paint.Align.CENTER
            c.drawText("3개", w * 0.78f, h * 0.36f, p)
            p.isFakeBoldText = false
            p.textAlign = Paint.Align.LEFT
        },
    )

    private val DEMO_FACES = intArrayOf(4, 2, 1, 6, 4)
}
