package com.siwoo.party.games.horserace

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.horserace.ui.HorsePainter
import com.siwoo.party.games.horserace.ui.HorseRaceActivity

/** 주사위 경마: 모음집 등록 정보 */
object HorseRaceModule : GameModule {
    override val id = "horserace"
    override val name = "주사위 경마"
    override val tagline = "말 6마리에 칩을 걸고, 주사위가 달리는 말을 정한다!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "10분"
    override val color = 0xFF2E8B57.toInt()
    override val activityClass = HorseRaceActivity::class.java

    private val horse = HorsePainter()
    private val rect = RectF()
    private const val INK = 0xFF1A2040.toInt()
    private const val TURF = 0xFF2F8A3E.toInt()
    private const val TURF2 = 0xFF2A7D38.toInt()
    private const val COIN = 0xFFFFC93C.toInt()
    private const val COIN_EDGE = 0xFFB8860B.toInt()
    private const val SILVER = 0xFFCFD8DC.toInt()

    private val HEADS = Array(GameConfig.HORSES) { (it + 1).toString() + "번 말" }

    private val PIPS = arrayOf(
        floatArrayOf(0.5f, 0.5f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.25f, 0.75f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.22f, 0.73f, 0.22f, 0.27f, 0.5f, 0.73f, 0.5f, 0.27f, 0.78f, 0.73f, 0.78f),
    )

