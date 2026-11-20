package justcoinche.app

import justcoinche.arena.RulePlayer
import justcoinche.arena.playGame
import justcoinche.arena.playTurn
import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Game
import justcoinche.engine.History
import justcoinche.engine.Rank
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Team
import justcoinche.engine.viewFor
import justcoinche.record.GameStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class TableFlowTest {

    private val bots = Seat.entries.filter { it != HUMAN }.associateWith { RulePlayer() }
    private val hearts = ContractType.SuitTrump(Suit.HEARTS)

    @get:Rule
    val folder = TemporaryFolder()

    /** The computer player whose turn it is bids or plays once. */
    private fun Round.botStep(): Round {
        val seat = checkNotNull(toAct)
        val view = viewFor(seat)
        val bot = bots.getValue(seat)
        return if (phase == Round.Phase.BIDDING) call(seat, bot.bid(view)) else play(bot.play(view))
    }

    /** The bots act until the human has to, or the deal is over. */
    private fun Round.untilHumanOrEnd(): Round {
        var round = this
        while (round.toAct != null && round.toAct != HUMAN) round = round.botStep()
        return round
    }

    /** The human always passes and plays their first playable card. */
    private fun Round.humanActs(): Round =
        if (phase == Round.Phase.BIDDING) call(HUMAN, Bid.Pass) else play(viewFor(HUMAN).playableCards.toList().first())

    /** The same for a whole game: the human's moves, then the bots' through the arena. */
    private fun Game.nextMove(random: Random): Game {
        val round = currentRound
        return when {
            round == null -> deal(Deal.random(random))
            round.toAct != HUMAN -> playTurn(bots)
            round.phase == Round.Phase.BIDDING -> call(HUMAN, Bid.Pass)
            else -> play(round.viewFor(HUMAN).playableCards.toList().first())
        }
    }

    @Test
    fun `a hand alternates black and red suits, highest first, trumps in trump order`() {
        val hand = CardSet.of(
            listOf(
                Card.of(Rank.SEVEN, Suit.HEARTS), Card.of(Rank.TEN, Suit.CLUBS), Card.of(Rank.ACE, Suit.HEARTS),
                Card.of(Rank.KING, Suit.DIAMONDS), Card.of(Rank.NINE, Suit.SPADES), Card.of(Rank.JACK, Suit.HEARTS),
            ),
        )
        val plainOrder = sortedForDisplay(hand).map { it.label.plain() }
        assertEquals(listOf("9♠", "A♥", "V♥", "7♥", "10♣", "R♦"), plainOrder)
        val trumpOrder = sortedForDisplay(hand, hearts).map { it.label.plain() }
        assertEquals(listOf("9♠", "V♥", "A♥", "7♥", "10♣", "R♦"), trumpOrder)
    }

    @Test
    fun `the bidding panel offers the values still legal, then the capots`() {
        val opening = Round.start(Seat.WEST, Deal.random(Random(1)), Rules())
        val levels = bidLevels(opening.viewFor(HUMAN).legalBids)
        assertEquals(BidLevel.Points(80), levels.first())
        assertEquals(listOf(BidLevel.Points(180), BidLevel.Capot, BidLevel.CapotBelote), levels.takeLast(3))
        assertEquals(Bid.Announce(80, hearts), BidLevel.Points(80).bid(hearts))
        assertEquals(Bid.CapotBelote(hearts), BidLevel.CapotBelote.bid(hearts))

        // After 170, only 180 and the capots remain; after a coinche, no value at all.
        val after170 = Round.start(Seat.EAST, Deal.random(Random(1)), Rules())
            .call(Seat.NORTH, Bid.Announce(170, hearts)).call(Seat.WEST, Bid.Pass)
        val top = listOf(BidLevel.Points(180), BidLevel.Capot, BidLevel.CapotBelote)
        assertEquals(top, bidLevels(after170.viewFor(HUMAN).legalBids))
        assertEquals(emptyList<BidLevel>(), bidLevels(listOf(Bid.Pass, Bid.Surcoinche)))
    }

    @Test
    fun `the bots act in turn until the human has to, and a whole deal can be played`() {
        repeat(50) { seed ->
            var round = Round.start(Seat.entries[seed % 4], Deal.random(Random(seed)), Rules()).untilHumanOrEnd()
            while (round.toAct == HUMAN) round = round.humanActs().untilHumanOrEnd()
            assertNull(round.toAct)
            assertTrue(round.phase == Round.Phase.OVER || round.phase == Round.Phase.REDEAL)
        }
    }

    @Test
    fun `a whole game is played and saved move by move, and it resumes where it stopped`() {
        val random = Random(9)
        val store = GameStore(folder.root)
        var game = store.openGame()
        while (!game.isOver) {
            game = game.nextMove(random)
            store.save(game)
            if (game.currentRound == null) assertTrue(dealSummary(game).isNotEmpty())
            if (!game.isOver && random.nextInt(40) == 0) {
                val resumed = GameStore(folder.root).openGame()
                assertEquals(game.scores, resumed.scores)
                assertEquals(game.results.map { it.historyLine }, resumed.results.map { it.historyLine })
                assertEquals(game.shownRound?.let { History.write(it) }, resumed.shownRound?.let { History.write(it) })
            }
        }
        val end = dealSummary(game).last()
        assertTrue(end, end.startsWith("Partie gagnée") || end.startsWith("Partie perdue"))
        // The game is over: the next launch starts a new one.
        assertTrue(GameStore(folder.root).openGame().results.isEmpty())
    }

    @Test
    fun `the end of a game names its result and the final score`() {
        val everyone = Seat.entries.associateWith { RulePlayer() }
        val results = List(8) { seed ->
            val game = playGame(Game.start(Rules(), Seat.WEST), everyone, Random(seed))
            val (us, them) = game.scores.northSouth to game.scores.eastWest
            val expected =
                if (game.winner == Team.NORTH_SOUTH) "Partie gagnée, $us à $them !" else "Partie perdue, $us à $them."
            assertEquals(expected, dealSummary(game).last())
            game.winner
        }
        // Both endings were checked.
        assertEquals(Team.entries.toSet(), results.toSet())
    }

    @Test
    fun `an unreadable record does not stop the app, a new game starts`() {
        val store = GameStore(folder.root)
        store.start(Rules(), Seat.WEST)
        store.records().single().writeText("partie v1 | regles ???\n")
        val game = GameStore(folder.root).openGame()
        assertTrue(game.results.isEmpty())
        assertEquals(2, store.records().size)
    }

    @Test
    fun `a completed trick stays on show until the next card is played`() {
        // South opens 80 hearts, the bots answer, and the first trick is played out.
        var round = Round.start(Seat.WEST, Deal.random(Random(5)), Rules()).call(HUMAN, Bid.Announce(80, hearts))
        round = round.untilHumanOrEnd()
        while (round.phase == Round.Phase.BIDDING) round = round.humanActs().untilHumanOrEnd()
        while (checkNotNull(round.cardPlay).tricks.isEmpty()) {
            round = if (round.toAct == HUMAN) round.humanActs() else round.botStep()
        }
        val play = checkNotNull(round.cardPlay)
        assertTrue(play.currentTrick.cards.isEmpty())
        assertSame(play.tricks.single(), round.shownTrick)
        assertTrue(round.showsCompletedTrick)

        val next = if (round.toAct == HUMAN) round.humanActs() else round.botStep()
        assertEquals(1, checkNotNull(next.shownTrick).cards.size)
    }

    @Test
    fun `each seat shows the last thing it said`() {
        val round = Round.start(Seat.WEST, Deal.random(Random(2)), Rules())
            .call(Seat.SOUTH, Bid.Announce(80, hearts)).call(Seat.EAST, Bid.Pass)
            .call(Seat.NORTH, Bid.Announce(90, hearts)).call(Seat.WEST, Bid.Pass)
            .call(Seat.SOUTH, Bid.Pass)
        val expected = mapOf(
            Seat.SOUTH to Bid.Pass,
            Seat.EAST to Bid.Pass,
            Seat.NORTH to Bid.Announce(90, hearts),
            Seat.WEST to Bid.Pass,
        )
        assertEquals(expected, round.lastCalls)
    }
}
