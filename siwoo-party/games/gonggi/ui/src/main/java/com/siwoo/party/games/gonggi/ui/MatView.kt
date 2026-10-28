package com.siwoo.party.games.gonggi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.gonggi.GameConfig
import com.siwoo.party.games.gonggi.GonggiGame
import com.siwoo.party.games.gonggi.Phase
import com.siwoo.party.games.gonggi.StoneState

/**
 * 공기놀이 판 (조금 비스듬히 내려다본 돗자리, 하나의 View).
 * 장판 바닥 + 사다리꼴 돗자리 / 흩어진 공깃돌 / 아래 가운데 손(플레이어 색 소매) / 공중의 돌(높이만큼 위로·크게 + 손바닥 그림자).
 * 손 둘레의 줄어드는 고리가 "받을 순간"을 알려 준다 (받기 구간 안이면 금색 + "지금!").
 * 터치하면 listener.onMatTap(바닥 돌 번호 또는 −1) — 판단은 Activity·규칙이.
 * 모든 움직임은 게임 시계([now])로 계산 → 일시정지하면 그대로 멈춘다. 스스로 반복해서 그리지 않는다. onDraw 할당 없음.
 */
class MatView(context: Context) : View(context) {

    fun interface Listener {
        fun onMatTap(stone: Int)
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: GonggiGame? = null
    private var colors = IntArray(GameConfig.MAX_PLAYERS) { Theme.GOLD }

    /** 게임 시계 (Activity 가 넣음) */
    var now = 0L

    // ── 배치 (onSizeChanged) ──
    private var cx = 0f
    private var matTop = 0f
    private var matH = 1f
    private var topW = 1f
    private var botW = 1f
    /** 손바닥 반폭 (px) */
    var handR = 1f
        private set
    private var handX = 0f
    private var handY = 0f
    /** 돌이 가장 높이 올라갈 때 위로 가는 거리 (px) */
    private var riseMax = 1f
    private val clip = Path()
    private val matPath = Path()
    private val bounds = RectF()
    private var weave = FloatArray(0)
    private var floorLines = FloatArray(0)
    private val tmp = RectF()
    private val pt = PointF()

    // ── 떨어지는 돌 연출 ──
    private var dropMask = 0
    private var dropStart = -1L
    private var dropMs = 1L
    private var dropFromH = 0f

    private val bg = Paint()
    private val matFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }

    fun bind(g: GonggiGame, playerColors: IntArray) {
        game = g
        colors = playerColors
        invalidate()
    }

    // ── 좌표 ──

    private fun widthAt(v: Float): Float = topW + (botW - topW) * (v / GameConfig.MAT_H)

    /** 돗자리 1단위 = px (깊이 v 에서) */
    private fun unitAt(v: Float): Float = widthAt(v) / GameConfig.MAT_W

    private fun mapX(u: Float, v: Float): Float = cx + (u / GameConfig.MAT_W - 0.5f) * widthAt(v)
    private fun mapY(v: Float): Float = matTop + v / GameConfig.MAT_H * matH

    /** 돌 반지름 (px, 깊이 v) */
    private fun stoneR(v: Float): Float = GameConfig.STONE_R * unitAt(v)

    /** 바닥 돌 i 의 화면 위치 (테스트·연출용) */
    fun stonePoint(i: Int, out: PointF) {
        val s = game?.stones?.get(i) ?: return
        out.set(mapX(s.x, s.y), mapY(s.y))
    }

    /** 손바닥 가운데 (화면) */
    fun handPoint(out: PointF) {
        out.set(handX, handY)
    }

