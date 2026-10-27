package com.siwoo.party.games.bowling

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bowling.ui.BowlingActivity
import com.siwoo.party.games.bowling.ui.BowlingArt
import com.siwoo.party.games.bowling.ui.BowlingSettings

/** 볼링: 모음집 등록 정보 (2~4인, 차례대로) */
object BowlingModule : GameModule {
    override val id = "bowling"
    override val name = "볼링"
    override val tagline = "쓱 밀어 굴려서 스트라이크!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "10분"
    override val color = 0xFFB5562A.toInt()
    override val activityClass = BowlingActivity::class.java

    /** 허브 소개 팝업의 [프레임 수] → 자동 / 10 / 5 (SharedPreferences "bowling") */
    override val settingsLabel: String get() = "프레임 수"

    override fun showSettings(popups: PopupLayer) {
        BowlingSettings.show(popups)
    }

    private val path = Path()
    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val ROW = intArrayOf(0, 1, 1, 2, 2, 2, 3, 3, 3, 3)
    private val COL = floatArrayOf(0f, -0.5f, 0.5f, -1f, 0f, 1f, -1.5f, -0.5f, 0.5f, 1.5f)

    /** 핀 삼각형 (머리 핀이 (x, cy), 오른쪽으로 줄이 늘어남). skip 비트가 켜진 핀은 회색으로 누움 */
    private fun rack(c: Canvas, p: Paint, x: Float, cy: Float, s: Float, pr: Float, fallen: Int) {
        for (i in 0 until 10) {
            val px = x + ROW[i] * s * 0.87f
            val py = cy + COL[i] * s
            if (fallen and (1 shl i) != 0) BowlingArt.fallenPin(c, p, px + s * 0.3f, py, pr, (i * 0.9f) - 1.2f)
            else BowlingArt.pin(c, p, px, py, pr)
        }
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF2B3C8F.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        // 레인 (가로 나무 띠 + 위아래 거터)
        canvas.save()
        path.reset()
        path.addCircle(cx, cy, r * 0.93f, Path.Direction.CW)
        canvas.clipPath(path)
        val lt = cy - r * 0.42f
        val lb = cy + r * 0.42f
        BowlingArt.gutter(canvas, paint, cx - r, lt - r * 0.16f, cx + r, lt)
        BowlingArt.gutter(canvas, paint, cx - r, lb, cx + r, lb + r * 0.16f)
        BowlingArt.woodStrip(canvas, paint, cx - r, lt, cx + r, lb, 8)
        canvas.restore()
        // 핀 삼각형 + 굴러오는 공
        val s = r * 0.19f
        rack(canvas, paint, cx + r * 0.08f, cy, s, s * 0.42f, 0)
        BowlingArt.ball(canvas, paint, cx - r * 0.5f, cy + r * 0.08f, r * 0.24f, RED, Theme.RED_DARK, 0.6f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 공통: 가로 레인 (위아래 거터) */
    private fun laneArt(c: Canvas, w: Float, h: Float, p: Paint): Float {
        val gw = h * 0.1f
        val top = h * 0.16f
        val bottom = h * 0.84f
        BowlingArt.gutter(c, p, 0f, top, w, top + gw)
        BowlingArt.gutter(c, p, 0f, bottom - gw, w, bottom)
        BowlingArt.woodStrip(c, p, 0f, top + gw, w, bottom - gw, 12)
        return (bottom - gw) - (top + gw)
    }

    /** 외곽선 있는 굵은 글자 */
    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF2A1A08.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 점수 칸 하나 (위 작은 칸 글자 둘 + 아래 누적) */
    private fun box(c: Canvas, p: Paint, x: Float, y: Float, bw: Float, a: String, b: String, total: String) {
        p.style = Paint.Style.FILL
        p.color = Theme.CARD
        c.drawRect(x, y, x + bw, y + bw, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = bw * 0.03f
        p.color = 0xAAFFFFFF.toInt()
        c.drawRect(x, y, x + bw, y + bw, p)
        val s = bw * 0.4f
        c.drawRect(x + bw - s * 2f, y, x + bw - s, y + s, p)
        c.drawRect(x + bw - s, y, x + bw, y + s, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = s * 0.8f
        p.color = Theme.TEXT
        c.drawText(a, x + bw - s * 1.5f, y + s * 0.8f, p)
        p.color = if (b == "X" || b == "/") Theme.GOLD else Theme.TEXT
        c.drawText(b, x + bw - s * 0.5f, y + s * 0.8f, p)
        p.textSize = bw * 0.36f
        p.color = Theme.TEXT
        c.drawText(total, x + bw / 2f, y + bw * 0.86f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("공을 잡고 쓱 밀기", "내 차례에 레인 왼쪽(어프로치)에서\n공을 누르고 오른쪽으로 쓱 밀었다 떼요.\n빠르게 밀수록 세게, 비스듬히 밀면 비스듬히!\n위아래로 옮겨 굴릴 자리를 정해요.") { c, w, h, p ->
            val lh = laneArt(c, w, h, p)
            // 파울 라인
            p.style = Paint.Style.FILL
            p.color = 0xFFB71C2C.toInt()
            c.drawRect(w * 0.34f, h * 0.16f, w * 0.34f + h * 0.025f, h * 0.84f, p)
            val by = h * 0.55f
            val br = lh * 0.15f
            // 손가락 스와이프 자국
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = br * 0.5f
            p.color = 0x88FFFFFF.toInt()
            c.drawLine(w * 0.06f, by + br * 0.4f, w * 0.27f, by, p)
            p.strokeCap = Paint.Cap.BUTT
            BowlingArt.ball(c, p, w * 0.27f, by, br, RED, Theme.RED_DARK, 0.5f)
            // 손가락
            p.style = Paint.Style.FILL
            p.color = 0xFFFFE0C8.toInt()
            c.drawCircle(w * 0.27f, by + br * 0.2f, br * 0.45f, p)
            // 굴러갈 방향 점선
            p.color = 0xEEFFFFFF.toInt()
            var x = w * 0.36f
            while (x < w * 0.66f) { c.drawCircle(x, by - (x - w * 0.36f) * 0.05f, br * 0.14f, p); x += br * 0.8f }
            p.color = 0xEEFFFFFF.toInt()
            BowlingArt.arrow(c, p, w * 0.66f, by - w * 0.3f * 0.05f, br * 0.8f, br * 0.4f, path)
            rack(c, p, w * 0.72f, h * 0.5f, lh * 0.19f, lh * 0.068f, 0)
        },
        RuleCard("휘게 밀면 훅!", "손가락을 활처럼 휘게 밀면\n공이 끝에서 휘어져 들어가요.\n공이 옆 홈(거터)에 빠지면 핀을 못 맞혀요.\n쓰러진 핀은 회색으로 누웠다가 치워져요.") { c, w, h, p ->
            val lh = laneArt(c, w, h, p)
            val br = lh * 0.13f
            // 휘는 경로 (점)
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            for (k in 0..11) {
                val f = k / 11f
                c.drawCircle(w * (0.08f + 0.56f * f), h * 0.66f - h * 0.2f * f * f, br * (0.12f + 0.06f * f), p)
            }
            BowlingArt.ball(c, p, w * 0.08f, h * 0.66f, br, BLUE, Theme.shade(BLUE, 0.55f), 1.4f)
            // 쓰러지는 핀
            rack(c, p, w * 0.68f, h * 0.5f, lh * 0.19f, lh * 0.068f, 0b0000110111)
            // 거터에 빠진 공 (흐리게)
            BowlingArt.ball(c, p, w * 0.42f, h * 0.84f - h * 0.05f, br * 0.6f, RED, Theme.RED_DARK, 0.2f)
            shout(c, p, "거터…", w * 0.42f, h * 0.98f, h * 0.12f, 0xFFFF8A95.toInt())
        },
        RuleCard("스트라이크·스페어", "한 프레임에 두 번 굴려요.\n스트라이크(X): 다음 두 공 점수 더하기\n스페어(/): 다음 한 공 점수 더하기\n마지막 프레임은 보너스 투구! 합계 순위") { c, w, h, p ->
            val bw = minOf(w * 0.2f, h * 0.48f)
            val y = h * 0.42f
            val x0 = (w - bw * 3.3f) / 2f
            box(c, p, x0, y, bw, "", "X", "20")
            box(c, p, x0 + bw * 1.15f, y, bw, "7", "/", "34")
            box(c, p, x0 + bw * 2.3f, y, bw, "4", "2", "40")
            shout(c, p, "스트라이크!", w * 0.5f, h * 0.3f, h * 0.17f, Theme.GOLD)
        },
    )
}
