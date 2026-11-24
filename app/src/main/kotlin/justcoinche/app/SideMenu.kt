package justcoinche.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.engine.DealResult
import justcoinche.engine.Game
import justcoinche.engine.Round
import justcoinche.engine.Seat

private enum class MenuPage(val title: String) {
    SCORES("Scores"),
    LAST_TRICK("Dernier pli"),
    SIGNAL("Signaler"),
    OPTIONS("Options"),
    PACE("Rythme"),
    NEW_GAME("Nouvelle partie"),
}

/** What the menu can do to the game. */
internal class MenuActions(
    val onClose: () -> Unit,
    val onSignal: (String) -> Unit,
    val onNewGame: () -> Unit,
    val onChoice: (AiChoice) -> Unit,
    val onTiming: (Timing) -> Unit,
)

/**
 * The menu, on a wooden board that slides over the table: tabs on the left,
 * the chosen page on a sheet of paper on the right. The game waits meanwhile;
 * "Continuer" or the back gesture returns to the table.
 */
@Composable
internal fun SideMenu(open: Boolean, game: Game, choice: AiChoice, timing: Timing, actions: MenuActions) {
    BackHandler(enabled = open, onBack = actions.onClose)
    AnimatedVisibility(
        open,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
    ) {
        var page by remember { mutableStateOf(MenuPage.SCORES) }
        Wood(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxSize().safeDrawingPadding().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.width(TAB_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MenuPage.entries.forEach { tab -> Tab(tab.title, selected = page == tab) { page = tab } }
                    Spacer(Modifier.weight(1f))
                    Tab("▶  Continuer", selected = false, onClick = actions.onClose)
                }
                LightPanel(Modifier.weight(1f).fillMaxHeight()) {
                    Text(page.title, color = INK.copy(alpha = 0.6f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    when (page) {
                        MenuPage.SCORES -> ScoresPage(game)
                        MenuPage.LAST_TRICK -> LastTrickPage(game.shownRound)
                        MenuPage.SIGNAL -> SignalPage(actions.onSignal)
                        MenuPage.OPTIONS -> OptionsPage(choice, actions.onChoice)
                        MenuPage.PACE -> PacePage(timing, actions.onTiming)
                        MenuPage.NEW_GAME -> NewGamePage(actions.onNewGame)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(38.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PAPER else TAB,
        shadowElevation = if (selected) 0.dp else 3.dp,
    ) {
        Text(
            text.uppercase(),
            Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            color = if (selected) INK else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Every deal of the game with what it scored, and the running totals. */
@Composable
private fun ColumnScope.ScoresPage(game: Game) {
    ScoreRow("Donne", "Contrat", "Nous", "Eux", bold = true)
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
        itemsIndexed(game.results) { index, result -> ScoreRow("${index + 1}", contractOf(result), result) }
    }
    val (us, them) = game.scores.northSouth.toString() to game.scores.eastWest.toString()
    ScoreRow("", "Total sur ${game.rules.targetScore}", us, them, bold = true)
}

@Composable
private fun ScoreRow(number: String, contract: String, result: DealResult) {
    val score = result.score
    ScoreRow(number, contract, score?.northSouth?.toString() ?: "–", score?.eastWest?.toString() ?: "–")
}

@Composable
private fun ScoreRow(number: String, contract: String, us: String, them: String, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(number, Modifier.weight(NUMBER_COLUMN), color = INK, fontWeight = weight)
        Text(contract, Modifier.weight(CONTRACT_COLUMN), color = INK, fontWeight = weight)
        Text(us, Modifier.weight(1f), color = INK, fontWeight = weight, textAlign = TextAlign.End)
        Text(them, Modifier.weight(1f), color = INK, fontWeight = weight, textAlign = TextAlign.End)
    }
}

private fun contractOf(result: DealResult): String {
    val round = result.round
    val contract = round.contract ?: return "redistribuée"
    val outcome = if (round.isContractMade) "réussi" else "chuté"
    return "${contract.label} ${contract.declarer.label}, $outcome"
}

/** The last complete trick of the deal, each card under its player's name. */
@Composable
private fun LastTrickPage(round: Round?) {
    val play = round?.cardPlay
    val trick = play?.tricks?.lastOrNull()
    if (trick == null) {
        Text("Aucun pli n'a encore été joué dans cette donne.", color = INK)
        return
    }
    val winner = trick.winner(play.contract)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        trick.cards.forEachIndexed { index, card ->
            val seat = Seat.entries[(trick.leader.ordinal + index) % Seat.entries.size]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (seat == HUMAN) "Toi" else seat.label, color = INK, fontWeight = FontWeight.Bold)
                CardFace(card, MENU_CARD, highlighted = seat == winner)
            }
        }
    }
}

/** Flags the current move, with an optional note, in the game record for later analysis. */
@Composable
private fun SignalPage(onSignal: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    Text("Un coup te semble faux ? Il sera retrouvé et rejoué exactement.", color = INK)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Ce qui ne va pas (facultatif)") })
    Spacer(Modifier.height(8.dp))
    if (done) {
        Text("Signalé ✓", color = INK, fontWeight = FontWeight.Bold)
    } else {
        Button(
            onClick = {
                onSignal(note)
                done = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = PILL, contentColor = Color.White),
        ) { Text("Enregistrer") }
    }
}

@Composable
private fun NewGamePage(onNewGame: () -> Unit) {
    Text("Abandonner la partie en cours et en commencer une nouvelle ?", color = INK)
    Text("La partie abandonnée reste enregistrée.", color = INK.copy(alpha = 0.6f))
    Spacer(Modifier.height(12.dp))
    val dark = ButtonDefaults.buttonColors(containerColor = PILL, contentColor = Color.White)
    Button(onClick = onNewGame, colors = dark) {
        Text("Nouvelle partie")
    }
}

private val TAB_WIDTH = 220.dp
// Column widths of the score sheet, relative to the score columns.
private const val NUMBER_COLUMN = 0.6f
private const val CONTRACT_COLUMN = 2.4f
private val TAB = Color(0xFF6E3B1A)
private val MENU_CARD = DpSize(72.dp, 72.dp * TableGeometry.CARD_RATIO)
