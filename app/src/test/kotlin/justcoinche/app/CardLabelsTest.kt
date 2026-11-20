package justcoinche.app

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CoincheLevel
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Rank
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLabelsTest {

    @Test
    fun `cards show French ranks and suit symbols`() {
        assertEquals("10♥", Card.of(Rank.TEN, Suit.HEARTS).label)
        assertEquals("V♣", Card.of(Rank.JACK, Suit.CLUBS).label)
        assertEquals(32, Card.deck.map { it.label }.toSet().size)
    }

    @Test
    fun `hearts and diamonds are red, spades and clubs are not`() {
        assertTrue(Card.of(Rank.ACE, Suit.HEARTS).isRed)
        assertTrue(Card.of(Rank.ACE, Suit.DIAMONDS).isRed)
        assertFalse(Card.of(Rank.ACE, Suit.SPADES).isRed)
        assertFalse(Card.of(Rank.ACE, Suit.CLUBS).isRed)
    }

    @Test
    fun `bids, contracts and seats read in French`() {
        assertEquals("Passe", Bid.Pass.label)
        assertEquals("100 ♥", Bid.Announce(100, ContractType.SuitTrump(Suit.HEARTS)).label)
        assertEquals("Capot SA", Bid.Capot(ContractType.NoTrump).label)
        assertEquals("Capot belote TA", Bid.CapotBelote(ContractType.AllTrump).label)
        val spades = ContractType.SuitTrump(Suit.SPADES)
        val coinched = Contract(Seat.NORTH, spades, 90, isCapot = false, CoincheLevel.COINCHED)
        assertEquals("90 ♠ coinché", coinched.label)
        assertEquals("Ouest", Seat.WEST.label)
    }
}
