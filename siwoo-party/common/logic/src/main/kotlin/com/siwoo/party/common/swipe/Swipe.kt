package com.siwoo.party.common.swipe

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 스와이프 판정 설정. 길이·속도 값은 모두 "기준 길이(refSize)" 배수다
 * (예: refSize = 구역 높이 → minLength 0.08 = 구역 높이의 8%, 속도 1.0 = 1초에 구역 높이만큼).
 * 각도는 수학 방향(0° = 오른쪽, 90° = 위, 화면 y 는 아래로 증가하므로 뒤집어 계산).
 */
class SwipeConfig(
    /** 이보다 짧으면 TOO_SHORT (refSize 배수) */
    var minLength: Float = 0.08f,
    /** 이보다 빨리 끝나면 TOO_QUICK (잘못 스친 터치) */
    var minDurationMs: Long = 30L,
    /** 이보다 오래 걸리면 TOO_SLOW */
    var maxDurationMs: Long = 2000L,
    /** 평균 속도가 이보다 느리면 TOO_SLOW (refSize/초) */
    var minSpeed: Float = 0.3f,
    /** 이 속도(refSize/초)이면 power = 1 */
    var fullSpeed: Float = 4f,
    /** 처음 이만큼 움직이기 전의 정지 시간은 빼고 잰다 (손가락을 대고 망설인 시간 제외, refSize 배수) */
    var startSlop: Float = 0.02f,
    /** 앞 방향 (기본 90° = 화면 위). deviationDeg 는 이 방향에서 얼마나 벗어났는지 */
    var forwardDeg: Float = 90f,
    /** 앞 방향에서 이보다 많이 벗어나면 WRONG_DIRECTION (180 = 방향 제한 없음) */
    var maxDeviationDeg: Float = 180f,
    /** |bow| 가 이 값이면 spin = ±1 (휘어짐 정도 = 최대 옆 벗어남 / 직선 길이) */
    var fullSpinBow: Float = 0.25f,
    /** 마지막 이 시간 동안의 속도 = flickSpeed (튕기기 세기) */
    var flickWindowMs: Long = 100L,
)

/** 스와이프가 무효인 이유 */
enum class SwipeReject { NONE, TOO_FEW_SAMPLES, TOO_SHORT, TOO_QUICK, TOO_SLOW, WRONG_DIRECTION }

/**
 * 스와이프 분석 결과 — 다시 쓰는 가변 객체 (매번 새로 만들지 않는다).
 * 좌표는 넣은 좌표계 그대로 (구역이 돌아가 있으면 ZoneFrame 으로 구역 안 좌표로 바꿔 넣을 것).
 */
class SwipeResult {
    /** 판정 통과 */
    var valid = false
    var reject = SwipeReject.TOO_FEW_SAMPLES
    /** 시작점 (정지 시간을 뺀 실제 출발점) · 끝점 */
    var startX = 0f
    var startY = 0f
    var endX = 0f
    var endY = 0f
    /** 시작→끝 단위 벡터 (화면 좌표: y 아래로 +) */
    var dirX = 0f
    var dirY = 0f
    /** 시작→끝 각도 (도, 0 = 오른쪽, 90 = 위, -90 = 아래, (-180, 180]) */
    var angleDeg = 0f
    /** 앞 방향(config.forwardDeg)에서 벗어난 각도: + = 오른쪽(시계 방향), - = 왼쪽 */
    var deviationDeg = 0f
    /** 직선 길이 (px) · 기준 길이 배수 */
    var length = 0f
    var lengthNorm = 0f
    /** 실제 움직인 시간 (ms) */
    var durationMs = 0L
    /** 평균 속도 (px/초) · 기준 길이 배수 */
    var speed = 0f
    var speedNorm = 0f
    /** 마지막 flickWindowMs 동안 속도 (px/초) — 마지막에 세게 튕겼는지 */
    var flickSpeed = 0f
    /** 세기 0..1 (minSpeed → 0, fullSpeed → 1, 평균 속도 기준) */
    var power = 0f
    /** 휘어짐: 직선(시작→끝)에서 가장 멀리 벗어난 거리 / 직선 길이. + = 진행 방향 오른쪽으로 불룩 */
    var bow = 0f
    /** 앞 절반 방향 → 뒤 절반 방향 꺾인 각도 (도). + = 오른쪽으로 꺾임(시계 방향) */
    var turnDeg = 0f
    /**
     * 회전(-1..1): + = 오른쪽으로 휘는 공. 왼쪽으로 불룩하게 그리면(끝에서 오른쪽으로 꺾임) +.
     * = -bow / fullSpinBow 를 -1..1 로 자른 값.
     */
    var spin = 0f

