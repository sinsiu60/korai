package com.siwoo.party.games.jumprope

import com.siwoo.party.common.Ranking
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

enum class Phase { READY, PLAYING, OVER }

/** 걸린 이유 */
enum class Miss {
    /** 판정 구간 전에 눌렀다 */
    EARLY,
    /** 판정 구간이 지난 직후 눌렀다 (안 눌러서 걸린 것을 표시만 바꿈) */
    LATE,
    /** 판정 구간 안에 안 눌렀다 */
    MISS,
}

/** tap() 결과 */
enum class Tap {
    /** 판정 구간 안 → 점프 */
    HIT,
    /** 무시 (시작 전·탈락자·같은 구간 두 번째 탭·줄 멈춤 중) */
    IGNORED,
    /** 너무 일찍 → 걸림 */
    CAUGHT,
    /** 이미 걸린 사람이 조금 늦게 누름 → 이유가 LATE 로 바뀜 */
    LATE_NOTE,
}

/**
 * 줄이 바닥에 닿는 순간 하나.
 * timeMs = 줄 시간(일시정지·멈춤 제외) 기준 바닥에 닿는 시각, windowMs = 판정 구간 반쪽 폭.
 * intervalMs = 바로 앞 접촉에서 여기까지 (한 바퀴 시간). double = 쌩쌩이 구간, doubleFirst = 쌩쌩이 첫 번째.
 */
class Contact(
    val index: Int,
    val timeMs: Long,
    val windowMs: Long,
    val intervalMs: Long,
    val level: Int,
    val double: Boolean,
    val doubleFirst: Boolean,
)

/** 게임 진행 알림 (tap / update 안에서 바로 불린다) */
interface JumpListener {
    /** 줄이 바닥을 쳤다 (소리·넘어짐 연출용) */
    fun onSlap(c: Contact) {}
    /** player 가 걸렸다 (EARLY 는 누른 순간, MISS 는 판정 구간이 끝날 때) */
    fun onCaught(player: Int, c: Contact, why: Miss) {}
    /** 접촉 하나의 판정이 끝났다 */
    fun onContactDone(c: Contact) {}
    /** 속도 단계가 올랐다 (다음 바퀴부터 빨라짐) */
    fun onLevelUp(level: Int) {}
    /** 끝 (winner = 최후 1인, 모두 같은 접촉에서 걸렸으면 −1) */
    fun onGameOver(winner: Int) {}
}

/**
 * 줄넘기 생존 규칙 (순수 Kotlin). 시간은 밖에서 넣어 준다 (화면은 timers.now()).
 *
 * - 줄은 미리 계획한 "바닥 접촉" 시각을 따라 돈다. 접촉 k 의 단계 = k / JUMPS_PER_LEVEL, 단계마다 빨라진다.
 * - 각 접촉의 판정 구간 [시각 − window, 시각 + window] 안에 자기 구역을 한 번 누르면 점프 성공.
 *   구간 밖에서 누르면(너무 일찍) 바로 걸림, 구간이 끝날 때까지 안 누르면 걸림. 같은 구간 두 번째 탭은 무시.
 * - 쌩쌩이: 빠른 바퀴 두 번 = 접촉 두 번 → 두 번 다 눌러야 한다.
 * - 접촉 하나의 판정이 끝날 때 남은 사람이 1명 이하면 끝. 같은 접촉에서 걸린 사람은 같은 순위.
 */
