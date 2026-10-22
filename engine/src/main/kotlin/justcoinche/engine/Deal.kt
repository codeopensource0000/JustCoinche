package justcoinche.engine

import kotlin.random.Random

/**
 * Who holds which card at the start of a deal: one 8-card hand per seat.
 *
 * A deal produced by [random] is fully determined by the generator's seed, so
 * replaying a seed replays the exact same cards. The game history, duplicate
 * matches in the arena and bug reproduction all rely on that.
 */
data class Deal(private val hands: List<CardSet>) {

    init {
        require(hands.size == Seat.entries.size) { "A deal needs one hand per seat, got ${hands.size}" }
        require(hands.all { it.size == HAND_SIZE }) { "Every hand must hold $HAND_SIZE cards" }
        // Four hands of 8 cards that together cover all 32 cards cannot overlap.
        require(hands.fold(CardSet.EMPTY) { seen, hand -> seen union hand } == CardSet.FULL) {
            "The hands must share out the whole deck, each card exactly once"
        }
    }

    fun hand(seat: Seat): CardSet = hands[seat.ordinal]

    /**
     * Packs the deal into 64 bits: two bits per card holding the [Seat.ordinal]
     * of its owner, card 0 in the lowest bits.
     *
     * This is what the game history stores. Unlike a seed, it replays the deal
     * exactly even if the random generator or the shuffle ever change.
     */
    fun encode(): Long {
        var code = 0L
        Seat.entries.forEach { seat ->
            hand(seat).forEach { card ->
                code = code or (seat.ordinal.toLong() shl (card.index * BITS_PER_CARD))
            }
        }
        return code
    }

    companion object {
        const val HAND_SIZE = 8
        private const val BITS_PER_CARD = 2
        private const val SEAT_MASK = 0b11L

        /**
         * Rebuilds a deal from [encode]'s output. A code that does not give
         * exactly 8 cards to each seat (a corrupted history line, say) is
         * rejected rather than turned into an impossible deal.
         */
        fun decode(code: Long): Deal {
            val hands = Array(Seat.entries.size) { CardSet.EMPTY }
            Card.deck.forEach { card ->
                val seat = ((code ushr (card.index * BITS_PER_CARD)) and SEAT_MASK).toInt()
                hands[seat] += card
            }
            return Deal(hands.toList())
        }

        /**
         * Deals from a 64-bit seed. A 32-bit seed could reach at most 2^32 deals,
         * a tiny fraction of the ~10^17 possible ones; 2^64 seeds cover them all.
         */
        fun random(seed: Long): Deal = random(Random(seed))

        /**
         * Shuffles the deck with [random] and gives 8 cards to each seat, in [Seat] order.
         *
         * The traditional 3-2-3 packets are not reproduced: after a uniform
         * shuffle, any fixed way of handing out the cards yields the same
         * distribution of deals.
         */
        fun random(random: Random): Deal =
            Deal(Card.deck.shuffled(random).chunked(HAND_SIZE).map { CardSet.of(it) })
    }
}
