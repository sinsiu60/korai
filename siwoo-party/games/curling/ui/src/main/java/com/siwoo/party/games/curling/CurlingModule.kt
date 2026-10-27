package com.siwoo.party.games.curling

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.curling.ui.CurlArt
import com.siwoo.party.games.curling.ui.CurlingActivity

/** 컬링: 모음집 등록 정보 (2~4인, 차례대로, 4인은 2 대 2 팀) */
object CurlingModule : GameModule {
    override val id = "curling"
    override val name = "컬링"
    override val tagline = "끌어서 던지고 연타로 스위핑, 하우스 중심을 노려라!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "10분"
    override val color = 0xFF1E88C8.toInt()
    override val activityClass = CurlingActivity::class.java

    private val RED = Theme.PLAYER_COLORS[0]
    private val BLUE = Theme.PLAYER_COLORS[1]
    private val YELLOW = Theme.PLAYER_COLORS[3]
    private val path = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = CurlArt.ICE
        canvas.drawCircle(cx, cy, r, paint)
        canvas.save()
        path.reset()
        path.addCircle(cx, cy, r * 0.97f, Path.Direction.CW)
        canvas.clipPath(path)
        // 하우스 (오른쪽 위)
        CurlArt.house(canvas, paint, cx + r * 0.32f, cy - r * 0.28f, r * 0.62f)
        // 미끄러지는 자국 (휘는 곡선)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.07f
        paint.color = 0x553A6EA8
        path.reset()
        path.moveTo(cx - r * 1.1f, cy + r * 0.55f)
        path.quadTo(cx - r * 0.5f, cy + r * 0.6f, cx - r * 0.25f, cy + r * 0.32f)
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
        canvas.restore()
        CurlArt.stone(canvas, paint, cx + r * 0.38f, cy - r * 0.2f, r * 0.2f, BLUE, 0.3f)
        CurlArt.stone(canvas, paint, cx - r * 0.18f, cy + r * 0.24f, r * 0.27f, RED, -0.5f)
        // 빗자루 (스톤 앞)
        CurlArt.broom(canvas, paint, cx + r * 0.2f, cy + r * 0.2f, r * 0.32f, 0.5f, 0.75f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.96f, paint)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 공통: 카드 안 얼음 띠 (위아래 여백) — 띠 높이를 돌려줌 */
    private fun sheet(c: Canvas, w: Float, h: Float, p: Paint): Float {
        CurlArt.ice(c, p, -w * 0.05f, h * 0.1f, w * 1.05f, h * 0.86f)
        return h * 0.76f
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
        RuleCard("뒤로 끌었다 놓아 던지기", "스톤을 뒤로 끌었다 놓으면\n하우스 쪽으로 미끄러져요.\n많이 끌수록 세게! 던지기 전에\n↺ 왼쪽 · ↻ 오른쪽 휘는 방향 고르기\n(느려질수록 많이 휘어요)") { c, w, h, p ->
            val sh = sheet(c, w, h, p)
            val mid = h * 0.48f
            val sr = sh * 0.11f
            CurlArt.house(c, p, w * 0.86f, mid, sh * 0.42f)
            val sx = w * 0.22f
            // 고무줄 + 손가락
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = sr * 0.28f
            p.color = Theme.GOLD_DARK
            c.drawLine(sx, mid, sx - w * 0.13f, mid + sh * 0.12f, p)
            p.strokeCap = Paint.Cap.BUTT
            // 휘는 길 (점선 → 화살표)
            p.style = Paint.Style.FILL
            p.color = 0xCC1A2A5A.toInt()
            var t = 0.1f
            while (t < 0.62f) {
                val x = sx + w * t
                val y = mid - sh * 0.02f * (t / 0.62f) * (t / 0.62f) * 8f
                c.drawCircle(x, y, sr * 0.17f, p)
                t += 0.05f
            }
            CurlArt.curlArrow(c, p, sx + w * 0.55f, mid - sh * 0.1f, w * 0.1f, sh * 0.1f, -1, sr * 0.22f, Theme.GOLD_DARK)
            CurlArt.stone(c, p, sx, mid, sr, RED, 0f)
            finger(c, p, sx - w * 0.13f, mid + sh * 0.12f, sr * 0.62f)
            // 휘는 방향 버튼 그림
            val by = h * 0.22f
            p.color = Theme.GOLD
            c.drawRoundRect(w * 0.06f, by - sh * 0.1f, w * 0.2f, by + sh * 0.1f, sh * 0.05f, sh * 0.05f, p)
            CurlArt.spinIcon(c, p, w * 0.13f, by + sh * 0.01f, sh * 0.055f, -1, Theme.INK)
            p.color = Theme.CARD_LIGHT
            c.drawRoundRect(w * 0.22f, by - sh * 0.1f, w * 0.36f, by + sh * 0.1f, sh * 0.05f, sh * 0.05f, p)
            CurlArt.spinIcon(c, p, w * 0.29f, by + sh * 0.01f, sh * 0.055f, 1, Theme.TEXT)
        },
        RuleCard("화면 연타로 스위핑!", "스톤이 움직이는 동안 화면을 연타!\n스위핑하면 더 멀리, 덜 휘어요.\n(팀전은 팀원도 함께 연타)\n빨간 호그 라인을 못 넘거나\n밖으로 나간 스톤은 빠져요.") { c, w, h, p ->
            val sh = sheet(c, w, h, p)
            val mid = h * 0.5f
            val sr = sh * 0.12f
            // 호그 라인
            p.style = Paint.Style.FILL
            p.color = CurlArt.HOG
            c.drawRect(w * 0.18f, h * 0.1f, w * 0.18f + sh * 0.03f, h * 0.86f, p)
            // 움직이는 스톤 + 자국 + 빗자루 둘
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = sr * 0.25f
            p.color = 0x553A6EA8
            c.drawLine(w * 0.05f, mid, w * 0.42f, mid, p)
            p.strokeCap = Paint.Cap.BUTT
            val sx = w * 0.46f
            CurlArt.stone(c, p, sx, mid, sr, BLUE, 0.4f)
            CurlArt.broom(c, p, sx + sr * 2.1f, mid - sr * 0.6f, sr * 1.3f, -0.6f, -0.8f)
            CurlArt.broom(c, p, sx + sr * 2.9f, mid + sr * 0.6f, sr * 1.3f, -0.5f, 0.85f)
            // 톡톡 (손가락 + 물결)
            val fx = w * 0.8f
            val fy = h * 0.32f
            p.style = Paint.Style.STROKE
            p.strokeWidth = sr * 0.14f
            p.color = Theme.GOLD
            c.drawCircle(fx, fy, sr * 0.95f, p)
            c.drawCircle(fx, fy, sr * 1.4f, p)
            finger(c, p, fx, fy, sr * 0.55f)
            shout(c, p, "톡톡!", w * 0.8f, h * 0.76f, h * 0.15f, Theme.GOLD)
        },
        RuleCard("엔드 득점 · 3엔드 합계", "엔드가 끝나면 중심에 가장 가까운\n스톤의 주인이, 상대 가장 가까운\n스톤보다 안쪽 스톤 수만큼 득점!\n3엔드 합계로 순위 (4인은 2 대 2 팀)") { c, w, h, p ->
            val sh = sheet(c, w, h, p)
            val cx = w * 0.36f
            val cy = h * 0.48f
            val hr = sh * 0.46f
            CurlArt.house(c, p, cx, cy, hr)
            val sr = hr * 0.16f
            // 상대 가장 가까운 스톤까지 점선 원
            p.style = Paint.Style.STROKE
            p.strokeWidth = sr * 0.14f
            p.color = 0xEE20305A.toInt()
            val rr = hr * 0.62f
            var a = 0f
            while (a < 360f) {
                c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, a, 9f, false, p)
                a += 18f
            }
            CurlArt.stone(c, p, cx + rr * 0.6f, cy + rr * 0.8f, sr, YELLOW, 0.2f)
            CurlArt.stone(c, p, cx - hr * 0.15f, cy - hr * 0.1f, sr, RED, -0.3f)
            CurlArt.stone(c, p, cx + hr * 0.25f, cy - hr * 0.35f, sr, RED, 0.5f)
            CurlArt.stone(c, p, cx - hr * 0.85f, cy + hr * 0.3f, sr, RED, 0.1f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = sr * 0.18f
            p.color = Theme.GOLD
            c.drawCircle(cx - hr * 0.15f, cy - hr * 0.1f, sr * 1.3f, p)
            c.drawCircle(cx + hr * 0.25f, cy - hr * 0.35f, sr * 1.3f, p)
            shout(c, p, "2점!", w * 0.8f, h * 0.58f, h * 0.2f, Theme.GOLD)
            p.style = Paint.Style.FILL
        },
    )
}
