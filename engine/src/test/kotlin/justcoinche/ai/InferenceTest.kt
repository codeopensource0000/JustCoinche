package justcoinche.ai

import justcoinche.engine.Deal
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.viewFor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InferenceTest {

    /** A deal bid and played by four ia2 players up to a random point of the card play, or null on a redeal. */
    private fun playedByIa2(random: Random): Round? {
        val players = Seat.entries.associateWith { RulesPlusPlayer() }
        var round = Round.start(Seat.entries.random(random), Deal.random(random), Rules())
        while (round.phase == Round.Phase.BIDDING) {
            val seat = checkNotNull(round.toAct)
            round = round.call(seat, players.getValue(seat).bid(round.viewFor(seat)))
        }
        if (round.phase != Round.Phase.PLAYING) return null
        repeat(random.nextInt(31)) {
            val seat = checkNotNull(round.toAct)
            round = round.play(players.getValue(seat).play(round.viewFor(seat)))
        }
        return round
    }

    private fun initialHands(round: Round) = Seat.entries.map { round.deal.hand(it) }

    @Test
    fun `the real deal always explains an auction bid by the convention`() {
        val random = Random(12)
        var otherDealsDoubted = 0
        var rounds = 0
        while (rounds < 300) {
            val round = playedByIa2(random) ?: continue
            rounds++
            val view = round.viewFor(checkNotNull(round.toAct))
            val reading = AuctionReading(view)
            assertEquals(1.0, reading.weight(initialHands(round)), "round $rounds")
            // A deal drawn without the auction usually contradicts it somewhere.
            val drawn = checkNotNull(HiddenCards(view).sample(random))
            if (reading.weight(HiddenCards(view).initialHands(drawn)) < 1.0) otherDealsDoubted++
        }
        assertTrue(otherDealsDoubted > 150, "only $otherDealsDoubted drawn deals contradicted the auction")
    }

    @Test
    fun `the real deal always explains the discards of a card play by the convention`() {
        val random = Random(13)
        var doubted = 0
        var rounds = 0
        while (rounds < 600) {
            val round = playedByIa2(random) ?: continue
            rounds++
            val view = round.viewFor(checkNotNull(round.toAct))
            val reading = CallReading(view)
            assertEquals(1.0, reading.weight(initialHands(round)), "round $rounds")
            val drawn = checkNotNull(HiddenCards(view).sample(random))
            if (reading.weight(HiddenCards(view).initialHands(drawn)) < 1.0) doubted++
        }
        assertTrue(doubted > 30, "only $doubted drawn deals contradicted a discard")
        println("Drawn deals contradicting a discard: $doubted of $rounds")
    }
}
