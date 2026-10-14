package com.siwoo.marble.logic

import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.BoardData
import com.siwoo.marble.data.CardData
import com.siwoo.marble.logic.GameEvent.Tone
import com.siwoo.marble.model.Card
import com.siwoo.marble.model.CardEffect
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.Pending
import com.siwoo.marble.model.PendingKind
import com.siwoo.marble.model.Player
import com.siwoo.marble.model.PlayerSetup
import com.siwoo.marble.model.Tile
import com.siwoo.marble.model.TileType
import kotlin.random.Random

/**
 * 규칙 처리 전담. UI 와 완전히 분리되어 있어 JVM 에서 단위 테스트할 수 있다.
 *
 * 사용 흐름:
 *  1. pending == null 이고 canRoll() 이면 roll()
 *  2. currentPlayer.spaceTravelPending 이면 spaceTravel(칸)
 *  3. pending 이 BUY / BUILD / USE_TOLL_FREE 이면 answer(true/false)
 *  4. pending 이 RAISE_FUNDS 이면 sellBuilding / sellLand / autoSell
 * 각 함수는 연출용 GameEvent 목록을 돌려준다. 턴 넘김은 엔진이 자동으로 처리한다.
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

    fun canRoll(): Boolean =
        !state.gameOver && state.pending == null && !currentPlayer.spaceTravelPending

    fun canUseEscapeCard(): Boolean {
        val p = currentPlayer
        return !state.gameOver && state.pending == null && p.islandTurns > 0 && p.escapeCards > 0
    }

    fun ownedTiles(playerId: Int): List<Tile> = tiles.filter { state.owner[it.index] == playerId }

    /** 땅/건물 1단계를 은행에 팔 때 받는 돈 (구매가의 절반) */
    fun saleValueOfTopBuilding(tileIndex: Int): Int {
        val lv = state.level[tileIndex]
        return if (lv == 0) 0 else half(tiles[tileIndex].buildCost(lv))
    }

    fun saleValueOfLand(tileIndex: Int): Int = half(tiles[tileIndex].price)

    /** 가진 땅·건물을 모두 은행에 팔면 받을 수 있는 돈 */
    fun liquidationValue(playerId: Int): Int {
        var sum = 0
        for (t in tiles) {
            if (state.owner[t.index] != playerId) continue
            sum += half(t.price)
            for (k in 1..state.level[t.index]) sum += half(t.buildCost(k))
        }
        return sum
    }

    /** 총자산 = 현금 + 땅값 + 건설비 (턴 제한 모드 순위 기준) */
    fun totalAssets(playerId: Int): Int {
        var sum = state.players[playerId].money
        for (t in tiles) if (state.owner[t.index] == playerId) sum += t.investedValue(state.level[t.index])
        return sum
    }

    /** 순위: 파산하지 않은 사람 → 총자산 → 현금 순 */
    fun ranking(): List<Player> = state.players.sortedWith(
        compareBy<Player> { it.bankrupt }
            .thenByDescending { totalAssets(it.id) }
            .thenByDescending { it.money }
    )

    // ─────────────────────────────────────────────
    // 행동
    // ─────────────────────────────────────────────

    /** 주사위 2개를 굴려 이동하고 도착 칸을 처리한다. */
    fun roll(): List<GameEvent> = act {
        check(canRoll()) { "지금은 주사위를 굴릴 수 없습니다" }
        val p = currentPlayer
        val d1 = dice.rollOne()
        val d2 = dice.rollOne()
        state.lastDie1 = d1
        state.lastDie2 = d2
        emit(GameEvent.DiceRolled(p.id, d1, d2))
        val isDouble = d1 == d2
        val sum = d1 + d2
        state.extraRoll = false

        // 무인도: 더블이면 즉시 탈출해 그 눈만큼 이동 (추가 굴림 없음)
        if (p.islandTurns > 0) {
            if (isDouble) {
                p.islandTurns = 0
                msg("더블! ${p.name}님이 무인도를 탈출합니다.", Tone.GOOD)
                moveForward(p, sum)
                resolveLanding(p)
            } else {
                p.islandTurns--
                if (p.islandTurns > 0) msg("무인도 탈출 실패… ${p.islandTurns}턴 남았습니다.", Tone.BAD)
                else msg("무인도 생활 끝! 다음 턴부터 움직일 수 있습니다.")
            }
            afterAction()
            return@act
        }

        if (isDouble) {
            state.doublesCount++
            if (state.doublesCount >= GameConfig.MAX_DOUBLES) {
                msg("${GameConfig.MAX_DOUBLES}연속 더블! 과속으로 무인도에 갑니다.", Tone.BAD)
                sendToIsland(p)
                afterAction()
                return@act
            }
            state.extraRoll = true
        }
        moveForward(p, sum)
        resolveLanding(p)
        afterAction()
    }

    /** 무인도 탈출권 사용. 사용 후 같은 턴에 바로 주사위를 굴린다. */
    fun useEscapeCard(): List<GameEvent> = act {
        check(canUseEscapeCard())
        val p = currentPlayer
        p.escapeCards--
        p.islandTurns = 0
        returnCard(CardEffect.KEEP_ESCAPE)
        msg("${p.name}님이 무인도 탈출권을 사용했습니다. 주사위를 굴리세요!", Tone.GOOD)
    }

    /** 우주여행: 원하는 칸으로 바로 이동 (출발 칸을 지나면 월급) */
    fun spaceTravel(target: Int): List<GameEvent> = act {
        val p = currentPlayer
        check(!state.gameOver && state.pending == null && p.spaceTravelPending)
        require(target in 0 until GameConfig.BOARD_SIZE && target != GameConfig.TILE_SPACE) { "이동할 수 없는 칸" }
        p.spaceTravelPending = false
        msg("${p.name}님이 ${tiles[target].name}(으)로 우주여행을 떠납니다!")
        jumpForward(p, target)
        resolveLanding(p)
        afterAction()
    }

    /** 구매 / 건설 / 통행료 면제권 사용 여부에 대한 답 */
    fun answer(accept: Boolean): List<GameEvent> = act {
        val pd = state.pending ?: error("대기 중인 선택이 없습니다")
        val p = currentPlayer
        when (pd.kind) {
            PendingKind.BUY -> {
                state.pending = null
                val t = tiles[pd.tile]
                if (accept && p.money >= t.price) {
                    p.money -= t.price
                    state.owner[t.index] = p.id
                    state.level[t.index] = 0
                    msg("${p.name}님이 ${t.name}을(를) ${Money.won(t.price)}에 샀습니다.", Tone.GOOD)
                }
            }
            PendingKind.BUILD -> {
                state.pending = null
                val t = tiles[pd.tile]
                if (accept && p.money >= pd.amount && state.level[t.index] < Tile.MAX_LEVEL) {
                    p.money -= pd.amount
                    state.level[t.index]++
                    if (pd.halfBuild) {
                        p.halfBuildCards--
                        returnCard(CardEffect.KEEP_HALF_BUILD)
                    }
                    val name = Tile.LEVEL_NAMES[state.level[t.index]]
                    msg("${t.name}에 ${name}을(를) 지었습니다! (${Money.won(pd.amount)})", Tone.GOOD)
                }
            }
            PendingKind.USE_TOLL_FREE -> {
                state.pending = null
                if (accept && p.tollFreeCards > 0) {
                    p.tollFreeCards--
                    returnCard(CardEffect.KEEP_TOLL_FREE)
                    msg("통행료 면제권 사용! ${Money.won(pd.amount)}을(를) 아꼈습니다.", Tone.GOOD)
                } else {
                    charge(p, pd.amount, pd.creditor, "통행료")
                }
            }
            PendingKind.RAISE_FUNDS -> error("매각은 sellBuilding/sellLand/autoSell 로 처리합니다")
        }
        afterAction()
    }

    /** (돈이 부족할 때) 해당 칸의 가장 높은 건물 1단계를 은행에 판다 */
    fun sellBuilding(tileIndex: Int): List<GameEvent> = act {
        val pd = requireRaiseFunds()
        val p = currentPlayer
        check(state.owner[tileIndex] == p.id && state.level[tileIndex] > 0) { "팔 건물이 없습니다" }
        doSellBuilding(p, tileIndex)
        settleRaiseFunds(pd)
    }

    /** (돈이 부족할 때) 건물이 없는 땅을 은행에 판다 */
    fun sellLand(tileIndex: Int): List<GameEvent> = act {
        val pd = requireRaiseFunds()
        val p = currentPlayer
        check(state.owner[tileIndex] == p.id && state.level[tileIndex] == 0) { "건물부터 팔아야 합니다" }
        doSellLand(p, tileIndex)
        settleRaiseFunds(pd)
    }

    /** 정해진 순서로 필요한 만큼만 자동 매각 */
    fun autoSell(): List<GameEvent> = act {
        val pd = requireRaiseFunds()
        val p = currentPlayer
        while (p.money < pd.amount && sellNextInOrder(p)) { /* 반복 */ }
        settleRaiseFunds(pd)
    }

    /**
     * 매각 순서 (자동 매각·파산 정리에 공통 사용):
     *  1) 건물 먼저 — 매각가가 가장 싼 건물부터 1단계씩 (같으면 칸 번호가 작은 것)
     *  2) 건물이 모두 없어지면 땅 — 땅값이 가장 싼 것부터 (같으면 칸 번호가 작은 것)
     * 비싼 땅을 최대한 오래 지키기 위한 순서이다.
     * @return 팔 것이 있어서 팔았으면 true
     */
    private fun sellNextInOrder(p: Player): Boolean {
        var best = -1
        var bestValue = Int.MAX_VALUE
        for (t in tiles) {
            if (state.owner[t.index] != p.id || state.level[t.index] == 0) continue
            val v = saleValueOfTopBuilding(t.index)
            if (v < bestValue) { best = t.index; bestValue = v }
        }
        if (best >= 0) { doSellBuilding(p, best); return true }
        for (t in tiles) {
            if (state.owner[t.index] != p.id) continue
            val v = saleValueOfLand(t.index)
            if (v < bestValue) { best = t.index; bestValue = v }
        }
        if (best >= 0) { doSellLand(p, best); return true }
        return false
    }

    // ─────────────────────────────────────────────
    // 내부 처리
    // ─────────────────────────────────────────────

    private inline fun act(block: () -> Unit): List<GameEvent> {
        events = ArrayList()
        block()
        return events
    }

    private fun emit(e: GameEvent) { events.add(e) }

    private fun msg(text: String, tone: Tone = Tone.INFO) = emit(GameEvent.Message(text, tone))

    private fun half(v: Int) = v * GameConfig.SELL_BACK_PERCENT / 100

    private fun paySalary(p: Player) {
        p.money += GameConfig.SALARY
        p.laps++
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
                msg("무인도에 표류했습니다! ${GameConfig.ISLAND_TURNS}턴 동안 갇힙니다. (더블이면 탈출)", Tone.BAD)
            }
            TileType.WELFARE_RECEIVE -> {
                val fund = state.welfareFund
                if (fund > 0) {
                    p.money += fund
                    state.welfareFund = 0
                    msg("사회복지기금 ${Money.won(fund)}을 모두 받았습니다!", Tone.GOOD)
                } else {
                    msg("사회복지기금이 비어 있습니다.")
                }
            }
            TileType.WELFARE_PAY -> charge(p, GameConfig.WELFARE_DONATION, GameState.FUND, "사회복지기금 기부")
            TileType.SPACE -> {
                p.spaceTravelPending = true
                state.extraRoll = false
                msg("우주여행! 다음 턴에 원하는 칸을 골라 이동합니다.", Tone.GOOD)
            }
        }
    }

    private fun landOnProperty(p: Player, t: Tile) {
        val ownerId = state.owner[t.index]
        when {
            ownerId < 0 -> {
                if (p.money >= t.price) {
                    state.pending = Pending(PendingKind.BUY, t.index, t.price)
                } else {
                    msg("돈이 부족해 ${t.name}을(를) 살 수 없습니다.")
                }
            }
            ownerId == p.id -> {
                val lv = state.level[t.index]
                if (!t.canBuild) {
                    msg("${t.name}은(는) 건물을 지을 수 없는 관광지입니다.")
                } else if (lv >= Tile.MAX_LEVEL) {
                    msg("${t.name}에는 이미 호텔이 있습니다.")
                } else {
                    val full = t.buildCost(lv + 1)
                    val half = p.halfBuildCards > 0
                    val cost = if (half) full / 2 else full
                    if (p.money >= cost) {
                        state.pending = Pending(PendingKind.BUILD, t.index, cost, halfBuild = half)
                    } else {
                        msg("돈이 부족해 ${Tile.LEVEL_NAMES[lv + 1]}을(를) 지을 수 없습니다.")
                    }
                }
            }
            else -> {
                val toll = t.toll(state.level[t.index])
                val owner = state.players[ownerId]
                msg("${owner.name}님의 ${t.name}! 통행료 ${Money.won(toll)}", Tone.BAD)
                if (p.tollFreeCards > 0) {
                    state.pending = Pending(PendingKind.USE_TOLL_FREE, t.index, toll, ownerId)
                } else {
                    charge(p, toll, ownerId, "통행료")
                }
            }
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
                msg("${Money.won(c.value)}을 받았습니다.", Tone.GOOD)
            }
            CardEffect.PAY -> charge(p, c.value, GameState.BANK, c.title)
            CardEffect.BIRTHDAY -> {
                var total = 0
                for (q in state.players) {
                    if (q.id == p.id || q.bankrupt) continue
                    // 다른 사람에게 강제 매각을 시키지 않도록 가진 현금 한도 안에서만 받는다
                    val amt = minOf(c.value, q.money)
                    q.money -= amt
                    total += amt
                }
                p.money += total
                msg("축하금 ${Money.won(total)}을 받았습니다!", Tone.GOOD)
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
                else charge(p, cost, GameState.BANK, c.title)
            }
            CardEffect.DONATE -> charge(p, c.value, GameState.FUND, c.title)
        }
    }

    private fun returnCard(effect: CardEffect) {
        state.deck.add(CardData.idOf(effect))
    }

    /**
     * 돈을 낸다. 현금이 모자라면 매각 대기(RAISE_FUNDS),
     * 다 팔아도 모자라면 파산.
     */
    private fun charge(p: Player, amount: Int, creditor: Int, reason: String) {
        if (amount <= 0) return
        when {
            p.money >= amount -> {
                transfer(p, amount, creditor)
                msg("$reason ${Money.won(amount)}을 냈습니다.", Tone.BAD)
            }
            p.money + liquidationValue(p.id) >= amount -> {
                state.pending = Pending(PendingKind.RAISE_FUNDS, -1, amount, creditor, reason = reason)
                msg("현금이 부족합니다! 땅이나 건물을 팔아 ${Money.won(amount)}을 마련하세요.", Tone.BAD)
            }
            else -> bankrupt(p, creditor, reason)
        }
    }

    private fun transfer(p: Player, amount: Int, creditor: Int) {
        p.money -= amount
        when {
            creditor >= 0 -> state.players[creditor].money += amount
            creditor == GameState.FUND -> state.welfareFund += amount
            else -> Unit // 은행
        }
    }

    private fun requireRaiseFunds(): Pending {
        val pd = state.pending
        check(pd != null && pd.kind == PendingKind.RAISE_FUNDS) { "매각할 상황이 아닙니다" }
        return pd
    }

    private fun settleRaiseFunds(pd: Pending) {
        val p = currentPlayer
        if (p.money < pd.amount) return
        state.pending = null
        transfer(p, pd.amount, pd.creditor)
        msg("${pd.reason} ${Money.won(pd.amount)}을 냈습니다.", Tone.BAD)
        afterAction()
    }

    private fun doSellBuilding(p: Player, i: Int) {
        val v = saleValueOfTopBuilding(i)
        val name = Tile.LEVEL_NAMES[state.level[i]]
        state.level[i]--
        p.money += v
        msg("${tiles[i].name}의 ${name}을(를) ${Money.won(v)}에 팔았습니다.")
    }

    private fun doSellLand(p: Player, i: Int) {
        val v = saleValueOfLand(i)
        state.owner[i] = -1
        state.level[i] = 0
        p.money += v
        msg("${tiles[i].name} 땅을 ${Money.won(v)}에 팔았습니다.")
    }

    /** 파산: 매각 순서대로 전부 팔아 있는 돈을 모두 채권자에게 주고, 땅은 은행으로 */
    private fun bankrupt(p: Player, creditor: Int, reason: String) {
        msg("$reason 낼 돈이 없습니다…", Tone.BAD)
        while (sellNextInOrder(p)) { /* 전부 매각 → 땅은 은행 귀속 */ }
        val cash = p.money
        if (cash > 0) transfer(p, cash, creditor)
        p.money = 0
        // 보관 중이던 카드는 덱으로
        repeat(p.escapeCards) { returnCard(CardEffect.KEEP_ESCAPE) }
        repeat(p.tollFreeCards) { returnCard(CardEffect.KEEP_TOLL_FREE) }
        repeat(p.halfBuildCards) { returnCard(CardEffect.KEEP_HALF_BUILD) }
        p.escapeCards = 0; p.tollFreeCards = 0; p.halfBuildCards = 0
        p.islandTurns = 0
        p.spaceTravelPending = false
        p.bankrupt = true
        state.extraRoll = false
        state.pending = null
        emit(GameEvent.Bankrupt(p.id))
        msg("${p.name}님이 파산했습니다. 소유한 땅은 모두 은행으로 돌아갑니다.", Tone.BAD)
    }

    /** 행동이 끝난 뒤: 선택 대기면 멈추고, 더블이면 한 번 더, 아니면 턴 넘김 */
    private fun afterAction() {
        if (state.pending != null || state.gameOver) return
        val p = currentPlayer
        if (!p.bankrupt && state.extraRoll && p.islandTurns == 0 && !p.spaceTravelPending) {
            msg("더블! 한 번 더 굴리세요.", Tone.GOOD)
            return
        }
        endTurn()
    }

    private fun endTurn() {
        state.extraRoll = false
        state.doublesCount = 0
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
