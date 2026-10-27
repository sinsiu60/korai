package com.siwoo.party.games.pirate.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.siwoo.party.common.ui.Theme

/**
 * 해적·칼·작은 나무통 그리기 (할당 없음: Paint·Path 는 부르는 쪽이 넘겨준다).
 * 게임 화면(BarrelView), 허브 아이콘, 규칙 카드가 같이 쓴다.
 */
object PirateArt {
    // 표정
    const val NORMAL = 0
    /** 칼이 꽂히는 동안: 눈썹이 올라가고 식은땀 */
    const val NERVOUS = 1
    /** 날아가는 중: X 눈 + 동그란 입 */
    const val FLYING = 2

    const val SKIN = 0xFFF2C19A.toInt()
    const val SKIN_DARK = 0xFFD99A70.toInt()
    const val BEARD = 0xFF5B3519.toInt()
    const val HAT = 0xFF1C1C26.toInt()
    const val HAT_TRIM = 0xFFE0B040.toInt()
    const val SHIRT = 0xFFD93A3A.toInt()
    const val BONE = 0xFFF4F1E6.toInt()
    const val INK = 0xFF15121A.toInt()
    const val SWEAT = 0xFF8FD3FF.toInt()

    const val WOOD_DARK = 0xFF5A3418.toInt()
    const val WOOD = 0xFF8B5A2B.toInt()
    const val WOOD_LIGHT = 0xFFC08A4A.toInt()
    const val METAL = 0xFF59606E.toInt()
    const val METAL_LIGHT = 0xFFB4BCCB.toInt()
    const val BLADE = 0xFFDDE3EA.toInt()
    const val BLADE_EDGE = 0xFF8A94A3.toInt()

