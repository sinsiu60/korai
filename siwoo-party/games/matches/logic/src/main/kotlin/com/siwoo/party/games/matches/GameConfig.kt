package com.siwoo.party.games.matches

/** 성냥개비 가져가기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 한 판에 놓는 성냥 수 */
    const val MATCHES = 21
    /** 한 차례에 가져갈 수 있는 수 (최소~최대) */
    const val MIN_TAKE = 1
    const val MAX_TAKE = 3

    /** 판 배치: 7개씩 3줄 (MATCHES = ROWS × PER_ROW) */
    const val ROWS = 3
    const val PER_ROW = 7

    // ── 연출 (속도 배율 적용) ──
    /** 라운드 시작 배너 ("1 / 3 판", "○○ 먼저!") */
    const val ROUND_BANNER_MS = 1600L
    /** 고른 성냥이 들리는 시간 */
    const val LIFT_MS = 160L
    /** 네 번째를 고르려 할 때 고개 젓기 */
    const val SHAKE_MS = 380L
    /** 가져간 성냥이 플레이어 패널로 날아가는 시간 */
    const val FLY_MS = 620L
    /** 여러 개가 날아갈 때 출발 간격 */
    const val FLY_STAGGER_MS = 90L
    /** 마지막 성냥에 불이 붙어 타는 시간 */
    const val BURN_MS = 1500L
    /** "펑!" 패배 배너 */
    const val LOSE_BANNER_MS = 1800L
    /** 다음 라운드 성냥이 다시 놓이는 시간 (한 개씩 톡톡) */
    const val DEAL_MS = 700L
    /** 마지막 승리 배너 → 결과로 */
    const val END_BANNER_MS = 2200L
}
