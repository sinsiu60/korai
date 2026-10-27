package com.siwoo.party.games.eggcatch.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.lane.LaneDrawer
import com.siwoo.party.common.lane.LaneItemPainter
import com.siwoo.party.common.lane.LaneTrack
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.eggcatch.GameConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 계란 받기 그리기 (모든 구역 공용, 할당 없음). LaneDrawer 가 구역 안 좌표로 돌려 준 캔버스에 그린다.
 * - 물체: 계란·황금 계란·폭탄. 놓친 것은 바구니 선을 지나 땅까지 떨어진다.
 * - 덧그림: 땅(풀밭), 깨진 계란 자국, 바구니(플레이어 색 띠), 받은 계란이 쏙 + "+1", 황금 반짝 + "+3",
 *   폭탄 펑 + "−3" + 바구니 흔들림, 레인 왼쪽 점수, 오른쪽 남은 시간.
 * 화면(Activity)이 바구니 위치·연출 사건을 넣고 LaneView.setNow(경기 시간) 로 다시 그린다.
 * 연출 시각은 모두 경기 시간(ms) → 일시정지하면 함께 멈춘다.
 */
class EggPainter(context: Context) : LaneItemPainter {

    // ── 화면이 넣는 값 ──
    /** 구역별 바구니 가운데 (레인 단위) */
    val basket = FloatArray(MAX_ZONES) { GameConfig.LANES / 2f }
    /** 구역별 바구니에 담긴 계란 · 그중 황금 계란 (위로 살짝 보임) */
    val filled = IntArray(MAX_ZONES)
    val filledGold = IntArray(MAX_ZONES)
    /** 남은 초 · 남은 비율 · 서두름(빨강) */
    var secs = GameConfig.GAME_SEC
    var timeFrac = 1f
    var hurry = false
    var showHud = true
    /** 연출 시간 (속도 배율 적용 값) */
    var popMs = GameConfig.POP_MS
    var sparkleMs = GameConfig.SPARKLE_MS
    var boomMs = GameConfig.BOOM_MS
    var shakeMs = GameConfig.SHAKE_MS
    var splatMs = GameConfig.SPLAT_MS
    var pulseMs = GameConfig.SCORE_PULSE_MS

    // ── 연출 사건 (구역마다 SLOTS 칸 고리 버퍼) ──
    private val evTime = LongArray(MAX_ZONES * SLOTS) { Long.MIN_VALUE }
    private val evItem = LongArray(MAX_ZONES * SLOTS)
    private val evFall = LongArray(MAX_ZONES * SLOTS)
    private val evKind = IntArray(MAX_ZONES * SLOTS)
    private val evState = IntArray(MAX_ZONES * SLOTS)
    private val evLane = IntArray(MAX_ZONES * SLOTS)
    private val evNext = IntArray(MAX_ZONES)
    private val shakeAt = LongArray(MAX_ZONES) { Long.MIN_VALUE }
    private val scoreAt = LongArray(MAX_ZONES) { Long.MIN_VALUE }

    private val dp = Theme.dpf(context, 1f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; color = Theme.BG_DEEP
    }
    private val buf = CharArray(16)

    /**
     * 받음/떨어짐 사건 (경기 시간 timeMs). itemTime·fallMs = 그 물체가 선에 닿는 시각·떨어지는 시간 (땅에 닿는 순간 계산용).
     */
    fun add(zone: Int, kind: Int, state: Int, lane: Int, timeMs: Long, itemTime: Long, fallMs: Long) {
        if (zone !in 0 until MAX_ZONES) return
        val k = zone * SLOTS + evNext[zone]
        evNext[zone] = (evNext[zone] + 1) % SLOTS
        evTime[k] = timeMs
        evItem[k] = itemTime
        evFall[k] = fallMs
        evKind[k] = kind
        evState[k] = state
        evLane[k] = lane
        if (state == ItemState.CAUGHT) {
            scoreAt[zone] = timeMs
            if (kind == GameConfig.KIND_BOMB) shakeAt[zone] = timeMs
        }
    }

    /** 모든 연출 지우기 */
    fun clearEffects() {
        evTime.fill(Long.MIN_VALUE)
        shakeAt.fill(Long.MIN_VALUE)
        scoreAt.fill(Long.MIN_VALUE)
    }

