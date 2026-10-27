package com.siwoo.party.games.codetile

/** 숨은 숫자 타일 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 숫자 종류 (0~11) */
    const val NUMBERS = 12
    /** 전체 타일 수 (숫자마다 검정·흰색 한 장씩) */
    const val TILE_COUNT = NUMBERS * 2
    /** 처음 나눠 받는 타일 수: 2~3인 */
    const val START_TILES_SMALL = 4
    /** 처음 나눠 받는 타일 수: 4인 */
    const val START_TILES_FOUR = 3

    /** 인원별 처음 타일 수 */
    fun startTiles(players: Int): Int = if (players >= 4) START_TILES_FOUR else START_TILES_SMALL

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1700L
    /** 타일 뒤집기 (정답 공개·오답 때 뽑은 타일 공개) */
    const val FLIP_MS = 520L
    /** 정답 뒤 [계속 추리]/[멈추기] 가 나오기까지 */
    const val CORRECT_PAUSE_MS = 650L
    /** 오답: 고른 타일이 도리도리 흔들림 */
    const val WRONG_SHAKE_MS = 480L
    /** 오답 배너 */
    const val WRONG_BANNER_MS = 1100L
    /** 뽑은 타일이 줄에 끼워지는 연출 */
    const val INSERT_MS = 480L
    /** 끼운 뒤 다음 사람 차례까지 */
    const val AFTER_INSERT_MS = 900L
    /** 탈락 배너 */
    const val ELIMINATED_MS = 1500L
    /** 승리 배너 뒤 결과로 */
    const val RESULT_SHOW_MS = 2300L
    /** 화면 흔들림 (탈락) */
    const val SHAKE_MS = 450L
}
