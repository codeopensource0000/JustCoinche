package justcoinche.engine

/** How far the contract was doubled, and by how much that multiplies its value. */
enum class CoincheLevel(val multiplier: Int) {
    NONE(1),
    COINCHED(2),
    SURCOINCHED(3),
}

/** What the declarer's team has to make, as settled by the auction. */
data class Contract(
    /** The player who made the winning bid. */
    val declarer: Seat,
    val type: ContractType,
    /** The bid's value: 80 to 180, [CAPOT_VALUE] for a capot, [CAPOT_BELOTE_VALUE] for a capot belote. */
    val value: Int,
    val isCapot: Boolean,
    val coinche: CoincheLevel,
    /** A capot that must also hold the belote; its value includes the belote's 20 points. */
    val capotBelote: Boolean = false,
) {
    companion object {
        const val CAPOT_VALUE = 250
        const val CAPOT_BELOTE_VALUE = 270
    }
}

/** How an auction ends. */
sealed interface AuctionResult {
    /** All four players passed without bidding: the cards are dealt again. */
    data object Redeal : AuctionResult

    data class Contracted(val contract: Contract) : AuctionResult
}
