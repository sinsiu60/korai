package com.siwoo.party.games.oddone

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/**
 * 격자에 쓰는 모양. symmetry = 회전 대칭 차수 (0 = 원, 어떻게 돌려도 같음).
 * 회전 문제에는 돌린 티가 확실히 나는 모양만 쓴다 (대칭 주기 90° 이상 → 차수 1~4).
 */
enum class Shape(val label: String, val symmetry: Int) {
    CIRCLE("동그라미", 0),
    STAR("별", 5),
    HEART("하트", 1),
    ARROW("화살표", 1),
    TRIANGLE("세모", 3),
    LEAF("나뭇잎", 1),
    MOON("초승달", 1),
    DIAMOND("마름모", 2),
    ;

    val rotatable: Boolean get() = symmetry in 1..4
}

/** 다른 하나가 무엇이 다른가 */
enum class Diff(val label: String) {
    /** 색 (밝기) */
    COLOR("색"),
    /** 기울기 (회전 각도) */
    ROTATION("기울기"),
    /** 크기 */
    SIZE("크기"),
}

/** 칸 하나의 모습. hue 도(0..360), sat·light 0..1 (HSL), rotation 도, scale 1 = 기본 크기 */
data class CellLook(val hue: Float, val sat: Float, val light: Float, val rotation: Float, val scale: Float) {
    /** 화면 색 (불투명 ARGB) */
    val argb: Int get() = Hsl.toArgb(hue, sat, light)
}

/** 한 라운드 문제: 16칸이 모두 base 이고 oddIndex 칸만 odd (kind 속성 하나만 delta 만큼 다름) */
class Puzzle(
    val number: Int,
    val shape: Shape,
    val kind: Diff,
    val delta: Float,
    val oddIndex: Int,
    val base: CellLook,
    val odd: CellLook,
) {
    val baseArgb: Int = base.argb
    val oddArgb: Int = odd.argb

    fun isOdd(cell: Int): Boolean = cell == oddIndex
    fun look(cell: Int): CellLook = if (cell == oddIndex) odd else base
    fun argb(cell: Int): Int = if (cell == oddIndex) oddArgb else baseArgb
}

/** 문제 만들기 (순수 함수, 난수 주입) */
object PuzzleMaker {

    /** 라운드별 차이 종류: 3라운드마다 색·기울기·크기가 한 번씩 (순서는 무작위) */
    fun schedule(random: Random, rounds: Int = GameConfig.ROUNDS): List<Diff> {
        val out = ArrayList<Diff>(rounds)
        val kinds = Diff.values().toMutableList()
        while (out.size < rounds) {
            kinds.shuffle(random)
            for (k in kinds) if (out.size < rounds) out.add(k)
        }
        return out
    }

    /** number(1부터) 번째 라운드, kind 차이로 문제 하나 (avoid 모양은 피함 — 앞 라운드와 다른 모양) */
    fun make(number: Int, kind: Diff, random: Random, avoid: Shape? = null): Puzzle {
        val shapes = Shape.values().filter { (kind != Diff.ROTATION || it.rotatable) && it != avoid }
        val shape = shapes[random.nextInt(shapes.size)]
        val hue = GameConfig.BASE_HUES[random.nextInt(GameConfig.BASE_HUES.size)]
        val rot = if (shape.symmetry == 0) 0f else random.nextInt(360).toFloat()
        val base = CellLook(hue, GameConfig.BASE_SAT, GameConfig.BASE_LIGHT, rot, 1f)
        val sign = if (random.nextBoolean()) 1f else -1f
        val delta = GameConfig.delta(kind, number)
        val odd = when (kind) {
            Diff.COLOR -> base.copy(light = base.light + sign * delta)
            Diff.ROTATION -> base.copy(rotation = base.rotation + sign * delta)
            Diff.SIZE -> base.copy(scale = base.scale + sign * delta)
        }
        return Puzzle(number, shape, kind, delta, random.nextInt(GameConfig.CELLS), base, odd)
    }
}

enum class Phase { IDLE, ASKING }

/** 칸을 누른 결과 */
sealed class Tap {
    /** 무시 (문제 밖·범위 밖·이미 끝남) */
    object Ignored : Tap()
    /** 잠금 중이라 무시 */
    class Locked(val player: Int, val untilMs: Long) : Tap()
    /** 정답 (먼저 찾음 → 라운드 끝) */
    class Correct(val player: Int, val cell: Int) : Tap()
    /** 오답 → untilMs 까지 잠금 (점수는 그대로) */
    class Wrong(val player: Int, val cell: Int, val untilMs: Long) : Tap()
}

