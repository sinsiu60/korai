package com.siwoo.party.games.colorcode

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.colorcode.ui.ColorArt
import com.siwoo.party.games.colorcode.ui.ColorCodeActivity
import com.siwoo.party.games.colorcode.ui.ColorCodeSettings

/** 색 코드 맞히기: 모음집 등록 정보 (2인 전용, 차례대로, 각자 비밀 코드) */
object ColorCodeModule : GameModule {
    private val crownPath = Path()

    /** 규칙 3 그림: 두 기록판의 줄 (색 4개 + 금 + 은). 왼쪽 정답 = 하늘·분홍·파랑·빨강 */
    private val R3_ROWS = arrayOf(
        arrayOf(intArrayOf(0, 1, 2, 3, 1, 1), intArrayOf(5, 0, 2, 4, 2, 2), intArrayOf(5, 4, 2, 0, 4, 0)),
        arrayOf(intArrayOf(1, 3, 0, 4, 0, 2), intArrayOf(3, 0, 4, 1, 1, 1), intArrayOf(0, 4, 1, 3, 2, 1)),
    )

    override val id = "colorcode"
    override val name = "색 코드 맞히기"
    override val tagline = "상대의 비밀 색 4칸을 금색·은색 핀 힌트로 더 빨리 맞혀라!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "10분"
    override val color = 0xFF7A3FD0.toInt()
    override val activityClass = ColorCodeActivity::class.java

    /** 허브 소개 팝업의 [중복 색 설정] → 켜기/끄기 (SharedPreferences "colorcode") */
    override val settingsLabel: String get() = "중복 색 설정"

    override fun showSettings(popups: PopupLayer) {
        ColorCodeSettings.show(popups)
    }

