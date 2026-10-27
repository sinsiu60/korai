package com.siwoo.party.hub

import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.games.alkkagi.AlkkagiModule
import com.siwoo.party.games.bomb.BombModule
import com.siwoo.party.games.boxes.BoxesModule
import com.siwoo.party.games.liar.LiarModule
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.memory.MemoryModule
import com.siwoo.party.games.pirate.PirateModule
import com.siwoo.party.games.reaction.ReactionModule
import com.siwoo.party.games.tug.TugModule
import com.siwoo.party.games.yacht.YachtModule
import com.siwoo.party.games.yut.YutModule

/**
 * 모음집에 들어 있는 게임 목록 (구현된 게임만 등록).
 * 새 게임 추가: games/<게임>/logic·ui 모듈을 만들고 GameModule 을 구현한 뒤 여기에 한 줄.
 */
object GameRegistry {
    val games: List<GameModule> = listOf(
        MarbleModule,
        ReactionModule,
        TugModule,
        BombModule,
        PirateModule,
        MemoryModule,
        YachtModule,
        BoxesModule,
        LiarModule,
        YutModule,
        AlkkagiModule,
    )

    fun byId(id: String?): GameModule? = games.firstOrNull { it.id == id }

    /** 허브 탭에 나오는 게임 (파티 존 제외) */
    val hubGames: List<GameModule> get() = games.filter { it.category != GameCategory.PARTY }

    /** 파티 존 게임 */
    val partyGames: List<GameModule> get() = games.filter { it.category == GameCategory.PARTY }

    fun inCategory(c: GameCategory): List<GameModule> = games.filter { it.category == c }
}
