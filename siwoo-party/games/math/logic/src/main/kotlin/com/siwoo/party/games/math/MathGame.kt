package com.siwoo.party.games.math

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 연산 */
enum class Op(val symbol: String) {
    ADD("+"),
    SUB("−"),
    MUL("×"),
    ;

    fun apply(a: Int, b: Int): Int = when (this) {
        ADD -> a + b
        SUB -> a - b
        MUL -> a * b
    }
}

/** 문제 하나: a op b = answer, 보기 choices (정답은 correctIndex 번째). 모든 구역이 같은 순서로 보여 준다 */
class Question(val stage: Int, val a: Int, val op: Op, val b: Int, val choices: IntArray) {
    val answer: Int = op.apply(a, b)
    val correctIndex: Int = choices.indexOf(answer)
    /** 화면용 "7 + 8 = ?" */
    val text: String = "$a ${op.symbol} $b = ?"
    /** 정답 공개용 "7 + 8 = 15" */
    val solved: String = "$a ${op.symbol} $b = $answer"
}

/**
 * 문제 만들기. 난이도 단계:
 * 1 = 한 자리 덧셈, 2 = 두 자리 덧셈·뺄셈, 3 = 곱셈 섞기 (곱셈 또는 두 자리 덧뺄셈).
 * 오답 보기는 정답 가까이(±NEAR_MAX)의 자주 하는 실수 값으로, 서로 다르고 0 이상.
 */
class QuestionGen(private val random: Random) {

    /** 질문 번호(1부터) → 단계 */
    fun stageOf(number: Int): Int = when {
        number <= GameConfig.STAGE1_LAST -> 1
        number <= GameConfig.STAGE2_LAST -> 2
        else -> 3
    }

    /** stage 단계 문제. forceMul = 3단계에서 곱셈으로 고정 */
    fun generate(stage: Int, forceMul: Boolean = false): Question {
        val op: Op
        val a: Int
        val b: Int
        when (stage) {
            1 -> {
                op = Op.ADD
                a = rand(GameConfig.S1_MIN, GameConfig.S1_MAX)
                b = rand(GameConfig.S1_MIN, GameConfig.S1_MAX)
            }
            2 -> {
                if (random.nextBoolean()) {
                    op = Op.ADD
                    a = rand(GameConfig.S2_ADD_MIN, GameConfig.S2_ADD_MAX)
                    b = rand(GameConfig.S2_ADD_MIN, GameConfig.S2_ADD_MAX)
                } else {
                    op = Op.SUB
                    a = rand(GameConfig.S2_SUB_MIN + 1, GameConfig.S2_SUB_MAX)
                    b = rand(GameConfig.S2_SUB_MIN, a - 1)
                }
            }
            else -> {
                val mul = forceMul || random.nextInt(3) < 2
                if (!mul) {
                    val q = generate(2)
                    return Question(3, q.a, q.op, q.b, q.choices)
                }
                op = Op.MUL
                if (random.nextInt(3) < 2) {
                    a = rand(GameConfig.MUL_MIN, GameConfig.MUL_MAX)
                    b = rand(GameConfig.MUL_MIN, GameConfig.MUL_MAX)
                } else {
                    a = rand(GameConfig.MUL_BIG_MIN, GameConfig.MUL_BIG_MAX)
                    b = rand(GameConfig.MUL_MIN, GameConfig.MUL_BIG_BY_MAX)
                }
            }
        }
        return Question(stage, a, op, b, choicesFor(stage, a, op, b))
    }

