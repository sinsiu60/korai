package com.siwoo.party.games.yacht

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.yacht.ui.YachtActivity

/** 야추 주사위: 모음집 등록 정보 */
object YachtModule : GameModule {
    override val id = "yacht"
    override val name = "야추 주사위"
    override val tagline = "주사위 5개, 3번의 기회! 족보를 모아 점수표를 채워라"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val minutes = "10분"
    override val color = 0xFFC77800.toInt()
    override val activityClass = YachtActivity::class.java

    private val rect = RectF()
    private const val INK = 0xFF1A2040.toInt()
    private const val RED_PIP = 0xFFD32F2F.toInt()
    private const val FELT = 0xFF1F7A53.toInt()

    /** 눈 위치 (한 변 1 기준, x,y 쌍) — 그림용 (게임 화면 주사위는 공통 DiceSet) */
    private val PIPS = arrayOf(
        floatArrayOf(0.5f, 0.5f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.25f, 0.75f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.22f, 0.73f, 0.22f, 0.27f, 0.5f, 0.73f, 0.5f, 0.27f, 0.78f, 0.73f, 0.78f),
    )

    /** 규칙 그림·아이콘용 주사위 한 개 (held = 금색 굵은 테두리) */
    private fun die(c: Canvas, cx: Float, cy: Float, s: Float, face: Int, deg: Float, held: Boolean, p: Paint) {
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
        p.strokeWidth = s * (if (held) 0.1f else 0.05f)
        p.color = if (held) Theme.GOLD else Theme.GOLD_DARK
        c.drawRoundRect(rect, s * 0.2f, s * 0.2f, p)
        p.style = Paint.Style.FILL
        p.color = if (face == 1) RED_PIP else INK
        val pips = PIPS[face - 1]
        val r = s * (if (face == 1) 0.13f else 0.095f)
        var k = 0
        while (k < pips.size) {
            c.drawCircle(cx + (pips[k] - 0.5f) * s, cy + (pips[k + 1] - 0.5f) * s, r, p)
            k += 2
        }
        c.restore()
    }

    /** 주사위 한 줄 (held 가 true 면 금색 + 살짝 아래) */
    private fun row(c: Canvas, cx: Float, cy: Float, s: Float, faces: IntArray, held: BooleanArray?, p: Paint) {
        val gap = s * 0.25f
        val total = faces.size * s + (faces.size - 1) * gap
        for (i in faces.indices) {
            val h = held != null && held[i]
            die(c, cx - total / 2 + s / 2 + i * (s + gap), cy + if (h) s * 0.14f else 0f, s, faces[i], 0f, h, p)
        }
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

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size * 0.4f
        // 뒤: 고정된(금테) 6, 앞: 기울어진 5
        die(canvas, cx + s * 0.42f, cy - s * 0.3f, s * 0.92f, 6, 14f, true, paint)
        die(canvas, cx - s * 0.36f, cy + s * 0.28f, s, 5, -12f, false, paint)
        // 반짝이
        paint.style = Paint.Style.FILL
        paint.color = Theme.GOLD_LIGHT
        val tw = size * 0.06f
        val tx = cx + s * 0.95f
        val ty = cy + s * 0.62f
        canvas.drawOval(tx - tw * 0.22f, ty - tw, tx + tw * 0.22f, ty + tw, paint)
        canvas.drawOval(tx - tw, ty - tw * 0.22f, tx + tw, ty + tw * 0.22f, paint)
    }

    private val RULE1_FACES = intArrayOf(5, 2, 5, 6, 5)
    private val RULE1_HELD = booleanArrayOf(true, false, true, false, true)
    private val EX_FULL = intArrayOf(3, 3, 3, 5, 5)
    private val EX_STRAIGHT = intArrayOf(1, 2, 3, 4, 6)
    private val EX_YACHT = intArrayOf(6, 6, 6, 6, 6)
    private val SHEET_ROWS = arrayOf("에이스", "듀얼", "트리플", "초이스", "풀하우스")

