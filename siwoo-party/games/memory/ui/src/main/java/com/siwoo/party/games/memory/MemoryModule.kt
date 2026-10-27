package com.siwoo.party.games.memory

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.memory.ui.CardArt
import com.siwoo.party.games.memory.ui.MemoryActivity

/** 짝 맞추기: 모음집 등록 정보 */
object MemoryModule : GameModule {
    override val id = "memory"
    override val name = "짝 맞추기"
    override val tagline = "뒤집힌 카드 속 같은 그림을 기억해서 찾아라!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val minutes = "4분"
    override val color = 0xFFC2185B.toInt()
    override val activityClass = MemoryActivity::class.java

    private const val STAR = 0
    private const val HEART = 1
    private const val MOON = 2

    /** 네 갈래 반짝이 */
    private fun twinkle(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_LIGHT
        c.drawOval(x - s * 0.22f, y - s, x + s * 0.22f, y + s, p)
        c.drawOval(x - s, y - s * 0.22f, x + s, y + s * 0.22f, p)
    }

    /** 기울인 카드 한 장 (front < 0 이면 뒷면) */
    private fun tilted(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, deg: Float, front: Int, p: Paint, glow: Float = 0f) {
        c.save()
        c.rotate(deg, cx, cy)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        val r = minOf(w, h) * 0.14f
        c.drawRoundRect(cx - w / 2 + w * 0.05f, cy - h / 2 + h * 0.05f, cx + w / 2 + w * 0.05f, cy + h / 2 + h * 0.05f, r, r, p)
        if (front < 0) CardArt.drawBack(c, cx, cy, w, h, p, null) else CardArt.drawFront(c, front, cx, cy, w, h, p, glow)
        c.restore()
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val h = size * 0.62f
        val w = h * 0.78f
        tilted(canvas, cx - w * 0.38f, cy + h * 0.02f, w, h, -14f, -1, paint)
        tilted(canvas, cx + w * 0.38f, cy - h * 0.02f, w, h, 12f, HEART, paint)
        twinkle(canvas, cx + w * 0.95f, cy - h * 0.48f, size * 0.07f, paint)
        twinkle(canvas, cx - w * 0.9f, cy + h * 0.46f, size * 0.05f, paint)
    }

    /** 규칙 그림 1: 3줄 판 (별 한 장은 앞면, 한 장은 뒤집히는 중이라 따로 그림) — 그릴 때 할당하지 않도록 미리 */
    private const val SKIP = -2
    private val RULE1_FACES = IntArray(18) { -1 }.also { it[7] = STAR; it[10] = SKIP }

