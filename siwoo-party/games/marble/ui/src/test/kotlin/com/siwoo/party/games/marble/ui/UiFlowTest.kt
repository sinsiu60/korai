package com.siwoo.party.games.marble.ui

import com.siwoo.party.common.ui.*

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.games.marble.GameConfig
import com.siwoo.party.games.marble.data.CardData
import com.siwoo.party.games.marble.logic.Dice
import com.siwoo.party.games.marble.logic.GameEngine
import com.siwoo.party.games.marble.model.GameState
import com.siwoo.party.games.marble.model.Player
import com.siwoo.party.games.marble.model.PlayerSetup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 가로 화면 UI 흐름 테스트 (Robolectric).
 * - 720×1280 기기(가로 640×360dp)에서 패널·보드·행동 바가 겹치지 않는지
 * - 연출 큐가 통행료 → 마이너스 → 판매 안내 순서로 진행되는지
 * - 실제 버튼·팝업을 눌러 여러 판을 진행해도 멈추거나 죽지 않는지
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class UiFlowTest {

    private class FixedDice(vararg faces: Int) : Dice() {
        private val q = ArrayDeque(faces.toList())
        override fun rollOne(): Int = q.removeFirstOrNull() ?: 1
    }

    @After
    fun tearDown() {
        MainActivity.diceOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(state: GameState): MainActivity {
        GameStorage(RuntimeEnvironment.getApplication()).save(state)
        val c = Robolectric.buildActivity(MainActivity::class.java)
        val a = c.setup().get()
        settle(a)
        return a
    }

    /** 연출 큐·차례 전환 화면이 끝날 때까지 시간을 흘린다 */
    private fun settle(a: MainActivity) {
        repeat(400) {
            if (!a.queue.running && !overlayShown(a)) { idle(50); if (!a.queue.running) return }
            // 황금열쇠·벌칙 카드처럼 연출 중에 [확인]을 기다리는 경우
            if (a.queue.running && a.popups.isShowing && buttons(a.popups).isNotEmpty()) return
            idle(250)
        }
        fail("연출이 끝나지 않음: ${a.queue.history.takeLast(10)}")
    }

    private fun overlayShown(a: MainActivity): Boolean = a.turnOverlayShowing

    private fun findAll(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): MutableList<View> {
        if (v.visibility != View.VISIBLE) return out
        if (pred(v)) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) findAll(v.getChildAt(i), pred, out)
        return out
    }

    private fun buttons(v: View): List<GameButton> =
        findAll(v, { it is GameButton && it.isShown && it.isEnabled }).map { it as GameButton }

    private fun button(a: MainActivity, text: String): GameButton? =
        buttons(a.window.decorView).firstOrNull { it.text.toString().startsWith(text) }

    private fun rect(v: View): Rect {
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    private fun game(n: Int, seed: Int = 1): GameState =
        GameEngine.newGame((0 until n).map { PlayerSetup("플레이어${it + 1}", it) }, 0, Random(seed))

    // ─────────────────────────────────────────────
    // 1. 작은 화면 가로 배치
    // ─────────────────────────────────────────────

    @Test
    fun `720x1280 가로 화면에서 4인 패널·보드·행동 바가 겹치지 않는다`() {
        val a = launch(game(4))
        val root = a.window.decorView
        val screen = rect(root)
        assertEquals(1280, screen.width())
        assertEquals(720, screen.height())
        val board = rect(a.board)
        val panels = findAll(root, { it is PlayerPanelView }).map { rect(it) }
        assertEquals(4, panels.size)
        val bar = rect(a.btnEnd.parent as View)
        val all = panels + listOf(board, bar)
        for (i in all.indices) {
            assertTrue("화면 밖으로 나감: ${all[i]}", screen.contains(all[i]))
            for (k in i + 1 until all.size) {
                assertFalse("겹침: ${all[i]} / ${all[k]}", Rect.intersects(all[i], all[k]))
            }
        }
        // 보드는 정사각형, 충분히 크게
        assertEquals(board.width(), board.height())
        assertTrue("보드가 너무 작음: ${board.width()}", board.width() >= 560)
        // 패널: 왼쪽 2개는 보드 왼쪽, 오른쪽 2개는 보드 오른쪽
        assertEquals(2, panels.count { it.right <= board.left })
        assertEquals(2, panels.count { it.left >= board.right })
        // 행동 바 버튼이 모두 화면 안에
        for (b in findAll(a.btnEnd.parent as View, { it is GameButton })) {
            val r = rect(b)
            if (b.visibility == View.VISIBLE) assertTrue("버튼이 잘림: ${(b as GameButton).text}", screen.contains(r) && r.width() > 0)
        }
    }

    // ─────────────────────────────────────────────
    // 2. 연출 큐 순서: 통행료 → 마이너스 → 판매 안내
    // ─────────────────────────────────────────────

    @Test
    fun `통행료로 마이너스가 되면 연출이 순서대로 진행되고 판매 안내 상태가 된다`() {
        val st = game(2)
        val me: Player = st.players[0]
        me.money = 10_000; me.position = 33
        st.owner[1] = 0                         // 팔 땅
        st.owner[39] = 0                        // 서울 (판매가 50만)
        st.owner[37] = 1; st.level[37] = 1      // 상대 뉴욕 별장 (통행료 17.5만)
        MainActivity.diceOverride = FixedDice(1, 3)
        val a = launch(st)
        a.queue.history.clear()
        assertTrue("주사위가 굴릴 수 있는 상태", a.board.diceReady)
        a.onDiceTapped()
        settle(a)
        val h = a.queue.history
        val order = listOf("dice", "moveDelay", "move", "arrive", "flash", "banner:통행료 수거!", "money", "debt")
        var at = -1
        for (step in order) {
            val i = h.indexOf(step)
            assertTrue("$step 없음: $h", i >= 0)
            assertTrue("순서 틀림 ($step): $h", i > at)
            at = i
        }
        assertTrue(a.stateForTest.players[0].money < 0)
        assertTrue("[판매]가 깜빡임", a.btnSell.blinking)
        assertFalse("[턴 종료] 비활성화", a.btnEnd.isEnabled)
        assertFalse("주사위 비활성화", a.board.diceReady)

        // 판매 화면에서 땅을 팔면 마이너스가 풀리고 턴 종료 가능
        a.btnSell.performClick()
        idle(400)
        val cards = findAll(a.window.decorView, { it is LandItemView })
        assertEquals(2, cards.size)
        cards.first { it.contentDescription.toString().startsWith("서울") }.performClick()
        button(a, "판매 확정")!!.performClick()
        settle(a)
        assertTrue(a.queue.history.contains("sold"))
        assertEquals(10_000 - 175_000 + 500_000, a.stateForTest.players[0].money)
        assertEquals(-1, a.stateForTest.owner[39])
        assertFalse("깜빡임 해제", a.btnSell.blinking)
        assertTrue("이제 턴 종료 가능", a.btnEnd.isEnabled)
    }

    @Test
    fun `빈 땅은 자동 팝업 없이 칸을 터치해야 구매 카드가 뜬다`() {
        val st = game(2)
        MainActivity.diceOverride = FixedDice(1, 2)
        val a = launch(st)
        a.onDiceTapped()
        settle(a)
        assertEquals(3, a.stateForTest.offerTile)
        assertFalse("자동 팝업 없음", a.popups.isShowing)
        a.onTileTapped(3)
        idle(300)
        assertTrue(a.popups.isShowing)
        button(a, "구매")!!.performClick()
        settle(a)
        assertEquals(0, a.stateForTest.owner[3])
        assertTrue(a.queue.history.containsAll(listOf("money", "flag")))
        assertTrue(a.queue.history.indexOf("money") < a.queue.history.indexOf("flag"))
        assertTrue(a.btnEnd.isEnabled)
    }

    // ─────────────────────────────────────────────
    // 3. 실제 버튼으로 여러 판 자동 진행 (멈춤·예외 검사)
    // ─────────────────────────────────────────────

    @Test
    fun `버튼과 팝업을 눌러 여러 판을 진행해도 멈추지 않는다`() {
        var totalActions = 0
        var rounds = 0
        val seen = HashSet<String>()
        for (seed in 0 until 3) {
            val rnd = Random(seed)
            val n = 2 + seed % 3
            val st = game(n, seed)
            for (p in st.players) p.money = 1_500_000 // 빨리 끝나도록
            val a = launch(st)
            var actions = 0
            while (actions < 260 && !a.stateForTest.gameOver) {
                if (!act(a, rnd, seen)) fail("누를 것이 없음 (seed=$seed, actions=$actions, 연출=${a.queue.history.takeLast(8)})")
                actions++
                settle(a)
            }
            totalActions += actions
            rounds += a.stateForTest.round
            a.finish()
        }
        println("UI 자동 진행: 행동 $totalActions 번, 라운드 합 $rounds, 본 연출 ${seen.sorted()}")
        assertTrue(rounds > 6)
        assertTrue("판매·거래·룰렛 연출이 한 번 이상", seen.contains("sold") || seen.contains("trade") || seen.contains("roulette"))
    }

    /** 지금 할 수 있는 행동 하나를 무작위로 한다. 없으면 false */
    private fun act(a: MainActivity, rnd: Random, seen: MutableSet<String>): Boolean {
        seen.addAll(a.queue.history)
        val root = a.window.decorView
        val st = a.stateForTest
        // 룰렛
        val wheel = findAll(root, { it is RouletteView }).firstOrNull() as RouletteView?
        if (wheel != null && !a.queue.running && st.pending?.kind == com.siwoo.party.games.marble.model.PendingKind.PENALTY_SPIN) {
            wheel.onSpinRequest?.invoke(); return true
        }
        if (a.popups.isShowing) {
            // 판매 화면: 카드 몇 장 고르고 확정
            val lands = findAll(root, { it is LandItemView })
            if (lands.isNotEmpty() && rnd.nextInt(3) > 0) {
                lands[rnd.nextInt(lands.size)].performClick()
                if (rnd.nextBoolean()) lands[rnd.nextInt(lands.size)].performClick()
            }
            val bs = buttons(a.popups).filter {
                val t = it.text.toString()
                !t.startsWith("타이틀로") && !t.startsWith("새 게임") && !t.startsWith("저장하고")
            }
            if (bs.isEmpty()) return false
            val prefer = bs.firstOrNull { it.text.toString().let { t -> t.startsWith("판매 확정") || t.startsWith("제안하기") || t == "확인" || t == "수락" || t == "보드 보기" } }
            (if (prefer != null && rnd.nextInt(4) > 0) prefer else bs[rnd.nextInt(bs.size)]).performClick()
            return true
        }
        val options = ArrayList<() -> Unit>()
        if (a.board.diceReady) repeat(4) { options.add { a.onDiceTapped() } }
        if (st.offerTile >= 0) repeat(3) { options.add { a.onTileTapped(st.offerTile) } }
        if (GameEngine(st).canSpaceTravel()) options.add { a.onTileTapped(rnd.nextInt(GameConfig.BOARD_SIZE)) }
        if (a.btnEnd.isEnabled) repeat(2) { options.add { a.btnEnd.performClick() } }
        if (a.btnSell.isEnabled && (st.currentPlayer.money < 0 || rnd.nextInt(8) == 0)) repeat(3) { options.add { a.btnSell.performClick() } }
        if (a.btnTrade.isEnabled && rnd.nextInt(6) == 0) options.add { a.btnTrade.performClick() }
        if (options.isEmpty()) return false
        options[rnd.nextInt(options.size)]()
        return true
    }

    @Test
    fun `황금열쇠 덱은 16장 (벌칙 면제권 포함)`() {
        assertEquals(16, CardData.cards.size)
    }
}
