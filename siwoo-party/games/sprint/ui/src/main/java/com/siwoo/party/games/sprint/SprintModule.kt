package com.siwoo.party.games.sprint

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.sprint.ui.SprintActivity
import com.siwoo.party.games.sprint.ui.SprintArt

/** 100m 달리기: 모음집 등록 정보 */
object SprintModule : GameModule {
    override val id = "sprint"
    override val name = "100m 달리기"
    override val tagline = "왼발·오른발 번갈아 연타! 결승선까지 전력 질주"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "1분"
    override val color = 0xFFE0603A.toInt()
    override val activityClass = SprintActivity::class.java

    private val box = RectF()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.42f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = SprintArt.TRACK
        canvas.drawCircle(cx, cy, r, paint)
        // 레인 선 두 줄
        canvas.save()
        canvas.clipRect(cx - r, cy - r, cx + r, cy + r)
        paint.color = 0x33FFFFFF
        canvas.drawCircle(cx, cy - r * 0.1f, r * 0.8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.05f
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawLine(cx - r, cy + r * 0.62f, cx + r, cy + r * 0.62f, paint)
        canvas.drawLine(cx - r, cy - r * 0.55f, cx + r, cy - r * 0.55f, paint)
        canvas.restore()
        // 결승선 (오른쪽)
        SprintArt.checker(canvas, paint, cx + r * 0.62f, cy - r * 0.55f, cy + r * 0.62f, r * 0.14f, r * 0.08f)
        // 선수 (금색)
        SprintArt.runner(canvas, paint, cx - r * 0.12f, cy + r * 0.58f, r * 1.2f, Theme.GOLD, 0.95f, 0f, false, r * 0.04f)
        // 속도선
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.07f
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawLine(cx - r * 0.82f, cy - r * 0.18f, cx - r * 0.5f, cy - r * 0.18f, paint)
        canvas.drawLine(cx - r * 0.9f, cy + r * 0.04f, cx - r * 0.56f, cy + r * 0.04f, paint)
        canvas.drawLine(cx - r * 0.8f, cy + r * 0.26f, cx - r * 0.52f, cy + r * 0.26f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 공통: 트랙 레인 3개 (y0..y1), 선수 위치(0..1)와 다리 */
    private fun lanes(c: Canvas, w: Float, top: Float, bottom: Float, p: Paint, pos0: Float, pos1: Float, pos2: Float, stride: Float, crouch: Float, right: Float = 0.94f) {
        val l = w * 0.06f
        val r = w * right
        val lh = (bottom - top) / 3f
        p.style = Paint.Style.FILL
        p.color = SprintArt.TRACK
        c.drawRect(l, top, r, bottom, p)
        for (k in 0 until 3) {
            p.color = Theme.PLAYER_COLORS[k]
            p.alpha = 46
            c.drawRect(l, top + lh * k, r, top + lh * (k + 1), p)
            p.alpha = 255
        }
        p.color = 0xCCFFFFFF.toInt()
        p.strokeWidth = lh * 0.05f
        for (k in 0..3) c.drawLine(l, top + lh * k, r, top + lh * k, p)
        val sx = l + w * 0.06f
        val fx = r - w * 0.08f
        p.strokeWidth = lh * 0.1f
        c.drawLine(sx, top, sx, bottom, p)
        SprintArt.checker(c, p, fx, top, bottom, lh * 0.28f, lh * 0.14f)
        for (k in 0 until 3) {
            val pos = when (k) { 0 -> pos0; 1 -> pos1; else -> pos2 }
            val x = sx + (fx - sx) * pos - lh * 0.12f
            SprintArt.runner(c, p, x, top + lh * (k + 1) - lh * 0.08f, lh * 1.25f, Theme.PLAYER_COLORS[k], if (k == 1) -stride else stride, crouch, false, 0f)
        }
    }

    /** 규칙 그림: [왼발] [오른발] 버튼 한 쌍 (b = 버튼 하나 폭) */
    private fun buttons(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, p: Paint, color: Int, downLeft: Boolean, downRight: Boolean, glowLeft: Boolean, glowRight: Boolean) {
        val depth = bh * 0.06f
        for (k in 0..1) {
            val left = k == 0
            val bx = if (left) x else x + bw * 1.18f
            box.set(bx, y, bx + bw, y + bh)
            val down = if (left) downLeft else downRight
            SprintArt.button(c, p, box, color, if (down) 1f else 0f, depth, bh * 0.16f, if (left) glowLeft else glowRight, bh * 0.05f)
            val off = if (down) depth else 0f
            SprintArt.footprint(c, p, bx + bw / 2f, y + off + bh * 0.36f, bh * 0.44f, left, 0xE6FFFFFF.toInt())
            text.textSize = bh * 0.19f
            text.color = Theme.TEXT
            c.drawText(if (left) "왼발" else "오른발", bx + bw / 2f, y + off + bh * 0.84f, text)
        }
    }

    /** 손가락 탭 표시 */
    private fun tap(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.14f
        p.color = Theme.GOLD
        c.drawCircle(x, y, s, p)
        p.alpha = 110
        c.drawCircle(x, y, s * 1.5f, p)
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    /** 말풍선 글자 */
    private fun tag(c: Canvas, cx: Float, cy: Float, h: Float, label: String, fill: Int, stroke: Int, ink: Int, p: Paint) {
        text.textSize = h * 0.6f
        val hw = text.measureText(label) / 2f + h * 0.5f
        box.set(cx - hw, cy - h / 2f, cx + hw, cy + h / 2f)
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawRoundRect(box, h / 2f, h / 2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.08f
        p.color = stroke
        c.drawRoundRect(box, h / 2f, h / 2f, p)
        p.style = Paint.Style.FILL
        text.color = ink
        c.drawText(label, cx, cy + text.textSize * 0.36f, text)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("왼발·오른발 번갈아!", "자기 구역의 [왼발] [오른발]을 번갈아 누를 때마다 내 선수가 한 걸음!\n같은 발을 연속으로 누르면 무효예요 (\"같은 발!\"). 60걸음이면 100 m 도착.") { c, w, h, p ->
            lanes(c, w, h * 0.06f, h * 0.5f, p, 0.42f, 0.3f, 0.55f, 0.9f, 0f)
            val bh = h * 0.36f
            val bw = bh * 1.15f
            val x = w / 2f - bw * 1.09f
            val y = h * 0.58f
            buttons(c, x, y, bw, bh, p, Theme.PLAYER_COLORS[0], true, false, false, true)
            tap(c, x + bw * 0.5f, y + bh * 0.38f, bh * 0.16f, p)
            // 다음 발 화살표
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.02f
            p.strokeCap = Paint.Cap.ROUND
            p.color = Theme.GOLD
            val ay = y - h * 0.035f
            c.drawLine(x + bw * 0.7f, ay, x + bw * 1.48f, ay, p)
            c.drawLine(x + bw * 1.48f, ay, x + bw * 1.4f, ay - h * 0.025f, p)
            c.drawLine(x + bw * 1.48f, ay, x + bw * 1.4f, ay + h * 0.025f, p)
            p.style = Paint.Style.FILL
        },
        RuleCard("\"땅!\" 전에 누르면 부정 출발", "\"준비… 차렷…\" 뒤 \"땅!\" 신호가 나오는 때는 매번 달라요.\n신호 전에 누르면 부정 출발 — 땅! 부터 1초 동안 못 움직여요.") { c, w, h, p ->
            lanes(c, w, h * 0.22f, h * 0.62f, p, 0f, 0f, 0f, 0f, 1f)
            // 신호등 (빨강 2개)
            val lr = h * 0.045f
            for (k in 0 until 3) SprintArt.lamp(c, p, w / 2f + (k - 1) * lr * 3f, h * 0.1f, lr, Theme.RED, k < 2)
            // 부정 출발한 선수 (가운데 레인) 위 "!"
            val lh = h * 0.4f / 3f
            val sx = w * 0.12f
            p.color = Theme.RED
            c.drawCircle(sx + lh * 0.45f, h * 0.22f + lh * 0.75f, lh * 0.32f, p)
            text.textSize = lh * 0.48f
            text.color = Theme.TEXT
            c.drawText("!", sx + lh * 0.45f, h * 0.22f + lh * 0.75f + text.textSize * 0.36f, text)
            val bh = h * 0.24f
            val bw = bh * 1.15f
            buttons(c, w * 0.08f, h * 0.71f, bw, bh, p, Theme.GRAY, true, false, false, false)
            tap(c, w * 0.08f + bw * 0.5f, h * 0.71f + bh * 0.38f, bh * 0.16f, p)
            tag(c, w * 0.74f, h * 0.83f, h * 0.14f, "1초 정지!", Theme.RED_DARK, Theme.RED, Theme.TEXT, p)
        },
        RuleCard("도착한 순서대로 순위", "결승선을 먼저 넘은 순서대로 1위·2위… 기록(초)도 보여 줘요. 같은 1/1000초면 같은 순위.\n40초 안에 못 들어오면 달린 거리가 긴 순서로 그 뒤 순위.") { c, w, h, p ->
            val top = h * 0.1f
            val lh = h * 0.8f / 3f
            lanes(c, w, top, top + lh * 3f, p, 1f, 0.82f, 1f, 0.6f, 0f, 0.64f)
            // 레인 오른쪽: 순위 배지 + 기록 (게임 화면과 같은 모양)
            for (k in 0 until 3) {
                val cy = top + lh * (k + 0.5f)
                val br = lh * 0.2f
                val bx = w * 0.7f
                val place = when (k) { 0 -> "1"; 1 -> "3"; else -> "2" }
                val label = when (k) { 0 -> "9.87초"; 1 -> "82m"; else -> "10.12초" }
                val gold = k == 0
                p.style = Paint.Style.FILL
                p.color = if (gold) Theme.GOLD else Theme.CARD_LIGHT
                c.drawCircle(bx, cy, br, p)
                text.textSize = br * 1.15f
                text.color = if (gold) Theme.INK else Theme.TEXT
                c.drawText(place, bx, cy + text.textSize * 0.36f, text)
                text.textAlign = Paint.Align.LEFT
                text.textSize = lh * 0.26f
                text.color = if (gold) Theme.GOLD_LIGHT else Theme.TEXT
                c.drawText(label, bx + br * 1.5f, cy + text.textSize * 0.36f, text)
                text.textAlign = Paint.Align.CENTER
            }
        },
    )
}
