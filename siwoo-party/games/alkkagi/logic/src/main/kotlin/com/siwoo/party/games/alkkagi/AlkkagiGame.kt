package com.siwoo.party.games.alkkagi

import kotlin.math.sqrt

/** 진행 단계: 조준(튕길 알 고르기) → 알이 움직이는 중 → 끝 */
enum class Phase { AIM, MOVING, OVER }

/** 튕기기 결과 */
enum class Flick {
    /** 튕김 → 알이 움직이기 시작 */
    SHOT,
    /** 끌기 거리가 ALKKAGI_MIN_DRAG 보다 짧아 취소 (차례 그대로) */
    TOO_SHORT,
    /** 내 알이 아님 (상대 알·떨어진 알) */
    NOT_YOURS,
    /** 지금은 튕길 수 없음 (움직이는 중·끝남) */
    NOT_NOW,
}

/**
 * 알까기 규칙 (2인, 차례대로). 순수 Kotlin — 시간은 밖에서 advance(ms) 로 넣는다.
 * - 각자 알 ALKKAGI_STONES 개: 0..7 은 0번(왼쪽), 8..15 는 1번(오른쪽)
 * - 내 알을 뒤로 끌었다 놓으면 반대 방향으로 튕겨 나감. 힘은 끌기 거리에 비례(최대 ALKKAGI_MAX_DRAG 에서 ALKKAGI_MAX_POWER)
 * - 모든 알이 멈추면(떨어지는 연출까지 끝나면) 차례가 넘어감 — 내 알만 떨어져도 넘어간다
 * - 한쪽 알이 모두 사라지면 끝. 한 번에 양쪽 마지막 알이 모두 떨어지면 **튕긴 사람이 진다**
 *   (자기 마지막 알까지 떨어뜨렸으므로). 순위: 승자 1위, 패자 2위
 */
class AlkkagiGame(firstPlayer: Int = 0, fallMs: Long = GameConfig.FALL_MS) {

    /** 게임 진행 알림 (화면 연출·소리용) */
    interface Listener {
        fun onCollide(x: Float, y: Float, speed: Float)
        /** 알이 판 밖으로 떨어지기 시작. byShooter = 튕긴 사람의 알인지 */
        fun onFall(stone: Stone, byShooter: Boolean)
        /** 모두 멈춤 → 다음 차례 next. knocked = 이번에 떨어뜨린 상대 알 수, lost = 떨어진 내 알 수 */
        fun onTurnEnd(shooter: Int, knocked: Int, lost: Int, next: Int)
        fun onGameOver(winner: Int)
    }

    var listener: Listener? = null

    val stones = Array(GameConfig.PLAYERS * GameConfig.ALKKAGI_STONES) { Stone(it, it / GameConfig.ALKKAGI_STONES) }
    val table = Table(stones, fallMs)

    var current = firstPlayer
        private set
    var phase = Phase.AIM
        private set
    /** 이긴 사람 (끝나기 전 −1) */
    var winner = -1
        private set
    /** 지금까지 튕긴 횟수 */
    var shots = 0
        private set

    /** 이번 샷에서 떨어진 상대 알 / 내 알 */
    var knocked = 0
        private set
    var lost = 0
        private set
    private var shooter = firstPlayer

    init {
        require(firstPlayer in 0 until GameConfig.PLAYERS)
        resetLayout()
        table.listener = object : Table.Listener {
            override fun onCollide(x: Float, y: Float, speed: Float) {
                listener?.onCollide(x, y, speed)
            }

            override fun onFall(stone: Stone) {
                val own = stone.owner == shooter
                if (own) lost++ else knocked++
                listener?.onFall(stone, own)
            }
        }
    }

