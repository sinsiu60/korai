package com.siwoo.party.games.rps

/** 손 모양 */
enum class Hand(val label: String) {
    SCISSORS("가위"), ROCK("바위"), PAPER("보");

    /** 이 손이 o 를 이기는지 */
    fun beats(o: Hand): Boolean =
        (this == ROCK && o == SCISSORS) || (this == SCISSORS && o == PAPER) || (this == PAPER && o == ROCK)
}

/** 반칙: 없음 / "보!" 전에 누름 / 0.6초 안에 안 누름 */
enum class Foul { NONE, EARLY, LATE }

/** 판 단계: IDLE(판 사이) → CHANT("가위… 바위…") → OPEN("보!" 후 0.6초) → (판정) → IDLE / OVER */
enum class Phase { IDLE, CHANT, OPEN, OVER }

/** 버튼 누름 결과 */
enum class Press { ACCEPTED, FOUL_EARLY, IGNORED }

/** 다시 하는 이유 */
enum class Replay { ALL_SAME, ALL_THREE, ALL_FOUL }

/** 한 판 결과: 탈락한 사람들(같은 순위 rank), 또는 다시(replay) */
class ThrowResult(val eliminated: List<Int>, val rank: Int, val replay: Replay?) {
    val isReplay: Boolean get() = replay != null
}

/**
 * 가위바위보 토너먼트 규칙 (살아 있는 사람 모두 동시에 한 판씩).
 * - "가위… 바위…" 동안(CHANT) 버튼을 누르면 이른 반칙(EARLY). "보!" 부터 0.6초(OPEN) 안에 처음 누른 버튼이 그 사람 손.
 *   0.6초 안에 안 누르면 늦은 반칙(LATE). 반칙은 그 판 "패배 처리" = 어떤 손에도 진다.
 * - 판정: 반칙한 사람은 탈락. 제대로 낸 사람들끼리는 보통 가위바위보 —
 *   손이 두 가지만 나왔으면 진 손을 낸 사람 탈락, 모두 같거나 세 가지가 다 나왔으면 그 사람들끼리는 무승부.
 *   그래서 아무도 탈락하지 않으면(모두 같은 손 / 세 가지 다 / 모두 반칙) 그 판은 다시.
 *   모두 반칙이면 전원 탈락이 되므로 다시 한다 — 한 판에 모두가 탈락하는 일은 없다.
 * - 순위: 탈락 순서. 같은 판에 함께 탈락하면 같은 순위(= 그 판 뒤 남은 사람 수 + 1). 마지막 1명이 1위.
 * 시간(ms)은 화면이 넘겨준다(일시정지 시간을 뺀 게임 시계).
 */
class RpsGame(val playerCount: Int) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val alive = BooleanArray(playerCount) { true }
    /** 확정 순위 (0 = 아직 살아 있음) */
    val rank = IntArray(playerCount)
    /** 이번 판에 낸 손 (null = 안 냄) */
    val hand = arrayOfNulls<Hand>(playerCount)
    /** 이번 판 반칙 */
    val foul = Array(playerCount) { Foul.NONE }
    var phase = Phase.IDLE
        private set
    /** 시작한 판 수 (다시 한 판 포함) */
    var throwNumber = 0
        private set
    private var openAt = 0L

    val aliveCount: Int get() = alive.count { it }
    val isOver: Boolean get() = phase == Phase.OVER
    /** 우승자 (끝나기 전 -1) */
    val winner: Int get() = if (isOver) alive.indexOfFirst { it } else -1

    /** 새 판: "가위…" 부터 (이때부터 누르면 이른 반칙) */
    fun startThrow() {
        check(phase == Phase.IDLE) { "판 진행 중이거나 끝남 ($phase)" }
        hand.fill(null)
        foul.fill(Foul.NONE)
        phase = Phase.CHANT
        throwNumber++
    }

    /** "보!" (nowMs = 게임 시계) */
    fun open(nowMs: Long) {
        check(phase == Phase.CHANT)
        openAt = nowMs
        phase = Phase.OPEN
    }

    /** 플레이어 p 가 손 h 버튼을 누름 */
    fun press(p: Int, h: Hand, nowMs: Long): Press {
        if (p !in 0 until playerCount || !alive[p] || hand[p] != null || foul[p] != Foul.NONE) return Press.IGNORED
        return when (phase) {
            Phase.CHANT -> {
                foul[p] = Foul.EARLY
                Press.FOUL_EARLY
            }
            Phase.OPEN -> if (nowMs - openAt in 0..GameConfig.WINDOW_MS) {
                hand[p] = h
                Press.ACCEPTED
            } else {
                Press.IGNORED // 늦음 → 판정 때 LATE
            }
            else -> Press.IGNORED
        }
    }

    /** 살아 있는 사람이 모두 손을 냈거나 반칙했는지 (그러면 창이 닫히기 전에 판정해도 된다) */
    val allDecided: Boolean
        get() = (0 until playerCount).all { !alive[it] || hand[it] != null || foul[it] != Foul.NONE }

    /** 판정 (창이 닫힘 / 모두 결정). 안 낸 사람은 늦은 반칙 */
    fun resolve(): ThrowResult {
        check(phase == Phase.CHANT || phase == Phase.OPEN) { "판정할 판이 없음" }
        for (p in 0 until playerCount) if (alive[p] && hand[p] == null && foul[p] == Foul.NONE) foul[p] = Foul.LATE
        val valid = (0 until playerCount).filter { alive[it] && hand[it] != null }
        val losers = ArrayList<Int>()
        var replay: Replay? = null
        if (valid.isEmpty()) {
            replay = Replay.ALL_FOUL
        } else {
            for (p in 0 until playerCount) if (alive[p] && foul[p] != Foul.NONE) losers.add(p)
            val kinds = valid.map { hand[it]!! }.distinct()
            if (kinds.size == 2) {
                val lose = if (kinds[0].beats(kinds[1])) kinds[1] else kinds[0]
                for (p in valid) if (hand[p] == lose) losers.add(p)
            }
            if (losers.isEmpty()) replay = if (kinds.size == 1) Replay.ALL_SAME else Replay.ALL_THREE
        }
        losers.sort()
        val left = aliveCount - losers.size
        val r = if (losers.isEmpty()) 0 else left + 1
        for (p in losers) {
            alive[p] = false
            rank[p] = r
        }
        if (left == 1) {
            rank[alive.indexOfFirst { it }] = 1
            phase = Phase.OVER
        } else {
            phase = Phase.IDLE
        }
        return ThrowResult(losers, r, replay)
    }

    /** 판 도중 일시정지 등으로 그 판을 없던 일로 (다시 startThrow) */
    fun abortThrow() {
        if (phase != Phase.CHANT && phase != Phase.OPEN) return
        hand.fill(null)
        foul.fill(Foul.NONE)
        phase = Phase.IDLE
    }

    /** 최종 순위 (1부터, 같은 판 탈락은 같은 순위) */
    fun ranks(): IntArray {
        check(isOver) { "아직 안 끝남" }
        return rank.copyOf()
    }
}
