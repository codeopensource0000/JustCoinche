package justcoinche.engine

import justcoinche.engine.Rank.ACE
import justcoinche.engine.Rank.JACK
import justcoinche.engine.Rank.NINE
import justcoinche.engine.Rank.SEVEN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardSetTest {

    private val jackOfHearts = Card.of(JACK, Suit.HEARTS)
    private val nineOfHearts = Card.of(NINE, Suit.HEARTS)
    private val sevenOfClubs = Card.of(SEVEN, Suit.CLUBS)

    @Test
    fun `adding and removing cards updates membership and size`() {
        val set = CardSet.EMPTY + jackOfHearts + nineOfHearts
        assertEquals(2, set.size)
        assertTrue(jackOfHearts in set)
        assertFalse(sevenOfClubs in set)

        val smaller = set - jackOfHearts
        assertEquals(1, smaller.size)
        assertFalse(jackOfHearts in smaller)
    }

    @Test
    fun `adding a card twice keeps a single copy`() {
        assertEquals(1, (CardSet.EMPTY + sevenOfClubs + sevenOfClubs).size)
    }

    @Test
    fun `the full set holds the whole deck`() {
        assertEquals(32, CardSet.FULL.size)
        assertEquals(Card.deck, CardSet.FULL.toList())
    }

    @Test
    fun `the ace of spades lives on the sign bit and still behaves like any card`() {
        // Card index 31 is bit 31 of an Int, the sign bit: the set's value becomes negative.
        val aceOfSpades = Card.of(ACE, Suit.SPADES)
        val set = CardSet.of(listOf(aceOfSpades))
        assertTrue(set.bits < 0)
        assertTrue(aceOfSpades in set)
        assertEquals(listOf(aceOfSpades), set.toList())
        assertTrue((set - aceOfSpades).isEmpty())
    }

    @Test
    fun `filtering by suit keeps exactly that suit`() {
        Suit.entries.forEach { suit ->
            val cards = CardSet.FULL.ofSuit(suit).toList()
            assertEquals(8, cards.size)
            assertTrue(cards.all { it.suit == suit })
        }
    }

    @Test
    fun `union and intersection combine sets`() {
        val a = CardSet.of(listOf(jackOfHearts, nineOfHearts))
        val b = CardSet.of(listOf(nineOfHearts, sevenOfClubs))
        assertEquals(CardSet.of(listOf(jackOfHearts, nineOfHearts, sevenOfClubs)), a union b)
        assertEquals(CardSet.of(listOf(nineOfHearts)), a intersect b)
    }
}