/**
 * 하나만 다른 모양 규칙.
 * - 라운드마다 4×4 같은 모양 격자, 한 칸만 색·기울기·크기 중 하나가 살짝 다르다 (라운드가 갈수록 차이 감소).
 * - 모든 구역에 같은 격자·같은 위치 → 그 칸을 가장 먼저 누른 사람 +1, 라운드 끝.
 * - 틀린 칸을 누르면 1초 잠금 (감점 없음). 잠금은 게임 시간 기준이라 다음 라운드까지 이어질 수 있다.
 * - 10초 동안 아무도 못 찾으면 공개하고 넘어감 (점수 없음). 15라운드 뒤 점수 순 (동점 같은 순위).
 * 시간은 화면이 넘겨준다(ms, 게임 시간) → 테스트에서 그대로 재현 가능.
 */
class OddOneGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val scores = IntArray(playerCount)
    /** 플레이어별 잠금 끝 시각 (게임 시간 ms) */
    val lockUntil = LongArray(playerCount) { Long.MIN_VALUE }
    /** 라운드별 차이 종류 */
    val kinds: List<Diff> = PuzzleMaker.schedule(random)
    /** 끝난 라운드 수 (찾음·시간 초과 모두) */
    var roundsDone = 0
        private set
    var phase = Phase.IDLE
        private set
    var current: Puzzle? = null
        private set
    /** 이번 라운드를 찾은 사람 (-1 = 아직/아무도) */
    var roundWinner = -1
        private set

    val gameOver: Boolean get() = roundsDone >= GameConfig.ROUNDS

    /** 다음 라운드 문제 (앞 라운드와 다른 모양) */
    fun nextPuzzle(): Puzzle {
        check(!gameOver) { "게임이 끝났습니다" }
        return ask(PuzzleMaker.make(roundsDone + 1, kinds[roundsDone], random, current?.shape))
    }

    /** 정해진 문제를 낸다 (테스트·재현용) */
    fun ask(p: Puzzle): Puzzle {
        check(!gameOver) { "게임이 끝났습니다" }
        current = p
        roundWinner = -1
        phase = Phase.ASKING
        return p
    }

    fun isLocked(player: Int, nowMs: Long): Boolean = nowMs < lockUntil[player]

    /** player 가 cell(0..15, 그 사람 기준 왼쪽 위부터 가로로) 을 nowMs(게임 시간)에 눌렀다 */
    fun tap(player: Int, cell: Int, nowMs: Long): Tap {
        if (player !in 0 until playerCount || cell !in 0 until GameConfig.CELLS) return Tap.Ignored
        val p = current
        if (phase != Phase.ASKING || p == null) return Tap.Ignored
        if (isLocked(player, nowMs)) return Tap.Locked(player, lockUntil[player])
        return if (p.isOdd(cell)) {
            scores[player] += GameConfig.CORRECT_POINTS
            roundWinner = player
            endRound()
            Tap.Correct(player, cell)
        } else {
            lockUntil[player] = nowMs + GameConfig.WRONG_LOCK_MS
            Tap.Wrong(player, cell, lockUntil[player])
        }
    }

    /** 시간 초과: 아직 묻는 중이면 아무도 점수 없이 끝. 끝냈으면 true */
    fun timeout(): Boolean {
        if (phase != Phase.ASKING) return false
        endRound()
        return true
    }

    private fun endRound() {
        phase = Phase.IDLE
        roundsDone++
    }

    /** 최종 순위 (1부터): 점수 높은 순, 같은 점수 같은 순위 */
    fun finalRanks(): IntArray = Ranking.competition(scores.map { it.toLong() })
}

/** HSL → ARGB (순수 계산, 할당 없음) */
object Hsl {
    fun toArgb(hueDeg: Float, sat: Float, light: Float): Int {
        val h = ((hueDeg % 360f) + 360f) % 360f / 60f
        val s = sat.coerceIn(0f, 1f)
        val l = light.coerceIn(0f, 1f)
        val c = (1f - Math.abs(2f * l - 1f)) * s
        val x = c * (1f - Math.abs(h % 2f - 1f))
        val m = l - c / 2f
        val r: Float
        val g: Float
        val b: Float
        when (h.toInt()) {
            0 -> { r = c; g = x; b = 0f }
            1 -> { r = x; g = c; b = 0f }
            2 -> { r = 0f; g = c; b = x }
            3 -> { r = 0f; g = x; b = c }
            4 -> { r = x; g = 0f; b = c }
            else -> { r = c; g = 0f; b = x }
        }
        return (0xFF shl 24) or (ch(r + m) shl 16) or (ch(g + m) shl 8) or ch(b + m)
    }

    private fun ch(v: Float): Int = Math.round(v.coerceIn(0f, 1f) * 255f)
}