    /** 규칙 그림·아이콘용 주사위 */
    private fun die(c: Canvas, cx: Float, cy: Float, s: Float, face: Int, deg: Float, p: Paint) {
        c.save()
        c.rotate(deg, cx, cy)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(cx - s / 2 + s * 0.05f, cy - s / 2 + s * 0.08f, cx + s / 2 + s * 0.05f, cy + s / 2 + s * 0.08f)
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p)
        p.color = 0xFFFFFFFF.toInt()
        rect.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.05f
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p)
        p.style = Paint.Style.FILL
        p.color = if (face == 1) 0xFFD32F2F.toInt() else INK
        val pips = PIPS[face - 1]
        val r = s * (if (face == 1) 0.13f else 0.095f)
        var k = 0
        while (k < pips.size) {
            c.drawCircle(cx + (pips[k] - 0.5f) * s, cy + (pips[k + 1] - 0.5f) * s, r, p)
            k += 2
        }
        c.restore()
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, p: Paint) {
        p.style = Paint.Style.FILL
        p.textSize = size
        p.color = color
        p.textAlign = align
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** 칩 더미 (n 개) */
    private fun chips(c: Canvas, cx: Float, bottom: Float, r: Float, n: Int, p: Paint) {
        p.style = Paint.Style.FILL
        for (k in 0 until n) {
            val y = bottom - k * r * 0.42f
            p.color = COIN_EDGE
            rect.set(cx - r, y - r * 0.38f, cx + r, y + r * 0.1f)
            c.drawOval(rect, p)
            p.color = COIN
            rect.set(cx - r, y - r * 0.5f, cx + r, y - r * 0.02f)
            c.drawOval(rect, p)
        }
    }

    /** 잔디 레인 (y 위, 높이 h) */
    private fun lane(c: Canvas, l: Float, t: Float, r: Float, h: Float, alt: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = if (alt) TURF2 else TURF
        c.drawRect(l, t, r, t + h, p)
    }

    /** 체크무늬 결승선 */
    private fun finish(c: Canvas, x: Float, t: Float, b: Float, sq: Float, p: Paint) {
        p.style = Paint.Style.FILL
        var y = t
        var k = 0
        while (y < b) {
            val y2 = minOf(y + sq, b)
            p.color = if (k % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
            c.drawRect(x - sq, y, x, y2, p)
            p.color = if (k % 2 == 0) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
            c.drawRect(x, y, x + sq, y2, p)
            y += sq
            k++
        }
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // 달리는 3번(빨강) 말 + 오른쪽 위 주사위
        horse.draw(canvas, cx - size * 0.06f, cy + size * 0.08f, size * 0.68f, 2, 0.9f, true)
        die(canvas, cx + size * 0.3f, cy - size * 0.28f, size * 0.24f, 3, 14f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("말을 골라 칩을 걸어요", "레이스마다 차례대로 말 카드를 누르고 [+10][+50]으로 금액을 정해 [걸기]!\n여러 마리에 나눠 걸어도 돼요. 시작 칩 100, 한 레이스에 최소 10칩. 다 걸었으면 [베팅 끝].") { c, w, h, p ->
            val cw = minOf(w * 0.26f, h * 0.62f)
            val gap = cw * 0.12f
            val left = w / 2f - cw * 1.5f - gap
            for (k in 0 until 3) {
                val l = left + k * (cw + gap)
                val sel = k == 1
                val top = h * 0.08f - if (sel) h * 0.04f else 0f
                p.style = Paint.Style.FILL
                p.color = if (sel) Theme.CARD_LIGHT else Theme.CARD
                rect.set(l, top, l + cw, top + h * 0.7f)
                c.drawRoundRect(rect, cw * 0.1f, cw * 0.1f, p)
                p.color = HorsePainter.SILK[k + 2]
                rect.set(l, top, l + cw, top + h * 0.13f)
                c.drawRoundRect(rect, cw * 0.1f, cw * 0.1f, p)
                text(c, HEADS[k + 2], l + cw / 2f, top + h * 0.1f, h * 0.075f, HorsePainter.SILK_TEXT[k + 2], Paint.Align.CENTER, p)
                horse.draw(c, l + cw / 2f, top + h * 0.36f, cw * 0.62f, k + 2, 0f, false)
                if (sel) {
                    chips(c, l + cw / 2f, top + h * 0.64f, cw * 0.16f, 3, p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = h * 0.015f
                    p.color = Theme.GOLD
                    rect.set(l, top, l + cw, top + h * 0.7f)
                    c.drawRoundRect(rect, cw * 0.1f, cw * 0.1f, p)
                }
            }
            // 금액 버튼
            p.style = Paint.Style.FILL
            val by = h * 0.82f
            val bw = w * 0.17f
            for (k in 0 until 2) {
                val bl = w * 0.08f + k * (bw + w * 0.03f)
                p.color = 0xFF7E57C2.toInt()
                rect.set(bl, by, bl + bw, by + h * 0.14f)
                c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
                text(c, if (k == 0) "+10" else "+50", bl + bw / 2f, by + h * 0.105f, h * 0.08f, Theme.TEXT, Paint.Align.CENTER, p)
            }
            val gl = w * 0.5f
            p.color = 0xFFC77800.toInt()
            rect.set(gl, by, w * 0.92f, by + h * 0.14f)
            c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
            text(c, "4번에 30 걸기", rect.centerX(), by + h * 0.1f, minOf(h * 0.075f, w * 0.05f), Theme.TEXT, Paint.Align.CENTER, p)
        },
        RuleCard("주사위가 말을 달려요", "주사위가 저절로 굴러 나온 번호의 말이 한 칸 전진!\n10칸에 먼저 닿은 말이 1착, 두 번째로 닿은 말이 2착 (2착이 들어오면 레이스 끝).\n[빨리 보기]로 빠르게 볼 수 있어요.") { c, w, h, p ->
            val l = w * 0.04f
            val r = w * 0.72f
            val lh = h * 0.27f
            val t = h * 0.1f
            for (k in 0 until 3) lane(c, l, t + k * lh, r, lh, k % 2 == 1, p)
            val fx = r - w * 0.05f
            finish(c, fx, t, t + lh * 3f, h * 0.035f, p)
            val s = lh * 0.95f
            horse.draw(c, fx - s * 0.5f, t + lh * 0.55f, s, 2, 0f, false)
            horse.draw(c, l + (fx - l) * 0.62f, t + lh * 1.55f, s, 3, 1.2f, true)
            horse.draw(c, l + (fx - l) * 0.38f, t + lh * 2.55f, s, 4, 0f, false)
            // 1착 표시
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            rect.set(fx + w * 0.03f, t + lh * 0.3f, fx + w * 0.13f, t + lh * 0.75f)
            c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
            text(c, "1착", rect.centerX(), rect.centerY() + h * 0.03f, h * 0.08f, INK, Paint.Align.CENTER, p)
            // 주사위 4 → 4번 말 전진 화살표
            die(c, w * 0.88f, h * 0.55f, minOf(w * 0.14f, h * 0.3f), 4, -10f, p)
            text(c, "4번 전진!", w * 0.86f, h * 0.88f, minOf(h * 0.07f, w * 0.045f), Theme.GOLD, Paint.Align.CENTER, p)
        },
        RuleCard("배당 · 5레이스 칩 승부", "1착 말에 건 칩은 ×3, 2착 말은 ×1.5로 돌려받고 나머지는 잃어요.\n예) 30칩 → 1착 90칩 · 2착 45칩\n칩이 10개 미만이면 구경만 해요. 5레이스 뒤 칩이 많은 순서로 순위!") { c, w, h, p ->
            val rowH = h * 0.36f
            for (k in 0 until 2) {
                val cy = h * 0.24f + k * rowH
                p.style = Paint.Style.FILL
                p.color = if (k == 0) Theme.GOLD else SILVER
                rect.set(w * 0.06f, cy - h * 0.1f, w * 0.24f, cy + h * 0.1f)
                c.drawRoundRect(rect, h * 0.1f, h * 0.1f, p)
                text(c, if (k == 0) "1착" else "2착", rect.centerX(), cy + h * 0.04f, h * 0.11f, INK, Paint.Align.CENTER, p)
                chips(c, w * 0.33f, cy + h * 0.08f, h * 0.07f, 3, p)
                text(c, "→", w * 0.43f, cy + h * 0.04f, h * 0.12f, Theme.TEXT, Paint.Align.CENTER, p)
                chips(c, w * 0.53f, cy + h * 0.08f, h * 0.07f, if (k == 0) 9 else 4, p)
                if (k == 1) chips(c, w * 0.53f + h * 0.16f, cy + h * 0.08f, h * 0.07f, 1, p)
                text(c, if (k == 0) "×3" else "×1.5", w * 0.96f, cy + h * 0.05f, h * 0.14f, if (k == 0) Theme.GOLD else SILVER, Paint.Align.RIGHT, p)
            }
            text(c, "5레이스 뒤 칩 많은 순!", w / 2f, h * 0.92f, h * 0.085f, Theme.TEXT, Paint.Align.CENTER, p)
        },
    )
}
