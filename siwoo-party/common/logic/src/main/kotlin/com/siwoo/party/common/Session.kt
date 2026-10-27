package com.siwoo.party.common

/** 모임 참가자. id 는 모임 안에서 바뀌지 않는 번호 (이름을 바꿔도 점수 유지용) */
class PlayerInfo(val id: Int, val name: String, val colorIndex: Int)

/** 게임 한 판의 결과 기록 */
class GameRecord(
    val gameId: String,
    val playerIds: IntArray,
    val ranks: IntArray,
    val points: IntArray,
    /** 점수를 모임에 더했는지 (파티 존 "점수 반영" 끔이면 false, points 는 모두 0) */
    val scored: Boolean = true,
)

/**
 * 모임(세션): 참가자, 누적 점수, 게임별 결과 기록.
 * 플레이어를 바꿔도 같은 이름이면 점수를 이어 받는다.
 */
class Session(players: List<PlayerInfo>) {
    val players: MutableList<PlayerInfo> = players.toMutableList()
    /** 지금은 빠진 사람 (같은 이름으로 돌아오면 점수 복구) */
    val former: MutableList<PlayerInfo> = ArrayList()
    /** 플레이어 id → 누적 점수 */
    val scores: MutableMap<Int, Int> = LinkedHashMap()
    val history: MutableList<GameRecord> = ArrayList()
    var nextId: Int = (players.maxOfOrNull { it.id } ?: -1) + 1

    init {
        validate(this.players)
        for (p in players) scores[p.id] = 0
    }

    fun score(id: Int): Int = scores[id] ?: 0

    fun player(id: Int): PlayerInfo? = players.firstOrNull { it.id == id }

    /**
     * 게임 결과 반영. ids 와 ranks 는 같은 순서 (ranks 는 1부터, 동점 허용).
     * scored = false 면 기록만 남기고 점수는 더하지 않는다 (파티 존 점수 반영 끔).
     * @return 각자 받은 점수 (ids 순서)
     */
    fun applyResult(gameId: String, ids: IntArray, ranks: IntArray, scored: Boolean = true): IntArray {
        require(ids.size == ranks.size) { "ids / ranks 길이가 다름" }
        require(ids.toSet().size == ids.size) { "같은 플레이어가 두 번" }
        for (id in ids) require(player(id) != null) { "모임에 없는 플레이어: $id" }
        val pts = if (scored) ScoreRules.points(ranks) else IntArray(ranks.size)
        for (k in ids.indices) scores[ids[k]] = score(ids[k]) + pts[k]
        history.add(GameRecord(gameId, ids.copyOf(), ranks.copyOf(), pts.copyOf(), scored))
        return pts
    }

    /** 최근에 한 게임 (최근 것부터, 중복 없이 최대 n개) */
    fun recentGames(n: Int): List<String> {
        val out = ArrayList<String>()
        for (k in history.indices.reversed()) {
            val id = history[k].gameId
            if (id !in out) out.add(id)
            if (out.size >= n) break
        }
        return out
    }

    /** 현재 참가자 중 1등(동점 포함). 아직 아무도 점수가 없으면 빈 목록 */
    fun leaders(): List<PlayerInfo> {
        val best = players.maxOfOrNull { score(it.id) } ?: return emptyList()
        if (best <= 0) return emptyList()
        return players.filter { score(it.id) == best }
    }

    /** 현재 참가자의 누적 점수 순위 (players 순서, 동점 같은 순위) */
    fun standings(): IntArray = Ranking.competition(players.map { score(it.id).toLong() })

    /** 이 게임을 몇 번 했는지 */
    fun timesPlayed(gameId: String): Int = history.count { it.gameId == gameId }

    /**
     * 플레이어 변경. (이름, 색) 목록 → 같은 이름이 있던 사람은 번호·점수를 이어 받고
     * 새 이름은 새 번호(0점). 빠진 사람은 former 로 옮겨 둔다.
     */
    fun replacePlayers(entries: List<Pair<String, Int>>) {
        val known = players + former
        val next = ArrayList<PlayerInfo>()
        for ((name, color) in entries) {
            val old = known.firstOrNull { it.name == name && next.none { n -> n.id == it.id } }
            val id = old?.id ?: nextId++
            next.add(PlayerInfo(id, name, color))
            if (!scores.containsKey(id)) scores[id] = 0
        }
        validate(next)
        val leaving = players.filter { p -> next.none { it.id == p.id } }
        former.removeAll { f -> next.any { it.id == f.id } }
        former.addAll(leaving)
        players.clear()
        players.addAll(next)
    }

    companion object {
        fun validate(list: List<PlayerInfo>) {
            require(list.size in PartyConfig.MIN_PLAYERS..PartyConfig.MAX_PLAYERS) { "플레이어는 2~4명" }
            require(list.map { it.id }.toSet().size == list.size) { "번호 중복" }
            require(list.map { it.colorIndex }.toSet().size == list.size) { "색 중복" }
            require(list.map { it.name }.toSet().size == list.size) { "이름 중복" }
            for (p in list) require(p.name.isNotBlank()) { "빈 이름" }
        }
    }
}

/** Session ↔ JSON (자동 저장) */
object SessionCodec {
    private const val VERSION = 1

    fun encode(s: Session): String = Json.write(linkedMapOf<String, Any?>(
        "version" to VERSION,
        "players" to s.players.map { player(it) },
        "former" to s.former.map { player(it) },
        "scores" to s.scores.entries.map { listOf(it.key, it.value) },
        "history" to s.history.map { r ->
            linkedMapOf<String, Any?>("game" to r.gameId, "ids" to r.playerIds, "ranks" to r.ranks, "points" to r.points, "scored" to r.scored)
        },
        "nextId" to s.nextId,
    ))

    /** 깨졌거나 버전이 다르면 예외 */
    fun decode(text: String): Session {
        val root = Json.parse(text) as Map<*, *>
        require((root["version"] as Number).toInt() == VERSION) { "지원하지 않는 모임 저장 버전" }
        val s = Session((root["players"] as List<*>).map { readPlayer(it) })
        (root["former"] as List<*>).forEach { s.former.add(readPlayer(it)) }
        for (e in root["scores"] as List<*>) {
            val pair = e as List<*>
            s.scores[(pair[0] as Number).toInt()] = (pair[1] as Number).toInt()
        }
        for (h in root["history"] as List<*>) {
            val m = h as Map<*, *>
            s.history.add(GameRecord(m["game"] as String, ints(m["ids"]), ints(m["ranks"]), ints(m["points"]), m["scored"] as? Boolean ?: true))
        }
        s.nextId = maxOf((root["nextId"] as Number).toInt(), s.nextId)
        return s
    }

    private fun player(p: PlayerInfo) = linkedMapOf<String, Any?>("id" to p.id, "name" to p.name, "color" to p.colorIndex)

    private fun readPlayer(o: Any?): PlayerInfo {
        val m = o as Map<*, *>
        return PlayerInfo((m["id"] as Number).toInt(), m["name"] as String, (m["color"] as Number).toInt())
    }

    private fun ints(o: Any?): IntArray = (o as List<*>).map { (it as Number).toInt() }.toIntArray()

    /** 게임 ↔ 허브 사이에 플레이어 목록을 넘길 때 쓰는 짧은 JSON */
    fun encodePlayers(list: List<PlayerInfo>): String = Json.write(list.map { player(it) })

    fun decodePlayers(text: String): List<PlayerInfo> = (Json.parse(text) as List<*>).map { readPlayer(it) }
}