    fun clear() {
        valid = false; reject = SwipeReject.TOO_FEW_SAMPLES
        startX = 0f; startY = 0f; endX = 0f; endY = 0f; dirX = 0f; dirY = 0f
        angleDeg = 0f; deviationDeg = 0f; length = 0f; lengthNorm = 0f; durationMs = 0L
        speed = 0f; speedNorm = 0f; flickSpeed = 0f; power = 0f; bow = 0f; turnDeg = 0f; spin = 0f
    }
}

/** 점 목록(시간순)을 방향·속도·휘어짐으로 바꾸는 순수 계산 (할당 없음) */
object Swipe {

    /**
     * xs/ys/ts 의 앞 count 개 점을 분석해 out 에 채워 돌려준다.
     * refSize = 정규화 기준 길이 (px, 예: 구역 높이). 0 이하이면 1 로 본다.
     */
    fun analyze(
        xs: FloatArray, ys: FloatArray, ts: LongArray, count: Int,
        refSize: Float, config: SwipeConfig, out: SwipeResult,
    ): SwipeResult {
        out.clear()
        if (count < 2) return out
        val ref = if (refSize > 0f) refSize else 1f
        val last = count - 1

        // 1) 처음 정지 구간 건너뛰기: 시작점에서 slop 이상 처음 벗어난 점의 바로 앞 점이 출발점
        val slop = config.startSlop * ref
        var s = 0
        for (i in 1..last) {
            if (dist(xs[0], ys[0], xs[i], ys[i]) > slop) { s = i - 1; break }
            s = i // 끝까지 안 벗어나면 마지막 점 (길이 0)
        }
        val sx = xs[s]; val sy = ys[s]; val ex = xs[last]; val ey = ys[last]
        out.startX = sx; out.startY = sy; out.endX = ex; out.endY = ey
        val dx = ex - sx; val dy = ey - sy
        val len = sqrt(dx * dx + dy * dy)
        val dur = ts[last] - ts[s]
        out.length = len
        out.lengthNorm = len / ref
        out.durationMs = dur
        if (len > 0f) { out.dirX = dx / len; out.dirY = dy / len }
        out.angleDeg = wrapDeg(Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat())
        out.deviationDeg = wrapDeg(config.forwardDeg - out.angleDeg)
        if (dur > 0) {
            out.speed = len * 1000f / dur
            out.speedNorm = out.speed / ref
        }
        val span = config.fullSpeed - config.minSpeed
        out.power = if (span > 0f) ((out.speedNorm - config.minSpeed) / span).coerceIn(0f, 1f) else 1f

        // 2) 마지막 순간 속도
        val tEnd = ts[last]
        var f = last
        while (f > s && tEnd - ts[f - 1] <= config.flickWindowMs) f--
        if (f == last && f > s) f = last - 1
        val fdt = tEnd - ts[f]
        if (fdt > 0) out.flickSpeed = dist(xs[f], ys[f], ex, ey) * 1000f / fdt

        // 3) 휘어짐: 진행 방향 오른쪽 수직 (-dirY, dirX) 로의 벗어남 중 절댓값 최대
        if (len > 0f) {
            val px = -out.dirY; val py = out.dirX
            var best = 0f
            for (i in s..last) {
                val d = (xs[i] - sx) * px + (ys[i] - sy) * py
                if (abs(d) > abs(best)) best = d
            }
            out.bow = best / len
            val fs = config.fullSpinBow
            out.spin = if (fs > 0f) (-out.bow / fs).coerceIn(-1f, 1f) else 0f

            // 앞 절반 / 뒤 절반 방향 변화 (경로 중간 시점의 점 기준)
            val mid = midIndex(ts, s, last)
            val ax = xs[mid] - sx; val ay = ys[mid] - sy
            val bx = ex - xs[mid]; val by = ey - ys[mid]
            if ((ax != 0f || ay != 0f) && (bx != 0f || by != 0f)) {
                val cross = ax * by - ay * bx // 화면 좌표(y 아래)에서 + = 시계 방향(오른쪽) 꺾임
                val dot = ax * bx + ay * by
                out.turnDeg = Math.toDegrees(atan2(cross.toDouble(), dot.toDouble())).toFloat()
            }
        }

        // 4) 판정
        out.reject = when {
            last - s < 1 || len <= 0f -> SwipeReject.TOO_SHORT
            out.lengthNorm < config.minLength -> SwipeReject.TOO_SHORT
            dur < config.minDurationMs -> SwipeReject.TOO_QUICK
            dur > config.maxDurationMs || out.speedNorm < config.minSpeed -> SwipeReject.TOO_SLOW
            abs(out.deviationDeg) > config.maxDeviationDeg -> SwipeReject.WRONG_DIRECTION
            else -> SwipeReject.NONE
        }
        out.valid = out.reject == SwipeReject.NONE
        return out
    }

