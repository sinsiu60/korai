package com.siwoo.party.games.count

import com.siwoo.party.common.Zone

/** 배치용 상자 */
class Box(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
    fun intersects(o: Box): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/**
 * 화면 배치.
 * - 그림 칸(flash): 화면 한가운데 (모두가 볼 수 있게). 2인 = 세로로 긴 칸, 3·4인 = 가로로 긴 칸.
 * - 구역마다 내용 칸(content): 그림 칸과 겹치지 않는 구역 바깥쪽. 2인은 그림 칸 옆, 3·4인은 그림 칸 위/아래.
 *   모든 구역의 내용 칸은 같은 크기 (공정성), 구역 방향(위쪽 구역 180°)으로 돌려 그린다.
 * - 내용 칸 안 (그 사람 기준 좌표, 내용 칸 가운데가 원점, x 오른쪽 / y 아래쪽):
 *   위 = 이름·라운드·점수 줄, 시간 막대 / 아래 = 보기 4개 (가로 칸이면 한 줄, 세로 칸이면 2×2).
 */
class CountLayout {
    val flash = Box()
    var contents: Array<Box> = emptyArray()
        private set
    var rotations = FloatArray(0)
        private set
    var cellW = 0f
        private set
    var cellH = 0f
        private set
    /** 세로로 긴 내용 칸 (2인): 보기 2×2 + 가운데 안내 줄 */
    var tall = false
        private set

    // ── 내용 칸 안 (사람 기준) ──
    var headerY = 0f
        private set
    var headerH = 0f
        private set
    val timeBar = Box()
    val choices = Array(GameConfig.CHOICES) { Box() }
    /** 세로 칸의 안내 글자 가운데 y (가로 칸은 headerY 를 같이 쓴다) */
    var statusY = 0f
        private set

    /** zones = TouchZones 결과, pad = 칸 안쪽 여백(px) */
    fun layout(zones: List<Zone>, width: Float, height: Float, pad: Float) {
        val n = zones.size
        val cx = width / 2f
        val cy = height / 2f
        if (n == 2) {
            val fw = width * FLASH2_W
            val fh = height * FLASH2_H
            flash.set(cx - fw / 2f, cy - fh / 2f, cx + fw / 2f, cy + fh / 2f)
        } else {
            val fw = width * FLASH4_W
            val fh = height * FLASH4_H
            flash.set(cx - fw / 2f, cy - fh / 2f, cx + fw / 2f, cy + fh / 2f)
        }
        val raw = Array(n) { Box() }
        for (z in zones) {
            val b = raw[z.index]
            if (n == 2) {
                if (z.centerX < cx) b.set(z.left + pad, z.top + pad, flash.left - pad, z.bottom - pad)
                else b.set(flash.right + pad, z.top + pad, z.right - pad, z.bottom - pad)
            } else {
                if (z.centerY < cy) b.set(z.left + pad, z.top + pad, z.right - pad, flash.top - pad)
                else b.set(z.left + pad, flash.bottom + pad, z.right - pad, z.bottom - pad)
            }
        }
        // 모든 구역 같은 크기: 가장 작은 폭·높이로 각자 가운데 맞춤
        cellW = raw.minOf { it.width }.coerceAtLeast(1f)
        cellH = raw.minOf { it.height }.coerceAtLeast(1f)
        contents = Array(n) { i ->
            val b = raw[i]
            Box(b.centerX - cellW / 2f, b.centerY - cellH / 2f, b.centerX + cellW / 2f, b.centerY + cellH / 2f)
        }
        rotations = FloatArray(n)
        for (z in zones) rotations[z.index] = z.rotation
        tall = cellH > cellW
        layoutCell()
    }

    private fun layoutCell() {
        val w = cellW
        val h = cellH
        val top = -h / 2f
        if (tall) {
            headerH = minOf(h * 0.1f, w * 0.17f)
            headerY = top + headerH / 2f
            val bt = top + headerH + h * 0.015f
            timeBar.set(-w * 0.48f, bt, w * 0.48f, bt + maxOf(h * 0.014f, 3f))
            val gap = w * 0.05f
            val bw = (w - gap) / 2f
            val bh = minOf(bw * 0.78f, h * 0.22f)
            val bottom = h / 2f
            for (k in 0 until GameConfig.CHOICES) {
                val col = k % 2
                val row = k / 2
                val l = -w / 2f + col * (bw + gap)
                val b = bottom - (1 - row) * (bh + gap)
                choices[k].set(l, b - bh, l + bw, b)
            }
            statusY = (timeBar.bottom + choices[0].top) / 2f
        } else {
            headerH = h * 0.24f
            headerY = top + headerH / 2f
            val bt = top + headerH + h * 0.02f
            timeBar.set(-w * 0.48f, bt, w * 0.48f, bt + maxOf(h * 0.04f, 3f))
            val gap = w * 0.03f
            val bw = (w - gap * (GameConfig.CHOICES - 1)) / GameConfig.CHOICES
            val ct = timeBar.bottom + h * 0.08f
            for (k in 0 until GameConfig.CHOICES) {
                val l = -w / 2f + k * (bw + gap)
                choices[k].set(l, ct, l + bw, h / 2f)
            }
            statusY = headerY
        }
    }

    /** 사람 기준 좌표 → 보기 번호 (보기 사이 틈·바깥은 -1) */
    fun choiceAtLocal(lx: Float, ly: Float): Int {
        for (k in choices.indices) if (choices[k].contains(lx, ly)) return k
        return -1
    }

    /** 화면 좌표 (x, y) → zone 구역의 보기 번호 (-1 = 보기 아님) */
    fun choiceAt(zone: Int, x: Float, y: Float, tmp: FloatArray): Int {
        val b = contents.getOrNull(zone) ?: return -1
        toLocal(b.centerX, b.centerY, rotations[zone], x, y, tmp)
        return choiceAtLocal(tmp[0], tmp[1])
    }

    /** zone 구역 k 번째 보기 가운데 화면 좌표 → out */
    fun choiceCenter(zone: Int, k: Int, out: FloatArray) {
        val b = contents[zone]
        val c = choices[k]
        toScreen(b.centerX, b.centerY, rotations[zone], c.centerX, c.centerY, out)
    }

    companion object {
        /** 2인 그림 칸 (화면 폭·높이 비율) */
        const val FLASH2_W = 0.40f
        const val FLASH2_H = 0.80f
        /** 3·4인 그림 칸 */
        const val FLASH4_W = 0.56f
        const val FLASH4_H = 0.40f

        /** 화면 좌표 (x, y) → (가운데 cx, cy, 회전 rotation°) 사람 기준 좌표 out */
        fun toLocal(cx: Float, cy: Float, rotation: Float, x: Float, y: Float, out: FloatArray) {
            val dx = x - cx
            val dy = y - cy
            if (rotation == 0f) { out[0] = dx; out[1] = dy; return }
            if (rotation == 180f) { out[0] = -dx; out[1] = -dy; return }
            val a = Math.toRadians(-rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = dx * cos - dy * sin
            out[1] = dx * sin + dy * cos
        }

        /** 사람 기준 좌표 → 화면 좌표 out */
        fun toScreen(cx: Float, cy: Float, rotation: Float, lx: Float, ly: Float, out: FloatArray) {
            if (rotation == 0f) { out[0] = cx + lx; out[1] = cy + ly; return }
            if (rotation == 180f) { out[0] = cx - lx; out[1] = cy - ly; return }
            val a = Math.toRadians(rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = cx + lx * cos - ly * sin
            out[1] = cy + lx * sin + ly * cos
        }
    }
}
