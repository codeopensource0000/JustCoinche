package justcoinche.engine

/**
 * A set of cards stored as a 32-bit mask: bit `i` is set when `Card(i)` is in the set.
 *
 * A hand, the cards still unseen, the cards of one suit: all of them are
 * CardSets, and combining them is a single machine instruction. That is what
 * keeps the search fast when it has to replay thousands of deals per decision.
 */
@JvmInline
value class CardSet(val bits: Int) {

    val size: Int get() = bits.countOneBits()

    fun isEmpty(): Boolean = bits == 0

    operator fun contains(card: Card): Boolean = bits and card.bit != 0

    operator fun plus(card: Card): CardSet = CardSet(bits or card.bit)

    operator fun minus(card: Card): CardSet = CardSet(bits and card.bit.inv())

    infix fun union(other: CardSet): CardSet = CardSet(bits or other.bits)

    infix fun intersect(other: CardSet): CardSet = CardSet(bits and other.bits)

    /** The cards of this set that belong to [suit]. Each suit occupies 8 consecutive bits. */
    fun ofSuit(suit: Suit): CardSet = CardSet(bits and (SUIT_MASK shl suit.ordinal * Card.RANKS_PER_SUIT))

    /** Calls [action] on each card, in index order, without allocating anything. */
    inline fun forEach(action: (Card) -> Unit) {
        var remaining = bits
        while (remaining != 0) {
            action(Card(remaining.countTrailingZeroBits()))
            remaining = remaining and (remaining - 1) // clears the lowest set bit
        }
    }

    fun toList(): List<Card> {
        // Deliberately not `buildList { forEach { add(it) } }`: inside buildList,
        // `forEach` resolves to the list being built (still empty), not to this set.
        val result = ArrayList<Card>(size)
        forEach { result.add(it) }
        return result
    }

    override fun toString(): String = toList().toString()

    companion object {
        private const val SUIT_MASK = 0xFF

        val EMPTY = CardSet(0)
        val FULL = CardSet(-1) // all 32 bits set

        // Not `vararg cards: Card`: Kotlin forbids varargs of a value class, because
        // there is no array type that would store them unboxed.
        fun of(cards: Iterable<Card>): CardSet = cards.fold(EMPTY) { set, card -> set + card }
    }
}

private val Card.bit: Int get() = 1 shl index
