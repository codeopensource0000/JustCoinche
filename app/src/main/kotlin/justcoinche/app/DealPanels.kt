package justcoinche.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.engine.ContractType
import justcoinche.engine.Game
import justcoinche.engine.Round
import justcoinche.engine.Seat
import justcoinche.engine.Team

/**
 * How the last deal ended, on a sheet of paper, with the four hands on
 * demand, then the next deal; or, when the game is over, who won and a new game.
 */
@Composable
internal fun DealResultPanel(game: Game, geometry: TableGeometry, onNextDeal: () -> Unit, onNewGame: () -> Unit) {
    val round = checkNotNull(game.results.lastOrNull()).round
    var showHands by remember(game) { mutableStateOf(false) }
    LightPanel {
        if (showHands) {
            AllHands(round, geometry)
        } else {
            val lines = dealSummary(game)
            Text(lines.first(), color = headlineColor(round), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            lines.drop(1).forEach { Text(it, color = INK, fontSize = 16.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { showHands = !showHands }) {
                Text(if (showHands) "Résultat" else "Voir les mains", color = INK)
            }
            val dark = ButtonDefaults.buttonColors(containerColor = PILL, contentColor = Color.White)
            if (game.isOver) {
                Button(onClick = onNewGame, colors = dark) { Text("Nouvelle partie") }
            } else {
                Button(onClick = onNextDeal, colors = dark) { Text("Donne suivante") }
            }
        }
    }
}

private fun headlineColor(round: Round): Color = when {
    round.phase != Round.Phase.OVER -> INK
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
    val size = DpSize(geometry.height * SMALL_CARD / TableGeometry.CARD_RATIO, geometry.height * SMALL_CARD)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(Seat.NORTH, Seat.WEST, Seat.EAST, Seat.SOUTH).forEach { seat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (seat == HUMAN) "Toi" else seat.label, Modifier.width(56.dp), color = INK)
                sortedForDisplay(round.deal.hand(seat), type).forEach { MiniCard(it, size) }
            }
        }
    }
}

private val MADE = Color(0xFF1E8E3E)
private val FAILED = Color(0xFFC62828)
private const val SMALL_CARD = 0.13f
