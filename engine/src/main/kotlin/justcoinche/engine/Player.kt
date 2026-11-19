package justcoinche.engine

/**
 * Everything a player may know when it has to act: its own hand and what has
 * been said and played in front of everyone. Never the other hands: a player
 * that saw them would cheat, and every measure of strength would be false.
 */
data class PlayerView(
    val seat: Seat,
    val rules: Rules,
    val dealer: Seat,
    /** The cards still in this player's hand. */
    val hand: CardSet,
    /** Everything said in the auction, in order. */
    val calls: List<Call>,
    /** The contract, once the auction has settled it. */
    val contract: Contract?,
    /** Completed tricks, in order. */
    val tricks: List<Trick>,
    /** The trick being played, during the card play. */
    val currentTrick: Trick?,
    /** What this player may bid now; empty when it is not its turn to speak. */
    val legalBids: List<Bid>,
    /** What this player may play now; empty when it is not its turn to play. */
    val playableCards: CardSet,
)

/** Someone at the table: a computer player, or the human through the app. */
interface Player {
    fun bid(view: PlayerView): Bid
    fun play(view: PlayerView): Card
}

/** What [seat] may know of this round at this moment. */
fun Round.viewFor(seat: Seat): PlayerView {
    val play = cardPlay
    val playing = phase == Round.Phase.PLAYING && play != null && play.toPlay == seat
    return PlayerView(
        seat = seat,
        rules = rules,
        dealer = dealer,
        hand = play?.hand(seat) ?: deal.hand(seat),
        calls = auction.calls,
        contract = contract,
        tricks = play?.tricks.orEmpty(),
        currentTrick = play?.currentTrick,
        legalBids = if (phase == Round.Phase.BIDDING) auction.legalBids(seat) else emptyList(),
        playableCards = if (playing) checkNotNull(play).playableCards() else CardSet.EMPTY,
    )
}
