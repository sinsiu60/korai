package com.siwoo.marble.model

enum class PenaltyGrade(val label: String) {
    SMALL("소형"),
    MEDIUM("중형"),
    LARGE("대형"),
}

enum class PenaltyEffect {
    FINE,          // value = 벌금 (은행)
    MOVE_BACK,     // value = 뒤로 갈 칸 수
    ONE_DIE,       // 다음 턴 주사위 1개만
    HALF_SALARY,   // 다음 월급 절반
    PAY_ALL,       // value = 생존 플레이어 1명당 지급액
    DOUBLE_TOLL,   // 다음 통행료 2배
    SKIP_TURN,     // 1턴 쉬기
    DEMOLISH,      // 내 건물 1단계 철거 (건물 없으면 value 벌금)
    ISLAND,        // 무인도 직행
    DONATE_RATE,   // 보유 현금 value% 사회복지기금 기부
    FORCED_SALE,   // 가장 비싼 땅을 판매가의 절반으로 강제 매각 (땅 없으면 value 벌금)
}

/** 벌칙 카드 */
class Penalty(
    val id: Int,
    val grade: PenaltyGrade,
    val title: String,
    val description: String,
    val effect: PenaltyEffect,
    val value: Int = 0,
)
