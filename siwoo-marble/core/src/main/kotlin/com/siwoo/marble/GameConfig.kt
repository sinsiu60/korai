package com.siwoo.marble

/**
 * 게임 밸런스 수치를 한 곳에 모은 설정 파일.
 * 금액 단위는 모두 "원"이다. (화면에는 "30만" 처럼 만원 단위로 표시)
 *
 * 칸별 땅값·건설비·통행료는 data/BoardData.kt,
 * 황금열쇠 카드 금액은 data/CardData.kt 에 있다.
 */
object GameConfig {
    // ── 인원 ──────────────────────────────────────
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 돈 ────────────────────────────────────────
    /** 시작 자금 */
    const val START_MONEY = 3_000_000
    /** 출발 칸을 지나가거나 도착할 때 받는 월급 */
    const val SALARY = 200_000

    // ── 보드 ──────────────────────────────────────
    const val BOARD_SIZE = 40
    const val TILE_START = 0
    const val TILE_ISLAND = 10
    const val TILE_WELFARE_RECEIVE = 20
    const val TILE_SPACE = 30

    // ── 주사위 / 무인도 ───────────────────────────
    /** 이 횟수만큼 연속 더블이 나오면 즉시 무인도행 */
    const val MAX_DOUBLES = 3
    /** 무인도에 갇히는 턴 수 (더블이 나오면 즉시 탈출) */
    const val ISLAND_TURNS = 3

    // ── 사회복지기금 ─────────────────────────────
    /** 사회복지기금 접수처 칸에 도착하면 내는 기부금 */
    const val WELFARE_DONATION = 150_000

    // ── 매각 ──────────────────────────────────────
    /** 은행 매각 시 돌려받는 비율(%) : 구매가의 절반 */
    const val SELL_BACK_PERCENT = 50

    // ── 턴 제한 모드 ─────────────────────────────
    /** 설정 화면에서 고를 수 있는 라운드 수 (0 = 턴 제한 없음, 최후 1인 승리) */
    val TURN_LIMIT_OPTIONS = intArrayOf(0, 15, 20, 30)
    const val DEFAULT_TURN_LIMIT = 0

    // ── 연출 ──────────────────────────────────────
    /** 말 이동 시 한 칸당 시간(ms) */
    const val MOVE_STEP_MS = 120L
    /** "빠르게" 옵션일 때 한 칸당 시간(ms) */
    const val MOVE_STEP_FAST_MS = 45L
    /** 주사위 굴림 연출 시간(ms) */
    const val DICE_ROLL_MS = 550L
    const val DICE_ROLL_FAST_MS = 250L

    /** 플레이어 말 색상 (ARGB). 설정 화면에서 이 중에서 고른다. */
    val PLAYER_COLORS = intArrayOf(
        0xFFE53935.toInt(), // 빨강
        0xFF1E88E5.toInt(), // 파랑
        0xFF43A047.toInt(), // 초록
        0xFFFDD835.toInt(), // 노랑
        0xFF8E24AA.toInt(), // 보라
        0xFFFF7043.toInt(), // 주황
    )
    val PLAYER_COLOR_NAMES = arrayOf("빨강", "파랑", "초록", "노랑", "보라", "주황")
}
