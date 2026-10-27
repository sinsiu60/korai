package com.siwoo.party.games.tuho.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.tuho.GameConfig
import com.siwoo.party.games.tuho.Opening
import com.siwoo.party.games.tuho.TuhoField

/**
 * 투호 그림 도구 (Canvas 도형만, 할당 없음 — 경로·사각형은 필드를 다시 씀, UI 스레드 전용).
 * 크기는 모두 "미터 × s(1미터 = s 픽셀)" — 판정 기하(GameConfig)와 똑같은 모양으로 그린다.
 * 항아리 기준: jx = 항아리 가운데 x(px), fy = 바닥 y(px).
 */
object TuhoArt {

    private val path = Path()
    private val r = RectF()

    // ── 색 ──
    const val BRONZE_DARK = 0xFF5E4220.toInt()
    const val BRONZE = 0xFF8F6A33.toInt()
    const val BRONZE_LIGHT = 0xFFB8904A.toInt()
    const val BRONZE_SHINE = 0x66FFF1C4
    const val HOLE = 0xFF241608.toInt()
    const val GOLD_LINE = 0xFFE8C25A.toInt()
    const val TAEGEUK_RED = 0xFFC8322F.toInt()
    const val TAEGEUK_BLUE = 0xFF1F4E9C.toInt()
    const val SHAFT = 0xFFD9B77E.toInt()
    const val SHAFT_DARK = 0xFF8A6A3A.toInt()
    const val INK = 0xFF2A1A0C.toInt()
    const val SKIN = 0xFFF5D3B0.toInt()

    /** 항아리 바닥 그림자 */
    fun jarShadow(c: Canvas, p: Paint, jx: Float, fy: Float, s: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        r.set(jx - GameConfig.BODY_RX * s * 1.15f, fy - 0.03f * s, jx + GameConfig.BODY_RX * s * 1.15f, fy + 0.035f * s)
        c.drawOval(r, p)
    }

    /** 몸통 (타원 + 무늬 띠 + 태극 + 굽) — 꽂힌 화살보다 먼저 그린다 */
    fun jarBody(c: Canvas, p: Paint, jx: Float, fy: Float, s: Float) {
        val rx = GameConfig.BODY_RX * s
        val ry = GameConfig.BODY_RY * s
        val cy = fy - GameConfig.BODY_CY * s
        p.style = Paint.Style.FILL
        // 굽
        p.color = BRONZE_DARK
        r.set(jx - rx * 0.5f, fy - 0.05f * s, jx + rx * 0.5f, fy)
        c.drawRoundRect(r, 0.015f * s, 0.015f * s, p)
        // 몸통: 어두운 바탕 → 밝은 면(왼쪽 위) → 반짝임
        p.color = BRONZE_DARK
        r.set(jx - rx, cy - ry, jx + rx, cy + ry)
        c.drawOval(r, p)
        p.color = BRONZE
        r.set(jx - rx * 0.97f, cy - ry * 0.97f, jx + rx * 0.86f, cy + ry * 0.9f)
        c.drawOval(r, p)
        // 무늬 띠 (몸통 안으로 잘라서)
        c.save()
        path.reset()
        r.set(jx - rx, cy - ry, jx + rx, cy + ry)
        path.addOval(r, Path.Direction.CW)
        c.clipPath(path)
        val band = 0.065f * s
        p.color = 0x33000000
        c.drawRect(jx - rx, cy - band, jx + rx, cy + band, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, 0.008f * s)
        p.color = GOLD_LINE
        c.drawLine(jx - rx, cy - band, jx + rx, cy - band, p)
        c.drawLine(jx - rx, cy + band, jx + rx, cy + band, p)
        // 띠 위·아래 가는 줄
        p.strokeWidth = maxOf(1f, 0.004f * s)
        c.drawLine(jx - rx, cy - ry * 0.62f, jx + rx, cy - ry * 0.62f, p)
        c.drawLine(jx - rx, cy + ry * 0.62f, jx + rx, cy + ry * 0.62f, p)
        // 띠 안 마름모 무늬
        p.style = Paint.Style.FILL
        p.color = 0xCCE8C25A.toInt()
        val step = 0.07f * s
        val dh = band * 0.55f
        var x = jx - step * 4f
        while (x <= jx + step * 4f) {
            if (x < jx - step * 0.9f || x > jx + step * 0.9f) {
                path.reset()
                path.moveTo(x, cy - dh)
                path.lineTo(x + dh * 0.7f, cy)
                path.lineTo(x, cy + dh)
                path.lineTo(x - dh * 0.7f, cy)
                path.close()
                c.drawPath(path, p)
            }
            x += step
        }
        // 반짝임 (왼쪽 위)
        p.color = BRONZE_SHINE
        r.set(jx - rx * 0.78f, cy - ry * 0.86f, jx - rx * 0.18f, cy - ry * 0.45f)
        c.drawOval(r, p)
        c.restore()
        // 가운데 태극
        taegeuk(c, p, jx, cy, 0.05f * s)
    }

