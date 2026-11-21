package justcoinche.ai

import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.Contract
import justcoinche.engine.Deal
import justcoinche.engine.PlayOutcome
import justcoinche.engine.Rules
import justcoinche.engine.ScoringMode
import justcoinche.engine.Team
import justcoinche.engine.TeamPoints
import justcoinche.engine.Trick
import justcoinche.engine.legalCards
import justcoinche.engine.scoreDeal

/**
 * Scores the legal cards of a position whose four hands are known: for each
 * card, the net score of the deal (the team's points minus its opponents')
 * for the team to play, if everybody then plays perfectly with the cards open.
 *
 * The score depends on the play only through the attack's points, and its
 * tricks for a capot, so the solver is enough. With the contract points
 * scoring, only whether the contract is made counts: one fast null-window
 * search per card then replaces an exact one.
 *
 * Not thread-safe: it reuses its solvers, made when first needed.
 */
internal class CardJudge(private val contract: Contract, private val rules: Rules) {
    private val attack = contract.declarer.team
    private val pointsSolver by lazy { Solver(contract.type, rules, attack, Objective.POINTS) }
    private val tricksSolver by lazy { Solver(contract.type, rules, attack, Objective.TRICKS) }

    /**
     * The net score of each legal card for the team to play, given the
     * remaining [hands] by seat, the finished [tricks], the [current] trick
     * and the team holding the belote, if any.
     */
    fun judge(hands: List<CardSet>, tricks: List<Trick>, current: Trick, beloteTeam: Team?): Map<Card, Int> {
        val type = contract.type
        val attackTricks = tricks.count { checkNotNull(it.winner(type)).team == attack }
        val attackPoints = tricks.filter { checkNotNull(it.winner(type)).team == attack }.sumOf { it.points(type) }
        val tricksLeft = Deal.HAND_SIZE - tricks.size
        val outcomes = Outcomes(beloteTeam)
        val toPlay = current.nextSeat.team
        // The attack's score made or failed, and from the side of the team to play.
        fun net(outcome: PlayOutcome): Int {
            val score = scoreDeal(contract, outcome, rules)
            return score[toPlay] - score[toPlay.opponent]
        }
        return when {
            contract.isCapot -> {
                // A single trick lost and the capot is down, whatever comes next.
                val made = if (attackTricks < tricks.size) {
                    null
                } else {
                    tricksSolver.load(hands, current, tricksLeft)
                    tricksSolver.moveReaches(tricksLeft)
                }
                val cards = legalCards(hands[current.nextSeat.ordinal], current, contract.type, rules).toList()
                cards.associateWith { net(outcomes.of(ALL_POINTS, capot = made?.get(it) == true)) }
            }
            rules.scoringMode == ScoringMode.CONTRACT_POINTS -> {
                val belote = if (beloteTeam == attack) BELOTE else 0
                val need = contract.value - attackPoints - belote
                pointsSolver.load(hands, current, tricksLeft)
                val made = pointsSolver.moveReaches(need)
                // Only made or failed is scored: any outcome on the right side gives the score.
                val (whenMade, whenFailed) = net(outcomes.of(ALL_POINTS)) to net(outcomes.of(0))
                made.mapValues { (_, isMade) -> if (isMade) whenMade else whenFailed }
            }
            else -> {
                pointsSolver.load(hands, current, tricksLeft)
                val values = pointsSolver.moveValues()
                val capots = if (attackTricks == tricks.size && values.values.any { attackPoints + it == ALL_POINTS }) {
                    tricksSolver.load(hands, current, tricksLeft)
                    tricksSolver.moveReaches(tricksLeft)
                } else {
                    emptyMap()
                }
                values.mapValues { (card, value) ->
                    net(outcomes.of(attackPoints + value, capot = capots[card] == true))
                }
            }
        }
    }

    private inner class Outcomes(private val beloteTeam: Team?) {
        fun of(attackCardPoints: Int, capot: Boolean = false): PlayOutcome =
            outcome(attack, attackCardPoints, capot, beloteTeam)
    }
}

/** A finished deal's outcome, built from the [attack]'s card points (last trick included). */
internal fun outcome(attack: Team, attackCardPoints: Int, capot: Boolean, beloteTeam: Team?): PlayOutcome {
    val belote = { team: Team -> if (beloteTeam == team) BELOTE else 0 }
    return PlayOutcome(
        pointsMade = TeamPoints.of(
            attack,
            attackCardPoints + belote(attack),
            ALL_POINTS - attackCardPoints + belote(attack.opponent),
        ),
        beloteTeam = beloteTeam,
        capotTeam = if (capot) attack else null,
    )
}

/** Card points of a whole deal, the 10 for the last trick included. */
internal const val ALL_POINTS = 162
private const val BELOTE = 20
