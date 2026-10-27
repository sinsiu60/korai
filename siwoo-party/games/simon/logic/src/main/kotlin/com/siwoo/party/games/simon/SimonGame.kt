package com.siwoo.party.games.simon

import kotlin.random.Random

/** 진행 단계 */
enum class Phase {
    /** 시작 전 */
    READY,
    /** 차례가 넘어가는 중 (다음 사람 beginTurn 대기) */
    BETWEEN,
    /** 차례인 사람이 지금까지의 순서를 그대로 누르는 중 */
    REPEAT,
    /** 순서를 다 맞혔다 → 새 색 하나를 더할 차례 */
    ADD,
    /** 한 명만 남음 → 끝 */
    GAME_OVER,
}

/** 탈락 이유 */
enum class OutReason { WRONG, TIMEOUT }

/** 버튼을 누른(또는 시간을 확인한) 결과 */
sealed class Press {
    /** 무시 (누르는 단계가 아님·없는 색·아직 시간 남음) */
    object Ignored : Press()

    /** 순서대로 맞게 누름: progress 개째까지 맞힘. needAdd 면 이제 새 색을 더할 차례 */
    class Correct(val player: Int, val color: Int, val progress: Int, val length: Int, val needAdd: Boolean) : Press()

    /** 새 색 추가 → 순서 길이 length, next 가 다음 차례 */
    class Added(val player: Int, val color: Int, val length: Int, val next: Int) : Press()

    /**
     * 탈락. pressed = 잘못 누른 색 (시간 초과면 -1), expected = 눌렀어야 할 색 (새 색을 더할 차례였으면 -1).
     * gameOver 면 남은 한 명이 1위, 아니면 next 가 다음 차례 (순서는 그대로 이어감).
     */
    class Out(
        val player: Int,
        val reason: OutReason,
        val pressed: Int,
        val expected: Int,
        val gameOver: Boolean,
        val next: Int,
    ) : Press()
}

/**
 * 패턴 기억 릴레이 규칙.
 * - 색 4개 (GameConfig.COLORS). 순서(sequence)는 모두가 함께 쓰는 하나 (릴레이).
 * - 차례인 사람은 지금까지의 순서를 처음부터 그대로 누른 뒤 새 색 하나를 더한다 → 다음 사람 차례.
 *   첫 사람은 순서가 비어 있으니 색 하나만 눌러 시작한다 (길이 1). 순서를 다시 보여 주지 않는다 (기억력 게임).
 * - 틀린 색을 누르거나, 마지막으로 누른 뒤(또는 차례 시작 뒤) PRESS_LIMIT_MS(5초) 동안 누르지 않으면 탈락.
 * - 탈락해도 순서는 지우지 않는다: 다음(살아 있는) 사람이 같은 순서를 처음부터 누르고 하나를 더한다.
 * - 최후의 1인이 1위, 나머지는 늦게 탈락한 순 (동점 없음).
 * 시간(now)과 난수(첫 차례)는 밖에서 주입 → 테스트에서 그대로 재현.
 */