    /** 시작 배치: 0번은 왼쪽 두 줄, 1번은 좌우 대칭으로 오른쪽 두 줄 */
    fun resetLayout() {
        val cols = GameConfig.START_COLS
        val rows = GameConfig.START_ROWS
        for (s in stones) {
            val k = s.id % GameConfig.ALKKAGI_STONES
            val cx = cols[k / rows.size]
            val y = rows[k % rows.size]
            s.x = if (s.owner == 0) cx else GameConfig.BOARD_W - cx
            s.y = y
            s.vx = 0f
            s.vy = 0f
            s.state = StoneState.ON_BOARD
            s.fall = 0f
        }
    }

    /** 판 위에 남은 알 수 (떨어지는 중인 알은 이미 뺀다) */
    fun remaining(player: Int): Int {
        var n = 0
        for (s in stones) if (s.owner == player && s.onBoard) n++
        return n
    }

    /** 지금 차례인 사람이 이 알을 튕길 수 있는지 */
    fun canFlick(id: Int): Boolean = phase == Phase.AIM && stones[id].owner == current && stones[id].onBoard

    /**
     * 알 id 를 (dragX, dragY) 만큼 뒤로 끌었다 놓음 (판 단위, 손가락이 움직인 벡터).
     * 알은 반대 방향으로, 끌기 거리에 비례한 속도로 튕겨 나간다.
     */
    fun flick(id: Int, dragX: Float, dragY: Float): Flick {
        if (phase != Phase.AIM) return Flick.NOT_NOW
        if (id !in stones.indices || !canFlick(id)) return Flick.NOT_YOURS
        val d = sqrt(dragX * dragX + dragY * dragY)
        if (d < GameConfig.ALKKAGI_MIN_DRAG) return Flick.TOO_SHORT
        val v = launchSpeed(d)
        val s = stones[id]
        s.vx = -dragX / d * v
        s.vy = -dragY / d * v
        shooter = current
        knocked = 0
        lost = 0
        shots++
        phase = Phase.MOVING
        table.resetClock()
        return Flick.SHOT
    }

    /**
     * 화면 프레임마다 흐른 시간(ms)을 넣는다. 움직이는 중에만 물리가 돈다.
     * 반환: 아직 움직이는 중이면 true (false 면 프레임 루프를 멈춰도 됨)
     */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.MOVING) return false
        table.advance(dtMs)
        if (table.settled) endTurn()
        return phase == Phase.MOVING
    }

    private fun endTurn() {
        val r0 = remaining(0)
        val r1 = remaining(1)
        if (r0 == 0 || r1 == 0) {
            phase = Phase.OVER
            winner = when {
                r0 == 0 && r1 == 0 -> 1 - shooter // 둘 다 마지막 알이 떨어지면 튕긴 사람이 진다
                r0 == 0 -> 1
                else -> 0
            }
            listener?.onGameOver(winner)
            return
        }
        current = 1 - current
        phase = Phase.AIM
        listener?.onTurnEnd(shooter, knocked, lost, current)
    }

    /** players 순서의 순위 (승자 1위, 패자 2위). 끝나기 전에는 모두 1 */
    fun ranks(): IntArray = IntArray(GameConfig.PLAYERS) { if (winner < 0 || it == winner) 1 else 2 }

    // ── 테스트·연출 확인용 ──

    /** 알을 (x, y) 에 멈춰 놓기 */
    fun place(id: Int, x: Float, y: Float) {
        val s = stones[id]
        s.x = x
        s.y = y
        s.vx = 0f
        s.vy = 0f
        s.state = StoneState.ON_BOARD
        s.fall = 0f
    }

    /** 알을 판에서 치우기 (이미 떨어진 것처럼) */
    fun remove(id: Int) {
        val s = stones[id]
        s.state = StoneState.GONE
        s.vx = 0f
        s.vy = 0f
    }

    companion object {
        /** 끌기 거리 → 힘 0..1 (게이지) */
        fun power(drag: Float): Float = (drag / GameConfig.ALKKAGI_MAX_DRAG).coerceIn(0f, 1f)

        /** 끌기 거리 → 처음 속도 (최대 ALKKAGI_MAX_POWER 로 제한) */
        fun launchSpeed(drag: Float): Float = GameConfig.ALKKAGI_MAX_POWER * power(drag)
    }
}
