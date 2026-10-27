package com.siwoo.marble.data

import com.siwoo.marble.model.Penalty
import com.siwoo.marble.model.PenaltyEffect
import com.siwoo.marble.model.PenaltyGrade

/**
 * 벌칙 룰렛 목록. 금액은 여기서 바로 고치면 된다.
 * 한 번의 벌칙으로 잃는 금액은 총자산 × GameConfig.PENALTY_MAX_RATE 를 넘지 않는다.
 */
object PenaltyData {

    val penalties: Array<Penalty> = arrayOf(
        // ── 소형 ──
        Penalty(0, PenaltyGrade.SMALL, "벌금 5만 원", "은행에 벌금 5만 원을 냅니다.", PenaltyEffect.FINE, 50_000),
        Penalty(1, PenaltyGrade.SMALL, "뒤로 2칸", "뒤로 2칸 이동합니다.", PenaltyEffect.MOVE_BACK, 2),
        Penalty(2, PenaltyGrade.SMALL, "주사위 1개", "다음 턴에는 주사위를 1개만 굴립니다.", PenaltyEffect.ONE_DIE),
        Penalty(3, PenaltyGrade.SMALL, "월급 반토막", "다음 월급을 절반만 받습니다.", PenaltyEffect.HALF_SALARY),
        // ── 중형 ──
        Penalty(4, PenaltyGrade.MEDIUM, "한턱 내기", "다른 생존 플레이어 모두에게 3만 원씩 줍니다.", PenaltyEffect.PAY_ALL, 30_000),
        Penalty(5, PenaltyGrade.MEDIUM, "통행료 2배", "다음에 내는 통행료가 2배가 됩니다.", PenaltyEffect.DOUBLE_TOLL),
        Penalty(6, PenaltyGrade.MEDIUM, "1턴 쉬기", "다음 차례를 한 번 쉽니다.", PenaltyEffect.SKIP_TURN),
        Penalty(7, PenaltyGrade.MEDIUM, "건물 철거", "내 건물 1단계를 철거합니다. (건물이 없으면 벌금 10만 원)", PenaltyEffect.DEMOLISH, 100_000),
        // ── 대형 ──
        Penalty(8, PenaltyGrade.LARGE, "무인도 직행", "곧바로 무인도로 갑니다.", PenaltyEffect.ISLAND),
        Penalty(9, PenaltyGrade.LARGE, "강제 기부", "보유 현금의 20%를 사회복지기금에 기부합니다.", PenaltyEffect.DONATE_RATE, 20),
        Penalty(10, PenaltyGrade.LARGE, "강제 매각", "가장 비싼 땅을 판매가의 절반에 팝니다. (땅이 없으면 벌금 20만 원)", PenaltyEffect.FORCED_SALE, 200_000),
    )

    /** 룰렛 8칸의 등급 (시계 방향, 0번 칸이 맨 위에서 시작) */
    val ROULETTE: Array<PenaltyGrade> = arrayOf(
        PenaltyGrade.SMALL, PenaltyGrade.MEDIUM, PenaltyGrade.SMALL, PenaltyGrade.LARGE,
        PenaltyGrade.SMALL, PenaltyGrade.MEDIUM, PenaltyGrade.SMALL, PenaltyGrade.MEDIUM,
    )

    init {
        penalties.forEachIndexed { i, p -> check(p.id == i) { "벌칙 id가 어긋났습니다: $i" } }
    }

    fun byId(id: Int): Penalty = penalties[id]

    fun ofGrade(g: PenaltyGrade): List<Penalty> = penalties.filter { it.grade == g }
}
