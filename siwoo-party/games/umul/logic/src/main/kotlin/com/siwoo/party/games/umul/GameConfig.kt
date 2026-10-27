package com.siwoo.party.games.umul

/**
 * 우물고누 수치. 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. 규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    /** 2인 전용 */
    const val PLAYERS = 2
    /** 한 사람의 말 수 */
    const val PIECES = 2

    // ── 규칙 ──
    /** 이만큼 판을 이기면 대결 승리 (3판 2선승) */
    const val WINS_NEEDED = 2
    /** 판 수 한도 — 무승부 판이 이어져도 이 판까지만 (그때 이긴 판이 많은 사람 승리, 같으면 둘 다 1위) */
    const val MAX_ROUNDS = 5
    /** 한 판의 수 한도 (두 사람 합계). 이만큼 두어도 아무도 갇히지 않으면 무승부 판 → 다음 판 */
    const val MOVE_LIMIT = 40
    /** 수 한도가 이만큼 남으면 남은 수를 빨갛게 강조 */
    const val MOVE_WARN = 6

    // ── 연출 (속도 배율 적용) ──
    /** 처음 "가람 vs 나래" 소개 */
    const val INTRO_MS = 1800L
    /** 2판째부터 "N판째" 소개 */
    const val ROUND_INTRO_MS = 1500L
    /** 새 판 시작 때 말이 하나씩 톡 놓이는 시간 */
    const val SETUP_MS = 520L
    /** 말이 선을 따라 미끄러지는 시간 */
    const val SLIDE_MS = 320L
    /** 막힌 말·금지 수를 누르면 말이 흔들리는 시간 */
    const val DENY_MS = 420L
    /** 갇힌 말이 부들부들 떠는 시간 (그 뒤 판 결과 배너) */
    const val TRAP_MS = 900L
    /** 판 결과 배너 → 다음 판 */
    const val ROUND_END_MS = 2000L
    /** 최종 배너 → 허브로 */
    const val END_BANNER_MS = 2600L
}