    /** 규칙 그림 공통: 작은 판 (가로 n × 세로 m 장), faces[k] 가 0 이상이면 앞면 그림, SKIP 이면 비움 */
    private fun miniBoard(c: Canvas, l: Float, t: Float, cw: Float, ch: Float, cols: Int, rows: Int, faces: IntArray, p: Paint, glow: Float = 0f) {
        val gap = cw * 0.14f
        for (k in 0 until cols * rows) {
            val x = l + (k % cols) * (cw + gap) + cw / 2f
            val y = t + (k / cols) * (ch + gap) + ch / 2f
            val f = faces[k]
            if (f == SKIP) continue
            if (f < 0) CardArt.drawBack(c, x, y, cw, ch, p, null)
            else CardArt.drawFront(c, f, x, y, cw, ch, p, glow)
        }
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("차례에 카드 2장 뒤집기", "카드 24장(그림 12쌍)이 금색 뒷면으로 엎어져 있어요.\n자기 차례에 카드 2장을 차례로 눌러 뒤집어요. 뒤집히는 중엔 다른 카드를 못 눌러요.") { c, w, h, p ->
            val ch = h * 0.24f
            val cw = ch * 0.78f
            val cols = 6
            val gw = cols * cw + (cols - 1) * cw * 0.14f
            miniBoard(c, (w - gw) / 2f, h * 0.1f, cw, ch, cols, 3, RULE1_FACES, p)
            // 두 번째 카드: 뒤집히는 중 (가로로 접힘)
            val x = (w - gw) / 2f + 4 * (cw * 1.14f) + cw / 2f
            val y = h * 0.1f + 1 * (ch + cw * 0.14f) + ch / 2f
            c.save()
            c.scale(0.35f, 1f, x, y)
            CardArt.drawBack(c, x, y, cw, ch, p, null)
            c.restore()
            // 손가락 대신 터치 물결
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.018f
            p.color = Theme.TEXT
            c.drawCircle(x + cw * 0.2f, y + ch * 0.25f, ch * 0.18f, p)
            c.drawCircle(x + cw * 0.2f, y + ch * 0.25f, ch * 0.3f, p)
            p.style = Paint.Style.FILL
        },
        RuleCard("같은 그림이면 한 번 더!", "두 장이 같은 그림이면 반짝이며 내 패널로 날아가요.\n짝을 맞춘 사람이 이어서 한 번 더 뒤집어요.") { c, w, h, p ->
            val ch = h * 0.42f
            val cw = ch * 0.78f
            CardArt.drawFront(c, HEART, w * 0.2f, h * 0.48f, cw, ch, p, 1f)
            CardArt.drawFront(c, HEART, w * 0.43f, h * 0.48f, cw, ch, p, 1f)
            twinkle(c, w * 0.31f, h * 0.18f, h * 0.06f, p)
            twinkle(c, w * 0.08f, h * 0.78f, h * 0.045f, p)
            twinkle(c, w * 0.53f, h * 0.8f, h * 0.05f, p)
            // 화살표 → 플레이어 패널
            p.color = Theme.GOLD
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.035f
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(w * 0.56f, h * 0.48f, w * 0.7f, h * 0.48f, p)
            c.drawLine(w * 0.7f, h * 0.48f, w * 0.66f, h * 0.42f, p)
            c.drawLine(w * 0.7f, h * 0.48f, w * 0.66f, h * 0.54f, p)
            p.style = Paint.Style.FILL
            // 패널 칸
            p.color = Theme.CARD_LIGHT
            c.drawRoundRect(w * 0.74f, h * 0.3f, w * 0.96f, h * 0.66f, h * 0.05f, h * 0.05f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.02f
            p.color = Theme.GOLD
            c.drawRoundRect(w * 0.74f, h * 0.3f, w * 0.96f, h * 0.66f, h * 0.05f, h * 0.05f, p)
            p.style = Paint.Style.FILL
            p.color = Theme.PLAYER_COLORS[0]
            c.drawCircle(w * 0.8f, h * 0.48f, h * 0.07f, p)
            // 모은 카드 2장
            for (k in 0 until 2) {
                val x = w * 0.87f + k * h * 0.07f
                p.color = Theme.GOLD_DARK
                c.drawRoundRect(x - h * 0.035f, h * 0.41f, x + h * 0.035f, h * 0.55f, h * 0.01f, h * 0.01f, p)
                p.color = Theme.GOLD_LIGHT
                c.drawRoundRect(x - h * 0.022f, h * 0.425f, x + h * 0.022f, h * 0.535f, h * 0.008f, h * 0.008f, p)
            }
        },
        RuleCard("다르면 다음 사람", "그림이 다르면 1.2초 동안 보여 준 뒤 다시 엎어지고 다음 사람 차례예요. 위치를 잘 기억하세요!\n12쌍을 다 찾으면 끝, 모은 쌍이 많은 순서로 순위.") { c, w, h, p ->
            val ch = h * 0.46f
            val cw = ch * 0.78f
            CardArt.drawFront(c, HEART, w * 0.26f, h * 0.48f, cw, ch, p)
            CardArt.drawFront(c, MOON, w * 0.74f, h * 0.48f, cw, ch, p)
            // 가운데 큰 X
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.06f
            p.color = Theme.RED
            val d = h * 0.09f
            c.drawLine(w * 0.5f - d, h * 0.48f - d, w * 0.5f + d, h * 0.48f + d, p)
            c.drawLine(w * 0.5f + d, h * 0.48f - d, w * 0.5f - d, h * 0.48f + d, p)
            p.style = Paint.Style.FILL
        },
    )
}
