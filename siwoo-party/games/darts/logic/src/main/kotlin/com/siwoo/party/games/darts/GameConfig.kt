package com.siwoo.party.games.darts

/**
 * 다트 수치. 길이는 mm (다트판 가운데 = (0, 0), x 오른쪽 +, y 아래쪽 +), 시간은 ms.
 * 다트판 크기는 정식 규격(더블 바깥 반지름 170mm)을 그대로 쓴다.
 * 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. 규칙·조준 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 경기 ──
    /** 한 차례에 던지는 다트 수 */
    const val DARTS_PER_TURN = 3
    /** 라운드 점수제: 라운드 수 (8라운드 × 3발 합계로 순위) */
    const val ROUNDS = 8
    /** 301: 시작 점수 (0 을 정확히 만들면 끝, 더블 아웃 없음) */
    const val X01_START = 301
    /** 301: 최대 라운드 — 이때까지 아무도(또는 일부만) 못 끝내면 남은 점수가 적은 순 */
    const val X01_MAX_ROUNDS = 15

    // ── 다트판 (정식 규격, 가운데에서의 반지름 mm) ──
    /** 불 (이너 불, 50점) */
    const val R_BULL = 6.35f
    /** 아우터 불 (25점) */
    const val R_OUTER_BULL = 15.9f
    /** 트리플 고리 안쪽·바깥쪽 */
    const val R_TREBLE_IN = 99f
    const val R_TREBLE_OUT = 107f
    /** 더블 고리 안쪽·바깥쪽 (= 점수 나는 판의 끝) */
    const val R_DOUBLE_IN = 162f
    const val R_DOUBLE_OUT = 170f
    /** 숫자 고리 (그림용): 숫자 글자 가운데 반지름, 검은 숫자 고리 바깥 반지름 */
    const val R_NUMBERS = 188f
    const val R_NUMBER_RING = 206f
    /** 나무 테두리 바깥 (그림 전체 반지름) */
    const val R_FRAME = 218f

    /** 위(12시)부터 시계 방향 숫자 순서 (정식 다트판) */
    val SECTORS = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)
    /** 한 칸의 각도 (도) */
    const val SECTOR_DEG = 18f

    // ── 조준 (흔들리는 조준점, common/aim Sway: 가로 8자) ──
    /** 8자 가로·세로 크기 (mm, 가운데에서 끝까지) */
    const val SWAY_AMP_X = 150f
    const val SWAY_AMP_Y = 118f
    /** 8자 한 바퀴 시간 */
    const val SWAY_PERIOD_MS = 3000L
    /** 작은 떨림 크기 (mm) · 바뀌는 간격 */
    const val SWAY_JITTER = 7f
    const val SWAY_JITTER_PERIOD_MS = 380L
    /** 다트마다 8자를 이만큼까지 무작위로 돌린다 (±도) — 지나가는 칸이 매번 달라짐 */
    const val AIM_ROTATE_MAX_DEG = 35f
    /** 라운드가 지날수록 흔들림이 커짐: 라운드마다 +비율, 최대 배율 */
    const val SWAY_GROWTH_PER_ROUND = 0.03f
    const val SWAY_GROWTH_MAX = 1.21f
    /** 던진 다트가 조준점에서 빗나가는 정도 (mm, 이 반지름 원 안에 고르게) */
    const val SCATTER_MM = 6f
    /** 8자 궤적 미리 보기 점 수 */
    const val TRAIL_POINTS = 72

    // ── 연출 (ms, 화면에서 t() 적용) ──
    const val INTRO_MS = 1700L
    const val TURN_BANNER_MS = 1100L
    /** 다트가 날아가 꽂히는 시간 */
    const val FLIGHT_MS = 280L
    /** 꽂힌 뒤 다음 다트까지 */
    const val AFTER_DART_MS = 650L
    /** 점수 글자 떠오르기 */
    const val HIT_TEXT_MS = 1100L
    /** 차례 끝 → 회수 시작까지 (꽂힌 다트 감상) */
    const val TURN_END_PAUSE_MS = 900L
    /** 회수 연출 (다트 셋이 차례로 빠져 날아감) */
    const val RETRIEVE_MS = 650L
    const val RETRIEVE_STAGGER_MS = 110L
    /** 180 · 불 · 버스트 · 완료 배너 */
    const val BIG_BANNER_MS = 1300L
    const val END_BANNER_MS = 2000L
    /** 프레임 사이 최대 간격 (일시정지 등에서 크게 건너뛰지 않게) */
    const val MAX_FRAME_MS = 50L
}
