package justcoinche.engine

enum class Suit { CLUBS, DIAMONDS, HEARTS, SPADES }

/** Ranks in face order. Trick strength and points depend on the contract, see [ContractType]. */
enum class Rank { SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING, ACE }

/**
 * One of the 32 cards, stored as an index in 0..31 (`suit * 8 + rank`).
 *
 * The index is what lets a whole hand fit in the 32 bits of an `Int`, one bit
 * per card. The search-based players resolve thousands of deals per decision,
 * so card sets have to be bit operations rather than collections.
 */
@JvmInline
value class Card(val index: Int) {
    init {
        require(index in 0 until DECK_SIZE) { "Card index out of range: $index" }
    }

    val suit: Suit get() = Suit.entries[index / RANKS_PER_SUIT]
    val rank: Rank get() = Rank.entries[index % RANKS_PER_SUIT]

    override fun toString(): String = "$rank of $suit"

    companion object {
        const val DECK_SIZE = 32
        const val RANKS_PER_SUIT = 8

        fun of(rank: Rank, suit: Suit): Card = Card(suit.ordinal * RANKS_PER_SUIT + rank.ordinal)

        val deck: List<Card> = (0 until DECK_SIZE).map(::Card)
    }
}
