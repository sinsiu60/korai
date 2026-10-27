package com.siwoo.party.games.yut

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
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.yut.ui.YutActivity
import com.siwoo.party.games.yut.ui.YutArt
import com.siwoo.party.games.yut.ui.YutSettings

/** 윷놀이: 모음집 등록 정보 */
object YutModule : GameModule {
    override val id = "yut"
    override val name = "윷놀이"
    override val tagline = "윷가락을 던져 말을 달려라! 업고 잡고 지름길로 골인!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "15분"
    override val color = 0xFF8D5A2B.toInt()
    override val activityClass = YutActivity::class.java

    override val settingsLabel: String get() = "윷놀이 설정"

    override fun showSettings(popups: PopupLayer) {
        YutSettings.show(popups)
    }

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val RED_DARK = Theme.shade(RED, 0.45f)
    private val BLUE_DARK = Theme.shade(BLUE, 0.45f)
    private val GREEN_DARK = Theme.shade(GREEN, 0.45f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val path = Path()

    private const val ONE = "1"
    private const val TWO = "2"
    private const val GEOL = "걸!"
    private const val MO = "모!"
    private const val GOAL = "골인!"
    private const val X2 = "×2"
    private const val CHIP_MO = "모"
    private const val CHIP_GEOL = "걸"
    private const val CHIP_BACK = "빽도"
    private const val CAUGHT = "잡았다!"

    /** 판 위 칸 좌표 (판 left, top, size) */
    private fun nx(l: Float, s: Float, n: Int): Float = l + s * (BOARD_INSET + YutBoard.X[n] * (1f - 2 * BOARD_INSET))
    private fun ny(t: Float, s: Float, n: Int): Float = t + s * (BOARD_INSET + YutBoard.Y[n] * (1f - 2 * BOARD_INSET))
    private const val BOARD_INSET = 0.085f

    /** 외곽선 글자 */
    private fun outlined(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = size * 0.16f
        p.color = Theme.INK
        c.drawText(s, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** 대기열 칩 하나 */
    private fun chip(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, selected: Boolean, p: Paint) {
        val r = h * 0.42f
        p.style = Paint.Style.FILL
        p.color = if (selected) Theme.GOLD_DARK else 0xFF8A6A3E.toInt()
        c.drawRoundRect(x - w / 2, y - h / 2 + h * 0.1f, x + w / 2, y + h / 2 + h * 0.1f, r, r, p)
        p.color = if (selected) Theme.GOLD else YutArt.HANJI
        c.drawRoundRect(x - w / 2, y - h / 2, x + w / 2, y + h / 2, r, r, p)
        text.color = if (label == CHIP_BACK) YutArt.SEAL else YutArt.INK
        text.textSize = h * 0.55f
        c.drawText(label, x, y + text.textSize * 0.36f, text)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_LIGHT
        c.drawOval(x - s * 0.22f, y - s, x + s * 0.22f, y + s, p)
        c.drawOval(x - s, y - s * 0.22f, x + s, y + s * 0.22f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // 뒤: 윷판 (살짝 기울임), 앞: 윷가락 셋
        val bs = size * 0.62f
        canvas.save()
        canvas.rotate(-8f, cx, cy)
        val l = cx - bs / 2f - size * 0.08f
        val t = cy - bs / 2f - size * 0.1f
        YutArt.drawBoard(canvas, l, t, bs, 0.1f, paint, null, null)
        val s = bs * 0.8f
        val gl = l + bs * 0.1f
        val gt = t + bs * 0.1f
        text.color = Theme.TEXT
        YutArt.drawPiece(canvas, gl + YutBoard.X[22] * s, gt + YutBoard.Y[22] * s, bs * 0.075f, RED, RED_DARK, null, paint, null)
        YutArt.drawPiece(canvas, gl + YutBoard.X[7] * s, gt + YutBoard.Y[7] * s, bs * 0.075f, BLUE, BLUE_DARK, null, paint, null)
        canvas.restore()
        val len = size * 0.5f
        val w = size * 0.1f
        YutArt.drawStick(canvas, cx + size * 0.12f, cy + size * 0.2f, len, w, 62f, 1f, true, true, paint, text)
        YutArt.drawStick(canvas, cx + size * 0.2f, cy + size * 0.12f, len, w, 35f, 1f, false, false, paint, text)
        YutArt.drawStick(canvas, cx + size * 0.28f, cy + size * 0.06f, len, w, 12f, 1f, true, false, paint, text)
        sparkle(canvas, cx + size * 0.36f, cy - size * 0.3f, size * 0.06f, paint)
        sparkle(canvas, cx - size * 0.38f, cy + size * 0.3f, size * 0.045f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("윷을 던져요", "아래 멍석의 윷가락을 터치해서 던져요 (설정에서 흔들어 던지기도).\n배(평평한 면) 1개 도 · 2개 개 · 3개 걸 · 4개 윷, 모두 등이면 모! 표시 가락 하나만 배면 빽도(한 칸 뒤).\n윷·모는 한 번 더 던져요.") { c, w, h, p ->
            // 멍석 + 윷가락 4개 (걸: 배 3, 등 1)
            val mw = h * 1.1f
            val mh = h * 0.8f
            val ml = w * 0.42f - mw / 2f
            val mt = h * 0.12f
            p.style = Paint.Style.FILL
            p.color = YutArt.MAT_DARK
            c.drawRoundRect(ml, mt, ml + mw, mt + mh, h * 0.06f, h * 0.06f, p)
            p.color = YutArt.MAT
            c.drawRoundRect(ml + h * 0.02f, mt + h * 0.02f, ml + mw - h * 0.02f, mt + mh - h * 0.02f, h * 0.05f, h * 0.05f, p)
            p.color = YutArt.MAT_LINE
            for (k in 1 until 10) c.drawRect(ml + h * 0.03f, mt + mh * k / 10f, ml + mw - h * 0.03f, mt + mh * k / 10f + h * 0.004f, p)
            val len = mh * 0.6f
            val sw = len * 0.2f
            val faces = booleanArrayOf(false, true, true, true)
            val degs = floatArrayOf(-8f, 5f, -3f, 9f)
            for (k in 0 until 4) {
                YutArt.drawStick(c, ml + mw * (0.2f + 0.2f * k), mt + mh * 0.58f, len, sw, degs[k], 1f, faces[k], k == 0, p, text)
            }
            outlined(c, GEOL, ml + mw / 2f, mt + mh * 0.2f, h * 0.15f, Theme.TEXT, p)
            // 오른쪽: 모! (금색) + 한 번 더 화살표
            outlined(c, MO, w * 0.83f, h * 0.42f, h * 0.2f, Theme.GOLD, p)
            sparkle(c, w * 0.92f, h * 0.2f, h * 0.05f, p)
            sparkle(c, w * 0.73f, h * 0.24f, h * 0.035f, p)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.02f
            p.color = Theme.GOLD_LIGHT
            path.reset()
            path.addArc(w * 0.74f, h * 0.52f, w * 0.92f, h * 0.84f, 200f, 280f)
            c.drawPath(path, p)
            c.drawLine(w * 0.74f + h * 0.005f, h * 0.66f, w * 0.725f, h * 0.6f, p)
            c.drawLine(w * 0.74f + h * 0.005f, h * 0.66f, w * 0.775f, h * 0.61f, p)
            p.style = Paint.Style.FILL
        },
        RuleCard("결과 칩 → 말 → 빛나는 칸", "던진 결과는 이동 대기열에 칩으로 쌓여요. 칩을 고르고 말(새 말은 패널의 대기 말)을 고르면 도착할 칸이 빛나요. 그 칸을 눌러 이동!\n모서리·방에 딱 멈추면 다음엔 지름길. 내 말 위면 업고 같이, 상대 말이면 잡고 한 번 더!") { c, w, h, p ->
            val bs = h * 0.86f
            val l = w * 0.04f
            val t = h * 0.07f
            YutArt.drawBoard(c, l, t, bs, BOARD_INSET, p, null, null)
            // 빨강 2 → 3, 4, 5 (파랑 말 위 → 잡기)
            val pr = bs * 0.045f
            p.style = Paint.Style.FILL
            for (n in intArrayOf(3, 4)) {
                p.color = RED
                p.alpha = 130
                c.drawCircle(nx(l, bs, n), ny(t, bs, n), pr * 0.6f, p)
                p.alpha = 255
                p.color = Theme.GOLD_LIGHT
                c.drawCircle(nx(l, bs, n), ny(t, bs, n), pr * 0.3f, p)
            }
            val dx = nx(l, bs, 5)
            val dy = ny(t, bs, 5)
            for (ring in 3 downTo 1) {
                p.color = RED
                p.alpha = 70 * (4 - ring) / 3
                c.drawCircle(dx, dy, pr * (1.2f + ring * 0.4f), p)
            }
            p.alpha = 255
            text.color = Theme.TEXT
            YutArt.drawPiece(c, dx, dy, pr, BLUE, BLUE_DARK, ONE, p, text)
            p.style = Paint.Style.STROKE
            p.color = Theme.GOLD
            p.strokeWidth = pr * 0.22f
            c.drawCircle(dx, dy, pr * 1.25f, p)
            p.style = Paint.Style.FILL
            YutArt.drawPiece(c, nx(l, bs, 2), ny(t, bs, 2), pr, RED, RED_DARK, ONE, p, text)
            p.style = Paint.Style.STROKE
            p.color = Theme.GOLD
            p.strokeWidth = pr * 0.25f
            c.drawCircle(nx(l, bs, 2), ny(t, bs, 2), pr * 1.5f, p)
            p.style = Paint.Style.FILL
            // 업힌 초록 묶음 (×2) — 방
            val cx = nx(l, bs, 22)
            val cy = ny(t, bs, 22)
            YutArt.drawPiece(c, cx, cy, pr, GREEN, GREEN_DARK, null, p, null)
            YutArt.drawPiece(c, cx, cy - pr * 0.34f, pr, GREEN, GREEN_DARK, TWO, p, text)
            p.color = Theme.GOLD
            c.drawCircle(cx + pr, cy - pr * 1.2f, pr * 0.55f, p)
            text.color = YutArt.INK
            text.textSize = pr * 0.6f
            c.drawText(X2, cx + pr, cy - pr * 1.2f + text.textSize * 0.36f, text)
            // 오른쪽: 대기열 칩 (걸 고름) + 판 위 "잡았다!"
            val qx = (l + bs + w) / 2f
            val ch = h * 0.15f
            val cw = minOf(ch * 1.9f, (w - l - bs) * 0.8f)
            chip(c, qx, h * 0.2f, cw, ch, CHIP_GEOL, true, p)
            chip(c, qx, h * 0.2f + ch * 1.4f, cw, ch, CHIP_MO, false, p)
            chip(c, qx, h * 0.2f + ch * 2.8f, cw, ch, CHIP_BACK, false, p)
            outlined(c, CAUGHT, l + bs * 0.5f, t + bs * 0.33f, h * 0.13f, Theme.GOLD, p)
        },
        RuleCard("먼저 다 골인하면 1등", "참먹이(출발점)에 도착하거나 지나가면 골인! 말(기본 4개, 설정 2~4)을 모두 골인시킨 순서로 순위가 정해져요.\n3~4명이면 1등이 나온 뒤 [끝까지 진행 / 여기서 종료]를 골라요. 여기서 끝내면 골인 수 → 진행 거리 순.") { c, w, h, p ->
            val bs = h * 0.86f
            val l = w * 0.04f
            val t = h * 0.07f
            YutArt.drawBoard(c, l, t, bs, BOARD_INSET, p, null, null)
            val pr = bs * 0.045f
            text.color = Theme.TEXT
            YutArt.drawPiece(c, nx(l, bs, 17), ny(t, bs, 17), pr, BLUE, BLUE_DARK, TWO, p, text)
            YutArt.drawPiece(c, nx(l, bs, 9), ny(t, bs, 9), pr, GREEN, GREEN_DARK, ONE, p, text)
            // 참먹이에서 솟아올라 오른쪽으로 날아가는 빨강 말 + 궤적
            val sx = nx(l, bs, 0)
            val sy = ny(t, bs, 0)
            val rx = (l + bs + w) / 2f
            val ry = h * 0.36f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.015f
            p.color = 0x99FFFFFF.toInt()
            path.reset()
            path.moveTo(sx, sy)
            path.quadTo(sx, ry - h * 0.1f, rx - pr * 1.5f, ry)
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
            sparkle(c, sx, sy, h * 0.06f, p)
            YutArt.drawPiece(c, rx, ry, h * 0.075f, RED, RED_DARK, ONE, p, text)
            Ui.drawCrown(c, rx, ry - h * 0.085f, h * 0.15f, path, p)
            sparkle(c, rx + h * 0.13f, ry - h * 0.2f, h * 0.035f, p)
            outlined(c, GOAL, rx, h * 0.66f, h * 0.12f, Theme.GOLD, p)
            p.color = Theme.GOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.1f
            p.typeface = Typeface.DEFAULT_BOLD
            c.drawText(FIRST, rx, h * 0.86f, p)
            p.textAlign = Paint.Align.LEFT
        },
    )

    private const val FIRST = "1위"
}
