package justcoinche.engine

/**
 * A trick: the cards played so far, in playing order, the first one by [leader].
 *
 * Like [CardSet], a Trick is never modified: playing a card returns a new Trick,
 * so a search can try several cards from the same position without undoing anything.
 */
data class Trick(val leader: Seat, val cards: List<Card> = emptyList()) {

    init {
        require(cards.size <= Seat.entries.size) { "A trick holds at most 4 cards, got ${cards.size}" }
    }

    val isComplete: Boolean get() = cards.size == Seat.entries.size

    /** The suit of the first card, which the other players must follow; null before the lead. */
    val ledSuit: Suit? get() = cards.firstOrNull()?.suit

    /** The seat whose turn it is. */
    val nextSeat: Seat get() = seatAt(cards.size)

    fun play(card: Card): Trick {
        check(!isComplete) { "The trick is already complete" }
        return copy(cards = cards + card)
    }

    /** The card currently winning the trick under [contract], or null before the lead. */
    fun winningCard(contract: ContractType): Card? =
        cards.reduceOrNull { best, card -> if (beats(card, best, contract)) card else best }

    /** The seat currently winning the trick (the "master"), or null before the lead. */
    fun winner(contract: ContractType): Seat? {
        val best = winningCard(contract) ?: return null
        return seatAt(cards.indexOf(best))
    }

    /** Card points in the trick. The 10 for the last trick is the deal's business, not the trick's. */
    fun points(contract: ContractType): Int = cards.sumOf { contract.points(it) }

    private fun seatAt(position: Int): Seat = Seat.entries[(leader.ordinal + position) % Seat.entries.size]
}

/**
 * Whether [challenger] takes the trick from [current], the card winning so far.
 *
 * Only a higher card of the same suit or, in a suit contract, a trump played
 * on another suit can take the lead. A discard never wins, however high.
 */
private fun beats(challenger: Card, current: Card, contract: ContractType): Boolean = when {
    challenger.suit == current.suit -> contract.strength(challenger) > contract.strength(current)
    contract is ContractType.SuitTrump -> challenger.suit == contract.trump
    else -> false
}
