package com.siwoo.party.games.archery

/**
 * 양궁 수치. 길이는 "고리 단위"(과녁 고리 한 칸 폭 = 1, 과녁 반지름 = 10), 시간은 ms.
 * 좌표는 과녁 가운데가 (0, 0), x 오른쪽 +, y 아래쪽 + (화면과 같은 방향).
 * 연출 시간(…_MS 중 연출용)은 화면에서 공통 속도 배율 t() 가 곱해진다. 흔들림·숨 참기 시간은 규칙이라 배율 없음.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 1인당 화살 수 — 한 발씩 돌아가며 쏜다 (1번 1발 → 2번 1발 → … → 1번 2발 …) */
    const val ARROWS = 6

    // ── 과녁 (10점 표적: 금 10·9, 빨강 8·7, 파랑 6·5, 검정 4·3, 흰색 2·1) ──
    /** 고리 수 = 과녁 반지름 (고리 단위) */
    const val RINGS = 10
    const val FACE_R = 10f
    /** 가운데 X 링 반지름 (10점 고리 안쪽 절반) — X 는 10점 + 동점 가르기 + "엑스 텐!" */
    const val X_R = 0.5f
    /** 화살 반지름: 화살이 선에 걸치면 안쪽(높은) 점수 (실제 양궁 규칙) */
    const val ARROW_R = 0.05f

    // ── 조준점 흔들림 (공통 aim.Sway, 가로 8자) ──
    const val SWAY_AMP_X = 3.0f
    const val SWAY_AMP_Y = 2.1f
    const val SWAY_PERIOD_MS = 2600L
    /** 작은 떨림 크기·주기 */
    const val SWAY_JITTER = 0.5f
    const val SWAY_JITTER_MS = 300L

    // ── 숨 참기 (공통 aim.BreathHold): 누르고 있으면 흔들림 배율 1 → BREATH_MIN (BREATH_CALM_MS 동안),
    //    BREATH_LIMIT_MS 까지 유지, 그 뒤 BREATH_GROW_MS 동안 BREATH_MAX 까지 커짐 ──
    const val BREATH_MIN = 0.2f
    const val BREATH_CALM_MS = 1000L
    const val BREATH_LIMIT_MS = 2000L
    const val BREATH_GROW_MS = 1500L
    const val BREATH_MAX = 2.2f

    // ── 조준 옮기기 (누른 채 끌기 — 바람 보정) ──
    /** 조준 가운데가 과녁 가운데에서 벗어날 수 있는 최대 거리 */
    const val AIM_LIMIT = 13f
    /** 손가락이 움직인 거리 × 이 값 = 조준이 움직인 거리 (화면 px 기준, 세밀하게) */
    const val AIM_DRAG_GAIN = 0.5f

    // ── 바람 (한 발마다 바뀜) ──
    /** 바람 세기 범위 (0.1 m/s 단위): 0.5 ~ 4.0 m/s */
    const val WIND_MIN10 = 5
    const val WIND_MAX10 = 40
    /** 1 m/s 마다 화살이 밀리는 거리 (고리 단위) — 4 m/s 면 2.8 고리 */
    const val WIND_DRIFT = 0.7f
    /** 바람 방향 간격 (도) — 0 = 오른쪽, 90 = 아래, 180 = 왼쪽, 270 = 위 */
    const val WIND_STEP_DEG = 15

    // ── 흩어짐 ──
    /** 놓는 순간의 작은 오차: 반지름 SCATTER 원 안 아무 곳 (시드로 결정) */
    const val SCATTER = 0.3f

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 2000L
    const val TURN_BANNER_MS = 900L
    /** 화살이 날아가는 시간 */
    const val FLIGHT_MS = 420L
    /** 꽂힌 뒤 확대 화면이 열리기까지 */
    const val HIT_PAUSE_MS = 250L
    /** 확대 화면: 커지기 → 보여 주기 → 닫히기 */
    const val ZOOM_IN_MS = 380L
    const val ZOOM_HOLD_MS = 1800L
    const val ZOOM_OUT_MS = 260L
    /** "엑스 텐!" 등 결과 배너 */
    const val RESULT_BANNER_MS = 1400L
    const val BOARD_DELAY_MS = 300L
    const val END_BANNER_MS = 2400L
}
