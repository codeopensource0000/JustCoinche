package justcoinche.engine

import justcoinche.engine.CoincheLevel.COINCHED
import justcoinche.engine.CoincheLevel.NONE
import justcoinche.engine.CoincheLevel.SURCOINCHED
import justcoinche.engine.Team.EAST_WEST
import justcoinche.engine.Team.NORTH_SOUTH
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The worked examples of docs/regles.md §8. North declares every contract,
 * so North-South attack and East-West defend. Points made are raw: card
 * points, 10 for the last trick, and 20 for a belote held.
 */
class ScoringTest {

    private val hearts = ContractType.SuitTrump(Suit.HEARTS)
    private val contractPoints = Rules(scoringMode = ScoringMode.CONTRACT_POINTS)
    private val contractAndPointsMade = Rules(scoringMode = ScoringMode.CONTRACT_AND_POINTS_MADE)

    private fun bid(value: Int, coinche: CoincheLevel = NONE) =
        Contract(Seat.NORTH, hearts, value, isCapot = false, coinche = coinche)

    private fun capot(coinche: CoincheLevel = NONE) =
        Contract(Seat.NORTH, hearts, Contract.CAPOT_VALUE, isCapot = true, coinche = coinche)

    /** North-South win every trick and hold the belote: 162 + 20 points. */
    private val everyTrickWithBelote =
        PlayOutcome(TeamPoints(182, 0), beloteTeam = NORTH_SOUTH, capotTeam = NORTH_SOUTH)

    private fun capotBelote(coinche: CoincheLevel = NONE) =
        Contract(Seat.NORTH, hearts, Contract.CAPOT_BELOTE_VALUE, isCapot = true, coinche = coinche, capotBelote = true)

    private fun outcome(attack: Int, defence: Int, belote: Team? = null, capot: Team? = null) =
        PlayOutcome(TeamPoints(attack, defence), belote, capot)

    private fun score(contract: Contract, outcome: PlayOutcome, rules: Rules, round: Boolean = true) =
        scoreDeal(contract, outcome, rules, round)

    // ---- "Contrat et points faits" ----

