package com.siwoo.party.games.darts

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
import com.siwoo.party.games.darts.ui.DartArt
import com.siwoo.party.games.darts.ui.DartsActivity
import com.siwoo.party.games.darts.ui.DartsSettings
import kotlin.math.PI
import kotlin.math.sin

/** 다트: 모음집 등록 정보 (2~4인, 차례대로) */
object DartsModule : GameModule {
    override val id = "darts"
    override val name = "다트"
    override val tagline = "8자로 흔들리는 조준점, 지금이다 싶을 때 터치!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "8분"
    override val color = 0xFF1C8A4A.toInt()
    override val activityClass = DartsActivity::class.java

    /** 허브 소개 팝업의 [경기 방식] → 라운드 점수제 / 301 (SharedPreferences "darts") */
    override val settingsLabel: String get() = "경기 방식"

    override fun showSettings(popups: PopupLayer) {
        DartsSettings.show(popups)
    }

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val path = Path()
    private val xy = FloatArray(2)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        // 다트판 (테두리까지 아이콘 원에 꽉)
        val s = r * 0.97f / GameConfig.R_FRAME
        DartArt.board(canvas, paint, cx, cy, s, wires = false, text = null)
        // 트리플 20 · 불에 꽂힌 다트
        Board.center(Ring.TRIPLE, 20, xy)
        DartArt.dart(canvas, paint, path, cx + xy[0] * s, cy + xy[1] * s, r * 0.62f, 28f, BLUE, 255, 1f)
        DartArt.dart(canvas, paint, path, cx + r * 0.02f, cy + r * 0.02f, r * 0.82f, 34f, RED, 255, 1f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.97f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        p.textAlign = align
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF0B1340.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 손가락 (톡) */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.16f
        p.color = Theme.GOLD
        c.drawCircle(x, y, r * 1.6f, p)
        c.drawCircle(x, y, r * 2.2f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE0C8.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.2f
        p.color = 0xFF8A5A26.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    /** 조준 십자선 */
    private fun crosshair(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (pass in 0..1) {
            p.color = if (pass == 0) 0x99000000.toInt() else Theme.TEXT
            p.strokeWidth = r * (if (pass == 0) 0.3f else 0.15f)
            c.drawCircle(x, y, r, p)
            c.drawLine(x - r * 1.35f, y, x - r * 0.45f, y, p)
            c.drawLine(x + r * 0.45f, y, x + r * 1.35f, y, p)
            c.drawLine(x, y - r * 1.35f, x, y - r * 0.45f, p)
            c.drawLine(x, y + r * 0.45f, x, y + r * 1.35f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.color = Theme.RED
        c.drawCircle(x, y, r * 0.18f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("흔들리는 조준점 · 터치로 던지기", "조준점이 8자로 흔들려요.\n노리는 칸을 지날 때\n화면 아무 곳이나 터치!\n다트가 조준점으로 날아가 꽂혀요\n(아주 살짝 빗나갈 수 있어요)") { c, w, h, p ->
            val bx = w * 0.36f
            val by = h * 0.5f
            val s = h * 0.46f / GameConfig.R_FRAME
            DartArt.board(c, p, bx, by, s, wires = true, text = null)
            // 8자 궤적 (점선)
            val ax = GameConfig.SWAY_AMP_X * s
            val ay = GameConfig.SWAY_AMP_Y * s
            for (k in 0 until 40) {
                val th = 2.0 * PI * k / 40
                val px = bx + (sin(th) * ax).toFloat()
                val py = by + (sin(2.0 * th) * ay).toFloat()
                p.color = 0xAA000000.toInt()
                c.drawCircle(px, py, h * 0.017f, p)
                p.color = Theme.GOLD_LIGHT
                c.drawCircle(px, py, h * 0.01f, p)
            }
            // 조준점 (8자 위)
            val th = 2.0 * PI * 0.1
            val tx = bx + (sin(th) * ax).toFloat()
            val ty = by + (sin(2.0 * th) * ay).toFloat()
            crosshair(c, p, tx, ty, h * 0.055f)
            // 움직이는 방향 화살표
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.018f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD
            val th2 = 2.0 * PI * 0.17
            val ux = bx + (sin(th2) * ax).toFloat()
            val uy = by + (sin(2.0 * th2) * ay).toFloat()
            c.drawLine(tx + h * 0.07f, ty - h * 0.02f, ux, uy - h * 0.06f, p)
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
            // 오른쪽: 손가락 톡
            finger(c, p, w * 0.84f, h * 0.62f, h * 0.06f)
            shout(c, p, "톡!", w * 0.84f, h * 0.3f, h * 0.15f, Theme.GOLD)
        },
        RuleCard("점수 판정", "숫자 칸 점수 그대로 (싱글)\n좁은 바깥 고리 = 더블 ×2\n좁은 안쪽 고리 = 트리플 ×3\n초록 불 25 · 빨강 불 50\n판 밖은 아웃 (0점)") { c, w, h, p ->
            // 20 칸을 크게 확대 (판 가운데는 아래쪽 밖)
            val s = h * 0.82f / GameConfig.R_DOUBLE_OUT
            val bx = w * 0.3f
            val by = h * 1.04f
            c.save()
            c.clipRect(0f, 0f, w * 0.62f, h)
            DartArt.board(c, p, bx, by, s, wires = true, text = null)
            c.restore()
            // 꽂힌 다트 셋: 트리플 20 · 더블 20 · 불
            Board.center(Ring.TRIPLE, 20, xy)
            DartArt.dart(c, p, path, bx + xy[0] * s, by + xy[1] * s, h * 0.2f, 30f, RED, 255, 1f)
            Board.center(Ring.DOUBLE, 20, xy)
            DartArt.dart(c, p, path, bx + xy[0] * s, by + xy[1] * s, h * 0.2f, 30f, RED, 255, 1f)
            // 오른쪽 글자 표
            val tx = w * 0.66f
            shout(c, p, "T20 = 60", tx, h * 0.3f, h * 0.11f, Theme.GOLD, Paint.Align.LEFT)
            shout(c, p, "D20 = 40", tx, h * 0.5f, h * 0.11f, Theme.TEXT, Paint.Align.LEFT)
            p.color = DartArt.GREEN
            c.drawCircle(tx + h * 0.05f, h * 0.68f, h * 0.05f, p)
            shout(c, p, "25", tx + h * 0.13f, h * 0.72f, h * 0.11f, Theme.TEXT, Paint.Align.LEFT)
            p.color = DartArt.RED
            c.drawCircle(tx + h * 0.05f, h * 0.86f, h * 0.05f, p)
            shout(c, p, "50", tx + h * 0.13f, h * 0.9f, h * 0.11f, Theme.TEXT, Paint.Align.LEFT)
        },
        RuleCard("라운드 점수제 · 301", "한 차례 3발, 던진 뒤 다트 회수\n라운드제(기본): 8라운드 합계 순\n301: 301에서 빼서 0을 먼저!\n0 밑으로 가면 버스트 (그 차례 취소)\n방식은 [경기 방식]에서 골라요") { c, w, h, p ->
            val bx = w * 0.27f
            val by = h * 0.55f
            val s = h * 0.4f / GameConfig.R_FRAME
            DartArt.board(c, p, bx, by, s, wires = false, text = null)
            // 트리플 20 에 세 발 → 180
            for (k in 0..2) {
                Board.toXY(103f, -4f + k * 4f, xy)
                DartArt.dart(c, p, path, bx + xy[0] * s, by + xy[1] * s, h * 0.15f, 20f + k * 9f, BLUE, 255, 1f)
            }
            shout(c, p, "180!!", bx, h * 0.17f, h * 0.15f, Theme.GOLD)
            // 오른쪽: 칠판 301 → 0
            val l = w * 0.56f
            val r = w * 0.96f
            p.color = 0xFF1F3A2C.toInt()
            c.drawRoundRect(l, h * 0.14f, r, h * 0.86f, h * 0.05f, h * 0.05f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.025f
            p.color = 0xFF8A6237.toInt()
            c.drawRoundRect(l, h * 0.14f, r, h * 0.86f, h * 0.05f, h * 0.05f, p)
            p.style = Paint.Style.FILL
            val cx = (l + r) / 2f
            shout(c, p, "301", cx, h * 0.34f, h * 0.13f, 0xFFF4F4E8.toInt())
            shout(c, p, "−180", cx, h * 0.52f, h * 0.11f, 0xFFF4F4E8.toInt())
            p.color = 0xCCF4F4E8.toInt()
            c.drawRect(cx - w * 0.13f, h * 0.57f, cx + w * 0.13f, h * 0.58f + h * 0.01f, p)
            shout(c, p, "121", cx, h * 0.73f, h * 0.13f, Theme.GOLD)
        },
    )
}
