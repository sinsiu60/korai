package com.siwoo.marble.logic

import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.CardData
import com.siwoo.marble.data.PenaltyData
import com.siwoo.marble.data.SaveCodec
import com.siwoo.marble.model.CardEffect
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.OfferKind
import com.siwoo.marble.model.Pending
import com.siwoo.marble.model.PenaltyEffect
import com.siwoo.marble.model.PenaltyGrade
import com.siwoo.marble.model.PendingKind
import com.siwoo.marble.model.Player
import com.siwoo.marble.model.PlayerSetup
import com.siwoo.marble.model.TradeOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 정해진 눈을 순서대로 내는 주사위 */
class ScriptedDice(vararg faces: Int) : Dice() {
    private val queue = ArrayDeque(faces.toList())
    override fun rollOne(): Int = queue.removeFirstOrNull() ?: error("준비한 주사위 눈이 모자랍니다")
}

class GameEngineTest {

    private val START = GameConfig.START_MONEY
    private val SALARY = GameConfig.SALARY

    /** 플레이어 A(0), B(1) … 순서가 고정된 게임 */
    private fun game(vararg faces: Int, players: Int = 2, random: Random = Random(7)): Pair<GameEngine, GameState> {
        val list = (0 until players).map { i ->
            Player(i, "P${'A' + i}", i).apply { money = START }
        }
        val state = GameState(list, deck = CardData.cards.map { it.id }.toMutableList())
        return GameEngine(state, ScriptedDice(*faces), random) to state
    }

    private fun penaltyId(effect: PenaltyEffect) = PenaltyData.penalties.first { it.effect == effect }.id

    private fun GameState.p(i: Int) = players[i]

    /** 황금열쇠 덱을 특정 카드 한 장으로 고정 (예측 가능한 테스트용) */
    private fun GameState.deckOf(effect: CardEffect) {
        deck.clear()
        deck.add(CardData.idOf(effect))
    }

    private fun cardId(title: String) = CardData.cards.first { it.title == title }.id

    /** 다른 플레이어를 무인도에 오래 가둬 턴만 넘기게 함 */
    private fun GameState.park(i: Int) {
        p(i).position = GameConfig.TILE_ISLAND
        p(i).islandTurns = 99
    }

    // ─────────────────────────────────────────────
    // 턴 흐름 / 턴 종료
    // ─────────────────────────────────────────────

    @Test
    fun `굴린 뒤에는 자동으로 넘어가지 않고 턴 종료를 눌러야 한다`() {
        val (e, s) = game(1, 2)
        assertFalse("굴리기 전에는 턴 종료 불가", e.canEndTurn())
        e.roll()
        assertEquals(0, s.current)
        assertFalse("한 번만 굴린다", e.canRoll())
        assertTrue(e.canEndTurn())
        val ev = e.endTurn()
        assertEquals(1, s.current)
        assertTrue(ev.any { it is GameEvent.TurnChanged && it.playerId == 1 })
        assertFalse(s.hasRolled)
    }

    @Test
    fun `더블이면 다시 굴리기 전까지 턴 종료 불가`() {
        val (e, s) = game(1, 1, 1, 2)
        s.deck.clear(); s.deck.add(cardId("장학금"))
        val ev = e.roll() // → 2 황금열쇠
        assertTrue(s.extraRoll)
        assertFalse(e.canEndTurn())
        assertTrue(e.canRoll())
        assertTrue(ev.any { it is GameEvent.Banner && it.text == "더블!" })
        e.roll()
        assertTrue(e.canEndTurn())
    }

    // ─────────────────────────────────────────────
    // 3연속 더블 / 무인도
    // ─────────────────────────────────────────────

    @Test
    fun `3연속 더블이면 이동하지 않고 즉시 무인도로 간다`() {
        val (e, s) = game(1, 1, 2, 2, 3, 3)
        s.deck.clear(); s.deck.add(cardId("장학금"))
        val a = s.p(0)
        e.roll()                       // (1,1) → 2 황금열쇠
        e.roll()                       // (2,2) → 6 싱가포르 (구매 발광)
        assertEquals(6, a.position)
        assertEquals(2, s.doublesCount)
        val ev = e.roll()              // (3,3) 세 번째 더블
        assertEquals(GameConfig.TILE_ISLAND, a.position)
        assertEquals(GameConfig.ISLAND_TURNS, a.islandTurns)
        assertEquals(START + 100_000, a.money)
        assertEquals(-1, s.offerTile)  // 싱가포르 구매 기회는 사라짐
        assertTrue(ev.any { it is GameEvent.Jumped && it.to == GameConfig.TILE_ISLAND })
        assertFalse(e.canRoll())
        assertTrue(e.canEndTurn())
    }

    @Test
    fun `무인도에서 더블이면 탈출해 이동하고 추가 굴림은 없다`() {
        val (e, s) = game(3, 3)
        val a = s.p(0)
        a.position = GameConfig.TILE_ISLAND; a.islandTurns = 3
        e.roll()
        assertEquals(0, a.islandTurns)
        assertEquals(16, a.position)
        assertFalse(s.extraRoll)
        assertTrue(e.canEndTurn())
    }

