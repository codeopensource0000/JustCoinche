package justcoinche.engine

/**
 * The cards of [hand] that may be played on [trick], following docs/regles.md §5.
 *
 * The search calls this for every card it simulates. It never returns an
 * empty set: a player who holds cards always has a legal one.
 */
fun legalCards(hand: CardSet, trick: Trick, contract: ContractType, rules: Rules): CardSet {
    require(!hand.isEmpty()) { "An empty hand has nothing to play" }
    check(!trick.isComplete) { "The trick is already complete" }

    val ledSuit = trick.ledSuit ?: return hand // leading: any card
    val following = hand.ofSuit(ledSuit)
    return if (following.isEmpty()) {
        cannotFollow(hand, trick, contract, rules)
    } else {
        followSuit(following, trick, contract, ledSuit)
    }
}

/** The player holds the led suit and must play it. */
private fun followSuit(following: CardSet, trick: Trick, contract: ContractType, ledSuit: Suit): CardSet {
    // A plain suit: any card of the suit, no need to go higher.
    if (!contract.usesTrumpOrder(ledSuit)) return following
    // A suit in trump order (a trump lead, or any lead in all-trump):
    // go higher than the winning card if possible, even over one's partner.
    val higher = following.strongerThan(trick.currentWinningCard(contract), contract)
    return if (higher.isEmpty()) following else higher
}

/** The player has no card of the led suit. */
private fun cannotFollow(hand: CardSet, trick: Trick, contract: ContractType, rules: Rules): CardSet {
    // Without a trump suit (no-trump, all-trump) nobody is ever obliged to trump.
    if (contract !is ContractType.SuitTrump) return hand

    val trumps = hand.ofSuit(contract.trump)
    val winning = trick.currentWinningCard(contract)
    val partnerIsMaster = trick.winner(contract) == trick.nextSeat.partner
    return when {
        trumps.isEmpty() || partnerIsMaster -> hand
        winning.suit != contract.trump -> trumps // nobody has trumped yet: any trump
        else -> {
            // An opponent has trumped: overtrump if possible, otherwise
            // play a lower trump ("pisser") or, if the table allows, anything.
            val overtrumps = trumps.strongerThan(winning, contract)
            when {
                !overtrumps.isEmpty() -> overtrumps
                rules.mustUnderTrump -> trumps
                else -> hand
            }
        }
    }
}

/** The winning card of a trick that has been led. */
private fun Trick.currentWinningCard(contract: ContractType): Card =
    checkNotNull(winningCard(contract)) { "The trick has not been led yet" }

/** The cards of this set stronger than [card]. Only meaningful for cards of [card]'s suit. */
private fun CardSet.strongerThan(card: Card, contract: ContractType): CardSet =
    filter { contract.strength(it) > contract.strength(card) }
