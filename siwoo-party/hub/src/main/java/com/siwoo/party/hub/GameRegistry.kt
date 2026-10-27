package com.siwoo.party.hub

import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.games.bomb.BombModule
import com.siwoo.party.games.boxes.BoxesModule
import com.siwoo.party.games.liar.LiarModule
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.memory.MemoryModule
import com.siwoo.party.games.pirate.PirateModule
import com.siwoo.party.games.reaction.ReactionModule
import com.siwoo.party.games.tug.TugModule
import com.siwoo.party.games.yacht.YachtModule

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
    )

    fun byId(id: String?): GameModule? = games.firstOrNull { it.id == id }
}
