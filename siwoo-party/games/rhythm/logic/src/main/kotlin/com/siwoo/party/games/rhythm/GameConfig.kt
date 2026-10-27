package com.siwoo.party.games.rhythm

/** 리듬 노트 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 경기 시간 */
    const val GAME_SEC = 60
    const val GAME_MS = GAME_SEC * 1000L

    /** 박자 빠르기: 시작 → 끝 (분당 박자). RAMP_MS 동안 직선으로 빨라진 뒤 그대로 */
    const val START_BPM = 100f
    const val END_BPM = 160f
    const val RAMP_MS = 55_000L
    /** 첫 노트가 판정선에 닿는 시간 (그 전에 준비 박자음 COUNT_IN_BEATS 번) */
    const val LEAD_IN_MS = 2_000L
    const val COUNT_IN_BEATS = 2
    /** 이 시간 뒤로는 노트를 만들지 않는다 (마지막 노트 판정이 경기 끝 전에 끝나게) */
    const val LAST_NOTE_MS = 58_500L
    /** 박자마다 쉴 확률 · 반 박자(엇박) 노트 확률 (시작 0 → 끝 이 값) */
    const val REST_CHANCE = 0.15f
    const val OFFBEAT_CHANCE = 0.2f
    /** 노트가 위에서 판정선까지 떨어지는 시간: 시작 → 끝 (빨라짐) */
    const val START_FALL_MS = 1_800L
    const val END_FALL_MS = 1_100L
    /** 노트 사이 최소 간격 */
    const val MIN_GAP_MS = 150L

    /** 판정 창 (|어긋난 시간|): 퍼펙트 ≤ 60, 굿 ≤ 140, 그보다 이르면 ≤ 220 까지 미스(노트 소모), 더 멀면 헛손질(무시) */
    const val PERFECT_MS = 60L
    const val GOOD_MS = 140L
    const val MISS_MS = 220L
    /** 점수: 퍼펙트 3 / 굿 1 / 미스 0 */
    const val PERFECT_SCORE = 3
    const val GOOD_SCORE = 1
    const val MISS_SCORE = 0

    /** 박자음: 이 박자마다 강박(높은 음) */
    const val ACCENT_EVERY = 4
    /** 출력 지연 보정: 박자음을 이만큼 먼저 울린다 (0 = 보정 없음) */
    const val TICK_LEAD_MS = 0L
    /** 박자음 만들기 (코드로 만든 짧은 음) */
    const val SAMPLE_RATE = 22_050
    const val TICK_MS = 45
    const val ACCENT_HZ = 1_760
    const val TICK_HZ = 1_175
    const val TICK_VOLUME = 0.55f
    /** 이보다 늦게 처리된 박자는 소리 없이 건너뛴다 (밀린 박자음이 한꺼번에 울리지 않게) */
    const val TICK_STALE_MS = 80L

    /** 마지막 몇 초는 시간이 빨갛게 */
    const val HURRY_SEC = 10
    /** 콤보 이 배수마다 별 연출 */
    const val COMBO_STARS_EVERY = 10

    // ── 화면 ──
    /** 레인 최대 폭 (dp) */
    const val LANE_MAX_WIDTH_DP = 110f
    /** 노트 최대 크기 (dp): 2인(높은 구역) / 3·4인(낮은 구역) */
    const val NOTE_MAX_DP = 46f
    const val NOTE_MAX_DP_SMALL = 32f
    /** 판정선 위치 (구역 높이 비율) */
    const val JUDGE_FRAC = 0.8f
    /** 노트가 판정선에서 이 시간 안이면 빛남 (규칙 시간 — 판정 창과 맞춤) */
    const val GLOW_WINDOW_MS = 220L

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1_500L
    /** 맞혔을 때 판정선에서 퍼지는 고리 */
    const val HIT_RING_MS = 360L
    /** 박자마다 판정선 과녁이 두근 */
    const val BEAT_PULSE_MS = 200L
    const val STARS_MS = 650L
    /** 결과 배너 후 허브로 */
    const val RESULT_SHOW_MS = 2_400L
}
