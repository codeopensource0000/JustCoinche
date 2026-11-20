package justcoinche.engine

/**
 * One deal from the first bid to the score: the auction, then, unless all four
 * players passed, the card play under the contract it settled.
 *
 * Immutable like the states it wraps: [call] and [play] return the next round.
 */
class Round private constructor(
    val dealer: Seat,
    val deal: Deal,
    val rules: Rules,
    val auction: Auction,
    /** Null until the auction settles a contract. */
    val cardPlay: CardPlay?,
) {

    enum class Phase { BIDDING, PLAYING, OVER, REDEAL }

    val phase: Phase
        get() = when {
            !auction.isOver -> Phase.BIDDING
            auction.result == AuctionResult.Redeal -> Phase.REDEAL
            checkNotNull(cardPlay).isOver -> Phase.OVER
            else -> Phase.PLAYING
        }

    /** The seat that has to bid or play now, or null once the deal is over or redealt. */
    val toAct: Seat?
        get() = when (phase) {
            Phase.BIDDING -> auction.toSpeak
            Phase.PLAYING -> checkNotNull(cardPlay).toPlay
            Phase.OVER, Phase.REDEAL -> null
        }

    /** The contract the auction settled, or null while bidding or after a redeal. */
    val contract: Contract? get() = (auction.result as? AuctionResult.Contracted)?.contract

    fun call(seat: Seat, bid: Bid): Round {
        check(phase == Phase.BIDDING) { "The auction is over" }
        val auction = auction.call(seat, bid)
        // The player on the dealer's right leads the first trick, as they spoke first.
        val cardPlay = (auction.result as? AuctionResult.Contracted)?.let {
            CardPlay.start(deal, it.contract.type, rules, leader = dealer.next)
        }
        return Round(dealer, deal, rules, auction, cardPlay)
    }

    fun play(card: Card): Round {
        check(phase == Phase.PLAYING) { "No card can be played in phase $phase" }
        return Round(dealer, deal, rules, auction, checkNotNull(cardPlay).play(card))
    }

    /** Whether the attacking team made its contract, once the deal is over. */
    val isContractMade: Boolean
        get() {
            check(phase == Phase.OVER) { "The deal is not over" }
            return isMade(checkNotNull(contract), checkNotNull(cardPlay).outcome())
        }

    /** The points each team scores. [round] is false for the final deal of a game. */
    fun score(round: Boolean = true): TeamPoints {
        check(phase == Phase.OVER) { "The deal is not over" }
        return scoreDeal(checkNotNull(contract), checkNotNull(cardPlay).outcome(), rules, round)
    }

    companion object {
        fun start(dealer: Seat, deal: Deal, rules: Rules): Round =
            Round(dealer, deal, rules, Auction.start(dealer, rules), cardPlay = null)
    }
}
