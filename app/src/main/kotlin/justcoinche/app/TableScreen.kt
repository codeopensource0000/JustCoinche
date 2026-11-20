package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
 * The card table, in landscape: the human sits South with their hand along the
 * bottom, the three computer players (rule players) around, the auction then
 * the tricks in the middle. A whole game, saved after every move in [store]
 * so that it resumes where it stopped.
 */
@Composable
fun TableScreen(store: GameStore) {
    val bots = remember { Seat.entries.filter { it != HUMAN }.associateWith { RulePlayer() } }
    var game by remember { mutableStateOf(store.openGame()) }
    // Reset by every move: the result of a deal shows once its last trick has been seen.
    var showResult by remember(game) { mutableStateOf(false) }
    val update: (Game) -> Unit = { next ->
        store.save(next)
        game = next
    }

    // Runs again after every move: the computer players act one after the
    // other, with a pause so that every bid and card can be followed.
    LaunchedEffect(game) {
        val round = game.currentRound
        when {
            round == null && game.results.isEmpty() -> update(game.deal(newDeal()))
            round == null -> {
                delay(TRICK_PAUSE_MS) // the last trick stays on show before the result
                showResult = true
            }
            round.toAct != HUMAN -> {
                delay(if (round.showsCompletedTrick) TRICK_PAUSE_MS else BOT_PAUSE_MS)
                update(game.playTurn(bots))
            }
        }
    }

    val round = game.shownRound ?: return
    Box(Modifier.fillMaxSize().background(FELT).safeDrawingPadding().padding(8.dp)) {
        ContractInfo(round, Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
            FeltText("Nous ${game.scores[Team.NORTH_SOUTH]} · Eux ${game.scores[Team.EAST_WEST]}")
            FeltText("Partie en ${game.rules.targetScore}")
            SignalButton(game) { note -> store.signal(game, note) }
        }
        Seat.entries.filter { it != HUMAN }.forEach { seat ->
            SeatBadge(round, seat, Modifier.align(placeOf(seat)))
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            SeatBadge(round, HUMAN)
            val playable = if (round.toAct == HUMAN) round.viewFor(HUMAN).playableCards else CardSet.EMPTY
            HandRow(round.handOf(HUMAN), round.contract?.type ?: ContractType.NoTrump, playable) { card ->
                update(game.play(card))
            }
        }
        Box(Modifier.align(Alignment.Center).offset(y = (-36).dp)) {
            if (showResult) {
                DealResultPanel(
                    game,
                    onNextDeal = { update(game.deal(newDeal())) },
                    onNewGame = { game = store.start(Rules(), Seat.entries.random()) },
                )
            } else {
                TableCenter(round) { bid -> update(game.call(HUMAN, bid)) }
            }
        }
    }
}

/** The middle of the table during a deal: the bidding panel on the human's turn to speak, else the trick. */
@Composable
private fun TableCenter(round: Round, onBid: (Bid) -> Unit) {
    if (round.toAct == HUMAN && round.phase == Round.Phase.BIDDING) {
        BiddingPanel(round.viewFor(HUMAN).legalBids, onBid)
    } else {
        round.shownTrick?.let { TrickView(it, checkNotNull(round.contract).type) }
    }
}

/** A seat's name, marked when it deals or must act, with its last bid or its contract. */
@Composable
private fun SeatBadge(round: Round, seat: Seat, modifier: Modifier = Modifier) {
    val name = if (seat == HUMAN) "Toi" else seat.label
    val dealer = if (seat == round.dealer) " · donneur" else ""
    val contract = round.contract
    val said = when {
        contract != null && contract.declarer == seat -> "prend : ${contract.label}"
        contract != null -> ""
        else -> round.lastCalls[seat]?.label.orEmpty()
    }
    val border = if (round.toAct == seat) BorderStroke(2.dp, HIGHLIGHT) else null
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = PANEL, border = border) {
        FeltText(name + dealer, said, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** The contract and the card points won so far by each team in this deal. */
@Composable
private fun ContractInfo(round: Round, modifier: Modifier) {
    val contract = round.contract ?: return
    val play = checkNotNull(round.cardPlay)
    FeltText(
        "Contrat : ${contract.label} (${contract.declarer.label})",
        "Points : Nous ${play.points(Team.NORTH_SOUTH)} · Eux ${play.points(Team.EAST_WEST)}",
        "Pli ${minOf(play.tricks.size + 1, Deal.HAND_SIZE)} / ${Deal.HAND_SIZE}",
        modifier = modifier,
    )
}

private fun placeOf(seat: Seat): Alignment = when (seat) {
    Seat.NORTH -> Alignment.TopCenter
    Seat.WEST -> Alignment.CenterStart
    Seat.EAST -> Alignment.CenterEnd
    Seat.SOUTH -> Alignment.BottomCenter
}

/** A fresh deal from an unpredictable 64-bit seed. */
private fun newDeal(): Deal = Deal.random(SecureRandom().nextLong())

private val FELT = Color(0xFF1E6B3A)
private const val BOT_PAUSE_MS = 700L
private const val TRICK_PAUSE_MS = 1500L
