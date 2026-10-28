package com.siwoo.party.games.gonggi

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.gonggi.ui.GonggiActivity
import com.siwoo.party.games.gonggi.ui.GonggiArt
import com.siwoo.party.games.gonggi.ui.GonggiSettings

/** 공기놀이: 모음집 등록 정보 (2~4인, 차례대로, 목표 점수 먼저 달성 순) */
object GonggiModule : GameModule {
    override val id = "gonggi"
    override val name = "공기놀이"
    override val tagline = "톡 던지고, 콕 집고, 탁 받기! 꺾기로 점수"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "10분"
    override val color = 0xFFC2492F.toInt()
    override val activityClass = GonggiActivity::class.java

    override val settingsLabel: String get() = "목표 점수"

    override fun showSettings(popups: PopupLayer) {
        GonggiSettings.show(popups)
    }

    private val r = RectF()
    private const val MAT_EDGE = 0xFF9C2F3A.toInt()
    /** 꺾기 그림: 손등 위 돌 자리 (돌 반지름 단위) */
    private val BACK_X = floatArrayOf(-1.5f, 0.2f, 1.6f, -0.6f)
    private val BACK_Y = floatArrayOf(-0.4f, -0.9f, -0.3f, 0.6f)

    /** 점선 위쪽 화살표 (돌이 올라가는 길) */
    private fun dots(c: Canvas, p: Paint, x: Float, y0: Float, y1: Float, dot: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        val n = 5
        for (k in 0 until n) {
            val y = y0 + (y1 - y0) * k / (n - 1).toFloat()
            c.drawCircle(x, y, dot, p)
        }
    }

