package com.siwoo.party.games.codetile

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.codetile.ui.CodeTileActivity
import com.siwoo.party.games.codetile.ui.TileArt

/** 숨은 숫자 타일: 모음집 등록 정보 (2~4인, 차례대로, 기기를 넘기며 내 타일은 몰래) */
object CodeTileModule : GameModule {
    override val id = "codetile"
    override val name = "숨은 숫자 타일"
    override val tagline = "색만 보이는 상대 타일, 숫자를 맞혀라!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.CARD_BRAIN
    override val minutes = "15분"
    override val color = 0xFF2F8F86.toInt()
    override val activityClass = CodeTileActivity::class.java

    private const val ASPECT = TileArt.ASPECT

    /** 타일 줄 하나 그리기 (numbers: -1 = 숨은 면, blacks: 검정 여부) */
    private fun row(c: Canvas, l: Float, t: Float, th: Float, numbers: IntArray, blacks: BooleanArray, lift: Int = -1) {
        val tw = th * ASPECT
        val g = tw * 0.16f
        for (k in numbers.indices) {
            val up = if (k == lift) th * 0.14f else 0f
            TileArt.draw(c, l + k * (tw + g), t - up, tw, th, blacks[k], numbers[k])
            if (k == lift) TileArt.outline(c, l + k * (tw + g), t - up, tw, th, Theme.GOLD, th * 0.05f)
        }
    }

    private fun rowWidth(th: Float, n: Int): Float {
        val tw = th * ASPECT
        return n * tw + (n - 1) * tw * 0.16f
    }

    // 규칙 1: 내 줄(숫자 보임) · 상대 줄(색만)
    private val R1_NUMS = intArrayOf(2, 5, 5, 9)
    private val R1_BLACK = booleanArrayOf(false, true, false, true)
    private val R1_BACK = intArrayOf(-1, -1, -1, -1)

    // 규칙 2: 상대 줄에서 하나 골라 추리
    private val R2_NUMS = intArrayOf(-1, 4, -1, -1)
    private val R2_BLACK = booleanArrayOf(false, true, true, false)

    // 규칙 3: 오답 → 뽑은 타일(7)이 공개된 채 내 줄로 / 모두 공개되면 탈락
    private val R3_MINE = intArrayOf(-1, -1, 7, -1)
    private val R3_MINE_B = booleanArrayOf(true, false, false, true)
    private val R3_OUT = intArrayOf(1, 6, 10)
    private val R3_OUT_B = booleanArrayOf(false, true, false)

    private val ICON_B = booleanArrayOf(true, false)
    private val ICON_N = intArrayOf(7, -1)

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF1D4F5A.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        paint.style = Paint.Style.FILL
        // 공개된 검정 7 + 들려 있는 숨은 흰 타일 + 물음표 말풍선
        val th = r * 1.0f
        val w = rowWidth(th, 2)
        row(canvas, cx - w / 2f - r * 0.08f, cy - th * 0.42f, th, ICON_N, ICON_B, lift = 1)
        TileArt.bubble(canvas, paint, cx + r * 0.5f, cy - r * 0.58f, r * 0.46f, r * 0.36f, Theme.GOLD)
        TileArt.shout(canvas, paint, "?", cx + r * 0.5f, cy - r * 0.46f, r * 0.34f, Theme.TEXT)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("내 타일은 몰래, 줄 세워서", "0~11 검정·흰색 타일 24장,\n2~3인은 4장 · 4인은 3장씩!\n내 줄은 작은 수부터 정렬,\n같은 수는 검정이 왼쪽.\n남들 눈엔 색만 보여요.") { c, w, h, p ->
            val th = h * 0.34f
            val rw = rowWidth(th, 4)
            val l = w * 0.56f - rw / 2f
            // 위: 내가 보는 줄 (숫자) / 아래: 남들이 보는 같은 줄 (색만)
            TileArt.shout(c, p, "나", l - h * 0.15f, h * 0.08f + th * 0.6f, h * 0.13f, Theme.GOLD)
            row(c, l, h * 0.08f, th, R1_NUMS, R1_BLACK)
            TileArt.shout(c, p, "남", l - h * 0.15f, h * 0.56f + th * 0.6f, h * 0.13f, Theme.TEXT_SUB)
            row(c, l, h * 0.56f, th, R1_BACK, R1_BLACK)
        },
        RuleCard("뽑고, 하나 골라 추리!", "내 차례엔 더미에서 1장 뽑고,\n상대의 숨은 타일을 눌러\n0~11 숫자를 맞혀요.\n맞히면 [계속 추리] / [멈추기]\n멈추면 뽑은 타일은 숨긴 채!") { c, w, h, p ->
            val th = h * 0.36f
            val rw = rowWidth(th, 4)
            val l = w * 0.98f - rw
            val t = h * 0.48f
            row(c, l, t, th, R2_NUMS, R2_BLACK, lift = 2)
            val tw = th * ASPECT
            val sx = l + 2 * (tw + tw * 0.16f) + tw / 2f
            TileArt.finger(c, p, sx + tw * 0.25f, t + th * 0.6f, h * 0.055f)
            TileArt.bubble(c, p, sx + tw * 0.6f, h * 0.18f, h * 0.36f, h * 0.22f, Theme.GOLD)
            TileArt.shout(c, p, "8?", sx + tw * 0.6f, h * 0.25f, h * 0.17f, Theme.TEXT)
            // 왼쪽: 뽑은 타일
            val dh = th * 0.8f
            val dl = maxOf(w * 0.02f, l - dh * ASPECT - h * 0.07f)
            TileArt.draw(c, dl, t + th - dh, dh * ASPECT, dh, false, -1)
            TileArt.shout(c, p, "뽑음", dl + dh * ASPECT / 2f, t + th - dh - h * 0.05f, h * 0.085f, Theme.GOLD)
        },
        RuleCard("틀리면 공개, 다 열리면 탈락", "틀리면 뽑은 타일을 공개한 채\n내 줄에 넣어요. (더미가 비면\n내 숨은 타일 하나를 골라 공개)\n숨은 타일이 없으면 탈락!\n남은 한 사람이 1위예요.") { c, w, h, p ->
            val th = h * 0.36f
            val l = w * 0.05f
            val t = h * 0.2f
            row(c, l, t, th, R3_MINE, R3_MINE_B)
            val tw = th * ASPECT
            val x7 = l + 2 * (tw + tw * 0.16f) + tw / 2f
            TileArt.shout(c, p, "땡!", x7, h * 0.15f, h * 0.12f, Theme.RED)
            // 오른쪽 아래: 모두 공개된 줄 + 탈락 도장
            val th2 = h * 0.3f
            val l2 = w * 0.96f - rowWidth(th2, 3)
            val t2 = h * 0.62f
            row(c, l2, t2, th2, R3_OUT, R3_OUT_B)
            val sx = l2 + rowWidth(th2, 3) / 2f
            val sy = t2 + th2 * 0.5f
            c.save()
            c.rotate(-12f, sx, sy)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.025f
            p.color = Theme.RED
            c.drawRoundRect(sx - h * 0.2f, sy - h * 0.1f, sx + h * 0.2f, sy + h * 0.1f, h * 0.03f, h * 0.03f, p)
            p.style = Paint.Style.FILL
            TileArt.shout(c, p, "탈락", sx, sy + h * 0.045f, h * 0.12f, Theme.RED)
            c.restore()
            TileArt.shout(c, p, "남은 1명 = 1위", minOf(w * 0.24f, l2 * 0.5f), h * 0.84f, h * 0.075f, Theme.GOLD)
        },
    )
}
