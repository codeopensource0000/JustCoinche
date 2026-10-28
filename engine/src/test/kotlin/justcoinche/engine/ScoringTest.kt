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
    private val modeA = Rules(scoringMode = ScoringMode.CONTRACT_ONLY)
    private val modeB = Rules(scoringMode = ScoringMode.POINTS_MADE_PLUS_CONTRACT)

    private fun bid(value: Int, coinche: CoincheLevel = NONE) =
        Contract(Seat.NORTH, hearts, value, isCapot = false, coinche = coinche)

    private fun capot(coinche: CoincheLevel = NONE) =
        Contract(Seat.NORTH, hearts, Contract.CAPOT_VALUE, isCapot = true, coinche = coinche)

    private fun outcome(attack: Int, defence: Int, belote: Team? = null, capot: Team? = null) =
        PlayOutcome(TeamPoints(attack, defence), belote, capot)

    private fun score(contract: Contract, outcome: PlayOutcome, rules: Rules, round: Boolean = true) =
        scoreDeal(contract, outcome, rules, round)

    // ---- Mode B: points made + contract ----

    @Test
    fun `B - a made contract scores contract plus points made, the defence its own points`() {
        // 80 hearts, 100 points made (102 raw): 80 + 100 = 180; the defence 60.
        assertEquals(TeamPoints(180, 60), score(bid(80), outcome(102, 60), modeB))
    }

    @Test
    fun `B - points made are rounded to the nearest ten, a 5 rounding up`() {
        assertEquals(TeamPoints(80 + 90, 80), score(bid(80), outcome(86, 76), modeB)) // the 6-and-6 case: 170
        assertEquals(TeamPoints(80 + 90, 80), score(bid(80), outcome(85, 77), modeB))
    }

    @Test
    fun `B - the final deal of a game is not rounded`() {
        // At 960 in a game to 1000: a 100 made with 112 points scores 212 and ends at 1172.
        assertEquals(TeamPoints(212, 50), score(bid(100), outcome(112, 50), modeB, round = false))
    }

    @Test
    fun `B - a failed contract gives the defence 160, the contract and the declarer's belote`() {
        // 100 with the belote, failed: the defence scores 100 + 160 + 20 = 280.
        assertEquals(TeamPoints(0, 280), score(bid(100), outcome(95, 87, belote = NORTH_SOUTH), modeB))
    }

    @Test
    fun `B - when the table says so, the failed declarer keeps its belote`() {
        val rules = modeB.copy(beloteKeptOnFailure = true)
        assertEquals(TeamPoints(20, 260), score(bid(100), outcome(95, 87, belote = NORTH_SOUTH), rules))
    }

    @Test
    fun `B - the defence's belote always scores`() {
        assertEquals(TeamPoints(0, 280), score(bid(100), outcome(70, 112, belote = EAST_WEST), modeB))
        assertEquals(TeamPoints(180, 80), score(bid(80), outcome(100, 82, belote = EAST_WEST), modeB))
    }

    @Test
    fun `B - success is decided on raw points, never on rounded ones`() {
        // 99 would round to 100, but a 100 needs 100 raw points.
        assertEquals(TeamPoints(0, 260), score(bid(100), outcome(99, 63), modeB))
        // 80 with the belote is made with 60 card points.
        assertEquals(TeamPoints(160, 100), score(bid(80), outcome(80, 102, belote = NORTH_SOUTH), modeB))
    }

    @Test
    fun `B - all eight tricks without a capot bid score the contract plus 250`() {
        assertEquals(TeamPoints(370, 0), score(bid(120), outcome(162, 0, capot = NORTH_SOUTH), modeB))
    }

    @Test
    fun `B - an announced capot scores 500, or gives 410 when it fails`() {
        assertEquals(TeamPoints(500, 0), score(capot(), outcome(162, 0, capot = NORTH_SOUTH), modeB))
        assertEquals(TeamPoints(0, 410), score(capot(), outcome(150, 12), modeB))
    }

    @Test
    fun `B - a coinche doubles the contract only, and a coinched defence scores nothing`() {
        // 100 coinched, made with 120: 2 x 100 + 120 = 320. Failed: 2 x 100 + 160 = 360.
        assertEquals(TeamPoints(320, 0), score(bid(100, COINCHED), outcome(120, 42), modeB))
        assertEquals(TeamPoints(0, 360), score(bid(100, COINCHED), outcome(80, 82), modeB))
    }

    @Test
    fun `B - a surcoinche triples the contract`() {
        assertEquals(TeamPoints(420, 0), score(bid(100, SURCOINCHED), outcome(120, 42), modeB))
    }

    // ---- Mode A: contract only ----

    @Test
    fun `A - a made contract scores its value only, even with every trick`() {
        assertEquals(TeamPoints(80, 0), score(bid(80), outcome(162, 0, capot = NORTH_SOUTH), modeA))
    }

    @Test
    fun `A - a plain failure gives the defence a fixed 160`() {
        assertEquals(TeamPoints(0, 160), score(bid(130), outcome(120, 42), modeA))
    }

    @Test
    fun `A - a coinched contract is worth twice its value either way`() {
        assertEquals(TeamPoints(0, 160), score(bid(80, COINCHED), outcome(70, 92), modeA)) // same as a plain failure
        assertEquals(TeamPoints(0, 180), score(bid(90, COINCHED), outcome(70, 92), modeA))
        assertEquals(TeamPoints(180, 0), score(bid(90, COINCHED), outcome(100, 62), modeA))
    }

    @Test
    fun `A - an announced capot is worth 250 either way, 500 coinched`() {
        assertEquals(TeamPoints(250, 0), score(capot(), outcome(162, 0, capot = NORTH_SOUTH), modeA))
        assertEquals(TeamPoints(0, 250), score(capot(), outcome(150, 12), modeA))
        assertEquals(TeamPoints(0, 500), score(capot(COINCHED), outcome(150, 12), modeA))
    }

    @Test
    fun `A - the declarer's belote never scores, the defence's always does`() {
        assertEquals(TeamPoints(80, 0), score(bid(80), outcome(100, 82, belote = NORTH_SOUTH), modeA))
        assertEquals(TeamPoints(80, 20), score(bid(80), outcome(100, 82, belote = EAST_WEST), modeA))
    }

    @Test
    fun `rounding goes to the nearest ten, a 5 rounding up`() {
        assertEquals(listOf(0, 40, 50, 50, 160), listOf(0, 44, 45, 48, 162).map(::roundToTen))
    }
}
