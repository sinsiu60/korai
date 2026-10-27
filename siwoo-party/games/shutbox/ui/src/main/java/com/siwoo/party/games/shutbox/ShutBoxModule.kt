package com.siwoo.party.games.shutbox

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
import com.siwoo.party.games.shutbox.ui.ShutBoxActivity

/** 숫자 닫기: 모음집 등록 정보 */
object ShutBoxModule : GameModule {
    override val id = "shutbox"
    override val name = "숫자 닫기"
    override val tagline = "주사위 합만큼 숫자판을 탁탁! 남은 숫자가 적을수록 승리"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "10분"
    override val color = 0xFF9C5A24.toInt()
    override val activityClass = ShutBoxActivity::class.java

    private val rect = RectF()
    private val path = Path()
    private const val WOOD = 0xFF8A5126.toInt()
    private const val WOOD_DARK = 0xFF5A3214.toInt()
    private const val WALL = 0xFF3B200C.toInt()
    private const val TILE = 0xFFFFF0CF.toInt()
    private const val TILE_EDGE = 0xFF8A5A2B.toInt()
    private const val BACK = 0xFF8B5427.toInt()
    private const val INK = 0xFF4A2810.toInt()
    private const val SEL_INK = 0xFFB3261E.toInt()
    private const val FELT = 0xFF1F7A53.toInt()
    private const val RED_PIP = 0xFFD32F2F.toInt()
    private const val DIE_INK = 0xFF1A2040.toInt()

    /** 눈 위치 (한 변 1 기준, x,y 쌍) — 그림용 (게임 화면 주사위는 공통 DiceSet) */
    private val PIPS = arrayOf(
        floatArrayOf(0.5f, 0.5f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.25f, 0.75f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.22f, 0.73f, 0.22f, 0.27f, 0.5f, 0.73f, 0.5f, 0.27f, 0.78f, 0.73f, 0.78f),
    )
    private val DIGITS = Array(10) { it.toString() }

