package justcoinche.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * The card table, in landscape: the human sits South with their hand fanned
 * along the bottom, the three computer players (rule players) around with
 * their cards face down, the auction then the tricks in the middle. A whole
 * game, saved after every move in [store] so that it resumes where it stopped.
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
    Box(Modifier.fillMaxSize().background(FELT)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val geometry = remember(maxWidth, maxHeight) { TableGeometry(maxWidth, maxHeight) }
            Table(game, round, geometry, update)
            val actions = MiddleActions(
                onBid = { bid -> update(game.call(HUMAN, bid)) },
                onNextDeal = { update(game.deal(newDeal())) },
                onNewGame = { game = store.start(Rules(), Seat.entries.random()) },
            )
            Middle(game, round, geometry, showResult, actions)
            Toolbar(round, geometry) { note -> store.signal(game, note) }
        }
    }
}

/** Everything on the table but the middle panels: cards, names, score. */
@Composable
private fun Table(game: Game, round: Round, geometry: TableGeometry, update: (Game) -> Unit) {
    val type = round.contract?.type ?: ContractType.NoTrump
    ScoreBoard(game, round, Modifier.place(geometry.scoreBoard))
    Seat.entries.forEach { seat ->
        if (seat != HUMAN) OpponentBacks(seat, round.handOf(seat).size, geometry)
        NameTag(round, seat, Modifier.place(geometry.nameTag(seat)))
    }
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
            // The hands are empty at the end of a deal: the result may use their room too.
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

/** A player's name, a dealer's token, and what they said or the contract they hold; gold on their turn. */
@Composable
private fun NameTag(round: Round, seat: Seat, modifier: Modifier) {
    val contract = round.contract
    val said = when {
        contract != null && contract.declarer == seat -> "prend ${contract.label}"
        contract != null -> ""
        else -> round.lastCalls[seat]?.label.orEmpty()
    }
    val active = round.toAct == seat
    val pulse by rememberInfiniteTransition(label = "turn").animateFloat(
        initialValue = 1f,
        targetValue = PULSE_LOW,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "pulse",
    )
    Panel(modifier.alpha(if (active) pulse else 1f), highlighted = active) {
        val dealer = if (seat == round.dealer) "  ●D" else ""
        FeltText((if (seat == HUMAN) "Toi" else seat.label) + dealer, bold = true)
        val waiting = when {
            !active -> " "
            seat == HUMAN -> "à toi"
            else -> "réfléchit…"
        }
        FeltText(said.ifEmpty { waiting }, color = if (said.isEmpty()) DIM_TEXT else GOLD)
    }
}

/** The game score and the deal's contract and points. */
@Composable
private fun ScoreBoard(game: Game, round: Round, modifier: Modifier) {
    Panel(modifier) {
        val score = "Nous ${game.scores[Team.NORTH_SOUTH]} · Eux ${game.scores[Team.EAST_WEST]}"
        FeltText(score, size = 15.sp, bold = true)
        val contract = round.contract
        val play = round.cardPlay
        if (contract != null && play != null) {
            FeltText("${contract.label} · ${contract.declarer.label}", color = GOLD)
            val points = "points ${play.points(Team.NORTH_SOUTH)} – ${play.points(Team.EAST_WEST)}"
            FeltText("$points · sur ${game.rules.targetScore}", size = 11.sp, color = DIM_TEXT)
        } else {
            FeltText("partie en ${game.rules.targetScore}", size = 11.sp, color = DIM_TEXT)
        }
    }
}

/** A fresh deal from an unpredictable 64-bit seed. */
private fun newDeal(): Deal = Deal.random(SecureRandom().nextLong())

private const val BOT_PAUSE_MS = 750L
private const val TRICK_PAUSE_MS = 1600L
private const val PULSE_MS = 700
private const val PULSE_LOW = 0.75f
