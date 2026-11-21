package justcoinche.ai

import justcoinche.engine.CardPlay
import justcoinche.engine.CoincheLevel
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.PlayerView
import justcoinche.engine.Rules
import justcoinche.engine.ScoringMode
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PimcPlayerTest {

    /** The view of the player to act after a random start of play, under a random contract and rules. */
    private fun randomView(random: Random): PlayerView {
        val rules = Rules(mustUnderTrump = random.nextBoolean(), scoringMode = ScoringMode.entries.random(random))
        val type = when (random.nextInt(6)) {
            4 -> ContractType.NoTrump
            5 -> ContractType.AllTrump
            else -> ContractType.SuitTrump(Suit.entries.random(random))
        }
        val declarer = Seat.entries.random(random)
        val coinche = CoincheLevel.entries.random(random)
        val contract = when (random.nextInt(6)) {
            0 -> Contract(declarer, type, Contract.CAPOT_VALUE, isCapot = true, coinche = coinche)
            1 -> Contract(declarer, type, Contract.CAPOT_BELOTE_VALUE, true, coinche, capotBelote = true)
            else -> Contract(declarer, type, 80 + 10 * random.nextInt(9), isCapot = false, coinche = coinche)
        }
        var play = CardPlay.start(Deal.random(random), type, rules, Seat.entries.random(random))
        repeat(random.nextInt(28)) { play = play.play(play.playableCards().toList().random(random)) }
        val seat = play.toPlay
        return PlayerView(
            seat = seat, rules = rules, dealer = Seat.SOUTH, hand = play.hand(seat), calls = emptyList(),
            contract = contract, tricks = play.tricks, currentTrick = play.currentTrick, legalBids = emptyList(),
            playableCards = play.playableCards(),
        )
    }

    @Test
    fun `it always plays a legal card, whatever the contract and the scoring`() {
        val random = Random(4)
        repeat(80) { i ->
            val view = randomView(random)
            val card = PimcPlayer(Effort.QUICK, seed = i.toLong(), timeLimited = false).play(view)
            assertTrue(card in view.playableCards, "position $i: $card")
        }
    }

    @Test
    fun `the same position and seed give the same card`() {
        val random = Random(6)
        repeat(30) { i ->
            val view = randomView(random)
            val (first, second) = PimcPlayer(Effort.QUICK, 9, timeLimited = false) to
                PimcPlayer(Effort.QUICK, 9, timeLimited = false)
            assertEquals(first.play(view), second.play(view), "position $i")
        }
    }
}
