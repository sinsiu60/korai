package com.siwoo.party.games.matches.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * 성냥개비·불꽃 그리기 (판·날아가는 성냥·아이콘·규칙 카드가 같이 쓴다).
 * 모두 Canvas 도형으로, 그릴 때 객체를 만들지 않는다 (Path 는 부르는 쪽이 재사용할 것을 넘긴다).
 */
object MatchArt {

    // 나무 막대
    private const val WOOD = 0xFFF2D29B.toInt()
    private const val WOOD_SHADE = 0xFFD2A867.toInt()
    private const val WOOD_LIGHT = 0xFFFFF0CF.toInt()
    private const val WOOD_END = 0xFFC08F4E.toInt()
    // 빨간 머리
    const val HEAD = 0xFFE53935.toInt()
    private const val HEAD_DARK = 0xFF9A1B1B.toInt()
    private const val HEAD_LIGHT = 0xFFFF8A80.toInt()
    // 탄 색
    private const val CHAR = 0xFF2B1F1A.toInt()
    private const val CHAR_HEAD = 0xFF1C1414.toInt()
    private const val EMBER = 0xFFFF7A1A.toInt()
    // 불꽃
    const val FLAME_OUT = 0xFFFF6A13.toInt()
    const val FLAME_MID = 0xFFFFB627.toInt()
    const val FLAME_CORE = 0xFFFFF3C4.toInt()

    /** 막대 굵기 / 길이 */
    const val THICK = 0.095f
    /** 머리 높이 / 길이 */
    const val HEAD_LEN = 0.17f

    /** 두 색 섞기 (f = 0 → a, 1 → b) */
    fun mix(a: Int, b: Int, f: Float): Int {
        val g = f.coerceIn(0f, 1f)
        val aa = (a ushr 24) and 0xFF; val ab = (b ushr 24) and 0xFF
        val ra = (a shr 16) and 0xFF; val rb = (b shr 16) and 0xFF
        val ga = (a shr 8) and 0xFF; val gb = (b shr 8) and 0xFF
        val ba = a and 0xFF; val bb = b and 0xFF
        return ((aa + (ab - aa) * g).toInt() shl 24) or ((ra + (rb - ra) * g).toInt() shl 16) or
            ((ga + (gb - ga) * g).toInt() shl 8) or (ba + (bb - ba) * g).toInt()
    }

    /** 색의 투명도를 a(0~1) 배로 */
    fun fade(color: Int, a: Float): Int {
        val al = (((color ushr 24) and 0xFF) * a.coerceIn(0f, 1f)).toInt()
        return (al shl 24) or (color and 0xFFFFFF)
    }

    /**
     * 성냥 한 개: (cx, cy) = 막대 가운데, 길이 len, 머리는 위쪽, deg 만큼 기울임.
     * burnt 0~1 = 위에서부터 타 들어간 정도 (머리는 조금만 타도 까매짐), alpha 0~1.
     */
    fun drawMatch(c: Canvas, cx: Float, cy: Float, len: Float, deg: Float, burnt: Float, alpha: Float, p: Paint) {
        val w = len * THICK
        val hw = w / 2f
        val top = -len / 2f
        val bottom = len / 2f
        val headH = len * HEAD_LEN
        c.save()
        c.translate(cx, cy)
        if (deg != 0f) c.rotate(deg)
        p.style = Paint.Style.FILL
        // 그림자
        p.color = fade(0x55000000, alpha)
        c.drawRoundRect(-hw + w * 0.5f, top + headH * 0.5f + w * 0.6f, hw + w * 0.5f, bottom + w * 0.6f, hw, hw, p)
        // 막대
        p.color = fade(WOOD, alpha)
        c.drawRoundRect(-hw, top + headH * 0.4f, hw, bottom, hw * 0.5f, hw * 0.5f, p)
        p.color = fade(WOOD_SHADE, alpha)
        c.drawRect(hw * 0.25f, top + headH * 0.5f, hw, bottom - hw * 0.4f, p)
        p.color = fade(WOOD_LIGHT, alpha)
        c.drawRect(-hw * 0.7f, top + headH, -hw * 0.25f, bottom - w, p)
        p.color = fade(WOOD_END, alpha)
        c.drawRoundRect(-hw, bottom - w * 0.55f, hw, bottom, hw * 0.5f, hw * 0.5f, p)
        // 탄 부분 (위에서부터) + 불씨 줄
        if (burnt > 0f) {
            val front = top + headH * 0.4f + (len - headH * 0.4f) * 0.62f * burnt
            p.color = fade(CHAR, alpha)
            c.drawRect(-hw * 0.92f, top + headH * 0.4f, hw * 0.92f, front, p)
            if (burnt < 1f) {
                p.color = fade(EMBER, alpha)
                c.drawRect(-hw * 0.92f, front - w * 0.35f, hw * 0.92f, front + w * 0.15f, p)
            }
        }
        // 머리 (빨강 → 타면 검정)
        val hb = (burnt * 4f).coerceIn(0f, 1f)
        val hcx = 0f
        val hcy = top + headH * 0.5f
        val hwid = w * 0.88f
        p.color = fade(mix(HEAD_DARK, CHAR_HEAD, hb), alpha)
        c.drawOval(hcx - hwid, hcy - headH * 0.5f, hcx + hwid, hcy + headH * 0.55f, p)
        p.color = fade(mix(HEAD, CHAR_HEAD, hb), alpha)
        c.drawOval(hcx - hwid * 0.92f, hcy - headH * 0.5f, hcx + hwid * 0.82f, hcy + headH * 0.42f, p)
        p.color = fade(mix(HEAD_LIGHT, CHAR, hb), alpha * 0.9f)
        c.drawOval(hcx - hwid * 0.6f, hcy - headH * 0.38f, hcx - hwid * 0.05f, hcy - headH * 0.02f, p)
        c.restore()
    }

