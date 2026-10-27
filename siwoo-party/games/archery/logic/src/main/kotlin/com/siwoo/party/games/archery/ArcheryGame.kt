package com.siwoo.party.games.archery

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.aim.BreathHold
import com.siwoo.party.common.aim.Sway
import com.siwoo.party.common.aim.SwayShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 진행 단계: 조준 중 → 결과 보는 중 → 끝 */
enum class Phase { AIM, RESULT, OVER }

/**
 * 양궁 규칙 (2~4인, 1인당 GameConfig.ARROWS 발). 순수 Kotlin — 시각(ms)은 밖에서 넣는다 (화면은 timers.now()).
 *
 * - 차례: 한 발씩 돌아가며 (first 부터 1발씩, 모두 1발 쏘면 2발째 …). 총 players × ARROWS 발.
 * - 발마다 새 바람(방향·세기)과 새 흔들림 궤적(시작 위치·떨림)을 시드 난수로 정한다.
 * - 조준점 = 조준 가운데(끌어서 옮김) + 흔들림(Sway) × 숨 참기 배율(BreathHold).
 *   누르면 숨 참기 시작(press), 누른 채 끌면 조준 가운데가 움직이고(moveAim), 떼면 발사(release).
 * - 꽂히는 자리 = 놓는 순간의 조준점 + 바람에 밀린 거리 + 작은 흩어짐(반지름 SCATTER × noise).
 * - 합계 점수 → 같으면 X 개수 → 같으면 10점(X 포함) 개수 → 그래도 같으면 같은 순위.
 *
 * 난수는 한 발마다 같은 개수만 쓰므로 같은 seed 면 쏜 자리와 상관없이 바람·흔들림 순서가 같다.
 *
 * @param seed 바람·흔들림·흩어짐 난수 씨앗
 * @param first 처음 쏘는 사람
 * @param noise 흔들림·흩어짐 배율 (1 = 보통, 0 = 전혀 안 흔들림 — 화면 테스트용)
 */
class ArcheryGame(val players: Int, seed: Long, val first: Int = 0, val noise: Float = 1f) {

