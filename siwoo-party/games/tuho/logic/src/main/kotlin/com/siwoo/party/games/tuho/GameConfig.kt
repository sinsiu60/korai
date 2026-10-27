package com.siwoo.party.games.tuho

/**
 * 투호 수치. 길이는 미터(옆에서 본 평면: x = 오른쪽, y = 위, 바닥 y = 0), 속도는 m/s, 시간은 ms.
 * 던지는 손은 왼쪽(x = 0), 항아리는 오른쪽(x = JAR_X).
 * 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. 게이지 왕복 시간(규칙 시간)과 물리 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 경기 ──
    /** 한 사람이 던지는 화살 수 — 한 사람씩 한 개씩 돌아가며 던진다 (가람 1 → 나래 1 → 가람 2 …) */
    const val ARROWS = 10
    /** 점수: 입구 2점, 귀 3점, 실패 0점 */
    const val MOUTH_POINTS = 2
    const val EAR_POINTS = 3

    // ── 던지기 ──
    /** 중력 가속도 (m/s²) — 바람 없음, 같은 각도·힘이면 늘 같은 포물선 */
    const val GRAVITY = 9.8f
    /** 화살을 놓는 손 위치 */
    const val RELEASE_X = 0f
    const val RELEASE_Y = 0.85f
    /** 각도 게이지: 0 → ANGLE_MIN, 1 → ANGLE_MAX (도, 수평 기준 위로) */
    const val ANGLE_MIN = 20f
    const val ANGLE_MAX = 65f
    /** 힘 게이지: 0 → SPEED_MIN, 1 → SPEED_MAX (m/s). 입구까지 약 4.1~5.4 m/s 가 필요 */
    const val SPEED_MIN = 3.7f
    const val SPEED_MAX = 5.9f
    /** 게이지 한 번 왕복 시간 (0 → 1 → 0) */
    const val ANGLE_PERIOD_MS = 2400L
    const val POWER_PERIOD_MS = 2200L

    // ── 항아리 (가운데 입구 + 양옆 귀) ──
    /** 항아리 가운데 x */
    const val JAR_X = 2.2f
    /** 몸통: 타원 (가운데 높이 BODY_CY, 반지름 BODY_RX × BODY_RY, 바닥에 닿음) */
    const val BODY_CY = 0.22f
    const val BODY_RX = 0.27f
    const val BODY_RY = 0.22f
    /** 목 (입구 관): 위 끝 = 입구 높이, 아래 끝은 몸통 속 */
    const val MOUTH_Y = 0.62f
    const val NECK_BOTTOM = 0.36f
    /** 입구 구멍 반폭 — 화살 끝이 입구 높이에서 가운데 ± 이만큼 안으로 내려가면 들어감 (경계 포함) */
    const val MOUTH_HALF = 0.07f
    /** 관 테두리(벽) 두께 — 구멍 바로 바깥 이만큼은 테두리: 맞으면 튕김 */
    const val RIM = 0.016f
    /** 귀: 목 양옆 EAR_DX 거리의 작은 관. 왼쪽(가까운 귀)이 조금 낮다 */
    const val EAR_DX = 0.17f
    const val EAR_HALF = 0.035f
    const val EAR_NEAR_Y = 0.52f
    const val EAR_FAR_Y = 0.55f
    /** 귀 관 길이 (위 끝에서 아래로) */
    const val EAR_LEN = 0.13f
    /** 귀와 목을 잇는 팔: 귀 위 끝에서 ARM_TOP ~ ARM_BOTTOM 아래 */
    const val ARM_TOP = 0.065f
    const val ARM_BOTTOM = 0.09f

    // ── 튕김 (테두리·몸통에 맞음) ──
    /** 튕긴 뒤 속도: 가로는 반대로 × BOUNCE_X, 세로는 위로 × BOUNCE_Y */
    const val BOUNCE_X = 0.3f
    const val BOUNCE_Y = 0.35f

    // ── 판정 계산 ──
    /** 몸통·관 벽 충돌을 찾는 시간 간격 (초) — 찾은 뒤 이분법으로 정확히 */
    const val SAMPLE_DT = 0.002f
    /** 화살 길이 (그림, 판정은 화살 끝 한 점) */
    const val ARROW_LEN = 0.42f
    /** 한 구멍에 꽂힌 화살을 그리는 최대 개수 */
    const val MAX_STUCK_DRAWN = 4

    // ── 연출 (배율 적용) ──
    const val INTRO_MS = 1900L
    const val TURN_BANNER_MS = 900L
    /** 비행 연출: 실제 비행 시간 × FLIGHT_SLOWMO (조금 느리게 보여 줌) */
    const val FLIGHT_SLOWMO = 1.25f
    /** 들어감·실패 배너 */
    const val RESULT_BANNER_MS = 1300L
    /** 화살이 멈춘(들어간·떨어진) 뒤 다음 차례까지 */
    const val RESULT_MS = 1500L
    /** "탁!" 뒤 축하음까지 */
    const val CHEER_DELAY_MS = 140L
    const val END_BANNER_MS = 2200L
}
