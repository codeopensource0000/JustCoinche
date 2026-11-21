package justcoinche.ai

import justcoinche.engine.CardPlay
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Team
import justcoinche.engine.Trick
import justcoinche.engine.parseCard
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolverTest {

    /** What the attack has made so far, as the engine counts it. */
    private fun measure(play: CardPlay, attack: Team, objective: Objective): Int = when (objective) {
        Objective.POINTS -> play.points(attack)
        Objective.TRICKS -> play.tricks.count { checkNotNull(it.winner(play.contract)).team == attack }
    }

    /** Exhaustive minimax through the engine itself: the reference the solver must match. */
    private fun bruteForce(play: CardPlay, attack: Team, objective: Objective): Int {
        if (play.isOver) return measure(play, attack, objective)
        val values = play.playableCards().toList().map { bruteForce(play.play(it), attack, objective) }
        return if (play.toPlay.team == attack) values.max() else values.min()
    }

    private fun randomContract(random: Random): ContractType = when (random.nextInt(6)) {
        in 0..3 -> ContractType.SuitTrump(Suit.entries[random.nextInt(4)])
        4 -> ContractType.NoTrump
        else -> ContractType.AllTrump
    }

    /** A random position with [tricksLeft] tricks to play, possibly in the middle of a trick. */
    private fun randomPosition(random: Random, tricksLeft: Int, rules: Rules, contract: ContractType): CardPlay {
        var play = CardPlay.start(Deal.random(random), contract, rules, Seat.entries.random(random))
        val cardsToPlay = (8 - tricksLeft) * 4 + random.nextInt(4)
        repeat(cardsToPlay) { play = play.play(play.playableCards().toList().random(random)) }
        return play
    }

    private fun solverFor(play: CardPlay, rules: Rules, attack: Team, objective: Objective): Solver =
        Solver(play.contract, rules, attack, objective).apply {
            load(Seat.entries.map { play.hand(it) }, play.currentTrick, 8 - play.tricks.size)
        }

    @Test
    fun `on thousands of random endings the solver matches an exhaustive search through the engine`() {
        val random = Random(2026)
        repeat(3000) { i ->
            val rules = Rules(mustUnderTrump = random.nextBoolean())
            val contract = randomContract(random)
            val tricksLeft = 1 + i % 3
            val play = randomPosition(random, tricksLeft, rules, contract)
            val attack = Team.entries.random(random)
            val objective = if (i % 5 == 0) Objective.TRICKS else Objective.POINTS
            val solver = solverFor(play, rules, attack, objective)
            val start = measure(play, attack, objective)
            val expected = bruteForce(play, attack, objective) - start
            assertEquals(expected, solver.value(), "position $i, $contract, $tricksLeft tricks left")

            // The value of each legal card, and the fast "reaches" test, agree too.
            solver.moveValues().forEach { (card, value) ->
                assertEquals(bruteForce(play.play(card), attack, objective) - start, value, "card $card, position $i")
            }
            assertTrue(solver.reaches(expected))
            assertTrue(!solver.reaches(expected + 1))
        }
    }

    @Test
    fun `the transposition table changes no result`() {
        val random = Random(99)
        repeat(150) { i ->
            val rules = Rules(mustUnderTrump = random.nextBoolean())
            val play = randomPosition(random, 4 + i % 2, rules, randomContract(random))
            val attack = Team.entries.random(random)
            val withTable = solverFor(play, rules, attack, Objective.POINTS)
            val without = solverFor(play, rules, attack, Objective.POINTS).apply { useTable = false }
            assertEquals(without.value(), withTable.value(), "position $i")
            assertEquals(without.moveValues(), withTable.moveValues(), "position $i")
        }
    }

    @Test
    fun `a two-trick ending worked out by hand`() {
        // Hearts are trumps, North-South attack, South to lead.
        //   South: Jack of hearts, 7 of clubs     East: 10 of clubs, 7 of diamonds
        //   North: 9 of hearts, 8 of clubs        West: Ace of clubs, 8 of diamonds
        // Leading the Jack: North's 9 falls under it (34), East and West throw their small
        // diamonds and West's Ace takes the last trick: 34. Leading the club first: West takes
        // it (21), then leads a diamond that South trumps, North's 9 on it: 20 + 14 + 10 = 44.
        val hands = listOf(hand("VC 7T"), hand("10T 7K"), hand("9C 8T"), hand("AT 8K"))
        val type = ContractType.SuitTrump(Suit.HEARTS)
        val solver = Solver(type, Rules(), Team.NORTH_SOUTH).apply { load(hands, Trick(Seat.SOUTH), 2) }
        assertEquals(44, solver.value())
        assertEquals(mapOf(parseCard("VC") to 34, parseCard("7T") to 44), solver.moveValues())
    }

    @Test
    fun `a team holding every trump makes every point`() {
        // South holds every club, East every diamond, North every heart, West every spade.
        val bySuit = Deal(Suit.entries.map { CardSet.FULL.ofSuit(it) })
        val rules = Rules()
        val play = CardPlay.start(bySuit, ContractType.SuitTrump(Suit.CLUBS), rules, Seat.SOUTH)
        assertEquals(162, solverFor(play, rules, Team.NORTH_SOUTH, Objective.POINTS).value())
        assertEquals(8, solverFor(play, rules, Team.NORTH_SOUTH, Objective.TRICKS).value())
        assertEquals(0, solverFor(play, rules, Team.EAST_WEST, Objective.TRICKS).value())
    }
}
