package justcoinche.arena

import justcoinche.ai.Objective
import justcoinche.ai.Solver
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Team
import justcoinche.engine.Trick
import java.util.Locale
import kotlin.random.Random

/**
 * Times the open-card solver on whole deals, from the first lead:
 *
 *     ./gradlew :engine:solverBench --args="1000 5"
 *
 * Arguments: the number of deals, the seed. For each deal, a random trump
 * suit (sometimes no trump or all trump), a random leader and attack; prints
 * the mean, median, 99th percentile and worst time of a full solve, then of
 * the values of all the leader's cards, which is what an expert player needs,
 * then of whether each of them still makes 82 points, the cheaper question
 * that decides the score when only the contract is written down.
 */
fun main(args: Array<String>) {
    val deals = args.getOrNull(0)?.toInt() ?: DEFAULT_DEALS
    val random = Random(args.getOrNull(1)?.toLong() ?: 1L)
    val rules = Rules()
    val solveTimes = mutableListOf<Long>()
    val allCardsTimes = mutableListOf<Long>()
    val allMadeTimes = mutableListOf<Long>()
    var nodes = 0L
    repeat(WARM_UP + deals) { i ->
        val deal = Deal.random(random)
        // Two times in three a trump suit, else no trump or all trump.
        val type = when (random.nextInt(SIX)) {
            NO_TRUMP_DRAW -> ContractType.NoTrump
            ALL_TRUMP_DRAW -> ContractType.AllTrump
            else -> ContractType.SuitTrump(Suit.entries[random.nextInt(Suit.entries.size)])
        }
        val solver = Solver(type, rules, Team.entries.random(random), Objective.POINTS)
        val load = { solver.load(Seat.entries.map { deal.hand(it) }, Trick(Seat.entries.random(random)), TRICKS) }
        load()
        val solve = timed { solver.value() }
        val solveNodes = solver.nodes
        load()
        val allCards = timed { solver.moveValues() }
        load()
        val allMade = timed { solver.moveReaches(TARGET) }
        if (i >= WARM_UP) {
            solveTimes += solve
            allCardsTimes += allCards
            allMadeTimes += allMade
            nodes += solveNodes
        }
    }
    println("$deals deals, mean ${nodes / deals} nodes per full solve")
    println("Full solve: ${describe(solveTimes)}")
    println("Every lead valued: ${describe(allCardsTimes)}")
    println("Every lead judged against $TARGET points: ${describe(allMadeTimes)}")
}

private fun timed(block: () -> Unit): Long {
    val start = System.nanoTime()
    block()
    return System.nanoTime() - start
}

private fun describe(nanos: List<Long>): String {
    val sorted = nanos.sorted()
    fun ms(n: Long) = "%.2f ms".format(Locale.ROOT, n / NANOS_PER_MILLI)
    return "mean ${ms(sorted.sum() / sorted.size)}, median ${ms(sorted[sorted.size / 2])}, " +
        "99% ${ms(sorted[sorted.size * P99 / PERCENT])}, worst ${ms(sorted.last())}"
}

private const val DEFAULT_DEALS = 1000
private const val WARM_UP = 50
private const val TRICKS = 8
private const val TARGET = 82
private const val SIX = 6
private const val NO_TRUMP_DRAW = 4
private const val ALL_TRUMP_DRAW = 5
private const val P99 = 99
private const val PERCENT = 100
private const val NANOS_PER_MILLI = 1_000_000.0