    /** 태극 (가로 S 자) */
    fun taegeuk(c: Canvas, p: Paint, x: Float, y: Float, rad: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFF3E6C8.toInt()
        c.drawCircle(x, y, rad * 1.18f, p)
        p.color = TAEGEUK_RED
        c.drawCircle(x, y, rad, p)
        p.color = TAEGEUK_BLUE
        r.set(x - rad, y - rad, x + rad, y + rad)
        c.drawArc(r, 0f, 180f, true, p)
        p.color = TAEGEUK_RED
        c.drawCircle(x - rad / 2f, y, rad / 2f, p)
        p.color = TAEGEUK_BLUE
        c.drawCircle(x + rad / 2f, y, rad / 2f, p)
    }

    /** 관 하나 (목·귀): 몸통색 + 밝은 줄 + 금 고리 + 위 끝 구멍 */
    private fun tube(c: Canvas, p: Paint, x: Float, fy: Float, s: Float, o: Opening) {
        val half = o.outer * s
        val top = fy - o.top * s
        val bottom = fy - o.bottom * s
        p.style = Paint.Style.FILL
        p.color = BRONZE_DARK
        c.drawRect(x - half, top, x + half, bottom, p)
        p.color = BRONZE
        c.drawRect(x - half, top, x + half * 0.55f, bottom, p)
        p.color = BRONZE_SHINE
        c.drawRect(x - half * 0.7f, top, x - half * 0.3f, bottom, p)
        // 금 고리 (위 끝, 아래쪽)
        p.color = GOLD_LINE
        val ring = maxOf(1.5f, 0.012f * s)
        c.drawRect(x - half, top, x + half, top + ring, p)
        c.drawRect(x - half, bottom - ring * 1.5f, x + half, bottom, p)
        // 구멍 (위에서 살짝 보이는 타원)
        p.color = HOLE
        val hh = maxOf(1.5f, 0.012f * s)
        r.set(x - o.half * s, top - hh, x + o.half * s, top + hh)
        c.drawOval(r, p)
    }

    /** 목(입구)·귀 두 개·팔 — 꽂힌 화살 위에 그려 관 속 부분을 가린다 */
    fun jarFront(c: Canvas, p: Paint, jx: Float, fy: Float, s: Float) {
        val m = TuhoField.mouth
        // 팔 (목 ↔ 귀)
        p.style = Paint.Style.FILL
        for (k in 1..2) {
            val e = if (k == 1) TuhoField.earNear else TuhoField.earFar
            val ex = jx + (e.cx - m.cx) * s
            val top = fy - (e.top - GameConfig.ARM_TOP) * s
            val bottom = fy - (e.top - GameConfig.ARM_BOTTOM) * s
            val l = if (ex < jx) ex else jx
            val rr = if (ex < jx) jx else ex
            p.color = BRONZE_DARK
            c.drawRect(l, top, rr, bottom, p)
            p.color = GOLD_LINE
            c.drawRect(l, top, rr, top + maxOf(1f, 0.006f * s), p)
        }
        // 어깨 (목이 몸통에 붙는 자리)
        p.color = BRONZE_DARK
        val sh = m.outer * s * 1.7f
        val shy = fy - (GameConfig.BODY_CY + GameConfig.BODY_RY * 0.92f) * s
        r.set(jx - sh, shy - 0.03f * s, jx + sh, shy + 0.03f * s)
        c.drawOval(r, p)
        tube(c, p, jx, fy, s, m)
        tube(c, p, jx + (TuhoField.earNear.cx - m.cx) * s, fy, s, TuhoField.earNear)
        tube(c, p, jx + (TuhoField.earFar.cx - m.cx) * s, fy, s, TuhoField.earFar)
    }

