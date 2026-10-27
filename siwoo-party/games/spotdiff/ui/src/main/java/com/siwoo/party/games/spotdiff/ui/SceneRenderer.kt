package com.siwoo.party.games.spotdiff.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.spotdiff.GameConfig
import com.siwoo.party.games.spotdiff.Kind
import com.siwoo.party.games.spotdiff.Palette
import com.siwoo.party.games.spotdiff.Scene
import com.siwoo.party.games.spotdiff.SceneObject

/**
 * 장면 그리기 (도형만, 할당 없음 — 규칙 카드 그림에서도 매번 부른다).
 * (x0, y0) 에서 그림 좌표 1 = s 픽셀로 그린다. 배경(하늘 띠·언덕·땅) → 물체 순서.
 * 물체는 굵은 테두리를 먼저 그리고 그 위에 색을 칠해 겹친 도형도 한 덩어리로 보이게 한다.
 */
class SceneRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()

    /** 배경 + 물체 (right = 오른쪽 그림, 차이 반영) */
    fun drawScene(c: Canvas, scene: Scene, right: Boolean, x0: Float, y0: Float, s: Float) {
        c.save()
        c.clipRect(x0, y0, x0 + GameConfig.SCENE_W * s, y0 + GameConfig.SCENE_H * s)
        drawBackground(c, scene.theme, scene.seed, x0, y0, s)
        val list = if (right) scene.right else scene.left
        for (i in list.indices) drawObject(c, list[i], x0, y0, s)
        c.restore()
    }

    fun drawBackground(c: Canvas, theme: Int, seed: Long, x0: Float, y0: Float, s: Float) {
        val w = GameConfig.SCENE_W * s
        val hz = GameConfig.HORIZON_Y * s
        val h = GameConfig.SCENE_H * s
        val top = SKY_TOP[theme]
        val bottom = SKY_BOTTOM[theme]
        // 하늘: 8개 띠로 부드럽게
        fill.style = Paint.Style.FILL
        for (k in 0 until 8) {
            fill.color = mix(top, bottom, k / 7f)
            c.drawRect(x0, y0 + hz * k / 8f, x0 + w, y0 + hz * (k + 1) / 8f + 1f, fill)
        }
        // 먼 언덕 두 개 (seed 로 자리만 바뀜)
        val a = ((seed ushr 3) and 0xFF).toFloat() / 255f
        val b = ((seed ushr 11) and 0xFF).toFloat() / 255f
        fill.color = HILL[theme]
        rect.set(x0 + w * (a * 0.5f - 0.25f), y0 + hz - 46f * s, x0 + w * (a * 0.5f + 0.45f), y0 + hz + 40f * s)
        c.drawOval(rect, fill)
        fill.color = Theme.shade(HILL[theme], 0.9f)
        rect.set(x0 + w * (b * 0.4f + 0.45f), y0 + hz - 34f * s, x0 + w * (b * 0.4f + 1.15f), y0 + hz + 40f * s)
        c.drawOval(rect, fill)
        // 땅: 가까울수록 조금 진하게
        val g = GROUND[theme]
        for (k in 0 until 3) {
            fill.color = Theme.shade(g, 1f - k * 0.06f)
            val t0 = hz + (h - hz) * k / 3f
            c.drawRect(x0, y0 + t0, x0 + w, y0 + hz + (h - hz) * (k + 1) / 3f + 1f, fill)
        }
        // 지평선 선
        line.color = Theme.shade(g, 0.8f)
        line.strokeWidth = 1.5f * s
        c.drawLine(x0, y0 + hz, x0 + w, y0 + hz, line)
    }

    fun drawObject(c: Canvas, o: SceneObject, x0: Float, y0: Float, s: Float) {
        val l = x0 + o.left * s
        val t = y0 + o.top * s
        val w = o.w * s
        val h = o.h * s
        val ow = (1.6f * s).coerceAtLeast(1f)
        line.strokeWidth = ow
        line.color = OUTLINE
        fill.style = Paint.Style.FILL
        val flip = o.flip
        if (flip) {
            c.save()
            c.scale(-1f, 1f, l + w / 2f, t + h / 2f)
        }
        when (o.kind) {
            Kind.HOUSE -> house(c, o, l, t, w, h)
            Kind.TREE -> tree(c, o, l, t, w, h, ow)
            Kind.PINE -> pine(c, o, l, t, w, h)
            Kind.SUN -> sun(c, o, l, t, w, ow)
            Kind.CLOUD -> cloud(c, o, l, t, w, h, ow)
            Kind.BALLOON -> balloon(c, o, l, t, w, h, ow)
            Kind.BIRD -> bird(c, o, l, t, w, h, ow)
            Kind.CAR -> car(c, o, l, t, w, h, ow)
            Kind.FENCE -> fence(c, o, l, t, w, h)
            Kind.PERSON -> person(c, o, l, t, w, h, ow)
            Kind.FLOWER -> flower(c, o, l, t, w, h, ow)
        }
        if (flip) c.restore()
    }

    // ── 물체별 ──

    private fun fillStroke(c: Canvas, color: Int) {
        fill.color = color
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    private fun rectFS(c: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float) {
        fill.color = color
        c.drawRect(l, t, r, b, fill)
        c.drawRect(l, t, r, b, line)
    }

    private fun circleFS(c: Canvas, color: Int, x: Float, y: Float, r: Float) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, line)
    }

    private fun house(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float) {
        val roofB = t + h * 0.42f
        // 굴뚝
        rectFS(c, Palette.BROWN, l + w * 0.68f, t + h * 0.08f, l + w * 0.8f, t + h * 0.3f)
        // 벽
        rectFS(c, o.color, l + w * 0.1f, roofB, l + w * 0.9f, t + h)
        // 지붕
        path.reset()
        path.moveTo(l, roofB)
        path.lineTo(l + w / 2f, t)
        path.lineTo(l + w, roofB)
        path.close()
        fillStroke(c, o.accent)
        // 문
        rectFS(c, DOOR, l + w * 0.22f, t + h * 0.66f, l + w * 0.42f, t + h)
        fill.color = Palette.YELLOW
        c.drawCircle(l + w * 0.38f, t + h * 0.84f, w * 0.018f + 0.5f, fill)
        // 창문 (십자 창살)
        val wl = l + w * 0.55f
        val wt = t + h * 0.55f
        val ws = w * 0.24f
        rectFS(c, WINDOW, wl, wt, wl + ws, wt + ws)
        c.drawLine(wl + ws / 2f, wt, wl + ws / 2f, wt + ws, line)
        c.drawLine(wl, wt + ws / 2f, wl + ws, wt + ws / 2f, line)
    }

    private fun tree(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float, ow: Float) {
        val cx = l + w / 2f
        rectFS(c, o.accent, cx - w * 0.1f, t + h * 0.55f, cx + w * 0.1f, t + h)
        // 잎: 세 동그라미 (테두리 먼저 → 색)
        val r1 = w * 0.42f
        val r2 = w * 0.3f
        line.strokeWidth = ow * 2f
        c.drawCircle(cx, t + r1 + ow, r1, line)
        c.drawCircle(l + r2 + ow, t + h * 0.5f, r2, line)
        c.drawCircle(l + w - r2 - ow, t + h * 0.5f, r2, line)
        line.strokeWidth = ow
        fill.color = o.color
        c.drawCircle(cx, t + r1 + ow, r1, fill)
        c.drawCircle(l + r2 + ow, t + h * 0.5f, r2, fill)
        c.drawCircle(l + w - r2 - ow, t + h * 0.5f, r2, fill)
        // 반짝 잎 점
        fill.color = Theme.shade(o.color, 1.25f)
        c.drawCircle(cx - r1 * 0.35f, t + r1 * 0.75f, r1 * 0.2f, fill)
    }

    private fun pine(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float) {
        val cx = l + w / 2f
        rectFS(c, o.accent, cx - w * 0.09f, t + h * 0.78f, cx + w * 0.09f, t + h)
        for (i in 0 until 3) {
            val half = w * (0.3f + 0.1f * i)
            val top = t + h * 0.24f * i
            val bottom = t + h * (0.38f + 0.2f * i)
            path.reset()
            path.moveTo(cx, top)
            path.lineTo(cx + half, bottom)
            path.lineTo(cx - half, bottom)
            path.close()
            fillStroke(c, o.color)
        }
    }

    private fun sun(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, ow: Float) {
        val cx = l + w / 2f
        val cy = t + w / 2f
        line.color = o.color
        line.strokeWidth = ow * 2.2f
        for (k in 0 until 8) {
            val a = k * Math.PI / 4.0
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            c.drawLine(cx + ca * w * 0.38f, cy + sa * w * 0.38f, cx + ca * w * 0.48f, cy + sa * w * 0.48f, line)
        }
        line.color = OUTLINE
        line.strokeWidth = ow
        circleFS(c, o.color, cx, cy, w * 0.3f)
        fill.color = Theme.shade(o.color, 1.3f)
        c.drawCircle(cx - w * 0.09f, cy - w * 0.09f, w * 0.09f, fill)
    }

    private fun cloud(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float, ow: Float) {
        line.strokeWidth = ow * 2f
        for (pass in 0..1) {
            val p = if (pass == 0) line else fill
            fill.color = o.color
            rect.set(l + ow, t + h * 0.45f, l + w - ow, t + h - ow)
            c.drawRoundRect(rect, h * 0.26f, h * 0.26f, p)
            c.drawCircle(l + w * 0.32f, t + h * 0.5f, h * 0.36f, p)
            c.drawCircle(l + w * 0.58f, t + h * 0.46f, h * 0.44f, p)
            c.drawCircle(l + w * 0.78f, t + h * 0.6f, h * 0.3f, p)
        }
        line.strokeWidth = ow
    }

    private fun balloon(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float, ow: Float) {
        val cx = l + w / 2f
        // 끈
        path.reset()
        path.moveTo(cx, t + h * 0.66f)
        path.quadTo(cx + w * 0.3f, t + h * 0.82f, cx - w * 0.05f, t + h)
        c.drawPath(path, line)
        // 매듭
        path.reset()
        path.moveTo(cx, t + h * 0.62f)
        path.lineTo(cx + w * 0.1f, t + h * 0.7f)
        path.lineTo(cx - w * 0.1f, t + h * 0.7f)
        path.close()
        fillStroke(c, o.color)
        rect.set(l + ow, t + ow, l + w - ow, t + h * 0.66f)
        fill.color = o.color
        c.drawOval(rect, fill)
        c.drawOval(rect, line)
        fill.color = 0x88FFFFFF.toInt()
        rect.set(l + w * 0.24f, t + h * 0.1f, l + w * 0.42f, t + h * 0.28f)
        c.drawOval(rect, fill)
    }

    private fun bird(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float, ow: Float) {
        path.reset()
        path.moveTo(l + ow, t + h * 0.35f)
        path.quadTo(l + w * 0.25f, t - h * 0.05f, l + w / 2f, t + h * 0.75f)
        path.quadTo(l + w * 0.75f, t - h * 0.05f, l + w - ow, t + h * 0.35f)
        line.color = o.color
        line.strokeWidth = ow * 1.7f
        c.drawPath(path, line)
        line.strokeWidth = ow
        line.color = OUTLINE
    }

    private fun car(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float, ow: Float) {
        // 지붕 (앞쪽이 오른쪽)
        path.reset()
        path.moveTo(l + w * 0.16f, t + h * 0.45f)
        path.lineTo(l + w * 0.3f, t + ow)
        path.lineTo(l + w * 0.66f, t + ow)
        path.lineTo(l + w * 0.82f, t + h * 0.45f)
        path.close()
        fillStroke(c, o.color)
        // 창
        path.reset()
        path.moveTo(l + w * 0.25f, t + h * 0.42f)
        path.lineTo(l + w * 0.34f, t + h * 0.12f)
        path.lineTo(l + w * 0.47f, t + h * 0.12f)
        path.lineTo(l + w * 0.47f, t + h * 0.42f)
        path.close()
        fillStroke(c, WINDOW)
        path.reset()
        path.moveTo(l + w * 0.52f, t + h * 0.42f)
        path.lineTo(l + w * 0.52f, t + h * 0.12f)
        path.lineTo(l + w * 0.63f, t + h * 0.12f)
        path.lineTo(l + w * 0.74f, t + h * 0.42f)
        path.close()
        fillStroke(c, WINDOW)
        // 몸통
        rect.set(l + ow, t + h * 0.42f, l + w - ow, t + h * 0.8f)
        fill.color = o.color
        c.drawRoundRect(rect, h * 0.14f, h * 0.14f, fill)
        c.drawRoundRect(rect, h * 0.14f, h * 0.14f, line)
        // 전조등
        fill.color = Palette.YELLOW
        c.drawCircle(l + w - ow * 3f, t + h * 0.55f, h * 0.06f, fill)
        // 바퀴
        val wr = h * 0.19f
        circleFS(c, 0xFF263238.toInt(), l + w * 0.25f, t + h - wr, wr)
        circleFS(c, 0xFF263238.toInt(), l + w * 0.75f, t + h - wr, wr)
        fill.color = 0xFFB0BEC5.toInt()
        c.drawCircle(l + w * 0.25f, t + h - wr, wr * 0.42f, fill)
        c.drawCircle(l + w * 0.75f, t + h - wr, wr * 0.42f, fill)
    }

    private fun fence(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float) {
        // 가로대 두 줄
        rectFS(c, o.color, l, t + h * 0.32f, l + w, t + h * 0.46f)
        rectFS(c, o.color, l, t + h * 0.68f, l + w, t + h * 0.82f)
        // 말뚝 5개 (뾰족 머리)
        val pw = w * 0.11f
        for (k in 0 until 5) {
            val px = l + pw / 2f + (w - pw) * k / 4f
            path.reset()
            path.moveTo(px - pw / 2f, t + h)
            path.lineTo(px - pw / 2f, t + h * 0.2f)
            path.lineTo(px, t)
            path.lineTo(px + pw / 2f, t + h * 0.2f)
            path.lineTo(px + pw / 2f, t + h)
            path.close()
            fillStroke(c, o.color)
        }
    }

    private fun person(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float, ow: Float) {
        val cx = l + w / 2f
        // 다리
        rectFS(c, o.accent, cx - w * 0.28f, t + h * 0.62f, cx - w * 0.04f, t + h - ow)
        rectFS(c, o.accent, cx + w * 0.04f, t + h * 0.62f, cx + w * 0.28f, t + h - ow)
        // 팔 (한쪽은 흔드는 손)
        line.strokeWidth = ow * 2.6f
        line.color = OUTLINE
        c.drawLine(cx - w * 0.3f, t + h * 0.36f, l + w * 0.06f, t + h * 0.58f, line)
        c.drawLine(cx + w * 0.3f, t + h * 0.36f, l + w * 0.94f, t + h * 0.2f, line)
        line.strokeWidth = ow * 1.4f
        line.color = SKIN
        c.drawLine(cx - w * 0.3f, t + h * 0.36f, l + w * 0.06f, t + h * 0.58f, line)
        c.drawLine(cx + w * 0.3f, t + h * 0.36f, l + w * 0.94f, t + h * 0.2f, line)
        line.color = OUTLINE
        line.strokeWidth = ow
        // 몸통 (주 색)
        rect.set(cx - w * 0.32f, t + h * 0.3f, cx + w * 0.32f, t + h * 0.66f)
        fill.color = o.color
        c.drawRoundRect(rect, w * 0.12f, w * 0.12f, fill)
        c.drawRoundRect(rect, w * 0.12f, w * 0.12f, line)
        // 머리 + 머리카락
        val hr = minOf(w * 0.3f, h * 0.15f)
        val hy = t + hr + ow
        circleFS(c, SKIN, cx, hy, hr)
        rect.set(cx - hr, hy - hr, cx + hr, hy + hr)
        fill.color = HAIR
        c.drawArc(rect, 180f, 180f, true, fill)
        c.drawCircle(cx, hy, hr, line)
    }

    private fun flower(c: Canvas, o: SceneObject, l: Float, t: Float, w: Float, h: Float, ow: Float) {
        val cx = l + w / 2f
        val cy = t + w * 0.5f
        line.color = Palette.GREEN
        line.strokeWidth = ow * 1.5f
        c.drawLine(cx, cy, cx, t + h, line)
        line.color = OUTLINE
        line.strokeWidth = ow
        fill.color = Palette.GREEN
        rect.set(cx, t + h * 0.66f, cx + w * 0.4f, t + h * 0.8f)
        c.drawOval(rect, fill)
        val pr = w * 0.22f
        val d = w * 0.27f
        for (k in 0 until 5) {
            val a = -Math.PI / 2 + k * 2 * Math.PI / 5
            circleFS(c, o.color, cx + (Math.cos(a) * d).toFloat(), cy + (Math.sin(a) * d).toFloat(), pr)
        }
        fill.color = if (o.color == Palette.YELLOW) Palette.ORANGE else Palette.YELLOW
        c.drawCircle(cx, cy, pr * 0.8f, fill)
    }

    companion object {
        const val OUTLINE = 0xFF2B2D42.toInt()
        const val WINDOW = 0xFFB3E5FC.toInt()
        const val DOOR = 0xFF795548.toInt()
        const val SKIN = 0xFFFFD3A6.toInt()
        const val HAIR = 0xFF4E342E.toInt()
        /** 테마별 (0 맑은 낮, 1 노을, 2 봄 들판) */
        val SKY_TOP = intArrayOf(0xFF4FA8F0.toInt(), 0xFFF08A68.toInt(), 0xFF7FD0F5.toInt())
        val SKY_BOTTOM = intArrayOf(0xFFBFE6FF.toInt(), 0xFFFFDDB0.toInt(), 0xFFE3F7FF.toInt())
        val HILL = intArrayOf(0xFF9CCF7C.toInt(), 0xFFB98F8F.toInt(), 0xFF7CC47F.toInt())
        val GROUND = intArrayOf(0xFF7BC25A.toInt(), 0xFF96BC5C.toInt(), 0xFFA5D774.toInt())

        /** a→b 사이 색 (f = 0..1) */
        fun mix(a: Int, b: Int, f: Float): Int {
            val r = mixCh(a, b, 16, f)
            val g = mixCh(a, b, 8, f)
            val bl = mixCh(a, b, 0, f)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }

        private fun mixCh(a: Int, b: Int, sh: Int, f: Float): Int {
            val x = (a shr sh) and 0xFF
            val y = (b shr sh) and 0xFF
            return (x + (y - x) * f).toInt().coerceIn(0, 255)
        }
    }
}