    /** 정답 + 그럴듯한 오답 2개를 섞은 보기 */
    fun choicesFor(stage: Int, a: Int, op: Op, b: Int): IntArray {
        val ans = op.apply(a, b)
        val offsets = ArrayList<Int>()
        when (op) {
            Op.ADD, Op.SUB -> {
                offsets.addAll(listOf(1, -1, 2, -2))
                if (stage == 1) offsets.addAll(listOf(3, -3)) else offsets.addAll(listOf(10, -10, 9, -9, 11, -11).filter { kotlin.math.abs(it) <= GameConfig.NEAR_MAX })
            }
            Op.MUL -> {
                // 옆 단 실수 a×(b±1), (a±1)×b 와 가까운 값
                if (a <= GameConfig.NEAR_MAX) { offsets.add(a); offsets.add(-a) }
                if (b <= GameConfig.NEAR_MAX) { offsets.add(b); offsets.add(-b) }
                offsets.addAll(listOf(1, -1, 2, -2, 10, -10))
            }
        }
        val cands = offsets.map { ans + it }.filter { it >= 0 && it != ans }.distinct().shuffled(random)
        val list = ArrayList<Int>(GameConfig.CHOICES)
        list.add(ans)
        for (c in cands) {
            if (list.size >= GameConfig.CHOICES) break
            list.add(c)
        }
        list.shuffle(random)
        return list.toIntArray()
    }

    private fun rand(lo: Int, hi: Int): Int = lo + random.nextInt(hi - lo + 1)

    /** 한 판 문제 count 개 (같은 문제 없이). 3단계는 첫 문제와 하나 걸러 곱셈 고정 */
    fun makeSet(count: Int = GameConfig.MATH_QUESTIONS): List<Question> {
        val out = ArrayList<Question>(count)
        val seen = HashSet<String>()
        for (n in 1..count) {
            val stage = stageOf(n)
            val forceMul = stage == 3 && (n - GameConfig.STAGE2_LAST) % 2 == 1
            var q = generate(stage, forceMul)
            var tries = 0
            while (!seen.add(q.text) && tries < 50) { q = generate(stage, forceMul); tries++ }
            out.add(q)
        }
        return out
    }
}

/** 단계 */
enum class Phase { READY, ASKING, CLOSED, GAME_OVER }

/** 보기 누름 결과 */
enum class Answer {
    /** 문제 중이 아님 (이미 누가 맞힘·시간 끝·시작 전) */
    IGNORED,
    /** 잠금 중 (틀린 뒤 1.5초) */
    LOCKED,
    /** 틀림 → 잠금 시작 */
    WRONG,
    /** 먼저 맞힘 → +1, 문제 끝 */
    CORRECT,
}

/**
 * 암산 대결 규칙.
 * - 10문제, 난이도 점점 증가 (QuestionGen)
 * - 모든 구역에 같은 문제·같은 보기(같은 순서). 먼저 맞힌 사람 +1, 그 문제는 끝
 * - 틀리면 그 사람만 MATH_LOCK_MS 동안 잠금 (그동안 누름 무시). 새 문제가 나오면 잠금 풀림
 * - 제한 시간이 끝나면 점수 없이 다음 문제
 * - 순위: 점수 순 (동점 같은 순위)
 * 시간·난수는 밖에서 넣는다 → 테스트에서 그대로 재현 가능.
 */