    /** a 를 (-180, 180] 로 */
    fun wrapDeg(a: Float): Float {
        var r = a % 360f
        if (r > 180f) r -= 360f
        if (r <= -180f) r += 360f
        return r
    }

    private fun dist(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0; val dy = y1 - y0
        return sqrt(dx * dx + dy * dy)
    }

    /** s..last 사이에서 시간상 가운데에 가장 가까운 점 (양 끝 제외할 수 있으면 제외) */
    private fun midIndex(ts: LongArray, s: Int, last: Int): Int {
        if (last - s < 2) return s + (last - s) / 2
        val tm = (ts[s] + ts[last]) / 2
        var best = s + 1
        for (i in s + 1 until last) if (abs(ts[i] - tm) < abs(ts[best] - tm)) best = i
        return best
    }
}

/**
 * 손가락 한 개의 스와이프 기록기 — 점(x, y, 시간)을 미리 만든 배열에 모은다 (할당 없음).
 * 칸이 차면 점을 하나 걸러 버리고(절반으로 줄임) 그 뒤로는 더 듬성듬성 받는다 → 긴 스와이프도 전체 모양을 유지.
 * 마지막으로 넣은 점(끝점)은 항상 따로 기억해 분석에 쓴다.
 *
 * 사용: down → begin(x, y, t), move → add(x, y, t), up → add(...) 후 analyze(refSize, config)
 */
class SwipeTracker(val capacity: Int = 64) {
    init { require(capacity >= 4) { "capacity >= 4" } }

    // 끝점 한 칸을 더 둔다 (분석 때만 씀)
    private val xs = FloatArray(capacity + 1)
    private val ys = FloatArray(capacity + 1)
    private val ts = LongArray(capacity + 1)
    private var stored = 0
    private var stride = 1
    private var received = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L

    /** 결과 (analyze 가 다시 채운다) */
    val result = SwipeResult()

    /** 기록 중인지 (begin 이후 reset 전) */
    var active = false
        private set

    /** 분석에 쓰일 점 개수 (저장된 점 + 따로 둔 끝점) */
    val sampleCount: Int get() = if (received == 0) 0 else stored + (if (endPending()) 1 else 0)

    /** 지금까지 받은 점 총수 */
    val receivedCount: Int get() = received

    val startX: Float get() = xs[0]
    val startY: Float get() = ys[0]
    val startTime: Long get() = ts[0]
    /** 마지막으로 받은 점 */
    val endX: Float get() = lastX
    val endY: Float get() = lastY
    val endTime: Long get() = lastT

    fun reset() {
        stored = 0; stride = 1; received = 0; active = false
    }

    fun begin(x: Float, y: Float, timeMs: Long) {
        reset()
        active = true
        add(x, y, timeMs)
    }

    fun add(x: Float, y: Float, timeMs: Long) {
        if (received == 0) active = true
        val t = if (received > 0 && timeMs < lastT) lastT else timeMs // 시간 역행 방지
        lastX = x; lastY = y; lastT = t
        if (received % stride == 0) {
            if (stored == capacity) compact()
            if (received % stride == 0) {
                xs[stored] = x; ys[stored] = y; ts[stored] = t
                stored++
            }
        }
        received++
    }

    /** 끝점(마지막에 받은 점)이 저장 칸에 없으면 true */
    private fun endPending(): Boolean = stored > 0 && (ts[stored - 1] != lastT || xs[stored - 1] != lastX || ys[stored - 1] != lastY)

    /** 하나 걸러 남기기: 0,2,4,… 번째 점만 남기고 간격 2배 */
    private fun compact() {
        var w = 0
        var r = 0
        while (r < stored) {
            xs[w] = xs[r]; ys[w] = ys[r]; ts[w] = ts[r]
            w++; r += 2
        }
        stored = w
        stride *= 2
    }

    /** i 번째 분석용 점 (0 ≤ i < sampleCount) */
    fun x(i: Int): Float = if (i == stored) lastX else xs[i]
    fun y(i: Int): Float = if (i == stored) lastY else ys[i]
    fun t(i: Int): Long = if (i == stored) lastT else ts[i]

    /** 지금까지의 경로를 분석해 result 에 채운다 (손 떼기 전에 불러도 됨: 조준선 미리보기 등) */
    fun analyze(refSize: Float, config: SwipeConfig, out: SwipeResult = result): SwipeResult {
        if (received == 0) { out.clear(); return out }
        var n = stored
        if (endPending()) { xs[n] = lastX; ys[n] = lastY; ts[n] = lastT; n++ }
        return Swipe.analyze(xs, ys, ts, n, refSize, config, out)
    }
}
