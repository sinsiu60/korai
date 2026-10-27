package com.siwoo.party.games.boxes

/** 땅따먹기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 2인 판: 한 줄 점 개수 6 → 6×6 점, 5×5칸, 선 60개 */
    const val BOXES_GRID_2P = 6
    /** 3~4인 판: 7×7 점, 6×6칸, 선 84개 */
    const val BOXES_GRID_34P = 7

    /**
     * 터치 판정 범위 (칸 한 변 = 1 기준).
     * 터치한 곳에서 선분까지 거리가 이 안이면 그 선을 고른다 (손가락이 굵어도 넉넉하게).
     */
    const val PICK_RADIUS = 0.5f

    // ── 연출 (속도 배율 적용) ──
    /** 첫 차례 안내 배너 */
    const val START_BANNER_MS = 1500L
    /** 선이 한쪽 점에서 다른 쪽 점으로 그어지는 시간 */
    const val LINE_DRAW_MS = 220L
    /** 완성된 칸이 색으로 차오르는 시간 (커지며 나타남) */
    const val BOX_FILL_MS = 420L
    /** "한 번 더!" 글자가 칸 위로 떠오르는 시간 */
    const val AGAIN_FLOAT_MS = 900L
    /** 차례 색 테두리가 바뀌는 시간 */
    const val BORDER_FADE_MS = 300L

    /** 종료: "칸을 세어 볼까요?" 배너 */
    const val END_INTRO_MS = 1300L
    /** 종료 칸 세기: 한 칸씩 반짝이는 간격 */
    const val COUNT_STEP_MS = 130L
    /** 종료 칸 세기: 칸 하나가 반짝이는 시간 */
    const val COUNT_FLASH_MS = 420L
    /** 종료 칸 세기: 한 사람을 다 센 뒤 쉬는 시간 */
    const val COUNT_PLAYER_PAUSE_MS = 700L
    /** 마지막 승리 배너 → 결과로 */
    const val END_BANNER_MS = 2200L
}
