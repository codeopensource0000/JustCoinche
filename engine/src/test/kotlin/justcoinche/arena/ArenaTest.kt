package justcoinche.arena

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.Deal
import justcoinche.engine.History
import justcoinche.engine.Player
import justcoinche.engine.PlayerView
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Team
import justcoinche.engine.viewFor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaTest {

    /**
     * Deterministic: the first speaker bids the lowest announce offered, everyone
     * else passes; it always plays its first legal card.
     */
    private class FirstChoicePlayer : Player {
        override fun bid(view: PlayerView): Bid =
            if (view.calls.isEmpty()) view.legalBids.first { it is Bid.Announce } else Bid.Pass

        override fun play(view: PlayerView): Card = view.playableCards.toList().first()
    }

    private fun randomTeam(seed: Int): PlayerFactory = { seat -> RandomPlayer(Random(seed * 10 + seat.ordinal)) }

    @Test
    fun `a player sees its own hand and what is public, never the other hands`() {
        val deal = Deal.random(Random(4))
        var round = Round.start(Seat.WEST, deal, Rules())
        val atStart = round.viewFor(Seat.SOUTH)
        assertEquals(deal.hand(Seat.SOUTH), atStart.hand)
        assertTrue(atStart.legalBids.isNotEmpty()) // South speaks first
        assertTrue(round.viewFor(Seat.EAST).legalBids.isEmpty())

        val players = Seat.entries.associateWith { FirstChoicePlayer() }
        while (round.phase == Round.Phase.BIDDING) {
            val seat = round.auction.toSpeak
            round = round.call(seat, players.getValue(seat).bid(round.viewFor(seat)))
        }
        val toPlay = checkNotNull(round.cardPlay).toPlay
        val playing = round.viewFor(toPlay)
        assertFalse(playing.playableCards.isEmpty())
        assertTrue(playing.legalBids.isEmpty())
        assertEquals(CardSet.EMPTY, round.viewFor(toPlay.next).playableCards)
    }

    @Test
    fun `a deal played out by random players ends, and the same seeds replay it identically`() {
        fun play(): Round {
            val players = Seat.entries.associateWith { RandomPlayer(Random(it.ordinal + 1)) }
            return playOut(Round.start(Seat.NORTH, Deal.random(Random(8)), Rules()), players)
        }
        val round = play()
        assertTrue(round.phase == Round.Phase.OVER || round.phase == Round.Phase.REDEAL)
        assertEquals(History.write(round), History.write(play()))
    }

    @Test
    fun `in a duplicate match each team sits North-South at one table and East-West at the other`() {
        val seatsOfA = mutableListOf<Seat>()
        val spyA: PlayerFactory = { seat -> seatsOfA += seat; FirstChoicePlayer() }
        duplicateMatch(spyA, { FirstChoicePlayer() }, deals = 2, seed = 1, rules = Rules())
        assertEquals(setOf(Team.NORTH_SOUTH, Team.EAST_WEST), seatsOfA.map { it.team }.toSet())
    }

    @Test
    fun `two teams playing the same deterministic strategy tie exactly on every deal`() {
        // Both tables then play the very same game, so team A's lead at one table
        // is exactly cancelled by its deficit at the other.
        val same: PlayerFactory = { FirstChoicePlayer() }
        val result = duplicateMatch(same, same, deals = 50, seed = 3, rules = Rules())
        assertEquals(0.0, result.meanLead)
        assertEquals(0.0, result.margin95)
        assertFalse(result.isSignificant)
    }

    @Test
    fun `random players against random players show no significant lead`() {
        val result = duplicateMatch(randomTeam(1), randomTeam(2), deals = 400, seed = 5, rules = Rules())
        assertFalse(result.isSignificant, "unexpected lead: $result")
    }

    @Test
    fun `the margin is 1,96 standard errors of the per-deal leads`() {
        // Leads 10, -10, 30, -30: mean 0, sample standard deviation sqrt(2000 / 3).
        val result = summarize(listOf(10, -10, 30, -30))
        assertEquals(0.0, result.meanLead)
        assertEquals(1.96 * kotlin.math.sqrt(2000.0 / 3) / 2, result.margin95, absoluteTolerance = 1e-9)
    }
}
