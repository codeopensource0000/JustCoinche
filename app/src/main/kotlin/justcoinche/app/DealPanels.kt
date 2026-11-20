package justcoinche.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.engine.ContractType
import justcoinche.engine.Game
import justcoinche.engine.Round
import justcoinche.engine.Seat
import justcoinche.engine.Team
import justcoinche.engine.Trick

/**
 * How the last deal ended, with the four hands on demand, then the next deal;
 * or, when the game is over, who won and a new game.
 */
@Composable
internal fun DealResultPanel(game: Game, geometry: TableGeometry, onNextDeal: () -> Unit, onNewGame: () -> Unit) {
    val round = checkNotNull(game.results.lastOrNull()).round
    var showHands by remember(game) { mutableStateOf(false) }
    Panel(highlighted = true) {
        if (showHands) {
            AllHands(round, geometry)
        } else {
            val lines = dealSummary(game)
            FeltText(lines.first(), size = 17.sp, bold = true, color = headlineColor(round))
            lines.drop(1).forEach { FeltText(it, size = 14.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showHands = !showHands }) {
                Text(if (showHands) "Résultat" else "Voir les mains", color = Color.White)
            }
            val gold = ButtonDefaults.buttonColors(containerColor = GOLD, contentColor = Color.Black)
            if (game.isOver) {
                Button(onClick = onNewGame, colors = gold) { Text("Nouvelle partie") }
            } else {
                Button(onClick = onNextDeal, colors = gold) { Text("Donne suivante") }
            }
        }
    }
}

private fun headlineColor(round: Round): Color = when {
    round.phase != Round.Phase.OVER -> Color.White
    round.isContractMade -> MADE
    else -> FAILED
}

/** The lines describing the last deal and, at the end, the game. */
internal fun dealSummary(game: Game): List<String> {
    val result = checkNotNull(game.results.lastOrNull())
    val round = result.round
    val deal = if (round.phase == Round.Phase.REDEAL) {
        listOf("Tout le monde a passé : on redistribue.")
    } else {
        val contract = checkNotNull(round.contract)
        val play = checkNotNull(round.cardPlay)
        val score = checkNotNull(result.score)
        listOf(
            "${contract.declarer.label} : ${contract.label}, " + if (round.isContractMade) "réussi" else "chuté",
            "Points faits : Nous ${play.points(Team.NORTH_SOUTH)} · Eux ${play.points(Team.EAST_WEST)}",
            "Marque : Nous ${score.northSouth} · Eux ${score.eastWest}",
        )
    }
    val end = when (game.winner) {
        null -> emptyList()
        Team.NORTH_SOUTH -> listOf("Partie gagnée, ${game.scores.northSouth} à ${game.scores.eastWest} !")
        Team.EAST_WEST -> listOf("Partie perdue, ${game.scores.northSouth} à ${game.scores.eastWest}.")
    }
    return deal + end
}

/** The four hands as they were dealt, to understand what happened. */
@Composable
private fun AllHands(round: Round, geometry: TableGeometry) {
    val type = round.contract?.type ?: ContractType.NoTrump
    val size = smallCard(geometry, SMALL_HAND_CARD)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Seat.NORTH, Seat.WEST, Seat.EAST, Seat.SOUTH).forEach { seat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FeltText(if (seat == HUMAN) "Toi" else seat.label, Modifier.width(52.dp))
                sortedForDisplay(round.deal.hand(seat), type).forEach { CardFace(it, size) }
            }
        }
    }
}

/** The last-trick and signal buttons, top right, and the last trick when asked for. */
@Composable
internal fun Toolbar(round: Round, geometry: TableGeometry, onSignal: (String) -> Unit) {
    var showLast by remember { mutableStateOf(false) }
    val last = round.cardPlay?.tricks?.lastOrNull()
    Column(Modifier.place(geometry.toolbar), horizontalAlignment = Alignment.End) {
        TextButton(onClick = { showLast = !showLast }, enabled = last != null) {
            Text("Dernier pli", color = if (last != null) Color.White else DIM_TEXT)
        }
        SignalButton(round, onSignal)
    }
    if (showLast && last != null) {
        Box(Modifier.place(geometry.middle), contentAlignment = Alignment.Center) {
            LastTrick(last, checkNotNull(round.contract).type, geometry) { showLast = false }
        }
    }
}

/** The last complete trick, each card under its player's name; a tap closes it. */
@Composable
private fun LastTrick(trick: Trick, type: ContractType, geometry: TableGeometry, onClose: () -> Unit) {
    val winner = trick.winner(type)
    Panel(Modifier.clickable(onClick = onClose)) {
        FeltText("Dernier pli", bold = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            trick.cards.forEachIndexed { index, card ->
                val seat = Seat.entries[(trick.leader.ordinal + index) % Seat.entries.size]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FeltText(if (seat == HUMAN) "Toi" else seat.label, size = 11.sp)
                    CardFace(card, geometry.trickCard, highlighted = seat == winner)
                }
            }
        }
    }
}

/**
 * Flags the current move as worth a look, with an optional note: it goes to
 * the game record, where the move can be found and replayed exactly.
 */
@Composable
private fun SignalButton(round: Round, onSignal: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var done by remember(round) { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    TextButton(onClick = { open = true }, enabled = !done) {
        Text(if (done) "Signalé ✓" else "Signaler", color = if (done) GOLD else Color.White)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Signaler ce coup") },
            text = {
                OutlinedTextField(note, { note = it }, label = { Text("Ce qui ne va pas (facultatif)") })
            },
            confirmButton = {
                TextButton(onClick = {
                    onSignal(note)
                    note = ""
                    open = false
                    done = true
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } },
        )
    }
}

private fun smallCard(geometry: TableGeometry, heightShare: Float): DpSize =
    DpSize(geometry.height * heightShare / TableGeometry.CARD_RATIO, geometry.height * heightShare)

private val MADE = Color(0xFF7CDB8A)
private val FAILED = Color(0xFFFF8A80)
private const val SMALL_HAND_CARD = 0.11f
