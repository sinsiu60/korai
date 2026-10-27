package com.siwoo.party.common.lane

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme

/**
 * 물체 하나를 그리는 방법 (게임이 노트·계란·폭탄 등을 직접 그림). 캔버스는 이미 구역 안 좌표로 돌려져 있다.
 * 할당 금지 (Paint·Path 는 필드로).
 */
interface LaneItemPainter {
    /**
     * (cx, cy) 가운데에 크기 size(지름 정도) 로 물체를 그린다.
     * state = ItemState (PENDING 이 보통, MISS·DROPPED 는 판정선을 지나 떨어지는 중 — 흐리게 그리기 등).
     * 맞춘·받은 물체(PERFECT/GOOD/CAUGHT)는 부르지 않는다.
     */
    fun drawItem(c: Canvas, d: LaneDrawer, kind: Int, state: Int, cx: Float, cy: Float, size: Float)

    /** 물체 위에 덧그리기 (바구니·점수 등, 구역 안 좌표). 기본: 없음 */
    fun drawOverlay(c: Canvas, d: LaneDrawer, track: LaneTrack, nowMs: Long) {}
}

/**
 * 구역 하나의 세로 레인 그리기 (할당 없음): 레인 바닥 줄무늬, 판정선(금색 빛), 레인 번쩍임(판정 때),
 * 떨어지는 물체(painter), 판정 글자(퍼펙트!/좋아!/놓침) + 콤보 숫자.
 * 구역의 rotation(위쪽 구역 180°)대로 돌려 그리므로 모든 글자·물체 방향이 그 플레이어 쪽이다.
 *
 * 쓰는 법: setZone(zone, lanes) → 매 프레임 draw(canvas, track, now).
 * 터치: localX(x, y) / laneAt(x, y) / laneUnitsAt(x, y) 로 화면 좌표를 레인 좌표로.
 */
class LaneDrawer(private val context: Context) {
    val frame = ZoneFrame()
    val layout = LaneLayout()

    /** 플레이어 색 (레인·기본 노트) */
    var color = Theme.GOLD
    /** 판정선 위치 (구역 높이 비율) */
    var judgeFrac = 0.8f
    /** 판정선 위 레인마다 과녁 고리 (리듬) */
    var showTargets = true
    /** 판정 글자·콤보 표시 (리듬) */
    var showJudgement = true
    /** 콤보가 이 이상일 때 콤보 숫자 표시 */
    var minComboShown = 2
    /** 물체 크기 (레인 폭 배수, 최대 maxItemDp) */
    var itemScale = 0.72f
    var maxItemDp = 46f
    /** 물체 그리기 (null 이면 기본 둥근 노트) */
    var painter: LaneItemPainter? = null
    /**
     * 레인 영역 밑에 까는 어두운 바탕의 불투명도 (0..255). 아래 MultiTouchZoneView 의 가운데 이름 글자가
     * 레인 위로 비쳐 노트·판정 글자와 겹치지 않게 가린다. 0 = 투명.
     */
    var laneShade = 235
    /** 구역 왼쪽 위 모서리 작은 이름표 (null = 없음; 바탕이 이름을 가리므로 보통 플레이어 이름) */
    var label: String? = null

    private val dp = Theme.dpf(context, 1f)
    private var inset = 0f
    private var laneCount = 1
    private var maxLaneWidth = 0f
    private val flashAt = LongArray(MAX_LANES) { Long.MIN_VALUE }
    private val flashColor = IntArray(MAX_LANES)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
        color = Theme.BG_DEEP
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val comboChars = CharArray(16)

    /** 구역(MultiTouchZoneView.zone(i) 또는 TouchZones.layout) 에 맞춘다. inset = 구역 테두리 안쪽 여백(px) */
    fun setZone(zone: Zone, lanes: Int, insetPx: Float = Theme.dpf(context, 8f), maxLaneWidthPx: Float = 0f) {
        inset = insetPx
        laneCount = lanes.coerceIn(1, MAX_LANES)
        maxLaneWidth = maxLaneWidthPx
        frame.set(zone, inset)
        relayout()
    }

    /** 판정선 위치 등을 바꾼 뒤 다시 배치 */
    fun relayout() {
        layout.layout(frame, laneCount, judgeFrac, sidePad = 4f * dp, topPad = 0f, maxLaneWidth = maxLaneWidth)
        val big = (frame.height * 0.11f).coerceIn(14f * dp, 30f * dp)
        textPaint.textSize = big
        outline.textSize = big
        outline.strokeWidth = big * 0.18f
        labelPaint.textSize = (big * 0.6f).coerceAtLeast(12f * dp)
    }

    /** 레인 번쩍임 (판정 순간 부름): PERFECT 금색, GOOD 초록, MISS 빨강 */
    fun flash(lane: Int, j: Judgement, nowMs: Long) {
        if (lane !in 0 until laneCount) return
        flashAt[lane] = nowMs
        flashColor[lane] = when (j) {
            Judgement.PERFECT -> Theme.GOLD
            Judgement.GOOD -> Theme.GREEN
            Judgement.MISS -> Theme.RED
            Judgement.NONE -> Theme.TEXT_SUB
        }
    }

