package justcoinche.app

import justcoinche.arena.RulePlayer
import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Rank
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.viewFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TableFlowTest {

    private val bots = Seat.entries.filter { it != HUMAN }.associateWith { RulePlayer() }
    private val hearts = ContractType.SuitTrump(Suit.HEARTS)

    /** The human always passes and plays their first playable card; the bots do the rest. */
    private fun Round.untilHumanOrEnd(): Round {
        var round = this
        while (round.seatToAct != null && round.seatToAct != HUMAN) round = round.botStep(bots)
        return round
    }

    private fun Round.humanActs(): Round =
        if (phase == Round.Phase.BIDDING) call(HUMAN, Bid.Pass) else play(viewFor(HUMAN).playableCards.toList().first())

    @Test
    fun `a hand alternates black and red suits, highest first, trumps in trump order`() {
        val hand = CardSet.of(
            listOf(
                Card.of(Rank.SEVEN, Suit.HEARTS), Card.of(Rank.TEN, Suit.CLUBS), Card.of(Rank.ACE, Suit.HEARTS),
                Card.of(Rank.KING, Suit.DIAMONDS), Card.of(Rank.NINE, Suit.SPADES), Card.of(Rank.JACK, Suit.HEARTS),
            ),
        )
        assertEquals(listOf("9♠", "A♥", "V♥", "7♥", "10♣", "R♦"), sortedForDisplay(hand).map { it.label })
        assertEquals(listOf("9♠", "V♥", "A♥", "7♥", "10♣", "R♦"), sortedForDisplay(hand, hearts).map { it.label })
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
            while (round.seatToAct == HUMAN) round = round.humanActs().untilHumanOrEnd()
            assertNull(round.seatToAct)
            assertTrue(round.phase == Round.Phase.OVER || round.phase == Round.Phase.REDEAL)
        }
    }

    @Test
    fun `a completed trick stays on show until the next card is played`() {
        // South opens 80 hearts, the bots answer, and the first trick is played out.
        var round = Round.start(Seat.WEST, Deal.random(Random(5)), Rules()).call(HUMAN, Bid.Announce(80, hearts))
        round = round.untilHumanOrEnd()
        while (round.phase == Round.Phase.BIDDING) round = round.humanActs().untilHumanOrEnd()
        while (checkNotNull(round.cardPlay).tricks.isEmpty()) {
            round = if (round.seatToAct == HUMAN) round.humanActs() else round.botStep(bots)
        }
        val play = checkNotNull(round.cardPlay)
        assertTrue(play.currentTrick.cards.isEmpty())
        assertSame(play.tricks.single(), round.shownTrick)
        assertTrue(round.showsCompletedTrick)

        val next = if (round.seatToAct == HUMAN) round.humanActs() else round.botStep(bots)
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
