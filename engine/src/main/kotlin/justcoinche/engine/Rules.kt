package justcoinche.engine

/** When a player may coinche. */
enum class CoincheTiming {
    /** Only when it is one's turn to speak. */
    IN_TURN,

    /** "À la volée": any opponent of the highest bidder, at any moment of the auction. */
    ANY_TIME,
}

/** How a deal turns into points on the score sheet (docs/regles.md §8). */
enum class ScoringMode {
    /** "Points au contrat": only the contract's value is scored. */
    CONTRACT_ONLY,

    /** "Points faits + contrat": the points made in play are scored on top of the contract. */
    POINTS_MADE_PLUS_CONTRACT,
}

/**
 * The variant choices of a table that change how the game is played, as
 * documented in docs/regles.md.
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
    val scoringMode: ScoringMode = ScoringMode.CONTRACT_ONLY,
    /**
     * When a contract fails, whether the declaring team keeps its belote.
     * When false, the defence scores it instead. Only matters in
     * [ScoringMode.POINTS_MADE_PLUS_CONTRACT]: in contract-only scoring the
     * declarer's belote never scores, it only helps make the contract.
     */
    val beloteKeptOnFailure: Boolean = false,
)
