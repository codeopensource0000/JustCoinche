package justcoinche.engine

/** A finished deal of a game, with what it scored. */
data class DealResult(
    val round: Round,
    /** The points scored, or null for a redeal. */
    val score: TeamPoints?,
    /** The deal that took a team past the target: scored without rounding. */
    val isFinal: Boolean,
) {
    /** The deal's line in the game record. */
    val historyLine: String get() = History.write(round, rounded = !isFinal)
}

/**
 * A game: deals follow one another until a team goes past the target score
 * (docs/regles.md §9). Reaching it exactly is not enough.
 *
 * The game never shuffles: whoever runs it supplies each deal with [deal].
 * The arena can then replay the same deals with the teams swapped, and the
 * app can shuffle from an unpredictable seed. Like the other states, a Game
 * is immutable.
 */
@ConsistentCopyVisibility
data class Game private constructor(
    val rules: Rules,
    /** The dealer of the deal being played, or of the next one. */
    val dealer: Seat,
    val scores: TeamPoints,
    /** Every finished deal, redeals included, in order. */
    val results: List<DealResult>,
    /** The deal being played, or null between deals. */
    val currentRound: Round?,
) {

    /**
     * The team that has won: once a team has gone past the target (strictly:
     * 1000 exactly does not win a game played to 1000), the one that went
     * furthest. Null while the game goes on, including after an exact tie
     * above the target, which calls for one more deal.
     */
    val winner: Team?
        get() = when {
            maxOf(scores.northSouth, scores.eastWest) <= rules.targetScore -> null
            scores.northSouth == scores.eastWest -> null
            scores.northSouth > scores.eastWest -> Team.NORTH_SOUTH
            else -> Team.EAST_WEST
        }

    val isOver: Boolean get() = winner != null

    /** Starts the next deal with [deal], dealt by [dealer]. */
    fun deal(deal: Deal): Game {
        check(!isOver) { "The game is over" }
        check(currentRound == null) { "A deal is already being played" }
        return copy(currentRound = Round.start(dealer, deal, rules))
    }

    fun call(seat: Seat, bid: Bid): Game = after(round().call(seat, bid))

    fun play(card: Card): Game = after(round().play(card))

    private fun round(): Round = checkNotNull(currentRound) { "No deal is being played: call deal() first" }

    /** Keeps playing the round, or records it once it is finished and passes the deal on. */
    private fun after(round: Round): Game = when (round.phase) {
        Round.Phase.BIDDING, Round.Phase.PLAYING -> copy(currentRound = round)
        Round.Phase.REDEAL -> passDeal(DealResult(round, score = null, isFinal = false), scores)
        Round.Phase.OVER -> {
            // The deal is final when, counted without rounding, it takes a team past the target.
            val raw = round.score(round = false)
            val isFinal = Team.entries.any { scores[it] + raw[it] > rules.targetScore }
            val score = if (isFinal) raw else round.score()
            passDeal(DealResult(round, score, isFinal), scores + score)
        }
    }

    /** The next player on the dealer's right deals next, after a redeal too. */
    private fun passDeal(result: DealResult, newScores: TeamPoints): Game =
        copy(dealer = dealer.next, scores = newScores, results = results + result, currentRound = null)

    companion object {
        fun start(rules: Rules, firstDealer: Seat): Game = resume(rules, firstDealer, TeamPoints(0, 0))

        /** Resumes a game at the given scores, for instance one saved by the app. */
        fun resume(rules: Rules, dealer: Seat, scores: TeamPoints): Game =
            Game(rules, dealer, scores, results = emptyList(), currentRound = null)
    }
}
