package com.siwoo.party.games.redlight

/** 무궁화 꽃이 피었습니다 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 제한 시간 (끝나면 진행 거리 순) */
    const val TIME_LIMIT_MS = 90_000L
    /** 출발점 → 술래까지 계속 누르고 있을 때 걸리는 시간 (= 말 속도) */
    const val TRACK_MS = 9_000L
    /** 술래가 외치는 글자 (한 글자씩 나타남) */
    const val PHRASE = "무궁화꽃이피었습니다"
    const val PHRASE_LEN = 10
    /** 화면에 띄어 쓸 자리: 이 글자 뒤에 빈칸 (무궁화 / 꽃이 / 피었습니다) */
    val PHRASE_GAPS = intArrayOf(2, 4)
    /** 한 바퀴의 기본 글자 간격 (바퀴마다 이 범위에서 무작위) */
    const val LETTER_MIN_MS = 110L
    const val LETTER_MAX_MS = 430L
    /** 글자마다 ±흔들림 비율 */
    const val LETTER_JITTER = 0.15
    /** 중간에 빨라질 확률, 빨라지는 배율, 빨라진 뒤 최소 간격 */
    const val SPEEDUP_CHANCE = 0.45
    const val SPEEDUP_FACTOR = 0.4
    const val LETTER_FAST_MIN_MS = 60L
    /** 빨라지기 시작하는 글자 위치 범위 (0부터) */
    const val SPEEDUP_FROM = 3
    const val SPEEDUP_TO = 8
    /** 마지막 글자 뒤 술래가 돌아보기까지 */
    const val TURN_DELAY_MS = 150L
    /** 돌아본 뒤 손을 뗄 수 있는 유예 시간 (이후에도 누르고 있으면 출발점으로) */
    const val GRACE_MS = 300L
    /** 술래가 쳐다보는 시간 (무작위) */
    const val LOOK_MIN_MS = 1_400L
    const val LOOK_MAX_MS = 2_600L
    /** 남은 시간이 이 초 이하면 빨갛게 */
    const val HURRY_SEC = 10

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1_700L
    /** 술래가 휙 돌아보는 연출 */
    const val TURN_ANIM_MS = 160L
    /** 걸린 말이 출발점으로 미끄러져 돌아가는 시간 */
    const val BACK_ANIM_MS = 650L
    /** 걸렸을 때 구역 빨간 번쩍임 */
    const val CAUGHT_FLASH_MS = 700L
    const val STARS_MS = 900L
    /** 결과 배너 후 허브로 */
    const val RESULT_SHOW_MS = 2_200L
}