    override val rules: List<RuleCard> = listOf(
        RuleCard("주사위 5개, 차례에 3번까지", "[굴리기]로 주사위 5개를 굴려요. 한 차례에 최대 3번!\n굴린 뒤 주사위를 누르면 고정(금색 테두리), 다시 누르면 해제. 고정 안 한 주사위만 다시 굴러요.") { c, w, h, p ->
            // 초록 판 + 주사위 (5 세 개 고정)
            p.style = Paint.Style.FILL
            p.color = FELT
            rect.set(w * 0.06f, h * 0.08f, w * 0.94f, h * 0.66f)
            c.drawRoundRect(rect, h * 0.06f, h * 0.06f, p)
            row(c, w / 2f, h * 0.33f, minOf(h * 0.24f, w * 0.135f), RULE1_FACES, RULE1_HELD, p)
            // 굴리기 버튼
            p.color = Theme.shade(0xFF7E57C2.toInt(), 0.55f)
            rect.set(w * 0.3f, h * 0.74f, w * 0.7f, h * 0.94f)
            c.drawRoundRect(rect, h * 0.06f, h * 0.06f, p)
            p.color = 0xFF7E57C2.toInt()
            rect.set(w * 0.3f, h * 0.72f, w * 0.7f, h * 0.91f)
            c.drawRoundRect(rect, h * 0.06f, h * 0.06f, p)
            text(c, "굴리기 2/3", w / 2f, h * 0.86f, h * 0.11f, Theme.TEXT, Paint.Align.CENTER, p)
        },
        RuleCard("점수표 빈칸에 기록", "굴린 뒤 내 열의 빈칸을 누르면 바로 기록되고 다음 사람 차례예요. 흐린 숫자 = 들어갈 점수 (0점이면 한 번 더 물어봐요).\n상단(1~6 눈의 합)이 63점 이상이면 보너스 +35!") { c, w, h, p ->
            // 작은 점수표: 칸 이름 + 두 열 (오른쪽 열이 지금 차례)
            val l = w * 0.12f
            val top = h * 0.06f
            val rh = h * 0.13f
            val catW = w * 0.3f
            val colW = w * 0.22f
            p.style = Paint.Style.FILL
            p.color = 0xFF1A2766.toInt()
            rect.set(l, top, l + catW + colW * 2f, top + rh * SHEET_ROWS.size)
            c.drawRoundRect(rect, h * 0.04f, h * 0.04f, p)
            p.color = Theme.PLAYER_COLORS[1]
            p.alpha = 0x50
            c.drawRect(l + catW + colW, top, l + catW + colW * 2f, top + rh * SHEET_ROWS.size, p)
            p.alpha = 255
            for (k in SHEET_ROWS.indices) {
                val y = top + rh * k + rh * 0.68f
                text(c, SHEET_ROWS[k], l + w * 0.02f, y, rh * 0.48f, Theme.TEXT, Paint.Align.LEFT, p)
            }
            val cx1 = l + catW + colW / 2f
            val cx2 = l + catW + colW * 1.5f
            text(c, "3", cx1, top + rh * 0.68f, rh * 0.5f, Theme.TEXT, Paint.Align.CENTER, p)
            text(c, "17", cx1, top + rh * 3.68f, rh * 0.5f, Theme.TEXT, Paint.Align.CENTER, p)
            text(c, "2", cx2, top + rh * 0.68f, rh * 0.5f, Theme.TEXT, Paint.Align.CENTER, p)
            // 미리보기 (흐림) + 누르는 칸
            text(c, "9", cx2, top + rh * 2.68f, rh * 0.5f, 0x99FFE08A.toInt(), Paint.Align.CENTER, p)
            text(c, "19", cx2, top + rh * 3.68f, rh * 0.5f, 0x99FFE08A.toInt(), Paint.Align.CENTER, p)
            text(c, "19", cx2, top + rh * 4.68f, rh * 0.5f, 0x99FFE08A.toInt(), Paint.Align.CENTER, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = Theme.TEXT
            c.drawCircle(cx2 + colW * 0.34f, top + rh * 4.6f, rh * 0.22f, p)
            c.drawCircle(cx2 + colW * 0.34f, top + rh * 4.6f, rh * 0.38f, p)
            p.style = Paint.Style.FILL
            // 지금 주사위 (풀하우스)
            row(c, w / 2f, h * 0.84f, h * 0.12f, EX_FULL, null, p)
        },
        RuleCard("족보 · 12라운드 총점 승부", "초이스: 5개 합 · 포카드: 같은 눈 4개+ → 5개 합 · 풀하우스: 3개+2개 → 5개 합 (5개 같음은 0점)\nS. 스트레이트(4연속) 15 · L. 스트레이트(5연속) 30 · 야추(5개 같음) 50\n12칸을 다 채우면 총점 순위!") { c, w, h, p ->
            // 왼쪽: 주사위 예, 오른쪽: 칸 이름 + 점수
            val s = minOf(h * 0.13f, w * 0.068f)
            val x = w * 0.05f + s * 3f
            val tx = x + s * 3.3f
            val ts = minOf(h * 0.085f, w * 0.055f)
            row(c, x, h * 0.2f, s, EX_FULL, null, p)
            text(c, "풀하우스 19", tx, h * 0.2f + ts * 0.36f, ts, Theme.TEXT, Paint.Align.LEFT, p)
            row(c, x, h * 0.5f, s, EX_STRAIGHT, null, p)
            text(c, "S. 스트레이트 15", tx, h * 0.5f + ts * 0.36f, ts, Theme.TEXT, Paint.Align.LEFT, p)
            row(c, x, h * 0.8f, s, EX_YACHT, null, p)
            text(c, "야추! 50", tx, h * 0.8f + ts * 0.4f, ts * 1.2f, Theme.GOLD, Paint.Align.LEFT, p)
        },
    )
}
