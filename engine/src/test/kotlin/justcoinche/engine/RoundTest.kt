package justcoinche.engine

import justcoinche.engine.Bid.Coinche
import justcoinche.engine.Bid.Pass
import justcoinche.engine.Round.Phase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WEST deals in these tests, so SOUTH speaks first and leads the first trick. */
class RoundTest {

    /** South holds every club, East every diamond, North every heart, West every spade. */
    private val bySuit = Deal(Suit.entries.map { CardSet.FULL.ofSuit(it) })
    private val clubs = ContractType.SuitTrump(Suit.CLUBS)

    private fun start(rules: Rules = Rules()) = Round.start(Seat.WEST, bySuit, rules)

    /** Plays [bids] in turn from the first speaker. */
    private fun Round.bidding(vararg bids: Bid): Round =
        bids.fold(this) { round, bid -> round.call(round.auction.toSpeak, bid) }

    /** Plays out the cards, each player choosing a random legal card. */
    private fun Round.playedOut(random: Random): Round {
        var round = this
        while (round.phase == Phase.PLAYING) {
            val play = checkNotNull(round.cardPlay)
            round = round.play(play.playableCards().toList().random(random))
        }
        return round
    }

    @Test
    fun `a round starts with the auction, the dealer's right speaking first`() {
        val round = start()
        assertEquals(Phase.BIDDING, round.phase)
        assertEquals(Seat.SOUTH, round.auction.toSpeak)
        assertNull(round.contract)
        assertNull(round.cardPlay)
    }

    @Test
    fun `four passes end the round with a redeal, and nothing can be played or scored`() {
        val round = start().bidding(Pass, Pass, Pass, Pass)
        assertEquals(Phase.REDEAL, round.phase)
        assertFailsWith<IllegalStateException> { round.play(Card.of(Rank.SEVEN, Suit.CLUBS)) }
        assertFailsWith<IllegalStateException> { round.score() }
    }

    @Test
    fun `a settled contract starts the card play, led by the dealer's right`() {
        val round = start().bidding(Bid.Announce(80, clubs), Pass, Pass, Pass)
        assertEquals(Phase.PLAYING, round.phase)
        val play = checkNotNull(round.cardPlay)
        assertEquals(clubs, play.contract)
        assertEquals(Seat.SOUTH, play.toPlay)
        Seat.entries.forEach { assertEquals(bySuit.hand(it), play.hand(it)) }
    }

    @Test
    fun `cards cannot be played during the auction, nor bids made during the play`() {
        assertFailsWith<IllegalStateException> { start().play(Card.of(Rank.SEVEN, Suit.CLUBS)) }
        val playing = start().bidding(Bid.Announce(80, clubs), Pass, Pass, Pass)
        assertFailsWith<IllegalStateException> { playing.call(Seat.EAST, Pass) }
    }

    @Test
    fun `a whole round is scored under the table's scoring mode`() {
        // South bids 80 clubs and, holding every trump, wins all eight tricks with the belote.
        val contractPoints = start(Rules(scoringMode = ScoringMode.CONTRACT_POINTS))
            .bidding(Bid.Announce(80, clubs), Pass, Pass, Pass).playedOut(Random(1))
        assertEquals(Phase.OVER, contractPoints.phase)
        assertEquals(TeamPoints(80, 0), contractPoints.score())

        // The same round counting points made: 80 + 250 for all the tricks + 20 for the belote.
        val pointsMade = start(Rules(scoringMode = ScoringMode.CONTRACT_AND_POINTS_MADE))
            .bidding(Bid.Announce(80, clubs), Pass, Pass, Pass).playedOut(Random(1))
        assertEquals(TeamPoints(350, 0), pointsMade.score())
    }

    @Test
    fun `a coinched contract is carried from the auction into the score`() {
        // South bids 80 clubs, West coinches; South then North decline to surcoinche.
        val round = start().bidding(Bid.Announce(80, clubs), Pass, Pass, Coinche, Pass, Pass)
        assertEquals(CoincheLevel.COINCHED, round.contract?.coinche)
        assertEquals(TeamPoints(160, 0), round.playedOut(Random(2)).score())
    }

    @Test
    fun `random rounds always end, either redealt or fully played and scored`() {
        val random = Random(23)
        repeat(1_000) {
            var round = Round.start(Seat.entries.random(random), Deal.random(random), Rules())
            while (round.phase == Phase.BIDDING) {
                val seat = Seat.entries.filter { round.auction.legalBids(it).isNotEmpty() }.random(random)
                val legal = round.auction.legalBids(seat)
                round = round.call(seat, if (Pass in legal && random.nextInt(3) > 0) Pass else legal.random(random))
            }
            round = round.playedOut(random)
            assertTrue(round.phase == Phase.OVER || round.phase == Phase.REDEAL)
            if (round.phase == Phase.OVER) {
                assertEquals(Deal.HAND_SIZE, checkNotNull(round.cardPlay).tricks.size)
                val score = round.score()
                assertTrue(score.northSouth >= 0 && score.eastWest >= 0)
            }
        }
    }
}
