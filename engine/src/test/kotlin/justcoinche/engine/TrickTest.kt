package justcoinche.engine

import justcoinche.engine.Rank.ACE
import justcoinche.engine.Rank.JACK
import justcoinche.engine.Rank.KING
import justcoinche.engine.Rank.NINE
import justcoinche.engine.Rank.SEVEN
import justcoinche.engine.Rank.TEN
import justcoinche.engine.Suit.CLUBS
import justcoinche.engine.Suit.DIAMONDS
import justcoinche.engine.Suit.HEARTS
import justcoinche.engine.Suit.SPADES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TrickTest {

    private val heartsTrump = ContractType.SuitTrump(HEARTS)

    /** A trick led by [leader] with [cards] played in order. */
    private fun trick(leader: Seat, vararg cards: Pair<Rank, Suit>): Trick =
        cards.fold(Trick(leader)) { trick, (rank, suit) -> trick.play(Card.of(rank, suit)) }

    @Test
    fun `before the lead there is no led suit and no winner`() {
        val empty = Trick(Seat.EAST)
        assertNull(empty.ledSuit)
        assertNull(empty.winner(heartsTrump))
        assertEquals(Seat.EAST, empty.nextSeat)
    }

    @Test
    fun `turns follow the seats from the leader`() {
        val trick = trick(Seat.NORTH, TEN to CLUBS, ACE to CLUBS)
        assertEquals(CLUBS, trick.ledSuit)
        assertEquals(Seat.SOUTH, trick.nextSeat) // NORTH, WEST, then SOUTH
    }

    @Test
    fun `without trumps the highest card of the led suit wins`() {
        // East leads the 10, North plays the ace: the ace wins.
        val trick = trick(Seat.EAST, TEN to CLUBS, ACE to CLUBS, SEVEN to CLUBS, KING to CLUBS)
        assertEquals(Seat.NORTH, trick.winner(heartsTrump))
    }

    @Test
    fun `a discard never wins, even an ace`() {
        val trick = trick(Seat.SOUTH, SEVEN to CLUBS, ACE to SPADES, ACE to DIAMONDS, KING to SPADES)
        assertEquals(Seat.SOUTH, trick.winner(heartsTrump))
    }

    @Test
    fun `any trump beats the led suit and the highest trump wins`() {
        // South leads the ace of clubs, East trumps with the 9, North overtrumps with the jack.
        val trick = trick(Seat.SOUTH, ACE to CLUBS, NINE to HEARTS, JACK to HEARTS, SEVEN to HEARTS)
        assertEquals(Seat.NORTH, trick.winner(heartsTrump))
        assertEquals(Card.of(JACK, HEARTS), trick.winningCard(heartsTrump))
    }

    @Test
    fun `when two players trump, the higher trump wins whichever comes first`() {
        // Clubs led; East trumps with the jack, then West trumps lower with the 9.
        // Once a trump is winning, the next trump is compared to it as a card of
        // the same suit, so the order in which they are played does not matter.
        val higherFirst = trick(Seat.SOUTH, ACE to CLUBS, JACK to HEARTS, KING to CLUBS, NINE to HEARTS)
        assertEquals(Seat.EAST, higherFirst.winner(heartsTrump))

        val lowerFirst = trick(Seat.SOUTH, ACE to CLUBS, NINE to HEARTS, KING to CLUBS, JACK to HEARTS)
        assertEquals(Seat.WEST, lowerFirst.winner(heartsTrump))
    }

    @Test
    fun `in a trump lead the nine beats the ace`() {
        val trick = trick(Seat.WEST, ACE to HEARTS, NINE to HEARTS)
        assertEquals(Seat.SOUTH, trick.winner(heartsTrump)) // WEST then SOUTH
    }

    @Test
    fun `in no-trump nothing beats the led suit and the ace is highest`() {
        val trick = trick(Seat.SOUTH, TEN to CLUBS, JACK to HEARTS, ACE to CLUBS, ACE to SPADES)
        assertEquals(Seat.NORTH, trick.winner(ContractType.NoTrump))
    }

    @Test
    fun `in all-trump the led suit ranks like trumps and other suits cannot win`() {
        // 9 beats ace in trump order; the jack of spades is a discard.
        val trick = trick(Seat.SOUTH, ACE to CLUBS, NINE to CLUBS, JACK to SPADES, KING to CLUBS)
        assertEquals(Seat.EAST, trick.winner(ContractType.AllTrump))
    }

    @Test
    fun `trick points add up the cards under the contract`() {
        val trick = trick(Seat.SOUTH, JACK to HEARTS, TEN to CLUBS, NINE to HEARTS, SEVEN to SPADES)
        assertEquals(20 + 10 + 14 + 0, trick.points(heartsTrump))
        assertEquals(2 + 10 + 0 + 0, trick.points(ContractType.SuitTrump(SPADES)))
    }

    @Test
    fun `a fifth card cannot be played`() {
        val full = trick(Seat.SOUTH, SEVEN to CLUBS, ACE to CLUBS, TEN to CLUBS, KING to CLUBS)
        assertFailsWith<IllegalStateException> { full.play(Card.of(JACK, CLUBS)) }
    }
}
