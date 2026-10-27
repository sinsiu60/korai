package com.siwoo.party.games.crown

/** 왕관 쟁탈 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 게임 시간 (초) — 이 시간이 지나면 점수 순으로 순위 */
    const val GAME_TIME_SEC = 45
    const val GAME_TIME_MS = GAME_TIME_SEC * 1000L
    /**
     * 왕관을 가진 사람이 버튼을 누르고 있는 동안 1초에 얻는 점수.
     * 누른 시간은 ms 단위로 정확히 쌓고, 점수 = 누적 ms × POINTS_PER_SEC / 1000 (소수점 버림).
     */
    const val POINTS_PER_SEC = 10
    /**
     * 빼앗기 쿨다운 (1인당): 다른 사람에게서 왕관을 빼앗은 사람은 이 시간 동안 다시 빼앗지 못한다.
     * 가운데 왕관 줍기는 쿨다운과 상관없이 언제나 가능하고 쿨다운도 새로 생기지 않는다.
     */
    const val STEAL_COOLDOWN_MS = 2000L
    /** 남은 시간이 이 이하면 시계가 빨갛게 + 1초마다 똑딱 */
    const val HURRY_SEC = 5

    // ── 연출 (속도 배율 적용) ──
    /** 시작 안내 배너 */
    const val INTRO_MS = 1500L
    /** 왕관이 날아가는 시간 (포물선) */
    const val CROWN_FLIGHT_MS = 420L
    /** 쿨다운 중 누르면 버튼이 부르르 떠는 시간 */
    const val BLOCKED_SHAKE_MS = 320L
    /** 왕관을 가진 동안 버튼 금빛이 숨 쉬는 주기 */
    const val GLOW_PERIOD_MS = 900L
    /** "시간 끝!" 배너 */
    const val TIME_UP_MS = 1400L
    /** 최종 우승 배너 후 허브로 */
    const val FINAL_SHOW_MS = 2400L
}
