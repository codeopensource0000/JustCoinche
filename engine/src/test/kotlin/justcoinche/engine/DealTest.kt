package justcoinche.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DealTest {

    @Test
    fun `a random deal gives 8 cards to each seat and every card to exactly one seat`() {
        val deal = Deal.random(Random(1))
        Seat.entries.forEach { assertEquals(8, deal.hand(it).size) }
        assertEquals(CardSet.FULL, Seat.entries.fold(CardSet.EMPTY) { all, seat -> all union deal.hand(seat) })
    }

    @Test
    fun `the same seed always produces the same deal`() {
        assertEquals(Deal.random(Random(42)), Deal.random(Random(42)))
    }

    @Test
    fun `different seeds produce different deals`() {
        assertNotEquals(Deal.random(Random(42)), Deal.random(Random(43)))
    }

    @Test
    fun `hands that share a card are rejected`() {
        val clubs = CardSet.FULL.ofSuit(Suit.CLUBS)
        assertFailsWith<IllegalArgumentException> {
            Deal(listOf(clubs, clubs, CardSet.FULL.ofSuit(Suit.HEARTS), CardSet.FULL.ofSuit(Suit.SPADES)))
        }
    }

    @Test
    fun `over many deals every card lands with each seat about a quarter of the time`() {
        // Guards against a biased shuffle, which would silently skew every
        // statistic the AI and the arena compute. 10,000 deals: each card is
        // expected 2,500 times per seat, with a standard deviation of about 43.
        val random = Random(7)
        val counts = Array(Seat.entries.size) { IntArray(Card.DECK_SIZE) }
        repeat(10_000) {
            val deal = Deal.random(random)
            Seat.entries.forEach { seat -> deal.hand(seat).forEach { card -> counts[seat.ordinal][card.index]++ } }
        }
        counts.forEach { perCard -> perCard.forEach { assertTrue(it in 2_300..2_700, "count $it out of range") } }
    }
}
