package com.siwoo.party.games.balloon

/** 풍선 치킨 게임 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 라운드 수 (BALLOON_ROUNDS) */
    const val BALLOON_ROUNDS = 3
    /** 숨겨진 터지는 크기: 이 범위에서 구역마다 서로 다른 값 (POP_MIN..POP_MAX, 양 끝 포함) */
    const val POP_MIN = 12
    const val POP_MAX = 50
    /** 인정된 탭 한 번에 커지는 크기 (= 점수) */
    const val GROW_PER_TAP = 1
    /** 1인당 1초 동안 인정되는 최대 탭 수 (여러 손가락 마구 두드리기 방지, 1초 슬라이딩 창) */
    const val MAX_TAPS_PER_SEC = 10
    const val TAP_WINDOW_MS = 1000L
    /**
     * 라운드 제한 시간 (멈춤 안 누르고 버티기 방지).
     * 시간이 끝나면 아직 안 멈춘 풍선은 그때 크기로 자동 확정된다 (터진 풍선은 0점 그대로).
     */
    const val ROUND_TIME_SEC = 15
    const val ROUND_TIME_MS = ROUND_TIME_SEC * 1000L
    /** 남은 시간이 이 이하면 시간 막대가 빨갛게 */
    const val HURRY_SEC = 4

    // ── 연출 (속도 배율 적용) ──
    /** "라운드 N" 배너 */
    const val ROUND_INTRO_MS = 1500L
    /** 모두 멈춤/터짐 뒤 결과로 넘어가기까지 */
    const val ROUND_END_DELAY_MS = 700L
    /** 라운드 결과(구역마다 +점수) 보여 주는 시간 */
    const val ROUND_RESULT_MS = 2600L
    /** 결과 배너는 그보다 짧게 (가운데 구역 글자를 오래 가리지 않게) */
    const val RESULT_BANNER_MS = 1300L
    /** 최종 우승 배너 후 허브로 */
    const val FINAL_SHOW_MS = 2400L
    /** 풍선이 목표 크기를 따라가는 시간 상수 (탭마다 통통 커짐) */
    const val GROW_EASE_MS = 90L
    /** 터진 조각이 흩어지는 시간 */
    const val POP_ANIM_MS = 650L
    /** 멈춤 도장이 찍히는 시간 */
    const val STAMP_ANIM_MS = 320L
    /** 크기가 최대일 때 떨림 폭 (dp) — 크기에만 비례 (숨겨진 터지는 크기는 드러나지 않음) */
    const val WOBBLE_MAX_DP = 3.2f
}
