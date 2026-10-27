package com.siwoo.party.games.artillery

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.physics.FixedStep
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** 진행 단계: 조준 → 날아가는 중 → (떨어짐) 판정 끝·다음 차례 기다림 → 끝 */
enum class Phase { AIM, FLYING, RESOLVED, OVER }

/** 쏘기 결과 */
enum class FireResult {
    FIRED,
    /** 힘이 MIN_POWER_PCT 보다 약함 → 안 쏨 */
    TOO_WEAK,
    /** 지금은 못 쏨 (날아가는 중·끝남) */
    NOT_NOW,
}

/** 끝난 이유 */
enum class EndReason {
    NONE,
    /** 한 명만 남음 */
    LAST_STANDING,
    /** 마지막 사람들이 한꺼번에 탈락 (공동 1등) */
    ALL_DOWN,
    /** 안전장치: 최대 발사 수 → 남은 사람은 체력 순 */
    MAX_TURNS,
}

/** 한 발 쏜 결과 (떨어진 뒤 판정) — 매번 같은 객체를 다시 채운다 */
class ShotReport(players: Int) {
    var shooter = -1
    var angle = 0
    var power = 0
    var wind = 0
    val impact = Impact()
    /** 이번에 각자 잃은 체력 (실제로 깎인 양) */
    val damage = IntArray(players)
    /** 맞기 전 체력 */
    val hpBefore = IntArray(players)
    /** 이번 발사로 탈락했는지 */
    val eliminated = BooleanArray(players)
    var eliminatedCount = 0
    var gameOver = false
    var endReason = EndReason.NONE

    /** 누군가 피해를 입었는지 */
    val anyDamage: Boolean get() = damage.any { it > 0 }
}

/**
 * 포탄 대결 규칙 (2~4인, 차례대로). 순수 Kotlin — 시간은 밖에서 advance(ms) 로, 난수는 생성자로 넣는다.
 * - 대포는 왼쪽부터 0번 플레이어 순서로 지형 위에 고르게 놓인다 ([Terrain.cannonXs]).
 * - 차례: 각도(도)·힘(%) 정수로 [fire] → [advance] 로 날아감 → 떨어지면 피해 계산·탈락 판정 → [nextTurn] (새 바람, 다음 살아 있는 사람).
 * - 피해: 폭발 지점에서 대포 몸통(원)까지 거리 d < BLAST_R 이면 MAX_DAMAGE × (1 − d/BLAST_R) 반올림, 직격은 d = 0.
 *   쏜 사람 자신도 맞는다 (자폭 가능). 화면 밖(좌우)으로 나가면 빗나감.
 * - 체력 0 → 탈락 (차례에서 빠짐). 마지막 1명이 남으면 끝. 순위는 늦게 탈락할수록 높고, 같은 발사에 탈락하면 같은 순위.
 * - 안전장치: 발사가 인원 × MAX_TURNS_PER_PLAYER 번이 되면 끝 → 남은 사람은 체력 순.
 */
