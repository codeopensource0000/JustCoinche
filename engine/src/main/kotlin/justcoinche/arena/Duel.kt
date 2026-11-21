package justcoinche.arena

import justcoinche.ai.AiCatalog
import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.Deal
import justcoinche.engine.Player
import justcoinche.engine.PlayerView
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Team

/**
 * A duplicate match between two players of the AI catalog, from the command line:
 *
 *     ./gradlew :engine:duel --args="ia2 ia1 1000 7"
 *
 * Arguments: the two AI ids, the number of deals, the seed. Prints team A's
 * lead per deal with its 95% margin, how often each side's contracts were
 * made, and how long each side thinks per decision.
 */
fun main(args: Array<String>) {
    val (idA, idB) = args.getOrNull(0) to args.getOrNull(1)
    require(idA != null && idB != null) { "Usage: duel <ai A> <ai B> [deals] [seed]" }
    val options = args.drop(2)
    val deals = options.getOrNull(0)?.toInt() ?: DEFAULT_DEALS
    val seed = options.getOrNull(1)?.toLong() ?: 1L
    val report = duel(idA, idB, deals, seed, Rules())
    println(report)
}

/** Decision counts and thinking time of one side of a match, safe to share between threads. */
class DecisionStats {
    var decisions = 0
        private set
    private var totalNanos = 0L
    private var maxNanos = 0L

    fun <T> measure(decision: () -> T): T {
        val start = System.nanoTime()
        return decision().also { record(System.nanoTime() - start) }
    }

    // The arena plays several deals at once: the counts are shared between threads.
    @Synchronized
    private fun record(elapsed: Long) {
        decisions++
        totalNanos += elapsed
        maxNanos = maxOf(maxNanos, elapsed)
    }

    @Synchronized
    override fun toString(): String {
        val mean = if (decisions == 0) 0.0 else totalNanos / NANOS_PER_MILLI / decisions
        return "%d decisions, %.2f ms mean, %.1f ms max".format(decisions, mean, maxNanos / NANOS_PER_MILLI)
    }
}

/** Wraps a player to time its decisions; passes the deal on to a player that may see it. */
class TimedPlayer(private val inner: Player, private val stats: DecisionStats) : Player, Omniscient {
    override fun bid(view: PlayerView): Bid = stats.measure { inner.bid(view) }
    override fun play(view: PlayerView): Card = stats.measure { inner.play(view) }
    override fun see(deal: Deal) {
        (inner as? Omniscient)?.see(deal)
    }
}

/** Plays the duplicate match and describes its result in a few lines. */
fun duel(idA: String, idB: String, deals: Int, seed: Long, rules: Rules): String {
    val (a, b) = AiCatalog[idA] to AiCatalog[idB]
    val (statsA, statsB) = DecisionStats() to DecisionStats()
    // Each side counts its contracts per table: [taken, made].
    val contractsA = IntArray(2)
    val contractsB = IntArray(2)
    val rounds = duplicateRounds(
        teamA = { seat -> TimedPlayer(a.create(seat, seed), statsA) },
        teamB = { seat -> TimedPlayer(b.create(seat, seed), statsB) },
        deals = deals,
        seed = seed,
        rules = rules,
    )
    rounds.forEach { (first, second) ->
        count(first, Team.NORTH_SOUTH, contractsA, contractsB)
        count(second, Team.EAST_WEST, contractsA, contractsB)
    }
    val result = summarize(rounds.map { (first, second) -> duplicateLead(first, second) })
    return listOf(
        "$idA (${a.name}) vs $idB (${b.name}), $deals deals, seed $seed",
        "Lead of $idA per deal: %.1f ± %.1f%s".format(
            result.meanLead,
            result.margin95,
            if (result.isSignificant) " (significant)" else " (not significant)",
        ),
        "Contracts made: $idA ${contractsA[1]}/${contractsA[0]}, $idB ${contractsB[1]}/${contractsB[0]}",
        "Thinking: $idA $statsA; $idB $statsB",
    ).joinToString("\n")
}

/** Counts the contract of a played deal for the side that took it; [teamA] is where A sat. */
private fun count(round: Round, teamA: Team, contractsA: IntArray, contractsB: IntArray) {
    val contract = round.contract ?: return
    val side = if (contract.declarer.team == teamA) contractsA else contractsB
    side[0]++
    if (round.isContractMade) side[1]++
}

private const val DEFAULT_DEALS = 400
private const val NANOS_PER_MILLI = 1_000_000.0
