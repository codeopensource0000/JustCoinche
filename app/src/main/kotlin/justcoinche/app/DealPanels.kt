package justcoinche.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import justcoinche.engine.ContractType
import justcoinche.engine.Game
import justcoinche.engine.Round
import justcoinche.engine.Seat
import justcoinche.engine.Team

/**
 * How the last deal ended, with the four hands on demand, then the next deal;
 * or, when the game is over, who won and a new game.
 */
@Composable
internal fun DealResultPanel(game: Game, onNextDeal: () -> Unit, onNewGame: () -> Unit) {
    val round = checkNotNull(game.results.lastOrNull()).round
    var showHands by remember(game) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = PANEL) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showHands) AllHands(round) else FeltText(dealSummary(game))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showHands = !showHands }) {
                    Text(if (showHands) "Résultat" else "Voir les mains")
                }
                if (game.isOver) {
                    Button(onClick = onNewGame) { Text("Nouvelle partie") }
                } else {
                    Button(onClick = onNextDeal) { Text("Donne suivante") }
                }
            }
        }
    }
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
private fun AllHands(round: Round) {
    val type = round.contract?.type ?: ContractType.NoTrump
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Seat.NORTH, Seat.WEST, Seat.EAST, Seat.SOUTH).forEach { seat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FeltText(if (seat == HUMAN) "Toi" else seat.label, modifier = Modifier.width(52.dp))
                sortedForDisplay(round.deal.hand(seat), type).forEach { CardFace(it, SMALL_CARD_WIDTH) }
            }
        }
    }
}

/**
 * Flags the current move as worth a look, with an optional note: it goes to
 * the game record, where the move can be found and replayed exactly.
 */
@Composable
internal fun SignalButton(game: Game, onSignal: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var done by remember(game) { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    TextButton(onClick = { open = true }, enabled = !done) { Text(if (done) "Signalé ✓" else "Signaler un coup") }
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

private val SMALL_CARD_WIDTH = 30.dp