    /** 말 홈 판 (가로 막대) + 말들. colors 의 -1 은 빈 구멍, -2 는 가려진 "?" */
    private fun tray(c: Canvas, cx: Float, cy: Float, r: Float, step: Float, c0: Int, c1: Int, c2: Int, c3: Int, p: Paint) {
        val w = step * 4f + r * 0.6f
        val h = r * 2.7f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(cx - w / 2f, cy - h / 2f + h * 0.08f, cx + w / 2f, cy + h / 2f + h * 0.08f, h / 2f, h / 2f, p)
        p.color = ColorArt.BOARD
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h / 2f, h / 2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h / 2f, h / 2f, p)
        for (i in 0 until 4) {
            val x = cx + step * (i - 1.5f)
            val col = when (i) { 0 -> c0; 1 -> c1; 2 -> c2; else -> c3 }
            when {
                col >= 0 -> ColorArt.peg(c, x, cy, r, col, p)
                col == -1 -> ColorArt.hole(c, x, cy, r, p)
                else -> {
                    p.style = Paint.Style.FILL
                    p.color = 0xFF0C1338.toInt()
                    c.drawCircle(x, cy, r * 0.9f, p)
                    ColorArt.boldText(p, r * 1.2f, Theme.GOLD)
                    c.drawText("?", x, cy + r * 0.42f, p)
                }
            }
        }
        p.style = Paint.Style.FILL
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF2A1C63.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 위: 말 4개 홈 / 아래: 금·은 핀 2×2
        val pr = r * 0.17f
        tray(canvas, cx, cy - r * 0.2f, pr, pr * 2.25f, 0, 2, 1, 4, paint)
        val pinR = r * 0.1f
        val step = r * 0.26f
        paint.style = Paint.Style.FILL
        paint.color = 0x66000000
        canvas.drawRoundRect(cx - step - pinR * 1.4f, cy + r * 0.48f - step / 2f - pinR * 1.4f, cx + step + pinR * 1.4f,
            cy + r * 0.48f + step / 2f + pinR * 1.4f, pinR * 1.6f, pinR * 1.6f, paint)
        ColorArt.pins(canvas, cx, cy + r * 0.48f, step, pinR, 2, 1, 4, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("비밀 색 코드 정하기",
            "기기를 넘겨 받아 6색 중 4칸을 몰래 골라요 ([무작위]도 있어요).\n기본은 같은 색 한 번만 — 게임 설정에서 중복 허용을 켤 수 있어요.") { c, w, h, p ->
            val r = h * 0.11f
            tray(c, w * 0.5f, h * 0.26f, r, r * 2.4f, 3, 0, 5, -1, p)
            // 색 단추 6개 (기호 포함)
            val br = h * 0.1f
            val step = w * 0.145f
            for (k in 0 until GameConfig.COLORS) {
                val x = w * 0.5f + step * (k - 2.5f)
                val y = h * 0.66f
                p.style = Paint.Style.FILL
                p.color = 0xFF2B3D8E.toInt()
                c.drawCircle(x, y, br * 1.12f, p)
                ColorArt.peg(c, x, y, br, k, p, if (k == 0 || k == 3 || k == 5) 90 else 255)
                ColorArt.boldText(p, h * 0.07f, Theme.TEXT_SUB)
                c.drawText(ColorArt.NAMES[k], x, y + br + h * 0.1f, p)
            }
        },
        RuleCard("금색 핀 · 은색 핀",
            "번갈아 상대 코드를 추리해요. 색과 자리가 모두 맞으면 금색 핀(●), 색만 맞으면 은색 핀(◎)!\n핀은 어느 칸인지 알려 주지 않아요. 금색 4개면 정답!") { c, w, h, p ->
            // 왼쪽 글자 열 · 가운데 말 홈 둘 · 오른쪽 핀 판 (폭에 맞춰 크기 정함)
            val labelX = w * 0.08f
            val step = minOf(h * 0.24f, w * 0.6f / 4.3f)
            val r = step / 2.4f
            val tx = w * 0.17f + step * 2.15f
            val y1 = h * 0.3f
            val y2 = h * 0.7f
            tray(c, tx, y1, r, step, 0, 1, 2, 3, p)
            tray(c, tx, y2, r, step, 0, 2, 4, 5, p)
            ColorArt.boldText(p, minOf(h * 0.085f, w * 0.06f), Theme.TEXT_SUB)
            c.drawText("정답", labelX, y1 + p.textSize * 0.36f, p)
            c.drawText("추리", labelX, y2 + p.textSize * 0.36f, p)
            // 같은 자리 같은 색(빨강) 금색 선, 자리만 다른 파랑 은색 선
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.022f
            p.color = ColorArt.GOLD
            c.drawLine(tx - step * 1.5f, y1 + r * 1.35f, tx - step * 1.5f, y2 - r * 1.35f, p)
            p.color = ColorArt.SILVER
            c.drawLine(tx + step * 0.5f, y1 + r * 1.35f, tx - step * 0.5f, y2 - r * 1.35f, p)
            p.strokeCap = Paint.Cap.BUTT
            // 핀 판 (오른쪽 남은 자리 가운데)
            val right = tx + step * 2f + r * 0.3f
            val px = (right + w) / 2f
            val ps = minOf(h * 0.12f, (w - right) * 0.3f)
            val pin = ps * 0.38f
            p.style = Paint.Style.FILL
            p.color = ColorArt.BOARD
            c.drawRoundRect(px - ps - pin * 1.4f, y2 - ps - pin * 1.4f, px + ps + pin * 1.4f, y2 + ps + pin * 1.4f, pin * 1.6f, pin * 1.6f, p)
            ColorArt.pins(c, px, y2, ps, pin, 1, 1, 4, p)
            ColorArt.boldText(p, minOf(h * 0.075f, w * 0.05f), Theme.GOLD)
            c.drawText("금1 은1", px, y2 - ps - pin * 1.4f - h * 0.05f, p)
        },
        RuleCard("적은 횟수로 맞히면 승리!",
            "한 사람 최대 ${GameConfig.MAX_TRIES}번. 두 사람은 항상 같은 횟수만큼 추리해요 — 먼저 맞혀도 상대가 같은 번째까지 해 보고, 같은 횟수면 무승부!\n끝나면 두 코드를 공개해요.") { c, w, h, p ->
            // 작은 기록판 둘: 왼쪽 3번째에 정답, 오른쪽 3번째 실패 (폭에 맞춰 말 크기 정함)
            val bw = w * 0.45f
            val r = minOf(h * 0.075f, bw / 15f)
            for (side in 0..1) {
                val bx = if (side == 0) w * 0.25f else w * 0.75f
                val top = h * 0.24f
                p.style = Paint.Style.FILL
                p.color = 0x55000000
                c.drawRoundRect(bx - bw / 2f, top + h * 0.02f, bx + bw / 2f, h * 0.96f, h * 0.06f, h * 0.06f, p)
                p.color = ColorArt.BOARD
                c.drawRoundRect(bx - bw / 2f, top, bx + bw / 2f, h * 0.94f, h * 0.06f, h * 0.06f, p)
                val left = bx - bw / 2f + r * 0.9f
                for (row in 0 until 3) {
                    val y = top + h * 0.12f + row * h * 0.23f
                    if (side == 0 && row == 2) {
                        p.color = 0x55FFC93C
                        c.drawRoundRect(bx - bw / 2f + h * 0.02f, y - h * 0.1f, bx + bw / 2f - h * 0.02f, y + h * 0.1f, h * 0.08f, h * 0.08f, p)
                    }
                    val line = R3_ROWS[side][row]
                    for (i in 0 until 4) ColorArt.peg(c, left + r * 1.2f + i * r * 2.4f, y, r, line[i], p)
                    ColorArt.pins(c, left + r * 11.8f, y, r * 1.35f, r * 0.5f, line[4], line[5], 4, p)
                }
            }
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            Ui.drawCrown(c, w * 0.25f, h * 0.19f, w * 0.1f, crownPath, p)
            ColorArt.boldText(p, minOf(h * 0.1f, w * 0.065f), Theme.GOLD)
            c.drawText("3번 만에 정답!", w * 0.72f, h * 0.17f, p)
        },
    )
}
