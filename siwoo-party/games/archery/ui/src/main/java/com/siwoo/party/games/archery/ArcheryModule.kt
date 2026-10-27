package com.siwoo.party.games.archery

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.archery.ui.ArcheryActivity
import com.siwoo.party.games.archery.ui.ArcheryArt
import kotlin.math.PI
import kotlin.math.sin

/** 양궁: 모음집 등록 정보 (2~4인, 차례대로) */
object ArcheryModule : GameModule {
    override val id = "archery"
    override val name = "양궁"
    override val tagline = "숨을 참고 바람을 읽어 과녁 정중앙에 — 엑스 텐!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.TIMING
    override val minutes = "6분"
    override val color = 0xFF1E88A8.toInt()
    override val activityClass = ArcheryActivity::class.java

    private const val SKY = 0xFF8CC8EE.toInt()
    private const val GRASS = 0xFF3E8E41.toInt()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.36f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.07f, cy + r * 0.1f, r * 1.04f, paint)
        ArcheryArt.drawFace(canvas, paint, cx, cy, r)
        // 정중앙에 꽂힌 화살 (오른쪽 위에서 비스듬히)
        val ang = (PI * 0.78).toFloat()
        ArcheryArt.drawArrowSide(canvas, paint, cx + r * 0.02f, cy - r * 0.02f, r * 1.25f, ang, Theme.RED)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** 규칙 그림 바탕: 하늘 + 잔디 */
    private fun range(c: Canvas, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = SKY
        c.drawRect(0f, 0f, w, h * 0.45f, p)
        p.color = GRASS
        c.drawRect(0f, h * 0.45f, w, h, p)
    }

    private fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF14203A.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 가로 8자 궤적 (점선, 할당 없음) */
    private fun figure8(c: Canvas, p: Paint, cx: Float, cy: Float, ax: Float, ay: Float, color: Int, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        val n = 48
        for (k in 0 until n step 2) {
            val t0 = 2.0 * PI * k / n
            val t1 = 2.0 * PI * (k + 1) / n
            c.drawLine(cx + (sin(t0) * ax).toFloat(), cy + (sin(2 * t0) * ay).toFloat(), cx + (sin(t1) * ax).toFloat(), cy + (sin(2 * t1) * ay).toFloat(), p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 십자 조준점 */
    private fun reticle(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.STROKE
        for (pass in 0..1) {
            p.color = if (pass == 0) 0x99000000.toInt() else color
            p.strokeWidth = if (pass == 0) r * 0.3f else r * 0.15f
            c.drawCircle(x, y, r, p)
            c.drawLine(x - r * 1.4f, y, x - r * 0.5f, y, p)
            c.drawLine(x + r * 0.5f, y, x + r * 1.4f, y, p)
            c.drawLine(x, y - r * 1.4f, x, y - r * 0.5f, p)
            c.drawLine(x, y + r * 0.5f, x, y + r * 1.4f, p)
        }
        p.style = Paint.Style.FILL
        p.color = Theme.RED
        c.drawCircle(x, y, r * 0.18f, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("길게 눌러 숨 참기", "조준점이 8자로 흔들려요.\n화면을 누르고 있으면 숨을 참아 흔들림이 줄어요 (약 1초).\n2초가 넘으면 숨이 차서 오히려 더 흔들려요!\n손을 떼는 순간 화살이 날아가요.") { c, w, h, p ->
            range(c, w, h, p)
            val tr = h * 0.4f
            val tx = w * 0.4f
            val ty = h * 0.48f
            ArcheryArt.drawFace(c, p, tx, ty, tr)
            figure8(c, p, tx, ty, tr * 0.75f, tr * 0.42f, 0xCCFFFFFF.toInt(), h * 0.015f)
            figure8(c, p, tx, ty, tr * 0.16f, tr * 0.09f, Theme.GOLD, h * 0.018f)
            reticle(c, p, tx + tr * 0.1f, ty - tr * 0.06f, h * 0.06f, 0xFFFFFFFF.toInt())
            // 숨 막대 (오른쪽, 세로)
            val bl = w * 0.8f
            val br = w * 0.88f
            val bt = h * 0.14f
            val bb = h * 0.86f
            p.color = 0x881A1030.toInt()
            c.drawRoundRect(bl, bt, br, bb, w * 0.02f, w * 0.02f, p)
            p.color = Theme.GOLD
            c.drawRoundRect(bl + w * 0.008f, bt + (bb - bt) * 0.45f, br - w * 0.008f, bb - w * 0.008f, w * 0.015f, w * 0.015f, p)
            shout(c, p, "숨", (bl + br) / 2f, bt - h * 0.02f, h * 0.1f, Theme.GOLD)
        },
        RuleCard("바람을 읽어요", "화살마다 바람이 바뀌어요 (깃발·화살표).\n화살은 바람이 부는 쪽으로 밀려요.\n누른 채로 끌어서 바람 반대쪽으로\n조준을 옮긴 뒤 떼세요.") { c, w, h, p ->
            range(c, w, h, p)
            val tr = h * 0.36f
            val tx = w * 0.46f
            val ty = h * 0.5f
            ArcheryArt.drawFace(c, p, tx, ty, tr)
            ArcheryArt.drawFlag(c, p, w * 0.86f, h * 0.88f, h * 0.7f, 0, 0.9f, 0.6f)
            // 바람 화살표 (위)
            p.color = 0x99101840.toInt()
            c.drawRoundRect(w * 0.04f, h * 0.06f, w * 0.3f, h * 0.3f, h * 0.05f, h * 0.05f, p)
            ArcheryArt.drawWindArrow(c, p, w * 0.17f, h * 0.18f, h * 0.17f, 0, Theme.GOLD)
            // 바람 반대쪽(왼쪽)을 조준 → 가운데에 꽂힘
            val ax = tx - tr * 0.42f
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.012f
            p.color = 0xEEFFFFFF.toInt()
            var x = ax + h * 0.06f
            while (x < tx - h * 0.04f) {
                c.drawLine(x, ty, minOf(x + h * 0.025f, tx - h * 0.04f), ty, p)
                x += h * 0.045f
            }
            p.style = Paint.Style.FILL
            reticle(c, p, ax, ty, h * 0.05f, 0xFFFFFFFF.toInt())
            ArcheryArt.drawNock(c, p, tx, ty, h * 0.05f, Theme.BLUE)
        },
        RuleCard("6발 합계 · 엑스 텐!", "한 사람씩 돌아가며 1발씩, 모두 " + GameConfig.ARROWS + "발.\n금색 10·9, 빨강 8·7, 파랑 6·5, 검정 4·3, 흰색 2·1, 밖은 0점.\n정중앙 X 링은 10점 + \"엑스 텐!\"\n합계가 같으면 X 개수 → 10점 개수로 순위.") { c, w, h, p ->
            range(c, w, h, p)
            val tr = h * 0.42f
            val tx = w * 0.32f
            val ty = h * 0.5f
            ArcheryArt.drawFace(c, p, tx, ty, tr)
            ArcheryArt.drawNock(c, p, tx + tr * 0.02f, ty + tr * 0.01f, h * 0.05f, Theme.RED)
            ArcheryArt.drawNock(c, p, tx + tr * 0.36f, ty - tr * 0.22f, h * 0.045f, Theme.RED)
            ArcheryArt.drawNock(c, p, tx - tr * 0.5f, ty + tr * 0.3f, h * 0.045f, Theme.RED)
            shout(c, p, "엑스 텐!", w * 0.74f, h * 0.3f, h * 0.15f, Theme.GOLD)
            // 점수 배지
            p.color = ArcheryArt.GOLD
            c.drawCircle(w * 0.74f, h * 0.64f, h * 0.15f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.015f
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(w * 0.74f, h * 0.64f, h * 0.15f, p)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = h * 0.16f
            p.color = ArcheryArt.BLACK
            c.drawText("X", w * 0.74f, h * 0.64f + h * 0.055f, p)
            p.textAlign = Paint.Align.LEFT
            p.typeface = Typeface.DEFAULT
        },
    )
}
