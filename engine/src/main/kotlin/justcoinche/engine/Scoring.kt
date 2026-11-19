package justcoinche.engine

/** A number of points for each team. */
data class TeamPoints(val northSouth: Int, val eastWest: Int) {

    operator fun get(team: Team): Int = if (team == Team.NORTH_SOUTH) northSouth else eastWest

    operator fun plus(other: TeamPoints): TeamPoints =
        TeamPoints(northSouth + other.northSouth, eastWest + other.eastWest)

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
        // A capot belote also needs the belote, in either hand of the team.
        outcome.capotTeam == attack && (!contract.capotBelote || outcome.beloteTeam == attack)
    } else {
        outcome.pointsMade[attack] >= contract.value
    }
    val (attackScore, defenceScore) = when (rules.scoringMode) {
        ScoringMode.CONTRACT_POINTS -> contractPoints(contract, outcome, made)
        ScoringMode.CONTRACT_AND_POINTS_MADE -> contractAndPointsMade(contract, outcome, made, rules, round)
    }
    return TeamPoints.of(attack, attackScore, defenceScore)
}

/** "Points au contrat": only the contract scores. Returns (attack, defence). */
private fun contractPoints(contract: Contract, outcome: PlayOutcome, made: Boolean): Pair<Int, Int> {
    val stake = stakeOf(contract)
    // The declarer's belote only helps make the contract; the defence's always scores.
    val defenceBelote = beloteOf(contract.declarer.team.opponent, outcome)
    return when {
        made -> stake to defenceBelote
        // A plain failure costs a fixed 160; a coinched one, or a capot, costs the stake.
        contract.isCapot || contract.coinche != CoincheLevel.NONE -> 0 to stake + defenceBelote
        else -> 0 to FAILURE_POINTS + defenceBelote
    }
}

/** "Contrat et points faits": the points made score on top of the contract. Returns (attack, defence). */
private fun contractAndPointsMade(
    contract: Contract,
    outcome: PlayOutcome,
    made: Boolean,
    rules: Rules,
    round: Boolean,
): Pair<Int, Int> {
    val attack = contract.declarer.team
    val defence = attack.opponent
    val stake = stakeOf(contract)
    // A capot belote's value already holds the belote: it is not counted again.
    val attackBelote = if (contract.capotBelote) 0 else beloteOf(attack, outcome)
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

/**
 * What the contract itself is worth: its value times the coinche multiplier.
 * The belote is never multiplied, so a capot belote doubles its 250, not its 270.
 */
private fun stakeOf(contract: Contract): Int {
    val belote = if (contract.capotBelote) CardPlay.BELOTE_BONUS else 0
    return contract.coinche.multiplier * (contract.value - belote) + belote
}

private fun beloteOf(team: Team, outcome: PlayOutcome): Int =
    if (outcome.beloteTeam == team) CardPlay.BELOTE_BONUS else 0

/** To the nearest ten, a 5 rounding up: 44 → 40, 45 → 50, 48 → 50. */
internal fun roundToTen(points: Int): Int = (points + HALF_TEN) / TEN * TEN

private const val FAILURE_POINTS = 160
private const val CAPOT_BONUS = 250
private const val TEN = 10
private const val HALF_TEN = 5
