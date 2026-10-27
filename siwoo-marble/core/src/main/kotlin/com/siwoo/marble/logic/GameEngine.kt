package com.siwoo.marble.logic

import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.BoardData
import com.siwoo.marble.data.CardData
import com.siwoo.marble.logic.GameEvent.Tone
import com.siwoo.marble.model.Card
import com.siwoo.marble.model.CardEffect
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.OfferKind
import com.siwoo.marble.model.Pending
import com.siwoo.marble.model.PendingKind
import com.siwoo.marble.model.Player
import com.siwoo.marble.model.PlayerSetup
import com.siwoo.marble.model.Tile
import com.siwoo.marble.model.TileType
import com.siwoo.marble.model.TradeOffer
import kotlin.random.Random

/**
 * 규칙 처리 전담. UI 와 완전히 분리되어 있어 JVM 에서 단위 테스트할 수 있다.
 *
 * 한 턴의 흐름:
 *  1. 시작: canRoll() 이면 roll() / 우주여행 대기면 spaceTravel(칸) / 무인도면 useEscapeCard() 가능
 *  2. 도착 결과로 offerTile 이 생기면 칸 터치 → buy() 또는 build() (안 하면 턴 종료 시 포기)
 *  3. 통행료 면제권이 있으면 pending(USE_TOLL_FREE) → answer(사용 여부)
 *  4. 언제든(자기 차례) sell(땅들), trade(제안, 수락 여부)
 *  5. canEndTurn() 이면 endTurn(). 더블이면 다시 굴려야 하고, 현금이 마이너스면 끝낼 수 없다.
 * 각 함수는 연출용 GameEvent 목록을 돌려준다.
 */