class MathGame(val playerCount: Int, random: Random = Random.Default, questions: List<Question>? = null) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val questions: List<Question> = questions ?: QuestionGen(random).makeSet()
    val scores = IntArray(playerCount)
    /** 잠금이 풀리는 게임 시각 (0 = 잠금 없음) */
    val lockUntil = LongArray(playerCount)
    /** 이번 문제에서 사람마다 틀린 보기 (비트) */
    val wrongMask = IntArray(playerCount)

    var phase = Phase.READY
        private set
    /** 지금 문제 번호 0.. (시작 전 -1) */
    var index = -1
        private set
    /** 이번 문제를 맞힌 사람 (-1 = 아직/없음) */
    var winner = -1
        private set

    val current: Question get() = questions[index.coerceAtLeast(0)]
    /** 1부터 */
    val number: Int get() = index + 1
    val total: Int get() = questions.size
    val hasNext: Boolean get() = index + 1 < questions.size

    /** 다음 문제 → ASKING (잠금·틀린 표시 초기화) */
    fun nextQuestion(): Question {
        check(phase == Phase.READY || phase == Phase.CLOSED) { "다음 문제로 갈 수 없습니다: $phase" }
        check(hasNext) { "문제가 끝났습니다" }
        index++
        winner = -1
        lockUntil.fill(0)
        wrongMask.fill(0)
        phase = Phase.ASKING
        return current
    }

    fun isLocked(player: Int, nowMs: Long): Boolean = nowMs < lockUntil[player]

    /** player 가 choice 번째 보기를 누름 (nowMs = 게임 시간) */
    fun answer(player: Int, choice: Int, nowMs: Long): Answer {
        if (player !in 0 until playerCount || choice !in 0 until GameConfig.CHOICES) return Answer.IGNORED
        if (phase != Phase.ASKING) return Answer.IGNORED
        if (isLocked(player, nowMs)) return Answer.LOCKED
        if (choice == current.correctIndex) {
            scores[player]++
            winner = player
            close()
            return Answer.CORRECT
        }
        lockUntil[player] = nowMs + GameConfig.MATH_LOCK_MS
        wrongMask[player] = wrongMask[player] or (1 shl choice)
        return Answer.WRONG
    }

    /** 제한 시간 끝 (아무도 못 맞힘) */
    fun timeUp() {
        if (phase == Phase.ASKING) close()
    }

    private fun close() {
        phase = if (hasNext) Phase.CLOSED else Phase.GAME_OVER
    }

    val gameOver: Boolean get() = phase == Phase.GAME_OVER

    /** 최종 순위 (1부터, 점수 많은 순, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(scores.map { it.toLong() })
}

/** 구역 안 그림 배치용 상자 (구역 가운데가 원점, 그 구역 사람이 보는 방향 기준: x 오른쪽, y 아래쪽) */
class Box(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/**
 * 모든 구역에 똑같이 쓰는 배치 (공정성: 같은 크기·같은 위치·같은 보기 순서, 구역 방향으로만 회전).
 * 칸 크기 = 모든 구역 중 가장 작은 폭 × (가장 작은 높이와 폭×0.85 중 작은 것) — 구역 가운데에 놓는다.
 * 위: 이름·문제 번호·점수 / 시간 막대 / 가운데: 문제 / 아래: 보기 3개 가로 한 줄.
 */
class MathLayout {
    var cellW = 0f
        private set
    var cellH = 0f
        private set
    val choices = Array(GameConfig.CHOICES) { Box() }
    val timeBar = Box()
    var headerY = 0f
        private set
    /** 문제 글자 가운데 y */
    var questionY = 0f
        private set

    fun layout(minZoneW: Float, minZoneH: Float) {
        cellW = minZoneW
        cellH = minOf(minZoneH, minZoneW * 0.85f)
        val w = cellW
        val h = cellH
        headerY = -h * 0.34f
        timeBar.set(-w * 0.46f, -h * 0.29f, w * 0.46f, -h * 0.29f + maxOf(h * 0.025f, 3f))
        val gap = w * 0.035f
        val bw = (w * 0.94f - gap * (GameConfig.CHOICES - 1)) / GameConfig.CHOICES
        val bottom = h * 0.47f
        val bh = minOf(h * 0.36f, w * 0.2f)
        for (k in 0 until GameConfig.CHOICES) {
            val l = -w * 0.47f + k * (bw + gap)
            choices[k].set(l, bottom - bh, l + bw, bottom)
        }
        questionY = (timeBar.bottom + choices[0].top) / 2f
    }

    /** 사람 기준 좌표 → 보기 번호 (보기 사이 틈·바깥은 -1) */
    fun choiceAt(lx: Float, ly: Float): Int {
        for (k in choices.indices) if (choices[k].contains(lx, ly)) return k
        return -1
    }

    companion object {
        /** 화면 좌표 (x, y) → 구역(가운데 cx, cy, 회전 rotation°) 사람 기준 좌표 out[0], out[1] */
        fun toLocal(cx: Float, cy: Float, rotation: Float, x: Float, y: Float, out: FloatArray) {
            val dx = x - cx
            val dy = y - cy
            val a = Math.toRadians(-rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = dx * cos - dy * sin
            out[1] = dx * sin + dy * cos
        }

        /** 사람 기준 좌표 (lx, ly) → 화면 좌표 out[0], out[1] */
        fun toScreen(cx: Float, cy: Float, rotation: Float, lx: Float, ly: Float, out: FloatArray) {
            val a = Math.toRadians(rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = cx + lx * cos - ly * sin
            out[1] = cy + lx * sin + ly * cos
        }
    }
}
