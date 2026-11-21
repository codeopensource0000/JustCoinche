package justcoinche.ai

import justcoinche.engine.Auction
import justcoinche.engine.Bid
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.PlayerView
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulatedBiddingTest {

    /** The view of the player to speak after ia2 players opened a random auction, or null if it is over. */
    private fun randomAuction(random: Random): PlayerView? {
        val rules = Rules()
        val deal = Deal.random(random)
        val dealer = Seat.entries.random(random)
        var auction = Auction.start(dealer, rules)
        repeat(random.nextInt(5)) {
            if (auction.isOver) return null
            val seat = auction.toSpeak
            auction = auction.call(seat, PetitJeu.bid(view(seat, deal.hand(seat), dealer, auction)))
        }
        return if (auction.isOver) null else view(auction.toSpeak, deal.hand(auction.toSpeak), dealer, auction)
    }

    private fun view(seat: Seat, hand: CardSet, dealer: Seat, auction: Auction) = PlayerView(
        seat = seat, rules = auction.rules, dealer = dealer, hand = hand, calls = auction.calls, contract = null,
        tricks = emptyList(), currentTrick = null, legalBids = auction.legalBids(seat), playableCards = CardSet.EMPTY,
    )

    @Test
    fun `it always makes a legal call, the same one for the same position and seed`() {
        val random = Random(21)
        var checked = 0
        while (checked < 12) {
            val view = randomAuction(random) ?: continue
            val bid = SimulatedBidding(samples = 12, seed = 5).bid(view)
            assertTrue(bid in view.legalBids, "$bid")
            assertEquals(bid, SimulatedBidding(samples = 12, seed = 5).bid(view))
            checked++
        }
    }

    @Test
    fun `holding every trump it bids higher than the convention, since every heart contract is made`() {
        // South holds the eight hearts and speaks first: the convention opens 100, every heart
        // contract South declares wins all the tricks, so the higher the better.
        val allHearts = CardSet.FULL.ofSuit(Suit.HEARTS)
        val view = view(Seat.SOUTH, allHearts, Seat.WEST, Auction.start(Seat.WEST, Rules()))
        assertEquals(Bid.Announce(100, ContractType.SuitTrump(Suit.HEARTS)), PetitJeu.bid(view))
        val bid = SimulatedBidding(samples = 12, seed = 1).bid(view)
        assertTrue(bid is Bid.Announce && bid.type == ContractType.SuitTrump(Suit.HEARTS) && bid.value > 100, "$bid")
    }
}