    /** 바구니가 흔들리는 중인지 (테스트용) */
    fun shaking(zone: Int, nowMs: Long): Boolean {
        val a = nowMs - shakeAt[zone]
        return shakeAt[zone] != Long.MIN_VALUE && a in 0 until shakeMs
    }

    /** 땅 y (구역 안 좌표) */
    private fun groundY(d: LaneDrawer): Float = d.frame.height - 9f * dp

    /** 물체 가운데가 이 y 를 넘으면 땅에 닿음 */
    private fun landY(d: LaneDrawer): Float = groundY(d) - d.itemSize * 0.3f

    override fun drawItem(c: Canvas, d: LaneDrawer, kind: Int, state: Int, cx: Float, cy: Float, size: Float) {
        // 땅에 닿은 뒤는 깨진 자국(덧그림)이 대신한다
        if (state == ItemState.DROPPED && cy > landY(d)) return
        val r = size * 0.42f
        when (kind) {
            GameConfig.KIND_EGG -> EggArt.egg(c, cx, cy, r, false, 255, art)
            GameConfig.KIND_GOLD -> {
                EggArt.egg(c, cx, cy, r, true, 255, art)
                // 반짝이 (떨어지는 위치에 따라 깜빡)
                val tw = 0.5f + 0.5f * sin(cy / (8f * dp))
                EggArt.twinkle(c, cx + r * 0.62f, cy - r * 0.62f, r * (0.25f + 0.15f * tw), 0xFFFFFFFF.toInt(), (140 + 115 * tw).toInt(), art)
            }
            else -> EggArt.bomb(c, cx, cy + r * 0.12f, r * 0.86f, 0.5f + 0.5f * sin(cy / (5f * dp)), 255, art)
        }
    }