    @Test
    fun `contract and points made - a made contract scores contract plus points made, the defence its own points`() {
        // 80 hearts, 100 points made (102 raw): 80 + 100 = 180; the defence 60.
        assertEquals(TeamPoints(180, 60), score(bid(80), outcome(102, 60), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - points made are rounded to the nearest ten, a 5 rounding up`() {
        // The 6-and-6 case: the deal is worth 170 in all.
        assertEquals(TeamPoints(80 + 90, 80), score(bid(80), outcome(86, 76), contractAndPointsMade))
        assertEquals(TeamPoints(80 + 90, 80), score(bid(80), outcome(85, 77), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - the final deal of a game is not rounded`() {
        // At 960 in a game to 1000: a 100 made with 112 points scores 212 and ends at 1172.
        assertEquals(TeamPoints(212, 50), score(bid(100), outcome(112, 50), contractAndPointsMade, round = false))
    }

    @Test
    fun `contract and points made - a failed contract gives the defence 160, the contract and the declarer's belote`() {
        // 100 with the belote, failed: the defence scores 100 + 160 + 20 = 280.
        assertEquals(TeamPoints(0, 280), score(bid(100), outcome(95, 87, belote = NORTH_SOUTH), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - when the table says so, the failed declarer keeps its belote`() {
        val rules = contractAndPointsMade.copy(beloteKeptOnFailure = true)
        assertEquals(TeamPoints(20, 260), score(bid(100), outcome(95, 87, belote = NORTH_SOUTH), rules))
    }

    @Test
    fun `contract and points made - the defence's belote always scores`() {
        assertEquals(TeamPoints(0, 280), score(bid(100), outcome(70, 112, belote = EAST_WEST), contractAndPointsMade))
        assertEquals(TeamPoints(180, 80), score(bid(80), outcome(100, 82, belote = EAST_WEST), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - success is decided on raw points, never on rounded ones`() {
        // 99 would round to 100, but a 100 needs 100 raw points.
        assertEquals(TeamPoints(0, 260), score(bid(100), outcome(99, 63), contractAndPointsMade))
        // 80 with the belote is made with 60 card points.
        assertEquals(
            TeamPoints(160, 100),
            score(bid(80), outcome(80, 102, belote = NORTH_SOUTH), contractAndPointsMade),
        )
    }

    @Test
    fun `contract and points made - all eight tricks without a capot bid score the contract plus 250`() {
        assertEquals(TeamPoints(370, 0), score(bid(120), outcome(162, 0, capot = NORTH_SOUTH), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - an announced capot scores 500, or gives 410 when it fails`() {
        assertEquals(TeamPoints(500, 0), score(capot(), outcome(162, 0, capot = NORTH_SOUTH), contractAndPointsMade))
        assertEquals(TeamPoints(0, 410), score(capot(), outcome(150, 12), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - a coinche doubles the contract only, and a coinched defence scores nothing`() {
        // 100 coinched, made with 120: 2 x 100 + 120 = 320. Failed: 2 x 100 + 160 = 360.
        assertEquals(TeamPoints(320, 0), score(bid(100, COINCHED), outcome(120, 42), contractAndPointsMade))
        assertEquals(TeamPoints(0, 360), score(bid(100, COINCHED), outcome(80, 82), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - a surcoinche triples the contract`() {
        assertEquals(TeamPoints(420, 0), score(bid(100, SURCOINCHED), outcome(120, 42), contractAndPointsMade))
    }

    @Test
    fun `contract and points made - a capot belote scores 250 + 250 + 20, or gives 270 + 160 when it fails`() {
        // Made: every trick and the belote, held by either hand of the team.
        assertEquals(TeamPoints(520, 0), score(capotBelote(), everyTrickWithBelote, contractAndPointsMade))
        // Failed by losing a trick: the belote is part of the 270, not given again.
        val lostATrick = outcome(170, 12, belote = NORTH_SOUTH)
        assertEquals(TeamPoints(0, 430), score(capotBelote(), lostATrick, contractAndPointsMade))
    }

    @Test
    fun `a capot belote without the belote fails, even with every trick`() {
        val everyTrickNoBelote = outcome(162, 0, capot = NORTH_SOUTH)
        assertEquals(TeamPoints(0, 430), score(capotBelote(), everyTrickNoBelote, contractAndPointsMade))
        assertEquals(TeamPoints(0, 270), score(capotBelote(), everyTrickNoBelote, contractPoints))
    }

    @Test
    fun `a coinched capot belote doubles its 250 but never the belote`() {
        // 2 x 250 + 20 + 250 = 770, not 2 x 270 + 250 = 790.
        assertEquals(
            TeamPoints(770, 0),
            score(capotBelote(COINCHED), everyTrickWithBelote, contractAndPointsMade),
        )
    }

    // ---- "Points au contrat" ----

    @Test
    fun `contract points - a made contract scores its value only, even with every trick`() {
        assertEquals(TeamPoints(80, 0), score(bid(80), outcome(162, 0, capot = NORTH_SOUTH), contractPoints))
    }

    @Test
    fun `contract points - a plain failure gives the defence a fixed 160`() {
        assertEquals(TeamPoints(0, 160), score(bid(130), outcome(120, 42), contractPoints))
    }

    @Test
    fun `contract points - a coinched contract is worth twice its value either way`() {
        // 80 coinched and failed costs 160, the same as a plain failure.
        assertEquals(TeamPoints(0, 160), score(bid(80, COINCHED), outcome(70, 92), contractPoints))
        assertEquals(TeamPoints(0, 180), score(bid(90, COINCHED), outcome(70, 92), contractPoints))
        assertEquals(TeamPoints(180, 0), score(bid(90, COINCHED), outcome(100, 62), contractPoints))
    }

    @Test
    fun `contract points - an announced capot is worth 250 either way, 500 coinched`() {
        assertEquals(TeamPoints(250, 0), score(capot(), outcome(162, 0, capot = NORTH_SOUTH), contractPoints))
        assertEquals(TeamPoints(0, 250), score(capot(), outcome(150, 12), contractPoints))
        assertEquals(TeamPoints(0, 500), score(capot(COINCHED), outcome(150, 12), contractPoints))
    }

    @Test
    fun `contract points - a capot belote is worth 270 either way`() {
        assertEquals(TeamPoints(270, 0), score(capotBelote(), everyTrickWithBelote, contractPoints))
        assertEquals(TeamPoints(0, 270), score(capotBelote(), outcome(170, 12, belote = NORTH_SOUTH), contractPoints))
    }

    @Test
    fun `contract points - the declarer's belote never scores, the defence's always does`() {
        assertEquals(TeamPoints(80, 0), score(bid(80), outcome(100, 82, belote = NORTH_SOUTH), contractPoints))
        assertEquals(TeamPoints(80, 20), score(bid(80), outcome(100, 82, belote = EAST_WEST), contractPoints))
    }

    @Test
    fun `rounding goes to the nearest ten, a 5 rounding up`() {
        assertEquals(listOf(0, 40, 50, 50, 160), listOf(0, 44, 45, 48, 162).map(::roundToTen))
    }
}
