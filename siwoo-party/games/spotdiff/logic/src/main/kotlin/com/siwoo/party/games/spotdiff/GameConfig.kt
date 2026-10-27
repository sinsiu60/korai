package com.siwoo.party.games.spotdiff

/** 틀린 그림 찾기 대결 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 게임 규칙 ──
    /** 장면 수 (합산 점수로 순위) */
    const val SCENES = 3
    /** 장면마다 차이 수 */
    const val DIFFS_PER_SCENE = 5
    /** 맞히면 + */
    const val CORRECT_POINTS = 1
    /** 틀리거나 시간 안에 못 누르면 - */
    const val WRONG_PENALTY = 1
    /** 버저를 누른 뒤 그림을 터치해야 하는 시간 (게임 규칙이라 속도 배율 적용 안 함) */
    const val ANSWER_MS = 3000L
    /** 오답·시간 초과 뒤 버저 잠금 (게임 규칙) */
    const val LOCK_MS = 2000L
    /** 한 장면 제한 시간: 지나면 남은 차이를 보여 주고 다음 장면 (게임 규칙) */
    const val SCENE_TIME_LIMIT_MS = 90_000L

    // ── 장면 만들기 (그림 좌표 단위) ──
    /** 그림 좌표 폭·높이 (화면에서는 비율만 유지해 늘린다) */
    const val SCENE_W = 400f
    const val SCENE_H = 360f
    /** 하늘과 땅의 경계 y */
    const val HORIZON_Y = 200f
    /** 물체 수 범위 */
    const val MIN_OBJECTS = 12
    const val MAX_OBJECTS = 20
    /** 물체끼리 띄우는 최소 간격 (겹치지 않게) */
    const val OBJECT_GAP = 4f
    /** 차이가 될 수 있는 물체의 최소 변 길이·넓이 (너무 작으면 안 보임) */
    const val DIFF_MIN_SIDE = 20f
    const val DIFF_MIN_AREA = 900f
    /** 크기 변경 배율 (줄이기 / 키우기) — 둘 다 30% 넘게 달라짐 */
    const val SHRINK = 0.6f
    const val GROW = 1.4f
    /** 색 바꾸기: 원래 색과 RGB 거리가 이 이상인 색만 (확실히 다른 색) */
    const val MIN_COLOR_DIST = 140.0
    /** 정답 판정 반경 = max(차이 영역 반지름, MIN_HIT_RADIUS) + HIT_SLOP (그림 좌표) */
    const val MIN_HIT_RADIUS = 20f
    const val HIT_SLOP = 8f

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 (뒤에 3·2·1) */
    const val INTRO_MS = 1500L
    /** 장면 시작 배너 (그동안 버저 막힘) */
    const val SCENE_INTRO_MS = 1300L
    /** 장면 완료 배너 → 다음 장면 */
    const val SCENE_CLEAR_MS = 1800L
    /** 시간 끝: 남은 차이 공개 */
    const val REVEAL_MS = 2600L
    /** 동그라미가 커지며 나타나는 시간 */
    const val CIRCLE_POP_MS = 380L
    /** 오답 X·점수 떠오름 */
    const val FLASH_MS = 700L
    /** 마지막 결과 배너 */
    const val END_SHOW_MS = 2000L
}
