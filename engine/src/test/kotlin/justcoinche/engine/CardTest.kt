package justcoinche.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardTest {

    @Test
    fun `the deck has 32 distinct cards, 8 per suit`() {
        assertEquals(32, Card.deck.toSet().size)
        Suit.entries.forEach { suit ->
            assertEquals(8, Card.deck.count { it.suit == suit })
        }
    }

    @Test
    fun `a card built from rank and suit gives them back`() {
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                val card = Card.of(rank, suit)
                assertEquals(rank, card.rank)
                assertEquals(suit, card.suit)
            }
        }
    }

    @Test
    fun `an index outside the deck is rejected`() {
        assertFailsWith<IllegalArgumentException> { Card(32) }
        assertFailsWith<IllegalArgumentException> { Card(-1) }
    }
}
