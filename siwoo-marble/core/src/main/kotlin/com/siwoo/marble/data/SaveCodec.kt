package com.siwoo.marble.data

import com.siwoo.marble.GameConfig
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.OfferKind
import com.siwoo.marble.model.Pending
import com.siwoo.marble.model.PendingKind
import com.siwoo.marble.model.Player

/** GameState ↔ JSON 문자열 (저장/이어하기) */
object SaveCodec {
    /** 2: 턴 종료 버튼·칸 터치 구매·판매/거래 규칙. 1(이전 규칙) 저장은 이어하기 불가 */
    private const val VERSION = 2

    fun encode(s: GameState): String {
        val pd = s.pending
        val root = linkedMapOf<String, Any?>(
            "version" to VERSION,
            "players" to s.players.map { p ->
                linkedMapOf<String, Any?>(
                    "id" to p.id, "name" to p.name, "color" to p.colorIndex,
                    "money" to p.money, "pos" to p.position, "island" to p.islandTurns,
                    "space" to p.spaceTravelPending, "bankrupt" to p.bankrupt, "laps" to p.laps,
                    "escape" to p.escapeCards, "tollFree" to p.tollFreeCards, "halfBuild" to p.halfBuildCards,
                )
            },
            "owner" to s.owner,
            "level" to s.level,
            "deck" to s.deck,
            "current" to s.current,
            "pending" to if (pd == null) null else linkedMapOf<String, Any?>(
                "kind" to pd.kind.name, "tile" to pd.tile, "amount" to pd.amount, "creditor" to pd.creditor,
            ),
            "hasRolled" to s.hasRolled,
            "extraRoll" to s.extraRoll,
            "doubles" to s.doublesCount,
            "offerTile" to s.offerTile,
            "offerKind" to s.offerKind?.name,
            "trades" to s.tradesThisTurn,
            "fund" to s.welfareFund,
            "round" to s.round,
            "turnLimit" to s.turnLimit,
            "gameOver" to s.gameOver,
            "winner" to s.winnerId,
            "die1" to s.lastDie1,
            "die2" to s.lastDie2,
        )
        return Json.write(root)
    }

    /** 저장 파일이 깨졌거나 버전이 다르면 IllegalArgumentException 등 예외를 던진다 */
    fun decode(text: String): GameState {
        val root = Json.parse(text) as Map<*, *>
        require(root.int("version") == VERSION) { "지원하지 않는 저장 버전" }
        val players = (root["players"] as List<*>).map { o ->
            val m = o as Map<*, *>
            Player(m.int("id"), m["name"] as String, m.int("color")).apply {
                money = m.int("money"); position = m.int("pos"); islandTurns = m.int("island")
                spaceTravelPending = m["space"] as Boolean; bankrupt = m["bankrupt"] as Boolean
                laps = m.int("laps"); escapeCards = m.int("escape"); tollFreeCards = m.int("tollFree")
                halfBuildCards = m.int("halfBuild")
            }
        }
        require(players.size in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        players.forEachIndexed { i, p -> require(p.id == i) }
        val owner = root.intArray("owner")
        val level = root.intArray("level")
        require(owner.size == GameConfig.BOARD_SIZE && level.size == GameConfig.BOARD_SIZE)
        val deck = root.intArray("deck").toMutableList()
        return GameState(players, owner, level, deck).apply {
            current = root.int("current")
            pending = (root["pending"] as Map<*, *>?)?.let { m ->
                Pending(PendingKind.valueOf(m["kind"] as String), m.int("tile"), m.int("amount"), m.int("creditor"))
            }
            hasRolled = root["hasRolled"] as Boolean
            extraRoll = root["extraRoll"] as Boolean
            doublesCount = root.int("doubles")
            offerTile = root.int("offerTile")
            offerKind = (root["offerKind"] as String?)?.let { OfferKind.valueOf(it) }
            tradesThisTurn = root.int("trades")
            welfareFund = root.int("fund")
            round = root.int("round")
            turnLimit = root.int("turnLimit")
            gameOver = root["gameOver"] as Boolean
            winnerId = root.int("winner")
            lastDie1 = root.int("die1")
            lastDie2 = root.int("die2")
            require(current in players.indices)
        }
    }

    private fun Map<*, *>.int(key: String): Int = (this[key] as Number).toInt()

    private fun Map<*, *>.intArray(key: String): IntArray =
        (this[key] as List<*>).map { (it as Number).toInt() }.toIntArray()
}
