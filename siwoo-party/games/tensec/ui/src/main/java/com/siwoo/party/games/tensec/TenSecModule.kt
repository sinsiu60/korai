package com.siwoo.party.games.tensec

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
import com.siwoo.party.games.tensec.ui.TenSecActivity

/** 딱 10초: 모음집 등록 정보 */
object TenSecModule : GameModule {
    override val id = "tensec"
    override val name = "딱 10초"
    override val tagline = "타이머가 사라져도 그 순간을 딱 맞춰 터치!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.SIMULTANEOUS
    override val category = GameCategory.REFLEX
    override val minutes = "3분"
    override val color = 0xFF7E57C2.toInt()
    override val activityClass = TenSecActivity::class.java

    private val rect = RectF()
    private val path = Path()
    private const val PANEL = 0xFF0B1238.toInt()
    private const val LCD = 0xFF7CFFB2.toInt()
    private val MONO_BOLD: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val DOT_X = floatArrayOf(0.18f, 0.39f, 0.64f, 0.84f)

    /** 초시계 (cx, cy 중심, 반지름 r). handDeg = 바늘 각도(12시 = 0) */
    private fun stopwatch(c: Canvas, cx: Float, cy: Float, r: Float, handDeg: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, p)
        // 꼭지 단추
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(cx - r * 0.16f, cy - r * 1.28f, cx + r * 0.16f, cy - r * 0.9f, r * 0.06f, r * 0.06f, p)
        p.color = Theme.GOLD
        c.drawRoundRect(cx - r * 0.3f, cy - r * 1.4f, cx + r * 0.3f, cy - r * 1.2f, r * 0.08f, r * 0.08f, p)
        c.drawCircle(cx, cy, r, p)
        p.color = Theme.PAPER
        c.drawCircle(cx, cy, r * 0.84f, p)
        // 눈금 12개
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = Theme.INK
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0)
            val s = kotlin.math.sin(a).toFloat()
            val co = kotlin.math.cos(a).toFloat()
            val r0 = if (k % 3 == 0) r * 0.6f else r * 0.68f
            p.strokeWidth = if (k % 3 == 0) r * 0.07f else r * 0.04f
            c.drawLine(cx + s * r0, cy - co * r0, cx + s * r * 0.76f, cy - co * r * 0.76f, p)
        }
        // 지나간 부분 (빨간 부채꼴)
        p.style = Paint.Style.FILL
        p.color = 0x55FF4D5E
        rect.set(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f)
        c.drawArc(rect, -90f, handDeg, true, p)
        // 바늘
        val a = Math.toRadians(handDeg.toDouble())
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.08f
        p.color = Theme.RED
        c.drawLine(cx, cy, cx + kotlin.math.sin(a).toFloat() * r * 0.62f, cy - kotlin.math.cos(a).toFloat() * r * 0.62f, p)
        p.style = Paint.Style.FILL
        p.color = Theme.INK
        c.drawCircle(cx, cy, r * 0.09f, p)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.33f
        stopwatch(canvas, cx, cy + size * 0.06f, r, 300f, paint)
        // "10" 말풍선 대신 금색 숫자 딱지
        paint.style = Paint.Style.FILL
        paint.color = Theme.RED
        val bx = cx + r * 0.85f
        val by = cy - r * 0.75f
        canvas.drawCircle(bx, by, r * 0.42f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(bx, by, r * 0.42f, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r * 0.48f
        canvas.drawText("10", bx, by + paint.textSize * 0.36f, paint)
        paint.typeface = null
        paint.textAlign = Paint.Align.LEFT
    }

    /** 규칙 그림: 어두운 패널 + 글자 */
    private fun panel(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, border: Int, text: String, textColor: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(cx - w / 2f + h * 0.05f, cy - h / 2f + h * 0.08f, cx + w / 2f + h * 0.05f, cy + h / 2f + h * 0.08f, h * 0.18f, h * 0.18f, p)
        p.color = PANEL
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h * 0.18f, h * 0.18f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.05f
        p.color = border
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h * 0.18f, h * 0.18f, p)
        p.style = Paint.Style.FILL
        p.color = textColor
        p.typeface = MONO_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = h * 0.5f
        val tw = p.measureText(text)
        if (tw > w * 0.8f) p.textSize *= w * 0.8f / tw
        c.drawText(text, cx, cy + p.textSize * 0.36f, p)
        p.typeface = null
        p.textAlign = Paint.Align.LEFT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("목표 시간 공개", "라운드마다 5~15초 사이 목표 시간이 나와요.\n3·2·1 뒤 모든 구역의 타이머가 함께 시작돼요.") { c, w, h, p ->
            stopwatch(c, w * 0.32f, h * 0.56f, h * 0.3f, 210f, p)
            panel(c, w * 0.7f, h * 0.5f, w * 0.34f, h * 0.36f, Theme.GOLD, "7초", Theme.GOLD, p)
        },
        RuleCard("3초 뒤 타이머가 사라져요", "처음 3초만 숫자가 보이고 그 뒤로는 마음속으로 세기!\n목표 시간이 됐다고 생각할 때 자기 구역을 한 번 터치.") { c, w, h, p ->
            panel(c, w * 0.26f, h * 0.5f, w * 0.32f, h * 0.36f, Theme.PLAYER_COLORS[1], "2.47", LCD, p)
            // 화살표
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            path.reset()
            path.moveTo(w * 0.47f, h * 0.43f)
            path.lineTo(w * 0.54f, h * 0.5f)
            path.lineTo(w * 0.47f, h * 0.57f)
            path.close()
            c.drawPath(path, p)
            panel(c, w * 0.74f, h * 0.5f, w * 0.32f, h * 0.36f, Theme.PLAYER_COLORS[1], "? ? ?", Theme.TEXT_SUB, p)
            // 터치 물결
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.025f
            for (k in 1..3) {
                p.color = Theme.GOLD
                p.alpha = 255 - k * 60
                c.drawCircle(w * 0.86f, h * 0.78f, h * 0.045f * k, p)
            }
            p.alpha = 255
            p.style = Paint.Style.FILL
            c.drawCircle(w * 0.86f, h * 0.78f, h * 0.035f, p)
        },
        RuleCard("가장 가까운 사람이 최고", "모두 누르면 차이가 큰 사람부터 한 명씩 기록 공개!\n3라운드 차이 합이 작은 순으로 순위 (같으면 공동). 끝까지 안 누르면 목표+10초로 기록.") { c, w, h, p ->
            val y = h * 0.62f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = h * 0.025f
            p.color = Theme.TEXT_SUB
            c.drawLine(w * 0.1f, y, w * 0.9f, y, p)
            p.color = Theme.GOLD
            p.strokeWidth = h * 0.035f
            c.drawLine(w * 0.5f, y - h * 0.22f, w * 0.5f, y + h * 0.14f, p)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = h * 0.11f
            c.drawText("목표", w * 0.5f, y + h * 0.28f, p)
            for (k in 0 until 4) {
                p.color = Theme.PLAYER_COLORS[k]
                c.drawCircle(w * DOT_X[k], y, h * 0.06f, p)
            }
            p.color = Theme.GOLD
            Ui.drawCrown(c, w * DOT_X[1], y - h * 0.1f, h * 0.16f, path, p)
            p.typeface = null
            p.textAlign = Paint.Align.LEFT
        },
    )
}
