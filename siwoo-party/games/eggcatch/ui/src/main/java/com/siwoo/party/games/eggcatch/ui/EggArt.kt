package com.siwoo.party.games.eggcatch.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/**
 * 계란 받기 그림 도구 (게임 화면·규칙 카드·아이콘 공용). 모두 Canvas 도형, 할당 없음 (Path·RectF 는 여기 필드 하나를 재사용 — UI 스레드 전용).
 * 크기 인자는 px. alpha 는 0..255.
 */
object EggArt {
    const val SHELL = 0xFFFFF4E0.toInt()
    const val SHELL_SHADE = 0xFFE3CBA4.toInt()
    const val SHELL_LINE = 0xFF9C7B52.toInt()
    const val GOLD_SHADE = 0xFFD99A12.toInt()
    const val YOLK = 0xFFFFB020.toInt()
    const val YOLK_GOLD = 0xFFFF7A00.toInt()
    const val BOMB = 0xFF262838.toInt()
    const val BOMB_LIGHT = 0xFF6A6F8C.toInt()
    const val FUSE = 0xFFC9A46A.toInt()
    const val SPARK = 0xFFFF8A1F.toInt()
    const val WICKER = 0xFFB9783C.toInt()
    const val WICKER_DARK = 0xFF7E4E22.toInt()
    const val WICKER_LIGHT = 0xFFE0A866.toInt()
    const val GRASS = 0xFF2F5E2A.toInt()
    const val GRASS_LIGHT = 0xFF63A845.toInt()
    const val FIRE = 0xFFFF6A2B.toInt()
    const val SMOKE = 0xFF8C90A8.toInt()

    private val path = Path()
    private val rect = RectF()

