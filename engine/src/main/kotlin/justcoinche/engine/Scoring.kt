package justcoinche.engine

/** A number of points for each team. */
data class TeamPoints(val northSouth: Int, val eastWest: Int) {

    operator fun get(team: Team): Int = if (team == Team.NORTH_SOUTH) northSouth else eastWest

    companion object {
        /** Builds the pair from one team's points and its opponents'. */
        fun of(team: Team, points: Int, opponentPoints: Int): TeamPoints =
            if (team == Team.NORTH_SOUTH) TeamPoints(points, opponentPoints) else TeamPoints(opponentPoints, points)
    }
}

/** What a finished card play gives the scoring, whatever way it was played. */
data class PlayOutcome(
    /** Points made by each team: card points, 10 for the last trick, 20 for a belote it holds. */
    val pointsMade: TeamPoints,
    /** The team holding king and queen of trumps, if any. */
    val beloteTeam: Team?,
    /** The team that won all eight tricks, if one did. */
    val capotTeam: Team?,
)

/**
 * The points each team scores for a finished deal, following docs/regles.md §8.
 *
 * Whether the contract is made is always decided on raw points. [round] only
 * concerns what is written on the score sheet: it is false for the final
 * deal of a game, which is scored without rounding.
 */
fun scoreDeal(contract: Contract, outcome: PlayOutcome, rules: Rules, round: Boolean = true): TeamPoints {
    val attack = contract.declarer.team
    val made = if (contract.isCapot) {
        outcome.capotTeam == attack
    } else {
        outcome.pointsMade[attack] >= contract.value
    }
    val (attackScore, defenceScore) = when (rules.scoringMode) {
        ScoringMode.CONTRACT_ONLY -> contractOnly(contract, outcome, made)
        ScoringMode.POINTS_MADE_PLUS_CONTRACT -> pointsMadePlusContract(contract, outcome, made, rules, round)
    }
    return TeamPoints.of(attack, attackScore, defenceScore)
}

/** Mode A, "points au contrat". Returns (attack, defence). */
private fun contractOnly(contract: Contract, outcome: PlayOutcome, made: Boolean): Pair<Int, Int> {
    val stake = contract.coinche.multiplier * contract.value
    // The declarer's belote only helps make the contract; the defence's always scores.
    val defenceBelote = beloteOf(contract.declarer.team.opponent, outcome)
    return when {
        made -> stake to defenceBelote
        // A plain failure costs a fixed 160; a coinched one, or a capot, costs the stake.
        contract.isCapot || contract.coinche != CoincheLevel.NONE -> 0 to stake + defenceBelote
        else -> 0 to FAILURE_POINTS + defenceBelote
    }
}

/** Mode B, "points faits + contrat". Returns (attack, defence). */
private fun pointsMadePlusContract(
    contract: Contract,
    outcome: PlayOutcome,
    made: Boolean,
    rules: Rules,
    round: Boolean,
): Pair<Int, Int> {
    val attack = contract.declarer.team
    val defence = attack.opponent
    val stake = contract.coinche.multiplier * contract.value
    val attackBelote = beloteOf(attack, outcome)
    val defenceBelote = beloteOf(defence, outcome)
    fun written(points: Int) = if (round) roundToTen(points) else points

    if (!made) {
        // The defence takes the stake and the 160 of the deal, plus the declarer's
        // belote unless the table lets the declarer keep it.
        val keptBelote = if (rules.beloteKeptOnFailure) attackBelote else 0
        return keptBelote to stake + FAILURE_POINTS + defenceBelote + (attackBelote - keptBelote)
    }
    val attackScore = if (outcome.capotTeam == attack) {
        // All eight tricks: the 250 replace the 160 of the deal.
        stake + CAPOT_BONUS + attackBelote
    } else {
        stake + written(outcome.pointsMade[attack])
    }
    // A coinched defence that loses scores nothing but its belote.
    val defenceScore = if (contract.coinche == CoincheLevel.NONE) {
        written(outcome.pointsMade[defence])
    } else {
        defenceBelote
    }
    return attackScore to defenceScore
}

private fun beloteOf(team: Team, outcome: PlayOutcome): Int =
    if (outcome.beloteTeam == team) CardPlay.BELOTE_BONUS else 0

/** To the nearest ten, a 5 rounding up: 44 → 40, 45 → 50, 48 → 50. */
internal fun roundToTen(points: Int): Int = (points + HALF_TEN) / TEN * TEN

private const val FAILURE_POINTS = 160
private const val CAPOT_BONUS = 250
private const val TEN = 10
private const val HALF_TEN = 5
