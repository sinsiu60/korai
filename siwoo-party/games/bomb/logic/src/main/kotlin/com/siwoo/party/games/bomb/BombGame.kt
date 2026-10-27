package com.siwoo.party.games.bomb

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 라운드 단계 */
enum class Phase {
    /** 시작 전 */
    IDLE,
    /** 주제 카드를 보는 중 (아직 불 안 붙음) */
    READY,
    /** 심지에 불이 붙어 넘기는 중 */
    LIVE,
    /** 펑! (다음 라운드 대기) */
    EXPLODED,
}

/** 이번 라운드 예정: 주제, 처음 드는 사람, 폭발까지 시간(화면에 표시 안 함) */
class RoundPlan(val topic: String, val firstHolder: Int, val fuseMs: Long)

/**
 * 폭탄 돌리기 규칙.
 * - 라운드마다 새 주제 (TopicDeck: 주제가 5개 이상이면 한 게임 안에서 겹치지 않음)
 * - 폭발 시간 20~60초 무작위 (규칙 시간, 비공개)
 * - 들고 있는 사람이 단어를 말하고 [넘기기] → 다음 순서(마지막 다음은 처음)로. 넘긴 뒤 0.8초는 다시 못 넘김
 * - 터질 때 들고 있던 사람 해골 +1, 5라운드 후 해골 적은 순 (같으면 공동 순위)
 * - 라운드 첫 사람: 1라운드는 무작위, 그다음은 직전 라운드에 터진 사람의 다음 사람
 * 시간은 화면이 넘겨준다(ms, 일시정지 시간을 뺀 게임 시간) → 테스트에서 그대로 재현 가능.
 */
class BombGame(val playerCount: Int, topics: List<String> = BombTopics.DEFAULT, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    private val deck = TopicDeck(topics, random)

    val skulls = IntArray(playerCount)
    /** 끝난 라운드 수 */
    var roundsDone = 0
        private set
    var phase = Phase.IDLE
        private set
    /** 지금 폭탄을 든 사람 */
    var holder = -1
        private set
    var topic = ""
        private set
    /** 이번 라운드 폭발까지 시간 (불붙은 순간부터) */
    var fuseMs = 0L
        private set
    /** 직전 라운드에 터진 사람 (-1 = 없음) */
    var lastVictim = -1
        private set
    /** 이번 라운드에 넘긴 횟수 */
    var passes = 0
        private set
    private var litAt = 0L
    private var lastPassAt = Long.MIN_VALUE / 2
    /** 이번 게임에 나온 주제 (순서대로) */
    val topicsUsed = ArrayList<String>()

    val gameOver: Boolean get() = roundsDone >= GameConfig.BOMB_ROUNDS
    /** 지금 하는(또는 방금 끝난) 라운드 번호 (1부터) */
    val roundNumber: Int
        get() = if (phase == Phase.EXPLODED) roundsDone else (roundsDone + 1).coerceAtMost(GameConfig.BOMB_ROUNDS)

    /** 새 라운드 준비: 주제·첫 사람·폭발 시간 정하기 (주제 카드 단계) */
    fun startRound(): RoundPlan {
        check(!gameOver) { "게임이 끝났습니다" }
        check(phase == Phase.IDLE || phase == Phase.EXPLODED) { "라운드 진행 중입니다" }
        topic = deck.next()
        topicsUsed.add(topic)
        holder = if (lastVictim < 0) random.nextInt(playerCount) else nextHolder(lastVictim, playerCount)
        fuseMs = randomFuseMs(random)
        passes = 0
        lastPassAt = Long.MIN_VALUE / 2
        phase = Phase.READY
        return RoundPlan(topic, holder, fuseMs)
    }

    /** 불 붙이기 (주제 카드를 닫고 카운트다운이 끝난 때) */
    fun light(nowMs: Long) {
        check(phase == Phase.READY) { "주제 카드 단계가 아닙니다" }
        litAt = nowMs
        phase = Phase.LIVE
    }

    /** 지금 넘길 수 있는지 (불붙은 상태 + 직전 넘기기 후 0.8초 지남) */
    fun canPass(nowMs: Long): Boolean =
        phase == Phase.LIVE && nowMs - lastPassAt >= GameConfig.BOMB_PASS_COOLDOWN_MS

    /** [넘기기]: 되면 다음 사람에게 넘기고 true, 대기 시간 중이거나 불이 안 붙었으면 false */
    fun pass(nowMs: Long): Boolean {
        if (!canPass(nowMs)) return false
        holder = nextHolder(holder, playerCount)
        lastPassAt = nowMs
        passes++
        return true
    }

    /** 불붙은 뒤 지난 시간 */
    fun elapsedMs(nowMs: Long): Long = if (phase == Phase.LIVE) (nowMs - litAt).coerceAtLeast(0) else 0L

    /** 폭발까지 남은 시간 (화면에는 표시하지 않고 째깍 속도·흔들림에만 쓴다) */
    fun remainingMs(nowMs: Long): Long = when (phase) {
        Phase.LIVE -> (fuseMs - elapsedMs(nowMs)).coerceAtLeast(0)
        Phase.READY -> fuseMs
        else -> 0L
    }

    /** 남은 비율 1(막 불붙음) → 0(터질 때) */
    fun remainingRatio(nowMs: Long): Double = if (fuseMs <= 0) 0.0 else remainingMs(nowMs).toDouble() / fuseMs

    /** 폭발 시간이 됐는지 */
    fun isDue(nowMs: Long): Boolean = phase == Phase.LIVE && elapsedMs(nowMs) >= fuseMs

    /** 펑! 들고 있던 사람에게 해골 +1, 그 사람 번호를 돌려준다 */
    fun explode(): Int {
        check(phase == Phase.LIVE) { "불이 붙지 않았습니다" }
        val victim = holder
        skulls[victim]++
        lastVictim = victim
        roundsDone++
        phase = Phase.EXPLODED
        return victim
    }

    /** 최종 순위 (1부터, 동점 허용): 해골 적은 순 */
    fun finalRanks(): IntArray = Ranking.competition(skulls.map { it.toLong() }, higherIsBetter = false)

    companion object {
        /** 다음 순서 (마지막 다음은 처음) */
        fun nextHolder(current: Int, playerCount: Int): Int = (current + 1) % playerCount

        /** 폭발 시간: FUSE_MIN_MS~FUSE_MAX_MS (양 끝 포함) */
        fun randomFuseMs(random: Random): Long =
            GameConfig.FUSE_MIN_MS + random.nextLong(GameConfig.FUSE_MAX_MS - GameConfig.FUSE_MIN_MS + 1)

        /** 째깍 간격(ms): 남은 비율이 줄수록 짧아진다 (1 → TICK_SLOW_MS, 0 → TICK_FAST_MS, 끝으로 갈수록 급하게) */
        fun tickIntervalMs(remainingRatio: Double): Long {
            val r = remainingRatio.coerceIn(0.0, 1.0)
            val k = Math.pow(r, 1.4)
            return GameConfig.TICK_FAST_MS + ((GameConfig.TICK_SLOW_MS - GameConfig.TICK_FAST_MS) * k).toLong()
        }

        /** 폭탄 흔들림 세기 0~1: 남은 비율이 줄수록 커진다 (1 → SHAKE_MIN, 0 → 1) */
        fun shakeLevel(remainingRatio: Double): Float {
            val left = 1.0 - remainingRatio.coerceIn(0.0, 1.0)
            return (GameConfig.SHAKE_MIN + (1.0 - GameConfig.SHAKE_MIN) * left * left).toFloat()
        }
    }
}
