package justcoinche.ai

import justcoinche.arena.playOut
import justcoinche.engine.Deal
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.parseCard
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hearts are trumps in these views. */
class RulesPlusPlayerTest {

    private val player = RulesPlusPlayer()

    @Test
    fun `with partner master and the led suit missing, it calls the suit of its Ace`() {
        // East leads spades, North wins with the Ace, West follows; South has no spade and holds the Ace of clubs.
        val view = playView("AT 8T 10K 7K VC 7C DK RK", declarer = Seat.NORTH, leader = Seat.EAST, played = "7P AP 8P")
        assertEquals(parseCard("8T"), player.play(view))
    }

    @Test
    fun `leading in defence, it plays the suit partner called`() {
        // In the first trick North (partner) discarded a club while South was master: he holds the Ace of clubs.
        val first = trick(Seat.SOUTH, "AP 7P 8T 8P")
        val view = playView(
            "9T 10T RK 7K 8C RP DP 9P",
            declarer = Seat.EAST,
            leader = Seat.SOUTH,
            tricks = listOf(first),
        )
        // Without the call it would lead its cheapest card, the 7 of diamonds.
        assertEquals(parseCard("9T"), player.play(view))
    }

    @Test
    fun `when partner declared, it leads a small trump for him to draw them`() {
        val view = playView("8C 7C 10K 7K 9T 7T DP 8P", declarer = Seat.NORTH, leader = Seat.SOUTH)
        assertEquals(parseCard("7C"), player.play(view))
    }

    @Test
    fun `four Règles+ players finish any deal legally, and their auctions always end`() {
        val random = Random(41)
        repeat(300) {
            val players = Seat.entries.associateWith { RulesPlusPlayer() }
            val round = playOut(Round.start(Seat.entries.random(random), Deal.random(random), Rules()), players)
            assertTrue(round.phase == Round.Phase.OVER || round.phase == Round.Phase.REDEAL)
        }
    }
}
