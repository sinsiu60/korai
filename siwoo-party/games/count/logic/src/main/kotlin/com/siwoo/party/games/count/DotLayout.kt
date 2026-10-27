package com.siwoo.party.games.count

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random

/** 그림 모양 (점·별 섞기) */
enum class DotShape { CIRCLE, STAR, SQUARE, TRIANGLE }

/**
 * 그림 하나. 좌표는 그림 칸 높이 = 1 인 단위 (x 0..aspect, y 0..1), r = 반지름.
 * color = 팔레트 번호 (0 until DOT_COLORS)
 */
class Dot(val x: Float, val y: Float, val r: Float, val shape: DotShape, val color: Int)

/**
 * 겹치지 않는 그림 배치 (같은 씨앗이면 같은 배치 → 공개 때 똑같은 그림을 다시 보여 준다).
 * 크기는 개수와 상관없이 칸 모양(aspect)으로만 정한다 → 크기로 개수를 짐작할 수 없게.
 * 무작위로 놓아 보고 겹치면 다시 (여러 번 실패하면 처음부터, 그래도 안 되면 크기를 조금 줄인다).
 */
object DotLayout {

    /** 칸 모양(가로/세로)에 맞는 기준 반지름: 가장 많은 개수도 넉넉히 들어가게 */
    fun baseRadius(aspect: Float): Float {
        val e = GameConfig.DOT_EDGE
        val area = (aspect - 2 * e).coerceAtLeast(0.1f) * (1f - 2 * e)
        val re = sqrt(GameConfig.DOT_FILL * area / (GameConfig.COUNT_MAX * PI.toFloat()))
        return (re / GameConfig.DOT_SPACING).coerceAtMost(GameConfig.DOT_RADIUS_MAX)
    }

    /** 두 그림 사이 최소 거리 (가운데끼리) */
    fun minDistance(a: Dot, b: Dot): Float = (a.r + b.r) * GameConfig.DOT_SPACING

    /** count 개를 aspect × 1 칸 안에 겹치지 않게 놓는다 */
    fun generate(count: Int, aspect: Float, seed: Long): List<Dot> {
        val random = Random(seed)
        var base = baseRadius(aspect)
        while (true) {
            repeat(RESTARTS) {
                val out = tryPlace(count, aspect, base, random)
                if (out != null) return out
            }
            base *= 0.9f
        }
    }

    private fun tryPlace(count: Int, aspect: Float, base: Float, random: Random): List<Dot>? {
        val out = ArrayList<Dot>(count)
        val e = GameConfig.DOT_EDGE
        for (n in 0 until count) {
            var placed = false
            for (attempt in 0 until ATTEMPTS) {
                val s = GameConfig.DOT_SCALE_MIN + random.nextFloat() * (GameConfig.DOT_SCALE_MAX - GameConfig.DOT_SCALE_MIN)
                val r = base * s
                val lo = e + r
                val hiX = aspect - e - r
                val hiY = 1f - e - r
                if (hiX <= lo || hiY <= lo) return null
                val x = lo + random.nextFloat() * (hiX - lo)
                val y = lo + random.nextFloat() * (hiY - lo)
                val shape = SHAPES[random.nextInt(SHAPES.size)]
                val d = Dot(x, y, r, shape, random.nextInt(GameConfig.DOT_COLORS))
                if (out.none { overlaps(it, d) }) {
                    out.add(d)
                    placed = true
                    break
                }
            }
            if (!placed) return null
        }
        return out
    }

    fun overlaps(a: Dot, b: Dot): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val m = minDistance(a, b)
        return dx * dx + dy * dy < m * m
    }

    private val SHAPES = DotShape.values()
    private const val ATTEMPTS = 400
    private const val RESTARTS = 8
}