    /** 손가락 터치 표시 (동심원) */
    private fun touch(c: Canvas, p: Paint, x: Float, y: Float, s: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.12f
        p.color = Theme.GOLD
        c.drawCircle(x, y, s * 0.8f, p)
        c.drawCircle(x, y, s * 1.25f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE0C8.toInt()
        c.drawCircle(x, y, s * 0.45f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val sr = size * 0.075f
        // 바닥 돌 셋
        GonggiArt.shadow(canvas, cx - size * 0.3f, cy - size * 0.02f + sr * 0.5f, sr, 1f)
        GonggiArt.stone(canvas, cx - size * 0.3f, cy - size * 0.02f, sr, 1)
        GonggiArt.shadow(canvas, cx + size * 0.3f, cy - size * 0.1f + sr * 0.5f, sr, 1f)
        GonggiArt.stone(canvas, cx + size * 0.3f, cy - size * 0.1f, sr, 3)
        GonggiArt.shadow(canvas, cx + size * 0.2f, cy + size * 0.16f + sr * 0.5f, sr, 1f)
        GonggiArt.stone(canvas, cx + size * 0.2f, cy + size * 0.16f, sr, 2)
        // 손 (아래 가운데)
        val hr = size * 0.1f
        val hx = cx - size * 0.06f
        val hy = cy + size * 0.3f
        canvas.save()
        canvas.clipRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        GonggiArt.hand(canvas, hx, hy, hr, false, Theme.PLAYER_COLORS[1], cy + size / 2f)
        canvas.restore()
        GonggiArt.stone(canvas, hx + sr * 1.2f, hy - hr * 0.2f, sr * 0.9f, 4)
        // 공중의 돌 + 올라가는 점선
        dots(canvas, paint, hx, hy - hr * 2.6f, cy - size * 0.2f, size * 0.014f, Theme.GOLD_LIGHT)
        GonggiArt.stone(canvas, hx, cy - size * 0.33f, sr * 1.35f, 0)
        paint.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("① 던지고 · 집고 · 받기", "손을 톡! 누르면 돌이 위로 올라가요.\n떠 있는 동안 바닥 돌을 콕콕 집고,\n고리가 금색일 때 손을 눌러 받아요.") { c, w, h, p ->
            GonggiArt.mat(c, p, w * 0.5f, h * 0.08f, h * 0.98f, w * 0.72f, w * 0.86f, MAT_EDGE)
            val sr = h * 0.06f
            GonggiArt.shadow(c, w * 0.26f, h * 0.36f + sr * 0.5f, sr, 1f)
            GonggiArt.stone(c, w * 0.26f, h * 0.36f, sr, 1)
            GonggiArt.shadow(c, w * 0.72f, h * 0.3f + sr * 0.5f, sr, 1f)
            GonggiArt.stone(c, w * 0.72f, h * 0.3f, sr, 2)
            GonggiArt.shadow(c, w * 0.62f, h * 0.55f + sr * 0.5f, sr, 1f)
            GonggiArt.stone(c, w * 0.62f, h * 0.55f, sr, 3)
            touch(c, p, w * 0.72f, h * 0.3f, sr * 1.1f)
            // 손 + 공중의 돌
            val hx = w * 0.42f
            val hy = h * 0.8f
            val hr = h * 0.075f
            c.save()
            c.clipRect(0f, 0f, w, h * 0.98f)
            GonggiArt.hand(c, hx, hy, hr, false, Theme.PLAYER_COLORS[0], h)
            c.restore()
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = Theme.GOLD
            c.drawCircle(hx, hy - hr * 0.4f, hr * 1.45f, p)
            p.color = 0xCCFFFFFF.toInt()
            c.drawCircle(hx, hy - hr * 0.4f, hr * 2.4f, p)
            dots(c, p, hx, hy - hr * 2.8f, h * 0.24f, h * 0.012f, Theme.GOLD_LIGHT)
            GonggiArt.shadow(c, hx, hy - hr * 0.1f, sr * 0.7f, 0.5f)
            GonggiArt.stone(c, hx, h * 0.14f, sr * 1.3f, 0)
            GonggiArt.shout(c, "톡!", w * 0.2f, h * 0.84f, h * 0.1f, Theme.GOLD)
            GonggiArt.shout(c, "콕!", w * 0.84f, h * 0.2f, h * 0.1f, Theme.GOLD_LIGHT)
        },
        RuleCard("② 1단 → 4단, 실패하면 넘김", "1단 하나씩 · 2단 둘씩\n3단 셋 다음 하나 · 4단 넷을 한꺼번에!\n더 집거나 덜 집거나 놓치면 차례 넘김,\n다음 차례엔 그 단계 처음부터.") { c, w, h, p ->
            val groups = GameConfig.PICKS
            val names = GameConfig.STAGE_NAMES
            val cw = w * 0.22f
            val gap = w * 0.03f
            val x0 = (w - cw * 4 - gap * 3) / 2f
            val sr = minOf(h * 0.065f, cw / 7.6f)
            for (k in 0 until 4) {
                val l = x0 + k * (cw + gap)
                r.set(l, h * 0.12f, l + cw, h * 0.92f)
                p.style = Paint.Style.FILL
                p.color = 0x33FFFFFF
                c.drawRoundRect(r, h * 0.05f, h * 0.05f, p)
                GonggiArt.shout(c, names[k], r.centerX(), h * 0.3f, h * 0.12f, Theme.GOLD)
                // 한 번 던질 때 집는 묶음 (줄마다)
                val g = groups[k]
                for (row in g.indices) {
                    val y = h * 0.44f + row * h * 0.13f
                    val n = g[row]
                    var sx = r.centerX() - (n - 1) * sr * 0.9f
                    for (s in 0 until n) {
                        GonggiArt.stone(c, sx, y, sr, (k + s + row) % 4 + 1)
                        sx += sr * 1.8f
                    }
                }
            }
        },
        RuleCard("③ 꺾기로 점수!", "4단 다음은 꺾기! 5개를 던져 손등으로 받고\n(정확할수록 많이) 튕겨 올려 낚아채면\n손등에 있던 수만큼 점수!\n목표(기본 20점)에 먼저 닿은 순서대로 순위.") { c, w, h, p ->
            GonggiArt.mat(c, p, w * 0.5f, h * 0.08f, h * 0.98f, w * 0.72f, w * 0.86f, MAT_EDGE)
            val hx = w * 0.4f
            val hy = h * 0.7f
            val hr = h * 0.1f
            c.save()
            c.clipRect(0f, 0f, w, h * 0.98f)
            GonggiArt.hand(c, hx, hy, hr, true, Theme.PLAYER_COLORS[2], h)
            c.restore()
            // 손등 위 4개 + 떨어진 1개
            val sr = h * 0.055f
            for (k in 0 until 4) GonggiArt.stone(c, hx + BACK_X[k] * sr, hy - hr * 0.6f + BACK_Y[k] * sr, sr, k)
            GonggiArt.shadow(c, w * 0.66f, h * 0.84f + sr * 0.5f, sr, 1f)
            GonggiArt.stone(c, w * 0.66f, h * 0.84f, sr, 4)
            // 위로 튕김 점선 + 점수
            dots(c, p, hx, hy - hr * 2.4f, h * 0.18f, h * 0.012f, Theme.GOLD_LIGHT)
            GonggiArt.shout(c, "+4", w * 0.72f, h * 0.42f, h * 0.2f, Theme.GOLD)
            GonggiArt.shout(c, "꺾기!", w * 0.2f, h * 0.3f, h * 0.11f, Theme.TEXT)
        },
    )
}
