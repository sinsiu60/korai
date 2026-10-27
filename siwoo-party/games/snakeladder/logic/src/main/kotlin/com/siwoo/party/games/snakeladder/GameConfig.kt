package com.siwoo.party.games.snakeladder

/** 뱀과 사다리 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 판: 10×10 = 100칸, 1번 칸은 왼쪽 아래, 지그재그로 올라가 100번 칸은 왼쪽 위 */
    const val SIDE = 10
    const val SQUARES = SIDE * SIDE
    /** 맵마다 사다리·뱀 수 */
    const val LADDERS = 8
    const val SNAKES = 8
    /** 주사위 눈 */
    const val DIE_FACES = 6
    /** 이 눈이 나오면 한 번 더 (SNAKE_EXTRA_FACE) */
    const val EXTRA_ROLL_FACE = 6
    /** 사다리·뱀 연쇄를 따라가는 최대 횟수 (검증된 맵에는 연쇄가 없음, 안전장치) */
    const val MAX_CHAIN = 8

    // ── 설정 기본값 ──
    /** 기본 맵 (0 클래식 / 1 뱀 소굴 / 2 롤러코스터) */
    const val DEFAULT_MAP = 0
    /** 정확히 100 도착 규칙 (기본 끔: 넘어도 도착) */
    const val EXACT_FINISH_DEFAULT = false

    // ── 연출 (속도 배율 적용) ──
    const val START_BANNER_MS = 1600L
    /** 주사위 굴림 */
    const val ROLL_MS = 900L
    /** 결과 눈을 보여 준 뒤 말이 출발하기까지 */
    const val ROLL_HOLD_MS = 250L
    /** 한 칸 깡충 */
    const val STEP_MS = 230L
    /** 사다리 오르기: 기본 + 한 줄(10칸 높이)당 */
    const val LADDER_BASE_MS = 450L
    const val LADDER_ROW_MS = 260L
    /** 뱀 미끄러지기: 기본 + 한 줄당 */
    const val SNAKE_BASE_MS = 600L
    const val SNAKE_ROW_MS = 280L
    /** 사다리·뱀 입구에서 잠깐 멈춤 */
    const val JUMP_PAUSE_MS = 260L
    /** 6 → 한 번 더 배너 */
    const val EXTRA_BANNER_MS = 1100L
    /** 도착 배너 */
    const val FINISH_BANNER_MS = 1800L
    /** 다음 차례까지 */
    const val TURN_GAP_MS = 450L
    const val TURN_BANNER_MS = 900L
    /** 끝 배너 → 결과 */
    const val END_BANNER_MS = 2400L
}