    /** 계란 (가운데 cx, cy, 반 높이 r). gold = 황금 계란 */
    fun egg(c: Canvas, cx: Float, cy: Float, r: Float, gold: Boolean, alpha: Int, p: Paint) {
        p.style = Paint.Style.FILL
        // 그림자 쪽 (오른쪽 아래)
        p.color = if (gold) GOLD_SHADE else SHELL_SHADE
        p.alpha = alpha
        eggShape(c, cx, cy, r, p)
        // 몸통 (살짝 왼쪽 위로 작게)
        p.color = if (gold) Theme.GOLD else SHELL
        p.alpha = alpha
        eggShape(c, cx - r * 0.06f, cy - r * 0.06f, r * 0.9f, p)
        // 반짝
        p.color = if (gold) Theme.GOLD_LIGHT else 0xFFFFFFFF.toInt()
        p.alpha = alpha
        rect.set(cx - r * 0.42f, cy - r * 0.62f, cx - r * 0.14f, cy - r * 0.18f)
        c.drawOval(rect, p)
        // 윤곽
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.08f
        p.color = if (gold) Theme.GOLD_DARK else SHELL_LINE
        p.alpha = alpha * 3 / 4
        eggShape(c, cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.alpha = 255
    }

    /** 계란 모양: 위가 좁고 아래가 둥근 곡선 (가장 넓은 곳 = 가운데보다 조금 아래) */
    private fun eggShape(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val hw = r * 0.76f
        path.reset()
        path.moveTo(cx, cy - r)
        path.cubicTo(cx + r * 0.45f, cy - r, cx + hw, cy - r * 0.38f, cx + hw, cy + r * 0.18f)
        path.cubicTo(cx + hw, cy + r * 0.68f, cx + r * 0.42f, cy + r, cx, cy + r)
        path.cubicTo(cx - r * 0.42f, cy + r, cx - hw, cy + r * 0.68f, cx - hw, cy + r * 0.18f)
        path.cubicTo(cx - hw, cy - r * 0.38f, cx - r * 0.45f, cy - r, cx, cy - r)
        path.close()
        c.drawPath(path, p)
    }

    /** 폭탄 (가운데 cx, cy, 반지름 r). spark = 불꽃 깜빡임 0..1 */
    fun bomb(c: Canvas, cx: Float, cy: Float, r: Float, spark: Float, alpha: Int, p: Paint) {
        p.style = Paint.Style.FILL
        // 심지 (뒤)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.14f
        p.color = FUSE
        p.alpha = alpha
        path.reset()
        path.moveTo(cx + r * 0.35f, cy - r * 0.95f)
        path.quadTo(cx + r * 0.55f, cy - r * 1.45f, cx + r * 0.95f, cy - r * 1.3f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        // 위험 빛 (어두운 레인에서도 보이게)
        p.color = Theme.RED
        p.alpha = alpha * 70 / 255
        c.drawCircle(cx, cy, r * 1.3f, p)
        p.alpha = alpha * 60 / 255
        c.drawCircle(cx, cy, r * 1.14f, p)
        // 몸통
        p.color = BOMB
        p.alpha = alpha
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.1f
        p.color = 0xFFFF8A95.toInt()
        p.alpha = alpha
        c.drawCircle(cx, cy, r * 0.95f, p)
        p.style = Paint.Style.FILL
        // 뚜껑
        p.color = BOMB_LIGHT
        p.alpha = alpha
        c.save()
        c.rotate(30f, cx, cy)
        rect.set(cx - r * 0.3f, cy - r * 1.18f, cx + r * 0.3f, cy - r * 0.82f)
        c.drawRoundRect(rect, r * 0.08f, r * 0.08f, p)
        c.restore()
        // 반짝
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha * 90 / 255
        rect.set(cx - r * 0.6f, cy - r * 0.6f, cx - r * 0.12f, cy - r * 0.2f)
        c.drawOval(rect, p)
        // 불꽃
        val sx = cx + r * 0.95f
        val sy = cy - r * 1.3f
        val sr = r * (0.22f + 0.12f * spark)
        p.color = SPARK
        p.alpha = alpha
        c.drawCircle(sx, sy, sr, p)
        p.color = Theme.GOLD_LIGHT
        p.alpha = alpha
        c.drawCircle(sx, sy, sr * 0.5f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.07f
        p.color = Theme.GOLD
        p.alpha = alpha
        val a = sr * 1.3f
        val b = sr * (1.9f + 0.4f * spark)
        c.drawLine(sx - a, sy, sx - b, sy, p)
        c.drawLine(sx + a, sy, sx + b, sy, p)
        c.drawLine(sx, sy - a, sx, sy - b, p)
        c.drawLine(sx, sy + a, sx, sy + b * 0.7f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.alpha = 255
    }

    /**
     * 바구니: 테두리 위 가운데 (cx, rimY), 위 폭 w, 몸통 높이 h. ribbon = 플레이어 색 띠.
     * soot 0..1 = 폭탄 맞은 그을음.
     */
    fun basket(c: Canvas, cx: Float, rimY: Float, w: Float, h: Float, ribbon: Int, soot: Float, p: Paint) {
        val top = rimY
        val bot = rimY + h
        val tw = w * 0.5f
        val bw = w * 0.4f
        p.style = Paint.Style.FILL
        // 그림자
        p.color = 0x55000000
        rect.set(cx - bw * 1.05f, bot - h * 0.08f, cx + bw * 1.05f, bot + h * 0.12f)
        c.drawOval(rect, p)
        // 몸통 (사다리꼴)
        path.reset()
        path.moveTo(cx - tw, top)
        path.lineTo(cx + tw, top)
        path.lineTo(cx + bw, bot)
        path.quadTo(cx, bot + h * 0.1f, cx - bw, bot)
        path.close()
        p.color = WICKER
        c.drawPath(path, p)
        // 엮은 무늬: 가로 줄 + 비스듬한 세로 살
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, h * 0.06f)
        p.color = WICKER_DARK
        for (k in 1..3) {
            val f = k / 4f
            val y = top + h * f
            val half = tw + (bw - tw) * f
            c.drawLine(cx - half, y, cx + half, y, p)
        }
        for (k in -2..2) {
            val f = k / 2.6f
            c.drawLine(cx + tw * f, top, cx + bw * f, bot, p)
        }
        // 플레이어 색 띠
        p.style = Paint.Style.FILL
        p.color = ribbon
        val ry = top + h * 0.42f
        val rh = h * 0.2f
        val rw0 = tw + (bw - tw) * 0.42f
        val rw1 = tw + (bw - tw) * 0.62f
        path.reset()
        path.moveTo(cx - rw0, ry)
        path.lineTo(cx + rw0, ry)
        path.lineTo(cx + rw1, ry + rh)
        path.lineTo(cx - rw1, ry + rh)
        path.close()
        c.drawPath(path, p)
        // 그을음
        if (soot > 0f) {
            p.color = 0xFF1A1410.toInt()
            p.alpha = (200 * soot).toInt()
            path.reset()
            path.moveTo(cx - tw, top)
            path.lineTo(cx + tw, top)
            path.lineTo(cx + bw, bot)
            path.lineTo(cx - bw, bot)
            path.close()
            c.drawPath(path, p)
            p.alpha = 255
        }
        // 테두리 (둥근 막대)
        p.color = WICKER_LIGHT
        rect.set(cx - tw * 1.08f, top - h * 0.13f, cx + tw * 1.08f, top + h * 0.13f)
        c.drawRoundRect(rect, h * 0.13f, h * 0.13f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, h * 0.05f)
        p.color = WICKER_DARK
        c.drawRoundRect(rect, h * 0.13f, h * 0.13f, p)
        p.style = Paint.Style.FILL
    }

    /** 깨진 계란: 땅(groundY) 위 가운데 x, 크기 s(계란 지름 정도). 흰자 + 노른자 + 껍데기 두 조각 */
    fun splat(c: Canvas, x: Float, groundY: Float, s: Float, gold: Boolean, alpha: Int, p: Paint) {
        val gy = groundY
        p.style = Paint.Style.FILL
        // 흰자
        p.color = 0xFFF7F2E6.toInt()
        p.alpha = alpha * 230 / 255
        rect.set(x - s * 0.58f, gy - s * 0.17f, x + s * 0.58f, gy + s * 0.06f)
        c.drawOval(rect, p)
        c.drawCircle(x - s * 0.42f, gy - s * 0.07f, s * 0.13f, p)
        c.drawCircle(x + s * 0.44f, gy - s * 0.08f, s * 0.11f, p)
        c.drawCircle(x - s * 0.74f, gy - s * 0.2f, s * 0.05f, p)
        c.drawCircle(x + s * 0.72f, gy - s * 0.24f, s * 0.045f, p)
        c.drawCircle(x + s * 0.18f, gy - s * 0.3f, s * 0.04f, p)
        // 노른자
        p.color = if (gold) YOLK_GOLD else YOLK
        p.alpha = alpha
        c.drawCircle(x + s * 0.04f, gy - s * 0.09f, s * 0.15f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha * 150 / 255
        c.drawCircle(x - s * 0.01f, gy - s * 0.14f, s * 0.045f, p)
        // 껍데기 두 조각 (깨진 톱니 가장자리)
        shell(c, x - s * 0.5f, gy - s * 0.1f, s * 0.26f, -25f, gold, alpha, p)
        shell(c, x + s * 0.52f, gy - s * 0.12f, s * 0.22f, 35f, gold, alpha, p)
        p.alpha = 255
    }

    /** 껍데기 조각: 아래가 둥근 반쪽 + 위가 톱니 */
    private fun shell(c: Canvas, x: Float, y: Float, r: Float, deg: Float, gold: Boolean, alpha: Int, p: Paint) {
        c.save()
        c.rotate(deg, x, y)
        path.reset()
        rect.set(x - r, y - r * 0.9f, x + r, y + r * 0.9f)
        path.arcTo(rect, 0f, 180f, true)
        // 톱니 (왼쪽 → 오른쪽)
        path.lineTo(x - r * 0.6f, y - r * 0.35f)
        path.lineTo(x - r * 0.25f, y + r * 0.05f)
        path.lineTo(x + r * 0.1f, y - r * 0.4f)
        path.lineTo(x + r * 0.5f, y - r * 0.02f)
        path.close()
        p.style = Paint.Style.FILL
        p.color = if (gold) Theme.GOLD else SHELL
        p.alpha = alpha
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, r * 0.12f)
        p.color = if (gold) Theme.GOLD_DARK else SHELL_LINE
        p.alpha = alpha * 3 / 4
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    /** 반짝이 별 (네 갈래) */
    fun twinkle(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = alpha
        path.reset()
        path.moveTo(x, y - r)
        path.quadTo(x, y, x + r, y)
        path.quadTo(x, y, x, y + r)
        path.quadTo(x, y, x - r, y)
        path.quadTo(x, y, x, y - r)
        path.close()
        c.drawPath(path, p)
        p.alpha = 255
    }

    /** 펑! 폭발 (가운데 x, y, 반지름 r, 진행 f 0..1) */
    fun boom(c: Canvas, x: Float, y: Float, r: Float, f: Float, p: Paint) {
        val a = ((1f - f) * 255).toInt().coerceIn(0, 255)
        p.style = Paint.Style.FILL
        // 연기
        p.color = SMOKE
        p.alpha = a * 140 / 255
        c.drawCircle(x - r * 0.5f, y - r * 0.3f * f - r * 0.2f, r * (0.4f + 0.5f * f), p)
        c.drawCircle(x + r * 0.5f, y - r * 0.4f * f - r * 0.25f, r * (0.35f + 0.45f * f), p)
        // 불덩이
        p.color = FIRE
        p.alpha = a
        c.drawCircle(x, y, r * (0.5f + 0.6f * f) * (1f - 0.3f * f), p)
        p.color = Theme.GOLD_LIGHT
        p.alpha = a
        c.drawCircle(x, y, r * 0.45f * (1f - f), p)
        // 파편 선
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.12f
        p.color = Theme.GOLD
        p.alpha = a
        val r0 = r * (0.7f + 0.7f * f)
        val r1 = r * (1.0f + 0.9f * f)
        for (k in 0 until 8) {
            val cs = COS8[k]
            val sn = SIN8[k]
            c.drawLine(x + cs * r0, y + sn * r0, x + cs * r1, y + sn * r1, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.alpha = 255
    }

    /** 8방향 (미리 계산) */
    val COS8 = FloatArray(8) { Math.cos(it * Math.PI / 4).toFloat() }
    val SIN8 = FloatArray(8) { Math.sin(it * Math.PI / 4).toFloat() }
}