    /** 터치한 곳의 바닥 돌 (가장 가까운 것, 반지름 × HIT_SCALE 안), 없으면 −1 */
    fun stoneAt(x: Float, y: Float): Int {
        val g = game ?: return -1
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in g.stones.indices) {
            val s = g.stones[i]
            if (s.state != StoneState.FLOOR) continue
            val sx = mapX(s.x, s.y)
            val sy = mapY(s.y)
            val hr = stoneR(s.y) * HIT_SCALE
            val dx = x - sx
            val dy = y - sy
            val dd = dx * dx + dy * dy
            if (dd <= hr * hr && dd < bestD) { bestD = dd; best = i }
        }
        return best
    }

    // ── 높이 (0 = 손 높이, 1 = 꼭대기) ──

    /** 포물선 높이: f = 지난 시간 / 공중 시간 (0..1 에서 0 → 1 → 0, 그 뒤는 손 아래로 조금) */
    private fun arc(f: Float): Float = (4f * f * (1f - f)).coerceAtLeast(-0.25f)

    /** 지금 공중에 있는 돌의 높이 (공중이 아니면 0) */
    fun airHeight(nowMs: Long): Float {
        val g = game ?: return 0f
        return when (g.phase) {
            Phase.AIR -> arc((nowMs - g.tossAt).toFloat() / g.airMs)
            Phase.KK_AIR -> arc((nowMs - g.kkTossAt).toFloat() / GameConfig.KK_AIR_MS)
            Phase.KK_FLIP -> arc((nowMs - g.kkFlipAt).toFloat() / GameConfig.KK_FLIP_AIR_MS)
            else -> 0f
        }
    }

    /** 떨어지는 돌 연출 시작 (mask = 돌 번호 비트, fromH = 떨어지기 시작한 높이) */
    fun startDrop(nowMs: Long, mask: Int, fromH: Float, ms: Long) {
        dropMask = mask
        dropStart = nowMs
        dropMs = maxOf(1L, ms)
        dropFromH = fromH.coerceAtLeast(0.12f)
        invalidate()
    }

    fun clearDrop() {
        dropMask = 0
        dropStart = -1L
    }

    /** 떨어지는 돌 연출 중인지 */
    fun animating(nowMs: Long): Boolean = dropMask != 0 && nowMs - dropStart < dropMs

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            listener?.onMatTap(stoneAt(e.getX(i), e.getY(i)))
        }
        return true
    }

    // ── 배치 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, 16f * d, 16f * d, Path.Direction.CW)
        matTop = 8f * d
        matH = h - matTop - 4f * d
        botW = minOf(w - 12f * d, matH * GameConfig.MAT_W * 1.08f)
        topW = botW * 0.86f
        cx = w / 2f
        matPath.reset()
        matPath.moveTo(cx - topW / 2f, matTop)
        matPath.lineTo(cx + topW / 2f, matTop)
        matPath.lineTo(cx + botW / 2f, matTop + matH)
        matPath.lineTo(cx - botW / 2f, matTop + matH)
        matPath.close()
        handX = mapX(GameConfig.HAND_X, GameConfig.HAND_Y)
        handY = mapY(GameConfig.HAND_Y)
        handR = 0.085f * unitAt(GameConfig.HAND_Y)
        riseMax = handY - 30f * d
        // 장판 (위 밝게) + 이음 선
        bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFFD9A65E.toInt(), 0xFFB47A3A.toInt(), Shader.TileMode.CLAMP)
        matFill.shader = LinearGradient(0f, matTop, 0f, matTop + matH, 0xFFEFD9A0.toInt(), 0xFFDDBB72.toInt(), Shader.TileMode.CLAMP)
        val cols = 6
        floorLines = FloatArray((cols + 1) * 4 + 4 * 4)
        var k = 0
        for (c in 0..cols) {
            val x = w * c / cols.toFloat()
            floorLines[k++] = x; floorLines[k++] = 0f; floorLines[k++] = x; floorLines[k++] = h.toFloat()
        }
        for (r in 1..4) {
            val y = h * r / 5f
            floorLines[k++] = 0f; floorLines[k++] = y; floorLines[k++] = w.toFloat(); floorLines[k++] = y
        }
        // 돗자리 짚 결 (가로줄) + 날실 (세로, 원근)
        val rows = 22
        val warps = 9
        weave = FloatArray((rows - 1) * 4 + (warps - 1) * 4)
        k = 0
        for (r in 1 until rows) {
            val v = r / rows.toFloat()
            val y = mapY(v)
            weave[k++] = mapX(0f, v); weave[k++] = y; weave[k++] = mapX(GameConfig.MAT_W, v); weave[k++] = y
        }
        for (c in 1 until warps) {
            val u = GameConfig.MAT_W * c / warps
            weave[k++] = mapX(u, 0f); weave[k++] = mapY(0f); weave[k++] = mapX(u, GameConfig.MAT_H); weave[k++] = mapY(GameConfig.MAT_H)
        }
        label.textSize = 15f * d
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        c.save()
        c.clipPath(clip)
        c.drawRect(bounds, bg)
        line.strokeWidth = 1.2f * d
        line.color = 0x33603A10
        c.drawLines(floorLines, line)
        // 돗자리
        c.drawPath(matPath, matFill)
        line.strokeWidth = 1f * d
        line.color = 0x55A07A30
        c.drawLines(weave, line)
        line.strokeWidth = 7f * d
        line.color = MAT_EDGE
        c.drawPath(matPath, line)
        line.strokeWidth = 1.5f * d
        line.color = Theme.GOLD_LIGHT
        c.drawPath(matPath, line)

        val phase = g.phase
        val h = airHeight(now)
        val dropping = dropMask != 0 && now - dropStart < dropMs
        val df = if (dropping) ((now - dropStart).toFloat() / dropMs).coerceIn(0f, 1f) else 1f

        // 바닥 돌 (떨어지는 중인 돌은 나중에)
        for (i in g.stones.indices) {
            val s = g.stones[i]
            if (s.state != StoneState.FLOOR) continue
            if (dropping && (dropMask shr i) and 1 == 1) continue
            val r = stoneR(s.y)
            val x = mapX(s.x, s.y)
            val y = mapY(s.y)
            GonggiArt.shadow(c, x + r * 0.15f, y + r * 0.55f, r * 1.05f, 1f)
            GonggiArt.stone(c, x, y, r, i)
        }
        // 옆에 둔 돌 (이번 단계에서 이미 집은 것) — 왼쪽 아래 줄
        var aside = 0
        for (i in g.stones.indices) {
            if (g.stones[i].state != StoneState.ASIDE) continue
            val u = ASIDE_U0 + aside * ASIDE_DU
            val r = stoneR(ASIDE_V) * 0.85f
            val x = mapX(u, ASIDE_V)
            val y = mapY(ASIDE_V)
            GonggiArt.shadow(c, x, y + r * 0.5f, r, 0.8f)
            GonggiArt.stone(c, x, y, r, i)
            aside++
        }

        // 손
        val back = phase == Phase.KK_BACK || (phase == Phase.KK_AIR && h < 0.97f && now - g.kkTossAt > GameConfig.KK_AIR_MS / 5)
        val who = g.current
        GonggiArt.hand(c, handX, handY, handR, back, colors[who % colors.size], height.toFloat())

        // 손에 있는 돌 (공중이 아닌 것)
        val sr = stoneR(GameConfig.HAND_Y) * 0.9f
        var slot = 0
        for (i in g.stones.indices) {
            val st = g.stones[i].state
            val inHand = st == StoneState.HAND || st == StoneState.PICKED || st == StoneState.BACK
            if (!inHand) continue
            val flying = (phase == Phase.AIR && i == GameConfig.TOSS_STONE) || phase == Phase.KK_AIR ||
                (phase == Phase.KK_FLIP && st == StoneState.BACK)
            if (flying) continue
            val k = if (i == GameConfig.TOSS_STONE) 0 else (++slot).coerceAtMost(CLUSTER_X.size - 1)
            GonggiArt.stone(c, handX + CLUSTER_X[k] * sr, handY - handR * 0.15f + CLUSTER_Y[k] * sr, sr, i)
        }

        // 떨어지는 돌 (손 근처 → 바닥 자리, 높이는 줄어듦)
        if (dropping) {
            for (i in g.stones.indices) {
                if ((dropMask shr i) and 1 == 0) continue
                val s = g.stones[i]
                val r = stoneR(s.y)
                val fx = mapX(s.x, s.y)
                val fy = mapY(s.y)
                val x = handX + (fx - handX) * df
                val y = handY + (fy - handY) * df
                val lift = dropFromH * (1f - df) * (1f - df) + 0.12f * (1f - df) * df * 4f
                GonggiArt.shadow(c, x, y + r * 0.55f, r * (1.1f - lift * 0.3f), 1f - lift * 0.5f)
                GonggiArt.stone(c, x, y - lift * riseMax, r * (1f + lift * 0.5f), i)
            }
        }

        // 공중의 돌
        if (phase == Phase.AIR) {
            drawFlying(c, GameConfig.TOSS_STONE, 0, h, sr)
        } else if (phase == Phase.KK_AIR) {
            for (i in g.stones.indices) drawFlying(c, i, i, h, sr)
        } else if (phase == Phase.KK_FLIP) {
            var k = 0
            for (i in g.stones.indices) if (g.stones[i].state == StoneState.BACK) drawFlying(c, i, k++, h, sr)
        }

        // 받을 순간 고리 + 집기 표시
        when (phase) {
            Phase.AIR -> {
                drawRing(c, g.landAt, g.airMs, now >= g.catchStart && now <= g.catchEnd)
                drawPicks(c, g.required, g.picked)
            }
            Phase.KK_AIR -> drawRing(c, g.kkLandAt, GameConfig.KK_AIR_MS, now >= g.kkLandAt - KK_GOLD_MS && now <= g.kkLandAt + KK_GOLD_MS)
            Phase.KK_FLIP -> drawRing(c, g.kkGrabAt, GameConfig.KK_FLIP_AIR_MS,
                now >= g.kkGrabAt - GameConfig.KK_GRAB_EARLY_MS && now <= g.kkGrabAt + GameConfig.KK_GRAB_LATE_MS)
            Phase.READY -> drawPicks(c, g.required, 0)
            else -> {}
        }
        c.restore()
    }

    /** 공중의 돌 k 번째 (흩어진 모양) — 높이 h 만큼 위로·크게, 그림자는 손 위 */
    private fun drawFlying(c: Canvas, i: Int, k: Int, h: Float, sr: Float) {
        val spread = 1f + h * 0.6f
        val ox = CLUSTER_X[k % CLUSTER_X.size] * sr * spread
        val oy = CLUSTER_Y[k % CLUSTER_Y.size] * sr * spread
        val hh = h.coerceAtLeast(0f)
        GonggiArt.shadow(c, handX + ox, handY + oy - handR * 0.1f, sr * (1.1f - hh * 0.45f), 1f - hh * 0.65f)
        GonggiArt.stone(c, handX + ox, handY - handR * 0.15f + oy - h * riseMax, sr * (1f + hh * 0.55f), i)
    }

    /** 손 둘레 고리: 받을 순간(target)에 손 크기가 되도록 줄어듦. gold = 지금 받으면 됨 */
    private fun drawRing(c: Canvas, target: Long, span: Long, gold: Boolean) {
        val f = ((target - now).toFloat() / span).coerceIn(-0.2f, 1f)
        val rad = handR * (RING_MIN + (RING_MAX - RING_MIN) * f.coerceAtLeast(0f)) + handR * f.coerceAtMost(0f)
        ring.strokeWidth = (if (gold) 5f else 3f) * d
        ring.color = if (gold) Theme.GOLD else 0xCCFFFFFF.toInt()
        c.drawCircle(handX, handY - handR * 0.4f, rad, ring)
        // 목표 고리 (손 크기)
        ring.strokeWidth = 1.5f * d
        ring.color = if (gold) Theme.GOLD_LIGHT else 0x66FFFFFF
        c.drawCircle(handX, handY - handR * 0.4f, handR * RING_MIN, ring)
        if (gold) GonggiArt.shout(c, NOW_TEXT, handX - handR * 2.9f, handY - handR * 0.2f, 17f * d, Theme.GOLD)
    }

    /** 손 오른쪽: 이번에 집을 돌 수 (빈 동그라미) / 집은 수 (채움) */
    private fun drawPicks(c: Canvas, req: Int, got: Int) {
        if (req <= 0) return
        val r = 6f * d
        val gap = 4f * d
        val w = PICK_LABEL_W * d + req * (r * 2f + gap) + 8f * d
        val x0 = handX + handR * 2.3f
        val y = handY + handR * 0.2f
        tmp.set(x0, y - 14f * d, x0 + w, y + 14f * d)
        p.style = Paint.Style.FILL
        p.color = 0xB31A1030.toInt()
        c.drawRoundRect(tmp, 14f * d, 14f * d, p)
        label.textSize = 13f * d
        c.drawText(PICK_TEXT, x0 + 8f * d + PICK_LABEL_W * d / 2f - 4f * d, y + 4.6f * d, label)
        var x = x0 + PICK_LABEL_W * d + 4f * d + r
        for (k in 0 until req) {
            if (k < got) {
                p.color = Theme.GOLD
                c.drawCircle(x, y, r, p)
            } else {
                ring.strokeWidth = 1.6f * d
                ring.color = Theme.GOLD_LIGHT
                c.drawCircle(x, y, r * 0.85f, ring)
            }
            x += r * 2f + gap
        }
    }

    companion object {
        /** 터치 판정 반지름 = 돌 반지름 × 이만큼 */
        const val HIT_SCALE = 1.8f
        private const val MAT_EDGE = 0xFF9C2F3A.toInt()
        private const val ASIDE_U0 = 0.12f
        private const val ASIDE_DU = 0.11f
        private const val ASIDE_V = 0.92f
        private const val RING_MIN = 1.45f
        private const val RING_MAX = 3.6f
        /** 꺾기 손등 고리가 금색인 범위 (돌 4개 이상) */
        private val KK_GOLD_MS = GameConfig.KK_BACK_THRESHOLDS[3]
        private const val PICK_LABEL_W = 34f
        private const val NOW_TEXT = "지금!"
        private const val PICK_TEXT = "집기"
        /** 손 위 돌 자리 (돌 반지름 단위) */
        private val CLUSTER_X = floatArrayOf(0f, -1.7f, 1.7f, -0.85f, 0.85f)
        private val CLUSTER_Y = floatArrayOf(0f, -0.6f, -0.6f, 1.0f, 1.0f)
    }
}
