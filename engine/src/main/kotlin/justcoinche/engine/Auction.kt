package justcoinche.engine

/**
 * The bidding of one deal: who may say what, and when it is over (docs/regles.md §4).
 *
 * It holds no strategy, only the rules. Like the other game states it is
 * immutable: [call] returns the next state.
 *
 * After a coinche, the coinched team answers in turn: first the player after
 * the coincher, then that player's partner. Either may surcoinche; two passes
 * leave the contract coinched.
 */
@ConsistentCopyVisibility
data class Auction private constructor(
    val rules: Rules,
    /** Everything said so far, in order: the auction's part of the game record. */
    val calls: List<Call>,
    /** The seat whose turn it is to speak. Meaningless once [result] is known. */
    val toSpeak: Seat,
    /** The highest announce or capot so far, with its author. */
    private val highest: Call?,
    private val passesInRow: Int,
    private val coinche: CoincheLevel,
    /** After a coinche, how many players of the coinched team may still answer. */
    private val answersLeft: Int,
    /** How the auction ended, or null while it goes on. */
    val result: AuctionResult?,
) {

    val isOver: Boolean get() = result != null

    /** Everything [seat] may say now. Empty when it may say nothing. */
    fun legalBids(seat: Seat): List<Bid> = when {
        isOver -> emptyList()
        coinche != CoincheLevel.NONE -> if (seat == toSpeak) listOf(Bid.Pass, Bid.Surcoinche) else emptyList()
        seat == toSpeak -> inTurnBids(seat)
        rules.coincheTiming == CoincheTiming.ANY_TIME && canCoinche(seat) -> listOf(Bid.Coinche)
        else -> emptyList()
    }

    fun call(seat: Seat, bid: Bid): Auction {
        require(bid in legalBids(seat)) { "$seat may not say $bid now" }
        val calls = calls + Call(seat, bid)
        return when (bid) {
            Bid.Pass -> afterPass(calls)
            is Bid.Announce, is Bid.Capot ->
                copy(calls = calls, toSpeak = seat.next, highest = Call(seat, bid), passesInRow = 0)
            Bid.Coinche ->
                copy(calls = calls, toSpeak = seat.next, coinche = CoincheLevel.COINCHED, answersLeft = 2)
            Bid.Surcoinche ->
                copy(calls = calls, result = contracted(CoincheLevel.SURCOINCHED))
        }
    }

    private fun inTurnBids(seat: Seat): List<Bid> = buildList {
        add(Bid.Pass)
        addAll(announcesAbove(highest?.bid))
        if (canCoinche(seat)) add(Bid.Coinche)
    }

    /** Every announce and capot that outbids [top]. Nothing outbids a capot. */
    private fun announcesAbove(top: Bid?): List<Bid> {
        if (top is Bid.Capot) return emptyList()
        val lowest = if (top is Bid.Announce) top.value + Bid.STEP else Bid.MIN_VALUE
        return allowedTypes().flatMap { type ->
            (lowest..Bid.MAX_VALUE step Bid.STEP).map { Bid.Announce(it, type) } + Bid.Capot(type)
        }
    }

    private fun allowedTypes(): List<ContractType> = buildList {
        Suit.entries.forEach { add(ContractType.SuitTrump(it)) }
        if (rules.allowNoTrump) add(ContractType.NoTrump)
        if (rules.allowAllTrump) add(ContractType.AllTrump)
    }

    /** Only an opponent of the highest bidder may coinche, and only once. */
    private fun canCoinche(seat: Seat): Boolean =
        highest != null && highest.seat.team != seat.team && coinche == CoincheLevel.NONE

    private fun afterPass(calls: List<Call>): Auction {
        if (coinche != CoincheLevel.NONE) {
            val left = answersLeft - 1
            return if (left == 0) {
                copy(calls = calls, answersLeft = 0, result = contracted(coinche))
            } else {
                copy(calls = calls, toSpeak = toSpeak.partner, answersLeft = left)
            }
        }
        val passes = passesInRow + 1
        val result = when {
            highest == null && passes == Seat.entries.size -> AuctionResult.Redeal
            highest != null && passes == Seat.entries.size - 1 -> contracted(CoincheLevel.NONE)
            else -> null
        }
        return copy(calls = calls, toSpeak = toSpeak.next, passesInRow = passes, result = result)
    }

    private fun contracted(level: CoincheLevel): AuctionResult {
        val top = checkNotNull(highest) { "No contract without a bid" }
        val contract = when (val bid = top.bid) {
            is Bid.Announce ->
                Contract(declarer = top.seat, type = bid.type, value = bid.value, isCapot = false, coinche = level)
            is Bid.Capot ->
                Contract(top.seat, bid.type, value = Contract.CAPOT_VALUE, isCapot = true, coinche = level)
            else -> error("Only an announce or a capot can be the highest bid, got $bid")
        }
        return AuctionResult.Contracted(contract)
    }

    companion object {
        /** The player on the dealer's right speaks first: the next seat in playing order. */
        fun start(dealer: Seat, rules: Rules): Auction = Auction(
            rules = rules,
            calls = emptyList(),
            toSpeak = dealer.next,
            highest = null,
            passesInRow = 0,
            coinche = CoincheLevel.NONE,
            answersLeft = 0,
            result = null,
        )
    }
}
