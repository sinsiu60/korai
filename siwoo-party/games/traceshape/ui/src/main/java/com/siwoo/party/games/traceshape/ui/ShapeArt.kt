package com.siwoo.party.games.traceshape.ui

import android.graphics.Path
import com.siwoo.party.games.traceshape.Shape

/** 도형 꺾은선 → Path (화면·규칙 그림 공용, 할당 없음 — 넘겨받은 Path·배열에 채운다) */
object ShapeArt {

    /**
     * shape 의 u0..u1 (길이 비율) 구간을 상자 (l, t, 한 변 s) 에 맞춰 path 에 담는다.
     * s = 1, l = t = 0 이면 상자 좌표 그대로 (캔버스 scale 로 그릴 때).
     */
    fun build(shape: Shape, path: Path, l: Float, t: Float, s: Float, u0: Float = 0f, u1: Float = 1f, tmp: FloatArray) {
        path.reset()
        val total = shape.length
        val d0 = u0 * total
        val d1 = u1 * total
        shape.pointAt(u0, tmp)
        path.moveTo(l + tmp[0] * s, t + tmp[1] * s)
        var acc = 0f
        for (i in 1 until shape.size) {
            acc += Shape.hypot(shape.xs[i] - shape.xs[i - 1], shape.ys[i] - shape.ys[i - 1])
            if (acc <= d0) continue
            if (acc >= d1) break
            path.lineTo(l + shape.xs[i] * s, t + shape.ys[i] * s)
        }
        shape.pointAt(u1, tmp)
        path.lineTo(l + tmp[0] * s, t + tmp[1] * s)
        if (shape.closed && u0 <= 0f && u1 >= 1f) path.close()
    }

    /**
     * 진행 방향 화살촉 (길이 비율 u 지점, 크기 a) — 상자 좌표 그대로.
     * tmp 는 4칸 (점 + 방향).
     */
    fun arrow(shape: Shape, path: Path, u: Float, a: Float, l: Float, t: Float, s: Float, tmp: FloatArray) {
        shape.pointAt(u, tmp)
        val x = l + tmp[0] * s
        val y = t + tmp[1] * s
        val dx = tmp[2]
        val dy = tmp[3]
        val size = a * s
        path.reset()
        path.moveTo(x + dx * size, y + dy * size)
        path.lineTo(x - dx * size * 0.6f - dy * size * 0.75f, y - dy * size * 0.6f + dx * size * 0.75f)
        path.lineTo(x - dx * size * 0.25f, y - dy * size * 0.25f)
        path.lineTo(x - dx * size * 0.6f + dy * size * 0.75f, y - dy * size * 0.6f - dx * size * 0.75f)
        path.close()
    }
}
