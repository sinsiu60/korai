package com.siwoo.party.games.yut.ui

import android.content.Context
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.yut.GameConfig
import com.siwoo.party.games.yut.MoveOutcome
import com.siwoo.party.games.yut.MovePlan
import com.siwoo.party.games.yut.Phase
import com.siwoo.party.games.yut.PieceState
import com.siwoo.party.games.yut.ThrowOutcome
import com.siwoo.party.games.yut.YutBoard
import com.siwoo.party.games.yut.YutGame
import com.siwoo.party.games.yut.YutModule
import com.siwoo.party.games.yut.YutResult
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 윷놀이 화면 (차례대로, 2~4인).
 * 왼쪽: 플레이어 패널(PanelView, 대기 말·골인 수), 가운데: 윷판(YutBoardView),
 * 오른쪽 위: 지금 차례 + 이동 대기열 칩(QueueView), 오른쪽 아래: 멍석 위 윷가락(StickView). 맨 위: 날아가는 말(FlyView).
 *
 * 흐름 (판정은 YutGame, 여기서는 연출 순서를 timers 로 이어 붙인다 — 연출 시간은 모두 t() 적용)
 * - 던지기: 윷가락 터치(설정을 켜면 흔들기도) → 가락이 돌며 떨어짐 → 결과 글자(모는 금색 대형 배너) → 대기열 칩
 *   윷·모면 다시 던지기, 낙이면 무효
 * - 이동: 칩 고르기(처음 쓸 수 있는 칩 자동) → 말 고르기(판 위 말 또는 패널의 대기 말) → 도착 칸 발광 미리보기 → 그 칸 터치로 확정
 *   → 칸마다 통통 이동 → 업기(통!)·잡기(상대 말이 패널로 날아감 + "잡았다!" + 한 번 더)·골인(패널로 날아감 + "골인!")
 * - 쓸 수 없는 결과만 남으면 칩에 줄이 그어지며 버려지고 안내 배너
 * - 말을 모두 골인하면 순위 도장. 3~4인에서 1위가 나오면 [끝까지 진행 / 여기서 종료]
 * 흔들기 센서는 던질 수 있을 때만 등록하고, 던지는 순간·일시정지·끝날 때 해제한다.
 */
class YutActivity : PartyGameActivity(), YutBoardView.Listener, StickView.Listener, QueueView.Listener, PanelView.Listener {

    override val module = YutModule

    lateinit var game: YutGame
        private set
    lateinit var board: YutBoardView
        private set
    lateinit var sticks: StickView
        private set
    lateinit var queueView: QueueView
        private set
    lateinit var panel: PanelView
        private set
    lateinit var fly: FlyView
        private set

    /** 연출 중 (던지기·이동·배너) — 입력 무시 */
    var busy = true
        private set
    var ended = false
        private set

    /** 고른 대기열 칩 · 말 · 이동 계획 (미리보기) */
    var selectedChip = -1
        private set
    var selectedPiece = -1
        private set
    var plan: MovePlan? = null
        private set

    /** 1위 확정 뒤 [끝까지 진행 / 여기서 종료] 팝업 */
    var endChoice: PopupLayer.Popup? = null
        private set
    var continueButton: GameButton? = null
        private set
    var stopButton: GameButton? = null
        private set

    /** 테스트·연출 확인용: 다음 던지기의 윷가락 (배 = true) 과 낙 여부 */
    var nextSticks: BooleanArray? = null
    var nextNak = false

    // ── 흔들어 던지기 ──
    private var sensorManager: SensorManager? = null
    private var accel: Sensor? = null
    /** 설정: 흔들어 던지기 */
    var shakeEnabled = false
        private set
    /** 지금 가속도 센서 리스너가 등록되어 있는지 */
    var sensorRegistered = false
        private set
    private var lastShake = -100000L

