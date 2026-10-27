package com.siwoo.party.games.mole

/** 두더지 잡기 대결 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 경기 시간 */
    const val GAME_SEC = 30
    const val GAME_MS = GAME_SEC * 1000L
    /** 구멍 배치 3 × 2 */
    const val HOLE_COLS = 3
    const val HOLE_ROWS = 2
    const val HOLES = HOLE_COLS * HOLE_ROWS
    /** 점수: 일반 +1 / 황금 +3 / 폭탄 -2 (빗나감은 0) */
    const val POINTS_NORMAL = 1
    const val POINTS_GOLD = 3
    const val POINTS_BOMB = -2
    /** 첫 두더지까지 */
    const val FIRST_DELAY_MS = 500L
    /** 두더지가 올라와 있는 시간: 시작 → 끝 (경기가 진행될수록 짧아짐) */
    const val UP_START_MS = 1_200L
    const val UP_END_MS = 520L
    /** 다음 두더지가 나오기까지 간격: 시작 → 끝 (±GAP_JITTER) */
    const val GAP_START_MS = 720L
    const val GAP_END_MS = 360L
    const val GAP_JITTER = 0.2
    /** 같은 구멍에서 다시 나오기 전 쉬는 시간 */
    const val HOLE_REST_MS = 260L
    /** 황금·폭탄 확률 (나머지는 일반) */
    const val GOLD_RATE = 0.12
    const val BOMB_RATE = 0.16
    /** 한 판에 최소 개수 (모자라면 일반 두더지를 바꿈) */
    const val MIN_GOLD = 2
    const val MIN_BOMB = 3
    /** 마지막 몇 초는 시간이 빨갛게 */
    const val HURRY_SEC = 5

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1_500L
    /** 구멍에서 올라오기/내려가기 */
    const val RISE_MS = 110L
    /** 잡힌 두더지가 어지러워하며 들어가는 시간 */
    const val HIT_SINK_MS = 280L
    /** "+1" 같은 점수 글자가 떠오르는 시간 */
    const val POP_TEXT_MS = 650L
    /** 폭탄: 구역 흔들림 */
    const val SHAKE_MS = 420L
    const val STARS_MS = 650L
    /** 결과 배너 후 허브로 */
    const val RESULT_SHOW_MS = 2_200L
}
