package com.siwoo.party.games.mole

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 두더지 종류와 점수 */
enum class Kind(val points: Int) {
    NORMAL(GameConfig.POINTS_NORMAL),
    GOLD(GameConfig.POINTS_GOLD),
    BOMB(GameConfig.POINTS_BOMB),
}

/** 두더지 한 번 등장: 경기 시작 기준 atMs 부터 upMs 동안 hole 에 올라와 있다 */
class Spawn(val id: Int, val atMs: Long, val hole: Int, val kind: Kind, val upMs: Long) {
    val endMs: Long get() = atMs + upMs
    fun isUp(t: Long): Boolean = t >= atMs && t < endMs
}

/** 터치 결과 */
enum class Hit { NORMAL, GOLD, BOMB, MISS, ALREADY, IGNORED }

/**
 * 등장 순서표 (모든 구역 공통). 경기가 진행될수록 올라와 있는 시간과 간격이 짧아진다.
 * 같은 구멍은 앞 두더지가 들어가고 HOLE_REST_MS 가 지나야 다시 쓴다. 모두 GAME_MS 안에 끝난다.
 */
object MolePattern {
    /** 경기 진행 비율 f(0..1) 의 올라와 있는 시간 */
    fun upMsAt(f: Double): Long = (GameConfig.UP_START_MS + (GameConfig.UP_END_MS - GameConfig.UP_START_MS) * f.coerceIn(0.0, 1.0)).toLong()

    fun gapMsAt(f: Double): Double = GameConfig.GAP_START_MS + (GameConfig.GAP_END_MS - GameConfig.GAP_START_MS) * f.coerceIn(0.0, 1.0)

    fun generate(random: Random): List<Spawn> {
        val list = ArrayList<Spawn>()
        val freeAt = LongArray(GameConfig.HOLES)
        val free = IntArray(GameConfig.HOLES)
        var t = GameConfig.FIRST_DELAY_MS
        while (true) {
            val f = t.toDouble() / GameConfig.GAME_MS
            val up = upMsAt(f)
            if (t + up > GameConfig.GAME_MS) break
            var nFree = 0
            for (h in 0 until GameConfig.HOLES) if (freeAt[h] <= t) free[nFree++] = h
            if (nFree == 0) { t += 40; continue }
            val hole = free[random.nextInt(nFree)]
            val r = random.nextDouble()
            val kind = when {
                list.isEmpty() -> Kind.NORMAL
                r < GameConfig.GOLD_RATE -> Kind.GOLD
                r < GameConfig.GOLD_RATE + GameConfig.BOMB_RATE -> Kind.BOMB
                else -> Kind.NORMAL
            }
            list.add(Spawn(list.size, t, hole, kind, up))
            freeAt[hole] = t + up + GameConfig.HOLE_REST_MS
            val jitter = 1.0 + (random.nextDouble() * 2 - 1) * GameConfig.GAP_JITTER
            t += (gapMsAt(f) * jitter).toLong().coerceAtLeast(1)
        }
        // 황금·폭탄이 너무 적으면 일반 두더지를 바꾼다 (첫 두더지는 그대로)
        ensure(list, Kind.GOLD, GameConfig.MIN_GOLD, random)
        ensure(list, Kind.BOMB, GameConfig.MIN_BOMB, random)
        return list
    }

    private fun ensure(list: ArrayList<Spawn>, kind: Kind, min: Int, random: Random) {
        var have = list.count { it.kind == kind }
        while (have < min) {
            val normals = list.indices.filter { it > 0 && list[it].kind == Kind.NORMAL }
            if (normals.isEmpty()) return
            val i = normals[random.nextInt(normals.size)]
            val s = list[i]
            list[i] = Spawn(s.id, s.atMs, s.hole, kind, s.upMs)
            have++
        }
    }
}

/**
 * 두더지 잡기 대결 규칙.
 * - 모든 구역에 같은 순서표(spawns)로 두더지가 나온다. 구역마다 따로 잡는다(남이 잡아도 내 구역 두더지는 그대로).
 * - 올라와 있는 두더지를 치면: 일반 +1, 황금 +3, 폭탄 -2. 한 두더지는 구역마다 한 번만. 빈 구멍은 빗나감(0점).
 * - 30초 뒤 점수 순 (동점 같은 순위).
 * 시간은 화면이 넘겨준다 (게임 시계 ms).
 */
class MoleGame(val playerCount: Int, random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val spawns: List<Spawn> = MolePattern.generate(random)
    val score = IntArray(playerCount)
    /** hits[p][spawnId] = 그 구역에서 잡았는지 */
    val hits = Array(playerCount) { BooleanArray(spawns.size) }
    val caught = IntArray(playerCount)
    val bombs = IntArray(playerCount)
    val misses = IntArray(playerCount)

    var started = false
        private set
    private var startAt = 0L

    fun start(now: Long) {
        check(!started)
        started = true
        startAt = now
    }

    /** 경기 시작 기준 시간 */
    fun elapsed(now: Long): Long = if (started) now - startAt else 0L

    fun remainingMs(now: Long): Long = (GameConfig.GAME_MS - elapsed(now)).coerceIn(0, GameConfig.GAME_MS)

    fun isOver(now: Long): Boolean = started && elapsed(now) >= GameConfig.GAME_MS

    /** hole 에 지금 올라와 있는 두더지 (없으면 null) */
    fun activeSpawn(hole: Int, now: Long): Spawn? {
        if (!started) return null
        val t = elapsed(now)
        for (i in spawns.indices) {
            val s = spawns[i]
            if (s.atMs > t) break
            if (s.hole == hole && s.isUp(t)) return s
        }
        return null
    }

    /** player 구역의 hole 을 침 */
    fun hit(player: Int, hole: Int, now: Long): Hit {
        if (!started || isOver(now) || player !in 0 until playerCount || hole !in 0 until GameConfig.HOLES) return Hit.IGNORED
        val s = activeSpawn(hole, now)
        if (s == null) {
            misses[player]++
            return Hit.MISS
        }
        if (hits[player][s.id]) return Hit.ALREADY
        hits[player][s.id] = true
        score[player] += s.kind.points
        return when (s.kind) {
            Kind.NORMAL -> { caught[player]++; Hit.NORMAL }
            Kind.GOLD -> { caught[player]++; Hit.GOLD }
            Kind.BOMB -> { bombs[player]++; Hit.BOMB }
        }
    }

    /** 최종 순위: 점수 높은 순, 동점은 같은 순위 */
    fun finalRanks(): IntArray = Ranking.competition(score.map { it.toLong() })
}
