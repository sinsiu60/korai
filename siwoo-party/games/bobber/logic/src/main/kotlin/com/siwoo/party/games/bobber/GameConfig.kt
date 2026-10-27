package com.siwoo.party.games.bobber

/** 낚시 찌 대결 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 */
    const val ROUNDS = 10

    // ── 입질 예정표 (게임 규칙이라 속도 배율 적용 안 함, 라운드 시작 기준) ──
    /** 가짜 입질 횟수 (라운드마다 이 사이에서 무작위) */
    const val FAKES_MIN = 0
    const val FAKES_MAX = 3
    /** 라운드 시작 → 첫 사건(가짜 입질 또는 진짜 입질)까지 */
    const val FIRST_MIN_MS = 1200L
    const val FIRST_MAX_MS = 2800L
    /** 가짜 입질 한 번의 길이 (찌가 살짝 흔들리거나 톡톡 잠기는 시간) */
    const val FAKE_MS = 520L
    /** 가짜 입질이 끝난 뒤 다음 사건까지 */
    const val GAP_MIN_MS = 650L
    const val GAP_MAX_MS = 1700L
    /** 진짜 입질: 찌가 완전히 잠기기까지 */
    const val PLUNGE_MS = 170L
    /** 진짜 입질 뒤 이 시간 안에 아무도 안 누르면 점수 없음 */
    const val BITE_WINDOW_MS = 1500L
    /** 첫 정상 터치 뒤 거의 동시에 누른 손가락을 더 받는 시간 (터치 시각으로 1등을 가림) */
    const val SETTLE_MS = 120L

    // ── 찌 움직임 크기 (0 = 떠 있음, 1 = 몸통이 다 잠김) ──
    /** 톡톡 가짜 입질의 잠김 깊이 */
    const val DIP_DEPTH = 0.26f
    /** 흔들 가짜 입질의 잠김 깊이 */
    const val WOBBLE_DEPTH = 0.08f
    /** 흔들 가짜 입질 왕복 횟수 */
    const val WOBBLE_CYCLES = 3
    /** 기다리는 동안 찌가 물결에 아주 살짝 오르내림 */
    const val IDLE_BOB = 0.035f
    const val IDLE_BOB_PERIOD_MS = 1700L

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1700L
    const val ROUND_INTRO_MS = 1100L
    const val RESULT_SHOW_MS = 1800L
    /** 물고기가 튀어 올랐다 떨어지는 시간 */
    const val FISH_LEAP_MS = 1100L
    /** 결과 뒤 찌가 다시 떠오르는 시간 */
    const val RESURFACE_MS = 380L
    /** 물결(동심원) 하나 */
    const val RIPPLE_MS = 900L
    const val END_SHOW_MS = 2200L
}