    /** 흔들기 감지 (중력 배수가 SHAKE_G 를 넘으면 던짐) */
    val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.values.size < 3) return
            val x = e.values[0]
            val y = e.values[1]
            val z = e.values[2]
            val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            if (g < GameConfig.SHAKE_G) return
            val now = timers.now()
            if (now - lastShake < GameConfig.SHAKE_COOLDOWN_MS) return
            lastShake = now
            onThrowTapped()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val pa = PointF()
    private val pb = PointF()
    private val la = IntArray(2)
    private val lb = IntArray(2)
    private val movableNodes = BooleanArray(YutBoard.NODES)
    private val selectableWaiting = BooleanArray(GameConfig.PIECES_MAX)

    /** 던질 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingThrow: Boolean get() = !busy && !ended && !gamePaused && game.phase == Phase.THROW
    /** 말을 고르고 옮길 수 있는지 */
    val acceptingMove: Boolean get() = !busy && !ended && !gamePaused && game.phase == Phase.MOVE

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = YutGame(players.size, YutSettings.rules(this), randomOverride ?: Random.Default)
        shakeEnabled = YutSettings.shake(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val colors = IntArray(players.size) { playerColor(it) }

        val pad = Theme.dp(this, 8f)
        val row = Theme.horizontal(this).apply {
            gravity = Gravity.FILL_VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        panel = PanelView(this)
        panel.timers = timers
        panel.listener = this
        panel.setup(players.map { it.name }, colors, game.pieces)
        row.addView(panel, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))

        board = YutBoardView(this)
        board.timers = timers
        board.listener = this
        board.setup(colors, game.pieces)
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply {
            leftMargin = Theme.dp(this@YutActivity, 6f)
            rightMargin = Theme.dp(this@YutActivity, 6f)
        })

        val right = Theme.vertical(this)
        queueView = QueueView(this)
        queueView.timers = timers
        queueView.listener = this
        right.addView(queueView, Theme.lp(Theme.MATCH, Theme.dp(this, 150f)))
        sticks = StickView(this)
        sticks.timers = timers
        sticks.listener = this
        right.addView(sticks, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@YutActivity, 8f) })
        row.addView(right, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        fly = FlyView(this)
        fly.timers = timers
        stage.addView(fly, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board.sync(game)
        panel.sync(game)
        showTurn()
        queueView.setHint(HINT_READY)
        stage.post { start() }
    }

    private fun start() {
        val p = game.current
        banner("윷놀이", players[p].name + "부터 던져요!", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS * 6 / 10)) { beginThrow(null) }
    }

    private fun showTurn() {
        val p = game.current
        panel.setCurrent(p)
        queueView.setPlayer(players[p].name, playerColor(p))
    }

    // ── 던지기 ──

    private fun beginThrow(againHint: String?) {
        if (ended) return
        showTurn()
        clearSelection()
        busy = false
        sticks.throwable = true
        sticks.setHint(if (shakeEnabled) HINT_SHAKE else HINT_TAP)
        queueView.setHint(againHint ?: HINT_THROW)
        updateSensor()
    }

    override fun onThrowTapped() {
        if (!acceptingThrow) return
        busy = true
        sticks.throwable = false
        updateSensor()
        val forced = nextSticks
        val o = if (forced != null) {
            nextSticks = null
            val n = nextNak
            nextNak = false
            game.throwWith(forced, n)
        } else game.throwYut()
        sticks.clearResult()
        sticks.setHint("")
        queueView.setHint(HINT_THROWING)
        sticks.toss(o.sticks, o.nak, t(GameConfig.THROW_MS))
        beeper.dice()
        timers.after(t(GameConfig.THROW_MS)) { landed(o) }
    }

    private fun landed(o: ThrowOutcome) {
        if (ended) return
        var wait = GameConfig.RESULT_SHOW_MS
        val r = o.result
        if (r == null) {
            sticks.showResult(NAK, Theme.RED)
            banner("낙!", "윷가락이 멍석 밖으로 · 이번 던지기는 무효", BannerTone.BAD, GameConfig.NAK_BANNER_MS)
            wait = GameConfig.NAK_BANNER_MS * 7 / 10
        } else {
            queueView.push(r)
            val color = when (r) {
                YutResult.MO, YutResult.YUT -> Theme.GOLD
                YutResult.BACKDO -> BACKDO_COLOR
                else -> Theme.TEXT
            }
            sticks.showResult(RESULT_TEXT[r.ordinal], color)
            when (r) {
                YutResult.MO -> {
                    // 금색 대형 배너 + 별·꽃가루
                    banner(RESULT_TEXT[r.ordinal], SUB_AGAIN, BannerTone.GOOD, GameConfig.MO_BANNER_MS)
                    fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.MO_BANNER_MS + 600))
                    pa.set(sticks.width / 2f, sticks.height * 0.3f)
                    mapTo(sticks, fx, pa)
                    fx.stars(pa.x, pa.y, 12, t(900))
                    wait = GameConfig.MO_BANNER_MS * 7 / 10
                }
                YutResult.YUT -> {
                    banner(RESULT_TEXT[r.ordinal], SUB_AGAIN, BannerTone.INFO, GameConfig.MO_BANNER_MS)
                    wait = GameConfig.MO_BANNER_MS * 6 / 10
                }
                else -> beeper.step()
            }
        }
        timers.after(t(wait)) { settle(o.discarded, o.turnEnded, if (o.again) HINT_AGAIN else null) }
    }

    /** 던지기·이동 뒤 다음 단계: 버린 결과 안내 → 차례 넘김 / 다시 던지기 / 이동 고르기 */
    private fun settle(discarded: List<YutResult>, turnEnded: Boolean, againHint: String?) {
        if (ended) return
        if (discarded.isNotEmpty()) {
            busy = true
            clearSelection()
            queueView.discardAll(t(GameConfig.DISCARD_BANNER_MS))
            val what = discarded.joinToString(" · ") { it.label }
            banner(what + " 버림", "쓸 수 있는 말이 없어요", BannerTone.BAD, GameConfig.DISCARD_BANNER_MS)
            timers.after(t(GameConfig.DISCARD_BANNER_MS)) { nextTurn() }
            return
        }
        if (turnEnded) {
            busy = true
            timers.after(t(GameConfig.TURN_GAP_MS)) { nextTurn() }
            return
        }
        when (game.phase) {
            Phase.THROW -> beginThrow(againHint)
            Phase.MOVE -> beginMove()
            Phase.OVER -> finishGame()
        }
    }

    private fun nextTurn() {
        if (ended) return
        queueView.clear()
        sticks.clearResult()
        showTurn()
        val p = game.current
        banner(players[p].name + " 차례", "", BannerTone.INFO, TURN_BANNER_MS)
        beginThrow(null)
    }

    // ── 이동 고르기 ──

    private fun beginMove() {
        busy = false
        sticks.throwable = false
        sticks.setHint(HINT_STICKS_WAIT)
        updateSensor()
        refreshUsable()
        var first = -1
        for (i in game.queue.indices) if (game.isUsable(game.queue[i])) { first = i; break }
        if (first >= 0) selectChip(first)
    }

    private fun refreshUsable() {
        for (i in game.queue.indices) queueView.setUsable(i, game.isUsable(game.queue[i]))
    }

    /** 칩 고르기 → 움직일 수 있는 말 표시 (하나뿐이면 바로 고름) */
    private fun selectChip(i: Int) {
        selectedChip = i
        queueView.setSelected(i)
        selectedPiece = -1
        plan = null
        board.clearPreview()
        board.setSelected(-1)
        panel.setSelectedWaiting(-1)
        val p = game.current
        for (n in movableNodes.indices) movableNodes[n] = false
        for (k in selectableWaiting.indices) selectableWaiting[k] = false
        var options = 0
        var only = -1
        var waitingCounted = false
        for (k in 0 until game.pieces) {
            if (game.plan(i, k) == null) continue
            when (game.state(p, k)) {
                PieceState.BOARD -> if (game.isStackLead(p, k)) {
                    movableNodes[game.node(p, k)] = true
                    options++
                    only = k
                }
                PieceState.WAITING -> {
                    selectableWaiting[k] = true
                    if (!waitingCounted) { waitingCounted = true; options++; only = k }
                }
                PieceState.DONE -> Unit
            }
        }
        board.setMovable(movableNodes)
        panel.setSelectable(selectableWaiting)
        queueView.setHint(HINT_PICK)
        if (options == 1) selectPiece(only)
    }

    /** 말 고르기 → 도착 예정 칸 발광 미리보기 */
    private fun selectPiece(k: Int) {
        val pl = game.plan(selectedChip, k)
        if (pl == null) {
            queueView.setHint(HINT_CANT)
            beeper.bad()
            return
        }
        selectedPiece = k
        plan = pl
        val p = game.current
        board.setSelected(if (pl.from >= 0) pl.from else -1)
        panel.setSelectedWaiting(if (pl.from < 0) k else -1)
        board.setPreview(pl.path, pl.goal, playerColor(p))
        queueView.setHint(if (pl.goal) HINT_GOAL else HINT_CONFIRM)
        beeper.step()
    }

    private fun clearSelection() {
        selectedChip = -1
        selectedPiece = -1
        plan = null
        queueView.setSelected(-1)
        board.clearPreview()
        board.setSelected(-1)
        board.setMovable(null)
        panel.setSelectable(null)
        panel.setSelectedWaiting(-1)
    }

    override fun onChipTapped(index: Int) {
        if (!acceptingMove || index !in game.queue.indices) return
        if (!game.isUsable(game.queue[index])) {
            queueView.setHint(HINT_UNUSABLE)
            beeper.bad()
            return
        }
        selectChip(index)
    }

    override fun onNodeTapped(node: Int) {
        if (!acceptingMove || selectedChip < 0) return
        if (plan != null && node == board.previewNode) {
            confirm()
            return
        }
        val ks = game.piecesAt(game.current, node)
        if (ks.isNotEmpty()) selectPiece(ks[0])
    }

    override fun onWaitingTapped(player: Int, piece: Int) {
        if (!acceptingMove || selectedChip < 0 || player != game.current) return
        if (selectedPiece == piece && plan != null) return
        selectPiece(piece)
    }

    // ── 이동 ──

    private fun confirm() {
        val chip = selectedChip
        val k = selectedPiece
        val o = game.move(chip, k) ?: return
        busy = true
        queueView.removeAt(chip)
        clearSelection()
        queueView.setHint(HINT_MOVING)
        val pl = o.plan
        val p = o.player
        // 패널: 올라간 말은 바로 빈 자리로, 잡힌 말·골인한 말은 날아가 도착한 뒤에 보임
        panel.sync(game)
        if (o.capturedPlayer >= 0) for (j in o.captured) panel.override(o.capturedPlayer, j, PieceState.BOARD)
        if (pl.goal) for (j in pl.pieces) panel.override(p, j, PieceState.BOARD)
        val from = if (pl.from < 0) YutBoard.START else pl.from
        val step = t(GameConfig.STEP_MS).coerceAtLeast(1)
        board.animateMove(p, pl.pieces, from, pl.path, step)
        for (s in pl.path.indices) timers.after(step * s + step / 2) { beeper.step() }
        timers.after(step * pl.path.size.coerceAtLeast(1)) { arrived(o) }
    }

    private fun arrived(o: MoveOutcome) {
        if (ended) return
        board.sync(game)
        val p = o.player
        val pl = o.plan
        var wait = 200L
        if (o.joined) {
            board.pop(pl.dest, t(GameConfig.STACK_POP_MS))
            board.floatLabel(pl.dest, STACK_TEXT[o.stackCount.coerceIn(0, 4)], Theme.GOLD, t(900))
            beeper.good()
            wait = GameConfig.STACK_POP_MS + 200
        }
        if (o.capturedPlayer >= 0) {
            val c = o.capturedPlayer
            board.nodeCenter(pl.dest, pa)
            mapTo(board, fly, pa)
            val flight = t(GameConfig.CAPTURE_FLY_MS)
            for ((j, k) in o.captured.withIndex()) {
                panel.slotCenter(c, k, pb)
                mapTo(panel, fly, pb)
                fly.launch(pa.x, pa.y, board.pieceR, pb.x, pb.y, panel.slotRadius, playerColor(c), NUMS[k], flight, t(80L * j), bounce = true)
            }
            board.nodeCenter(pl.dest, pb)
            mapTo(board, fx, pb)
            fx.stars(pb.x, pb.y, 10, t(800))
            shakeScreen(Theme.dpf(this, 7f), t(320))
            val sub = players[c].name + " 말 " + o.captured.size + "개 잡음 · 한 번 더!"
            banner(CAUGHT, sub, BannerTone.GOOD, GameConfig.CAPTURE_BANNER_MS)
            timers.after(flight + t(80L * o.captured.size)) {
                panel.sync(game)
                panel.flash(c)
            }
            wait = GameConfig.CAPTURE_BANNER_MS * 7 / 10
        }
        if (pl.goal) {
            board.nodeCenter(YutBoard.START, pa)
            mapTo(board, fly, pa)
            val flight = t(GameConfig.GOAL_FLY_MS)
            for ((j, k) in pl.pieces.withIndex()) {
                panel.slotCenter(p, k, pb)
                mapTo(panel, fly, pb)
                fly.launch(pa.x, pa.y, board.pieceR, pb.x, pb.y, panel.slotRadius, playerColor(p), NUMS[k], flight, t(70L * j), bounce = false)
            }
            board.nodeCenter(YutBoard.START, pb)
            mapTo(board, fx, pb)
            fx.stars(pb.x, pb.y, 14, t(900))
            if (!o.playerFinished) {
                val sub = players[p].name + " " + game.goalCount(p) + " / " + game.pieces
                banner(GOAL, sub, BannerTone.GOOD, GameConfig.GOAL_BANNER_MS)
            }
            timers.after(flight + t(70L * pl.pieces.size)) {
                panel.sync(game)
                panel.flash(p)
            }
            wait = GameConfig.GOAL_BANNER_MS * 7 / 10
        }
        if (o.playerFinished) {
            timers.after(t(GameConfig.GOAL_FLY_MS + 150)) { playerDone(o) }
        } else {
            timers.after(t(wait)) { settle(o.discarded, o.turnEnded, if (o.extraThrow) HINT_CAUGHT else null) }
        }
    }

    // ── 순위·끝 ──

    private fun playerDone(o: MoveOutcome) {
        if (ended) return
        val p = o.player
        panel.sync(game)
        panel.flash(p)
        if (o.gameOver) {
            finishGame()
            return
        }
        banner(players[p].name + " " + o.rank + "위!", "말을 모두 골인시켰어요", BannerTone.GOOD, GameConfig.FIRST_BANNER_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 12, t(GameConfig.FIRST_BANNER_MS))
        if (o.rank == 1 && players.size >= 3) {
            timers.after(t(GameConfig.FIRST_BANNER_MS)) { showEndChoice(p) }
        } else {
            timers.after(t(GameConfig.FIRST_BANNER_MS)) { nextTurn() }
        }
    }

    /** 3~4인: 1위 확정 뒤 [끝까지 진행 / 여기서 종료] */
    private fun showEndChoice(first: Int) {
        if (ended) return
        busy = true
        updateSensor()
        val card = Ui.darkCard(this)
        card.addView(Theme.text(this, players[first].name + " 1위 확정!", 21f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, "남은 사람끼리 끝까지 할까요?\n여기서 끝내면 골인 수 → 판 위 말 진행 거리 순으로 순위를 정해요.", 14f, Theme.TEXT).apply {
            setLineSpacing(0f, 1.2f)
        }, Ui.wrapGap(this, 10f))
        var popup: PopupLayer.Popup? = null
        val go = Ui.button(this, "끝까지 진행", Theme.GREEN_DARK, 17f) {
            popup?.dismiss()
            endChoice = null
            nextTurn()
        }
        val stop = Ui.button(this, "여기서 종료", Theme.RED_DARK, 17f) {
            popup?.dismiss()
            endChoice = null
            game.stopHere()
            finishGame()
        }
        continueButton = go
        stopButton = stop
        card.addView(Ui.buttonRow(this, go, stop, heightDp = 52f), Ui.gap(this, 16f))
        popup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 430f))
        endChoice = popup
        beeper.turn()
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        busy = true
        updateSensor()
        clearSelection()
        sticks.throwable = false
        val ranks = game.ranks ?: game.stopHere()
        panel.sync(game)
        var w = 0
        for (i in ranks.indices) if (ranks[i] == 1) { w = i; break }
        panel.setCurrent(w)
        queueView.setPlayer(players[w].name, playerColor(w))
        queueView.setHint(HINT_END)
        val order = ranks.indices.sortedBy { ranks[it] }
        val sub = order.joinToString(" · ") { ranks[it].toString() + "위 " + players[it].name }
        val title = if (game.stoppedEarly) "여기서 종료!" else players[w].name + " 승리!"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 흔들기 센서 ──

    /** 던질 수 있을 때만 리스너 등록, 아니면 해제 */
    private fun updateSensor() {
        val sm = sensorManager ?: return
        val want = shakeEnabled && !busy && !ended && !gamePaused && game.phase == Phase.THROW
        if (want && !sensorRegistered) {
            val s = accel ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accel = it } ?: return
            sm.registerListener(shakeListener, s, SensorManager.SENSOR_DELAY_GAME)
            sensorRegistered = true
        } else if (!want && sensorRegistered) {
            sm.unregisterListener(shakeListener)
            sensorRegistered = false
        }
    }

    private fun releaseSensor() {
        if (sensorRegistered) sensorManager?.unregisterListener(shakeListener)
        sensorRegistered = false
    }

    // ── 일시정지 ──

    override fun onGamePause() {
        releaseSensor()
        board.pauseAnims()
        sticks.pauseAnims()
        queueView.pauseAnims()
        panel.pauseAnims()
        fly.pauseAnims()
    }

    override fun onGameResume() {
        board.resumeAnims()
        sticks.resumeAnims()
        queueView.resumeAnims()
        panel.resumeAnims()
        fly.resumeAnims()
        updateSensor()
    }

    override fun onGameDestroy() {
        releaseSensor()
    }

    /** src 뷰 좌표 → dst 뷰 좌표 */
    private fun mapTo(src: View, dst: View, pt: PointF) {
        src.getLocationInWindow(la)
        dst.getLocationInWindow(lb)
        pt.x += (la[0] - lb[0]).toFloat()
        pt.y += (la[1] - lb[1]).toFloat()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 136f
        private const val TURN_BANNER_MS = 900L
        private val BACKDO_COLOR = 0xFFFF8A80.toInt()
        private val RESULT_TEXT = Array(YutResult.values().size) { YutResult.values()[it].label + "!" }
        private val NUMS = arrayOf("1", "2", "3", "4")
        private val STACK_TEXT = arrayOf("업기!", "업기!", "업기 ×2", "업기 ×3", "업기 ×4")
        private const val NAK = "낙!"
        private const val CAUGHT = "잡았다!"
        private const val GOAL = "골인!"
        private const val SUB_AGAIN = "한 번 더 던져요"

        private const val HINT_READY = "곧 시작해요"
        private const val HINT_THROW = "윷을 던지세요"
        private const val HINT_AGAIN = "한 번 더 던지세요!"
        private const val HINT_CAUGHT = "잡았다! 한 번 더 던지세요"
        private const val HINT_THROWING = "휙~"
        private const val HINT_PICK = "움직일 말을 고르세요"
        private const val HINT_CONFIRM = "빛나는 칸을 눌러 이동"
        private const val HINT_GOAL = "참먹이를 눌러 골인!"
        private const val HINT_CANT = "이 결과로는 못 움직여요"
        private const val HINT_UNUSABLE = "지금은 쓸 수 없는 결과예요"
        private const val HINT_MOVING = "이동 중..."
        private const val HINT_END = "게임 끝!"
        private const val HINT_TAP = "터치해서 던지기"
        private const val HINT_SHAKE = "터치하거나 흔들어서 던지기"
        private const val HINT_STICKS_WAIT = "대기열 결과로 말을 옮기세요"
    }
}
