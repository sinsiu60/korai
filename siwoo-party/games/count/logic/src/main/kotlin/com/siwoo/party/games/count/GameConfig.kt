package com.siwoo.party.games.count

/** 몇 개였지? 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 라운드 수 */
    const val ROUNDS = 12
    /** 첫 라운드 기준 개수 → 마지막 라운드 기준 개수 (사이는 고르게 늘어남) */
    const val COUNT_MIN = 5
    const val COUNT_MAX = 20
    /** 기준 개수에서 ±이만큼 흔들기 (같은 판이 늘 같지 않게) */
    const val COUNT_JITTER = 1
    /** 그림을 보여 주는 시간: 첫 라운드 → 마지막 라운드 (고르게 줄어듦). 게임 규칙이라 배율 없음 */
    const val SHOW_FIRST_MS = 900L
    const val SHOW_LAST_MS = 400L
    /** 보기 수 */
    const val CHOICES = 4
    /** 오답 보기는 정답에서 ±1..이만큼 (하나는 꼭 ±1) */
    const val NEAR_MAX = 3
    /** 틀리면 이 시간 동안 그 사람만 잠금 */
    const val LOCK_MS = 1000L
    /** 그림이 사라진 뒤 답할 수 있는 시간. 아무도 못 맞히면 정답 공개 후 다음 라운드 */
    const val ANSWER_TIME_SEC = 6
    const val ANSWER_TIME_MS = ANSWER_TIME_SEC * 1000L
    /** 남은 시간이 이 이하면 시간 막대가 빨갛게 */
    const val HURRY_MS = 2000L

    // ── 그림 배치 (그림 칸 높이 = 1 인 단위) ──
    /** 칸 가장자리 여백 */
    const val DOT_EDGE = 0.06f
    /** 개수가 가장 많을 때 칸 넓이 중 모양(간격 포함)이 차지하는 비율 — 이보다 크면 배치가 막힐 수 있다 */
    const val DOT_FILL = 0.45f
    /** 모양 사이 간격 = 반지름 × 이 배수 */
    const val DOT_SPACING = 1.3f
    /** 모양 크기 흔들기 (반지름 배수 범위) */
    const val DOT_SCALE_MIN = 0.82f
    const val DOT_SCALE_MAX = 1.12f
    /** 반지름 상한 (칸 높이 대비) */
    const val DOT_RADIUS_MAX = 0.11f
    /** 색 가짓수 (화면이 팔레트를 가진다) */
    const val DOT_COLORS = 6

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1800L
    /** 그림을 보여 주기 전 "잘 보세요" 준비 */
    const val READY_MS = 900L
    /** 정답 공개 (그림 다시 보여 주기) 후 다음 라운드까지 */
    const val REVEAL_MS = 2000L
    /** 보기가 나타날 때 커지는 연출 */
    const val POP_MS = 240L
    /** 보기를 눌렀을 때 번쩍 */
    const val TAP_FLASH_MS = 220L
    /** 최종 우승 배너 후 허브로 */
    const val FINAL_SHOW_MS = 2400L
}
