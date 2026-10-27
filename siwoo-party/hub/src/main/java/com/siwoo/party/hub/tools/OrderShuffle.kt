package com.siwoo.party.hub.tools

import kotlin.random.Random

/**
 * 순서 정하기 (순수 Kotlin).
 * 결과 순서는 Fisher–Yates 로 고르게 뽑고, 화면 연출용 "자리 바꾸기" 목록을 만든다:
 * 먼저 눈속임 바꾸기 몇 번 → 그다음 그 배치에서 결과 순서가 되도록 바꾸기.
 * 바꾸기를 처음 배치(0,1,2,..)에 차례로 적용하면 정확히 [order] 가 된다.
 */
class OrderShuffle(val order: IntArray, val swaps: List<IntArray>) {

    /** 바꾸기를 모두 적용한 배치 (= order) */
    fun replay(): IntArray {
        val a = IntArray(order.size) { it }
        for (s in swaps) { val t = a[s[0]]; a[s[0]] = a[s[1]]; a[s[1]] = t }
        return a
    }

    companion object {
        /** 눈속임 바꾸기 수 = 기본 + 인원 */
        const val DECOY_BASE = 4

        /** n 명의 순서 (order[k] = k번째 자리(1번부터)에 올 사람 번호) */
        fun permutation(n: Int, random: Random): IntArray {
            val a = IntArray(n) { it }
            for (i in n - 1 downTo 1) {
                val j = random.nextInt(i + 1)
                val t = a[i]; a[i] = a[j]; a[j] = t
            }
            return a
        }

        fun plan(n: Int, random: Random, decoys: Int = DECOY_BASE + n): OrderShuffle {
            require(n >= 1)
            val target = permutation(n, random)
            val swaps = ArrayList<IntArray>()
            val cur = IntArray(n) { it }
            fun swap(i: Int, j: Int) {
                swaps.add(intArrayOf(i, j))
                val t = cur[i]; cur[i] = cur[j]; cur[j] = t
            }
            if (n >= 2) {
                repeat(decoys) {
                    val i = random.nextInt(n)
                    var j = random.nextInt(n - 1)
                    if (j >= i) j++
                    swap(i, j)
                }
                // 지금 배치 → 목표 배치
                for (k in 0 until n) {
                    if (cur[k] != target[k]) swap(k, cur.indexOf(target[k]))
                }
            }
            return OrderShuffle(target, swaps)
        }
    }
}
