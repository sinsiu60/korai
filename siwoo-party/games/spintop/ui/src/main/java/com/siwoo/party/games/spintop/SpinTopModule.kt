package com.siwoo.party.games.spintop

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.spintop.ui.SpinArt
import com.siwoo.party.games.spintop.ui.SpinTopActivity

/** 팽이 대결: 모음집 등록 정보 (2~4인, 차례대로 준비 → 동시에 대결, 3판 합산) */
object SpinTopModule : GameModule {
    override val id = "spintop"
    override val name = "팽이 대결"
    override val tagline = "줄을 감고 던져라, 마지막까지 도는 팽이가 승리!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "5분"
    override val color = 0xFFD9772B.toInt()
    override val activityClass = SpinTopActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val GREEN = Theme.PLAYER_COLORS[2]
    private val YELLOW = Theme.PLAYER_COLORS[3]

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        SpinArt.bowl(canvas, paint, cx, cy, r)
        // 넘어진 팽이 + 도는 팽이 둘 (부딪히는 중)
        SpinArt.fallen(canvas, paint, cx - r * 0.42f, cy + r * 0.38f, r * 0.2f, GREEN, -30f)
        SpinArt.top(canvas, paint, cx - r * 0.2f, cy - r * 0.12f, r * 0.3f, RED, 0.4f)
        SpinArt.top(canvas, paint, cx + r * 0.36f, cy + r * 0.12f, r * 0.3f, BLUE, 1.3f)
        // 부딪힘 불꽃
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.06f
        paint.color = Theme.GOLD_LIGHT
        val sx = cx + r * 0.08f
        val sy = cy - r * 0.02f
        canvas.drawLine(sx, sy - r * 0.42f, sx, sy - r * 0.6f, paint)
        canvas.drawLine(sx + r * 0.14f, sy - r * 0.36f, sx + r * 0.26f, sy - r * 0.5f, paint)
        canvas.drawLine(sx - r * 0.14f, sy - r * 0.36f, sx - r * 0.26f, sy - r * 0.5f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF0B1340.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 손가락 */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE0C8.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.2f
        p.color = 0xFF8A5A26.toInt()
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("① 줄 감기 연타 → 회전력", "내 차례가 되면 [줄 감기!] 버튼을\n5초 동안 마구 연타하세요.\n많이 칠수록 회전력이 올라가요.\n(너무 많이 쳐도 점점 덜 올라요)") { c, w, h, p ->
            val bx = w * 0.3f
            val by = h * 0.5f
            val br = h * 0.3f
            p.style = Paint.Style.FILL
            p.color = 0x55000000
            c.drawCircle(bx, by + br * 0.08f, br, p)
            p.color = Theme.shade(RED, 0.6f)
            c.drawCircle(bx, by, br, p)
            p.color = RED
            c.drawCircle(bx, by - br * 0.05f, br * 0.9f, p)
            SpinArt.coil(c, p, bx, by + br * 0.38f, br * 0.55f, 3f, br * 0.06f)
            shout(c, p, "줄 감기!", bx, by - br * 0.05f, br * 0.32f, Theme.TEXT)
            p.style = Paint.Style.STROKE
            p.strokeWidth = br * 0.05f
            p.color = Theme.GOLD
            c.drawCircle(bx, by, br * 1.12f, p)
            c.drawCircle(bx, by, br * 1.25f, p)
            finger(c, p, bx + br * 0.55f, by + br * 0.6f, br * 0.22f)
            // 오른쪽: 회전력 막대 (오르는 곡선)
            val gx = w * 0.62f
            val gy = h * 0.8f
            val gw = w * 0.32f
            val gh = h * 0.55f
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.TEXT_SUB
            c.drawLine(gx, gy, gx + gw, gy, p)
            c.drawLine(gx, gy, gx, gy - gh, p)
            p.color = Theme.GOLD
            p.strokeWidth = h * 0.025f
            var px = gx
            var py = gy - gh * 0.2f
            for (k in 1..20) {
                val f = k / 20f
                val nx = gx + gw * f
                val ny = gy - gh * (0.2f + 0.8f * (1f - Math.exp(-f * 2.6).toFloat()))
                c.drawLine(px, py, nx, ny, p)
                px = nx
                py = ny
            }
            p.style = Paint.Style.FILL
            shout(c, p, "5초", gx + gw * 0.6f, gy - gh * 0.12f, h * 0.13f, Theme.GOLD_LIGHT)
        },
        RuleCard("② 던지기 게이지 → 안정성", "줄을 다 감으면 바늘이 좌우로 왕복!\n가운데 금색 칸에서 터치할수록\n안정성이 높아요. 안정성이 높으면\n부딪혀도 회전력이 덜 깎여요.") { c, w, h, p ->
            val l = w * 0.1f
            val r = w * 0.9f
            val t = h * 0.38f
            val b = h * 0.56f
            p.style = Paint.Style.FILL
            p.color = 0xAA1A1030.toInt()
            c.drawRoundRect(l, t, r, b, (b - t) / 2f, (b - t) / 2f, p)
            val zl = l + (r - l) * (GameConfig.GAUGE_CENTER - GameConfig.GAUGE_HALF)
            val zr = l + (r - l) * (GameConfig.GAUGE_CENTER + GameConfig.GAUGE_HALF)
            p.color = 0x40FFC93C
            c.drawRect(zl - (r - l) * 0.12f, t, zr + (r - l) * 0.12f, b, p)
            p.color = 0xCCFFC93C.toInt()
            c.drawRect(zl, t, zr, b, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.GOLD_LIGHT
            c.drawRoundRect(l, t, r, b, (b - t) / 2f, (b - t) / 2f, p)
            // 바늘 + 왕복 화살표
            p.style = Paint.Style.FILL
            p.color = Theme.TEXT
            val nx = (zl + zr) / 2f
            c.drawRect(nx - h * 0.01f, t - h * 0.04f, nx + h * 0.01f, b + h * 0.04f, p)
            shout(c, p, "◀  ▶", nx, t - h * 0.08f, h * 0.12f, Theme.TEXT)
            finger(c, p, nx + w * 0.06f, b + h * 0.14f, h * 0.06f)
            // 아래: 안정성 높음/낮음 팽이
            SpinArt.top(c, p, w * 0.2f, h * 0.79f, h * 0.09f, BLUE, 0.3f, squash = 0.8f, tilt = 30f)
            shout(c, p, "비틀", w * 0.2f, h * 0.95f, h * 0.07f, Theme.RED)
            SpinArt.top(c, p, w * 0.8f, h * 0.79f, h * 0.09f, BLUE, 0.9f)
            shout(c, p, "꼿꼿", w * 0.8f, h * 0.95f, h * 0.07f, Theme.GREEN)
        },
        RuleCard("③ 마지막까지 도는 팽이 승리", "모든 팽이를 동시에 던져요. 경기장은\n가운데로 기울어져 서로 부딪혀요.\n회전력이 0 이 되면 쓰러짐!\n쓰러진 순서로 순위·점수, 3판 합산") { c, w, h, p ->
            val cx = w * 0.36f
            val cy = h * 0.52f
            val br = h * 0.44f
            SpinArt.bowl(c, p, cx, cy, br)
            SpinArt.fallen(c, p, cx - br * 0.45f, cy + br * 0.4f, br * 0.16f, GREEN, -20f)
            SpinArt.fallen(c, p, cx + br * 0.5f, cy - br * 0.4f, br * 0.16f, YELLOW, 140f)
            SpinArt.top(c, p, cx - br * 0.08f, cy, br * 0.2f, RED, 0.5f, blur = 0.6f)
            SpinArt.top(c, p, cx + br * 0.32f, cy + br * 0.18f, br * 0.2f, BLUE, 1.2f, blur = 0.6f)
            // 기울기 화살표 (가운데로)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.014f
            p.color = 0x99FFFFFF.toInt()
            c.drawLine(cx - br * 0.75f, cy - br * 0.1f, cx - br * 0.45f, cy - br * 0.05f, p)
            c.drawLine(cx + br * 0.1f, cy + br * 0.78f, cx + br * 0.05f, cy + br * 0.5f, p)
            p.style = Paint.Style.FILL
            // 오른쪽: 점수
            val tx = w * 0.82f
            shout(c, p, "1위 4점", tx, h * 0.3f, h * 0.1f, Theme.GOLD)
            shout(c, p, "2위 3점", tx, h * 0.46f, h * 0.085f, Theme.TEXT)
            shout(c, p, "…", tx, h * 0.58f, h * 0.085f, Theme.TEXT_SUB)
            shout(c, p, "3판 합산!", tx, h * 0.8f, h * 0.1f, Theme.GOLD_LIGHT)
        },
    )
}
