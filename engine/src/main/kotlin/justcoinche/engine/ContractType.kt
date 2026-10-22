package justcoinche.engine

/**
 * What the contract does to the suits. The three types share one rule engine:
 * they differ only by which order and which point scale each suit follows.
 */
sealed interface ContractType {
    data class SuitTrump(val trump: Suit) : ContractType
    data object NoTrump : ContractType
    data object AllTrump : ContractType

    /** Whether [suit] ranks in trump order (Jack, Nine, Ace, ...) rather than plain order (Ace, Ten, King, ...). */
    fun usesTrumpOrder(suit: Suit): Boolean = when (this) {
        is SuitTrump -> suit == trump
        NoTrump -> false
        AllTrump -> true
    }

    /** Card points under this contract. Every contract type totals 152 over the deck. */
    fun points(card: Card): Int {
        val table = when (this) {
            is SuitTrump -> if (card.suit == trump) TRUMP_POINTS else PLAIN_POINTS
            NoTrump -> NO_TRUMP_POINTS
            AllTrump -> ALL_TRUMP_POINTS
        }
        return table[card.rank.ordinal]
    }

    /**
     * Strength of [card] within its own suit: the higher value wins a trick
     * among cards of the same suit. Comparing across suits is the trick
     * rules' job (following suit, trumping), not this function's.
     */
    fun strength(card: Card): Int {
        val table = if (usesTrumpOrder(card.suit)) TRUMP_STRENGTH else PLAIN_STRENGTH
        return table[card.rank.ordinal]
    }

    private companion object {
        // All tables are indexed by Rank.ordinal: 7, 8, 9, 10, J, Q, K, A.
        val TRUMP_POINTS = intArrayOf(0, 0, 14, 10, 20, 3, 4, 11)
        val PLAIN_POINTS = intArrayOf(0, 0, 0, 10, 2, 3, 4, 11)

        // No-trump: plain order, the Ace raised to 19 so that the deck still totals 152.
        val NO_TRUMP_POINTS = intArrayOf(0, 0, 0, 10, 2, 3, 4, 19)

        // All-trump scale chosen for this project: J 14, 9 9, A 6, 10 5, K 3, Q 1.
        val ALL_TRUMP_POINTS = intArrayOf(0, 0, 9, 5, 14, 1, 3, 6)

        // Trump order, weakest to strongest: 7, 8, Q, K, 10, A, 9, J.
        val TRUMP_STRENGTH = intArrayOf(0, 1, 6, 4, 7, 2, 3, 5)

        // Plain order, weakest to strongest: 7, 8, 9, J, Q, K, 10, A.
        val PLAIN_STRENGTH = intArrayOf(0, 1, 2, 6, 3, 4, 5, 7)
    }
}
