package com.siwoo.party.games.spotdiff

import com.siwoo.party.common.Zone

/**
 * 화면 배치 (픽셀, 순수 계산 → 테스트 가능).
 * 가운데 띠: 위 머리줄(장면·찾은 개수·남은 시간) / 두 그림(왼쪽 원래, 오른쪽 차이) / 아래 안내줄.
 * 플레이어 구역(버저)은 동시 터치 구역 배치를 따른다 (TouchZones 와 같은 자리·방향):
 *   2인: 왼쪽 기둥 / 오른쪽 기둥 (둘 다 0°)
 *   3인: 왼쪽 기둥·오른쪽 기둥(맞은편, 180°) / 아래 가운데(0°, 그림 아래)
 *   4인: 네 귀퉁이 (위 둘 180°)
 * 구역 어디를 눌러도 그 사람 버저. 버저 동그라미는 구역 안에서 그 사람 쪽(구역 기준 아래)에 그린다.
 */
class SpotLayout(val count: Int, val width: Float, val height: Float, val dp: Float) {

    val panels: List<Zone>
    /** 버저 가운데 (화면 좌표)·반지름 */
    val buzzX = FloatArray(count)
    val buzzY = FloatArray(count)
    val buzzR = FloatArray(count)
    /** 그림 두 장 [왼쪽, 오른쪽] 화면 사각형 */
    val picLeft = FloatArray(2)
    val picTop: Float
    val picW: Float
    val picH: Float
    /** 그림 좌표 1 = 화면 scale 픽셀 */
    val scale: Float
    /** 머리줄·안내줄 */
    val headerLeft: Float
    val headerTop: Float
    val headerRight: Float
    val headerBottom: Float
    val statusTop: Float
    val statusBottom: Float

    init {
        require(count in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        val side = (width * 0.155f).coerceIn(88f * dp, 128f * dp)
        val gap = 6f * dp
        val w = width
        val h = height
        val bottomW = minOf(w * 0.36f, 240f * dp)
        val bottomH = (h * 0.27f).coerceIn(80f * dp, 110f * dp)
        panels = when (count) {
            2 -> listOf(
                Zone(0, 0f, 0f, side, h, 0f),
                Zone(1, w - side, 0f, w, h, 0f),
            )
            3 -> listOf(
                Zone(0, 0f, 0f, side, h, 180f),
                Zone(1, w - side, 0f, w, h, 180f),
                Zone(2, (w - bottomW) / 2f, h - bottomH, (w + bottomW) / 2f, h, 0f),
            )
            else -> listOf(
                Zone(0, 0f, 0f, side, h / 2f - gap / 2f, 180f),
                Zone(1, w - side, 0f, w, h / 2f - gap / 2f, 180f),
                Zone(2, 0f, h / 2f + gap / 2f, side, h, 0f),
                Zone(3, w - side, h / 2f + gap / 2f, w, h, 0f),
            )
        }
        // 버저: 구역 기준 좌표(그 사람이 볼 때)에서 자리를 정해 화면 좌표로
        for (z in panels) {
            val pad = 12f * dp
            val lx: Float
            val ly: Float
            val r: Float
            if (z.height >= z.width * 1.2f) {
                r = minOf(z.width * 0.40f, z.height * 0.30f)
                lx = z.width / 2f
                ly = z.height - pad - r
            } else {
                r = minOf(z.height * 0.40f, z.width * 0.22f)
                lx = z.width - pad - r
                ly = z.height / 2f
            }
            buzzR[z.index] = r
            if (z.rotation == 180f) {
                buzzX[z.index] = z.right - lx
                buzzY[z.index] = z.bottom - ly
            } else {
                buzzX[z.index] = z.left + lx
                buzzY[z.index] = z.top + ly
            }
        }
        // 가운데 띠
        val x0 = side + gap * 1.5f
        val x1 = w - side - gap * 1.5f
        val picGap = 10f * dp
        val headerH = 26f * dp
        val statusH = 24f * dp
        val availTop = gap
        val availBottom = if (count == 3) panels[2].top - gap else h - gap
        val maxByWidth = (x1 - x0 - picGap) / 2f
        val maxByHeight = (availBottom - availTop - headerH - statusH - 2 * gap) * GameConfig.SCENE_W / GameConfig.SCENE_H
        picW = minOf(maxByWidth, maxByHeight)
        picH = picW * GameConfig.SCENE_H / GameConfig.SCENE_W
        scale = picW / GameConfig.SCENE_W
        val blockH = headerH + gap + picH + gap + statusH
        val blockTop = availTop + (availBottom - availTop - blockH) / 2f
        val total = picW * 2 + picGap
        val px0 = (x0 + x1) / 2f - total / 2f
        picLeft[0] = px0
        picLeft[1] = px0 + picW + picGap
        headerLeft = px0
        headerRight = px0 + total
        headerTop = blockTop
        headerBottom = blockTop + headerH
        picTop = headerBottom + gap
        statusTop = picTop + picH + gap
        statusBottom = statusTop + statusH
    }

    /** (x, y) 가 속한 플레이어 구역, 없으면 -1 */
    fun panelAt(x: Float, y: Float): Int {
        for (z in panels) if (z.contains(x, y)) return z.index
        return -1
    }

    /** (x, y) 가 있는 그림 (0 왼쪽, 1 오른쪽, -1 그림 밖) */
    fun pictureAt(x: Float, y: Float): Int {
        if (y < picTop || y >= picTop + picH) return -1
        for (k in 0..1) if (x >= picLeft[k] && x < picLeft[k] + picW) return k
        return -1
    }

    /** 화면 → 그림 좌표 (out[0], out[1]) */
    fun toScene(pic: Int, x: Float, y: Float, out: FloatArray) {
        out[0] = (x - picLeft[pic]) / scale
        out[1] = (y - picTop) / scale
    }

    /** 그림 좌표 → 화면 (out[0], out[1]) */
    fun toScreen(pic: Int, sx: Float, sy: Float, out: FloatArray) {
        out[0] = picLeft[pic] + sx * scale
        out[1] = picTop + sy * scale
    }
}
