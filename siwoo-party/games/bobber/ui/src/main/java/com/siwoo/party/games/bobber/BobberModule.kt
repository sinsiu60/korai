package com.siwoo.party.games.bobber

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bobber.ui.BobberActivity
import com.siwoo.party.games.bobber.ui.BobberArt

/** 낚시 찌 대결: 모음집 등록 정보 */
object BobberModule : GameModule {
    override val id = "bobber"
    override val name = "낚시 찌 대결"
    override val tagline = "찌가 쑥! 가라앉는 순간, 누가 먼저 낚아챌까?"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "3분"
    override val color = 0xFF1E7FC2.toInt()
    override val activityClass = BobberActivity::class.java

    private val oval = RectF()
    private val water = RectF()
    private val light = RectF()
    private val clip = Path()

    /** 작은 연못 (가운데 cx, 물 높이 wl, 폭 w, 높이 h) */
    private fun pond(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = BobberArt.BANK
        oval.set(cx - w / 2f - h * 0.08f, cy - h / 2f - h * 0.08f, cx + w / 2f + h * 0.08f, cy + h / 2f + h * 0.1f)
        c.drawOval(oval, p)
        p.color = BobberArt.WATER_BOTTOM
        water.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawOval(water, p)
        p.color = BobberArt.WATER_TOP
        light.set(cx - w * 0.44f, cy - h * 0.46f, cx + w * 0.44f, cy + h * 0.2f)
        c.drawOval(light, p)
    }

    /** 방금 그린 연못(water) 안에 찌 (몸통 가운데 cy, 물 높이 wl) — 물 아래는 물빛으로 덮고 연못 밖은 잘라냄 */
    private fun floatAt(c: Canvas, cx: Float, wl: Float, cy: Float, r: Float, p: Paint, tilt: Float = 0f) {
        c.save()
        clip.reset()
        clip.addOval(water, Path.Direction.CW)
        clip.addRect(water.left, water.top - r * 4f, water.right, water.centerY(), Path.Direction.CW)
        c.clipPath(clip)
        c.save()
        c.rotate(tilt, cx, wl)
        BobberArt.bobber(c, p, cx, cy, r)
        c.restore()
        c.clipRect(cx - r * 2f, wl, cx + r * 2f, cy + r * 3f)
        // 물 아래 부분: 연못 물빛을 반투명하게 다시 칠함
        p.style = Paint.Style.FILL
        p.color = BobberArt.WATER_BOTTOM
        p.alpha = 0xD0
        c.drawOval(water, p)
        p.color = BobberArt.WATER_TOP
        p.alpha = 0xD0
        c.drawOval(light, p)
        p.alpha = 255
        c.restore()
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size
        // 연못
        pond(canvas, cx, cy + s * 0.2f, s * 0.86f, s * 0.42f, paint)
        // 물결
        BobberArt.ripple(canvas, paint, cx - s * 0.1f, cy + s * 0.2f, s * 0.16f, 200, s * 0.02f)
        BobberArt.ripple(canvas, paint, cx - s * 0.1f, cy + s * 0.2f, s * 0.27f, 110, s * 0.018f)
        // 찌 (반쯤 잠김)
        floatAt(canvas, cx - s * 0.1f, cy + s * 0.2f, cy + s * 0.14f, s * 0.1f, paint)
        // 튀어 오르는 물고기
        canvas.save()
        canvas.rotate(-35f, cx + s * 0.2f, cy - s * 0.2f)
        BobberArt.fish(canvas, paint, cx + s * 0.2f, cy - s * 0.2f, s * 0.42f, 0xFFFF8A3D.toInt())
        canvas.restore()
        // 물방울
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx + s * 0.02f, cy + s * 0.02f, s * 0.025f, paint)
        canvas.drawCircle(cx + s * 0.08f, cy - s * 0.06f, s * 0.018f, paint)
        canvas.drawCircle(cx + s * 0.38f, cy + s * 0.04f, s * 0.02f, paint)
    }

    /** 규칙 그림 공통: 네 귀퉁이 구역에 같은 연못·같은 찌 (sinkF: 0 떠 있음 ~ 1 잠김, tilt: 기울기 도) */
    private fun zones(c: Canvas, w: Float, h: Float, p: Paint, sinkF: Float, tilt: Float, crossed: Int, winner: Int) {
        for (k in 0 until 4) {
            val l = if (k % 2 == 0) w * 0.05f else w * 0.53f
            val t = if (k < 2) h * 0.06f else h * 0.53f
            val zw = w * 0.42f
            val zh = h * 0.41f
            p.style = Paint.Style.FILL
            p.color = Theme.PLAYER_COLORS[k]
            p.alpha = 45
            c.drawRoundRect(l, t, l + zw, t + zh, h * 0.04f, h * 0.04f, p)
            p.alpha = 255
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = if (k == winner) Theme.GOLD else Theme.PLAYER_COLORS[k]
            c.drawRoundRect(l, t, l + zw, t + zh, h * 0.04f, h * 0.04f, p)
            val cx = l + zw / 2f
            val cy = t + zh * 0.55f
            pond(c, cx, cy, zw * 0.78f, zh * 0.62f, p)
            val r = zh * 0.085f
            val wl = cy + zh * 0.04f
            floatAt(c, cx, wl, wl - r * 0.3f + sinkF * r * 3f, r, p, tilt)
            if (sinkF > 0.5f) {
                BobberArt.ripple(c, p, cx, wl, r * 2.2f, 220, h * 0.008f)
                BobberArt.ripple(c, p, cx, wl, r * 3.6f, 120, h * 0.008f)
            }
            if (k == crossed) {
                p.style = Paint.Style.FILL
                p.color = 0xB0384058.toInt()
                c.drawRoundRect(l, t, l + zw, t + zh, h * 0.04f, h * 0.04f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.03f
                p.color = Theme.RED
                val d = zh * 0.22f
                c.drawLine(cx - d, cy - d, cx + d, cy + d, p)
                c.drawLine(cx + d, cy - d, cx - d, cy + d, p)
            }
            if (k == winner) {
                c.save()
                c.rotate(-30f, cx + zw * 0.2f, t + zh * 0.25f)
                BobberArt.fish(c, p, cx + zw * 0.2f, t + zh * 0.25f, zh * 0.42f, 0xFFFF6FA8.toInt())
                c.restore()
            }
        }
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("모두 같은 찌를 봐요", "자기 구역 연못의 찌를 지켜보세요.\n모든 구역의 찌는 똑같이 움직여요.\n살짝 흔들리거나 톡톡 잠기면 가짜 입질!") { c, w, h, p ->
            zones(c, w, h, p, 0f, 14f, -1, -1)
        },
        RuleCard("쑥! 가라앉으면 터치", "진짜 입질은 찌가 쑥! 가라앉아요.\n그때 가장 먼저 터치한 사람이 한 마리!\n반응 시간도 보여 줘요. 1.5초 안에\n아무도 못 누르면 점수 없음.") { c, w, h, p ->
            zones(c, w, h, p, 1f, 0f, -1, 1)
        },
        RuleCard("가짜 입질에 누르면 실격", "진짜 입질 전에 누르면 그 라운드만 실격\n(회색 + \"너무 빨라요!\").\n10라운드 동안 많이 낚은 순서로 순위,\n같으면 공동 순위예요.") { c, w, h, p ->
            zones(c, w, h, p, 0.25f, -10f, 2, -1)
        },
    )
}
