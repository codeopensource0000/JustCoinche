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
    fun `a deal survives encoding and decoding unchanged`() {
        val random = Random(3)
        repeat(10_000) {
            val deal = Deal.random(random)
            assertEquals(deal, Deal.decode(deal.encode()))
        }
    }

    @Test
    fun `the encoding gives two bits per card naming its holder`() {
        // South holds the clubs (seat 0 = 00), East the diamonds (01),
        // North the hearts (10), West the spades (11). Clubs are cards 0..7,
        // so they occupy the lowest 16 bits, and so on up to the spades.
        val bySuit = Deal(Suit.entries.map { CardSet.FULL.ofSuit(it) })
        assertEquals("ffffaaaa55550000", bySuit.encode().toULong().toString(16))
    }

    @Test
    fun `a code that does not give 8 cards to each seat is rejected`() {
        // All bits zero: every card would belong to South.
        assertFailsWith<IllegalArgumentException> { Deal.decode(0L) }
    }

    @Test
    fun `all 64 bits of the seed matter`() {
        // Two seeds that differ only in their upper 32 bits. If the generator
        // kept just the lower half, they would produce the same deal.
        val low = 12_345L
        val high = low + (1L shl 32)
        assertNotEquals(Deal.random(low), Deal.random(high))
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
