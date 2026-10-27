package com.siwoo.party.games.spotdiff

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.spotdiff.ui.SceneRenderer
import com.siwoo.party.games.spotdiff.ui.SpotDiffActivity
import com.siwoo.party.games.spotdiff.ui.SpotView

/** 틀린 그림 찾기 대결: 모음집 등록 정보 */
object SpotDiffModule : GameModule {
    override val id = "spotdiff"
    override val name = "틀린 그림 찾기 대결"
    override val tagline = "다른 곳을 찾았다! 버저 먼저 누르고 콕!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "5분"
    override val color = 0xFF2BA6C9.toInt()
    override val activityClass = SpotDiffActivity::class.java

    private val rect = RectF()
    private val renderer = SceneRenderer()
    /** 규칙 카드 그림용 고정 장면 (처음 한 번만 만든다) */
    private val demo: Scene by lazy { SceneGenerator.generate(DEMO_SEED) }
    private const val DEMO_SEED = 21L
    /** 규칙 카드의 오답 자리: 땅 위, 어떤 차이와도 먼 곳 (그림 좌표) */
    private val demoMiss: FloatArray by lazy {
        var best = floatArrayOf(GameConfig.SCENE_W / 2f, GameConfig.HORIZON_Y + 40f)
        var bestD = -1f
        var y = GameConfig.HORIZON_Y + 20f
        while (y < GameConfig.SCENE_H - 20f) {
            var x = 40f
            while (x < GameConfig.SCENE_W - 40f) {
                var near = Float.MAX_VALUE
                for (d in demo.differences) near = minOf(near, d.dist2(x, y) / (d.hitRadius * d.hitRadius))
                if (near > bestD) { bestD = near; best = floatArrayOf(x, y) }
                x += 10f
            }
            y += 10f
        }
        best
    }
    private const val RED = SpotView.CIRCLE_RED

