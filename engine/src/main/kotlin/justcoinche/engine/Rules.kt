package justcoinche.engine

/** When a player may coinche. */
enum class CoincheTiming {
    /** Only when it is one's turn to speak. */
    IN_TURN,

    /** "À la volée": any opponent of the highest bidder, at any moment of the auction. */
    ANY_TIME,
}

/**
 * The variant choices of a table that change how the game is played, as
 * documented in docs/regles.md. More will join as the engine grows (scoring
 * mode, ...).
 */
data class Rules(
    /**
     * "Pisser": when an opponent has trumped and you can neither follow suit
     * nor overtrump, you must still play a lower trump. When false, you may
     * discard any card instead.
     */
    val mustUnderTrump: Boolean = true,
    val coincheTiming: CoincheTiming = CoincheTiming.IN_TURN,
    val allowNoTrump: Boolean = true,
    val allowAllTrump: Boolean = true,
)
