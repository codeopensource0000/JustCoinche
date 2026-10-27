package justcoinche.engine

import justcoinche.engine.Bid.Coinche
import justcoinche.engine.Bid.Pass
import justcoinche.engine.Bid.Surcoinche
import justcoinche.engine.Seat.EAST
import justcoinche.engine.Seat.NORTH
import justcoinche.engine.Seat.SOUTH
import justcoinche.engine.Seat.WEST
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unless stated otherwise SOUTH deals, so EAST speaks first, then NORTH, WEST and SOUTH. */
class AuctionTest {

    private val hearts = ContractType.SuitTrump(Suit.HEARTS)
    private val spades = ContractType.SuitTrump(Suit.SPADES)

    private fun announce(value: Int, type: ContractType = hearts) = Bid.Announce(value, type)

    /** Starts an auction dealt by SOUTH and plays [calls] in turn, from EAST. */
    private fun auction(vararg calls: Bid, rules: Rules = Rules()): Auction =
        calls.fold(Auction.start(SOUTH, rules)) { auction, bid -> auction.call(auction.toSpeak, bid) }

    private fun contractOf(auction: Auction): Contract =
        (auction.result as AuctionResult.Contracted).contract

    @Test
    fun `the player on the dealer's right speaks first`() {
        assertEquals(EAST, Auction.start(SOUTH, Rules()).toSpeak)
        assertEquals(SOUTH, Auction.start(WEST, Rules()).toSpeak)
    }

    @Test
    fun `four passes without a bid mean a redeal`() {
        val auction = auction(Pass, Pass, Pass, Pass)
        assertEquals(AuctionResult.Redeal, auction.result)
    }

    @Test
    fun `three passes after a bid end the auction on that contract`() {
        val auction = auction(Pass, announce(80), Pass, Pass, Pass) // NORTH bids 80 hearts
        assertEquals(
            Contract(declarer = NORTH, type = hearts, value = 80, isCapot = false, coinche = CoincheLevel.NONE),
            contractOf(auction),
        )
    }

    @Test
    fun `the auction goes on while fewer than three players have passed since the last bid`() {
        val auction = auction(announce(80), Pass, Pass)
        assertFalse(auction.isOver)
        assertNull(auction.result)
    }

    @Test
    fun `a player who passed may bid again later`() {
        // EAST passes, NORTH bids, WEST and SOUTH pass: EAST may now overbid.
        val auction = auction(Pass, announce(80), Pass, Pass)
        assertEquals(EAST, auction.toSpeak)
        assertTrue(announce(90, spades) in auction.legalBids(EAST))
    }

    @Test
    fun `an announce must be strictly higher than the previous one, whatever the type`() {
        val legal = auction(announce(90)).legalBids(NORTH)
        assertFalse(announce(90, spades) in legal)
        assertFalse(announce(80) in legal)
        assertTrue(announce(100, spades) in legal)
        assertTrue(announce(100, ContractType.NoTrump) in legal)
    }

    @Test
    fun `announces go from 80 to 180 by tens`() {
        val opening = auction().legalBids(EAST).filterIsInstance<Bid.Announce>().map { it.value }.toSet()
        assertEquals((80..180 step 10).toSet(), opening)
        assertFailsWith<IllegalArgumentException> { announce(85) }
        assertFailsWith<IllegalArgumentException> { announce(190) }
    }

    @Test
    fun `a capot outbids any announce and nothing outbids a capot`() {
        val afterAnnounce = auction(announce(180))
        assertTrue(Bid.Capot(spades) in afterAnnounce.legalBids(NORTH))

        val afterCapot = auction(Bid.Capot(hearts))
        assertEquals(listOf(Pass, Coinche), afterCapot.legalBids(NORTH))
    }

    @Test
    fun `a capot is a contract worth 250`() {
        val auction = auction(Bid.Capot(hearts), Pass, Pass, Pass)
        assertEquals(
            Contract(EAST, hearts, value = 250, isCapot = true, coinche = CoincheLevel.NONE),
            contractOf(auction),
        )
    }

    @Test
    fun `no-trump and all-trump are offered only when the rules allow them`() {
        val types = auction(rules = Rules(allowNoTrump = false, allowAllTrump = false))
            .legalBids(EAST).filterIsInstance<Bid.Announce>().map { it.type }.toSet()
        assertEquals(Suit.entries.map { ContractType.SuitTrump(it) }.toSet(), types)
    }

