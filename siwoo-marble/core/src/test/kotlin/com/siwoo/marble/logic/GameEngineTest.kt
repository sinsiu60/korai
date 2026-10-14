package com.siwoo.marble.logic

import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.CardData
import com.siwoo.marble.data.SaveCodec
import com.siwoo.marble.model.CardEffect
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.PendingKind
import com.siwoo.marble.model.Player
import com.siwoo.marble.model.PlayerSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    private fun game(vararg faces: Int, players: Int = 2): Pair<GameEngine, GameState> {
        val list = (0 until players).map { i ->
            Player(i, "P${'A' + i}", i).apply { money = START }
        }
        val state = GameState(list, deck = CardData.cards.map { it.id }.toMutableList())
        return GameEngine(state, ScriptedDice(*faces)) to state
    }

    private fun GameState.p(i: Int) = players[i]

    /** 황금열쇠 덱을 특정 카드 한 장으로 고정 (예측 가능한 테스트용) */
    private fun GameState.deckOf(effect: CardEffect) {
        deck.clear()
        deck.add(CardData.idOf(effect))
    }

    private fun cardId(title: String) = CardData.cards.first { it.title == title }.id

    // ─────────────────────────────────────────────
    // 1. 3연속 더블
    // ─────────────────────────────────────────────

    @Test
    fun `3연속 더블이면 이동하지 않고 즉시 무인도로 간다`() {
        val (e, s) = game(1, 1, 2, 2, 3, 3)
        s.deck.clear(); s.deck.add(cardId("장학금"))
        val a = s.p(0)

        e.roll()                       // (1,1) → 2번 황금열쇠: 장학금
        assertEquals(2, a.position)
        assertEquals(0, s.current)     // 더블 → 같은 사람이 다시
        assertTrue(e.canRoll())
        assertEquals(1, s.doublesCount)

        e.roll()                       // (2,2) → 6번 싱가포르: 구매 선택
        assertEquals(6, a.position)
        assertEquals(PendingKind.BUY, s.pending!!.kind)
        e.answer(false)
        assertEquals(0, s.current)     // 아직 더블 보너스 남음
        assertEquals(2, s.doublesCount)

        val ev = e.roll()              // (3,3) → 세 번째 더블
        assertEquals(GameConfig.TILE_ISLAND, a.position)
        assertEquals(GameConfig.ISLAND_TURNS, a.islandTurns)
        assertEquals(START + 100_000, a.money)   // 월급 없음 (출발 칸을 지나지 않음)
        assertEquals(1, s.current)               // 턴 넘어감
        assertEquals(0, s.doublesCount)
        assertTrue(ev.any { it is GameEvent.Jumped && it.to == GameConfig.TILE_ISLAND })
        assertTrue(ev.any { it is GameEvent.TurnChanged && it.playerId == 1 })
        assertFalse(ev.any { it is GameEvent.Moved && it.from == 6 })
    }

    @Test
    fun `더블이 아니면 연속 더블 횟수가 초기화되고 턴이 넘어간다`() {
        val (e, s) = game(2, 2, 1, 2)
        e.roll()                       // (2,2) → 4번 마닐라
        e.answer(false)
        e.roll()                       // (1,2) → 7번 황금열쇠
        s.pending?.let { fail("예상하지 못한 선택: ${it.kind}") }
        assertEquals(1, s.current)
        assertEquals(0, s.doublesCount)
    }

    // ─────────────────────────────────────────────
    // 2. 무인도 탈출
    // ─────────────────────────────────────────────

    @Test
    fun `무인도에서 더블이 나오면 즉시 탈출해 이동하고 추가로 굴리지 않는다`() {
        val (e, s) = game(3, 3)
        val a = s.p(0)
        a.position = GameConfig.TILE_ISLAND
        a.islandTurns = 3

        e.roll()
        assertEquals(0, a.islandTurns)
        assertEquals(16, a.position)   // 10 + 6
        assertEquals(PendingKind.BUY, s.pending!!.kind)
        e.answer(false)
        assertEquals(1, s.current)     // 더블이어도 한 번 더 없음
    }

    @Test
    fun `무인도에서 3턴을 보내면 4번째 턴에 움직인다`() {
        // A,B 번갈아: A 실패, B(무인도 오래 갇힘) 실패 … A 는 네 번째 턴에 3칸 이동
        val (e, s) = game(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)
        val a = s.p(0)
        val b = s.p(1)
        a.position = GameConfig.TILE_ISLAND; a.islandTurns = GameConfig.ISLAND_TURNS
        b.position = GameConfig.TILE_ISLAND; b.islandTurns = 99

        for (left in GameConfig.ISLAND_TURNS - 1 downTo 0) {
            e.roll()                   // A 실패
            assertEquals(left, a.islandTurns)
            assertEquals(GameConfig.TILE_ISLAND, a.position)
            assertEquals(1, s.current)
            e.roll()                   // B 실패
        }
        e.roll()                       // A 자유
        assertEquals(13, a.position)
    }

    @Test
    fun `무인도 탈출권을 쓰면 바로 풀려나 같은 턴에 굴리고 카드는 덱 맨 아래로`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        val escapeId = CardData.idOf(CardEffect.KEEP_ESCAPE)
        s.deck.remove(escapeId)
        a.position = GameConfig.TILE_ISLAND; a.islandTurns = 3; a.escapeCards = 1

        assertTrue(e.canUseEscapeCard())
        e.useEscapeCard()
        assertEquals(0, a.islandTurns)
        assertEquals(0, a.escapeCards)
        assertEquals(escapeId, s.deck.last())
        assertEquals(0, s.current)
        assertTrue(e.canRoll())
        e.roll()
        assertEquals(13, a.position)
    }

    @Test
    fun `무인도 칸에 그냥 도착해도 갇히고 더블 보너스는 사라진다`() {
        val (e, s) = game(3, 3)
        val a = s.p(0)
        a.position = 4
        e.roll()
        assertEquals(GameConfig.TILE_ISLAND, a.position)
        assertEquals(GameConfig.ISLAND_TURNS, a.islandTurns)
        assertEquals(1, s.current)
    }

    // ─────────────────────────────────────────────
    // 3. 파산 / 매각 순서
    // ─────────────────────────────────────────────

    @Test
    fun `돈이 부족하면 싼 건물부터 1단계씩, 그다음 싼 땅 순서로 필요한 만큼만 매각한다`() {
        val (e, s) = game(1, 3)
        val a = s.p(0); val b = s.p(1)
        a.money = 0
        a.position = 33
        // A: 타이베이(1) 빌딩까지, 베이징(3) 별장, 서울(39) 땅
        s.owner[1] = 0; s.level[1] = 2   // 매각가: 빌딩 2.5만, 별장 1만, 땅 2.5만
        s.owner[3] = 0; s.level[3] = 1   // 매각가: 별장 2만, 땅 4만
        s.owner[39] = 0                  // 매각가: 땅 50만
        s.owner[37] = 1; s.level[37] = 0 // B 의 뉴욕 (통행료 3.5만)

        e.roll()                         // 33 + 4 = 37
        val pd = s.pending!!
        assertEquals(PendingKind.RAISE_FUNDS, pd.kind)
        assertEquals(35_000, pd.amount)
        assertEquals(1, pd.creditor)

        // 건물이 있는 땅은 바로 팔 수 없다
        assertThrows(IllegalStateException::class.java) { e.sellLand(1) }

        e.autoSell()
        // 1) 베이징 별장(2만) → 2) 타이베이 빌딩(2.5만) = 4.5만 ≥ 3.5만 → 멈춤
        assertEquals(0, s.level[3]); assertEquals(0, s.owner[3])
        assertEquals(1, s.level[1]); assertEquals(0, s.owner[1])
        assertEquals(0, s.owner[39])
        assertEquals(10_000, a.money)
        assertEquals(START + 35_000, b.money)
        assertNull(s.pending)
        assertEquals(1, s.current)
    }

    @Test
    fun `직접 고른 순서로 매각하다 돈이 모이면 자동으로 지불된다`() {
        val (e, s) = game(1, 3)
        val a = s.p(0); val b = s.p(1)
        a.money = 10_000; a.position = 33
        s.owner[39] = 0                   // 서울 (매각가 50만)
        s.owner[37] = 1; s.level[37] = 1  // B 의 뉴욕 별장 → 통행료 17.5만
        e.roll()
        assertEquals(PendingKind.RAISE_FUNDS, s.pending!!.kind)
        e.sellLand(39)
        assertNull(s.pending)
        assertEquals(10_000 + 500_000 - 175_000, a.money)
        assertEquals(START + 175_000, b.money)
        assertEquals(-1, s.owner[39])
    }

    @Test
    fun `전부 팔아도 못 내면 파산하고 땅은 은행으로, 남은 돈은 채권자에게 간다`() {
        val (e, s) = game(1, 2, players = 3)
        val a = s.p(0); val b = s.p(1)
        a.money = 5_000; a.position = 36
        a.halfBuildCards = 1
        val halfId = CardData.idOf(CardEffect.KEEP_HALF_BUILD)
        s.deck.remove(halfId)
        s.owner[1] = 0; s.level[1] = 1     // 매각: 별장 1만 + 땅 2.5만
        s.owner[39] = 1                    // B 의 서울 (통행료 200만)

        val ev = e.roll()                  // 36 + 3 = 39 서울
        assertTrue(a.bankrupt)
        assertEquals(0, a.money)
        assertEquals(-1, s.owner[1]); assertEquals(0, s.level[1])
        assertEquals(START + 5_000 + 10_000 + 25_000, b.money)
        assertEquals(0, a.halfBuildCards)
        assertEquals(halfId, s.deck.last())  // 보관 카드는 덱으로
        assertTrue(ev.any { it is GameEvent.Bankrupt && it.playerId == 0 })
        assertEquals(1, s.current)           // 3인 게임이므로 계속
        assertFalse(s.gameOver)
    }

    @Test
    fun `두 명 중 한 명이 파산하면 남은 사람이 승리한다`() {
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
        e.roll()                // A: 20 → 23 상파울루 구매 선택
        e.answer(false)
        assertEquals(2, s.current)
        e.roll()
        e.answer(false)
        assertEquals(0, s.current)
        assertEquals(2, s.round)
    }

    // ─────────────────────────────────────────────
    // 4. 출발 통과 월급
    // ─────────────────────────────────────────────

    @Test
    fun `출발 칸을 지나가면 월급을 받는다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        a.position = 38
        e.roll()                       // 38 + 3 → 1 타이베이
        assertEquals(1, a.position)
        assertEquals(START + SALARY, a.money)
        assertEquals(1, a.laps)
    }

    @Test
    fun `출발 칸에 정확히 도착해도 월급을 받는다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        a.position = 37
        e.roll()
        assertEquals(0, a.position)
        assertEquals(START + SALARY, a.money)
    }

    @Test
    fun `출발 칸을 지나지 않으면 월급이 없고 뒤로 가는 카드도 월급이 없다`() {
        val (e, s) = game(1, 1)
        val a = s.p(0)
        s.deckOf(CardEffect.MOVE_BACK)
        e.roll()                       // 0 → 2 황금열쇠: 뒤로 3칸 → 39 서울
        assertEquals(39, a.position)
        assertEquals(START, a.money)
        assertEquals(PendingKind.BUY, s.pending!!.kind)
    }

    @Test
    fun `출발지로 이동 카드는 월급을 준다`() {
        val (e, s) = game(3, 4)
        val a = s.p(0)
        s.deck.clear(); s.deck.add(cardId("출발지로!"))
        e.roll()                       // 0 → 7 황금열쇠 → 출발
        assertEquals(0, a.position)
        assertEquals(START + SALARY, a.money)
    }

    // ─────────────────────────────────────────────
    // 5. 우주여행
    // ─────────────────────────────────────────────

    @Test
    fun `우주여행 칸에 도착하면 다음 턴에 원하는 칸으로 바로 이동한다`() {
        val (e, s) = game(1, 2, 1, 2)
        val a = s.p(0); val b = s.p(1)
        a.position = 27
        b.position = GameConfig.TILE_ISLAND; b.islandTurns = 99

        e.roll()                       // 27 + 3 = 30
        assertEquals(GameConfig.TILE_SPACE, a.position)
        assertTrue(a.spaceTravelPending)
        assertEquals(1, s.current)

        e.roll()                       // B: 무인도 탈출 실패
        assertEquals(0, s.current)

        assertFalse(e.canRoll())       // 굴리기 대신 칸 선택
        assertThrows(IllegalStateException::class.java) { e.roll() }
        assertThrows(IllegalArgumentException::class.java) { e.spaceTravel(GameConfig.TILE_SPACE) }
        val ev = e.spaceTravel(5)      // 제주도: 출발 통과 → 월급
        assertEquals(5, a.position)
        assertFalse(a.spaceTravelPending)
        assertEquals(START + SALARY, a.money)
        assertTrue(ev.any { it is GameEvent.Jumped && it.from == 30 && it.to == 5 })
        assertEquals(PendingKind.BUY, s.pending!!.kind)
        e.answer(true)
        assertEquals(0, s.owner[5])
        assertEquals(1, s.current)
    }

    @Test
    fun `우주여행으로 출발 칸을 지나지 않으면 월급이 없다`() {
        val (e, s) = game()
        val a = s.p(0)
        a.position = 30; a.spaceTravelPending = true
        e.spaceTravel(36)
        assertEquals(36, a.position)
        assertEquals(START, a.money)
    }

    @Test
    fun `더블로 우주여행 칸에 도착하면 추가 굴림 없이 턴이 끝난다`() {
        val (e, s) = game(2, 2)
        s.p(0).position = 26
        e.roll()
        assertTrue(s.p(0).spaceTravelPending)
        assertEquals(1, s.current)
    }

    // ─────────────────────────────────────────────
    // 6. 사회복지기금
    // ─────────────────────────────────────────────

    @Test
    fun `사회복지기금은 접수처와 기부 카드로 쌓이고 도착한 사람이 전액 받는다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2, 1, 2)
        val a = s.p(0); val b = s.p(1)
        a.position = 35                // → 38 접수처
        b.position = 32                // → 35 황금열쇠 (기부 카드)
        s.deck.clear(); s.deck.add(cardId("사회복지기금 기부"))

        e.roll()
        assertEquals(GameConfig.WELFARE_DONATION, s.welfareFund)
        assertEquals(START - GameConfig.WELFARE_DONATION, a.money)

        e.roll()
        assertEquals(GameConfig.WELFARE_DONATION + 100_000, s.welfareFund)
        assertEquals(START - 100_000, b.money)

        a.position = 17                // → 20 사회복지기금
        e.roll()
        assertEquals(0, s.welfareFund)
        assertEquals(START - GameConfig.WELFARE_DONATION + GameConfig.WELFARE_DONATION + 100_000, a.money)

        b.position = 17                // 기금이 비었을 때
        val ev = e.roll()
        assertEquals(START - 100_000, b.money)
        assertTrue(ev.any { it is GameEvent.Message && it.text.contains("비어") })
    }

    // ─────────────────────────────────────────────
    // 기타 규칙
    // ─────────────────────────────────────────────

    @Test
    fun `땅을 산 그 방문에는 짓지 못하고 다음 방문부터 한 단계씩 짓는다`() {
        val (e, s) = game(1, 2, 1, 2, 1, 2)
        val a = s.p(0); val b = s.p(1)
        b.position = GameConfig.TILE_ISLAND; b.islandTurns = 99

        e.roll()                       // 0 → 3 베이징
        assertEquals(PendingKind.BUY, s.pending!!.kind)
        e.answer(true)
        assertEquals(0, s.owner[3])
        assertEquals(0, s.level[3])
        assertNull(s.pending)          // 같은 방문에는 건설 선택 없음
        e.roll()                       // B

        a.position = 0
        e.roll()                       // 다시 베이징
        val pd = s.pending!!
        assertEquals(PendingKind.BUILD, pd.kind)
        assertEquals(40_000, pd.amount)
        e.answer(true)
        assertEquals(1, s.level[3])
        assertEquals(START - 80_000 - 40_000, a.money)
    }

    @Test
    fun `반값 건설권은 다음 건설에 자동 적용되고 덱으로 돌아간다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        val halfId = CardData.idOf(CardEffect.KEEP_HALF_BUILD)
        s.deck.remove(halfId)
        a.halfBuildCards = 1
        s.owner[3] = 0; s.level[3] = 1
        e.roll()
        val pd = s.pending!!
        assertEquals(PendingKind.BUILD, pd.kind)
        assertTrue(pd.halfBuild)
        assertEquals(40_000, pd.amount)    // 빌딩 8만의 절반
        e.answer(true)
        assertEquals(2, s.level[3])
        assertEquals(0, a.halfBuildCards)
        assertEquals(halfId, s.deck.last())
    }

    @Test
    fun `관광지는 건물을 지을 수 없다`() {
        val (e, s) = game(2, 3)
        s.owner[5] = 0
        e.roll()
        assertNull(s.pending)
    }

    @Test
    fun `남의 땅 통행료는 건물 단계에 따라 다르다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0); val b = s.p(1)
        s.owner[3] = 1; s.level[3] = 3     // 베이징 호텔 24만
        e.roll()
        assertEquals(START - 240_000, a.money)
        assertEquals(START + 240_000, b.money)
    }

    @Test
    fun `통행료 면제권을 쓰면 통행료를 내지 않는다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        a.tollFreeCards = 1
        s.deck.remove(CardData.idOf(CardEffect.KEEP_TOLL_FREE))
        s.owner[3] = 1
        e.roll()
        assertEquals(PendingKind.USE_TOLL_FREE, s.pending!!.kind)
        e.answer(true)
        assertEquals(START, a.money)
        assertEquals(0, a.tollFreeCards)
        assertEquals(CardData.idOf(CardEffect.KEEP_TOLL_FREE), s.deck.last())
    }

    @Test
    fun `통행료 면제권을 아끼면 통행료를 낸다`() {
        val (e, s) = game(1, 2)
        val a = s.p(0)
        a.tollFreeCards = 1
        s.owner[3] = 1
        e.roll()
        e.answer(false)
        assertEquals(START - 8_000, a.money)
        assertEquals(1, a.tollFreeCards)
    }

    @Test
    fun `사용한 황금열쇠는 덱 맨 아래로 간다`() {
        val (e, s) = game(1, 1)
        val first = cardId("장학금")
        s.deck.remove(first); s.deck.add(0, first)
        val size = s.deck.size
        e.roll()
        assertEquals(first, s.deck.last())
        assertEquals(size, s.deck.size)
    }

    @Test
    fun `보관형 카드는 덱에서 빠져 플레이어가 가진다`() {
        val (e, s) = game(1, 1)
        val id = CardData.idOf(CardEffect.KEEP_ESCAPE)
        s.deck.remove(id); s.deck.add(0, id)
        val size = s.deck.size
        e.roll()
        assertEquals(1, s.p(0).escapeCards)
        assertEquals(size - 1, s.deck.size)
        assertFalse(s.deck.contains(id))
    }

    @Test
    fun `생일 카드는 다른 사람의 현금 한도 안에서 받는다`() {
        val (e, s) = game(1, 1, players = 3)
        s.deckOf(CardEffect.BIRTHDAY)
        s.p(2).money = 20_000
        e.roll()
        assertEquals(START + 50_000 + 20_000, s.p(0).money)
        assertEquals(START - 50_000, s.p(1).money)
        assertEquals(0, s.p(2).money)
    }

    @Test
    fun `건물 수리비는 건물 수에 비례한다`() {
        val (e, s) = game(1, 1)
        s.deckOf(CardEffect.REPAIR)
        s.owner[3] = 0; s.level[3] = 1    // 별장 3만
        s.owner[4] = 0; s.level[4] = 3    // 별장+빌딩+호텔 3+6+10만
        s.owner[5] = 0                    // 관광지: 건물 없음
        e.roll()
        assertEquals(START - (30_000 + 30_000 + 60_000 + 100_000), s.p(0).money)
    }

    @Test
    fun `턴 제한 모드는 마지막 라운드가 끝나면 총자산 1위가 이긴다`() {
        val (e, s) = game(1, 2, 1, 2)
        s.turnLimit = 1
        s.p(1).position = 5           // B → 8 카이로
        e.roll()                      // A → 3 베이징 구매
        e.answer(true)
        e.roll()                      // B → 8 카이로 미구매
        val ev = e.answer(false)
        assertTrue(s.gameOver)
        // A: 300만-8만 + 땅 8만 = 300만, B: 300만 → 동점이면 현금 많은 B
        assertEquals(1, s.winnerId)
        assertTrue(ev.any { it is GameEvent.GameOver && it.byTurnLimit })
    }

    @Test
    fun `새 게임은 순서를 섞고 모두 같은 돈을 받는다`() {
        val setups = listOf(PlayerSetup("가", 0), PlayerSetup("나", 1), PlayerSetup("다", 2), PlayerSetup("라", 3))
        val orders = HashSet<String>()
        for (seed in 0 until 30) {
            val s = GameEngine.newGame(setups, 20, Random(seed))
            assertEquals(4, s.players.size)
            s.players.forEachIndexed { i, p ->
                assertEquals(i, p.id)
                assertEquals(START, p.money)
                assertEquals(0, p.position)
            }
            assertEquals(CardData.cards.size, s.deck.size)
            assertEquals(20, s.turnLimit)
            orders.add(s.players.joinToString { it.name })
        }
        assertTrue("순서가 섞여야 합니다", orders.size > 1)
    }

    @Test
    fun `저장 후 불러오면 상태가 그대로다`() {
        val (e, s) = game(1, 2, players = 3)
        s.owner[3] = 1; s.level[3] = 2
        s.welfareFund = 123_000
        s.p(0).tollFreeCards = 1
        e.roll()                       // 선택 대기 상태에서 저장
        assertNotNull(s.pending)
        val text = SaveCodec.encode(s)
        val r = SaveCodec.decode(text)
        assertEquals(text, SaveCodec.encode(r))
        assertEquals(s.pending!!.kind, r.pending!!.kind)
        assertEquals(123_000, r.welfareFund)
        assertEquals(2, r.level[3])
        assertEquals("PB", r.players[1].name)
        // 불러온 상태로 이어서 진행 가능
        GameEngine(r, ScriptedDice()).answer(false)
        assertEquals(1, r.current)
    }

    // ─────────────────────────────────────────────
    // 무작위 전체 게임 시뮬레이션 (예외·불변식 검사)
    // ─────────────────────────────────────────────

    @Test
    fun `무작위로 수백 판을 끝까지 진행해도 규칙이 깨지지 않는다`() {
        var bankruptEnds = 0
        var limitEnds = 0
        repeat(200) { seed ->
            val rnd = Random(seed)
            val n = 2 + seed % 3
            val setups = (0 until n).map { PlayerSetup("P$it", it) }
            val s = GameEngine.newGame(setups, if (seed % 4 == 0) 20 else 0, rnd)
            val e = GameEngine(s, Dice(rnd))
            var steps = 0
            while (!s.gameOver && steps < 5_000) {
                val pd = s.pending
                when {
                    pd == null && s.currentPlayer.spaceTravelPending -> {
                        var t = rnd.nextInt(40)
                        if (t == GameConfig.TILE_SPACE) t = 0
                        e.spaceTravel(t)
                    }
                    pd == null -> if (e.canUseEscapeCard() && rnd.nextBoolean()) e.useEscapeCard() else e.roll()
                    pd.kind == PendingKind.RAISE_FUNDS -> e.autoSell()
                    else -> e.answer(rnd.nextInt(10) < 8)
                }
                checkInvariants(e, s)
                steps++
            }
            if (s.turnLimit > 0) assertTrue("턴 제한 게임은 끝나야 합니다 (seed=$seed)", s.gameOver)
            if (s.gameOver && s.aliveCount() == 1) bankruptEnds++
            if (s.gameOver && s.turnLimit > 0 && s.aliveCount() > 1) limitEnds++
            // 저장/복원도 매 게임 확인
            assertEquals(SaveCodec.encode(s), SaveCodec.encode(SaveCodec.decode(SaveCodec.encode(s))))
        }
        println("시뮬레이션: 파산으로 끝난 판 $bankruptEnds, 턴 제한으로 끝난 판 $limitEnds / 200")
        assertTrue(bankruptEnds > 50)
        assertTrue(limitEnds > 0)
    }

    private fun checkInvariants(e: GameEngine, s: GameState) {
        for (p in s.players) {
            assertTrue("돈이 음수: ${p.money}", p.money >= 0)
            assertTrue(p.position in 0 until 40)
            if (p.bankrupt) assertTrue((0 until 40).none { s.owner[it] == p.id })
        }
        for (i in 0 until 40) {
            assertTrue(s.level[i] in 0..3)
            if (s.owner[i] < 0) assertEquals(0, s.level[i])
            if (s.owner[i] >= 0) assertFalse(s.players[s.owner[i]].bankrupt)
        }
        // 황금열쇠 카드 수 보존: 덱 + 보관 중인 카드 = 전체
        val held = s.players.sumOf { it.escapeCards + it.tollFreeCards + it.halfBuildCards }
        assertEquals(CardData.cards.size, s.deck.size + held)
        assertEquals(s.deck.size, s.deck.toSet().size)   // 덱 안에 중복 카드 없음
        if (!s.gameOver) assertFalse(s.currentPlayer.bankrupt)
    }
}
