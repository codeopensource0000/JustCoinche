package justcoinche.ai

import justcoinche.engine.Bid
import justcoinche.engine.Seat
import justcoinche.engine.parseBid
import kotlin.test.Test
import kotlin.test.assertEquals

/** South speaks; WEST deals so that South opens, EAST deals so that North opens before South. */
class PetitJeuTest {

    private fun bid(cards: String, dealer: Seat, vararg calls: String) = PetitJeu.bid(bidView(cards, dealer, *calls))

    @Test
    fun `Jack and Nine open at 100, whatever the Aces`() {
        assertEquals(parseBid("100C"), bid("VC 9C 7P 8P 7K 8K 7T AT", Seat.WEST))
    }

    @Test
    fun `a Jack or a Nine with three trumps opens at 80 with something beside, at 90 with four trumps`() {
        assertEquals(parseBid("80C"), bid("VC 8C 7C AP 8P 7K 8K 9T", Seat.WEST))
        assertEquals(parseBid("90C"), bid("9C 10C 8C 7C 8P 7K 8K 9T", Seat.WEST))
        // Three small trumps with the Nine and nothing beside: pass.
        assertEquals(Bid.Pass, bid("9C 8C 7C 7P 8P 7K 8K 9T", Seat.WEST))
    }

    @Test
    fun `a free answer needs the complement, never Aces alone`() {
        // North opened 80 hearts (Jack or Nine). South holds the Nine and two Aces: 80 + 10 + 20.
        assertEquals(parseBid("110C"), bid("9C 7C AP AT 8K 7K 8T 7T", Seat.EAST, "N:80C", "O:-"))
        // Three Aces but neither Jack nor Nine: no answer.
        assertEquals(Bid.Pass, bid("8C AP AT AK 8K 7K 8T 7T", Seat.EAST, "N:80C", "O:-"))
    }

    @Test
    fun `over an opening at 100, each Ace adds 10`() {
        assertEquals(parseBid("120C"), bid("8C AP AT 8K 7K 8T 7T 7P", Seat.EAST, "N:100C", "O:-"))
    }

    @Test
    fun `a forced answer counts everything that helps, belote included`() {
        // North 80 hearts, West 90 spades. South: King-Queen of hearts (belote 20) and an Ace (10).
        assertEquals(parseBid("110C"), bid("RC DC AT 8K 7K 8T 7T 7P", Seat.EAST, "N:80C", "O:90P"))
    }

    @Test
    fun `nobody announces a third time, which ends every auction`() {
        // South opened at 100, then competed at 120 over East: a third announce is out of the question.
        val calls = arrayOf("S:100C", "E:110P", "N:-", "O:-", "S:120C", "E:130P", "N:-", "O:-")
        assertEquals(Bid.Pass, bid("VC 9C AC AP AT AK 8K 7K", Seat.WEST, *calls))
    }

    @Test
    fun `a coinche needs a high contract and sure defensive tricks`() {
        // West bid 120 hearts; South holds two outside Aces and the Nine of hearts.
        assertEquals(Bid.Coinche, bid("9C 7C AP AT 8K 7K 8T 7T", Seat.EAST, "N:-", "O:120C"))
        // Same hand against a mere 80: no coinche.
        assertEquals(Bid.Pass, bid("9C 7C AP AT 8K 7K 8T 7T", Seat.EAST, "N:-", "O:80C"))
    }
}