class JumpRopeGame(
    val playerCount: Int,
    private val random: Random,
    private val doubleChance: Double = GameConfig.DOUBLE_CHANCE,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "2~4명" }
    }

    var listener: JumpListener? = null
    var phase = Phase.READY
        private set

    /** 탈락했는지, 몇 번째 접촉에서 걸렸는지(−1 = 살아 있음), 이유 */
    val out = BooleanArray(playerCount)
    val outAt = IntArray(playerCount) { -1 }
    val reason = arrayOfNulls<Miss>(playerCount)
    /** 넘은 횟수 */
    val jumps = IntArray(playerCount)
    /** 이 접촉 번호에 이미 눌렀음 (같은 구간 두 번째 탭 무시) */
    private val tappedFor = IntArray(playerCount) { -1 }

    private val contacts = ArrayList<Contact>()
    /** 판정이 아직 안 끝난 첫 접촉 번호 (= 지금까지 넘은 횟수) */
    var pending = 0
        private set
    /** 바닥을 친 접촉 수 */
    private var slapped = 0
    private var startRt = 0L
    private var lastDoubleFirst = -1000
    var winner = -1
        private set
    /** 끝난 순간의 줄 시간 (줄이 거기서 멈춘다) */
    private var endRt = 0L

    // ── 줄 시간 (일시정지 뒤 잠깐 멈춤 반영) ──
    private var offset = 0L
    private var holdFrom = -1L
    private var holdTo = -1L

    /** 게임 시간 now → 줄 시간 */
    private fun rt(now: Long): Long {
        if (holdFrom >= 0) {
            if (now < holdTo) return holdFrom - offset
            offset += holdTo - holdFrom
            holdFrom = -1
            holdTo = -1
        }
        return now - offset
    }

    /** 줄이 멈춰 있는 중 */
    fun holding(now: Long): Boolean = holdFrom >= 0 && now < holdTo

    /** now 부터 ms 동안 줄을 멈춘다 (그동안 탭 무시, 이후 계획이 그만큼 밀림) */
    fun hold(now: Long, ms: Long) {
        rt(now)
        if (phase != Phase.PLAYING || ms <= 0) return
        holdFrom = now
        holdTo = now + ms
    }

    val alive: Int get() = out.count { !it }

    /** 현재 속도 단계 (0부터) */
    val level: Int get() = pending / GameConfig.JUMPS_PER_LEVEL

    fun start(now: Long) {
        if (phase != Phase.READY) return
        phase = Phase.PLAYING
        offset = 0L
        startRt = now
    }

    // ── 계획 ──

    /** 접촉 i (필요하면 그때까지 계획) */
    fun contact(i: Int): Contact {
        while (contacts.size <= i) planNext()
        return contacts[i]
    }

    private fun planNext() {
        val k = contacts.size
        val lv = k / GameConfig.JUMPS_PER_LEVEL
        val period = periodFor(lv)
        val prev = contacts.lastOrNull()
        val c = when {
            prev == null -> Contact(0, startRt + GameConfig.LEAD_IN_MS, windowFor(lv, GameConfig.LEAD_IN_MS), GameConfig.LEAD_IN_MS, lv, false, false)
            prev.doubleFirst -> Contact(k, prev.timeMs + prev.intervalMs, prev.windowMs, prev.intervalMs, lv, true, false)
            k >= GameConfig.DOUBLE_MIN_INDEX && k - lastDoubleFirst >= GameConfig.DOUBLE_GAP && random.nextDouble() < doubleChance -> {
                lastDoubleFirst = k
                val iv = doubleInterval(period)
                Contact(k, prev.timeMs + iv, windowFor(lv, iv), iv, lv, true, true)
            }
            else -> Contact(k, prev.timeMs + period, windowFor(lv, period), period, lv, false, false)
        }
        contacts.add(c)
    }

    /** 다음 판정할 접촉 */
    val next: Contact get() = contact(pending)

    /** 게임 시간 기준 다음 접촉 시각 (테스트·화면용) */
    fun nextContactAt(now: Long): Long = now + (next.timeMs - rt(now))

    // ── 입력 ──

    /** player 가 now 에 눌렀다 */
    fun tap(player: Int, now: Long): Tap {
        if (phase != Phase.PLAYING) return Tap.IGNORED
        update(now)
        if (phase != Phase.PLAYING) return lateNote(player, now)
        if (holding(now)) return Tap.IGNORED
        if (out[player]) return lateNote(player, now)
        val t = rt(now)
        val c = next
        if (tappedFor[player] == c.index) return Tap.IGNORED
        if (t >= c.timeMs - c.windowMs) {
            // update 가 끝난 구간은 이미 판정했으므로 여기는 구간 안
            tappedFor[player] = c.index
            return Tap.HIT
        }
        eliminate(player, c, Miss.EARLY)
        return Tap.CAUGHT
    }

    private fun lateNote(player: Int, now: Long): Tap {
        if (!out[player] || reason[player] != Miss.MISS) return Tap.IGNORED
        val c = contact(outAt[player])
        if (rt(now) - (c.timeMs + c.windowMs) > GameConfig.LATE_NOTE_MS) return Tap.IGNORED
        reason[player] = Miss.LATE
        return Tap.LATE_NOTE
    }

    private fun eliminate(player: Int, c: Contact, why: Miss) {
        out[player] = true
        outAt[player] = c.index
        reason[player] = why
        listener?.onCaught(player, c, why)
    }

    /** 시간 진행: 바닥 치기 알림, 판정 구간이 끝난 접촉 판정 */
    fun update(now: Long) {
        if (phase != Phase.PLAYING) return
        val t = rt(now)
        while (phase == Phase.PLAYING) {
            val s = contact(slapped)
            if (s.timeMs <= t && slapped <= pending) {
                slapped++
                listener?.onSlap(s)
                continue
            }
            val c = next
            if (t <= c.timeMs + c.windowMs) break
            if (slapped <= c.index) { slapped = c.index + 1; listener?.onSlap(c) }
            resolve(c)
        }
    }

    private fun resolve(c: Contact) {
        for (p in 0 until playerCount) {
            if (out[p]) continue
            if (tappedFor[p] == c.index) jumps[p]++ else eliminate(p, c, Miss.MISS)
        }
        pending = c.index + 1
        listener?.onContactDone(c)
        val left = alive
        if (left <= 1) {
            phase = Phase.OVER
            endRt = c.timeMs + c.windowMs
            winner = if (left == 1) out.indexOfFirst { !it } else -1
            listener?.onGameOver(winner)
            return
        }
        if (pending % GameConfig.JUMPS_PER_LEVEL == 0) listener?.onLevelUp(level)
    }

    // ── 줄 모양 ──

    /**
     * now 의 줄 위상 0..1 (0 = 바닥, 0.25 = 앞쪽 가운데, 0.5 = 머리 위, 0.75 = 뒤쪽).
     * 시작 전에는 머리 위(0.5)에 멈춰 있고, 끝나면 마지막 판정 순간에 멈춘다.
     */
    fun ropePhase(now: Long): Float {
        if (phase == Phase.READY) return 0.5f
        val t = if (phase == Phase.OVER) endRt else rt(now)
        val c0 = contact(0)
        if (t < c0.timeMs) {
            val f = ((t - startRt).toFloat() / c0.intervalMs).coerceIn(0f, 1f)
            return 0.5f + 0.5f * f
        }
        // t 가 들어 있는 바퀴 찾기 (보통 판정 중인 접촉 근처)
        var k = max(0, min(pending, contacts.size - 1) - 1)
        while (k > 0 && contacts[k].timeMs > t) k--
        while (contact(k + 1).timeMs <= t) k++
        val a = contacts[k]
        val b = contacts[k + 1]
        val f = (t - a.timeMs).toFloat() / (b.timeMs - a.timeMs)
        return f.coerceIn(0f, 0.9999f)
    }

    /** now 에 줄이 쌩쌩이로 돌고 있는지 (줄 색) */
    fun spinningDouble(now: Long): Boolean {
        if (phase != Phase.PLAYING) return false
        val t = rt(now)
        val c = next
        return c.double && t > c.timeMs - c.intervalMs
    }

    /** now 가 다음 접촉의 판정 구간 안인지 (바닥 빛) */
    fun inWindow(now: Long): Boolean {
        if (phase != Phase.PLAYING || holding(now)) return false
        val c = next
        val t = rt(now)
        return t >= c.timeMs - c.windowMs && t <= c.timeMs + c.windowMs
    }

    /** 지금 바퀴 시간 (점프 연출 길이 맞추기) */
    val currentInterval: Long get() = if (phase == Phase.PLAYING) next.intervalMs else GameConfig.PERIOD_START_MS

    // ── 결과 ──

    /** 순위: 최후 1인 1위, 늦게 걸릴수록 높은 순위, 같은 접촉에서 걸리면 같은 순위 */
    fun ranks(): IntArray = Ranking.competition(List(playerCount) { if (out[it]) outAt[it].toLong() else Long.MAX_VALUE })

    companion object {
        /** 단계별 한 바퀴 시간 */
        fun periodFor(level: Int): Long =
            max(GameConfig.PERIOD_MIN_MS, (GameConfig.PERIOD_START_MS * GameConfig.SPEEDUP.pow(level)).toLong())

        /** 쌩쌩이 한 바퀴 시간 */
        fun doubleInterval(period: Long): Long =
            max(GameConfig.DOUBLE_MIN_INTERVAL_MS, (period * GameConfig.DOUBLE_FACTOR).toLong())

        /** 단계별 판정 구간 (바퀴 시간의 40% 를 넘지 않게) */
        fun windowFor(level: Int, interval: Long): Long =
            min(max(GameConfig.WINDOW_MIN_MS, GameConfig.WINDOW_START_MS - level * GameConfig.WINDOW_SHRINK_MS), interval * 2 / 5)
    }
}
