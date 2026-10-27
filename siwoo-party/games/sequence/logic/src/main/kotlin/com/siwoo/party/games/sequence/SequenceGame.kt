package com.siwoo.party.games.sequence

import com.siwoo.party.common.Ranking
import kotlin.random.Random

enum class Phase { IDLE, PLAYING, ROUND_OVER }

/** 칸을 누른 결과 */
sealed class Tap {
    /** 무시 (라운드 밖·이미 끝냄·이미 누른 숫자·범위 밖) */
    object Ignored : Tap()
    /** 정지 중이라 무시 */
    class Frozen(val player: Int, val untilMs: Long) : Tap()
    /** 다음 숫자를 맞게 누름 */
    class Correct(val player: Int, val cell: Int, val number: Int) : Tap()
    /** 마지막 숫자까지 끝냄 (place = 완료 순서, 1부터) */
    class Finished(val player: Int, val cell: Int, val place: Int) : Tap()
    /** 틀린 숫자 → untilMs 까지 정지 */
    class Wrong(val player: Int, val cell: Int, val number: Int, val expected: Int, val untilMs: Long) : Tap()
}

/**
 * 숫자 순서 터치 규칙.
 * - 라운드마다 1~16 을 섞은 4×4 배치 하나를 모든 구역이 같이 쓴다 (공정).
 * - 1부터 순서대로 누른다. 이미 누른 숫자는 무시, 다른 숫자를 누르면 0.5초 정지.
 * - 먼저 끝낸 순서가 라운드 등수. (인원-1)명이 끝나면 라운드 종료 → 남은 사람은 꼴찌.
 *   제한 시간이 지나면 남은 사람들은 진행(누른 개수) 순 (같으면 같은 등수).
 * - 라운드 점수 = 인원 + 1 - 등수 (4인: 4·3·2·1점). 3라운드 합계로 최종 순위 (동점 같은 순위).
 * 시간은 화면이 넘겨준다(ms, 게임 시간).
 */
class SequenceGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** layout[cell] = 그 칸의 숫자 (칸 번호 = 줄*4 + 칸, 플레이어 기준) — 모든 구역 같음 */
    val layout = IntArray(GameConfig.CELLS) { it + 1 }
    /** 플레이어별 다음에 누를 숫자 (17 = 끝냄) */
    val next = IntArray(playerCount) { 1 }
    val frozenUntil = LongArray(playerCount) { Long.MIN_VALUE }
    /** 이번 라운드 끝낸 순서 */
    val finishOrder = ArrayList<Int>()
    /** 이번 라운드 등수 (라운드가 끝나야 채워짐, 0 = 아직) */
    val roundPlaces = IntArray(playerCount)
    /** 이번 라운드 받은 점수 */
    val roundPoints = IntArray(playerCount)
    val totalPoints = IntArray(playerCount)
    var roundsDone = 0
        private set
    var phase = Phase.IDLE
        private set

    val gameOver: Boolean get() = roundsDone >= GameConfig.SEQUENCE_ROUNDS
    /** 지금(또는 다음) 라운드 번호 (1부터) */
    val roundNumber: Int get() = (roundsDone + (if (phase == Phase.ROUND_OVER) 0 else 1)).coerceIn(1, GameConfig.SEQUENCE_ROUNDS)

    /** 플레이어 player 구역의 cell 칸 숫자 (모든 구역 같은 배치) */
    fun numberAt(player: Int, cell: Int): Int {
        require(player in 0 until playerCount)
        return layout[cell]
    }

    fun isFinished(player: Int): Boolean = next[player] > GameConfig.CELLS

    fun isFrozen(player: Int, nowMs: Long): Boolean = nowMs < frozenUntil[player]

    /** 누른 개수 */
    fun progress(player: Int): Int = next[player] - 1

    /** 새 라운드: 배치를 섞고 모두 1부터 */
    fun startRound() {
        check(!gameOver) { "게임이 끝났습니다" }
        for (i in layout.indices) layout[i] = i + 1
        for (i in layout.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = layout[i]; layout[i] = layout[j]; layout[j] = t
        }
        next.fill(1)
        frozenUntil.fill(Long.MIN_VALUE)
        finishOrder.clear()
        roundPlaces.fill(0)
        roundPoints.fill(0)
        phase = Phase.PLAYING
    }

    fun tap(player: Int, cell: Int, nowMs: Long): Tap {
        if (phase != Phase.PLAYING || player !in 0 until playerCount || cell !in 0 until GameConfig.CELLS) return Tap.Ignored
        if (isFinished(player)) return Tap.Ignored
        val number = layout[cell]
        if (number < next[player]) return Tap.Ignored // 이미 누른 숫자
        if (isFrozen(player, nowMs)) return Tap.Frozen(player, frozenUntil[player])
        if (number != next[player]) {
            frozenUntil[player] = nowMs + GameConfig.WRONG_FREEZE_MS
            return Tap.Wrong(player, cell, number, next[player], frozenUntil[player])
        }
        next[player]++
        if (isFinished(player)) {
            finishOrder.add(player)
            return Tap.Finished(player, cell, finishOrder.size)
        }
        return Tap.Correct(player, cell, number)
    }

    /** (인원-1)명 이상 끝냈으면 라운드를 끝낼 때 */
    val roundShouldEnd: Boolean get() = phase == Phase.PLAYING && finishOrder.size >= playerCount - 1

    /**
     * 라운드 끝: 등수·점수 계산 (끝낸 순서 → 나머지는 누른 개수 순, 같으면 같은 등수).
     * 결과는 roundPlaces / roundPoints, 합계는 totalPoints.
     */
    fun endRound() {
        check(phase == Phase.PLAYING) { "진행 중인 라운드가 없습니다" }
        for ((k, p) in finishOrder.withIndex()) roundPlaces[p] = k + 1
        val rest = (0 until playerCount).filter { !isFinished(it) }
        val restRanks = Ranking.competition(rest.map { progress(it).toLong() })
        for ((k, p) in rest.withIndex()) roundPlaces[p] = finishOrder.size + restRanks[k]
        for (p in 0 until playerCount) {
            roundPoints[p] = playerCount + 1 - roundPlaces[p]
            totalPoints[p] += roundPoints[p]
        }
        roundsDone++
        phase = Phase.ROUND_OVER
    }

    /** 최종 순위 (1부터): 합계 점수 높은 순, 같으면 같은 순위 */
    fun finalRanks(): IntArray = Ranking.competition(totalPoints.map { it.toLong() })
}
