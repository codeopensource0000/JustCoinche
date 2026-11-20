package justcoinche.engine

import justcoinche.engine.Bid.Pass
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {

    /** South holds every club, East every diamond, North every heart, West every spade. */
    private val bySuit = Deal(Suit.entries.map { CardSet.FULL.ofSuit(it) })

    /** West deals the by-suit deal: South bids 80 clubs, all pass, South wins every trick with the belote. */
    private fun Game.southTakesAll(): Game {
        var game = deal(bySuit)
        listOf(Bid.Announce(80, ContractType.SuitTrump(Suit.CLUBS)), Pass, Pass, Pass)
            .forEach { game = game.call(checkNotNull(game.currentRound).auction.toSpeak, it) }
        while (game.currentRound != null) {
            game = game.play(checkNotNull(game.currentRound?.cardPlay).playableCards().toList().first())
        }
        return game
    }

    @Test
    fun `the target score defaults to 1000 with contract points and 2000 with points made`() {
        assertEquals(1000, Rules(scoringMode = ScoringMode.CONTRACT_POINTS).targetScore)
        assertEquals(2000, Rules(scoringMode = ScoringMode.CONTRACT_AND_POINTS_MADE).targetScore)
    }

    @Test
    fun `a deal is played and scored, then the next player deals`() {
        val game = Game.start(Rules(), firstDealer = Seat.WEST).southTakesAll()
        assertEquals(TeamPoints(80, 0), game.scores)
        assertEquals(Seat.SOUTH, game.dealer)
        assertNull(game.currentRound)
        assertEquals(1, game.results.size)
        assertFalse(game.results.single().isFinal)
        assertFalse(game.isOver)
    }

    @Test
    fun `a redeal scores nothing and passes the deal on`() {
        var game = Game.start(Rules(), Seat.WEST).deal(bySuit)
        repeat(4) { game = game.call(checkNotNull(game.currentRound).auction.toSpeak, Pass) }
        assertEquals(TeamPoints(0, 0), game.scores)
        assertEquals(Seat.SOUTH, game.dealer)
        assertNull(game.results.single().score)
        assertNull(game.currentRound)
    }

    @Test
    fun `bids and cards need a deal in progress, and a new deal waits for the current one`() {
        val waiting = Game.start(Rules(), Seat.WEST)
        assertFailsWith<IllegalStateException> { waiting.call(Seat.SOUTH, Pass) }
        assertFailsWith<IllegalStateException> { waiting.deal(bySuit).deal(bySuit) }
    }

    @Test
    fun `the game ends when a team goes past the target, the team that went furthest winning`() {
        val game = Game.resume(Rules(), Seat.WEST, TeamPoints(950, 0)).southTakesAll()
        assertEquals(TeamPoints(1030, 0), game.scores)
        assertTrue(game.isOver)
        assertEquals(Team.NORTH_SOUTH, game.winner)
        assertTrue(game.results.single().isFinal)
        assertTrue(game.results.single().historyLine.endsWith("score NS 80 EO 0 brut"))
        assertFailsWith<IllegalStateException> { game.deal(bySuit) }

        assertEquals(Team.EAST_WEST, Game.resume(Rules(), Seat.WEST, TeamPoints(1010, 1030)).winner)
    }

    @Test
    fun `reaching the target exactly does not end the game`() {
        val game = Game.resume(Rules(), Seat.WEST, TeamPoints(920, 0)).southTakesAll()
        assertEquals(TeamPoints(1000, 0), game.scores)
        assertNull(game.winner)
        assertFalse(game.isOver)
        assertFalse(game.results.single().isFinal)

        // One more point than the target is enough.
        assertEquals(Team.NORTH_SOUTH, Game.resume(Rules(), Seat.WEST, TeamPoints(1001, 1000)).winner)
    }

    @Test
    fun `an exact tie above the target calls for one more deal, scored raw`() {
        val tied = Game.resume(Rules(), Seat.WEST, TeamPoints(1020, 1020))
        assertNull(tied.winner)
        assertFalse(tied.isOver)
        val after = tied.southTakesAll()
        assertTrue(after.results.single().isFinal)
        assertEquals(Team.NORTH_SOUTH, after.winner)
    }

    @Test
    fun `in whole random games only the final deal is scored raw, and every deal replays from its line`() {
        val random = Random(31)
        var finalsWhereRoundingMatters = 0
        repeat(200) {
            // Points made scoring with a low target: rounding matters and games stay short.
            val rules = Rules(scoringMode = ScoringMode.CONTRACT_AND_POINTS_MADE, targetScore = 300)
            val game = playRandomGame(rules, random)
            assertTrue(game.isOver)
            val winner = checkNotNull(game.winner)
            assertTrue(game.scores[winner] > 300 && game.scores[winner] > game.scores[winner.opponent])

            val scored = game.results.filter { it.score != null }
            val total = scored.fold(TeamPoints(0, 0)) { sum, result -> sum + checkNotNull(result.score) }
            assertEquals(game.scores, total)
            scored.forEach { result ->
                val expected = result.round.score(round = !result.isFinal)
                assertEquals(expected, result.score)
            }
            assertTrue(scored.last().isFinal)
            if (scored.last().round.score(round = false) != scored.last().round.score()) finalsWhereRoundingMatters++

            game.results.forEach { History.read(it.historyLine) }
        }
        // Guards against a test that could not tell rounded from raw.
        assertTrue(finalsWhereRoundingMatters > 0)
    }

    private fun playRandomGame(rules: Rules, random: Random): Game {
        var game = Game.start(rules, Seat.entries.random(random))
        while (!game.isOver) {
            game = game.deal(Deal.random(random))
            while (game.currentRound?.phase == Round.Phase.BIDDING) {
                val auction = checkNotNull(game.currentRound).auction
                val seat = Seat.entries.filter { auction.legalBids(it).isNotEmpty() }.random(random)
                val legal = auction.legalBids(seat)
                game = game.call(seat, if (Pass in legal && random.nextInt(3) > 0) Pass else legal.random(random))
            }
            while (game.currentRound?.phase == Round.Phase.PLAYING) {
                val play = checkNotNull(game.currentRound?.cardPlay)
                game = game.play(play.playableCards().toList().random(random))
            }
        }
        return game
    }
}