    /** 작은 그림 한 장 (하늘·땅·해·집) — 아이콘용 */
    private fun miniPicture(c: Canvas, l: Float, t: Float, s: Float, p: Paint, sunColor: Int, houseColor: Int) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFF8E7.toInt()
        c.drawRoundRect(l - s * 0.05f, t - s * 0.05f, l + s * 1.05f, t + s * 1.15f, s * 0.08f, s * 0.08f, p)
        p.color = 0xFF6EC1F5.toInt()
        c.drawRect(l, t, l + s, t + s * 0.62f, p)
        p.color = 0xFF7BC25A.toInt()
        c.drawRect(l, t + s * 0.62f, l + s, t + s * 1.1f, p)
        if (sunColor != 0) {
            p.color = sunColor
            c.drawCircle(l + s * 0.24f, t + s * 0.24f, s * 0.13f, p)
        }
        // 집
        p.color = houseColor
        c.drawRect(l + s * 0.5f, t + s * 0.58f, l + s * 0.88f, t + s * 0.92f, p)
        p.color = Theme.RED
        rect.set(l + s * 0.44f, t + s * 0.36f, l + s * 0.94f, t + s * 0.6f)
        c.drawArc(rect, 180f, 180f, true, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val s = size * 0.4f
        val gap = size * 0.06f
        val l0 = cx - s - gap / 2f
        val l1 = cx + gap / 2f
        val t = cy - s * 0.62f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawRoundRect(l0 + s * 0.02f, t + s * 0.08f, l1 + s * 1.08f, t + s * 1.22f, s * 0.1f, s * 0.1f, paint)
        miniPicture(canvas, l0, t, s, paint, 0xFFFDD835.toInt(), 0xFFFDD835.toInt())
        // 오른쪽: 해가 사라지고 집 색이 바뀜
        miniPicture(canvas, l1, t, s, paint, 0, 0xFFF06292.toInt())
        // 빨간 동그라미 (해 자리)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.035f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(l1 + s * 0.24f, t + s * 0.24f, s * 0.2f, paint)
        paint.color = RED
        paint.strokeWidth = size * 0.022f
        canvas.drawCircle(l1 + s * 0.24f, t + s * 0.24f, s * 0.2f, paint)
        // 돋보기
        val mx = cx + s * 0.62f
        val my = cy + s * 0.62f
        val mr = s * 0.22f
        paint.color = 0xFF3E2723.toInt()
        paint.strokeWidth = size * 0.06f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(mx + mr * 0.7f, my + mr * 0.7f, mx + mr * 1.6f, my + mr * 1.6f, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        paint.color = 0x99E1F5FE.toInt()
        canvas.drawCircle(mx, my, mr, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.04f
        paint.color = Theme.GOLD
        canvas.drawCircle(mx, my, mr, paint)
        paint.style = Paint.Style.FILL
    }

    // ── 규칙 그림 ──

    /**
     * 그림을 (w, h) 안 세로 비율 top..bottom 에 그린다 → 그림 좌표 배율.
     * only = -1 두 장 나란히, 0/1 = 그 그림 한 장만 가운데. 그린 자리는 picX0(왼쪽)·picX1(오른쪽)·picY.
     */
    private fun pictures(c: Canvas, w: Float, h: Float, p: Paint, top: Float, bottom: Float, only: Int): Float {
        val gap = w * 0.04f
        val maxW = if (only < 0) (w * 0.9f - gap) / 2f else w * 0.9f
        val maxH = h * (bottom - top)
        val pw = minOf(maxW, maxH * GameConfig.SCENE_W / GameConfig.SCENE_H)
        val s = pw / GameConfig.SCENE_W
        val ph = GameConfig.SCENE_H * s
        val x0 = if (only < 0) w / 2f - pw - gap / 2f else (w - pw) / 2f
        val y0 = h * top + (maxH - ph) / 2f
        for (k in 0..1) {
            if (only >= 0 && k != only) continue
            val x = if (only < 0) x0 + k * (pw + gap) else x0
            p.style = Paint.Style.FILL
            p.color = 0xFF1A2040.toInt()
            c.drawRoundRect(x - 3f, y0 - 3f, x + pw + 3f, y0 + ph + 3f, 6f, 6f, p)
            c.save()
            c.clipRect(x, y0, x + pw, y0 + ph)
            renderer.drawScene(c, demo, k == 1, x, y0, s)
            c.restore()
        }
        picX0 = x0
        picX1 = if (only < 0) x0 + pw + gap else x0
        picY = y0
        return s
    }

    private var picX0 = 0f
    private var picX1 = 0f
    private var picY = 0f

    private fun circleAt(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.STROKE
        p.color = 0xE6FFFFFF.toInt()
        p.strokeWidth = r * 0.22f
        c.drawCircle(x, y, r, p)
        p.color = RED
        p.strokeWidth = r * 0.12f
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    private fun label(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.color = color
        c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** 버저 하나 (잠금이면 회색 + 빨간 고리) */
    private fun buzzer(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int, locked: Boolean) {
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawCircle(x, y + r * 0.08f, r, p)
        val base = if (locked) Theme.GRAY else color
        p.color = Theme.shade(base, 0.62f)
        c.drawCircle(x, y + r * 0.04f, r, p)
        p.color = base
        c.drawCircle(x, y - r * 0.03f, r * 0.9f, p)
        if (locked) {
            p.style = Paint.Style.STROKE
            p.color = Theme.RED
            p.strokeWidth = r * 0.12f
            rect.set(x - r * 1.15f, y - r * 1.15f, x + r * 1.15f, y + r * 1.15f)
            c.drawArc(rect, -90f, 220f, false, p)
        } else {
            p.style = Paint.Style.STROKE
            p.color = Theme.GOLD
            p.strokeWidth = r * 0.1f
            c.drawCircle(x, y - r * 0.03f, r * 0.9f, p)
        }
        label(c, p, if (locked) SpotView.LOCK_LABEL else SpotView.BUZZ_LABEL, x, y + r * 0.14f, r * 0.38f, if (locked) 0xFFE0E4F5.toInt() else Theme.textOn(base))
    }

    /** 손가락 끝 (반투명 동그라미 + 테두리) */
    private fun finger(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x66FFFFFF
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.18f
        p.color = Theme.INK
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("두 그림을 비교해요", "왼쪽은 원래 그림, 오른쪽은 5곳이 달라요.\n색이 바뀌거나, 사라지거나, 크기가 달라졌어요.") { c, w, h, p ->
            val s = pictures(c, w, h, p, 0.16f, 0.98f, -1)
            val pw = GameConfig.SCENE_W * s
            label(c, p, "원래 그림", picX0 + pw / 2f, h * 0.11f, h * 0.075f, Theme.TEXT)
            label(c, p, "다른 곳 5군데", picX1 + pw / 2f, h * 0.11f, h * 0.075f, Theme.GOLD)
            for (d in demo.differences) circleAt(c, p, picX1 + d.cx * s, picY + d.cy * s, d.radius * s)
        },
        RuleCard("[찾았다!] 먼저 → 3초 안에 콕!", "다른 곳을 찾으면 내 구역의 버저를 먼저 눌러요.\n3초 안에 그림에서 그곳을 터치하면 +1점!\n(왼쪽·오른쪽 그림 어디를 눌러도 돼요)") { c, w, h, p ->
            val s = pictures(c, w * 0.7f, h, p, 0.04f, 0.96f, 1)
            // 오른쪽 그림 하나 + 버저
            val pw = GameConfig.SCENE_W * s
            val d = demo.differences[0]
            val br = h * 0.17f
            buzzer(c, p, w * 0.86f, h * 0.5f, br, Theme.playerColor(0), false)
            val fx = picX0 + d.cx * s
            val fy = picY + d.cy * s
            circleAt(c, p, fx, fy, d.radius * s)
            finger(c, p, fx + d.radius * s * 0.4f, fy + d.radius * s * 0.5f, h * 0.06f)
            label(c, p, "+1", picX0 + pw + (w * 0.86f - br - picX0 - pw) / 2f, h * 0.3f, h * 0.14f, Theme.GOLD_DARK)
            label(c, p, "3초!", w * 0.86f, h * 0.5f + br + h * 0.13f, h * 0.08f, Theme.TEXT)
        },
        RuleCard("틀리면 -1점, 2초 잠금", "틀린 곳을 누르거나 3초가 지나면 -1점,\n2초 동안 버저를 못 눌러요. 5곳을 다 찾으면 다음 장면!\n3장면 합계 점수 순 (같으면 같은 순위).") { c, w, h, p ->
            val s = pictures(c, w * 0.7f, h, p, 0.04f, 0.96f, 1)
            val pw = GameConfig.SCENE_W * s
            // 차이가 없는 곳에 X
            val x = picX0 + demoMiss[0] * s
            val y = picY + demoMiss[1] * s
            val sz = h * 0.06f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFFFFFFFF.toInt()
            p.strokeWidth = sz * 0.6f
            c.drawLine(x - sz, y - sz, x + sz, y + sz, p)
            c.drawLine(x + sz, y - sz, x - sz, y + sz, p)
            p.color = Theme.RED
            p.strokeWidth = sz * 0.34f
            c.drawLine(x - sz, y - sz, x + sz, y + sz, p)
            c.drawLine(x + sz, y - sz, x - sz, y + sz, p)
            p.strokeCap = Paint.Cap.BUTT
            val br = h * 0.17f
            buzzer(c, p, w * 0.86f, h * 0.5f, br, Theme.playerColor(1), true)
            label(c, p, "-1", picX0 + pw + (w * 0.86f - br - picX0 - pw) / 2f, h * 0.3f, h * 0.14f, Theme.RED)
            label(c, p, "2초 잠금", w * 0.86f, h * 0.5f + br + h * 0.13f, h * 0.08f, Theme.TEXT)
        },
    )
}