    /** 다른 색으로 레인 번쩍임 (받기 게임: 황금 계란 등) */
    fun flashColor(lane: Int, color: Int, nowMs: Long) {
        if (lane !in 0 until laneCount) return
        flashAt[lane] = nowMs
        flashColor[lane] = color
    }

    // ---------- 좌표 (화면 → 구역 안) ----------
    fun localX(x: Float, y: Float): Float = frame.toLocalX(x, y)
    fun localY(x: Float, y: Float): Float = frame.toLocalY(x, y)
    /** 화면 터치 → 레인 번호 (가장 가까운 레인으로 자름) */
    fun laneAt(x: Float, y: Float): Int = layout.laneAt(frame.toLocalX(x, y))
    /** 화면 터치 → 레인 단위 가로 위치 (바구니: 0..lanes) */
    fun laneUnitsAt(x: Float, y: Float): Float = layout.laneUnits(frame.toLocalX(x, y))
    /** 물체 그리기 크기 (px) */
    val itemSize: Float get() = minOf(layout.laneWidth * itemScale, maxItemDp * dp)

    /** 구역 안 좌표로 돌린 캔버스 위에서 그린다 */
    fun draw(c: Canvas, track: LaneTrack, nowMs: Long) {
        val w = frame.width
        val h = frame.height
        if (w <= 0f || h <= 0f) return
        c.save()
        c.translate(frame.centerX, frame.centerY)
        c.rotate(frame.rotation)
        c.translate(-w / 2f, -h / 2f)
        c.clipRect(0f, 0f, w, h)
        val lw = layout.laneWidth
        val jy = layout.judgeY

        // 어두운 바탕 (아래 구역 글자 가리기)
        if (laneShade > 0) {
            rect.set(layout.laneLeft, 0f, layout.laneRight, h)
            fill.color = Theme.BG_DEEP
            fill.alpha = laneShade
            c.drawRoundRect(rect, 10f * dp, 10f * dp, fill)
        }
        // 레인 바닥: 번갈아 옅은 플레이어 색
        for (k in 0 until laneCount) {
            val l = layout.laneLeft + k * lw
            rect.set(l + dp, 0f, l + lw - dp, h)
            fill.color = color
            fill.alpha = if (k % 2 == 0) 26 else 40
            c.drawRoundRect(rect, 8f * dp, 8f * dp, fill)
            // 번쩍임
            val age = nowMs - flashAt[k]
            if (age >= 0 && age < FLASH_MS) {
                val f = 1f - age.toFloat() / FLASH_MS
                fill.color = flashColor[k]
                fill.alpha = (110 * f).toInt()
                rect.set(l + dp, jy - h * 0.45f, l + lw - dp, h)
                c.drawRoundRect(rect, 8f * dp, 8f * dp, fill)
            }
        }
        // 레인 구분선
        stroke.color = color
        stroke.alpha = 70
        stroke.strokeWidth = 1.5f * dp
        for (k in 1 until laneCount) {
            val x = layout.laneLeft + k * lw
            c.drawLine(x, 0f, x, h, stroke)
        }

        // 판정선: 빛 + 금색 줄
        stroke.color = Theme.GOLD
        stroke.alpha = 60
        stroke.strokeWidth = 12f * dp
        c.drawLine(layout.laneLeft + 4f * dp, jy, layout.laneRight - 4f * dp, jy, stroke)
        stroke.alpha = 255
        stroke.strokeWidth = 3.5f * dp
        c.drawLine(layout.laneLeft + 4f * dp, jy, layout.laneRight - 4f * dp, jy, stroke)
        val size = itemSize
        if (showTargets) {
            stroke.strokeWidth = 2.5f * dp
            stroke.color = Theme.GOLD_LIGHT
            stroke.alpha = 200
            for (k in 0 until laneCount) c.drawCircle(layout.laneCenterX(k), jy, size * 0.52f, stroke)
        }
        stroke.alpha = 255

        // 떨어지는 물체 (먼저 나온 것이 아래 → 나중 것부터 그려 겹칠 때 아래 것이 위)
        val p = painter
        val from = track.drawFrom(nowMs)
        var i = track.drawTo(nowMs) - 1
        while (i >= from) {
            val st = track.state(i)
            if (!ItemState.isTaken(st)) {
                val pos = track.position(i, nowMs)
                val y = layout.itemY(pos)
                if (pos >= 0f && y - size < h) {
                    val pat = track.pattern
                    val x = layout.laneCenterX(pat.lane(i))
                    if (p != null) p.drawItem(c, this, pat.kind(i), st, x, y, size)
                    else drawNote(c, st, x, y, size)
                }
            }
            i--
        }

        p?.drawOverlay(c, this, track, nowMs)

        val name = label
        if (name != null) {
            labelPaint.color = color
            outline.textAlign = Paint.Align.LEFT
            val ow = outline.strokeWidth
            val os = outline.textSize
            outline.textSize = labelPaint.textSize
            outline.strokeWidth = labelPaint.textSize * 0.2f
            val ly = labelPaint.textSize + 4f * dp
            c.drawText(name, 6f * dp, ly, outline)
            c.drawText(name, 6f * dp, ly, labelPaint)
            outline.textAlign = Paint.Align.CENTER
            outline.strokeWidth = ow
            outline.textSize = os
        }

        // 판정 글자 + 콤보
        if (showJudgement) drawJudgement(c, track, nowMs)
        c.restore()
    }

