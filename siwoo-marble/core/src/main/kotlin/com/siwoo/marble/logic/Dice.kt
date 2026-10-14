package com.siwoo.marble.logic

import kotlin.random.Random

/** 주사위 1개. 테스트에서는 상속해서 원하는 눈을 내도록 바꿀 수 있다. */
open class Dice(private val random: Random = Random.Default) {
    open fun rollOne(): Int = random.nextInt(1, 7)
}