    @Test
    fun `nobody may coinche before a bid, nor coinche their own team`() {
        assertFalse(Coinche in auction().legalBids(EAST))
        // EAST bids; NORTH (opponent) may coinche, then WEST (EAST's partner) may not.
        val afterBid = auction(announce(80))
        assertTrue(Coinche in afterBid.legalBids(NORTH))
        val afterPass = auction(announce(80), Pass)
        assertFalse(Coinche in afterPass.legalBids(WEST))
    }

    @Test
    fun `a coinche ends the bidding, then the coinched team may surcoinche in turn`() {
        // EAST bids 80, NORTH coinches: WEST (after NORTH) answers first, then EAST.
        val coinched = auction(announce(80), Coinche)
        assertEquals(WEST, coinched.toSpeak)
        assertEquals(listOf(Pass, Surcoinche), coinched.legalBids(WEST))
        assertTrue(coinched.legalBids(SOUTH).isEmpty())

        val afterWestPasses = coinched.call(WEST, Pass)
        assertEquals(EAST, afterWestPasses.toSpeak)
        assertEquals(CoincheLevel.COINCHED, contractOf(afterWestPasses.call(EAST, Pass)).coinche)
        assertEquals(CoincheLevel.SURCOINCHED, contractOf(afterWestPasses.call(EAST, Surcoinche)).coinche)
    }

    @Test
    fun `a surcoinche ends the auction at once`() {
        val auction = auction(announce(80), Coinche, Surcoinche)
        assertEquals(CoincheLevel.SURCOINCHED, contractOf(auction).coinche)
        assertEquals(EAST, contractOf(auction).declarer)
    }

    @Test
    fun `a capot can be coinched`() {
        val auction = auction(Bid.Capot(hearts), Coinche, Pass, Pass)
        assertEquals(CoincheLevel.COINCHED, contractOf(auction).coinche)
        assertTrue(contractOf(auction).isCapot)
    }

    @Test
    fun `out of turn, an opponent may coinche only when coinche is played on the fly`() {
        // EAST bids 80; it is NORTH's turn. SOUTH, also an opponent, is not in turn.
        val inTurn = auction(announce(80))
        assertTrue(inTurn.legalBids(SOUTH).isEmpty())

        val onTheFly = auction(announce(80), rules = Rules(coincheTiming = CoincheTiming.ANY_TIME))
        assertEquals(listOf(Coinche), onTheFly.legalBids(SOUTH))
        assertTrue(onTheFly.legalBids(WEST).isEmpty()) // EAST's partner
        val coinched = onTheFly.call(SOUTH, Coinche)
        assertEquals(EAST, coinched.toSpeak) // the coinched team answers, starting with the seat after SOUTH
    }

    @Test
    fun `an illegal call is rejected, and nothing can be said once the auction is over`() {
        assertFailsWith<IllegalArgumentException> { auction().call(NORTH, Pass) } // not NORTH's turn
        val over = auction(Pass, Pass, Pass, Pass)
        assertTrue(Seat.entries.all { over.legalBids(it).isEmpty() })
    }

    @Test
    fun `random auctions always end, and the contract is the last bid`() {
        val random = Random(17)
        repeat(5_000) {
            val rules = Rules(coincheTiming = CoincheTiming.entries.random(random))
            var auction = Auction.start(Seat.entries.random(random), rules)
            while (!auction.isOver) {
                // Any seat with something to say, chosen at random; passes favoured so that auctions end.
                val seat = Seat.entries.filter { auction.legalBids(it).isNotEmpty() }.random(random)
                val legal = auction.legalBids(seat)
                val bid = if (Pass in legal && random.nextInt(3) > 0) Pass else legal.random(random)
                auction = auction.call(seat, bid)
                assertTrue(auction.calls.size < 100, "the auction should have ended")
            }
            val lastBid = auction.calls.lastOrNull { it.bid is Bid.Announce || it.bid is Bid.Capot }
            when (val result = auction.result) {
                AuctionResult.Redeal -> assertNull(lastBid)
                is AuctionResult.Contracted -> assertEquals(checkNotNull(lastBid).seat, result.contract.declarer)
                null -> error("unreachable")
            }
        }
    }
}
