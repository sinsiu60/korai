package com.siwoo.party.games.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PartyTag
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.drawing.ui.DrawCanvasView
import com.siwoo.party.games.drawing.ui.DrawingActivity
import com.siwoo.party.games.drawing.ui.DrawingArt
import com.siwoo.party.games.drawing.ui.DrawingStore

/** 그림 맞히기: 모음집 등록 정보 */
object DrawingModule : GameModule {
    override val id = "drawing"
    override val name = "그림 맞히기"
    override val tagline = "80초 안에 그려라! 친구들은 말로 정답을 외쳐요"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.PARTY
    override val partyTags = listOf(PartyTag.TALK)
    override val minutes = "10분"
    override val color = 0xFF3FB8FF.toInt()
    override val activityClass = DrawingActivity::class.java

    /** 허브 소개 팝업의 [제시어 편집] → 공통 ListEditor (카테고리 6탭) */
    override val settingsLabel: String get() = "제시어 편집"

    override fun showSettings(popups: PopupLayer) = DrawingStore.showEditor(popups)

    private val path = Path()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF1E5FA8.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.Party.NEON_SKY
        canvas.drawCircle(cx, cy, r * 0.9f, paint)
        DrawingArt.drawEasel(canvas, cx - r * 0.08f, cy + r * 0.02f, r * 1.05f, r * 1.1f, paint)
        DrawingArt.drawDoodleCat(canvas, cx - r * 0.08f, cy - r * 0.06f, r * 0.42f, DrawCanvasView.PEN_COLORS[1], paint, path)
        DrawingArt.drawPencil(canvas, cx + r * 0.32f, cy + r * 0.1f, r * 0.75f, -50f, paint, path)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textSize = size
        p.isFakeBoldText = true
        p.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
    }

    private fun bubble(c: Canvas, x: Float, y: Float, w: Float, h: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.PAPER
        c.drawRoundRect(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f, h * 0.35f, h * 0.35f, p)
        path.reset()
        path.moveTo(x - w * 0.1f, y + h / 2f - 1f)
        path.lineTo(x - w * 0.2f, y + h * 0.95f)
        path.lineTo(x + w * 0.08f, y + h / 2f - 1f)
        path.close()
        c.drawPath(path, p)
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("몰래 제시어 확인 → 80초 그리기", "그리는 사람만 화면을 꾹 눌러 제시어를 봐요.\n80초 동안 손가락으로 그림! 글자·숫자는 쓰면 안 돼요.\n도구: 색 4개 · 굵기 2단계 · 지우개 · 되돌리기 · 전체 지우기") { c, w, h, p ->
            DrawingArt.drawEasel(c, w * 0.32f, h * 0.5f, w * 0.42f, h * 0.7f, p)
            DrawingArt.drawDoodleCat(c, w * 0.32f, h * 0.4f, h * 0.18f, DrawCanvasView.PEN_COLORS[0], p, path)
            DrawingArt.drawPencil(c, w * 0.46f, h * 0.55f, h * 0.4f, -40f, p, path)
            // 색 4개
            for (k in 0 until 4) {
                p.style = Paint.Style.FILL
                p.color = DrawCanvasView.PEN_COLORS[k]
                c.drawCircle(w * 0.72f + (k % 2) * w * 0.12f, h * 0.3f + (k / 2) * h * 0.22f, h * 0.08f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.015f
                p.color = Theme.TEXT
                c.drawCircle(w * 0.72f + (k % 2) * w * 0.12f, h * 0.3f + (k / 2) * h * 0.22f, h * 0.08f, p)
            }
            text(c, "80초", w * 0.78f, h * 0.86f, h * 0.14f, Theme.GOLD, p)
        },
        RuleCard("말로 외쳐서 맞히기", "나머지 사람들은 그림을 보고 정답을 큰 소리로 외쳐요.\n맞히면 그린 사람이 화면 아래 맞힌 사람 이름을 터치!\n맞힌 사람 +2점, 그린 사람 +1점.") { c, w, h, p ->
            DrawingArt.drawEasel(c, w * 0.2f, h * 0.5f, w * 0.28f, h * 0.6f, p)
            DrawingArt.drawDoodleCat(c, w * 0.2f, h * 0.42f, h * 0.13f, DrawCanvasView.PEN_COLORS[2], p, path)
            DrawingArt.drawPerson(c, w * 0.52f, h * 0.62f, h * 0.22f, Theme.PLAYER_COLORS[1], p)
            DrawingArt.drawPerson(c, w * 0.8f, h * 0.62f, h * 0.22f, Theme.PLAYER_COLORS[2], p)
            bubble(c, w * 0.55f, h * 0.2f, w * 0.24f, h * 0.18f, p)
            text(c, "고양이!", w * 0.55f, h * 0.23f, h * 0.09f, Theme.INK, p)
            text(c, "+2", w * 0.52f, h * 0.95f, h * 0.12f, Theme.GOLD, p)
            text(c, "+1", w * 0.2f, h * 0.95f, h * 0.12f, Theme.GOLD_LIGHT, p)
        },
        RuleCard("각자 2번씩 그리기", "시간이 끝나면 정답을 공개해요 (점수 없음).\n모두 2번씩 그리면 합계 점수로 순위! 같은 점수는 같은 등수.\n제시어는 허브의 [제시어 편집]에서 추가할 수 있어요.") { c, w, h, p ->
            for (k in 0 until 3) {
                val x = w * (0.2f + 0.3f * k)
                DrawingArt.drawPerson(c, x, h * 0.42f, h * 0.2f, Theme.PLAYER_COLORS[k], p)
                for (j in 0 until 2) {
                    DrawingArt.drawPencil(c, x - w * 0.07f + j * w * 0.09f, h * 0.84f, h * 0.2f, -70f, p, path)
                }
            }
            text(c, "× 2바퀴", w * 0.5f, h * 0.16f, h * 0.13f, Theme.GOLD, p)
        },
    )
}