    @Test
    fun `무인도에서 3턴을 보내면 4번째 턴에 움직인다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)
        val a = s.p(0)
        a.position = GameConfig.TILE_ISLAND; a.islandTurns = GameConfig.ISLAND_TURNS
        s.park(1)
        for (left in GameConfig.ISLAND_TURNS - 1 downTo 0) {
            e.roll(); assertEquals(left, a.islandTurns); assertEquals(GameConfig.TILE_ISLAND, a.position)
            e.endTurn()
            e.roll(); e.endTurn()      // B
        }
        e.roll()
        assertEquals(13, a.position)
    }

    @Test
    fun `무인도 탈출권은 굴리기 전에만 쓰고 카드는 덱 맨 아래로`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        val id = CardData.idOf(CardEffect.KEEP_ESCAPE)
        s.deck.remove(id)
        a.position = GameConfig.TILE_ISLAND; a.islandTurns = 3; a.escapeCards = 1
        assertTrue(e.canUseEscapeCard())
        e.useEscapeCard()
        assertEquals(0, a.islandTurns)
        assertEquals(id, s.deck.last())
        assertTrue(e.canRoll())
        e.roll()
        assertEquals(13, a.position)
        assertFalse(e.canUseEscapeCard())
    }

    // ─────────────────────────────────────────────
    // 출발 월급 / 우주여행 / 사회복지기금
    // ─────────────────────────────────────────────

    @Test
    fun `출발 칸을 지나가면 월급을 받고 코인 이벤트가 나온다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        a.position = 38
        val ev = e.roll()
        assertEquals(1, a.position)
        assertEquals(START + SALARY, a.money)
        assertTrue(ev.any { it is GameEvent.Transfer && it.from == GameState.BANK && it.to == 0 && it.amount == SALARY })
    }

    @Test
    fun `출발 칸에 정확히 도착해도 월급, 뒤로 가는 카드는 월급 없음`() {
        val (e, s) = game(1, 2)
        s.p(0).position = 37
        e.roll()
        assertEquals(START + SALARY, s.p(0).money)

        val (e2, s2) = game(1, 1)
        s2.deckOf(CardEffect.MOVE_BACK)
        e2.roll()                      // 0 → 2 → 뒤로 3칸 → 39
        assertEquals(39, s2.p(0).position)
        assertEquals(START, s2.p(0).money)
    }

    @Test
    fun `우주여행은 다음 턴에 굴림 대신 원하는 칸으로 이동한다`() {
        val (e, s) = game(1, 2, 1, 2)
        val a = s.p(0)
        a.position = 27
        s.park(1)
        e.roll()                       // → 30
        assertTrue(a.spaceTravelPending)
        e.endTurn()
        e.roll(); e.endTurn()          // B
        assertFalse(e.canRoll())
        assertTrue(e.canSpaceTravel())
        assertThrows(IllegalArgumentException::class.java) { e.spaceTravel(GameConfig.TILE_SPACE) }
        val ev = e.spaceTravel(5)
        assertEquals(5, a.position)
        assertEquals(START + SALARY, a.money)
        assertTrue(ev.any { it is GameEvent.Jumped && it.from == 30 && it.to == 5 })
        assertEquals(OfferKind.BUY, s.offerKind)
        assertTrue(e.canEndTurn())
        assertFalse(e.canRoll())
    }

    @Test
    fun `사회복지기금은 기부로 쌓이고 도착한 사람이 전액 받는다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2, 1, 2)
        val a = s.p(0); val b = s.p(1)
        a.position = 35
        b.position = 32
        s.deck.clear(); s.deck.add(cardId("사회복지기금 기부"))
        e.roll(); e.endTurn()          // A → 38 접수처
        assertEquals(GameConfig.WELFARE_DONATION, s.welfareFund)
        e.roll(); e.endTurn()          // B → 35 기부 카드
        assertEquals(GameConfig.WELFARE_DONATION + 100_000, s.welfareFund)
        a.position = 17
        val ev = e.roll(); e.endTurn() // A → 20
        assertEquals(0, s.welfareFund)
        assertEquals(START + 100_000, a.money)
        assertTrue(ev.any { it is GameEvent.Transfer && it.from == GameState.FUND && it.to == 0 })
        b.position = 17
        e.roll()
        assertEquals(START - 100_000, b.money)
    }

    // ─────────────────────────────────────────────
    // 칸 터치 구매 / 건설
    // ─────────────────────────────────────────────

    @Test
    fun `빈 땅에 도착하면 자동 구매 없이 발광 제안만 생기고 터치해서 산다`() {
        val (e, s) = game(1, 2)
        val ev = e.roll()              // → 3 베이징
        assertNull(s.pending)
        assertEquals(3, s.offerTile)
        assertEquals(OfferKind.BUY, s.offerKind)
        assertTrue(ev.any { it is GameEvent.OfferStarted && it.tile == 3 && !it.build })
        assertEquals(80_000, e.offerCost())
        val buy = e.buy()
        assertEquals(0, s.owner[3])
        assertEquals(START - 80_000, s.p(0).money)
        assertEquals(-1, s.offerTile)
        // 순서: 코인 → 깃발
        val ti = buy.indexOfFirst { it is GameEvent.Transfer && it.to == GameState.BANK && it.tile == 3 }
        val bi = buy.indexOfFirst { it is GameEvent.Bought && it.tile == 3 }
        assertTrue(ti in 0 until bi)
    }

    @Test
    fun `사지 않고 턴 종료하면 구매 포기`() {
        val (e, s) = game(1, 2)
        e.roll()
        assertEquals(3, s.offerTile)
        e.endTurn()
        assertEquals(-1, s.offerTile)
        assertEquals(-1, s.owner[3])
        assertThrows(IllegalStateException::class.java) { e.buy() }
    }

    @Test
    fun `돈이 부족해도 발광은 하지만 구매는 막힌다`() {
        val (e, s) = game(1, 2)
        s.p(0).money = 50_000
        e.roll()                       // 베이징 8만
        assertEquals(3, s.offerTile)
        assertFalse(e.canAffordOffer())
        assertThrows(IllegalStateException::class.java) { e.buy() }
        assertEquals(-1, s.owner[3])
    }

    @Test
    fun `땅을 산 방문에는 짓지 못하고 다음 방문에 초록 발광으로 한 단계 짓는다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2)
        val a = s.p(0)
        s.park(1)
        e.roll(); e.buy()
        assertEquals(-1, s.offerTile)
        e.endTurn()
        e.roll(); e.endTurn()          // B
        a.position = 0
        val ev = e.roll()
        assertEquals(OfferKind.BUILD, s.offerKind)
        assertTrue(ev.any { it is GameEvent.OfferStarted && it.build })
        assertEquals(40_000, e.offerCost())
        val b = e.build()
        assertEquals(1, s.level[3])
        assertEquals(START - 80_000 - 40_000, a.money)
        assertTrue(b.any { it is GameEvent.Built && it.level == 1 })
    }

    @Test
    fun `반값 건설권은 건설비를 절반으로 하고 덱으로 돌아간다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        val id = CardData.idOf(CardEffect.KEEP_HALF_BUILD)
        s.deck.remove(id)
        a.halfBuildCards = 1
        s.owner[3] = 0; s.level[3] = 1
        e.roll()
        assertTrue(e.offerUsesHalfBuild())
        assertEquals(40_000, e.offerCost())  // 빌딩 8만의 절반
        e.build()
        assertEquals(2, s.level[3])
        assertEquals(0, a.halfBuildCards)
        assertEquals(id, s.deck.last())
    }

    @Test
    fun `관광지와 호텔이 있는 땅은 건설 제안이 없다`() {
        val (e, s) = game(2, 3)
        s.owner[5] = 0
        e.roll()
        assertEquals(-1, s.offerTile)
    }

    // ─────────────────────────────────────────────
    // 통행료 / 마이너스 / 면제권
    // ─────────────────────────────────────────────

    @Test
    fun `통행료는 번쩍임 → 문구 → 코인 순서로 나오고 단계별 금액을 낸다`() {
        val (e, s) = game(1, 2)
        s.owner[3] = 1; s.level[3] = 3     // 베이징 호텔 24만
        val ev = e.roll()
        assertEquals(START - 240_000, s.p(0).money)
        assertEquals(START + 240_000, s.p(1).money)
        val f = ev.indexOfFirst { it is GameEvent.TileFlash && it.tile == 3 && it.ownerId == 1 }
        val b = ev.indexOfFirst { it is GameEvent.Banner && it.text == "통행료 수거!" }
        val t = ev.indexOfFirst { it is GameEvent.Transfer && it.from == 0 && it.to == 1 && it.amount == 240_000 }
        assertTrue("$f < $b < $t", f in 0 until b && b < t)
    }

    @Test
    fun `통행료가 모자라면 마이너스가 되고 판매 안내가 마지막에 나온다`() {
        val (e, s) = game(1, 3)
        val a = s.p(0); val b = s.p(1)
        a.money = 10_000; a.position = 33
        s.owner[1] = 0                     // 팔 땅 하나
        s.owner[37] = 1; s.level[37] = 1   // 뉴욕 별장 17.5만
        val ev = e.roll()
        assertEquals(10_000 - 175_000, a.money)
        assertEquals(START + 175_000, b.money)  // 받는 쪽은 전액
        assertTrue(e.isInDebt(0))
        val t = ev.indexOfFirst { it is GameEvent.Transfer && it.from == 0 && it.to == 1 }
        val d = ev.indexOfFirst { it is GameEvent.Debt && it.playerId == 0 }
        assertTrue(t in 0 until d)
        assertFalse(a.bankrupt)
    }

    @Test
    fun `마이너스 상태에서는 턴 종료도 굴리기도 할 수 없다`() {
        val (e, s) = game(1, 1)
        val a = s.p(0)
        s.owner[1] = 0
        s.owner[39] = 0                    // 땅이 남아 있어야 파산이 아니라 빚 상태
        s.deckOf(CardEffect.PAY)           // 병원비 5만
        a.money = 20_000
        e.roll()                           // (1,1) 더블 → 황금열쇠 병원비 → 마이너스
        assertEquals(-30_000, a.money)
        assertTrue(s.extraRoll)
        assertFalse(e.canEndTurn())
        assertFalse(e.canRoll())
        assertThrows(IllegalStateException::class.java) { e.endTurn() }
        assertThrows(IllegalStateException::class.java) { e.roll() }
        // 판매로 메우면 다시 굴릴 수 있다
        e.sell(intArrayOf(1))              // 타이베이 땅 5만 → 2.5만
        assertEquals(-5_000, a.money)
        assertTrue(e.isInDebt(0))
        assertFalse(e.canRoll())
        a.money += 10_000                  // (예: 거래로 받은 돈)
        assertTrue(e.canRoll())
    }

    @Test
    fun `통행료 면제권이 있으면 지불 전에 묻는다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        a.tollFreeCards = 1
        s.deck.remove(CardData.idOf(CardEffect.KEEP_TOLL_FREE))
        s.owner[3] = 1
        e.roll()
        assertEquals(PendingKind.USE_TOLL_FREE, s.pending!!.kind)
        assertFalse(e.canEndTurn())
        e.answer(true)
        assertEquals(START, a.money)
        assertEquals(CardData.idOf(CardEffect.KEEP_TOLL_FREE), s.deck.last())
        assertTrue(e.canEndTurn())

        val (e2, s2) = game(1, 2)
        s2.p(0).tollFreeCards = 1
        s2.owner[3] = 1
        e2.roll()
        e2.answer(false)
        assertEquals(START - 8_000, s2.p(0).money)
        assertEquals(1, s2.p(0).tollFreeCards)
    }

    // ─────────────────────────────────────────────
    // 판매
    // ─────────────────────────────────────────────

    @Test
    fun `판매가는 땅값과 지은 건물 건설비 합계의 SELL_RATE 배`() {
        val (e, s) = game()
        s.owner[1] = 0; s.level[1] = 2     // 타이베이: 5만 + 별장 2만 + 빌딩 5만 = 12만
        s.owner[39] = 0                     // 서울: 100만 (건물 없음)
        s.owner[34] = 0; s.level[34] = 3    // 로마: 32만 + 16 + 32 + 48 = 128만
        assertEquals(120_000, e.assetValue(1))
        assertEquals(60_000, e.saleValue(1))
        assertEquals(1_000_000, e.assetValue(39))
        assertEquals(500_000, e.saleValue(39))
        assertEquals(1_280_000, e.assetValue(34))
        assertEquals((1_280_000 * GameConfig.SELL_RATE).toInt(), e.saleValue(34))
        assertTrue("건물이 있으면 가치가 높다", e.assetValue(34) > 320_000)
    }

    @Test
    fun `여러 땅을 한 번에 팔면 합계를 받고 칸은 은행으로 돌아간다`() {
        val (e, s) = game()
        val a = s.p(0)
        s.owner[1] = 0; s.level[1] = 2
        s.owner[39] = 0
        val ev = e.sell(intArrayOf(1, 39))
        assertEquals(START + 60_000 + 500_000, a.money)
        assertEquals(-1, s.owner[1]); assertEquals(0, s.level[1])
        assertEquals(-1, s.owner[39])
        val sold = ev.first { it is GameEvent.Sold } as GameEvent.Sold
        assertEquals(560_000, sold.amount)
        assertEquals(listOf(1, 39), sold.tiles.toList())
    }

    @Test
    fun `남의 땅이나 중복된 칸은 팔 수 없다`() {
        val (e, s) = game()
        s.owner[1] = 1
        s.owner[3] = 0
        assertThrows(IllegalArgumentException::class.java) { e.sell(intArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { e.sell(intArrayOf(3, 3)) }
        assertThrows(IllegalArgumentException::class.java) { e.sell(intArrayOf()) }
        assertEquals(1, s.owner[1])
        assertEquals(0, s.owner[3])
    }

    @Test
    fun `건설 제안 중인 땅을 팔면 제안이 사라진다`() {
        val (e, s) = game(1, 2)
        s.owner[3] = 0
        e.roll()
        assertEquals(OfferKind.BUILD, s.offerKind)
        e.sell(intArrayOf(3))
        assertEquals(-1, s.offerTile)
    }

    // ─────────────────────────────────────────────
    // 파산
    // ─────────────────────────────────────────────

    @Test
    fun `땅을 모두 팔아도 마이너스면 파산하고 다음 사람 차례`() {
        val (e, s) = game(1, 2, players = 3)
        val a = s.p(0); val b = s.p(1)
        a.money = 5_000; a.position = 36
        a.halfBuildCards = 1
        val halfId = CardData.idOf(CardEffect.KEEP_HALF_BUILD)
        s.deck.remove(halfId)
        s.owner[1] = 0; s.level[1] = 1     // 판매가 (5+2)/2 = 3.5만
        s.owner[39] = 1                     // B 의 서울 (통행료 200만)
        e.roll()                            // → 39
        assertEquals(5_000 - 2_000_000, a.money)
        assertFalse("땅이 남아 있으면 아직 파산 아님", a.bankrupt)
        assertEquals(START + 2_000_000, b.money)
        val ev = e.sell(intArrayOf(1))
        assertTrue(a.bankrupt)
        assertEquals(0, a.money)
        assertEquals(-1, s.owner[1])
        assertEquals(0, a.halfBuildCards)
        assertEquals(halfId, s.deck.last())
        assertTrue(ev.any { it is GameEvent.Bankrupt && it.playerId == 0 })
        assertEquals(1, s.current)
        assertTrue(ev.any { it is GameEvent.TurnChanged && it.playerId == 1 })
        assertFalse(s.gameOver)
    }

    @Test
    fun `팔 땅이 하나도 없는데 마이너스가 되면 즉시 파산`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        a.money = 0; a.position = 36
        s.owner[39] = 1
        val ev = e.roll()
        assertTrue(a.bankrupt)
        assertTrue(s.gameOver)
        assertEquals(1, s.winnerId)
        assertTrue(ev.any { it is GameEvent.GameOver && it.winnerId == 1 && !it.byTurnLimit })
    }

    @Test
    fun `파산한 플레이어는 차례에서 빠진다`() {
        val (e, s) = game(1, 2, 1, 2, players = 3)
        s.p(1).bankrupt = true
        s.p(0).position = 20; s.p(2).position = 20
        e.roll(); e.endTurn()
        assertEquals(2, s.current)
        e.roll(); e.endTurn()
        assertEquals(0, s.current)
        assertEquals(2, s.round)
    }

    // ─────────────────────────────────────────────
    // 거래
    // ─────────────────────────────────────────────

    @Test
    fun `거래 수락 시 여러 땅과 돈이 서로 바뀌고 건물도 함께 넘어간다`() {
        val (e, s) = game(players = 3)
        val a = s.p(0); val c = s.p(2)
        s.owner[1] = 0; s.level[1] = 2      // A: 타이베이(빌딩)
        s.owner[3] = 0                       // A: 베이징
        s.owner[39] = 2                      // C: 서울
        s.owner[34] = 2; s.level[34] = 1     // C: 로마(별장)
        val o = TradeOffer(0, 2, intArrayOf(1, 3), 300_000, intArrayOf(39, 34), 50_000)
        assertNull(e.validateTrade(o))
        assertEquals(e.assetValue(1) + e.assetValue(3) + 300_000, e.tradeValue(o.giveTiles, o.giveMoney))
        val ev = e.trade(o, accepted = true)
        assertEquals(2, s.owner[1]); assertEquals(2, s.level[1])
        assertEquals(2, s.owner[3])
        assertEquals(0, s.owner[39])
        assertEquals(0, s.owner[34]); assertEquals(1, s.level[34])
        assertEquals(START - 300_000 + 50_000, a.money)
        assertEquals(START + 300_000 - 50_000, c.money)
        val t = ev.first { it is GameEvent.Traded } as GameEvent.Traded
        assertEquals(300_000, t.giveMoney); assertEquals(50_000, t.takeMoney)
        assertTrue(ev.any { it is GameEvent.Banner && it.text == "거래 성립!" })
        assertEquals(1, s.tradesThisTurn)
    }

    @Test
    fun `거래 거절이면 아무것도 바뀌지 않지만 제안 횟수는 센다`() {
        val (e, s) = game()
        s.owner[3] = 0
        val o = TradeOffer(0, 1, intArrayOf(3), 0, intArrayOf(), 100_000)
        val ev = e.trade(o, accepted = false)
        assertEquals(0, s.owner[3])
        assertEquals(START, s.p(0).money)
        assertEquals(1, s.tradesThisTurn)
        assertTrue(ev.any { it is GameEvent.Banner && it.text == "거래 거절" })
    }

    @Test
    fun `한 턴에 거래 제안은 최대 3번`() {
        val (e, s) = game(1, 2)
        val o = TradeOffer(0, 1, intArrayOf(), 10_000, intArrayOf(), 0)
        repeat(GameConfig.MAX_TRADES_PER_TURN) { e.trade(o, accepted = false) }
        assertFalse(e.canTrade())
        assertThrows(IllegalStateException::class.java) { e.trade(o, accepted = true) }
        e.roll(); e.endTurn()
        assertTrue("다음 턴에는 다시 가능", e.canTrade())
    }

    @Test
    fun `잘못된 거래 제안은 거부된다`() {
        val (e, s) = game(players = 3)
        s.owner[3] = 1
        s.owner[5] = 0
        s.p(2).bankrupt = true
        assertNotNull(e.validateTrade(TradeOffer(0, 1, intArrayOf(), 0, intArrayOf(), 0)))              // 빈 제안
        assertNotNull(e.validateTrade(TradeOffer(0, 1, intArrayOf(3), 0, intArrayOf(), 0)))             // 내 땅 아님
        assertNotNull(e.validateTrade(TradeOffer(0, 1, intArrayOf(), 0, intArrayOf(5), 0)))             // 상대 땅 아님
        assertNotNull(e.validateTrade(TradeOffer(0, 1, intArrayOf(), START + 1, intArrayOf(), 0)))      // 보유액 초과
        assertNotNull(e.validateTrade(TradeOffer(0, 1, intArrayOf(), 0, intArrayOf(), START + 1)))      // 상대 보유액 초과
        assertNotNull(e.validateTrade(TradeOffer(0, 2, intArrayOf(5), 0, intArrayOf(), 0)))             // 파산자
        assertNotNull(e.validateTrade(TradeOffer(1, 0, intArrayOf(3), 0, intArrayOf(), 0)))             // 내 차례 아님
        assertThrows(IllegalArgumentException::class.java) {
            e.trade(TradeOffer(0, 1, intArrayOf(3), 0, intArrayOf(), 0), accepted = true)
        }
        assertEquals(0, s.tradesThisTurn)
    }

    @Test
    fun `마이너스일 때 거래로 돈을 받으면 턴을 끝낼 수 있다 (돈은 줄 수 없음)`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        s.owner[39] = 0
        e.roll()
        a.money = -100_000
        assertNotNull(e.validateTrade(TradeOffer(0, 1, intArrayOf(), 1, intArrayOf(), 0)))
        e.trade(TradeOffer(0, 1, intArrayOf(39), 0, intArrayOf(), 300_000), accepted = true)
        assertEquals(200_000, a.money)
        assertEquals(1, s.owner[39])
        assertTrue(e.canEndTurn())
    }

    // ─────────────────────────────────────────────
    // 카드 / 기타
    // ─────────────────────────────────────────────

    @Test
    fun `사용한 황금열쇠는 덱 맨 아래로, 보관형은 플레이어가 가진다`() {
        val (e, s) = game(1, 1)
        val first = cardId("장학금")
        s.deck.remove(first); s.deck.add(0, first)
        e.roll()
        assertEquals(first, s.deck.last())

        val (e2, s2) = game(1, 1)
        val id = CardData.idOf(CardEffect.KEEP_ESCAPE)
        s2.deck.remove(id); s2.deck.add(0, id)
        val size = s2.deck.size
        e2.roll()
        assertEquals(1, s2.p(0).escapeCards)
        assertEquals(size - 1, s2.deck.size)
    }

    @Test
    fun `생일 카드는 다른 사람의 현금 한도 안에서만 받는다`() {
        val (e, s) = game(1, 1, players = 3)
        s.deckOf(CardEffect.BIRTHDAY)
        s.p(2).money = 20_000
        e.roll()
        assertEquals(START + 50_000 + 20_000, s.p(0).money)
        assertEquals(0, s.p(2).money)
    }

    @Test
    fun `건물 수리비는 건물 수에 비례한다`() {
        val (e, s) = game(1, 1)
        s.deckOf(CardEffect.REPAIR)
        s.owner[3] = 0; s.level[3] = 1
        s.owner[4] = 0; s.level[4] = 3
        e.roll()
        assertEquals(START - (30_000 + 30_000 + 60_000 + 100_000), s.p(0).money)
    }

    @Test
    fun `턴 제한 모드는 마지막 라운드가 끝나면 총자산 1위가 이긴다`() {
        val (e, s) = game(1, 2, 1, 2)
        s.turnLimit = 1
        s.p(1).position = 5
        e.roll(); e.buy(); e.endTurn()
        e.roll()
        val ev = e.endTurn()
        assertTrue(s.gameOver)
        assertEquals(1, s.winnerId) // 총자산 동점 → 현금 많은 B
        assertTrue(ev.any { it is GameEvent.GameOver && it.byTurnLimit })
    }

    @Test
    fun `새 게임은 순서를 섞고 모두 같은 돈을 받는다`() {
        val setups = listOf(PlayerSetup("가", 0), PlayerSetup("나", 1), PlayerSetup("다", 2), PlayerSetup("라", 3))
        val orders = HashSet<String>()
        for (seed in 0 until 30) {
            val s = GameEngine.newGame(setups, 20, Random(seed))
            s.players.forEachIndexed { i, p -> assertEquals(i, p.id); assertEquals(START, p.money) }
            assertEquals(CardData.cards.size, s.deck.size)
            orders.add(s.players.joinToString { it.name })
        }
        assertTrue(orders.size > 1)
    }

    @Test
    fun `저장 후 불러오면 제안·마이너스·거래 횟수까지 그대로다`() {
        val (e, s) = game(1, 2, players = 3)
        s.welfareFund = 123_000
        e.roll()                            // 베이징 구매 제안
        e.trade(TradeOffer(0, 1, intArrayOf(), 10_000, intArrayOf(), 0), accepted = false)
        s.p(0).money = -5_000
        val text = SaveCodec.encode(s)
        val r = SaveCodec.decode(text)
        assertEquals(text, SaveCodec.encode(r))
        assertEquals(3, r.offerTile)
        assertEquals(OfferKind.BUY, r.offerKind)
        assertEquals(1, r.tradesThisTurn)
        assertTrue(r.hasRolled)
        assertEquals(-5_000, r.players[0].money)
        assertFalse(GameEngine(r).canEndTurn())
    }

    @Test
    fun `이전 버전 저장은 불러오지 않는다`() {
        assertThrows(IllegalArgumentException::class.java) { SaveCodec.decode("{\"version\":1}") }
    }

    // ─────────────────────────────────────────────
    // 벌칙 룰렛 (PART 12)
    // ─────────────────────────────────────────────

    @Test
    fun `벌칙 칸은 7번과 22번이고 도착하면 룰렛 대기 상태가 된다`() {
        assertEquals(com.siwoo.marble.model.TileType.PENALTY, com.siwoo.marble.data.BoardData.tiles[7].type)
        assertEquals(com.siwoo.marble.model.TileType.PENALTY, com.siwoo.marble.data.BoardData.tiles[22].type)
        assertEquals(16, CardData.cards.size)  // 기존 15장 + 벌칙 면제권
        val (e, s) = game(3, 4)
        val ev = e.roll()                       // → 7
        assertEquals(PendingKind.PENALTY_SPIN, s.pending!!.kind)
        assertTrue(ev.any { it is GameEvent.PenaltyLanded && it.tile == 7 })
        assertTrue(ev.any { it is GameEvent.Banner && it.text == "벌칙 당첨!" })
        assertFalse(e.canEndTurn())
        assertFalse(e.canRoll())
        assertTrue(e.canSpinPenalty())
        val spin = e.spinPenalty()
        val sp = spin.first { it is GameEvent.PenaltySpun } as GameEvent.PenaltySpun
        val pen = PenaltyData.byId(sp.penaltyId)
        assertEquals(pen.grade.ordinal, sp.grade)
        assertEquals("룰렛 칸 등급 = 결과 등급", pen.grade, PenaltyData.ROULETTE[sp.slot])
        assertNull(s.pending)
    }

    @Test
    fun `등급 확률은 기본 60-30-10, 꼴찌는 대형 3%, 1위는 대형 15%`() {
        val (e, s) = game(players = 3)
        assertEquals(listOf(60, 30, 10), e.penaltyOdds(0).toList())      // 모두 동점 → 보정 없음
        s.p(0).money = START + 500_000                                     // A 단독 1위
        assertEquals(listOf(55, 30, 15), e.penaltyOdds(0).toList())
        s.p(1).money = START - 500_000                                     // B 단독 꼴찌
        assertEquals(listOf(67, 30, 3), e.penaltyOdds(1).toList())
        assertEquals(listOf(60, 30, 10), e.penaltyOdds(2).toList())        // C 중간
        s.p(2).money = START + 500_000                                     // A·C 공동 1위 → 보정 없음
        assertEquals(listOf(60, 30, 10), e.penaltyOdds(0).toList())
        for (k in 0 until 3) assertEquals(100, e.penaltyOdds(k).sum())
    }

    @Test
    fun `룰렛 결과는 보정된 확률대로 나온다 (2만 번 표본)`() {
        val rnd = Random(42)
        val count = IntArray(3)
        val n = 20_000
        repeat(n) {
            val (e, s) = game(players = 2, random = rnd)
            s.p(0).money = START + 1_000_000      // 1위 → 55/30/15
            s.pending = Pending(PendingKind.PENALTY_SPIN, 7)
            val sp = e.spinPenalty().first { it is GameEvent.PenaltySpun } as GameEvent.PenaltySpun
            count[sp.grade]++
        }
        val pct = count.map { it * 100.0 / n }
        assertEquals(55.0, pct[0], 1.5)
        assertEquals(30.0, pct[1], 1.5)
        assertEquals(15.0, pct[2], 1.2)
    }

    @Test
    fun `벌칙 손실은 총자산의 25%를 넘지 않는다`() {
        // 총자산 10만 → 상한 2.5만
        val (e, s) = game(players = 4)
        val a = s.p(0)
        a.money = 100_000
        assertEquals(25_000, e.penaltyCap(0))
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.FINE))               // 5만 → 2.5만
        assertEquals(75_000, a.money)
        a.money = 100_000
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.PAY_ALL))            // 3명 × 3만 = 9만 → 2.5만/3
        assertEquals(100_000 - 3 * (25_000 / 3), a.money)
        assertEquals(START + 25_000 / 3, s.p(1).money)
        a.money = 100_000
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.DONATE_RATE))        // 20% = 2만 (상한 이내)
        assertEquals(80_000, a.money)
        assertEquals(20_000, s.welfareFund)
    }

    @Test
    fun `강제 매각과 철거도 잃는 가치가 상한을 넘지 않는다`() {
        val (e, s) = game()
        val a = s.p(0)
        a.money = 0
        s.owner[39] = 0                         // 서울 100만 → 총자산 100만, 상한 25만
        val ev = e.applyPenaltyForTest(penaltyId(PenaltyEffect.FORCED_SALE))
        // 판매가 50만의 절반 = 25만이면 75만을 잃으므로, 잃는 가치 25만에 맞춰 75만을 받는다
        assertEquals(750_000, a.money)
        assertEquals(-1, s.owner[39])
        assertTrue(ev.any { it is GameEvent.Sold && it.amount == 750_000 })

        val (e2, s2) = game()
        val b = s2.p(0)
        b.money = 0
        s2.owner[34] = 0; s2.level[34] = 3      // 로마 호텔: 자산 가치 128만 → 상한 32만, 호텔 48만
        e2.applyPenaltyForTest(penaltyId(PenaltyEffect.DEMOLISH))
        assertEquals(2, s2.level[34])
        assertEquals(480_000 - 320_000, b.money) // 넘는 16만은 돌려받음
        assertEquals(1_280_000 - 320_000, e2.totalAssets(0))
    }

    @Test
    fun `강제 매각은 가장 비싼 땅을 판매가의 절반에 판다`() {
        val (e, s) = game()
        val a = s.p(0)
        s.owner[3] = 0                          // 베이징 8만
        s.owner[26] = 0; s.level[26] = 1        // 하와이 26 + 13 = 39만 (가장 비쌈)
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.FORCED_SALE))
        assertEquals(-1, s.owner[26])
        assertEquals(0, s.owner[3])
        assertEquals(START + 390_000 / 2 / 2, a.money)
    }

    @Test
    fun `건물이 없으면 철거 대신 벌금 10만, 땅이 없으면 강제 매각 대신 벌금 20만`() {
        val (e, s) = game()
        val a = s.p(0)
        s.owner[3] = 0                          // 땅은 있지만 건물 없음
        val ev = e.applyPenaltyForTest(penaltyId(PenaltyEffect.DEMOLISH))
        assertEquals(START - 100_000, a.money)
        assertTrue(ev.any { it is GameEvent.Banner && it.text == "대체 벌칙" })
        assertEquals(0, s.owner[3])

        val (e2, s2) = game()
        e2.applyPenaltyForTest(penaltyId(PenaltyEffect.FORCED_SALE))
        assertEquals(START - 200_000, s2.p(0).money)
    }

    @Test
    fun `주사위 1개 벌칙은 다음 굴림 한 번만 적용되고 사라진다`() {
        val (e, s) = game(3, 2, 2)
        val a = s.p(0)
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.ONE_DIE))
        assertTrue(a.oneDieNext)
        val ev = e.roll()                       // 주사위 1개: 3
        val d = ev.first { it is GameEvent.DiceRolled } as GameEvent.DiceRolled
        assertEquals(0, d.die2)
        assertEquals(3, a.position)
        assertFalse(a.oneDieNext)
        e.endTurn()
        e.roll()                                // B: 다시 2개
        assertEquals(4, s.p(1).position)
    }

    @Test
    fun `월급 절반 벌칙은 다음 월급에만 적용된다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2)
        val a = s.p(0)
        s.park(1)
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.HALF_SALARY))
        a.position = 38
        e.roll(); e.endTurn()                  // 출발 통과
        assertEquals(START + SALARY / 2, a.money)
        assertFalse(a.halfSalaryNext)
        e.roll(); e.endTurn()                  // B
        a.position = 38
        e.roll()
        assertEquals(START + SALARY / 2 + SALARY, a.money)
    }

    @Test
    fun `통행료 2배 벌칙은 다음 통행료에만 적용된다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2)
        val a = s.p(0)
        s.park(1)
        s.owner[3] = 1                          // 베이징 통행료 8천
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.DOUBLE_TOLL))
        assertTrue(a.doubleTollNext)
        e.roll(); e.endTurn()
        assertEquals(START - 16_000, a.money)
        assertFalse(a.doubleTollNext)
        e.roll(); e.endTurn()
        a.position = 0
        e.roll()
        assertEquals(START - 16_000 - 8_000, a.money)
    }

    @Test
    fun `1턴 쉬기는 다음 차례를 건너뛰고 사라진다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2, 1, 2)
        val a = s.p(0)
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.SKIP_TURN))
        assertEquals(1, a.skipTurns)
        e.roll(); e.endTurn()                  // A → B
        e.roll()
        val ev = e.endTurn()                   // B → (A 쉼) → B
        assertTrue(ev.any { it is GameEvent.TurnSkipped && it.playerId == 0 })
        assertEquals(1, s.current)
        assertEquals(0, a.skipTurns)
        assertEquals(2, s.round)               // A 가 건너뛴 차례도 2라운드에 속함
        e.roll()
        e.endTurn()
        assertEquals(0, s.current)             // 다음에는 다시 A 차례
    }

    @Test
    fun `무인도 직행 벌칙은 더블 보너스도 없앤다`() {
        val (e, s) = game(3, 4)
        e.applyPenaltyForTest(penaltyId(PenaltyEffect.ISLAND))
        assertEquals(GameConfig.TILE_ISLAND, s.p(0).position)
        assertEquals(GameConfig.ISLAND_TURNS, s.p(0).islandTurns)
        assertFalse(s.extraRoll)
    }

    @Test
    fun `벌칙 면제권이 있으면 룰렛 전에 묻고, 쓰면 룰렛 없이 넘어간다`() {
        val (e, s) = game(3, 4)
        val a = s.p(0)
        val id = CardData.idOf(CardEffect.KEEP_PENALTY_FREE)
        s.deck.remove(id)
        a.penaltyFreeCards = 1
        e.roll()                                // → 7 벌칙
        assertEquals(PendingKind.USE_PENALTY_FREE, s.pending!!.kind)
        val ev = e.answerPenaltyFree(true)
        assertNull(s.pending)
        assertEquals(0, a.penaltyFreeCards)
        assertEquals(id, s.deck.last())
        assertTrue(ev.any { it is GameEvent.Banner && it.text == "벌칙 면제!" })
        assertTrue(e.canEndTurn())
        assertEquals(START, a.money)

        val (e2, s2) = game(3, 4)
        s2.p(0).penaltyFreeCards = 1
        e2.roll()
        e2.answerPenaltyFree(false)             // 아끼면 룰렛
        assertEquals(PendingKind.PENALTY_SPIN, s2.pending!!.kind)
        assertEquals(1, s2.p(0).penaltyFreeCards)
    }

    @Test
    fun `벌칙 면제권은 황금열쇠 덱에 있고 뽑으면 보관한다`() {
        val (e, s) = game(1, 1)
        val id = CardData.idOf(CardEffect.KEEP_PENALTY_FREE)
        s.deck.remove(id); s.deck.add(0, id)
        e.roll()
        assertEquals(1, s.p(0).penaltyFreeCards)
        assertFalse(s.deck.contains(id))
    }

    @Test
    fun `벌칙 지속 효과와 면제권도 저장된다`() {
        val (_, s) = game()
        s.p(0).oneDieNext = true; s.p(0).halfSalaryNext = true
        s.p(1).doubleTollNext = true; s.p(1).skipTurns = 1; s.p(1).penaltyFreeCards = 1
        s.pending = Pending(PendingKind.PENALTY_SPIN, 22)
        val r = SaveCodec.decode(SaveCodec.encode(s))
        assertTrue(r.players[0].oneDieNext && r.players[0].halfSalaryNext)
        assertTrue(r.players[1].doubleTollNext)
        assertEquals(1, r.players[1].skipTurns)
        assertEquals(1, r.players[1].penaltyFreeCards)
        assertEquals(PendingKind.PENALTY_SPIN, r.pending!!.kind)
    }

    @Test
    fun `벌칙 등급별 목록 (소형4 중형4 대형3)과 룰렛 8칸`() {
        assertEquals(4, PenaltyData.ofGrade(PenaltyGrade.SMALL).size)
        assertEquals(4, PenaltyData.ofGrade(PenaltyGrade.MEDIUM).size)
        assertEquals(3, PenaltyData.ofGrade(PenaltyGrade.LARGE).size)
        assertEquals(8, PenaltyData.ROULETTE.size)
        for (g in PenaltyGrade.values()) assertTrue(PenaltyData.ROULETTE.contains(g))
    }

    // ─────────────────────────────────────────────
    // 무작위 전체 게임 시뮬레이션 (예외·불변식 검사)
    // ─────────────────────────────────────────────

    @Test
    fun `무작위로 수백 판을 끝까지 진행해도 규칙이 깨지지 않는다`() {
        var bankruptEnds = 0
        var limitEnds = 0
        var trades = 0
        var spins = 0
        repeat(200) { seed ->
            val rnd = Random(seed)
            val n = 2 + seed % 3
            val s = GameEngine.newGame((0 until n).map { PlayerSetup("P$it", it) }, if (seed % 4 == 0) 20 else 0, rnd)
            val e = GameEngine(s, Dice(rnd), rnd)
            var steps = 0
            while (!s.gameOver && steps < 8_000) {
                val p = s.currentPlayer
                when {
                    s.pending?.kind == PendingKind.USE_TOLL_FREE -> e.answer(rnd.nextBoolean())
                    s.pending?.kind == PendingKind.USE_PENALTY_FREE -> e.answerPenaltyFree(rnd.nextBoolean())
                    s.pending?.kind == PendingKind.PENALTY_SPIN -> { e.spinPenalty(); spins++ }
                    p.money < 0 -> {
                        // 빚: 가끔 거래로 돈을 받고, 아니면 싼 땅부터 판다
                        val others = s.players.filter { !it.bankrupt && it.id != p.id && it.money > 0 }
                        if (e.canTrade() && others.isNotEmpty() && rnd.nextInt(4) == 0) {
                            val q = others[rnd.nextInt(others.size)]
                            val mine = e.ownedTiles(p.id).map { it.index }
                            val give = if (mine.isNotEmpty()) intArrayOf(mine[rnd.nextInt(mine.size)]) else intArrayOf()
                            val o = TradeOffer(p.id, q.id, give, 0, intArrayOf(), rnd.nextInt(q.money + 1))
                            if (!o.isEmpty && e.validateTrade(o) == null) { e.trade(o, rnd.nextBoolean()); trades++ }
                        } else {
                            val cheapest = e.ownedTiles(p.id).minByOrNull { e.saleValue(it.index) }!!
                            e.sell(intArrayOf(cheapest.index))
                        }
                    }
                    e.canAffordOffer() && rnd.nextInt(10) < 7 -> if (s.offerKind == OfferKind.BUY) e.buy() else e.build()
                    e.canUseEscapeCard() && rnd.nextBoolean() -> e.useEscapeCard()
                    e.canSpaceTravel() -> {
                        var t = rnd.nextInt(40)
                        if (t == GameConfig.TILE_SPACE) t = 0
                        e.spaceTravel(t)
                    }
                    e.canRoll() -> e.roll()
                    e.canEndTurn() -> e.endTurn()
                    else -> error("진행할 수 없는 상태 (seed=$seed, step=$steps)")
                }
                checkInvariants(e, s)
                steps++
            }
            if (s.turnLimit > 0) assertTrue("턴 제한 게임은 끝나야 합니다 (seed=$seed)", s.gameOver)
            if (s.gameOver && s.aliveCount() == 1) bankruptEnds++
            if (s.gameOver && s.turnLimit > 0 && s.aliveCount() > 1) limitEnds++
            assertEquals(SaveCodec.encode(s), SaveCodec.encode(SaveCodec.decode(SaveCodec.encode(s))))
        }
        println("시뮬레이션: 파산 종료 $bankruptEnds, 턴 제한 종료 $limitEnds, 거래 $trades, 벌칙 룰렛 $spins / 200판")
        assertTrue(spins > 100)
        assertTrue(bankruptEnds > 50)
        assertTrue(limitEnds > 0)
        assertTrue(trades > 0)
    }

    private fun checkInvariants(e: GameEngine, s: GameState) {
        for (p in s.players) {
            if (p.id != s.current) assertTrue("차례가 아닌 사람은 마이너스가 될 수 없음", p.money >= 0)
            assertTrue(p.position in 0 until 40)
            if (p.bankrupt) {
                assertEquals(0, p.money)
                assertEquals(0, e.landCount(p.id))
            }
        }
        for (i in 0 until 40) {
            assertTrue(s.level[i] in 0..3)
            if (s.owner[i] < 0) assertEquals(0, s.level[i])
        }
        if (s.offerTile >= 0) {
            if (s.offerKind == OfferKind.BUY) assertEquals(-1, s.owner[s.offerTile])
            else assertEquals(s.current, s.owner[s.offerTile])
        }
        val held = s.players.sumOf { it.escapeCards + it.tollFreeCards + it.halfBuildCards + it.penaltyFreeCards }
        assertEquals(CardData.cards.size, s.deck.size + held)
        assertEquals(s.deck.size, s.deck.toSet().size)
        assertTrue(s.tradesThisTurn <= GameConfig.MAX_TRADES_PER_TURN)
        if (!s.gameOver) assertFalse(s.currentPlayer.bankrupt)
    }
}
