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

/** A label without the text-glyph selectors, to compare it with plain text. */
internal fun String.plain(): String = replace(TEXT_GLYPH, "")

class CardLabelsTest {

    @Test
    fun `every suit asks for its text glyph, never a coloured emoji`() {
        SUIT_SYMBOLS.values.forEach { assertTrue(it.endsWith(TEXT_GLYPH)) }
    }


    @Test
    fun `cards show French ranks and suit symbols`() {
        assertEquals("10♥", Card.of(Rank.TEN, Suit.HEARTS).label.plain())
        assertEquals("V♣", Card.of(Rank.JACK, Suit.CLUBS).label.plain())
        assertEquals(32, Card.deck.map { it.label.plain() }.toSet().size)
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
        assertEquals("Passe", Bid.Pass.label.plain())
        assertEquals("100 ♥", Bid.Announce(100, ContractType.SuitTrump(Suit.HEARTS)).label.plain())
        assertEquals("Capot SA", Bid.Capot(ContractType.NoTrump).label.plain())
        assertEquals("Capot belote TA", Bid.CapotBelote(ContractType.AllTrump).label.plain())
        val spades = ContractType.SuitTrump(Suit.SPADES)
        val coinched = Contract(Seat.NORTH, spades, 90, isCapot = false, CoincheLevel.COINCHED)
        assertEquals("90 ♠ coinché", coinched.label.plain())
        assertEquals("Ouest", Seat.WEST.label.plain())
    }
}
