package com.siwoo.party.hub

import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.games.avoidbid.AvoidBidModule
import com.siwoo.party.games.balloon.BalloonModule
import com.siwoo.party.games.bingo.BingoModule
import com.siwoo.party.games.baseball.BaseballModule
import com.siwoo.party.games.blackjack.BlackjackModule
import com.siwoo.party.games.matches.MatchesModule
import com.siwoo.party.games.math.MathModule
import com.siwoo.party.games.minefield.MinefieldModule
import com.siwoo.party.games.mole.MoleModule
import com.siwoo.party.games.oddone.OddOneModule
import com.siwoo.party.games.oldmaid.OldMaidModule
import com.siwoo.party.games.onecard.OneCardModule
import com.siwoo.party.games.redlight.RedlightModule
import com.siwoo.party.games.alkkagi.AlkkagiModule
import com.siwoo.party.games.bomb.BombModule
import com.siwoo.party.games.boxes.BoxesModule
import com.siwoo.party.games.codetile.CodeTileModule
import com.siwoo.party.games.colorcode.ColorCodeModule
import com.siwoo.party.games.connect4.Connect4Module
import com.siwoo.party.games.charades.CharadesModule
import com.siwoo.party.games.chosung.ChosungModule
import com.siwoo.party.games.clap369.Clap369Module
import com.siwoo.party.games.doubt.DoubtModule
import com.siwoo.party.games.drawing.DrawingModule
import com.siwoo.party.games.flood.FloodModule
import com.siwoo.party.games.forehead.ForeheadModule
import com.siwoo.party.games.highlow.HighLowModule
import com.siwoo.party.games.ddakji.DdakjiModule
import com.siwoo.party.games.gomoku.GomokuModule
import com.siwoo.party.games.liar.LiarModule
import com.siwoo.party.games.liardice.LiarDiceModule
import com.siwoo.party.games.horserace.HorseRaceModule
import com.siwoo.party.games.janggi.JanggiModule
import com.siwoo.party.games.jumprope.JumpRopeModule
import com.siwoo.party.games.airhockey.AirHockeyModule
import com.siwoo.party.games.bobber.BobberModule
import com.siwoo.party.games.count.CountModule
import com.siwoo.party.games.digitmemo.DigitMemoModule
import com.siwoo.party.games.crown.CrownModule
import com.siwoo.party.games.ludo.LudoModule
import com.siwoo.party.games.polyblock.PolyblockModule
import com.siwoo.party.games.reversi.ReversiModule
import com.siwoo.party.games.rhythm.RhythmModule
import com.siwoo.party.games.sevens.SevensModule
import com.siwoo.party.games.shutbox.ShutBoxModule
import com.siwoo.party.games.simon.SimonModule
import com.siwoo.party.games.snakeladder.SnakeLadderModule
import com.siwoo.party.games.umul.UmulModule
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.memory.MemoryModule
import com.siwoo.party.games.pirate.PirateModule
import com.siwoo.party.games.reaction.ReactionModule
import com.siwoo.party.games.rps.RpsModule
import com.siwoo.party.games.sequence.SequenceModule
import com.siwoo.party.games.speed.SpeedModule
import com.siwoo.party.games.spotdiff.SpotDiffModule
import com.siwoo.party.games.stack.StackModule
import com.siwoo.party.games.stroop.StroopModule
import com.siwoo.party.games.tensec.TenSecModule
import com.siwoo.party.games.updown.UpDownModule
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
        StroopModule,
        SequenceModule,
        TenSecModule,
        RpsModule,
        RedlightModule,
        MoleModule,
        BalloonModule,
        MathModule,
        OneCardModule,
        OldMaidModule,
        BlackjackModule,
        LiarDiceModule,
        StackModule,
        DdakjiModule,
        GomokuModule,
        Connect4Module,
        BaseballModule,
        UpDownModule,
        ForeheadModule,
        DrawingModule,
        ChosungModule,
        Clap369Module,
        CharadesModule,
        // ── 2차 확장 ──
        SnakeLadderModule,
        LudoModule,
        HorseRaceModule,
        JanggiModule,
        UmulModule,
        ReversiModule,
        PolyblockModule,
        BingoModule,
        FloodModule,
        HighLowModule,
        SevensModule,
        DoubtModule,
        CodeTileModule,
        ColorCodeModule,
        MinefieldModule,
        MatchesModule,
        AvoidBidModule,
        SimonModule,
        ShutBoxModule,
        SpeedModule,
        RhythmModule,
        SpotDiffModule,
        OddOneModule,
        JumpRopeModule,
        AirHockeyModule,
        BobberModule,
        CountModule,
        DigitMemoModule,
        CrownModule,
    )

    fun byId(id: String?): GameModule? = games.firstOrNull { it.id == id }

    /** 허브 탭에 나오는 게임 (파티 존 제외) */
    val hubGames: List<GameModule> get() = games.filter { it.category != GameCategory.PARTY }

    /** 파티 존 게임 */
    val partyGames: List<GameModule> get() = games.filter { it.category == GameCategory.PARTY }

    fun inCategory(c: GameCategory): List<GameModule> = games.filter { it.category == c }
}