    /**
     * 화살 (끝 = (tx, ty), 방향 (dx, dy) 단위 벡터 — 화면 좌표, 아래가 +). 몸통은 끝에서 뒤로 len.
     * 끝은 쇠 촉, 뒤쪽에 깃 (color).
     */
    fun arrow(c: Canvas, p: Paint, tx: Float, ty: Float, dx: Float, dy: Float, len: Float, color: Int, alpha: Int = 255) {
        val bx = tx - dx * len
        val by = ty - dy * len
        val nx = -dy
        val ny = dx
        val w = maxOf(1.6f, len * 0.035f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = SHAFT_DARK
        p.alpha = alpha
        c.drawLine(tx - dx * len * 0.1f, ty - dy * len * 0.1f, bx, by, p)
        p.strokeWidth = w * 0.55f
        p.color = SHAFT
        p.alpha = alpha
        c.drawLine(tx - dx * len * 0.1f - nx * w * 0.15f, ty - dy * len * 0.1f - ny * w * 0.15f, bx, by, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        // 촉
        p.color = INK
        p.alpha = alpha
        val hl = len * 0.12f
        val hw = w * 1.3f
        path.reset()
        path.moveTo(tx, ty)
        path.lineTo(tx - dx * hl + nx * hw, ty - dy * hl + ny * hw)
        path.lineTo(tx - dx * hl - nx * hw, ty - dy * hl - ny * hw)
        path.close()
        c.drawPath(path, p)
        // 깃 (뒤쪽 양옆 두 장)
        p.color = color
        p.alpha = alpha
        val f0 = len * 0.78f
        val f1 = len * 0.98f
        val fw = w * 2.4f
        for (k in 0..1) {
            val sg = if (k == 0) 1f else -1f
            path.reset()
            path.moveTo(tx - dx * f0, ty - dy * f0)
            path.lineTo(tx - dx * (f0 + len * 0.06f) + nx * fw * sg, ty - dy * (f0 + len * 0.06f) + ny * fw * sg)
            path.lineTo(tx - dx * f1 + nx * fw * sg, ty - dy * f1 + ny * fw * sg)
            path.lineTo(tx - dx * f1, ty - dy * f1)
            path.close()
            c.drawPath(path, p)
        }
        p.alpha = 255
    }

    /**
     * 던지는 사람 (옆모습, 한복). 발 가운데 (x, fy), 키 약 1.15 m.
     * 손은 (hx, hy) — 팔이 어깨에서 손까지 뻗는다.
     */
    fun thrower(c: Canvas, p: Paint, x: Float, fy: Float, s: Float, color: Int, hx: Float, hy: Float) {
        p.style = Paint.Style.FILL
        // 그림자
        p.color = 0x33000000
        r.set(x - 0.2f * s, fy - 0.025f * s, x + 0.2f * s, fy + 0.025f * s)
        c.drawOval(r, p)
        // 바지 (흰 한복 바지, 발목 대님)
        p.color = 0xFFF2EBDD.toInt()
        path.reset()
        path.moveTo(x - 0.1f * s, fy - 0.5f * s)
        path.lineTo(x + 0.1f * s, fy - 0.5f * s)
        path.lineTo(x + 0.13f * s, fy - 0.06f * s)
        path.lineTo(x - 0.13f * s, fy - 0.06f * s)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFCBBF9F.toInt()
        c.drawRect(x - 0.005f * s, fy - 0.42f * s, x + 0.005f * s, fy - 0.07f * s, p)
        // 신발
        p.color = INK
        r.set(x - 0.12f * s, fy - 0.06f * s, x + 0.16f * s, fy)
        c.drawRoundRect(r, 0.03f * s, 0.03f * s, p)
        // 저고리 (플레이어 색)
        p.color = color
        path.reset()
        path.moveTo(x - 0.1f * s, fy - 0.92f * s)
        path.lineTo(x + 0.09f * s, fy - 0.92f * s)
        path.lineTo(x + 0.14f * s, fy - 0.48f * s)
        path.lineTo(x - 0.14f * s, fy - 0.48f * s)
        path.close()
        c.drawPath(path, p)
        // 동정(흰 깃)·고름
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(x + 0.02f * s, fy - 0.92f * s, x + 0.07f * s, fy - 0.86f * s, p)
        p.color = Theme.shade(color, 0.55f)
        c.drawRect(x + 0.05f * s, fy - 0.78f * s, x + 0.08f * s, fy - 0.6f * s, p)
        // 머리
        val hx0 = x + 0.01f * s
        val hy0 = fy - 1.03f * s
        val hr = 0.1f * s
        p.color = SKIN
        c.drawRect(hx0 - 0.03f * s, hy0 + hr * 0.6f, hx0 + 0.03f * s, fy - 0.9f * s, p)
        c.drawCircle(hx0, hy0, hr, p)
        // 머리카락 (뒤통수) + 상투
        p.color = INK
        r.set(hx0 - hr, hy0 - hr, hx0 + hr, hy0 + hr)
        c.drawArc(r, 150f, 150f, true, p)
        c.drawCircle(hx0 - hr * 0.1f, hy0 - hr * 1.05f, hr * 0.32f, p)
        // 눈·볼
        c.drawCircle(hx0 + hr * 0.5f, hy0 - hr * 0.05f, maxOf(1f, hr * 0.1f), p)
        p.color = 0x55FF6B6B
        c.drawCircle(hx0 + hr * 0.42f, hy0 + hr * 0.35f, hr * 0.18f, p)
        // 팔 (어깨 → 손)
        val sx = x + 0.0f * s
        val sy = fy - 0.86f * s
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 0.07f * s
        p.color = Theme.shade(color, 0.85f)
        c.drawLine(sx, sy, hx, hy, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.color = SKIN
        c.drawCircle(hx, hy, 0.035f * s, p)
    }

    /** 외곽선 있는 굵은 글자 (규칙 카드·아이콘용 — 할당 없음, 글자는 상수) */
    fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int, outline: Int = 0xFF0B1340.toInt()) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = outline
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }
}
