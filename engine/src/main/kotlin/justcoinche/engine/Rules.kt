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
    CONTRACT_POINTS,

    /** "Contrat et points faits": the points made in play are scored on top of the contract. */
    CONTRACT_AND_POINTS_MADE,
}

/**
 * The variant choices of a table that change how the game is played, as
 * documented in docs/regles.md.
 */
data class Rules(
    /**
     * When an opponent has trumped and you can neither follow suit nor
     * overtrump, whether you must still play a lower trump ("sous-couper").
     * When false, the table allows you to "pisser" instead: discard any card,
     * as you always may when you hold no trump or your partner is master.
     */
    val mustUnderTrump: Boolean = true,
    val coincheTiming: CoincheTiming = CoincheTiming.IN_TURN,
    val allowNoTrump: Boolean = true,
    val allowAllTrump: Boolean = true,
    val scoringMode: ScoringMode = ScoringMode.CONTRACT_POINTS,
    /**
     * When a contract fails, whether the declaring team keeps its belote.
     * When false, the defence scores it instead. Only matters in
     * [ScoringMode.CONTRACT_AND_POINTS_MADE]: with contract points the
     * declarer's belote never scores, it only helps make the contract.
     */
    val beloteKeptOnFailure: Boolean = false,
    /**
     * The score that ends a game. A default may refer to an earlier parameter:
     * 1000 when only the contract is scored, 2000 when points made are added.
     */
    val targetScore: Int =
        if (scoringMode == ScoringMode.CONTRACT_POINTS) CONTRACT_POINTS_TARGET else POINTS_MADE_TARGET,
) {
    companion object {
        const val CONTRACT_POINTS_TARGET = 1000
        const val POINTS_MADE_TARGET = 2000
    }
}
