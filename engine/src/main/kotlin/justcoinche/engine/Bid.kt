package justcoinche.engine

/** What a player says during the auction. */
sealed interface Bid {

    data object Pass : Bid

    /** A contract to make at least [value] points (80 to 180, by tens) under [type]. */
    data class Announce(val value: Int, val type: ContractType) : Bid {
        init {
            require(value in Bid.MIN_VALUE..Bid.MAX_VALUE && value % Bid.STEP == 0) { "Invalid bid value: $value" }
        }
    }

    /** A contract to win all eight tricks under [type]. Nothing can be bid above it. */
    data class Capot(val type: ContractType) : Bid

    /** An opponent of the highest bidder bets that the contract will fail. It ends the bidding. */
    data object Coinche : Bid

    /** The coinched team's answer: it bets that the contract will be made anyway. */
    data object Surcoinche : Bid

    companion object {
        const val MIN_VALUE = 80
        const val MAX_VALUE = 180
        const val STEP = 10
    }
}

/** One thing said during the auction: who said it and what. */
data class Call(val seat: Seat, val bid: Bid)