class GameEngine(
    val state: GameState,
    private val dice: Dice = Dice(),
) {
    private val tiles: Array<Tile> = BoardData.tiles
    private var events = ArrayList<GameEvent>()

    val currentPlayer: Player get() = state.currentPlayer

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /** 지금 다른 행동을 막는 것이 없는지 (게임 진행 중 + 대답 대기 없음) */
    private fun idle(): Boolean = !state.gameOver && state.pending == null

    fun canRoll(): Boolean {
        val p = currentPlayer
        if (!idle() || p.money < 0) return false
        if (!state.hasRolled && p.spaceTravelPending) return false // 우주여행이 굴림을 대신함
        return !state.hasRolled || state.extraRoll
    }

    fun canSpaceTravel(): Boolean {
        val p = currentPlayer
        return idle() && p.money >= 0 && p.spaceTravelPending && !state.hasRolled
    }

    fun canUseEscapeCard(): Boolean {
        val p = currentPlayer
        return idle() && !state.hasRolled && p.islandTurns > 0 && p.escapeCards > 0
    }

    /** 턴 종료 가능: 굴림을 마쳤고(더블 추가 굴림 없음), 현금이 마이너스가 아님 */
    fun canEndTurn(): Boolean =
        idle() && state.hasRolled && !state.extraRoll && currentPlayer.money >= 0

    fun canSell(): Boolean = idle() && landCount(currentPlayer.id) > 0

    fun canTrade(): Boolean =
        idle() && state.tradesThisTurn < GameConfig.MAX_TRADES_PER_TURN &&
            state.players.any { !it.bankrupt && it.id != state.current }

    fun tradesLeft(): Int = GameConfig.MAX_TRADES_PER_TURN - state.tradesThisTurn

    fun isInDebt(playerId: Int): Boolean = state.players[playerId].money < 0

    fun ownedTiles(playerId: Int): List<Tile> = tiles.filter { state.owner[it.index] == playerId }

    fun landCount(playerId: Int): Int {
        var n = 0
        for (i in 0 until GameConfig.BOARD_SIZE) if (state.owner[i] == playerId) n++
        return n
    }

    /** 자산 가치 = 땅값 + 지어진 건물 건설비 합계 */
    fun assetValue(tileIndex: Int): Int = tiles[tileIndex].investedValue(state.level[tileIndex])

    /** 은행 판매가 = 자산 가치 × SELL_RATE */
    fun saleValue(tileIndex: Int): Int = (assetValue(tileIndex) * GameConfig.SELL_RATE).toInt()

    /** 총자산 = 현금 + 보유 땅의 자산 가치 */
    fun totalAssets(playerId: Int): Int {
        var sum = state.players[playerId].money
        for (i in 0 until GameConfig.BOARD_SIZE) if (state.owner[i] == playerId) sum += assetValue(i)
        return sum
    }

    /** 순위: 파산하지 않은 사람 → 총자산 → 현금 순 */
    fun ranking(): List<Player> = state.players.sortedWith(
        compareBy<Player> { it.bankrupt }
            .thenByDescending { totalAssets(it.id) }
            .thenByDescending { it.money }
    )

    /** 지금 발광 중인 칸의 구매/건설 비용 (반값 건설권 반영). 제안이 없으면 -1 */
    fun offerCost(): Int {
        val i = state.offerTile
        val kind = state.offerKind ?: return -1
        if (i < 0) return -1
        val t = tiles[i]
        return when (kind) {
            OfferKind.BUY -> t.price
            OfferKind.BUILD -> {
                val full = t.buildCost(state.level[i] + 1)
                if (currentPlayer.halfBuildCards > 0) full / 2 else full
            }
        }
    }

    fun offerUsesHalfBuild(): Boolean =
        state.offerKind == OfferKind.BUILD && currentPlayer.halfBuildCards > 0

    fun canAffordOffer(): Boolean {
        val cost = offerCost()
        return cost >= 0 && idle() && currentPlayer.money >= cost
    }

    /** 거래 제안이 규칙에 맞는지. 맞으면 null, 아니면 이유 */
    fun validateTrade(o: TradeOffer): String? {
        if (o.fromId != state.current) return "자기 차례에만 거래를 제안할 수 있습니다"
        if (o.toId == o.fromId || o.toId !in state.players.indices) return "거래 상대가 올바르지 않습니다"
        val from = state.players[o.fromId]
        val to = state.players[o.toId]
        if (to.bankrupt || from.bankrupt) return "파산한 플레이어와는 거래할 수 없습니다"
        if (o.isEmpty) return "주고받을 것을 고르세요"
        if (o.giveTiles.distinct().size != o.giveTiles.size || o.takeTiles.distinct().size != o.takeTiles.size) {
            return "같은 땅이 두 번 들어 있습니다"
        }
        for (i in o.giveTiles) if (i !in 0 until GameConfig.BOARD_SIZE || state.owner[i] != o.fromId) return "내 땅이 아닌 칸이 있습니다"
        for (i in o.takeTiles) if (i !in 0 until GameConfig.BOARD_SIZE || state.owner[i] != o.toId) return "상대 땅이 아닌 칸이 있습니다"
        if (o.giveMoney < 0 || o.giveMoney > maxOf(0, from.money)) return "줄 돈이 보유액보다 많습니다"
        if (o.takeMoney < 0 || o.takeMoney > maxOf(0, to.money)) return "받을 돈이 상대 보유액보다 많습니다"
        return null
    }

    /** 거래에서 한쪽이 내놓는 총가치 (땅 자산 가치 합 + 돈) */
    fun tradeValue(tilesGiven: IntArray, money: Int): Int {
        var sum = money
        for (i in tilesGiven) sum += assetValue(i)
        return sum
    }

    // ─────────────────────────────────────────────
    // 행동
    // ─────────────────────────────────────────────

    /** 주사위 2개를 굴려 이동하고 도착 칸을 처리한다. */
    fun roll(): List<GameEvent> = act {
        check(canRoll()) { "지금은 주사위를 굴릴 수 없습니다" }
        val p = currentPlayer
        state.clearOffer() // 다시 굴리면 이전 칸의 구매 기회는 포기
        val d1 = dice.rollOne()
        val d2 = dice.rollOne()
        state.lastDie1 = d1
        state.lastDie2 = d2
        state.hasRolled = true
        state.extraRoll = false
        emit(GameEvent.DiceRolled(p.id, d1, d2))
        val isDouble = d1 == d2
        val sum = d1 + d2

        // 무인도: 더블이면 즉시 탈출해 그 눈만큼 이동 (추가 굴림 없음)
        if (p.islandTurns > 0) {
            if (isDouble) {
                p.islandTurns = 0
                banner("무인도 탈출!", "더블이 나왔습니다", Tone.GOOD)
                moveForward(p, sum)
                resolveLanding(p)
            } else {
                p.islandTurns--
                if (p.islandTurns > 0) banner("탈출 실패", "무인도 ${p.islandTurns}턴 남음", Tone.BAD)
                else banner("무인도 생활 끝", "다음 턴부터 움직일 수 있습니다")
            }
            return@act
        }

        if (isDouble) {
            state.doublesCount++
            if (state.doublesCount >= GameConfig.MAX_DOUBLES) {
                banner("${GameConfig.MAX_DOUBLES}연속 더블!", "과속으로 무인도행", Tone.BAD)
                sendToIsland(p)
                return@act
            }
            state.extraRoll = true
        }
        moveForward(p, sum)
        resolveLanding(p)
        if (state.pending == null) afterLanding(p)
    }

    /** 무인도 탈출권 사용. 사용 후 같은 턴에 바로 주사위를 굴린다. */
    fun useEscapeCard(): List<GameEvent> = act {
        check(canUseEscapeCard())
        val p = currentPlayer
        p.escapeCards--
        p.islandTurns = 0
        returnCard(CardEffect.KEEP_ESCAPE)
        banner("무인도 탈출권 사용!", "주사위를 굴리세요", Tone.GOOD)
    }

    /** 우주여행: 원하는 칸으로 바로 이동 (출발 칸을 지나면 월급). 굴림을 대신한다. */
    fun spaceTravel(target: Int): List<GameEvent> = act {
        check(canSpaceTravel())
        require(target in 0 until GameConfig.BOARD_SIZE && target != GameConfig.TILE_SPACE) { "이동할 수 없는 칸" }
        val p = currentPlayer
        p.spaceTravelPending = false
        state.hasRolled = true
        state.extraRoll = false
        msg("${p.name}님이 ${tiles[target].name}(으)로 우주여행을 떠납니다!")
        jumpForward(p, target)
        resolveLanding(p)
    }

    /** 발광 중인 빈 땅을 산다 */
    fun buy(): List<GameEvent> = act {
        check(idle() && state.offerKind == OfferKind.BUY) { "살 수 있는 땅이 없습니다" }
        val p = currentPlayer
        val t = tiles[state.offerTile]
        check(state.owner[t.index] < 0) { "이미 주인이 있는 땅입니다" }
        check(p.money >= t.price) { "잔액이 부족합니다" }
        state.clearOffer()
        p.money -= t.price
        state.owner[t.index] = p.id
        state.level[t.index] = 0
        emit(GameEvent.Transfer(p.id, GameState.BANK, t.price, t.index))
        emit(GameEvent.Bought(p.id, t.index))
        msg("${p.name}님이 ${t.name}을(를) ${Money.won(t.price)}에 샀습니다.", Tone.GOOD)
    }

    /** 발광 중인 내 땅에 다음 단계 건물을 짓는다 (반값 건설권 자동 적용) */
    fun build(): List<GameEvent> = act {
        check(idle() && state.offerKind == OfferKind.BUILD) { "지을 수 있는 땅이 없습니다" }
        val p = currentPlayer
        val i = state.offerTile
        val t = tiles[i]
        check(state.owner[i] == p.id && t.canBuild && state.level[i] < Tile.MAX_LEVEL)
        val cost = offerCost()
        check(p.money >= cost) { "잔액이 부족합니다" }
        val half = p.halfBuildCards > 0
        state.clearOffer()
        p.money -= cost
        state.level[i]++
        if (half) {
            p.halfBuildCards--
            returnCard(CardEffect.KEEP_HALF_BUILD)
        }
        emit(GameEvent.Transfer(p.id, GameState.BANK, cost, i))
        emit(GameEvent.Built(p.id, i, state.level[i]))
        msg("${t.name}에 ${Tile.LEVEL_NAMES[state.level[i]]}을(를) 지었습니다! (${Money.won(cost)})", Tone.GOOD)
    }

    /** 통행료 면제권 사용 여부 */
    fun answer(useCard: Boolean): List<GameEvent> = act {
        val pd = state.pending ?: error("대기 중인 선택이 없습니다")
        check(pd.kind == PendingKind.USE_TOLL_FREE)
        val p = currentPlayer
        state.pending = null
        if (useCard && p.tollFreeCards > 0) {
            p.tollFreeCards--
            returnCard(CardEffect.KEEP_TOLL_FREE)
            banner("통행료 면제!", Money.won(pd.amount) + " 절약", Tone.GOOD)
        } else {
            payToll(p, pd.tile, pd.amount, pd.creditor)
        }
        afterLanding(p)
    }

    /**
     * 내 땅(건물 포함)을 은행에 판다. 판매가 = 자산 가치 × SELL_RATE.
     * 팔고도 마이너스인데 남은 땅이 없으면 파산.
     */
    fun sell(tileIndexes: IntArray): List<GameEvent> = act {
        check(idle()) { "지금은 판매할 수 없습니다" }
        val p = currentPlayer
        require(tileIndexes.isNotEmpty()) { "판매할 땅을 고르세요" }
        require(tileIndexes.distinct().size == tileIndexes.size)
        for (i in tileIndexes) require(i in 0 until GameConfig.BOARD_SIZE && state.owner[i] == p.id) { "내 땅이 아닙니다" }
        var sum = 0
        for (i in tileIndexes) {
            sum += saleValue(i)
            state.owner[i] = -1
            state.level[i] = 0
            if (state.offerTile == i) state.clearOffer()
        }
        p.money += sum
        emit(GameEvent.Sold(p.id, tileIndexes.copyOf(), sum))
        msg("${p.name}님이 땅 ${tileIndexes.size}곳을 ${Money.won(sum)}에 팔았습니다.")
        checkSolvency(p)
    }

    /**
     * 거래 제안 처리 (핫시트: 상대가 기기를 받아 수락/거절한 결과를 함께 넘긴다).
     * 수락·거절 모두 한 턴 제안 횟수(MAX_TRADES_PER_TURN)에 포함된다.
     */
    fun trade(o: TradeOffer, accepted: Boolean): List<GameEvent> = act {
        check(canTrade()) { "더 이상 거래를 제안할 수 없습니다" }
        validateTrade(o)?.let { throw IllegalArgumentException(it) }
        state.tradesThisTurn++
        val from = state.players[o.fromId]
        val to = state.players[o.toId]
        if (!accepted) {
            banner("거래 거절", "${to.name}님이 거절했습니다", Tone.BAD)
            msg("${to.name}님이 거래를 거절했습니다.")
            return@act
        }
        for (i in o.giveTiles) state.owner[i] = to.id
        for (i in o.takeTiles) state.owner[i] = from.id
        if (state.offerTile >= 0 && (state.offerTile in o.giveTiles || state.offerTile in o.takeTiles)) state.clearOffer()
        from.money += o.takeMoney - o.giveMoney
        to.money += o.giveMoney - o.takeMoney
        emit(GameEvent.Traded(from.id, to.id, o.giveTiles.copyOf(), o.takeTiles.copyOf(), o.giveMoney, o.takeMoney))
        banner("거래 성립!", "${from.name} ⇄ ${to.name}", Tone.GOOD)
        msg("${from.name}님과 ${to.name}님의 거래가 성립했습니다.", Tone.GOOD)
        checkSolvency(from)
    }

    /** 턴 종료. 사지 않은 땅(발광)은 포기한다. */
    fun endTurn(): List<GameEvent> = act {
        check(canEndTurn()) { if (currentPlayer.money < 0) "잔액이 부족해 턴을 끝낼 수 없습니다" else "아직 턴을 끝낼 수 없습니다" }
        advanceTurn()
    }

    // ─────────────────────────────────────────────
    // 내부 처리
    // ─────────────────────────────────────────────

    private inline fun act(block: () -> Unit): List<GameEvent> {
        events = ArrayList()
        block()
        // 현재 플레이어가 파산했으면 바로 다음 사람 차례로
        if (!state.gameOver && currentPlayer.bankrupt) advanceTurn()
        return events
    }

    private fun emit(e: GameEvent) { events.add(e) }

    private fun msg(text: String, tone: Tone = Tone.INFO) = emit(GameEvent.Message(text, tone))

    private fun banner(text: String, sub: String = "", tone: Tone = Tone.INFO) = emit(GameEvent.Banner(text, sub, tone))

    private fun paySalary(p: Player) {
        p.money += GameConfig.SALARY
        p.laps++
        emit(GameEvent.Transfer(GameState.BANK, p.id, GameConfig.SALARY, GameConfig.TILE_START))
        msg("출발 칸 통과! 월급 ${Money.won(GameConfig.SALARY)}을 받습니다.", Tone.GOOD)
    }

    /** 한 칸씩 앞으로 걸어간다. 출발 칸을 지나거나 도착하면 월급 */
    private fun moveForward(p: Player, steps: Int) {
        val from = p.position
        emit(GameEvent.Moved(p.id, from, steps))
        p.position = (from + steps) % GameConfig.BOARD_SIZE
        if (from + steps >= GameConfig.BOARD_SIZE) paySalary(p)
    }

    /** 목표 칸까지 앞으로 순간이동. 출발 칸을 지나거나 도착하면 월급 */
    private fun jumpForward(p: Player, target: Int) {
        val from = p.position
        emit(GameEvent.Jumped(p.id, from, target))
        p.position = target
        if (target <= from) paySalary(p)
    }

    private fun moveBack(p: Player, steps: Int) {
        val from = p.position
        emit(GameEvent.Moved(p.id, from, -steps))
        p.position = ((from - steps) % GameConfig.BOARD_SIZE + GameConfig.BOARD_SIZE) % GameConfig.BOARD_SIZE
    }

    private fun sendToIsland(p: Player) {
        emit(GameEvent.Jumped(p.id, p.position, GameConfig.TILE_ISLAND))
        p.position = GameConfig.TILE_ISLAND
        p.islandTurns = GameConfig.ISLAND_TURNS
        state.extraRoll = false
    }

    private fun resolveLanding(p: Player) {
        val t = tiles[p.position]
        when (t.type) {
            TileType.START -> Unit
            TileType.CITY, TileType.SPECIAL -> landOnProperty(p, t)
            TileType.GOLDEN_KEY -> drawCard(p)
            TileType.ISLAND -> {
                p.islandTurns = GameConfig.ISLAND_TURNS
                state.extraRoll = false
                banner("무인도 표류!", "${GameConfig.ISLAND_TURNS}턴 동안 갇힙니다 (더블이면 탈출)", Tone.BAD)
            }
            TileType.WELFARE_RECEIVE -> {
                val fund = state.welfareFund
                if (fund > 0) {
                    p.money += fund
                    state.welfareFund = 0
                    banner("사회복지기금 수령!", Money.won(fund), Tone.GOOD)
                    emit(GameEvent.Transfer(GameState.FUND, p.id, fund, -1))
                } else {
                    banner("사회복지기금", "기금이 비어 있습니다")
                }
            }
            TileType.WELFARE_PAY -> {
                banner("사회복지기금 기부", Money.won(GameConfig.WELFARE_DONATION))
                charge(p, GameConfig.WELFARE_DONATION, GameState.FUND, "사회복지기금 기부", -1)
            }
            TileType.SPACE -> {
                p.spaceTravelPending = true
                state.extraRoll = false
                banner("우주여행!", "다음 턴에 원하는 칸으로 날아갑니다", Tone.GOOD)
            }
        }
    }

    private fun landOnProperty(p: Player, t: Tile) {
        val ownerId = state.owner[t.index]
        when {
            ownerId < 0 -> {
                // 자동 팝업 없이 칸을 발광시키고, 터치하면 구매 (돈이 모자라도 발광 → 팝업에서 잔액 부족)
                state.offerTile = t.index
                state.offerKind = OfferKind.BUY
                emit(GameEvent.OfferStarted(t.index, build = false))
            }
            ownerId == p.id -> {
                val lv = state.level[t.index]
                if (!t.canBuild) {
                    msg("${t.name}은(는) 건물을 지을 수 없는 관광지입니다.")
                } else if (lv >= Tile.MAX_LEVEL) {
                    msg("${t.name}에는 이미 호텔이 있습니다.")
                } else {
                    state.offerTile = t.index
                    state.offerKind = OfferKind.BUILD
                    emit(GameEvent.OfferStarted(t.index, build = true))
                }
            }
            else -> {
                val toll = t.toll(state.level[t.index])
                emit(GameEvent.TileFlash(t.index, ownerId))
                if (p.tollFreeCards > 0) {
                    state.pending = Pending(PendingKind.USE_TOLL_FREE, t.index, toll, ownerId)
                } else {
                    payToll(p, t.index, toll, ownerId)
                }
            }
        }
    }

    private fun payToll(p: Player, tile: Int, toll: Int, ownerId: Int) {
        banner("통행료 수거!", Money.won(toll), Tone.BAD)
        charge(p, toll, ownerId, "${state.players[ownerId].name}님에게 통행료", tile)
    }

    /** 대답(면제권) 뒤에 이어지는 처리: 더블 안내 */
    private fun afterLanding(p: Player) {
        if (state.extraRoll && !p.bankrupt && p.money >= 0 && p.islandTurns == 0 && !p.spaceTravelPending) {
            banner("더블!", "한 번 더", Tone.GOOD)
        }
    }

    private fun drawCard(p: Player) {
        if (state.deck.isEmpty()) {
            msg("황금열쇠가 모두 떨어졌습니다.")
            return
        }
        val card = CardData.byId(state.deck.removeAt(0))
        emit(GameEvent.CardDrawn(p.id, card))
        // 보관형은 사용할 때 덱으로 돌아가고, 나머지는 바로 덱 맨 아래로
        if (!card.isKeepable) state.deck.add(card.id)
        applyCard(p, card)
    }

    private fun applyCard(p: Player, c: Card) {
        when (c.effect) {
            CardEffect.MOVE_TO -> {
                jumpForward(p, c.value)
                resolveLanding(p)
            }
            CardEffect.MOVE_BACK -> {
                moveBack(p, c.value)
                resolveLanding(p)
            }
            CardEffect.RECEIVE -> {
                p.money += c.value
                emit(GameEvent.Transfer(GameState.BANK, p.id, c.value, -1))
                msg("${c.title}: ${Money.won(c.value)}을 받았습니다.", Tone.GOOD)
            }
            CardEffect.PAY -> charge(p, c.value, GameState.BANK, c.title, -1)
            CardEffect.BIRTHDAY -> {
                for (q in state.players) {
                    if (q.id == p.id || q.bankrupt) continue
                    // 남의 차례에 빚을 지게 하지 않도록 가진 현금 한도 안에서만 받는다
                    val amt = minOf(c.value, maxOf(0, q.money))
                    if (amt <= 0) continue
                    q.money -= amt
                    p.money += amt
                    emit(GameEvent.Transfer(q.id, p.id, amt))
                }
                msg("생일 축하금을 받았습니다!", Tone.GOOD)
            }
            CardEffect.KEEP_ESCAPE -> { p.escapeCards++; msg("무인도 탈출권을 보관했습니다.", Tone.GOOD) }
            CardEffect.KEEP_TOLL_FREE -> { p.tollFreeCards++; msg("통행료 면제권을 보관했습니다.", Tone.GOOD) }
            CardEffect.KEEP_HALF_BUILD -> { p.halfBuildCards++; msg("반값 건설권을 보관했습니다.", Tone.GOOD) }
            CardEffect.REPAIR -> {
                var cost = 0
                for (t in tiles) {
                    if (state.owner[t.index] != p.id) continue
                    val lv = state.level[t.index]
                    if (lv >= 1) cost += c.value
                    if (lv >= 2) cost += c.value2
                    if (lv >= 3) cost += c.value3
                }
                if (cost == 0) msg("수리할 건물이 없습니다. 휴~")
                else charge(p, cost, GameState.BANK, c.title, -1)
            }
            CardEffect.DONATE -> charge(p, c.value, GameState.FUND, c.title, -1)
        }
    }

    private fun returnCard(effect: CardEffect) {
        state.deck.add(CardData.idOf(effect))
    }

    /**
     * 돈을 낸다. 현금이 모자라도 전액을 내고 마이너스가 된다(받는 쪽은 전액을 받음).
     * 마이너스면 판매·거래로 메워야 하고, 팔 땅이 하나도 없으면 바로 파산.
     */
    private fun charge(p: Player, amount: Int, creditor: Int, reason: String, tile: Int) {
        if (amount <= 0) return
        p.money -= amount
        when {
            creditor >= 0 -> state.players[creditor].money += amount
            creditor == GameState.FUND -> state.welfareFund += amount
            else -> Unit // 은행
        }
        emit(GameEvent.Transfer(p.id, creditor, amount, tile))
        msg("$reason ${Money.won(amount)}을 냈습니다.", Tone.BAD)
        checkSolvency(p)
    }

    /** 현금이 마이너스면: 땅이 있으면 판매 안내, 없으면 파산 */
    private fun checkSolvency(p: Player) {
        if (p.money >= 0 || p.bankrupt) return
        if (landCount(p.id) == 0) {
            bankrupt(p)
        } else {
            emit(GameEvent.Debt(p.id, p.money))
            msg("잔액이 부족합니다 (${Money.won(p.money)}). 땅을 판매하거나 거래하세요.", Tone.BAD)
        }
    }

    /** 파산: 남은 땅은 모두 은행으로, 보관 카드는 덱으로 */
    private fun bankrupt(p: Player) {
        for (i in 0 until GameConfig.BOARD_SIZE) {
            if (state.owner[i] == p.id) {
                state.owner[i] = -1
                state.level[i] = 0
            }
        }
        p.money = 0
        repeat(p.escapeCards) { returnCard(CardEffect.KEEP_ESCAPE) }
        repeat(p.tollFreeCards) { returnCard(CardEffect.KEEP_TOLL_FREE) }
        repeat(p.halfBuildCards) { returnCard(CardEffect.KEEP_HALF_BUILD) }
        p.escapeCards = 0; p.tollFreeCards = 0; p.halfBuildCards = 0
        p.islandTurns = 0
        p.spaceTravelPending = false
        p.bankrupt = true
        if (p.id == state.current) {
            state.extraRoll = false
            state.pending = null
            state.clearOffer()
        }
        emit(GameEvent.Bankrupt(p.id))
        msg("${p.name}님이 파산했습니다.", Tone.BAD)
    }

    private fun advanceTurn() {
        state.hasRolled = false
        state.extraRoll = false
        state.doublesCount = 0
        state.tradesThisTurn = 0
        state.pending = null
        state.clearOffer()
        val alive = state.players.filter { !it.bankrupt }
        if (alive.size <= 1) {
            finish(alive.firstOrNull()?.id ?: -1, byTurnLimit = false)
            return
        }
        val n = state.players.size
        var next = state.current
        do { next = (next + 1) % n } while (state.players[next].bankrupt)
        if (next <= state.current) {
            state.round++
            if (state.turnLimit > 0 && state.round > state.turnLimit) {
                finish(ranking().first().id, byTurnLimit = true)
                return
            }
        }
        state.current = next
        emit(GameEvent.TurnChanged(next))
    }

    private fun finish(winnerId: Int, byTurnLimit: Boolean) {
        state.gameOver = true
        state.winnerId = winnerId
        state.pending = null
        state.clearOffer()
        emit(GameEvent.GameOver(winnerId, byTurnLimit))
    }

    companion object {
        /** 새 게임 상태 생성: 순서 랜덤, 같은 초기 자금, 황금열쇠 덱 섞기 */
        fun newGame(setups: List<PlayerSetup>, turnLimit: Int, random: Random = Random.Default): GameState {
            require(setups.size in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "플레이어는 2~4명" }
            val order = setups.shuffled(random)
            val players = order.mapIndexed { i, s ->
                Player(i, s.name, s.colorIndex).apply { money = GameConfig.START_MONEY }
            }
            val deck = CardData.cards.map { it.id }.shuffled(random).toMutableList()
            return GameState(players, deck = deck).apply { this.turnLimit = turnLimit }
        }
    }
}
