package com.siwoo.party.games.marble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.marble.ui.GameStorage
import com.siwoo.party.games.marble.ui.MarbleStartActivity

/** 시우 마블: 모음집 등록 정보 (카드·아이콘·규칙 카드) */
object MarbleModule : GameModule {
    const val ID = "marble"

    override val id = ID
    override val name = "시우 마블"
    override val tagline = "주사위를 굴려 땅을 사고 건물을 올려라!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "20~40분"
    override val color = 0xFF2E7D32.toInt()
    override val activityClass = MarbleStartActivity::class.java

    // 그림용 (그릴 때마다 만들지 않게 미리)
    private val path = Path()
    private val HEIGHTS = floatArrayOf(0.2f, 0.32f, 0.46f)
    private val WHEEL = intArrayOf(0xFF27AE60.toInt(), 0xFFF2B705.toInt(), 0xFF27AE60.toInt(), 0xFFE53935.toInt(),
        0xFF27AE60.toInt(), 0xFFF2B705.toInt(), 0xFF27AE60.toInt(), 0xFFF2B705.toInt())

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size * 0.5f
        // 집 (뒤)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFF8E7.toInt()
        canvas.drawRect(cx - s * 0.05f, cy - s * 0.55f, cx + s * 0.8f, cy + s * 0.25f, paint)
        path.reset()
        path.moveTo(cx - s * 0.18f, cy - s * 0.5f)
        path.lineTo(cx + s * 0.375f, cy - s * 0.95f)
        path.lineTo(cx + s * 0.93f, cy - s * 0.5f)
        path.close()
        paint.color = Theme.RED
        canvas.drawPath(path, paint)
        paint.color = 0xFF5D4037.toInt()
        canvas.drawRect(cx + s * 0.28f, cy - s * 0.18f, cx + s * 0.48f, cy + s * 0.25f, paint)
        // 주사위 (앞)
        val d = s * 0.95f
        val l = cx - s * 0.85f
        val t = cy - s * 0.05f
        paint.color = 0x55000000
        canvas.drawRoundRect(l + s * 0.06f, t + s * 0.08f, l + d + s * 0.06f, t + d + s * 0.08f, d * 0.2f, d * 0.2f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(l, t, l + d, t + d, d * 0.2f, d * 0.2f, paint)
        paint.color = Theme.INK
        val r = d * 0.09f
        canvas.drawCircle(l + d * 0.27f, t + d * 0.27f, r, paint)
        canvas.drawCircle(l + d * 0.73f, t + d * 0.73f, r, paint)
        paint.color = Theme.RED
        canvas.drawCircle(l + d * 0.5f, t + d * 0.5f, r * 1.2f, paint)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("주사위를 굴려 한 바퀴씩", "화면 가운데 주사위를 눌러 굴리고 나온 수만큼 말이 움직여요.\n출발 칸을 지나면 월급! 더블이면 한 번 더 굴립니다.") { c, w, h, p ->
            // 보드 테두리 + 말 + 주사위 두 개
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.06f
            p.color = 0xFF3D7BFF.toInt()
            c.drawRoundRect(w * 0.12f, h * 0.15f, w * 0.88f, h * 0.85f, h * 0.06f, h * 0.06f, p)
            p.style = Paint.Style.FILL
            for (k in 0 until 6) {
                p.color = if (k % 2 == 0) Theme.GOLD else 0xFFFFF8E7.toInt()
                val x = w * (0.18f + k * 0.13f)
                c.drawRect(x, h * 0.1f, x + w * 0.1f, h * 0.2f, p)
            }
            p.color = Theme.RED
            c.drawCircle(w * 0.5f, h * 0.15f, h * 0.07f, p)
            die(c, w * 0.36f, h * 0.42f, h * 0.28f, 3, p)
            die(c, w * 0.53f, h * 0.48f, h * 0.28f, 5, p)
        },
        RuleCard("땅을 사고 건물을 올려요", "빈 땅에 서면 칸이 반짝여요. 눌러서 사고, 다음에 오면 별장·빌딩·호텔을 지어요.\n남의 땅에 서면 통행료를 냅니다.") { c, w, h, p ->
            p.style = Paint.Style.FILL
            p.color = 0xFF27AE60.toInt()
            c.drawRect(w * 0.1f, h * 0.72f, w * 0.9f, h * 0.84f, p)
            for (k in 0 until 3) {
                val x = w * (0.18f + k * 0.24f)
                p.color = 0xFFFFF8E7.toInt()
                c.drawRect(x, h * (0.72f - HEIGHTS[k]), x + w * 0.16f, h * 0.72f, p)
                p.color = Theme.BLUE
                for (f in 0 until k + 1) c.drawRect(x + w * 0.03f, h * (0.72f - HEIGHTS[k] + 0.05f + f * 0.12f), x + w * 0.13f, h * (0.72f - HEIGHTS[k] + 0.11f + f * 0.12f), p)
            }
            // 깃발
            p.color = 0xFF5D4037.toInt()
            c.drawRect(w * 0.12f, h * 0.18f, w * 0.135f, h * 0.5f, p)
            p.color = Theme.RED
            c.drawRect(w * 0.135f, h * 0.18f, w * 0.24f, h * 0.28f, p)
            // 코인
            for (k in 0 until 3) {
                p.color = Theme.GOLD_DARK
                c.drawCircle(w * (0.78f + k * 0.04f), h * (0.25f + k * 0.08f), h * 0.07f, p)
                p.color = Theme.GOLD
                c.drawCircle(w * (0.78f + k * 0.04f), h * (0.25f + k * 0.08f), h * 0.055f, p)
            }
        },
        RuleCard("벌칙 칸 · 파산 · 승리", "벌칙 칸에서는 룰렛을 돌려요. 돈이 모자라면 땅을 팔거나 거래하고, 그래도 안 되면 파산!\n라운드가 끝나면 총자산 순, 아니면 끝까지 남은 사람이 1위.") { c, w, h, p ->
            p.style = Paint.Style.FILL
            val cx = w * 0.32f
            val cy = h * 0.5f
            val r = h * 0.32f
            for (k in 0 until 8) {
                p.color = WHEEL[k]
                c.drawArc(cx - r, cy - r, cx + r, cy + r, -112.5f + k * 45f, 45f, true, p)
            }
            p.color = Theme.CARD
            c.drawCircle(cx, cy, r * 0.22f, p)
            // 왕관
            val x = w * 0.72f
            val b = h * 0.62f
            val cw = w * 0.26f
            path.reset()
            path.moveTo(x - cw / 2, b)
            path.lineTo(x - cw / 2, b - cw * 0.55f)
            path.lineTo(x - cw * 0.23f, b - cw * 0.28f)
            path.lineTo(x, b - cw * 0.72f)
            path.lineTo(x + cw * 0.23f, b - cw * 0.28f)
            path.lineTo(x + cw / 2, b - cw * 0.55f)
            path.lineTo(x + cw / 2, b)
            path.close()
            p.color = Theme.GOLD
            c.drawPath(path, p)
            p.color = Theme.GOLD_DARK
            c.drawRect(x - cw / 2, b - cw * 0.12f, x + cw / 2, b, p)
        },
    )

    private fun die(c: Canvas, l: Float, t: Float, d: Float, v: Int, p: Paint) {
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(l, t, l + d, t + d, d * 0.18f, d * 0.18f, p)
        p.color = Theme.INK
        val r = d * 0.09f
        val a = d * 0.27f
        val m = d * 0.5f
        val z = d * 0.73f
        if (v % 2 == 1) c.drawCircle(l + m, t + m, r, p)
        if (v >= 2) { c.drawCircle(l + a, t + a, r, p); c.drawCircle(l + z, t + z, r, p) }
        if (v >= 4) { c.drawCircle(l + z, t + a, r, p); c.drawCircle(l + a, t + z, r, p) }
    }

    override fun hasSavedGame(context: Context, players: List<PlayerInfo>): Boolean =
        GameStorage(context).ongoingFor(players) != null

    override fun clearSave(context: Context) = GameStorage(context).clear()
}
