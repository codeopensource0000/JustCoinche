package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import justcoinche.arena.RulePlayer
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Team
import justcoinche.engine.viewFor
import kotlinx.coroutines.delay
import java.security.SecureRandom

/**
 * The card table, in landscape: the human sits South with their hand along the
 * bottom, the three computer players (rule players) around, the auction then
 * the tricks in the middle. One deal at a time, for now: no game score yet.
 */
@Composable
fun TableScreen() {
    val bots = remember { Seat.entries.filter { it != HUMAN }.associateWith { RulePlayer() } }
    var round by remember { mutableStateOf(newRound(Seat.WEST)) }
    var showResult by remember { mutableStateOf(false) }

    // Runs again each time the round changes: the computer players act one
    // after the other, with a pause so that every bid and card can be followed.
    LaunchedEffect(round) {
        val seat = round.seatToAct
        if (seat == null) {
            delay(TRICK_PAUSE_MS) // the last trick stays on show before the result
            showResult = true
        } else if (seat != HUMAN) {
            delay(if (round.showsCompletedTrick) TRICK_PAUSE_MS else BOT_PAUSE_MS)
            round = round.botStep(bots)
        }
    }

    Box(Modifier.fillMaxSize().background(FELT).safeDrawingPadding().padding(8.dp)) {
        InfoPanel(round, Modifier.align(Alignment.TopStart))
        Seat.entries.filter { it != HUMAN }.forEach { seat ->
            SeatBadge(round, seat, Modifier.align(placeOf(seat)))
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            SeatBadge(round, HUMAN)
            val toPlay = if (round.seatToAct == HUMAN && round.phase == Round.Phase.PLAYING) {
                round.viewFor(HUMAN).playableCards
            } else {
                CardSet.EMPTY
            }
            HandRow(round.handOf(HUMAN), round.contract?.type ?: ContractType.NoTrump, toPlay) { card ->
                round = round.play(card)
            }
        }
        Box(Modifier.align(Alignment.Center).offset(y = (-36).dp)) {
            when {
                showResult -> ResultPanel(round) {
                    showResult = false
                    round = newRound(round.dealer.next)
                }
                round.seatToAct == HUMAN && round.phase == Round.Phase.BIDDING ->
                    BiddingPanel(round.viewFor(HUMAN).legalBids) { bid -> round = round.call(HUMAN, bid) }
                else -> round.shownTrick?.let { TrickView(it, checkNotNull(round.contract).type) }
            }
        }
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
    val border = if (round.seatToAct == seat) BorderStroke(2.dp, HIGHLIGHT) else null
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = PANEL, border = border) {
        FeltText(name + dealer, said, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** The contract and the card points won so far by each team. */
@Composable
private fun InfoPanel(round: Round, modifier: Modifier) {
    val contract = round.contract ?: return
    val play = checkNotNull(round.cardPlay)
    FeltText(
        "Contrat : ${contract.label} (${contract.declarer.label})",
        "Nous ${play.points(Team.NORTH_SOUTH)} · Eux ${play.points(Team.EAST_WEST)}",
        "Pli ${minOf(play.tricks.size + 1, Deal.HAND_SIZE)} / ${Deal.HAND_SIZE}",
        modifier = modifier,
    )
}

/** How the deal ended, and the button for the next one. */
@Composable
private fun ResultPanel(round: Round, onNext: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = PANEL) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (round.phase == Round.Phase.REDEAL) {
                FeltText("Tout le monde a passé : on redistribue.")
            } else {
                val contract = checkNotNull(round.contract)
                val play = checkNotNull(round.cardPlay)
                val score = round.score()
                val outcome = if (round.isContractMade) "réussi" else "chuté"
                FeltText(
                    "${contract.declarer.label} : ${contract.label}, $outcome",
                    "Points faits : Nous ${play.points(Team.NORTH_SOUTH)} · Eux ${play.points(Team.EAST_WEST)}",
                    "Marque : Nous ${score.northSouth} · Eux ${score.eastWest}",
                )
            }
            Button(onClick = onNext) { Text("Donne suivante") }
        }
    }
}

private fun placeOf(seat: Seat): Alignment = when (seat) {
    Seat.NORTH -> Alignment.TopCenter
    Seat.WEST -> Alignment.CenterStart
    Seat.EAST -> Alignment.CenterEnd
    Seat.SOUTH -> Alignment.BottomCenter
}

/** A fresh deal from an unpredictable 64-bit seed, with the default rules. */
private fun newRound(dealer: Seat): Round = Round.start(dealer, Deal.random(SecureRandom().nextLong()), Rules())

private val FELT = Color(0xFF1E6B3A)
private const val BOT_PAUSE_MS = 700L
private const val TRICK_PAUSE_MS = 1500L
