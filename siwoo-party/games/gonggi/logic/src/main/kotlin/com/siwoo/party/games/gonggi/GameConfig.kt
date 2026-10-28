package com.siwoo.party.games.gonggi

/**
 * 공기놀이 수치 (시간 단위 ms).
 * 던진 돌이 공중에 있는 시간·받기 구간·꺾기 판정 구간은 게임 규칙이라 속도 배율(t())을 곱하지 않는다.
 * "연출" 아래 시간만 화면에서 t() 를 곱한다.
 *
 * 돗자리 좌표: 가로 0..MAT_W, 세로 0..MAT_H (위 = 0, 아래 = MAT_H). 손은 아래 가운데.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 경기 ──
    /** 공깃돌 수 */
    const val STONES = 5
    /** 단계마다 손에 들고 시작하는 "던질 돌" 번호 */
    const val TOSS_STONE = 0
    /** 목표 점수 고르기 (게임 설정) */
    val TARGET_OPTIONS = intArrayOf(10, 20, 30)
    const val DEFAULT_TARGET = 20
    /** 최대 바퀴 (모두가 이만큼 차례를 하면 끝 — 남은 사람은 점수 → 단계 순) */
    const val MAX_ROUNDS = 15

    // ── 단계 ──
    /** 단계 번호 0..3 = 1단~4단, 4 = 꺾기 */
    const val STAGE_KKEOKGI = 4
    const val STAGE_COUNT = 5
    /** 단계마다 한 번 던질 때 집을 돌 수 (1단 하나씩 ×4, 2단 둘씩 ×2, 3단 셋 → 하나, 4단 넷) */
    val PICKS: Array<IntArray> = arrayOf(
        intArrayOf(1, 1, 1, 1),
        intArrayOf(2, 2),
        intArrayOf(3, 1),
        intArrayOf(4),
    )
    val STAGE_NAMES = arrayOf("1단", "2단", "3단", "4단", "꺾기")

    // ── 던지기 (규칙 시간) ──
    /** 던진 돌이 손을 떠나 다시 손 높이로 내려올 때까지 (단계별, 조금씩 짧아짐) */
    val AIR_MS = longArrayOf(1600L, 1550L, 1500L, 1500L)
    /** 받기 구간: 손 높이에 오는 순간(착지) 기준 [착지 − EARLY, 착지 + LATE] (경계 포함) */
    const val CATCH_EARLY_MS = 240L
    const val CATCH_LATE_MS = 140L

    // ── 꺾기 (규칙 시간) ──
    /** 손바닥의 5개를 던져 손등에 내려올 때까지 */
    const val KK_AIR_MS = 1200L
    /**
     * 손등 받기 정확도: |터치 − 착지| 가 이 값 이하인 문턱 개수 = 손등에 남는 돌 수.
     * ≤70ms → 5개, ≤130 → 4, ≤200 → 3, ≤280 → 2, ≤380 → 1, 그 밖 → 0 (모두 떨어짐 = 실패)
     */
    val KK_BACK_THRESHOLDS = longArrayOf(380L, 280L, 200L, 130L, 70L)
    /** 손등의 돌을 튕겨 올린 뒤 다시 낚아챌 순간까지 */
    const val KK_FLIP_AIR_MS = 1000L
    /** 낚아채기 구간 [순간 − EARLY, 순간 + LATE] */
    const val KK_GRAB_EARLY_MS = 220L
    const val KK_GRAB_LATE_MS = 140L

    // ── 돗자리 (좌표) ──
    const val MAT_W = 1.6f
    const val MAT_H = 1f
    /** 공깃돌 반지름 (돗자리 단위) */
    const val STONE_R = 0.055f
    /** 흩어 놓는 구역 (손 근처는 비움) */
    const val SCATTER_LEFT = 0.16f
    const val SCATTER_RIGHT = 1.44f
    const val SCATTER_TOP = 0.12f
    const val SCATTER_BOTTOM = 0.6f
    /** 흩어 놓은 돌 사이 최소 거리 (중심 간) */
    const val MIN_GAP = 0.22f
    /** 손 (손바닥 가운데) */
    const val HAND_X = 0.8f
    const val HAND_Y = 0.84f

    // ── 연출 (배율 적용) ──
    const val INTRO_MS = 2000L
    const val TURN_BANNER_MS = 1000L
    /** 받은 뒤 다음 던지기까지 */
    const val CAUGHT_MS = 600L
    const val STAGE_BANNER_MS = 1100L
    const val FAIL_MS = 1800L
    const val KK_RESULT_MS = 1900L
    const val FINISH_BANNER_MS = 1700L
    const val END_BANNER_MS = 2200L
    /** 실패해 떨어진 돌이 바닥에 닿기까지 */
    const val DROP_MS = 420L
}
