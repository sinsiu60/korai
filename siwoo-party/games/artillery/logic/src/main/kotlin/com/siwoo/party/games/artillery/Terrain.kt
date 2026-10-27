package com.siwoo.party.games.artillery

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 옆에서 본 땅 = 높이 표본 배열 (x = 0..WORLD_W 를 고르게 나눈 점의 높이, 사이는 직선으로 이음).
 * 지형은 부서지지 않는다 — 한 번 만들면 그대로.
 */
class Terrain(val heights: FloatArray, val width: Float = GameConfig.WORLD_W) {

    init {
        require(heights.size >= 2) { "표본이 2개 이상 필요" }
    }

    /** 표본 간격 */
    val dx: Float = width / (heights.size - 1)

    /** 가장 높은 곳 */
    val maxHeight: Float get() = heights.max()

    /** x 의 땅 높이 (전장 밖이면 가장자리 높이) */
    fun heightAt(x: Float): Float {
        if (x <= 0f) return heights[0]
        if (x >= width) return heights[heights.size - 1]
        val f = x / dx
        val i = f.toInt().coerceAtMost(heights.size - 2)
        val t = f - i
        return heights[i] + (heights[i + 1] - heights[i]) * t
    }

    /** x 근처(± half) 땅의 가장 높은 곳과 가장 낮은 곳의 차 (평평한지 확인용) */
    fun bumpiness(x: Float, half: Float): Float {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        var s = x - half
        while (s <= x + half + 1e-4f) {
            val h = heightAt(s)
            if (h < lo) lo = h
            if (h > hi) hi = h
            s += dx * 0.5f
        }
        return hi - lo
    }

    companion object {
        /** n 명의 대포 x 위치: 양 끝 CANNON_MARGIN 사이에 고르게 (왼쪽부터 0번 플레이어) */
        fun cannonXs(n: Int, width: Float = GameConfig.WORLD_W): FloatArray {
            require(n >= 1)
            if (n == 1) return floatArrayOf(width / 2f)
            val m = GameConfig.CANNON_MARGIN
            return FloatArray(n) { i -> m + (width - 2f * m) * i / (n - 1) }
        }

        /** 평평한 땅 (테스트용) */
        fun flat(h: Float, samples: Int = GameConfig.SAMPLES): Terrain = Terrain(FloatArray(samples) { h })

        /**
         * 시드로 언덕 지형 만들기 (같은 시드 → 같은 땅).
         * 기본 높이 + 사인 물결 몇 개 + 가운데 근처 큰 언덕 → 이웃 평균으로 다듬기 → 높이 범위로 자르기
         * → 대포 자리(cannonXs)를 평평하게 다지고 둘레를 원래 땅과 매끄럽게 이음.
         */
        fun generate(seed: Int, cannonXs: FloatArray, samples: Int = GameConfig.SAMPLES): Terrain {
            val rnd = Random(seed)
            val w = GameConfig.WORLD_W
            val h = FloatArray(samples)
            val dx = w / (samples - 1)
            // 물결 (진폭은 뒤로 갈수록 작게, 주기는 짧게)
            val amp = FloatArray(GameConfig.WAVES)
            val freq = FloatArray(GameConfig.WAVES)
            val phase = FloatArray(GameConfig.WAVES)
            for (k in 0 until GameConfig.WAVES) {
                amp[k] = GameConfig.WAVE_AMP * (0.55f + 0.45f * rnd.nextFloat()) / (1f + k * 0.8f)
                freq[k] = (k + 1) * (0.6f + 0.5f * rnd.nextFloat())
                phase[k] = rnd.nextFloat() * 2f * PI.toFloat()
            }
            // 가운데 언덕 (위치 35~65%, 높이 40~100%)
            val hillX = w * (0.35f + 0.3f * rnd.nextFloat())
            val hillH = GameConfig.HILL_MAX * (0.4f + 0.6f * rnd.nextFloat())
            for (i in 0 until samples) {
                val x = i * dx
                var y = GameConfig.BASE_H
                for (k in 0 until GameConfig.WAVES) y += amp[k] * sin(2f * PI.toFloat() * freq[k] * x / w + phase[k])
                val u = (x - hillX) / GameConfig.HILL_WIDTH
                y += hillH * exp(-u * u)
                h[i] = y
            }
            smooth(h, GameConfig.SMOOTH_PASSES)
            for (i in h.indices) h[i] = h[i].coerceIn(GameConfig.MIN_H, GameConfig.MAX_H)
            // 대포 자리 다지기
            for (cx in cannonXs) flatten(h, dx, cx)
            return Terrain(h, w)
        }

        /** 이웃 3칸 평균으로 passes 번 다듬기 */
        private fun smooth(h: FloatArray, passes: Int) {
            val tmp = FloatArray(h.size)
            repeat(passes) {
                for (i in h.indices) {
                    val a = h[(i - 1).coerceAtLeast(0)]
                    val b = h[(i + 1).coerceAtMost(h.size - 1)]
                    tmp[i] = (a + 2f * h[i] + b) / 4f
                }
                tmp.copyInto(h)
            }
        }

        /** cx 둘레 ±PAD_HALF 를 그 구간 평균 높이로 평평하게, 그 밖 PAD_BLEND 는 원래 땅과 직선으로 섞음 */
        private fun flatten(h: FloatArray, dx: Float, cx: Float) {
            var sum = 0f
            var n = 0
            for (i in h.indices) {
                if (kotlin.math.abs(i * dx - cx) <= GameConfig.PAD_HALF) {
                    sum += h[i]
                    n++
                }
            }
            if (n == 0) return
            val pad = sum / n
            for (i in h.indices) {
                val d = kotlin.math.abs(i * dx - cx)
                if (d <= GameConfig.PAD_HALF) {
                    h[i] = pad
                } else if (d < GameConfig.PAD_HALF + GameConfig.PAD_BLEND) {
                    // 부드럽게 (smoothstep)
                    val t = (d - GameConfig.PAD_HALF) / GameConfig.PAD_BLEND
                    val s = t * t * (3f - 2f * t)
                    h[i] = pad + (h[i] - pad) * s
                }
            }
        }
    }
}