    /**
     * 불꽃: 아래 가운데 (x, baseY), 높이 h, sway(-1~1) 만큼 끝이 흔들림.
     * 바깥 주황 → 노랑 → 가운데 밝은 심지 세 겹 + 은은한 빛.
     */
    fun drawFlame(c: Canvas, x: Float, baseY: Float, h: Float, sway: Float, alpha: Float, path: Path, p: Paint) {
        if (h <= 0f) return
        p.style = Paint.Style.FILL
        // 빛 무리
        p.color = fade(0x40FFB627, alpha)
        c.drawCircle(x, baseY - h * 0.35f, h * 0.75f, p)
        p.color = fade(0x30FF6A13, alpha)
        c.drawCircle(x, baseY - h * 0.3f, h * 1.05f, p)
        layer(c, x, baseY, h, h * 0.36f, sway, fade(FLAME_OUT, alpha), path, p)
        layer(c, x, baseY - h * 0.03f, h * 0.74f, h * 0.25f, sway * 0.8f, fade(FLAME_MID, alpha), path, p)
        layer(c, x, baseY - h * 0.05f, h * 0.42f, h * 0.13f, sway * 0.5f, fade(FLAME_CORE, alpha), path, p)
    }

    /** 물방울을 거꾸로 한 모양 한 겹 */
    private fun layer(c: Canvas, x: Float, baseY: Float, h: Float, r: Float, sway: Float, color: Int, path: Path, p: Paint) {
        val tipX = x + sway * h * 0.18f
        val tipY = baseY - h
        val cy = baseY - r
        path.reset()
        path.moveTo(tipX, tipY)
        path.cubicTo(tipX + r * 0.3f, tipY + h * 0.35f, x + r * 1.15f, cy - r * 0.4f, x + r, cy)
        path.cubicTo(x + r, cy + r * 0.75f, x + r * 0.45f, baseY, x, baseY)
        path.cubicTo(x - r * 0.45f, baseY, x - r, cy + r * 0.75f, x - r, cy)
        path.cubicTo(x - r * 1.15f, cy - r * 0.4f, tipX - r * 0.3f, tipY + h * 0.35f, tipX, tipY)
        path.close()
        p.color = color
        c.drawPath(path, p)
    }

    /** 해골 (패배 표시): (x, y) 가운데, 크기 s */
    fun drawSkull(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFFF2F2F2.toInt()
        c.drawCircle(x, y - s * 0.08f, s * 0.42f, p)
        c.drawRect(x - s * 0.22f, y + s * 0.15f, x + s * 0.22f, y + s * 0.45f, p)
        p.color = 0xFF1A2040.toInt()
        c.drawCircle(x - s * 0.15f, y - s * 0.08f, s * 0.11f, p)
        c.drawCircle(x + s * 0.15f, y - s * 0.08f, s * 0.11f, p)
    }
}
