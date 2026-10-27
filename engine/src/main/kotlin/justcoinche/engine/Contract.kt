package justcoinche.engine

enum class CoincheLevel { NONE, COINCHED, SURCOINCHED }

/** What the declarer's team has to make, as settled by the auction. */
data class Contract(
    /** The player who made the winning bid. */
    val declarer: Seat,
    val type: ContractType,
    /** The points to reach: the bid's value, or [CAPOT_VALUE] for a capot. */
    val value: Int,
    val isCapot: Boolean,
    val coinche: CoincheLevel,
) {
    companion object {
        const val CAPOT_VALUE = 250
    }
}

/** How an auction ends. */
sealed interface AuctionResult {
    /** All four players passed without bidding: the cards are dealt again. */
    data object Redeal : AuctionResult

    data class Contracted(val contract: Contract) : AuctionResult
}
