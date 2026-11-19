package justcoinche.app

import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.Rank
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
    fun `a hand is shown by suit, highest card first`() {
        val hand = CardSet.of(
            listOf(
                Card.of(Rank.SEVEN, Suit.HEARTS),
                Card.of(Rank.TEN, Suit.CLUBS),
                Card.of(Rank.ACE, Suit.HEARTS),
                Card.of(Rank.KING, Suit.CLUBS),
            ),
        )
        assertEquals(listOf("10♣", "R♣", "A♥", "7♥"), sortedForDisplay(hand).map { it.label })
    }
}