class SimonGame(
    val playerCount: Int,
    private val random: Random = Random.Default,
    /** 버튼 사이 제한 시간 (ms) */
    val limitMs: Long = GameConfig.PRESS_LIMIT_MS,
) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 함께 쓰는 순서 (색 번호) */
    val sequence = ArrayList<Int>()
    val alive = BooleanArray(playerCount) { true }
    /** 탈락한 순서 (먼저 탈락한 사람이 앞) */
    val eliminated = ArrayList<Int>()
    /** 사람마다 자기 차례에 완성한 가장 긴 순서 길이 */
    val best = IntArray(playerCount)

    var phase = Phase.READY
        private set
    /** 지금 차례 (players 순서 번호, 시작 전 -1) */
    var current = -1
        private set
    /** 이번 차례에 순서를 몇 개째까지 맞혔는지 */
    var progress = 0
        private set
    /** 이 시각(게임 시간)까지 다음 버튼을 눌러야 한다 */
    var deadline = 0L
        private set
    /** 지금까지 차례가 시작된 횟수 */
    var turns = 0
        private set

    val length: Int get() = sequence.size
    val aliveCount: Int get() = alive.count { it }
    val gameOver: Boolean get() = phase == Phase.GAME_OVER
    /** 최후의 1인 (끝나기 전엔 -1) */
    val winner: Int get() = if (gameOver) alive.indexOfFirst { it } else -1
    /** 지금 버튼을 누를 수 있는 단계인지 */
    val accepting: Boolean get() = phase == Phase.REPEAT || phase == Phase.ADD
    /** 지금 눌러야 할 색 (새 색을 더할 차례·누르는 단계가 아니면 -1) */
    val expected: Int get() = if (phase == Phase.REPEAT) sequence[progress] else -1

    /** 남은 시간 (누르는 단계가 아니면 0) */
    fun remainingMs(now: Long): Long = if (accepting) (deadline - now).coerceAtLeast(0) else 0

    /** from 다음의 살아 있는 사람 (from 자신이 탈락했어도 동작) */
    fun nextAlive(from: Int): Int {
        for (k in 1..playerCount) {
            val i = (from + k) % playerCount
            if (alive[i]) return i
        }
        return -1
    }

    /** 게임 시작: 첫 차례를 정한다 (first 를 주지 않으면 무작위). beginTurn 으로 차례를 연다 */
    fun start(first: Int = -1): Int {
        check(phase == Phase.READY) { "이미 시작함" }
        current = if (first in 0 until playerCount) first else random.nextInt(playerCount)
        phase = Phase.BETWEEN
        return current
    }

    /** 지금 차례인 사람의 차례를 연다 (now = 게임 시간). 순서가 비어 있으면 바로 새 색 추가 단계 */
    fun beginTurn(now: Long) {
        check(phase == Phase.BETWEEN) { "차례를 열 수 없는 단계: $phase" }
        progress = 0
        phase = if (sequence.isEmpty()) Phase.ADD else Phase.REPEAT
        deadline = now + limitMs
        turns++
    }

    /** 지금 차례인 사람이 color 버튼을 누름 (now = 게임 시간) */
    fun press(color: Int, now: Long): Press {
        if (!accepting || color !in 0 until GameConfig.COLORS) return Press.Ignored
        // 시간이 이미 지났으면 누른 것과 상관없이 시간 초과
        if (now >= deadline) return out(OutReason.TIMEOUT, -1)
        val p = current
        if (phase == Phase.ADD) {
            sequence.add(color)
            best[p] = maxOf(best[p], sequence.size)
            current = nextAlive(p)
            phase = Phase.BETWEEN
            return Press.Added(p, color, sequence.size, current)
        }
        if (color != sequence[progress]) return out(OutReason.WRONG, color)
        progress++
        deadline = now + limitMs
        val done = progress == sequence.size
        if (done) phase = Phase.ADD
        return Press.Correct(p, color, progress, sequence.size, done)
    }

    /** 시간 확인: 누르는 단계에서 now 가 마감을 지났으면 시간 초과 탈락, 아니면 Ignored */
    fun checkTimeout(now: Long): Press {
        if (!accepting || now < deadline) return Press.Ignored
        return out(OutReason.TIMEOUT, -1)
    }

    private fun out(reason: OutReason, pressed: Int): Press.Out {
        val p = current
        val exp = expected
        alive[p] = false
        eliminated.add(p)
        progress = 0
        val over = aliveCount <= 1
        current = if (over) alive.indexOfFirst { it } else nextAlive(p)
        phase = if (over) Phase.GAME_OVER else Phase.BETWEEN
        return Press.Out(p, reason, pressed, exp, over, current)
    }

    /**
     * 순위 (players 순서, 1부터): 살아 남은 사람 1위, 나머지는 늦게 탈락할수록 높은 순위.
     * 예) 4명 중 탈락 순서 2 → 0 → 3 이면 1번이 1위, 3번 2위, 0번 3위, 2번 4위
     */
    fun finalRanks(): IntArray = IntArray(playerCount) { p ->
        val k = eliminated.indexOf(p)
        if (k < 0) 1 else playerCount - k
    }
}
