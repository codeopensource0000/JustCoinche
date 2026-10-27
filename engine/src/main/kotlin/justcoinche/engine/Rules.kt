package justcoinche.engine

/**
 * The variant choices of a table that change how the game is played, as
 * documented in docs/regles.md. More will join as the engine grows (scoring
 * mode, coinche timing, ...).
 */
data class Rules(
    /**
     * "Pisser": when an opponent has trumped and you can neither follow suit
     * nor overtrump, you must still play a lower trump. When false, you may
     * discard any card instead.
     */
    val mustUnderTrump: Boolean = true,
)