class ArtilleryGame(
    val terrain: Terrain,
    cannonXs: FloatArray,
    first: Int,
    private val random: Random,
    initialWind: Int? = null,
) {
    val playerCount = cannonXs.size

    init {
        require(playerCount in 1..GameConfig.MAX_PLAYERS)
        require(first in 0 until playerCount)
    }

    val cannons: List<Cannon> = List(playerCount) { i -> Cannon(i, cannonXs[i], terrain.heightAt(cannonXs[i])) }

    var phase = Phase.AIM
        private set
    /** 지금 차례 */
    var current = first
        private set
    /** 지금 바람 단계 (−WIND_LEVELS..WIND_LEVELS, + = 오른쪽) */
    var wind = initialWind ?: rollWind()
        private set
    /** 지금까지 쏜 수 */
    var shots = 0
        private set
    /** 최대 발사 수 */
    val maxShots = playerCount * GameConfig.MAX_TURNS_PER_PLAYER
    var endReason = EndReason.NONE
        private set

    /** 날아가는 포탄 (화면이 그림) */
    val shell = Shell()
    /** 마지막 발사 결과 */
    val report = ShotReport(playerCount)
    /** 지금 날아가는 포탄을 쏜 사람 */
    var shooter = -1
        private set

    private val stepper = FixedStep(GameConfig.MAX_FRAME_MS)
    private val scratchShell = Shell()

    val over: Boolean get() = phase == Phase.OVER
    val aliveCount: Int get() = cannons.count { it.alive }
    val canFire: Boolean get() = phase == Phase.AIM

    private fun rollWind(): Int = random.nextInt(-GameConfig.WIND_LEVELS, GameConfig.WIND_LEVELS + 1)

    /** 쏘기 (각도는 ANGLE_MIN..ANGLE_MAX 로, 힘은 ..100 으로 자름) */
    fun fire(angleDeg: Int, powerPct: Int): FireResult {
        if (phase != Phase.AIM) return FireResult.NOT_NOW
        if (powerPct < GameConfig.MIN_POWER_PCT) return FireResult.TOO_WEAK
        val a = angleDeg.coerceIn(GameConfig.ANGLE_MIN, GameConfig.ANGLE_MAX)
        val p = powerPct.coerceAtMost(100)
        val c = cannons[current]
        c.angle = a
        c.lastAngle = a
        c.lastPower = p
        shooter = current
        shots++
        report.shooter = current
        report.angle = a
        report.power = p
        report.wind = wind
        Ballistics.launch(shell, c, a, p, wind)
        stepper.reset()
        phase = Phase.FLYING
        return FireResult.FIRED
    }

    /** 시간 진행 (ms). 아직 날아가는 중이면 true, 이번에 떨어졌거나 날아가는 중이 아니면 false */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.FLYING) return false
        val n = stepper.add(dtMs)
        for (k in 0 until n) {
            if (Ballistics.step(shell, terrain, cannons, report.impact)) {
                resolve()
                return false
            }
        }
        return true
    }

    /** 끝날 때까지 한꺼번에 (테스트용) */
    fun advanceUntilLanded() {
        while (phase == Phase.FLYING) advance(GameConfig.MAX_FRAME_MS)
    }

    /** 떨어진 뒤 피해·탈락·끝 판정 */
    private fun resolve() {
        val imp = report.impact
        for (i in 0 until playerCount) {
            report.hpBefore[i] = cannons[i].hp
            report.damage[i] = 0
            report.eliminated[i] = false
        }
        report.eliminatedCount = 0
        if (imp.kind.explodes) {
            damageAt(imp.x, imp.y, imp.cannon, report.damage)
            for (c in cannons) {
                if (!c.alive) {
                    report.damage[c.index] = 0
                    continue
                }
                val d = report.damage[c.index].coerceAtMost(c.hp)
                report.damage[c.index] = d
                c.hp -= d
                if (!c.alive) {
                    c.eliminatedAt = shots
                    report.eliminated[c.index] = true
                    report.eliminatedCount++
                }
            }
        }
        val alive = aliveCount
        endReason = when {
            alive == 0 -> EndReason.ALL_DOWN
            alive == 1 -> EndReason.LAST_STANDING
            shots >= maxShots -> EndReason.MAX_TURNS
            else -> EndReason.NONE
        }
        report.endReason = endReason
        report.gameOver = endReason != EndReason.NONE
        phase = if (report.gameOver) Phase.OVER else Phase.RESOLVED
    }

    /** 다음 차례 (판정이 끝난 뒤): 다음 살아 있는 사람 + 새 바람. 끝났으면 false */
    fun nextTurn(): Boolean {
        if (phase != Phase.RESOLVED) return false
        current = nextAlive(current)
        wind = rollWind()
        phase = Phase.AIM
        return true
    }

    /** from 다음(시계 방향)의 살아 있는 사람 (아무도 없으면 from) */
    fun nextAlive(from: Int): Int {
        for (k in 1..playerCount) {
            val i = (from + k) % playerCount
            if (cannons[i].alive) return i
        }
        return from
    }

    /**
     * 폭발 (x, y) 의 피해를 out 에 채움 (살아 있든 아니든 계산만, 체력 반영은 안 함).
     * directCannon = 직격당한 대포 (그 대포는 거리 0 = 최대 피해), −1 = 없음.
     */
    fun damageAt(x: Float, y: Float, directCannon: Int, out: IntArray) {
        for (c in cannons) {
            out[c.index] = if (c.index == directCannon) GameConfig.MAX_DAMAGE else blastDamage(distanceToBody(c, x, y))
        }
    }

    /**
     * 시험 사격: 지금 차례·바람으로 (angle, power) 를 쏘면 어디에 떨어지는지 (상태는 바꾸지 않음).
     * 테스트·연습용.
     */
    fun simulate(angleDeg: Int, powerPct: Int, out: Impact, from: Int = current, windLevel: Int = wind) {
        Ballistics.launch(scratchShell, cannons[from], angleDeg.coerceIn(GameConfig.ANGLE_MIN, GameConfig.ANGLE_MAX), powerPct.coerceAtMost(100), windLevel)
        Ballistics.run(scratchShell, terrain, cannons, out)
    }

    /** 순위 (players 순서, 1부터): 살아 있는 사람 > 늦게 탈락한 사람 > 일찍 탈락한 사람. 살아 있는 사람끼리는 체력 순 */
    fun ranks(): IntArray =
        Ranking.competition(cannons.map { if (it.alive) ALIVE_BASE + it.hp else it.eliminatedAt.toLong() })

    // ── 테스트용 ──

    /** 바람 바꾸기 (테스트용) */
    fun setWind(level: Int) {
        wind = level.coerceIn(-GameConfig.WIND_LEVELS, GameConfig.WIND_LEVELS)
    }

    /** 체력 바꾸기 (테스트용, 살아 있는 대포만 1..START_HP) */
    fun setHp(index: Int, hp: Int) {
        val c = cannons[index]
        if (c.alive) c.hp = hp.coerceIn(1, GameConfig.START_HP)
    }

    companion object {
        private const val ALIVE_BASE = 1_000_000L

        /** 시드로 지형을 만든 새 게임 */
        fun create(players: Int, first: Int, seed: Int, random: Random): ArtilleryGame {
            val xs = Terrain.cannonXs(players)
            return ArtilleryGame(Terrain.generate(seed, xs), xs, first, random)
        }

        /** 거리 d(대포 몸통 가장자리까지) 의 피해: d ≥ BLAST_R → 0, 아니면 MAX_DAMAGE × (1 − d/BLAST_R) 반올림 */
        fun blastDamage(d: Float): Int {
            if (d >= GameConfig.BLAST_R) return 0
            val v = GameConfig.MAX_DAMAGE * (1f - d.coerceAtLeast(0f) / GameConfig.BLAST_R)
            return v.roundToInt().coerceIn(0, GameConfig.START_HP)
        }

        /** 폭발 지점에서 대포 몸통(원) 가장자리까지 거리 (안이면 0) */
        fun distanceToBody(c: Cannon, x: Float, y: Float): Float {
            val dx = x - c.x
            val dy = y - c.y
            return (sqrt(dx * dx + dy * dy) - GameConfig.CANNON_R).coerceAtLeast(0f)
        }
    }
}
