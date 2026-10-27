package com.siwoo.party.games.clap369

/** 369 박수 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 시간 (속도 배율 적용 안 함, 게임 시계 = 일시정지 제외) ──
    /** 1~9 의 제한 시간 */
    const val LIMIT_START_MS = 3000L
    /** 10의 배수마다 줄어드는 시간 (10~19: 2.8초, 20~29: 2.6초 …) */
    const val LIMIT_STEP_MS = 200L
    /** 최소 제한 시간 (90 이상) */
    const val LIMIT_MIN_MS = 1200L
    /**
     * 입력 완료 확인 시간: 필요한 입력([숫자] 1번 또는 [짝] N번)을 마친 뒤 이 시간 동안 더 누르면 "너무 많이 누름"으로 탈락.
     * 이 시간이 지나야 다음 사람 차례가 시작된다 (앞사람 손가락이 다음 사람 입력으로 새지 않게).
     */
    const val SETTLE_MS = 400L

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 (뒤에 3·2·1) */
    const val INTRO_MS = 1600L
    /** 탈락 배너 → 다음 사람 */
    const val ELIM_SHOW_MS = 2000L
    /** 버튼 눌림·짝 말풍선 반짝 */
    const val FLASH_MS = 450L
    /** 마지막 결과 배너 */
    const val END_SHOW_MS = 2000L
}