    override fun drawOverlay(c: Canvas, d: LaneDrawer, track: LaneTrack, nowMs: Long) {
        val z = track.zone
        val lay = d.layout
        val h = d.frame.height
        val size = d.itemSize
        val jy = lay.judgeY
        val gy = groundY(d)
        val lw = lay.laneWidth

        // ── 땅 (풀밭 띠) ──
        fill.color = EggArt.GRASS
        fill.alpha = 230
        rect.set(lay.laneLeft + dp, gy - 1f * dp, lay.laneRight - dp, h)
        c.drawRect(rect, fill)
        stroke.color = EggArt.GRASS_LIGHT
        stroke.alpha = 255
        stroke.strokeWidth = 2f * dp
        c.drawLine(lay.laneLeft + 2f * dp, gy - dp, lay.laneRight - 2f * dp, gy - dp, stroke)

        // ── 깨진 계란 · 피한 폭탄 연기 (땅에 닿은 뒤) ──
        val spanY = jy - lay.spawnY
        val landPos = if (spanY > 0f) (landY(d) - lay.spawnY) / spanY else 1f
        for (s in 0 until SLOTS) {
            val k = z * SLOTS + s
            if (evTime[k] == Long.MIN_VALUE || evState[k] != ItemState.DROPPED) continue
            val tLand = evItem[k] + ((landPos - 1f) * evFall[k]).toLong()
            val age = nowMs - tLand
            if (age < 0) continue
            val x = lay.laneCenterX(evLane[k])
            if (evKind[k] == GameConfig.KIND_BOMB) {
                // 피한 폭탄: 땅에서 작게 피식 (연기)
                if (age >= popMs) continue
                val f = age.toFloat() / popMs
                fill.color = EggArt.SMOKE
                fill.alpha = (150 * (1f - f)).toInt()
                c.drawCircle(x, gy - size * 0.15f - size * 0.3f * f, size * (0.15f + 0.25f * f), fill)
                continue
            }
            if (age >= splatMs) continue
            val f = age.toFloat() / splatMs
            val a = if (f < 0.6f) 255 else (255 * (1f - (f - 0.6f) / 0.4f)).toInt()
            // 처음 잠깐 납작하게 퍼짐
            val grow = if (age < 90) 0.6f + 0.4f * age / 90f else 1f
            EggArt.splat(c, x, gy + 3f * dp, size * 1.35f * grow, evKind[k] == GameConfig.KIND_GOLD, a, art)
        }

        // ── 바구니 위치 (흔들림) ──
        var bx = lay.unitsToX(basket[z])
        var soot = 0f
        val sAge = nowMs - shakeAt[z]
        if (shakeAt[z] != Long.MIN_VALUE && sAge >= 0 && sAge < shakeMs) {
            val f = sAge.toFloat() / shakeMs
            bx += sin(f * 6f * PI).toFloat() * lw * 0.12f * (1f - f)
            soot = 1f - f
        }
        val bw = 2f * GameConfig.BASKET_HALF * lw * 0.98f
        val bh = minOf(lw * 0.62f, gy - jy - 3f * dp).coerceAtLeast(6f * dp)
        val er = size * 0.42f

        // ── 받은 계란이 바구니로 쏙 (바구니 앞면에 가려지도록 먼저) ──
        for (s in 0 until SLOTS) {
            val k = z * SLOTS + s
            if (evTime[k] == Long.MIN_VALUE || evState[k] != ItemState.CAUGHT || evKind[k] == GameConfig.KIND_BOMB) continue
            val age = nowMs - evTime[k]
            if (age < 0 || age >= popMs) continue
            val f = age.toFloat() / popMs
            // 레인 가운데에서 바구니 가운데로 모이며 살짝 튀었다가 가라앉음
            val x = lay.laneCenterX(evLane[k]) + (bx - lay.laneCenterX(evLane[k])) * f
            val y = jy - er * 0.6f - sin(f * PI).toFloat() * er * 0.7f + f * er * 1.6f
            EggArt.egg(c, x, y, er * (1f - 0.25f * f), evKind[k] == GameConfig.KIND_GOLD, 255, art)
        }
        // 담긴 계란 윗부분 (최대 3개)
        val n = minOf(filled[z], 3)
        for (e in 0 until n) {
            val off = (e - (n - 1) / 2f) * bw * 0.26f
            EggArt.egg(c, bx + off, jy - er * 0.2f, er * 0.62f, e < filledGold[z], 255, art)
        }
        EggArt.basket(c, bx, jy, bw, bh, d.color, soot, art)

        // ── 점수 글자·반짝·펑 (위에) ──
        for (s in 0 until SLOTS) {
            val k = z * SLOTS + s
            if (evTime[k] == Long.MIN_VALUE || evState[k] != ItemState.CAUGHT) continue
            val age = nowMs - evTime[k]
            if (age < 0) continue
            val x = lay.laneCenterX(evLane[k])
            when (evKind[k]) {
                GameConfig.KIND_EGG -> if (age < popMs) floatText(c, PLUS1, bx, jy - er * 1.6f, size, age.toFloat() / popMs, Theme.TEXT, 0.8f)
                GameConfig.KIND_GOLD -> if (age < sparkleMs) {
                    val f = age.toFloat() / sparkleMs
                    sparkle(c, bx, jy - er * 0.4f, size * (0.7f + 0.6f * f), f)
                    floatText(c, PLUS3, bx, jy - er * 1.8f, size, f, Theme.GOLD, 1f)
                }
                else -> if (age < boomMs) {
                    val f = age.toFloat() / boomMs
                    EggArt.boom(c, x, jy - er * 0.3f, size * 0.75f, f, art)
                    floatText(c, MINUS3, bx, jy - er * 1.8f, size, f, Theme.RED, 1f)
                }
            }
        }

        if (showHud) drawHud(c, d, track, nowMs)
    }

    /** 떠오르며 사라지는 점수 글자 */
    private fun floatText(c: Canvas, s: String, x: Float, y: Float, size: Float, f: Float, color: Int, scale: Float) {
        val ts = (size * 0.6f * scale).coerceAtLeast(12f * dp)
        val a = if (f < 0.5f) 255 else (255 * (1f - (f - 0.5f) / 0.5f)).toInt()
        text.textSize = ts
        outline.textSize = ts
        outline.strokeWidth = ts * 0.2f
        text.color = color
        text.alpha = a
        outline.alpha = a
        val yy = y - size * 0.9f * f
        c.drawText(s, x, yy, outline)
        c.drawText(s, x, yy, text)
        text.alpha = 255
        outline.alpha = 255
    }

