package com.siwoo.party.games.updown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.updown.ui.UpDownActivity
import com.siwoo.party.games.updown.ui.UpDownArt

/** 폭탄 숫자 업다운: 모음집 등록 정보 */
object UpDownModule : GameModule {
    override val id = "updown"
    override val name = "폭탄 숫자 업다운"
    override val tagline = "1~100 사이 숨은 폭탄 숫자! 범위는 점점 좁아지고… 누가 터뜨릴까?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "5분"
    override val color = 0xFFD84315.toInt()
    override val activityClass = UpDownActivity::class.java

    private val arrow = Path()

    /** 굵은 화살표 (cx, cy, 크기 s, 위쪽이면 up) */
    private fun arrow(c: Canvas, cx: Float, cy: Float, s: Float, up: Boolean, color: Int, p: Paint) {
        val d = if (up) -1f else 1f
        arrow.reset()
        arrow.moveTo(cx, cy + d * s * 0.5f)
        arrow.lineTo(cx + s * 0.42f, cy)
        arrow.lineTo(cx + s * 0.16f, cy)
        arrow.lineTo(cx + s * 0.16f, cy - d * s * 0.5f)
        arrow.lineTo(cx - s * 0.16f, cy - d * s * 0.5f)
        arrow.lineTo(cx - s * 0.16f, cy)
        arrow.lineTo(cx - s * 0.42f, cy)
        arrow.close()
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.save()
        c.translate(s * 0.04f, s * 0.06f)
        c.drawPath(arrow, p)
        c.restore()
        p.color = color
        c.drawPath(arrow, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        UpDownArt.bomb(canvas, cx - size * 0.04f, cy + size * 0.06f, size * 0.26f, paint, "?")
        arrow(canvas, cx - size * 0.34f, cy - size * 0.2f, size * 0.26f, true, UpDownArt.UP, paint)
        arrow(canvas, cx + size * 0.33f, cy + size * 0.24f, size * 0.26f, false, UpDownArt.DOWN, paint)
    }

    /** 규칙 그림용 막대: 1~100 중 lo~hi 가 열린 범위 */
    private fun bar(c: Canvas, w: Float, h: Float, y: Float, lo: Int, hi: Int, p: Paint) {
        val l = w * 0.08f
        val r = w * 0.92f
        val bh = h * 0.12f
        val cell = (r - l) / 100f
        p.style = Paint.Style.FILL
        p.color = UpDownArt.CUT
        c.drawRoundRect(l, y, r, y + bh, bh / 2f, bh / 2f, p)
        p.color = UpDownArt.FUSE_A
        c.drawRoundRect(l + (lo - 1) * cell, y, l + hi * cell, y + bh, bh / 2f, bh / 2f, p)
        p.color = 0x55FFFFFF
        c.drawRoundRect(l + (lo - 1) * cell + bh * 0.3f, y + bh * 0.15f, l + hi * cell - bh * 0.3f, y + bh * 0.4f, bh * 0.15f, bh * 0.15f, p)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = h * 0.075f
        p.color = Theme.TEXT_SUB
        p.textAlign = Paint.Align.LEFT
        c.drawText("1", l, y + bh + h * 0.1f, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("100", r, y + bh + h * 0.1f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("1~100 중 폭탄 숫자",
            "라운드마다 1~100 중 하나에 폭탄이 숨어요. 범위는 가로 막대로 보여요.\n라운드 수는 인원수만큼, 시작하는 사람은 한 명씩 돌아가요.") { c, w, h, p ->
            bar(c, w, h, h * 0.6f, 1, 100, p)
            UpDownArt.bomb(c, w * 0.5f, h * 0.36f, h * 0.13f, p, "?")
        },
        RuleCard("크면 위쪽, 작으면 아래쪽 싹둑!",
            "차례대로 숫자 패드로 범위 안 숫자를 눌러요. 폭탄보다 크면 위쪽이,\n작으면 아래쪽이 잘려 나가요. 잘린 숫자는 다시 못 눌러요.") { c, w, h, p ->
            bar(c, w, h, h * 0.6f, 31, 69, p)
            // 날아가는 조각 (위쪽)
            p.style = Paint.Style.FILL
            p.color = UpDownArt.DOWN
            c.save()
            c.rotate(18f, w * 0.85f, h * 0.3f)
            c.drawRoundRect(w * 0.74f, h * 0.24f, w * 0.94f, h * 0.36f, h * 0.06f, h * 0.06f, p)
            c.restore()
            p.color = UpDownArt.UP
            c.save()
            c.rotate(-14f, w * 0.15f, h * 0.88f)
            c.drawRoundRect(w * 0.04f, h * 0.83f, w * 0.27f, h * 0.95f, h * 0.06f, h * 0.06f, p)
            c.restore()
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.12f
            p.color = UpDownArt.DOWN
            c.drawText("DOWN ▼", w * 0.6f, h * 0.2f, p)
            p.color = UpDownArt.UP
            c.drawText("UP ▲", w * 0.3f, h * 0.4f, p)
        },
        RuleCard("폭탄을 누르면 펑! 해골 +1",
            "폭탄 숫자를 누르면 펑! 그 사람 해골 +1. 폭탄만 남으면 다음 사람은 눌러야 해요.\n모든 라운드가 끝나면 해골이 적은 순으로 순위 (같으면 같은 순위).") { c, w, h, p ->
            p.style = Paint.Style.FILL
            p.color = 0x99FF6A00.toInt()
            c.drawCircle(w * 0.36f, h * 0.48f, h * 0.36f, p)
            p.color = 0xCCFFE08A.toInt()
            c.drawCircle(w * 0.36f, h * 0.48f, h * 0.2f, p)
            UpDownArt.sparkAt(c, w * 0.2f, h * 0.22f, h * 0.08f, p)
            UpDownArt.sparkAt(c, w * 0.52f, h * 0.78f, h * 0.07f, p)
            UpDownArt.bomb(c, w * 0.36f, h * 0.5f, h * 0.15f, p, "42")
            UpDownArt.skull(c, w * 0.74f, h * 0.44f, h * 0.38f, p)
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.13f
            p.color = Theme.RED
            c.drawText("+1", w * 0.74f, h * 0.9f, p)
        },
    )
}
