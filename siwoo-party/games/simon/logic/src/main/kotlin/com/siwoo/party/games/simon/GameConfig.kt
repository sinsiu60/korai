package com.siwoo.party.games.simon

/** 패턴 기억 릴레이 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해지고, 규칙 시간은 그대로. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 색 버튼 수 (0 초록 왼쪽 위, 1 빨강 오른쪽 위, 2 파랑 오른쪽 아래, 3 노랑 왼쪽 아래 — 시계 방향) */
    const val COLORS = 4

    /** 색 이름 (안내·탈락 문구) */
    val COLOR_NAMES = arrayOf("초록", "빨강", "파랑", "노랑")

    // ── 규칙 시간 (속도 배율 적용 안 함) ──
    /** 한 번 누른 뒤(또는 차례 시작 뒤) 다음 버튼까지 기다리는 최대 시간. 넘기면 탈락 */
    const val PRESS_LIMIT_MS = 5000L

    // ── 소리 (속도 배율 적용 안 함) ──
    /** 색마다 다른 음 (Hz) — 초록 E5, 빨강 A4, 파랑 E4, 노랑 C#5 (A장조 화음) */
    val TONE_HZ = intArrayOf(659, 440, 330, 554)
    /** 한 음 길이 */
    const val TONE_MS = 280
    /** 음 만드는 표본 수 (초당) */
    const val SAMPLE_RATE = 22050
    /** 음량 (0..1) */
    const val TONE_VOLUME = 0.35f

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1600L
    /** 버튼을 누르면 켜져 있는 시간 */
    const val PRESS_LIGHT_MS = 230L
    /** 켜진 버튼이 꺼지는 시간 */
    const val PRESS_FADE_MS = 200L
    /** 연출 시간이 너무 짧아지지 않게 (빠르게 설정에서도) */
    const val MIN_LIGHT_MS = 90L
    /** 새 색을 더한 뒤 다음 사람 차례까지 */
    const val ADD_NEXT_MS = 1000L
    /** 틀렸을 때 정답 색이 깜빡이는 시간 */
    const val FLASH_MS = 1500L
    /** 정답 색 깜빡임 횟수 */
    const val FLASH_TIMES = 3
    /** 탈락 배너 → 다음 사람 차례까지 */
    const val OUT_SHOW_MS = 2000L
    /** 길이 숫자가 톡 커졌다 돌아오는 시간 */
    const val POP_MS = 380L
    /** 화면 흔들림 */
    const val SHAKE_MS = 500L
    /** 마지막 승자 배너 뒤 결과로 넘어가기까지 */
    const val RESULT_SHOW_MS = 2400L
}
