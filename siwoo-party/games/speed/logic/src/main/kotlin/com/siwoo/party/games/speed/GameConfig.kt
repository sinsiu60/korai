package com.siwoo.party.games.speed

/** 스피드 카드의 모든 수치 (규칙 + 연출 시간). 연출 시간(…_MS)은 화면에서 t() 배율을 곱해 쓴다 */
object GameConfig {
    // ── 인원 ──
    const val PLAYERS = 2

    // ── 카드 나누기 (조커 뺀 52장) ──
    /** 한 사람 몫 (52 ÷ 2) */
    const val CARDS_PER_PLAYER = 26
    /** 각자 앞에 펼쳐 놓는 칸 수 */
    const val SLOTS = 4
    /** 가운데 더미 수 (0 = 0번 플레이어 쪽에서 본 왼쪽, 1 = 1번 플레이어 쪽에서 본 왼쪽) */
    const val CENTER_PILES = 2
    /** 시작할 때 뽑을 더미 장수 (26 − 칸 4 − 가운데로 뒤집는 1) */
    const val START_DRAW_PILE = CARDS_PER_PLAYER - SLOTS - 1

    // ── 연출 시간 (ms, t() 배율 적용) ──
    /** 시작 안내 배너 */
    const val INTRO_MS = 1500L
    /** 시작 때 칸으로 카드 나눠 주기 (한 장 비행) */
    const val DEAL_FLIGHT_MS = 260L
    /** 나눠 주기 장 사이 간격 */
    const val DEAL_GAP_MS = 70L
    /** 놓은 카드가 가운데 더미로 빨려 들어가는 시간 */
    const val PLACE_MS = 110L
    /** 못 내는 카드가 제자리로 돌아가는 시간 */
    const val SNAP_BACK_MS = 190L
    /** 빈 칸을 자기 더미에서 채우는 비행 */
    const val REFILL_MS = 230L
    /** [다시]: 더미에서 가운데로 뒤집어 나가는 비행 */
    const val FLIP_MS = 340L
    /** 둘 다 막힌 걸 알려 주기 전 잠깐 기다림 (마지막 카드가 내려앉은 뒤) */
    const val STUCK_NOTICE_MS = 350L
    /** 승리 배너 표시 후 결과로 */
    const val END_BANNER_MS = 2000L

    // ── 화면 비율 ──
    /** 카드 높이 = 화면 높이 × 이 값 */
    const val CARD_H_FRAC = 0.235f
    /** 가운데 더미 카드 크기 배율 */
    const val CENTER_SCALE = 1.15f
    /** 끌어 놓을 때 가운데 더미 판정 여유 (가운데 카드 폭·높이에 곱함, 반폭 기준) */
    const val DROP_REACH = 0.95f
    /** 끄는 동안 카드 확대 */
    const val DRAG_SCALE = 1.08f
}