    /** 기본 노트: 플레이어 색 둥근 알약 + 흰 윤곽 + 윗면 밝은 띠 */
    fun drawNote(c: Canvas, state: Int, cx: Float, cy: Float, size: Float) {
        val hw = size * 0.55f
        val hh = size * 0.26f
        val missed = state == ItemState.MISS || state == ItemState.DROPPED
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
        fill.color = if (missed) Theme.GRAY else color
        fill.alpha = if (missed) 150 else 255
        c.drawRoundRect(rect, hh, hh, fill)
        fill.color = Theme.TEXT
        fill.alpha = if (missed) 40 else 110
        rect.set(cx - hw * 0.75f, cy - hh * 0.7f, cx + hw * 0.75f, cy - hh * 0.1f)
        c.drawRoundRect(rect, hh * 0.3f, hh * 0.3f, fill)
        stroke.color = Theme.TEXT
        stroke.alpha = if (missed) 90 else 230
        stroke.strokeWidth = 2f * dp
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
        c.drawRoundRect(rect, hh, hh, stroke)
        stroke.alpha = 255
    }

    private fun drawJudgement(c: Canvas, track: LaneTrack, nowMs: Long) {
        val j = track.lastJudgement
        val age = nowMs - track.lastJudgeTime
        val cx = (layout.laneLeft + layout.laneRight) / 2f
        // 판정선 과녁 고리 바로 위
        val ty = layout.judgeY - itemSize * 0.52f - 8f * dp
        if (j != Judgement.NONE && age >= 0 && age < TEXT_MS) {
            val f = 1f - age.toFloat() / TEXT_MS
            val label = when (j) {
                Judgement.PERFECT -> PERFECT_TEXT
                Judgement.GOOD -> GOOD_TEXT
                else -> MISS_TEXT
            }
            textPaint.color = when (j) {
                Judgement.PERFECT -> Theme.GOLD
                Judgement.GOOD -> Theme.GREEN
                else -> Theme.RED
            }
            val a = (255 * minOf(1f, f * 2f)).toInt()
            textPaint.alpha = a
            outline.alpha = a
            val rise = (1f - f) * 10f * dp
            c.drawText(label, cx, ty - rise, outline)
            c.drawText(label, cx, ty - rise, textPaint)
            textPaint.alpha = 255
            outline.alpha = 255
        }
        val combo = track.combo
        if (combo >= minComboShown) {
            val n = writeCombo(combo)
            // 레인 위쪽 (판정 글자와 떨어진 곳), 살짝 투명
            val old = textPaint.textSize
            val ow = outline.strokeWidth
            textPaint.textSize = old * 0.7f
            outline.textSize = old * 0.7f
            outline.strokeWidth = ow * 0.7f
            val y = layout.spawnY + textPaint.textSize * 2.2f + 4f * dp
            textPaint.color = Theme.GOLD_LIGHT
            textPaint.alpha = 220
            outline.alpha = 200
            c.drawText(comboChars, 0, n, cx, y, outline)
            c.drawText(comboChars, 0, n, cx, y, textPaint)
            textPaint.alpha = 255
            outline.alpha = 255
            textPaint.textSize = old
            outline.textSize = old
            outline.strokeWidth = ow
        }
    }

    /** comboChars 에 "12 콤보" 를 쓰고 길이를 돌려준다 (할당 없음) */
    private fun writeCombo(v: Int): Int {
        var x = v.coerceIn(0, 99_999)
        var digits = 1
        var t = x
        while (t >= 10) { t /= 10; digits++ }
        for (k in digits - 1 downTo 0) { comboChars[k] = ('0' + x % 10); x /= 10 }
        var n = digits
        for (ch in COMBO_SUFFIX) comboChars[n++] = ch
        return n
    }

    companion object {
        /** 한 구역 최대 레인 수 */
        const val MAX_LANES = 8
        const val FLASH_MS = 220L
        const val TEXT_MS = 520L
        const val PERFECT_TEXT = "퍼펙트!"
        const val GOOD_TEXT = "좋아!"
        const val MISS_TEXT = "놓침"
        private const val COMBO_SUFFIX = " 콤보"
    }
}