    init {
        require(players in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(first in 0 until players)
    }

    private val random = Random(seed)

    /** 흔들림 (고리 단위) */
    val sway = Sway(
        SwayShape.FIGURE8,
        GameConfig.SWAY_AMP_X * noise,
        GameConfig.SWAY_AMP_Y * noise,
        GameConfig.SWAY_PERIOD_MS,
        GameConfig.SWAY_JITTER * noise,
        GameConfig.SWAY_JITTER_MS,
        seed,
    )

    /** 숨 참기 */
    val breath = BreathHold(
        GameConfig.BREATH_MIN,
        GameConfig.BREATH_CALM_MS,
        GameConfig.BREATH_LIMIT_MS,
        GameConfig.BREATH_GROW_MS,
        GameConfig.BREATH_MAX,
    )

    val totalShots = players * GameConfig.ARROWS

    /** 지금 쏠(쏜) 화살 번호 (전체, 0부터) */
    var shotIndex = 0
        private set
    var phase = Phase.AIM
        private set
    /** 이번 발의 바람 */
    var wind: Wind = Wind.CALM
        private set
    /** 조준 가운데 (끌어서 옮긴 자리) */
    var aimBaseX = 0f
        private set
    var aimBaseY = 0f
        private set

    /** [사람][발] 기록 (아직 안 쏜 발은 null) */
    val shots: Array<Array<Shot?>> = Array(players) { arrayOfNulls<Shot>(GameConfig.ARROWS) }
    var lastShot: Shot? = null
        private set
    val totals = IntArray(players)
    val xCount = IntArray(players)
    /** 10점 개수 (X 포함) */
    val tenCount = IntArray(players)

    init {
        newShot()
    }

    /** 지금 쏘는 사람 */
    val current: Int get() = shooterAt(minOf(shotIndex, totalShots - 1))

    /** 지금 몇 발째 (0부터, 사람마다 같은 번호) */
    val arrow: Int get() = minOf(shotIndex, totalShots - 1) / players

    /** 전체 k 번째 화살을 쏘는 사람 (한 발씩 돌아가며) */
    fun shooterAt(k: Int): Int = (first + k) % players

    /** 전체 k 번째 화살이 그 사람의 몇 번째 화살인지 */
    fun arrowAt(k: Int): Int = k / players

    /** 이 사람이 쏜 화살 수 */
    fun shotsBy(player: Int): Int = shots[player].count { it != null }

    // ── 조준 ──

    /** 지금 흔들림 배율 (숨 참기) */
    fun swayScaleAt(now: Long): Float = breath.multiplierAt(now)

    /** 지금 흔들림 크기 (고리 단위, 가로) */
    fun swayAmplitudeAt(now: Long): Float = sway.amplitudeX * swayScaleAt(now)

    /** 지금 조준점 → out[0], out[1] (할당 없음) */
    fun aimAt(now: Long, out: FloatArray) {
        sway.offset(now, breath.multiplierAt(now), out)
        out[0] += aimBaseX
        out[1] += aimBaseY
    }

    val holding: Boolean get() = breath.holding

    /** 누름 → 숨 참기 시작 (조준 중에만) */
    fun press(now: Long): Boolean {
        if (phase != Phase.AIM) return false
        breath.press(now)
        return true
    }

    /** 누른 채 끌기 → 조준 가운데를 (dx, dy) 고리만큼 옮김 (AIM_LIMIT 원 안으로 제한) */
    fun moveAim(dx: Float, dy: Float) {
        if (phase != Phase.AIM) return
        setAim(aimBaseX + dx, aimBaseY + dy)
    }

    /** 조준 가운데를 (x, y) 로 (AIM_LIMIT 원 안으로 제한) */
    fun setAim(x: Float, y: Float) {
        if (phase != Phase.AIM) return
        val d = sqrt(x * x + y * y)
        val k = if (d > GameConfig.AIM_LIMIT) GameConfig.AIM_LIMIT / d else 1f
        aimBaseX = x * k
        aimBaseY = y * k
    }

    /** 쏘지 않고 숨 참기만 그만 (일시정지 등) */
    fun cancelHold(now: Long) {
        breath.release(now)
    }

    /** 뗌 → 발사. 누르고 있지 않았거나 조준 중이 아니면 null */
    fun release(now: Long): Shot? {
        if (phase != Phase.AIM || !breath.holding) return null
        val scale = breath.multiplierAt(now)
        val ax = aimBaseX + sway.x(now, scale)
        val ay = aimBaseY + sway.y(now, scale)
        val held = breath.release(now)
        return fire(ax, ay, held, scale)
    }

    /** (ax, ay) 를 조준한 채로 쏨 (바람·흩어짐 더함) */
    private fun fire(ax: Float, ay: Float, held: Long, scale: Float): Shot {
        // 흩어짐: 원 안 고르게 (noise 와 상관없이 난수 두 개를 늘 씀 → 순서 결정성)
        val a = random.nextDouble() * 2.0 * PI
        val r = GameConfig.SCATTER * noise * sqrt(random.nextDouble()).toFloat()
        val x = land(ax, wind.driftX, (cos(a) * r).toFloat())
        val y = land(ay, wind.driftY, (sin(a) * r).toFloat())
        val p = current
        val k = arrow
        val d = Target.distance(x, y)
        val score = Target.score(d)
        val isX = Target.isX(d)
        val s = Shot(p, k, ax, ay, x, y, score, isX, wind, held, scale)
        shots[p][k] = s
        totals[p] += score
        if (score == GameConfig.RINGS) tenCount[p]++
        if (isX) xCount[p]++
        lastShot = s
        phase = Phase.RESULT
        return s
    }

    /** 결과를 본 뒤 다음 화살로. 마지막이었으면 OVER */
    fun next(): Phase {
        if (phase != Phase.RESULT) return phase
        shotIndex++
        if (shotIndex >= totalShots) {
            phase = Phase.OVER
            return phase
        }
        newShot()
        phase = Phase.AIM
        return phase
    }

    /** 새 화살: 바람·흔들림 궤적을 새로, 조준은 가운데로 */
    private fun newShot() {
        wind = Wind.random(random)
        sway.phaseMs = random.nextLong(GameConfig.SWAY_PERIOD_MS)
        sway.seed = random.nextLong()
        aimBaseX = 0f
        aimBaseY = 0f
        if (breath.holding) breath.release(0L)
    }

    // ── 순위 ──

    /** 순위 비교 값: 합계 → X 개수 → 10점 개수 (클수록 좋음) */
    fun rankKey(player: Int): Long = rankKey(totals[player], xCount[player], tenCount[player])

    /** players 순서의 순위 (1부터, 모두 같으면 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(List(players) { rankKey(it) })

    /** 1위들 */
    fun winners(): List<Int> {
        val r = ranks()
        return (0 until players).filter { r[it] == 1 }
    }

    // ── 테스트·연출용 ──

    /** 숨 참기 없이 (ax, ay) 를 조준해 바로 쏨 (흩어짐·바람은 그대로) */
    fun shootAt(ax: Float, ay: Float): Shot? {
        if (phase != Phase.AIM) return null
        if (breath.holding) breath.release(0L)
        return fire(ax, ay, 0L, 1f)
    }

    companion object {
        /** 꽂히는 자리 한 축 = 조준 + 바람 + 흩어짐 */
        fun land(aim: Float, drift: Float, scatter: Float): Float = aim + drift + scatter

        /** 순위 비교 값 (합계 최대 60, X·10 최대 6 → 자릿수가 겹치지 않음) */
        fun rankKey(total: Int, xs: Int, tens: Int): Long = total * 10_000L + xs * 100L + tens
    }
}
