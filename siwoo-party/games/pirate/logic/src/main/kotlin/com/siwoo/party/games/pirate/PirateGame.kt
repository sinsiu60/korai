package com.siwoo.party.games.pirate

import kotlin.random.Random

/** 진행 단계 */
enum class Phase {
    /** 첫 판 시작 전 */
    READY,
    /** 차례인 사람이 구멍을 고르는 중 */
    PICKING,
    /** 누가 함정을 찔러 탈락 → 다음 판(startRound) 대기 */
    ROUND_OVER,
    /** 한 명만 남음 → 끝 */
    GAME_OVER,
}

/** 구멍을 고른 결과 */
sealed class Pick {
    /** 무시 (이미 칼이 꽂힌 구멍·없는 구멍·고르는 단계가 아님) */
    object Ignored : Pick()
    /** 안전: 칼은 꽂힌 채로 남고 next 가 다음 차례 */
    class Safe(val player: Int, val hole: Int, val next: Int) : Pick()
    /** 함정: player 탈락. gameOver 면 남은 한 명이 1위 */
    class Trap(val player: Int, val hole: Int, val gameOver: Boolean) : Pick()
}

/**
 * 해적 룰렛 규칙.
 * - 나무통 구멍 HOLES(24)개 중 함정이 매 판 무작위로 숨겨진다 (서로 다른 구멍).
 *   함정 수: 그 판에 남은 인원이 2명이면 1개 고정, 3~4명이면 설정값(1 또는 2).
 * - 차례대로 빈 구멍 하나에 칼을 꽂는다. 안전하면 칼은 그대로 두고 다음(살아 있는) 사람 차례.
 * - 함정을 찌르면 그 사람 탈락 → 통을 비우고 남은 사람끼리 다음 판.
 *   다음 판은 **탈락한 사람의 다음 사람**부터 시작한다 (첫 판은 무작위).
 * - 최후의 1인이 1위, 나머지는 늦게 탈락한 순 (동점 없음).
 * 난수는 밖에서 주입 → 테스트에서 함정 위치를 그대로 재현할 수 있다.
 */
class PirateGame(
    val playerCount: Int,
    trapSetting: Int = GameConfig.PIRATE_TRAPS,
    private val random: Random = Random.Default,
) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 3~4인 판의 함정 수 설정 (1~2로 맞춤) */
    val trapSetting: Int = trapSetting.coerceIn(GameConfig.TRAPS_MIN, GameConfig.TRAPS_MAX)

    val alive = BooleanArray(playerCount) { true }
    /** 탈락한 순서 (먼저 탈락한 사람이 앞) */
    val eliminated = ArrayList<Int>()
    /** 구멍마다 칼을 꽂은 사람 (-1 = 빈 구멍) */
    val holeOwner = IntArray(GameConfig.HOLES) { -1 }
    private val trap = BooleanArray(GameConfig.HOLES)
    private val order = IntArray(GameConfig.HOLES) { it }

    var phase = Phase.READY
        private set
    /** 지금 판 번호 (1부터, 시작 전 0) */
    var round = 0
        private set
    /** 지금 차례 (players 순서 번호) */
    var current = -1
        private set
    /** 이번 판 함정 수 */
    var roundTraps = 0
        private set
    /** 다음 판을 시작할 사람 (-1 = 무작위: 첫 판) */
    var nextStarter = -1
        private set

    val aliveCount: Int get() = alive.count { it }
    val gameOver: Boolean get() = phase == Phase.GAME_OVER
    /** 최후의 1인 (끝나기 전엔 -1) */
    val winner: Int get() = if (gameOver) alive.indexOfFirst { it } else -1
    /** 이번 판에 남은 빈 구멍 수 */
    val emptyHoles: Int get() = holeOwner.count { it < 0 }

    fun isTrap(hole: Int): Boolean = hole in 0 until GameConfig.HOLES && trap[hole]
    fun isUsed(hole: Int): Boolean = hole in 0 until GameConfig.HOLES && holeOwner[hole] >= 0
    /** 지금 이 구멍을 고를 수 있는지 (고르는 단계 + 빈 구멍) */
    fun canPick(hole: Int): Boolean = phase == Phase.PICKING && hole in 0 until GameConfig.HOLES && holeOwner[hole] < 0

    /** from 다음의 살아 있는 사람 (from 자신이 탈락했어도 동작) */
    fun nextAlive(from: Int): Int {
        for (k in 1..playerCount) {
            val i = (from + k) % playerCount
            if (alive[i]) return i
        }
        return -1
    }

    /**
     * 새 판: 통을 비우고 함정을 다시 숨긴다. 시작하는 사람을 돌려준다.
     * starter 를 주지 않으면 nextStarter(첫 판은 무작위).
     */
    fun startRound(starter: Int = nextStarter): Int {
        check(phase == Phase.READY || phase == Phase.ROUND_OVER) { "판을 시작할 수 없는 단계: $phase" }
        holeOwner.fill(-1)
        trap.fill(false)
        roundTraps = trapsFor(aliveCount, trapSetting)
        // 서로 다른 구멍 roundTraps 개 (부분 피셔-예이츠 섞기)
        for (k in 0 until GameConfig.HOLES) order[k] = k
        for (k in 0 until roundTraps) {
            val j = k + random.nextInt(GameConfig.HOLES - k)
            val tmp = order[k]; order[k] = order[j]; order[j] = tmp
            trap[order[k]] = true
        }
        current = if (starter in 0 until playerCount && alive[starter]) starter else randomAlive()
        round++
        phase = Phase.PICKING
        return current
    }

    private fun randomAlive(): Int {
        var n = random.nextInt(aliveCount)
        for (i in 0 until playerCount) {
            if (!alive[i]) continue
            if (n == 0) return i
            n--
        }
        return nextAlive(0)
    }

    /** 지금 차례인 사람이 hole 에 칼을 꽂는다 */
    fun pick(hole: Int): Pick {
        if (!canPick(hole)) return Pick.Ignored
        val p = current
        holeOwner[hole] = p
        if (!trap[hole]) {
            current = nextAlive(p)
            return Pick.Safe(p, hole, current)
        }
        alive[p] = false
        eliminated.add(p)
        nextStarter = nextAlive(p)
        val over = aliveCount <= 1
        phase = if (over) Phase.GAME_OVER else Phase.ROUND_OVER
        return Pick.Trap(p, hole, over)
    }

    /**
     * 순위 (players 순서, 1부터): 살아 남은 사람 1위, 나머지는 늦게 탈락할수록 높은 순위.
     * 예) 4명 중 탈락 순서 2 → 0 → 3 이면 1번이 1위, 3번 2위, 0번 3위, 2번 4위
     */
    fun finalRanks(): IntArray = IntArray(playerCount) { p ->
        val k = eliminated.indexOf(p)
        if (k < 0) 1 else playerCount - k
    }

    companion object {
        /** 남은 인원 players 인 판의 함정 수: 2명이면 1개 고정, 3~4명이면 설정값(1~2) */
        fun trapsFor(players: Int, setting: Int): Int =
            if (players <= 2) GameConfig.TRAPS_TWO_PLAYERS
            else setting.coerceIn(GameConfig.TRAPS_MIN, GameConfig.TRAPS_MAX)
    }
}
