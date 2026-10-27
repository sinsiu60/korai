package com.siwoo.party.games.jegi

/**
 * 제기차기 수치.
 * 마당 좌표: x = 왼쪽 0 → 오른쪽 [WORLD_W], y = 땅 0 → 위로 + (단위는 "칸", 화면이 같은 비율로 늘린다). 시간은 ms.
 * 제기가 나는 시간·떨어지는 속도·판정 구간은 게임 규칙이라 속도 배율을 곱하지 않는다.
 * 아래 "연출" 값만 화면에서 공통 속도 배율 t() 가 곱해진다.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 경기 ──
    /** 한 사람의 도전 횟수 (가장 좋은 기록으로 순위) */
    const val ATTEMPTS = 2
    /** 한 번 도전에서 이만큼 차면 "완벽!" 으로 그 도전 끝 (끝없이 이어지지 않게) */
    const val MAX_KICKS = 100

    // ── 마당 ──
    const val WORLD_W = 120f
    /** 화면이 보여 줄 높이 (제기 꼭대기 + 여유) */
    const val WORLD_H = 90f
    /** 발이 갈 수 있는 가로 범위 */
    const val FOOT_MIN = 14f
    const val FOOT_MAX = 106f
    /** 도전 시작 때 발 위치 (가운데) */
    const val FOOT_START = 60f
    /** 제기가 날 수 있는 가로 범위 (흔들림 포함) */
    const val JEGI_MIN = 16f
    const val JEGI_MAX = 104f

    // ── 제기 높이·시간 ──
    /** 차는 높이 (발 높이) — 제기가 여기에 내려올 때가 딱 좋은 순간 */
    const val KICK_Y = 14f
    /** 꼭대기 높이 */
    const val APEX_Y = 76f
    /** 처음 손으로 던져 올리는 높이 */
    const val TOSS_Y = 32f
    /** 차거나 던진 뒤 꼭대기까지 올라가는 시간 */
    const val RISE_MS = 520L
    /** 꼭대기에서 발 높이까지 내려오는 시간 (제기는 깃털처럼 일정한 속도로 떨어진다) */
    const val FALL_MS = 1150L

    // ── 좌우 움직임 ──
    /** 찰 때마다 떨어질 자리가 차는 자리에서 ± 이만큼 안에서 무작위로 바뀐다 */
    const val KICK_DRIFT = 22f
    /** 처음 던질 때의 ± 차이 */
    const val TOSS_DRIFT = 6f
    /** 떨어지면서 좌우로 살랑 흔들리는 크기 (사인) · 한 번 왕복 시간 */
    const val SWAY_AMP = 5f
    const val SWAY_PERIOD_MS = 1000L

    // ── 판정 ──
    /** 발이 닿는 가로 거리 (발 가운데 ± 이만큼 안에 제기가 있어야 맞음) */
    const val REACH = 10f
    /** 판정 구간 (딱 좋은 순간 ± 이만큼): 처음 값 */
    const val WINDOW_START_MS = 200L
    /** 한 번 찰 때마다 줄어드는 양 */
    const val WINDOW_SHRINK_MS = 5L
    /** 가장 좁은 판정 구간 */
    const val WINDOW_MIN_MS = 80L
    /**
     * 딱 좋은 순간보다 이만큼 이전부터의 터치가 "차기" 다.
     * 그보다 먼저(제기가 높이 있을 때) 터치하면 발만 그 자리로 옮긴다 (실패 아님).
     */
    const val KICK_ZONE_MS = 450L

    /** 큰 배너로 축하하는 개수 */
    val MILESTONES = intArrayOf(10, 20, 50)

    // ── 연출 (ms, 화면에서 t() 적용) ──
    const val INTRO_MS = 1800L
    const val TURN_BANNER_MS = 1200L
    /** 배너 뒤 제기를 던져 올리기 전 잠깐 */
    const val TOSS_DELAY_MS = 250L
    /** 발(아이)이 터치한 자리로 미끄러지는 시간 */
    const val FOOT_SLIDE_MS = 110L
    /** 다리 들어 차는 동작 */
    const val KICK_ANIM_MS = 280L
    /** 개수 글자 톡 */
    const val COUNT_POP_MS = 260L
    /** "좋아요" · "빨라요" 같은 판정 글자 */
    const val JUDGE_TEXT_MS = 800L
    /** 제기가 땅에 떨어진 뒤 기록 배너까지 */
    const val LANDED_PAUSE_MS = 450L
    const val RESULT_BANNER_MS = 1600L
    const val MILESTONE_BANNER_MS = 1100L
    const val END_BANNER_MS = 2000L
}
