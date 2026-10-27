package com.siwoo.party.games.marble.data

import com.siwoo.party.games.marble.GameConfig
import com.siwoo.party.games.marble.model.Tile
import com.siwoo.party.games.marble.model.TileType

/**
 * 40칸 보드 정의 (시계 방향, 0번 = 출발).
 * 황금열쇠: 2, 12, 17, 35 / 벌칙(룰렛): 7(아래 줄), 22(위 줄)
 * 땅값·건설비·통행료를 바꾸려면 아래 숫자만 고치면 된다.
 *
 * city(칸번호, 이름, 보드표시이름, 색그룹, 땅값, build(별장, 빌딩, 호텔), toll(땅, 별장, 빌딩, 호텔))
 * special(칸번호, 이름, 보드표시이름, 땅값, 고정통행료)
 */
object BoardData {

    /** 색상 그룹 이름과 색 (ARGB) */
    val GROUP_NAMES = arrayOf("아시아", "중동·아프리카", "북유럽", "중부 유럽", "남미·오세아니아", "휴양지", "동아시아·유럽", "세계 금융도시")
    val GROUP_COLORS = intArrayOf(
        0xFF9CCC65.toInt(), // 연두
        0xFF26C6DA.toInt(), // 청록
        0xFFEC407A.toInt(), // 분홍
        0xFFFFA726.toInt(), // 주황
        0xFFEF5350.toInt(), // 빨강
        0xFFFFEE58.toInt(), // 노랑
        0xFF66BB6A.toInt(), // 초록
        0xFF5C6BC0.toInt(), // 남색
    )
    const val SPECIAL_COLOR = 0xFF8D6E63.toInt()

