package justcoinche.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.arena.RulePlayer
import justcoinche.arena.playTurn
import justcoinche.engine.Bid
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Game
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Team
import justcoinche.engine.viewFor
import justcoinche.record.GameStore
import kotlinx.coroutines.delay
import java.security.SecureRandom

/**
 * The card table, in landscape: the human's hand along the bottom edge, the
 * three computer players (rule players) engraved around the felt, the auction
 * then the tricks in the middle, the menu on a wooden side panel. A whole
 * game, saved after every move in [store] so that it resumes where it stopped.
 */
@Composable
fun TableScreen(store: GameStore) {
    val bots = remember { Seat.entries.filter { it != HUMAN }.associateWith { RulePlayer() } }
    var game by remember { mutableStateOf(store.openGame()) }
    // Reset by every move: the result of a deal shows once its last trick has been seen.
    var showResult by remember(game) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val update: (Game) -> Unit = { next ->
        store.save(next)
        game = next
    }

    // Runs again after every move: the computer players act one after the
    // other, with a pause so that every bid and card can be followed. The
    // game waits while the menu is open.
    LaunchedEffect(game, menuOpen) {
        val round = game.currentRound
        when {
            menuOpen -> Unit
            round == null && game.results.isEmpty() -> update(game.deal(newDeal()))
            round == null -> {
                delay(TRICK_PAUSE_MS) // the last trick is gathered before the result
                showResult = true
            }
            round.toAct != HUMAN -> {
                delay(if (round.showsCompletedTrick) TRICK_PAUSE_MS else BOT_PAUSE_MS)
                update(game.playTurn(bots))
            }
        }
    }

    val round = game.shownRound ?: return
    Felt {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val geometry = remember(maxWidth, maxHeight) { TableGeometry(maxWidth, maxHeight) }
            MenuButton(Modifier.place(geometry.menuButton)) { menuOpen = true }
            ScoreLine(game, round, Modifier.place(geometry.scoreLine))
            Seat.entries.forEach { seat -> NameTag(round, seat, Modifier.place(geometry.nameTag(seat))) }
            Cards(game, round, geometry, update)
            val actions = MiddleActions(
                onBid = { bid -> update(game.call(HUMAN, bid)) },
                onNextDeal = { update(game.deal(newDeal())) },
                onNewGame = { game = store.start(Rules(), Seat.entries.random()) },
            )
            Middle(game, round, geometry, showResult, actions)
        }
        SideMenu(
            open = menuOpen,
            game = game,
            onClose = { menuOpen = false },
            onSignal = { note -> store.signal(game, note) },
            onNewGame = {
                game = store.start(Rules(), Seat.entries.random())
                menuOpen = false
            },
        )
    }
}

/** The trick in the middle and the human's hand. */
@Composable
private fun Cards(game: Game, round: Round, geometry: TableGeometry, update: (Game) -> Unit) {
    val type = round.contract?.type ?: ContractType.NoTrump
    round.shownTrick?.let { TrickLayer(it, type, round.handOf(HUMAN), geometry) }
    val playable = if (round.toAct == HUMAN && round.phase == Round.Phase.PLAYING) {
        round.viewFor(HUMAN).playableCards
    } else {
        CardSet.EMPTY
    }
    HandFan(sortedForDisplay(round.handOf(HUMAN), type), playable, geometry) { card -> update(game.play(card)) }
}

/** What the human can do from the middle panels. */
private class MiddleActions(val onBid: (Bid) -> Unit, val onNextDeal: () -> Unit, val onNewGame: () -> Unit)

/** The middle of the table: the bidding panel on the human's turn, or the deal's result. */
@Composable
private fun Middle(game: Game, round: Round, geometry: TableGeometry, showResult: Boolean, actions: MiddleActions) {
    val middle = geometry.middle
    when {
        showResult -> {
            // The hand is empty at the end of a deal: the result may use its room too.
            val area = middle.copy(height = geometry.height - middle.top - TableGeometry.MARGIN)
            Box(Modifier.place(area), contentAlignment = Alignment.Center) {
                DealResultPanel(game, geometry, actions.onNextDeal, actions.onNewGame)
            }
        }
        round.toAct == HUMAN && round.phase == Round.Phase.BIDDING ->
            Box(Modifier.place(middle), contentAlignment = Alignment.Center) {
                BiddingPanel(round.viewFor(HUMAN).legalBids, actions.onBid)
            }
    }
}

/**
 * A player's name engraved in the felt, a small mark above it, the dealer's
 * chip beside it, and under it a pill with their last bid or their contract.
 * The name lights up in gold on their turn.
 */
@Composable
private fun NameTag(round: Round, seat: Seat, modifier: Modifier) {
    val contract = round.contract
    val said = when {
        contract != null && contract.declarer == seat -> contract.label
        contract != null -> null
        else -> round.lastCalls[seat]?.label
    }
    val active = round.toAct == seat
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Engraved(if (seat == HUMAN) "Toi" else seat.label, lit = active)
            if (seat == round.dealer) DealerChip()
        }
        when {
            said != null -> Pill(said, gold = contract != null)
            active && seat == HUMAN && round.phase == Round.Phase.PLAYING -> Pill("à toi !", gold = true)
        }
    }
}

/** The game score and, during the play, the points won so far in the deal. */
@Composable
private fun ScoreLine(game: Game, round: Round, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Engraved("Nous ${game.scores[Team.NORTH_SOUTH]} · Eux ${game.scores[Team.EAST_WEST]}", size = 16.sp)
        val play = round.cardPlay
        val detail = if (play != null && round.contract != null) {
            "donne ${play.points(Team.NORTH_SOUTH)} – ${play.points(Team.EAST_WEST)} · sur ${game.rules.targetScore}"
        } else {
            "partie en ${game.rules.targetScore}"
        }
        Engraved(detail, size = 12.sp)
    }
}

/** The square button that opens the side menu. */
@Composable
private fun MenuButton(modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp), color = SHADE) {
        Box(contentAlignment = Alignment.Center) { Text("☰", color = Color.White, fontSize = 26.sp) }
    }
}

/** A fresh deal from an unpredictable 64-bit seed. */
private fun newDeal(): Deal = Deal.random(SecureRandom().nextLong())

private val SHADE = Color(0x33000000)
private const val BOT_PAUSE_MS = 800L
private const val TRICK_PAUSE_MS = 1700L
