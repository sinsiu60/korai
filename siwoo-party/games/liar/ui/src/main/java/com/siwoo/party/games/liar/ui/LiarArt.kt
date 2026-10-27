package com.siwoo.party.games.liar.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme

/**
 * 라이어 게임 그림 모음 (모두 도형, 할당 없음 — Paint/Path 는 부르는 쪽 것을 재사용).
 * 카테고리 아이콘 6개, 가면 쓴 라이어 얼굴, 눈(가림/보임), 투표 용지.
 */
object LiarArt {
    /** 카테고리 색 (음식·장소·동물·직업·스포츠·물건) */
    val CATEGORY_COLORS = intArrayOf(
        0xFFFF8A3D.toInt(),
        0xFF1FA88E.toInt(),
        0xFFB0703C.toInt(),
        0xFF3D7BFF.toInt(),
        0xFFE5395A.toInt(),
        0xFF9A62F0.toInt(),
    )

    const val SKIN = 0xFFFFD3A5.toInt()
    const val MASK = 0xFF2A1440.toInt()
    const val NOSE = 0xFFE89A5F.toInt()
    private const val NOSE_DARK = 0xFFB9764A.toInt()
    const val LIAR_RED = 0xFFE53950.toInt()
    const val CITIZEN_BLUE = 0xFF2F7BEF.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** 색을 45% 밝기로 (그리는 중에 써도 할당 없음) */
    fun dark(color: Int): Int {
        val r = ((color shr 16) and 0xFF) * 45 / 100
        val g = ((color shr 8) and 0xFF) * 45 / 100
        val b = (color and 0xFF) * 45 / 100
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    fun categoryColor(k: Int): Int = CATEGORY_COLORS[((k % CATEGORY_COLORS.size) + CATEGORY_COLORS.size) % CATEGORY_COLORS.size]

    /** 카테고리 k 아이콘: (cx, cy) 중심, 한 변 s. color = 주 색 (보통 흰색 또는 카테고리 색) */
    fun drawCategory(c: Canvas, k: Int, cx: Float, cy: Float, s: Float, color: Int, p: Paint, path: Path) {
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        when (k) {
            0 -> { // 음식: 그릇 + 밥 + 젓가락
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.06f
                c.drawLine(cx + s * 0.05f, cy - s * 0.05f, cx + s * 0.38f, cy - s * 0.44f, p)
                c.drawLine(cx + s * 0.16f, cy - s * 0.02f, cx + s * 0.46f, cy - s * 0.36f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx - s * 0.02f, cy - s * 0.02f, s * 0.26f, p)
                c.drawArc(cx - s * 0.42f, cy - s * 0.32f, cx + s * 0.42f, cy + s * 0.38f, 0f, 180f, true, p)
                c.drawRoundRect(cx - s * 0.16f, cy + s * 0.3f, cx + s * 0.16f, cy + s * 0.4f, s * 0.04f, s * 0.04f, p)
            }
            1 -> { // 장소: 지도 핀
                path.reset()
                path.moveTo(cx, cy + s * 0.46f)
                path.lineTo(cx - s * 0.26f, cy - s * 0.02f)
                path.lineTo(cx + s * 0.26f, cy - s * 0.02f)
                path.close()
                c.drawPath(path, p)
                c.drawCircle(cx, cy - s * 0.14f, s * 0.3f, p)
                p.color = dark(color)
                c.drawCircle(cx, cy - s * 0.14f, s * 0.12f, p)
            }
            2 -> { // 동물: 발바닥
                c.drawOval(cx - s * 0.24f, cy - s * 0.02f, cx + s * 0.24f, cy + s * 0.38f, p)
                c.drawOval(cx - s * 0.42f, cy - s * 0.2f, cx - s * 0.24f, cy + s * 0.04f, p)
                c.drawOval(cx - s * 0.2f, cy - s * 0.42f, cx - s * 0.02f, cy - s * 0.14f, p)
                c.drawOval(cx + s * 0.02f, cy - s * 0.42f, cx + s * 0.2f, cy - s * 0.14f, p)
                c.drawOval(cx + s * 0.24f, cy - s * 0.2f, cx + s * 0.42f, cy + s * 0.04f, p)
            }
            3 -> { // 직업: 서류 가방
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.07f
                c.drawRoundRect(cx - s * 0.14f, cy - s * 0.36f, cx + s * 0.14f, cy - s * 0.14f, s * 0.05f, s * 0.05f, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(cx - s * 0.44f, cy - s * 0.2f, cx + s * 0.44f, cy + s * 0.36f, s * 0.08f, s * 0.08f, p)
                p.color = dark(color)
                c.drawRect(cx - s * 0.44f, cy + s * 0.02f, cx + s * 0.44f, cy + s * 0.06f, p)
                c.drawRoundRect(cx - s * 0.07f, cy - s * 0.02f, cx + s * 0.07f, cy + s * 0.12f, s * 0.02f, s * 0.02f, p)
            }
            4 -> { // 스포츠: 공 (농구공 줄무늬)
                c.drawCircle(cx, cy, s * 0.4f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.045f
                p.color = dark(color)
                c.drawLine(cx - s * 0.4f, cy, cx + s * 0.4f, cy, p)
                c.drawLine(cx, cy - s * 0.4f, cx, cy + s * 0.4f, p)
                c.drawArc(cx - s * 0.62f, cy - s * 0.4f, cx - s * 0.06f, cy + s * 0.4f, -60f, 120f, false, p)
                c.drawArc(cx + s * 0.06f, cy - s * 0.4f, cx + s * 0.62f, cy + s * 0.4f, 120f, 120f, false, p)
                p.style = Paint.Style.FILL
            }
            else -> { // 물건: 선물 상자
                c.drawRect(cx - s * 0.36f, cy - s * 0.06f, cx + s * 0.36f, cy + s * 0.4f, p)
                c.drawRect(cx - s * 0.42f, cy - s * 0.2f, cx + s * 0.42f, cy - s * 0.04f, p)
                p.color = dark(color)
                c.drawRect(cx - s * 0.06f, cy - s * 0.2f, cx + s * 0.06f, cy + s * 0.4f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.06f
                p.color = color
                c.drawOval(cx - s * 0.3f, cy - s * 0.4f, cx - s * 0.02f, cy - s * 0.2f, p)
                c.drawOval(cx + s * 0.02f, cy - s * 0.4f, cx + s * 0.3f, cy - s * 0.2f, p)
                p.style = Paint.Style.FILL
            }
        }
        p.style = Paint.Style.FILL
    }

    /** 가면 쓴 라이어 얼굴 + 긴 코 (중심 cx, cy, 얼굴 반지름 r). 코는 오른쪽으로 r 만큼 나온다 */
    fun drawLiarFace(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint, path: Path) {
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        c.drawCircle(cx + r * 0.05f, cy + r * 0.08f, r, p)
        p.color = SKIN
        c.drawCircle(cx, cy, r, p)
        // 머리카락 (위쪽 반달)
        p.color = 0xFF3B2414.toInt()
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 192f, 156f, false, p)
        // 가면: 눈 위를 가로지르는 띠 + 양옆 끈
        p.color = MASK
        c.drawRoundRect(cx - r * 0.86f, cy - r * 0.36f, cx + r * 0.86f, cy + r * 0.06f, r * 0.2f, r * 0.2f, p)
        path.reset()
        path.moveTo(cx - r * 0.8f, cy - r * 0.2f)
        path.lineTo(cx - r * 1.12f, cy - r * 0.38f)
        path.lineTo(cx - r * 1.08f, cy - r * 0.06f)
        path.close()
        c.drawPath(path, p)
        // 눈 (흰자 + 옆으로 흘깃 보는 눈동자)
        p.color = WHITE
        c.drawOval(cx - r * 0.56f, cy - r * 0.26f, cx - r * 0.18f, cy - r * 0.04f, p)
        c.drawOval(cx + r * 0.18f, cy - r * 0.26f, cx + r * 0.56f, cy - r * 0.04f, p)
        p.color = 0xFF111111.toInt()
        c.drawCircle(cx - r * 0.26f, cy - r * 0.15f, r * 0.08f, p)
        c.drawCircle(cx + r * 0.48f, cy - r * 0.15f, r * 0.08f, p)
        // 히죽 입
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.08f
        p.color = 0xFF8A3B22.toInt()
        c.drawArc(cx - r * 0.42f, cy + r * 0.2f, cx + r * 0.1f, cy + r * 0.62f, 20f, 130f, false, p)
        p.style = Paint.Style.FILL
        // 긴 코 (거짓말하면 길어지는)
        p.color = NOSE_DARK
        c.drawRoundRect(cx - r * 0.02f, cy + r * 0.1f, cx + r * 1.08f, cy + r * 0.3f, r * 0.1f, r * 0.1f, p)
        p.color = NOSE
        c.drawRoundRect(cx - r * 0.02f, cy + r * 0.08f, cx + r * 1.04f, cy + r * 0.24f, r * 0.08f, r * 0.08f, p)
    }

    /** 눈: 감은 눈(가려짐) 또는 뜬 눈 (중심, 폭 w) */
    fun drawEye(c: Canvas, cx: Float, cy: Float, w: Float, open: Boolean, color: Int, p: Paint) {
        p.color = color
        p.strokeCap = Paint.Cap.ROUND
        if (open) {
            p.style = Paint.Style.FILL
            c.drawOval(cx - w / 2f, cy - w * 0.28f, cx + w / 2f, cy + w * 0.28f, p)
            p.color = Theme.INK
            c.drawCircle(cx, cy, w * 0.2f, p)
            p.color = WHITE
            c.drawCircle(cx + w * 0.07f, cy - w * 0.07f, w * 0.06f, p)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.08f
            c.drawArc(cx - w / 2f, cy - w * 0.4f, cx + w / 2f, cy + w * 0.3f, 15f, 150f, false, p)
            // 속눈썹
            c.drawLine(cx, cy + w * 0.3f, cx, cy + w * 0.44f, p)
            c.drawLine(cx - w * 0.26f, cy + w * 0.24f, cx - w * 0.34f, cy + w * 0.36f, p)
            c.drawLine(cx + w * 0.26f, cy + w * 0.24f, cx + w * 0.34f, cy + w * 0.36f, p)
        }
        p.style = Paint.Style.FILL
    }

    /** 사람 모양 (머리 + 몸) */
    fun drawPerson(c: Canvas, x: Float, y: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x, y - s * 0.55f, s * 0.32f, p)
        c.drawRoundRect(x - s * 0.5f, y - s * 0.12f, x + s * 0.5f, y + s * 0.6f, s * 0.3f, s * 0.3f, p)
    }

    /** 체크 표시 (투표 용지 등) */
    fun drawCheck(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.16f
        p.color = color
        c.drawLine(cx - s * 0.4f, cy, cx - s * 0.1f, cy + s * 0.3f, p)
        c.drawLine(cx - s * 0.1f, cy + s * 0.3f, cx + s * 0.45f, cy - s * 0.35f, p)
        p.style = Paint.Style.FILL
    }
}
