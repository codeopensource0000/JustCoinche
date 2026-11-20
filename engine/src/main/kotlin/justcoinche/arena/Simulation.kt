package justcoinche.arena

import justcoinche.engine.Game
import justcoinche.engine.Player
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Team
import justcoinche.record.GameStore
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Plays complete games between four computer players and writes their
 * records, in the app's format, for a human or a script to read:
 *
 *     ./gradlew :engine:simulate --args="20 regles 42"
 *
 * Arguments: number of games, players ("regles" or "aleatoire"), seed. The
 * records go to engine/build/simulations/<date-time>/, and a summary is printed.
 */
fun main(args: Array<String>) {
    val games = args.getOrNull(0)?.toInt() ?: DEFAULT_GAMES
    val kind = args.getOrNull(1) ?: "regles"
    val seed = args.getOrNull(2)?.toLong() ?: System.nanoTime()
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val dir = File("build/simulations/$stamp")
    val random = Random(seed)
    val players: Map<Seat, Player> = Seat.entries.associateWith {
        when (kind) {
            "regles" -> RulePlayer()
            "aleatoire" -> RandomPlayer(Random(random.nextLong()))
            else -> throw IllegalArgumentException("Unknown players '$kind': regles or aleatoire")
        }
    }
    val store = GameStore(dir)
    val played = List(games) {
        playGame(store.start(Rules(), Seat.entries.random(random)), players, random) { store.save(it) }
    }
    println("Seed $seed, $kind players, records in ${dir.absolutePath}")
    println(summary(played))
}

/** A few figures over finished games, to spot anything odd at a glance. */
fun summary(games: List<Game>): String {
    val deals = games.flatMap { it.results }
    val played = deals.map { it.round }.filter { it.phase == Round.Phase.OVER }
    val contracts = played.map { checkNotNull(it.contract) }
    val made = played.count { it.isContractMade }
    val wins = games.groupingBy { checkNotNull(it.winner) }.eachCount()
    val perGame = "%.1f".format(deals.size.toDouble() / games.size)
    return listOf(
        "Games: ${games.size}, won by North-South ${wins[Team.NORTH_SOUTH] ?: 0}" +
            ", East-West ${wins[Team.EAST_WEST] ?: 0}",
        "Deals: ${deals.size}, redeals ${deals.size - played.size}, per game $perGame",
        "Contracts made: $made of ${played.size} (${percent(made, played.size)})",
        "Capots bid: ${contracts.count { it.isCapot }}, coinched: ${contracts.count { it.coinche.multiplier > 1 }}",
        "Average contract: ${"%.0f".format(contracts.filter { !it.isCapot }.map { it.value }.average())}",
    ).joinToString("\n")
}

private fun percent(part: Int, whole: Int): String = if (whole == 0) "-" else "${part * PERCENT / whole}%"

private const val DEFAULT_GAMES = 20
private const val PERCENT = 100
