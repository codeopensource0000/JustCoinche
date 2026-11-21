package justcoinche.ai

import justcoinche.engine.CardPlay
import justcoinche.engine.CardSet
import justcoinche.engine.CoincheLevel
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.PlayerView
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HiddenCardsTest {

    /** A deal played at random for a while, with the view of the player to act. */
    private class Position(val deal: Deal, val start: CardPlay, val play: CardPlay, val view: PlayerView)

    private fun randomPosition(random: Random, moves: Int = random.nextInt(31)): Position {
        val rules = Rules(mustUnderTrump = random.nextBoolean())
        val type = when (random.nextInt(6)) {
            4 -> ContractType.NoTrump
            5 -> ContractType.AllTrump
            else -> ContractType.SuitTrump(Suit.entries.random(random))
        }
        val deal = Deal.random(random)
        val start = CardPlay.start(deal, type, rules, Seat.entries.random(random))
        var play = start
        repeat(moves) { play = play.play(play.playableCards().toList().random(random)) }
        val seat = play.toPlay
        val view = PlayerView(
            seat = seat, rules = rules, dealer = Seat.SOUTH, hand = play.hand(seat), calls = emptyList(),
            contract = Contract(Seat.entries.random(random), type, 80, isCapot = false, coinche = CoincheLevel.NONE),
            tricks = play.tricks, currentTrick = play.currentTrick, legalBids = emptyList(),
            playableCards = play.playableCards(),
        )
        return Position(deal, start, play, view)
    }

    @Test
    fun `every drawn deal could have produced the play seen so far`() {
        val random = Random(5)
        repeat(400) { i ->
            val position = randomPosition(random)
            val hidden = HiddenCards(position.view)
            val me = position.view.seat
            // The real hands are never ruled out.
            Seat.entries.forEach { seat ->
                val excluded = hidden.excluded[seat.ordinal]
                assertTrue((position.play.hand(seat) intersect excluded).isEmpty(), "position $i, $seat")
            }
            repeat(20) {
                val hands = assertNotNull(hidden.sample(random), "position $i")
                assertEquals(position.play.hand(me), hands[me.ordinal])
                Seat.entries.forEach { seat ->
                    assertEquals(position.play.hand(seat).size, hands[seat.ordinal].size, "position $i, $seat")
                }
                // Replaying every card from the drawn deal: the engine accepts each one as legal.
                val drawn = Deal(Seat.entries.map { hands[it.ordinal] union hidden.played[it.ordinal] })
                var replay = CardPlay.start(drawn, position.start.contract, position.start.rules, position.start.toPlay)
                (position.play.tricks + position.play.currentTrick).flatMap { it.cards }.forEach { card ->
                    replay = replay.play(card)
                }
                assertEquals(position.play.playableCards(), replay.playableCards(), "position $i")
            }
        }
    }

    @Test
    fun `a player who did not follow a suit is never dealt one`() {
        // Whoever played another suit than the one led has none of it left in any drawn deal.
        val random = Random(8)
        var checked = 0
        repeat(300) {
            val position = randomPosition(random)
            val tricks = position.play.tricks + position.play.currentTrick
            tricks.forEach { trick ->
                trick.cards.forEachIndexed { index, card ->
                    val seat = Seat.entries[(trick.leader.ordinal + index) % 4]
                    val led = trick.cards.first().suit
                    if (index > 0 && card.suit != led && seat != position.view.seat) {
                        val hidden = HiddenCards(position.view)
                        val hands = checkNotNull(hidden.sample(random))
                        assertTrue(hands[seat.ordinal].ofSuit(led).isEmpty())
                        checked++
                    }
                }
            }
        }
        assertTrue(checked > 100, "only $checked discards checked")
    }

    @Test
    fun `without constraints each hidden card goes to each other player about a third of the time`() {
        val view = randomPosition(Random(1), moves = 0).view
        val hidden = HiddenCards(view)
        val random = Random(3)
        val counts = IntArray(4)
        val card = hidden.unseen.toList().first()
        repeat(3000) {
            val hands = checkNotNull(hidden.sample(random))
            counts[Seat.entries.first { card in hands[it.ordinal] }.ordinal]++
        }
        Seat.entries.filter { it != view.seat }.forEach {
            assertTrue(counts[it.ordinal] in 900..1100, counts.toList().toString())
        }
        assertEquals(0, counts[view.seat.ordinal])
        assertEquals(CardSet.EMPTY, hidden.played.fold(CardSet.EMPTY) { a, b -> a union b })
    }
}
