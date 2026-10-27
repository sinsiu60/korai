package com.siwoo.party.games.gomoku

/**
 * 오목 수치. 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. 규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    /** 2인 전용 */
    const val PLAYERS = 2

    // ── 규칙 ──
    /** 바둑판 줄 수 (가로·세로 GOMOKU_SIZE 줄 → 교차점 GOMOKU_SIZE² 개) */
    const val GOMOKU_SIZE = 15
    /** 이 개수 이상 이으면 승리 (금지수 없음 — 6개 이상 장목도 승리) */
    const val WIN_LENGTH = 5
    /** 한 사람이 한 판에 쓸 수 있는 무르기 횟수 */
    const val UNDO_PER_PLAYER = 1
    /** 무르기 설정 기본값 (켬) — 허브 [무르기 설정] 에서 끌 수 있다 */
    const val UNDO_DEFAULT = true

    /** 화점 (x, y 쌍) — 15줄 판의 네 귀 + 천원 */
    val STAR_POINTS = intArrayOf(3, 3, 11, 3, 3, 11, 11, 11, 7, 7)

    // ── 연출 (속도 배율 적용) ──
    /** 처음 "흑 가람 vs 백 나래" 소개 */
    const val INTRO_MS = 1800L
    /** 돌을 놓을 때 "탁" (조금 크게 떨어져 제자리로) */
    const val PLACE_MS = 200L
    /** 승리한 줄의 돌이 한 알씩 차례로 빛나는 간격 */
    const val WIN_STEP_MS = 170L
    /** 한 알이 빛을 퍼뜨리는 시간 */
    const val WIN_GLOW_MS = 520L
    /** 줄이 다 빛난 뒤 끝 배너까지 */
    const val END_DELAY_MS = 350L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2600L
    /** "무르기!" 배너 */
    const val UNDO_BANNER_MS = 1000L
}