    val tiles: Array<Tile> = arrayOf(
        corner(GameConfig.TILE_START, TileType.START, "출발"),
        city(1, "타이베이", "타이베이", 0, 50_000, build(20_000, 50_000, 80_000), toll(5_000, 25_000, 75_000, 150_000)),
        key(2),
        city(3, "베이징", "베이징", 0, 80_000, build(40_000, 80_000, 120_000), toll(8_000, 40_000, 120_000, 240_000)),
        city(4, "마닐라", "마닐라", 0, 80_000, build(40_000, 80_000, 120_000), toll(8_000, 40_000, 120_000, 240_000)),
        special(5, "제주도", "제주도", 200_000, 300_000),
        city(6, "싱가포르", "싱가포르", 1, 100_000, build(50_000, 100_000, 150_000), toll(10_000, 50_000, 150_000, 300_000)),
        penalty(7),
        city(8, "카이로", "카이로", 1, 100_000, build(50_000, 100_000, 150_000), toll(10_000, 50_000, 150_000, 300_000)),
        city(9, "이스탄불", "이스탄불", 1, 120_000, build(60_000, 120_000, 180_000), toll(12_000, 60_000, 180_000, 360_000)),
        corner(GameConfig.TILE_ISLAND, TileType.ISLAND, "무인도"),
        city(11, "아테네", "아테네", 2, 140_000, build(70_000, 140_000, 210_000), toll(14_000, 70_000, 210_000, 420_000)),
        key(12),
        city(13, "코펜하겐", "코펜하겐", 2, 160_000, build(80_000, 160_000, 240_000), toll(16_000, 80_000, 240_000, 480_000)),
        city(14, "스톡홀름", "스톡홀름", 2, 160_000, build(80_000, 160_000, 240_000), toll(16_000, 80_000, 240_000, 480_000)),
        special(15, "콩코드 여객기", "여객기", 200_000, 300_000),
        city(16, "취리히", "취리히", 3, 180_000, build(90_000, 180_000, 270_000), toll(18_000, 90_000, 270_000, 540_000)),
        key(17),
        city(18, "베를린", "베를린", 3, 180_000, build(90_000, 180_000, 270_000), toll(18_000, 90_000, 270_000, 540_000)),
        city(19, "몬트리올", "몬트리올", 3, 200_000, build(100_000, 200_000, 300_000), toll(20_000, 100_000, 300_000, 600_000)),
        corner(GameConfig.TILE_WELFARE_RECEIVE, TileType.WELFARE_RECEIVE, "사회복지기금"),
        city(21, "부에노스아이레스", "부에노스", 4, 220_000, build(110_000, 220_000, 330_000), toll(22_000, 110_000, 330_000, 660_000)),
        penalty(22),
        city(23, "상파울루", "상파울루", 4, 240_000, build(120_000, 240_000, 360_000), toll(24_000, 120_000, 360_000, 720_000)),
        city(24, "시드니", "시드니", 4, 240_000, build(120_000, 240_000, 360_000), toll(24_000, 120_000, 360_000, 720_000)),
        special(25, "부산", "부산", 500_000, 600_000),
        city(26, "하와이", "하와이", 5, 260_000, build(130_000, 260_000, 390_000), toll(26_000, 130_000, 390_000, 780_000)),
        city(27, "리스본", "리스본", 5, 260_000, build(130_000, 260_000, 390_000), toll(26_000, 130_000, 390_000, 780_000)),
        special(28, "퀸엘리자베스호", "여객선", 300_000, 250_000),
        city(29, "마드리드", "마드리드", 5, 280_000, build(140_000, 280_000, 420_000), toll(28_000, 140_000, 420_000, 840_000)),
        corner(GameConfig.TILE_SPACE, TileType.SPACE, "우주여행"),
        city(31, "도쿄", "도쿄", 6, 300_000, build(150_000, 300_000, 450_000), toll(30_000, 150_000, 450_000, 900_000)),
        special(32, "컬럼비아호", "우주선", 450_000, 400_000),
        city(33, "파리", "파리", 6, 320_000, build(160_000, 320_000, 480_000), toll(32_000, 160_000, 480_000, 960_000)),
        city(34, "로마", "로마", 6, 320_000, build(160_000, 320_000, 480_000), toll(32_000, 160_000, 480_000, 960_000)),
        key(35),
        city(36, "런던", "런던", 7, 350_000, build(180_000, 350_000, 520_000), toll(35_000, 175_000, 525_000, 1_050_000)),
        city(37, "뉴욕", "뉴욕", 7, 350_000, build(180_000, 350_000, 520_000), toll(35_000, 175_000, 525_000, 1_050_000)),
        Tile(38, TileType.WELFARE_PAY, "사회복지기금 접수처", "기금접수"),
        special(39, "서울", "서울", 1_000_000, 2_000_000),
    )

    init {
        check(tiles.size == GameConfig.BOARD_SIZE) { "보드는 ${GameConfig.BOARD_SIZE}칸이어야 합니다" }
        tiles.forEachIndexed { i, t -> check(t.index == i) { "칸 번호가 어긋났습니다: $i" } }
    }

    private fun build(villa: Int, building: Int, hotel: Int) = intArrayOf(villa, building, hotel)
    private fun toll(land: Int, villa: Int, building: Int, hotel: Int) = intArrayOf(land, villa, building, hotel)

    private fun city(i: Int, name: String, short: String, group: Int, price: Int, build: IntArray, toll: IntArray) =
        Tile(i, TileType.CITY, name, short, group, price, build, toll)

    private fun special(i: Int, name: String, short: String, price: Int, toll: Int) =
        Tile(i, TileType.SPECIAL, name, short, -1, price, Tile.EMPTY, intArrayOf(toll))

    private fun key(i: Int) = Tile(i, TileType.GOLDEN_KEY, "황금열쇠", "황금열쇠")

    private fun penalty(i: Int) = Tile(i, TileType.PENALTY, "벌칙", "벌칙")

    private fun corner(i: Int, type: TileType, name: String) = Tile(i, type, name)
}