    /**
     * 해적 상반신. 원점 = 얼굴 가운데, r = 얼굴 반지름 (부르는 쪽이 translate·rotate).
     * skin·stripes 셰이더가 있으면 입체 음영·줄무늬 셔츠 (없으면 단색).
     */
    fun drawPirate(c: Canvas, r: Float, p: Paint, path: Path, mood: Int, skin: Shader? = null, stripes: Shader? = null) {
        p.style = Paint.Style.FILL
        p.shader = null
        // 셔츠 (어깨)
        p.color = SHIRT
        p.shader = stripes
        c.drawOval(-r * 1.2f, r * 0.62f, r * 1.2f, r * 2.0f, p)
        p.shader = null
        // 목
        p.color = SKIN_DARK
        c.drawRect(-r * 0.32f, r * 0.6f, r * 0.32f, r * 0.95f, p)
        // 귀
        p.color = SKIN_DARK
        c.drawCircle(-r * 0.96f, r * 0.05f, r * 0.2f, p)
        c.drawCircle(r * 0.96f, r * 0.05f, r * 0.2f, p)
        // 얼굴
        p.color = SKIN
        p.shader = skin
        c.drawCircle(0f, 0f, r, p)
        p.shader = null
        // 수염 (턱을 감싸는 초승달)
        p.color = BEARD
        path.reset()
        path.moveTo(-r * 0.9f, r * 0.12f)
        path.quadTo(-r * 0.75f, r * 1.25f, 0f, r * 1.22f)
        path.quadTo(r * 0.75f, r * 1.25f, r * 0.9f, r * 0.12f)
        path.quadTo(r * 0.5f, r * 0.62f, 0f, r * 0.58f)
        path.quadTo(-r * 0.5f, r * 0.62f, -r * 0.9f, r * 0.12f)
        path.close()
        c.drawPath(path, p)
        // 입
        if (mood == FLYING) {
            p.color = 0xFF6E1414.toInt()
            c.drawOval(-r * 0.17f, r * 0.5f, r * 0.17f, r * 0.9f, p)
        } else {
            p.color = 0xFF6E1414.toInt()
            c.drawArc(-r * 0.3f, r * 0.42f, r * 0.3f, r * 0.82f, 0f, 180f, true, p)
            if (mood == NORMAL) {
                p.color = BONE
                c.drawRect(-r * 0.2f, r * 0.62f, r * 0.2f, r * 0.7f, p)
            }
        }
        // 콧수염
        p.color = BEARD
        path.reset()
        path.moveTo(0f, r * 0.36f)
        path.quadTo(-r * 0.32f, r * 0.2f, -r * 0.62f, r * 0.48f)
        path.quadTo(-r * 0.3f, r * 0.5f, 0f, r * 0.5f)
        path.quadTo(r * 0.3f, r * 0.5f, r * 0.62f, r * 0.48f)
        path.quadTo(r * 0.32f, r * 0.2f, 0f, r * 0.36f)
        path.close()
        c.drawPath(path, p)
        // 코
        p.color = SKIN_DARK
        c.drawCircle(0f, r * 0.2f, r * 0.15f, p)
        // 안대 끈 (이마를 비스듬히)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.07f
        p.color = INK
        c.drawLine(-r * 0.92f, -r * 0.42f, r * 0.62f, -r * 0.16f, p)
        // 왼눈 (보는 쪽 왼쪽)
        val ex = -r * 0.36f
        val ey = -r * 0.1f
        if (mood == FLYING) {
            p.strokeWidth = r * 0.08f
            val d = r * 0.12f
            c.drawLine(ex - d, ey - d, ex + d, ey + d, p)
            c.drawLine(ex - d, ey + d, ex + d, ey - d, p)
        } else {
            p.style = Paint.Style.FILL
            p.color = BONE
            val big = if (mood == NERVOUS) 1.2f else 1f
            c.drawOval(ex - r * 0.17f * big, ey - r * 0.2f * big, ex + r * 0.17f * big, ey + r * 0.2f * big, p)
            p.color = INK
            c.drawCircle(ex + r * 0.03f, ey + r * 0.03f, r * 0.09f, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(ex + r * 0.06f, ey - r * 0.02f, r * 0.035f, p)
        }
        // 눈썹
        p.style = Paint.Style.STROKE
        p.color = BEARD
        p.strokeWidth = r * 0.09f
        val lift = if (mood == NERVOUS) r * 0.1f else 0f
        c.drawLine(ex - r * 0.2f, ey - r * 0.3f - lift, ex + r * 0.18f, ey - r * 0.36f - lift * 1.6f, p)
        // 안대
        p.style = Paint.Style.FILL
        p.color = INK
        c.drawOval(r * 0.12f, -r * 0.33f, r * 0.62f, r * 0.12f, p)
        p.color = 0x33FFFFFF
        c.drawOval(r * 0.2f, -r * 0.27f, r * 0.38f, -r * 0.14f, p)
        // 식은땀
        if (mood == NERVOUS) {
            p.color = SWEAT
            path.reset()
            path.moveTo(-r * 0.78f, -r * 0.62f)
            path.quadTo(-r * 0.62f, -r * 0.35f, -r * 0.7f, -r * 0.28f)
            path.quadTo(-r * 0.86f, -r * 0.24f, -r * 0.86f, -r * 0.36f)
            path.quadTo(-r * 0.86f, -r * 0.45f, -r * 0.78f, -r * 0.62f)
            path.close()
            c.drawPath(path, p)
        }
        drawHat(c, r, p, path)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** 삼각 해적 모자 + 해골 문장 */
    private fun drawHat(c: Canvas, r: Float, p: Paint, path: Path) {
        p.style = Paint.Style.FILL
        p.color = HAT
        // 둥근 윗부분
        path.reset()
        path.moveTo(-r * 0.9f, -r * 0.52f)
        path.cubicTo(-r * 0.86f, -r * 1.55f, r * 0.86f, -r * 1.55f, r * 0.9f, -r * 0.52f)
        path.close()
        c.drawPath(path, p)
        // 챙: 양끝이 위로 말리고 가운데가 내려온 모양
        path.reset()
        path.moveTo(-r * 1.5f, -r * 1.0f)
        path.quadTo(-r * 1.05f, -r * 0.42f, 0f, -r * 0.4f)
        path.quadTo(r * 1.05f, -r * 0.42f, r * 1.5f, -r * 1.0f)
        path.quadTo(r * 0.95f, -r * 0.66f, 0f, -r * 0.7f)
        path.quadTo(-r * 0.95f, -r * 0.66f, -r * 1.5f, -r * 1.0f)
        path.close()
        c.drawPath(path, p)
        // 금색 테두리
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.07f
        p.color = HAT_TRIM
        path.reset()
        path.moveTo(-r * 1.5f, -r * 1.0f)
        path.quadTo(-r * 1.05f, -r * 0.42f, 0f, -r * 0.4f)
        path.quadTo(r * 1.05f, -r * 0.42f, r * 1.5f, -r * 1.0f)
        c.drawPath(path, p)
        // 뼈 두 개 (X)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.09f
        p.color = BONE
        val sy = -r * 0.98f
        c.drawLine(-r * 0.3f, sy - r * 0.2f, r * 0.3f, sy + r * 0.2f, p)
        c.drawLine(r * 0.3f, sy - r * 0.2f, -r * 0.3f, sy + r * 0.2f, p)
        // 해골
        p.style = Paint.Style.FILL
        c.drawCircle(0f, sy - r * 0.03f, r * 0.17f, p)
        c.drawRect(-r * 0.09f, sy + r * 0.06f, r * 0.09f, sy + r * 0.18f, p)
        p.color = HAT
        c.drawCircle(-r * 0.065f, sy - r * 0.03f, r * 0.05f, p)
        c.drawCircle(r * 0.065f, sy - r * 0.03f, r * 0.05f, p)
    }

    /**
     * 칼. 원점 = 구멍, x 축 = 칼이 나오는 방향 (부르는 쪽이 translate·rotate).
     * blade = 구멍 밖으로 보이는 칼날 길이, s = 칼 굵기 기준.
     */
    fun drawSword(c: Canvas, blade: Float, s: Float, color: Int, dark: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.shader = null
        // 칼날
        p.color = BLADE
        c.drawRect(0f, -s * 0.42f, blade, s * 0.42f, p)
        p.color = BLADE_EDGE
        c.drawRect(0f, s * 0.12f, blade, s * 0.42f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(0f, -s * 0.3f, blade, -s * 0.16f, p)
        // 손잡이 막이 (금)
        val g0 = blade
        val g1 = blade + s * 0.7f
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(g0, -s * 1.45f, g1, s * 1.45f, s * 0.3f, s * 0.3f, p)
        p.color = Theme.GOLD
        c.drawRoundRect(g0, -s * 1.45f, g1 - s * 0.22f, s * 1.3f, s * 0.3f, s * 0.3f, p)
        // 손잡이 (플레이어 색) + 감은 줄
        val h1 = g1 + s * 2.6f
        p.color = color
        c.drawRoundRect(g1, -s * 0.55f, h1, s * 0.55f, s * 0.3f, s * 0.3f, p)
        p.color = dark
        var x = g1 + s * 0.55f
        while (x < h1 - s * 0.3f) {
            c.drawRect(x, -s * 0.55f, x + s * 0.18f, s * 0.55f, p)
            x += s * 0.6f
        }
        // 손잡이 끝 구슬
        p.color = Theme.GOLD_DARK
        c.drawCircle(h1 + s * 0.45f, 0f, s * 0.62f, p)
        p.color = Theme.GOLD
        c.drawCircle(h1 + s * 0.38f, -s * 0.08f, s * 0.48f, p)
    }

    /** 칼 전체 길이 (blade 제외 부분: 손잡이 막이 + 손잡이 + 구슬) */
    fun hiltLength(s: Float): Float = s * (0.7f + 2.6f + 1.1f)

    /**
     * 단순한 나무통 (아이콘·규칙 카드용): (cx, top) 부터 폭 w, 높이 h.
     * 구멍은 그리지 않는다.
     */
    fun drawSimpleBarrel(c: Canvas, cx: Float, top: Float, w: Float, h: Float, p: Paint, path: Path) {
        val rx = w * 0.42f
        val ry = h * 0.08f
        val bot = top + h
        p.style = Paint.Style.FILL
        p.shader = null
        // 안쪽 (어두운 입구)
        p.color = 0xFF24130A.toInt()
        c.drawOval(cx - rx, top - ry, cx + rx, top + ry, p)
        // 몸통
        path.reset()
        path.moveTo(cx - rx, top)
        path.quadTo(cx, top + ry * 2f, cx + rx, top)
        path.cubicTo(cx + w * 0.56f, top + h * 0.3f, cx + w * 0.56f, bot - h * 0.3f, cx + rx, bot - ry)
        path.quadTo(cx, bot + ry, cx - rx, bot - ry)
        path.cubicTo(cx - w * 0.56f, bot - h * 0.3f, cx - w * 0.56f, top + h * 0.3f, cx - rx, top)
        path.close()
        p.color = WOOD
        c.drawPath(path, p)
        // 밝은 가운데 판
        p.color = WOOD_LIGHT
        c.drawRect(cx - w * 0.1f, top + ry * 1.2f, cx + w * 0.06f, bot - ry * 1.2f, p)
        // 판자 줄
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.012f
        p.color = WOOD_DARK
        for (k in -2..2) {
            if (k == 0) continue
            val x = cx + k * w * 0.17f
            c.drawLine(x, top + ry * 1.3f, x, bot - ry * 1.3f, p)
        }
        // 쇠테 두 줄
        p.strokeWidth = h * 0.06f
        p.color = METAL
        path.reset()
        path.moveTo(cx - w * 0.47f, top + h * 0.16f)
        path.quadTo(cx, top + h * 0.24f, cx + w * 0.47f, top + h * 0.16f)
        path.moveTo(cx - w * 0.47f, bot - h * 0.18f)
        path.quadTo(cx, bot - h * 0.1f, cx + w * 0.47f, bot - h * 0.18f)
        c.drawPath(path, p)
        // 입구 테두리
        p.strokeWidth = h * 0.035f
        p.color = WOOD_LIGHT
        c.drawOval(cx - rx, top - ry, cx + rx, top + ry, p)
        p.style = Paint.Style.FILL
    }

    /** 구멍 하나 (아이콘·규칙 카드용) */
    fun drawSimpleHole(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = WOOD_DARK
        c.drawCircle(x, y, r * 1.3f, p)
        p.color = 0xFF140A04.toInt()
        c.drawCircle(x, y, r, p)
    }
}
