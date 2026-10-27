package com.siwoo.party.games.sprint

/**
 * 100m 달리기 수치 (거리 단위 cm, 시간 단위 ms).
 * 거리·출발 신호 지연·부정 출발 정지·제한 시간은 게임 규칙이라 속도 배율을 곱하지 않는다.
 * 아래 "연출" 값만 화면에서 공통 속도 배율 t() 가 곱해진다.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 경주 거리 (100 m) */
    const val TRACK_CM = 10_000
    /** 올바른 한 걸음(왼발↔오른발 번갈아)마다 나아가는 거리 → 60걸음이면 도착 */
    const val STEP_CM = 167
    /** "차렷…" 뒤 "땅!" 까지 무작위 지연 (최소~최대) */
    const val SIGNAL_DELAY_MIN_MS = 1200L
    const val SIGNAL_DELAY_MAX_MS = 2800L
    /** 부정 출발 벌칙: "땅!" 신호부터 이 시간 동안 움직일 수 없다 */
    const val FALSE_START_PENALTY_MS = 1000L
    /** "땅!" 부터 제한 시간. 그때까지 못 들어온 사람은 달린 거리로 순위 */
    const val TIME_LIMIT_MS = 40_000L
    /** 남은 시간이 이만큼 이하면 시계가 빨갛게 */
    const val TIME_WARN_MS = 10_000L

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1800L
    /** "준비…" 에서 "차렷…" 까지 */
    const val READY_MS = 1300L
    /** 한 걸음 다리 휘두르기 */
    const val STEP_ANIM_MS = 150L
    /** 버튼 눌림 빛 */
    const val PRESS_FLASH_MS = 170L
    /** "같은 발!" 흔들림·글자 */
    const val SHAKE_MS = 360L
    const val MSG_MS = 700L
    /** 도착 순간 반짝임 */
    const val FINISH_FLASH_MS = 600L
    /** 마지막 도착(또는 시간 종료) 뒤 결과 배너까지 */
    const val RESULT_DELAY_MS = 700L
    const val RESULT_SHOW_MS = 2200L
    const val TIME_UP_BANNER_MS = 1300L
}
