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

    companion object {
        const val HAND_SIZE = 8

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
