package com.siwoo.party.games.tug

/** 연타 줄다리기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    /** 2인(1 대 1) 또는 4인(2 대 2)만. 3인은 지원 안 함 */
    const val SOLO_PLAYERS = 2
    const val TEAM_PLAYERS = 4
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 제한 시간 (TUG_TIME_SEC) */
    const val TUG_TIME_SEC = 30
    const val TUG_TIME_MS = TUG_TIME_SEC * 1000L
    /** 1인당 1초 동안 인정되는 최대 탭 수 (TUG_MAX_TAPS_PER_SEC, 1초 슬라이딩 창) */
    const val TUG_MAX_TAPS_PER_SEC = 15
    const val TAP_WINDOW_MS = 1000L
    /**
     * 깃발이 승리선까지 가는 데 필요한 "양 팀 탭 수 차이" (깃발 위치 = 차이 ÷ 이 값).
     * 1 대 1 기준. 2 대 2 는 팀 합계라 차이가 커지기 쉬워 따로 둔다.
     */
    const val TUG_WIN_DIFF_SOLO = 40
    const val TUG_WIN_DIFF_TEAM = 60
    /** 시간 종료 때 탭 차이가 정확히 0 이면 연장전 (이 시간 안에 승리선 또는 끝났을 때 앞선 팀 승리, 그래도 같으면 무승부) */
    const val TUG_OVERTIME_MS = 5000L
    /** 마지막 몇 초는 빨갛게 + 째깍 소리 */
    const val HURRY_SEC = 5

    // ── 연출 (속도 배율 적용) ──
    /** 2인 "가람 vs 나래" 소개 */
    const val INTRO_MS = 1500L
    /** 4인 팀 뽑기: 이름이 양쪽을 오가는 횟수, 첫 간격 → 마지막 간격 (점점 느려짐) */
    const val REVEAL_STEPS = 14
    const val REVEAL_STEP_FIRST_MS = 70L
    const val REVEAL_STEP_LAST_MS = 300L
    /** 팀 확정 후 보여 주는 시간 */
    const val REVEAL_HOLD_MS = 1400L
    /** 이름 카드가 자기 구역으로 날아가는 시간 */
    const val CARD_FLIGHT_MS = 650L
    /** 깃발이 목표 위치를 따라가는 시간 상수 (작을수록 빠르게 따라감) */
    const val FLAG_FOLLOW_MS = 110L
    /** 탭 물결 */
    const val RIPPLE_MS = 380L
    /** 승패가 난 뒤 밧줄이 끌려가고 진 쪽이 넘어지는 연출 */
    const val END_ANIM_MS = 1300L
    /** 승리 배너 후 허브로 */
    const val RESULT_SHOW_MS = 2200L
}