    /** 황금 반짝: 천천히 도는 네 갈래 별 고리 */
    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, f: Float) {
        val a = (255 * (1f - f)).toInt()
        // 8방향(미리 계산)을 turn 만큼 돌림: cos(a+b) = cos a cos b − sin a sin b
        val turn = f * 1.2f
        val ct = cos(turn)
        val st = sin(turn)
        for (k in 0 until 8) {
            val big = k % 2 == 0
            val rr = r * (if (big) 1f else 0.7f)
            val ca = EggArt.COS8[k]
            val sa = EggArt.SIN8[k]
            val px = x + (ca * ct - sa * st) * rr
            val py = y + (sa * ct + ca * st) * rr
            EggArt.twinkle(c, px, py, (if (big) 6f else 4f) * dp * (1f - 0.4f * f), if (big) Theme.GOLD_LIGHT else 0xFFFFFFFF.toInt(), a, art)
        }
        stroke.color = Theme.GOLD
        stroke.alpha = a
        stroke.strokeWidth = 3f * dp * (1f - f) + dp
        c.drawCircle(x, y, r * 0.8f, stroke)
        stroke.alpha = 255
    }

    /** 레인 왼쪽: 점수 / 오른쪽: 남은 시간 고리 */
    private fun drawHud(c: Canvas, d: LaneDrawer, track: LaneTrack, nowMs: Long) {
        val lay = d.layout
        val w = d.frame.width
        val h = d.frame.height
        val sideL = lay.laneLeft
        val sideR = w - lay.laneRight
        if (sideL < 34f * dp || sideR < 34f * dp) return
        val z = track.zone

        // ── 점수 ──
        val lx = sideL / 2f
        val big0 = minOf(sideL * 0.42f, h * 0.2f, 40f * dp)
        var big = big0
        val pAge = nowMs - scoreAt[z]
        if (scoreAt[z] != Long.MIN_VALUE && pAge >= 0 && pAge < pulseMs) big *= 1f + 0.3f * (1f - pAge.toFloat() / pulseMs)
        val small = (big0 * 0.45f).coerceAtLeast(11f * dp)
        val top = h * 0.5f - (small + big0) * 0.55f
        text.textSize = small
        text.color = Theme.TEXT_SUB
        c.drawText(SCORE_LABEL, lx, top + small, text)
        val n = writeInt(track.score)
        text.textSize = big
        outline.textSize = big
        outline.strokeWidth = big * 0.16f
        text.color = if (track.score < 0) Theme.RED else d.color
        val sy = top + small + big0 * 1.05f + (big - big0) * 0.4f
        c.drawText(buf, 0, n, lx, sy, outline)
        c.drawText(buf, 0, n, lx, sy, text)

        // ── 시간 고리 ──
        val rx = lay.laneRight + sideR / 2f
        val rr = minOf(sideR * 0.32f, h * 0.17f, 34f * dp)
        val ry = h * 0.47f
        rect.set(rx - rr, ry - rr, rx + rr, ry + rr)
        stroke.color = Theme.BG_DEEP
        stroke.alpha = 200
        stroke.strokeWidth = rr * 0.22f
        c.drawCircle(rx, ry, rr, stroke)
        stroke.color = if (hurry) Theme.RED else Theme.GOLD
        stroke.alpha = 255
        c.drawArc(rect, -90f, 360f * timeFrac.coerceIn(0f, 1f), false, stroke)
        val m = writeInt(secs)
        val ts = rr * 0.85f
        text.textSize = ts
        outline.textSize = ts
        outline.strokeWidth = ts * 0.14f
        text.color = if (hurry) Theme.RED else Theme.TEXT
        c.drawText(buf, 0, m, rx, ry + ts * 0.36f, outline)
        c.drawText(buf, 0, m, rx, ry + ts * 0.36f, text)
    }

    /** buf 에 정수를 쓰고 길이를 돌려준다 (할당 없음, 음수는 '−') */
    private fun writeInt(value: Int): Int {
        var x = value
        var at = 0
        if (x < 0) { buf[at++] = '-'; x = -x }
        var digits = 1
        var t = x
        while (t >= 10) { t /= 10; digits++ }
        for (k in digits - 1 downTo 0) { buf[at + k] = '0' + x % 10; x /= 10 }
        return at + digits
    }

    companion object {
        const val MAX_ZONES = 4
        /** 구역마다 동시에 보이는 연출 수 */
        const val SLOTS = 12
        const val PLUS1 = "+1"
        const val PLUS3 = "+3"
        const val MINUS3 = "-3"
        const val SCORE_LABEL = "점수"
    }
}
