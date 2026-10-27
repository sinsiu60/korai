package com.siwoo.party.common.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.PlayerInfo

/** 규칙 카드 한 장: 제목 + 설명 + 코드로 그리는 그림 (w×h 영역에 그린다) */
class RuleCard(
    val title: String,
    val text: String,
    val draw: RuleArt,
)

/** 규칙 카드 그림 (float 을 상자에 담지 않도록 람다 대신 fun interface) */
fun interface RuleArt {
    operator fun invoke(canvas: Canvas, w: Float, h: Float, paint: Paint)
}

/** 조작 방식 표시 */
enum class PlayMode(val label: String) {
    SIMULTANEOUS("동시 터치"),
    TURNS("차례대로"),
}

/**
 * 모음집에 들어가는 게임 하나. 허브는 이 정보로 카드·소개·규칙을 보여 주고
 * activityClass 를 Intent(플레이어 목록 포함)로 띄운 뒤, 돌려받은 순위로 점수를 준다.
 * (실행·결과 규약은 Party 참고)
 */
interface GameModule {
    /** 저장·기록용 고유 이름 (영문) */
    val id: String
    val name: String
    /** 한 줄 소개 */
    val tagline: String
    /** 할 수 있는 인원 */
    val playerCounts: IntRange
    val mode: PlayMode
    /** 예상 시간(분) */
    val minutes: String
    /** 카드 대표 색 */
    val color: Int
    val activityClass: Class<out Activity>
    /** 규칙 카드 3장 */
    val rules: List<RuleCard>

    /** 카드 아이콘: (cx, cy) 중심, 한 변 size 영역에 그린다 (Canvas, 할당 없이) */
    fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint)

    /** 이 플레이어들로 이어할 수 있는 저장 게임이 있는지 (허브 카드에 "이어하기") */
    fun hasSavedGame(context: Context, players: List<PlayerInfo>): Boolean = false

    /** 모임이 끝날 때 저장 게임 지우기 */
    fun clearSave(context: Context) {}

    /** 게임 전용 설정이 있으면 버튼 글자 (허브 소개 팝업에 [게임 설정]) — 없으면 null */
    val settingsLabel: String? get() = null

    /** 게임 전용 설정 팝업 (폭탄 주제·라이어 제시어·윷 말 수 등) */
    fun showSettings(popups: PopupLayer) {}

    /** 이 인원으로 할 수 있는지 (2·4인처럼 띄엄띄엄이면 override) */
    fun supports(count: Int): Boolean = count in playerCounts

    /** 카드·소개에 쓰는 인원 글자 (예: "2~4인", "2인", "2·4인") */
    val playersLabel: String
        get() = if (playerCounts.first == playerCounts.last) "${playerCounts.first}인" else "${playerCounts.first}~${playerCounts.last}인"

    /** 이 인원으로 할 수 없을 때 이유 (되면 null) */
    fun unavailableReason(count: Int): String? =
        if (supports(count)) null
        else if (playerCounts.first == playerCounts.last) "${playerCounts.first}인 전용"
        else "${playersLabel}용"
}
