package com.siwoo.party.games.bomb

/** 폭탄 돌리기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 (BOMB_ROUNDS) */
    const val BOMB_ROUNDS = 5
    /** 폭발까지 무작위 시간 (게임 규칙이라 속도 배율 적용 안 함, 화면에 표시하지 않음) */
    const val FUSE_MIN_MS = 20_000L
    const val FUSE_MAX_MS = 60_000L
    /** [넘기기] 터치 후 다시 넘길 수 있을 때까지 (연타 방지, BOMB_PASS_COOLDOWN_MS) */
    const val BOMB_PASS_COOLDOWN_MS = 800L

    /** 째깍 간격: 막 불붙었을 때(남은 비율 1) → 터지기 직전(남은 비율 0) */
    const val TICK_SLOW_MS = 1000L
    const val TICK_FAST_MS = 140L
    /** 폭탄 흔들림 세기(0~1): 불붙었을 때 최소값 → 터지기 직전 1 */
    const val SHAKE_MIN = 0.06

    /** 주제 목록 (설정에서 편집): 최대 개수, 한 주제 최대 글자 수 */
    const val MAX_TOPICS = 60
    const val MAX_TOPIC_LEN = 20
    /** 이 개수 이상이면 한 게임(5라운드) 안에서 주제가 겹치지 않는다 */
    const val MIN_TOPICS_NO_REPEAT = BOMB_ROUNDS

    // ── 연출 (속도 배율 적용) ──
    /** 주제 카드 확대 등장 → 뒤집기 */
    const val TOPIC_ZOOM_MS = 450L
    const val TOPIC_FLIP_MS = 600L
    /** 넘길 때 이름이 바뀌는 연출 */
    const val PASS_ANIM_MS = 260L
    /** 폭발: 흰색 번쩍, 화면 흔들림, "펑!" 도장 */
    const val FLASH_MS = 550L
    const val EXPLODE_SHAKE_MS = 700L
    const val STAMP_MS = 380L
    const val EXPLODE_ANIM_MS = 900L
    /** 폭발 뒤 다음 라운드까지 */
    const val EXPLODE_SHOW_MS = 2800L
    /** 마지막 라운드 뒤 "게임 끝!" */
    const val END_SHOW_MS = 1800L
}