    /** 상태: 0 = 서 있음, 1 = 고름(들림·금테), 2 = 닫힘(누움), 3 = 강조(금빛 테) */
    private const val UP = 0
    private const val SEL = 1
    private const val SHUT = 2
    private const val HINT = 3

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
        p.color = if (face == 1) RED_PIP else DIE_INK
        val pips = PIPS[face - 1]
        val r = s * (if (face == 1) 0.13f else 0.095f)
        var k = 0
        while (k < pips.size) {
            c.drawCircle(cx + (pips[k] - 0.5f) * s, cy + (pips[k + 1] - 0.5f) * s, r, p)
            k += 2
        }
        c.restore()
    }

    /** 숫자판 하나: x 왼쪽, hinge = 아래 경첩 y, 폭 w, 높이 h */
    private fun tile(c: Canvas, x: Float, hinge: Float, w: Float, h: Float, n: Int, state: Int, p: Paint) {
        val r = w * 0.16f
        p.style = Paint.Style.FILL
        if (state == SHUT) {
            rect.set(x, hinge, x + w, hinge + h * 0.3f)
            p.color = BACK
            c.drawRoundRect(rect, r * 0.6f, r * 0.6f, p)
            p.color = 0x66000000
            c.drawRect(x + w * 0.12f, hinge + h * 0.1f, x + w * 0.88f, hinge + h * 0.13f, p)
            return
        }
        val up = if (state == SEL) h * 0.1f else 0f
        rect.set(x, hinge - h - up, x + w, hinge - up)
        if (state == SEL) {
            p.color = 0x66FFC93C
            rect.inset(-w * 0.08f, -w * 0.08f)
            c.drawRoundRect(rect, r * 1.3f, r * 1.3f, p)
            rect.inset(w * 0.08f, w * 0.08f)
        }
        p.color = TILE
        c.drawRoundRect(rect, r, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * (if (state == UP) 0.04f else 0.09f)
        p.color = when (state) {
            SEL -> Theme.GOLD
            HINT -> 0xFFFFD866.toInt()
            else -> TILE_EDGE
        }
        c.drawRoundRect(rect, r, r, p)
        text(c, DIGITS[n], x + w / 2f, rect.centerY() + w * 0.24f, w * 0.66f, if (state == SEL) SEL_INK else INK, Paint.Align.CENTER, p)
    }

    /** 나무 상자 + 숫자판 줄: states[n-1] */
    private fun rack(c: Canvas, l: Float, t: Float, r: Float, b: Float, states: IntArray, p: Paint) {
        val w = r - l
        val fw = (b - t) * 0.08f
        p.style = Paint.Style.FILL
        p.color = WOOD
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, fw * 1.5f, fw * 1.5f, p)
        p.color = WALL
        rect.set(l + fw, t + fw, r - fw, b - fw)
        c.drawRect(rect, p)
        val innerW = w - fw * 2f
        val gap = innerW * 0.012f
        val pad = innerW * 0.02f
        val tw = (innerW - pad * 2f - gap * (states.size - 1)) / states.size
        val th = (b - t - fw * 2f) * 0.68f
        val hinge = t + fw + (b - t - fw * 2f) * 0.76f
        p.color = 0x88000000.toInt()
        c.drawRect(l + fw, hinge - fw * 0.15f, r - fw, hinge + fw * 0.2f, p)
        for (k in states.indices) tile(c, l + fw + pad + k * (tw + gap), hinge, tw, th, k + 1, states[k], p)
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

    private val ICON_STATES = intArrayOf(UP, SHUT, UP, SHUT, SHUT)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        // 작은 상자 (5칸: 1·3·5 서 있고 2·4 닫힘) + 앞에 주사위 두 개
        val w = size * 0.86f
        val h = size * 0.5f
        val top = cy - size * 0.42f
        rack(canvas, cx - w / 2f, top, cx + w / 2f, top + h, ICON_STATES, paint)
        val s = size * 0.27f
        die(canvas, cx - s * 0.62f, cy + size * 0.24f, s, 4, -12f, paint)
        die(canvas, cx + s * 0.66f, cy + size * 0.26f, s * 0.95f, 5, 10f, paint)
    }

    private val R1_STATES = intArrayOf(UP, UP, SEL, UP, UP, SEL, UP, UP, UP)
    private val R2_STATES = intArrayOf(UP, UP, SHUT, SHUT, SHUT, UP, SHUT, SHUT, SHUT)
    private val R3_NAMES = arrayOf("가람", "나래", "다온")
    private val R3_SCORES = arrayOf(intArrayOf(9, 0, 4), intArrayOf(19, 12, 7), intArrayOf(0, 15, 22))
    private val R3_TEXT = Array(3) { i -> Array(3) { k -> R3_SCORES[i][k].toString() } }
    private val R3_TOTALS = arrayOf("13", "38", "37")
    private val R3_HEADS = arrayOf("1R", "2R", "3R", "합")

    /** 펠트 판 + 주사위 두 개 + "선택 a / 주사위 b" */
    private fun trayWithReadout(c: Canvas, w: Float, h: Float, top: Float, f1: Int, f2: Int, sel: String, total: String, selColor: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = FELT
        rect.set(w * 0.06f, top, w * 0.94f, h * 0.96f)
        c.drawRoundRect(rect, h * 0.05f, h * 0.05f, p)
        val s = minOf((h * 0.96f - top) * 0.62f, w * 0.13f)
        val dy = (top + h * 0.96f) / 2f
        die(c, w * 0.2f, dy, s, f1, -8f, p)
        die(c, w * 0.2f + s * 1.35f, dy, s, f2, 7f, p)
        p.color = 0xCC0B1340.toInt()
        rect.set(w * 0.52f, top + h * 0.04f, w * 0.9f, h * 0.92f)
        c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
        val ts = minOf(rect.height() * 0.24f, w * 0.05f)
        text(c, "선택", w * 0.6f, rect.top + rect.height() * 0.36f, ts, Theme.TEXT_SUB, Paint.Align.CENTER, p)
        text(c, "주사위", w * 0.82f, rect.top + rect.height() * 0.36f, ts, Theme.TEXT_SUB, Paint.Align.CENTER, p)
        text(c, sel, w * 0.6f, rect.top + rect.height() * 0.84f, ts * 1.9f, selColor, Paint.Align.CENTER, p)
        text(c, "/", w * 0.71f, rect.top + rect.height() * 0.84f, ts * 1.6f, Theme.TEXT_SUB, Paint.Align.CENTER, p)
        text(c, total, w * 0.82f, rect.top + rect.height() * 0.84f, ts * 1.9f, Theme.TEXT, Paint.Align.CENTER, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("주사위 합만큼 숫자 닫기", "주사위 2개를 굴리고, 열린 숫자를 골라 합이 주사위 합과 같아지면 [닫기]! 숫자판이 탁 넘어가요.\n닫으면 다시 굴려요. 합을 만들 수 있는 숫자는 금빛으로 살짝 빛나요.") { c, w, h, p ->
            rack(c, w * 0.04f, h * 0.04f, w * 0.96f, h * 0.5f, R1_STATES, p)
            trayWithReadout(c, w, h, h * 0.56f, 4, 5, "9", "9", Theme.GOLD, p)
        },
        RuleCard("못 닫으면 차례 끝", "주사위 합을 만들 조합이 없으면 \"더 못 닫아요\" — 차례가 끝나고 남은 숫자 합이 이번 라운드 점수예요.\n남은 합이 6 이하면 주사위 1개만 굴려도 돼요.") { c, w, h, p ->
            rack(c, w * 0.04f, h * 0.02f, w * 0.96f, h * 0.42f, R2_STATES, p)
            trayWithReadout(c, w, h, h * 0.58f, 6, 6, "–", "12", Theme.TEXT_SUB, p)
            // 빨간 X 와 "남은 합 9점"
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.03f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.RED
            val x = w * 0.2f + minOf(h * 0.4f * 0.62f, w * 0.13f) * 0.68f
            val y = h * 0.77f
            val r = h * 0.1f
            c.drawLine(x - r, y - r, x + r, y + r, p)
            c.drawLine(x + r, y - r, x - r, y + r, p)
            p.strokeCap = Paint.Cap.BUTT
            text(c, "남은 합 1+2+6 = 9점", w / 2f, h * 0.53f, h * 0.08f, Theme.GOLD_LIGHT, Paint.Align.CENTER, p)
        },
        RuleCard("3라운드 · 합이 낮을수록 승리", "라운드마다 모두 한 번씩, 새 상자로 해요. 먼저 하는 사람은 라운드마다 바뀌어요.\n숫자를 모두 닫으면 \"완벽!\" 0점! 3라운드 합이 가장 낮은 사람이 1등.") { c, w, h, p ->
            val l = w * 0.08f
            val nameW = w * 0.26f
            val colW = w * 0.15f
            val top = h * 0.06f
            val rh = h * 0.21f
            p.style = Paint.Style.FILL
            p.color = 0xFF1A2766.toInt()
            rect.set(l, top, l + nameW + colW * 4f, top + rh * 0.8f + rh * 3f)
            c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
            for (k in 0 until 4) text(c, R3_HEADS[k], l + nameW + colW * k + colW / 2f, top + rh * 0.56f, rh * 0.36f, Theme.TEXT_SUB, Paint.Align.CENTER, p)
            for (i in 0 until 3) {
                val y = top + rh * 0.8f + rh * i
                if (i == 0) {
                    p.color = 0x33FFC93C
                    rect.set(l + h * 0.01f, y + rh * 0.06f, l + nameW + colW * 4f - h * 0.01f, y + rh * 0.94f)
                    c.drawRoundRect(rect, h * 0.03f, h * 0.03f, p)
                    Ui.drawCrown(c, l + nameW * 0.16f, y + rh * 0.42f, rh * 0.42f, path, p)
                }
                p.color = Theme.PLAYER_COLORS[i]
                c.drawCircle(l + nameW * 0.16f, y + rh * 0.6f, rh * 0.17f, p)
                text(c, R3_NAMES[i], l + nameW * 0.32f, y + rh * 0.68f, rh * 0.36f, Theme.TEXT, Paint.Align.LEFT, p)
                for (k in 0 until 3) {
                    val v = R3_SCORES[i][k]
                    text(c, R3_TEXT[i][k], l + nameW + colW * k + colW / 2f, y + rh * 0.66f, rh * 0.38f, if (v == 0) Theme.GOLD else Theme.TEXT, Paint.Align.CENTER, p)
                }
                text(c, R3_TOTALS[i], l + nameW + colW * 3.5f, y + rh * 0.66f, rh * 0.42f, Theme.GOLD, Paint.Align.CENTER, p)
            }
            text(c, "완벽! = 0점", w * 0.5f, h * 0.97f, h * 0.08f, Theme.GOLD_LIGHT, Paint.Align.CENTER, p)
        },
    )
}
