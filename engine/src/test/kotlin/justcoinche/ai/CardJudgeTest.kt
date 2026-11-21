package justcoinche.ai

import justcoinche.engine.CardPlay
import justcoinche.engine.CoincheLevel
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Rules
import justcoinche.engine.ScoringMode
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.scoreDeal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CardJudgeTest {

    /** The attack's net score at the end of a finished play, as the engine scores it. */
    private fun attackNet(play: CardPlay, contract: Contract, rules: Rules): Int {
        val score = scoreDeal(contract, play.outcome(), rules)
        val attack = contract.declarer.team
        return score[attack] - score[attack.opponent]
    }

    /** Exhaustive minimax of the real score through the engine: the attack maximises its net score. */
    private fun bruteForce(play: CardPlay, contract: Contract, rules: Rules): Int {
        if (play.isOver) return attackNet(play, contract, rules)
        val values = play.playableCards().toList().map { bruteForce(play.play(it), contract, rules) }
        return if (play.toPlay.team == contract.declarer.team) values.max() else values.min()
    }

    private fun randomContract(random: Random, type: ContractType): Contract {
        val declarer = Seat.entries.random(random)
        val coinche = CoincheLevel.entries.random(random)
        return when (random.nextInt(8)) {
            0 -> Contract(declarer, type, Contract.CAPOT_VALUE, isCapot = true, coinche = coinche)
            1 -> Contract(declarer, type, Contract.CAPOT_BELOTE_VALUE, true, coinche, capotBelote = true)
            else -> Contract(declarer, type, 80 + 10 * random.nextInt(9), isCapot = false, coinche = coinche)
        }
    }

    @Test
    fun `on random endings every card is scored as the engine scores perfect play`() {
        val random = Random(77)
        repeat(1500) { i ->
            val rules = Rules(
                mustUnderTrump = random.nextBoolean(),
                scoringMode = ScoringMode.entries.random(random),
                beloteKeptOnFailure = random.nextBoolean(),
            )
            val type = when (random.nextInt(6)) {
                in 0..3 -> ContractType.SuitTrump(Suit.entries.random(random))
                4 -> ContractType.NoTrump
                else -> ContractType.AllTrump
            }
            val deal = Deal.random(random)
            val contract = randomContract(random, type)
            var play = CardPlay.start(deal, type, rules, Seat.entries.random(random))
            // One to three tricks left, possibly in the middle of a trick.
            val cardsToPlay = (8 - (1 + i % 3)) * 4 + random.nextInt(4)
            repeat(cardsToPlay) { play = play.play(play.playableCards().toList().random(random)) }

            val judged = CardJudge(contract, rules).judge(
                Seat.entries.map { play.hand(it) },
                play.tricks,
                play.currentTrick,
                CardPlay.beloteTeam(deal, type),
            )
            val sign = if (play.toPlay.team == contract.declarer.team) 1 else -1
            play.playableCards().toList().forEach { card ->
                val expected = sign * bruteForce(play.play(card), contract, rules)
                assertEquals(expected, judged[card], "position $i, card $card, $contract, ${rules.scoringMode}")
            }
            assertEquals(play.playableCards().toList().toSet(), judged.keys, "position $i")
        }
    }
}
