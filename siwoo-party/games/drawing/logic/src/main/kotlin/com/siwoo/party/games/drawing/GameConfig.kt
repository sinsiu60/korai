package com.siwoo.party.games.drawing

/** 그림 맞히기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 4

    // ── 차례·점수 ──
    /** 각자 그리는 횟수 */
    const val DRAWS_PER_PLAYER = 2
    /** 맞힌 사람 +2 */
    const val POINTS_GUESSER = 2
    /** 그린 사람 +1 (누군가 맞혔을 때만) */
    const val POINTS_DRAWER = 1

    // ── 규칙 시간 (속도 배율 적용 안 함) ──
    /** 그리는 시간 80초 */
    const val DRAW_MS = 80_000L

    // ── 제시어 ──
    const val CATEGORY_COUNT = 6
    const val DEFAULT_WORDS = 30
    const val MIN_WORDS = 5
    const val MAX_WORDS = 60
    const val MAX_WORD_LEN = 10

    // ── 그리기 도구 ──
    /** 색 4개 (검정·빨강·파랑·초록) — 실제 색은 화면 쪽 */
    const val COLOR_COUNT = 4
    /** 지우개 = 색 번호 COLOR_COUNT (종이색으로 칠함) */
    const val ERASER = COLOR_COUNT
    /** 굵기 2단계: 캔버스 높이 대비 선 굵기 (가는 / 굵은), 지우개는 굵은 것의 ERASER_SCALE 배 */
    const val WIDTH_THIN = 0.012f
    const val WIDTH_THICK = 0.032f
    const val ERASER_SCALE = 2.2f

    // ── 선 저장 한도 (성능) ──
    /** 되돌리기용으로 기억하는 선 수. 넘으면 가장 오래된 선은 바탕 그림에 굳힌다(되돌리기 불가) */
    const val MAX_STROKES = 120
    /** 선 하나 최대 점 수. 넘으면 그 자리에서 선을 끝내고 이어서 새 선으로 */
    const val MAX_POINTS_PER_STROKE = 256
    /** 모든 선의 점 합계 한도 (float 2개 = 점 1개) */
    const val MAX_TOTAL_POINTS = 12_000
    /** 이전 점과 이 거리(캔버스 높이 대비)보다 가까운 점은 버림 */
    const val MIN_POINT_DIST = 0.004f

    // ── 연출 (속도 배율 적용) ──
    const val TURN_INTRO_MS = 1300L
    /** 정답·시간 끝 배너 */
    const val RESULT_BANNER_MS = 1800L
    /** 맞힌 사람 칩에서 별이 날아가는 시간 */
    const val STAR_FLY_MS = 800L
    /** 게임 끝 배너 */
    const val END_MS = 2200L
}
