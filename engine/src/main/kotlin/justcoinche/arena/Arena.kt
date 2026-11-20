package justcoinche.arena

import justcoinche.engine.Deal
import justcoinche.engine.Game
import justcoinche.engine.Player
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Team
import justcoinche.engine.viewFor
import kotlin.math.sqrt
import kotlin.random.Random

/** Makes the player for a seat. Called once per seat and per table, so each player can hold its own state. */
typealias PlayerFactory = (Seat) -> Player

/**
 * Plays [start] to its end, asking each seat's player for its bids and cards.
 * Players speak in turn only: a coinche on the fly is not used by the arena.
 */
fun playOut(start: Round, players: Map<Seat, Player>): Round {
    var round = start
    while (round.phase == Round.Phase.BIDDING) {
        val seat = round.auction.toSpeak
        round = round.call(seat, players.getValue(seat).bid(round.viewFor(seat)))
    }
    while (round.phase == Round.Phase.PLAYING) {
        val seat = checkNotNull(round.cardPlay).toPlay
        round = round.play(players.getValue(seat).play(round.viewFor(seat)))
    }
    return round
}

/** The player whose turn it is in the deal being played bids or plays once. */
fun Game.playTurn(players: Map<Seat, Player>): Game {
    val round = checkNotNull(currentRound) { "No deal is being played" }
    val seat = checkNotNull(round.toAct)
    val player = players.getValue(seat)
    val view = round.viewFor(seat)
    return if (round.phase == Round.Phase.BIDDING) call(seat, player.bid(view)) else play(player.play(view))
}

/**
 * Plays [start] to the end of the game, shuffling each deal from [random].
 * [afterMove] sees every state along the way, for instance to save it.
 */
fun playGame(start: Game, players: Map<Seat, Player>, random: Random, afterMove: (Game) -> Unit = {}): Game {
    var game = start
    while (!game.isOver) {
        game = if (game.currentRound == null) game.deal(Deal.random(random)) else game.playTurn(players)
        afterMove(game)
    }
    return game
}

/** Team A's average lead per deal, both tables together, and the 95% margin of error around it. */
data class MatchResult(val deals: Int, val meanLead: Double, val margin95: Double) {
    /** Whether the lead is larger than chance can explain. */
    val isSignificant: Boolean get() = kotlin.math.abs(meanLead) > margin95
}

/**
 * A duplicate match: each deal is played twice, team A sitting North-South at
 * the first table and East-West at the second, so both teams hold exactly the
 * same cards and the luck of the deal cancels out. A deal's result is team A's
 * lead in points over both tables; a redeal counts 0.
 */
fun duplicateMatch(teamA: PlayerFactory, teamB: PlayerFactory, deals: Int, seed: Long, rules: Rules): MatchResult {
    val random = Random(seed)
    val leads = List(deals) { index ->
        val deal = Deal.random(random)
        val dealer = Seat.entries[index % Seat.entries.size]
        val first = playOut(Round.start(dealer, deal, rules), seating(northSouth = teamA, eastWest = teamB))
        val second = playOut(Round.start(dealer, deal, rules), seating(northSouth = teamB, eastWest = teamA))
        lead(first, Team.NORTH_SOUTH) + lead(second, Team.EAST_WEST)
    }
    return summarize(leads)
}

/** Mean and 95% margin (1.96 standard errors) of per-deal leads. */
internal fun summarize(leads: List<Int>): MatchResult {
    require(leads.size >= 2) { "A match needs at least two deals" }
    val mean = leads.average()
    val variance = leads.sumOf { (it - mean) * (it - mean) } / (leads.size - 1)
    return MatchResult(leads.size, mean, Z_95 * sqrt(variance / leads.size))
}

private fun seating(northSouth: PlayerFactory, eastWest: PlayerFactory): Map<Seat, Player> =
    Seat.entries.associateWith { seat -> if (seat.team == Team.NORTH_SOUTH) northSouth(seat) else eastWest(seat) }

private fun lead(round: Round, team: Team): Int =
    if (round.phase == Round.Phase.OVER) round.score().let { it[team] - it[team.opponent] } else 0

private const val Z_95 = 1.96
