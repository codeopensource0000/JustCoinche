package justcoinche.arena

import justcoinche.engine.Auction
import justcoinche.engine.Bid
import justcoinche.engine.CardSet
import justcoinche.engine.CoincheLevel
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.PlayerView
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Trick
import justcoinche.engine.legalCards
import justcoinche.engine.parseBid
import justcoinche.engine.parseCard
import justcoinche.engine.parseSeat
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cards and bids are written as in the game record: "VC" is the Jack of hearts
 * (P spades, C hearts, K diamonds, T clubs), "110C" a bid of 110 in hearts.
 */
class RulePlayerTest {

    private val player = RulePlayer()
    private val rules = Rules()
    private val hearts = ContractType.SuitTrump(Suit.HEARTS)

    private fun hand(text: String): CardSet = CardSet.of(text.split(" ").map { parseCard(it) })

    /** South's view of an auction where the earlier calls, "N:80C" style, were made after [dealer] dealt. */
    private fun bidView(cards: String, dealer: Seat, vararg calls: String): PlayerView {
        var auction = Auction.start(dealer, rules)
        calls.forEach { call ->
            val (seat, bid) = call.split(":")
            auction = auction.call(parseSeat(seat), parseBid(bid))
        }
        assertEquals(Seat.SOUTH, auction.toSpeak)
        return PlayerView(
            seat = Seat.SOUTH, rules = rules, dealer = dealer, hand = hand(cards), calls = auction.calls,
            contract = null, tricks = emptyList(), currentTrick = null,
            legalBids = auction.legalBids(Seat.SOUTH), playableCards = CardSet.EMPTY,
        )
    }

    /** South's view at the first trick of a hearts contract, the trick led by [leader] with [played]. */
    private fun playView(
        cards: String,
        declarer: Seat,
        leader: Seat,
        played: String = "",
        tricks: List<Trick> = emptyList(),
    ): PlayerView {
        val trick = Trick(leader, if (played.isEmpty()) emptyList() else played.split(" ").map { parseCard(it) })
        assertEquals(Seat.SOUTH, trick.nextSeat)
        val hand = hand(cards)
        return PlayerView(
            seat = Seat.SOUTH, rules = rules, dealer = Seat.WEST, hand = hand, calls = emptyList(),
            contract = Contract(declarer, hearts, value = 80, isCapot = false, coinche = CoincheLevel.NONE),
            tricks = tricks, currentTrick = trick, legalBids = emptyList(),
            playableCards = legalCards(hand, trick, hearts, rules),
        )
    }

    @Test
    fun `it opens at 80, plus 10 per outside Ace and 10 for the Jack and Nine of trumps together`() {
        val view = bidView("VC 9C 7C AP AT 8K 7K 9P", dealer = Seat.WEST)
        assertEquals(parseBid("110C"), player.bid(view))
    }

    @Test
    fun `five trumps add 10, and the suit worth most is chosen`() {
        // Spades: 5 trumps with the Nine = 90. Hearts: 3 trumps with the Jack = 80.
        val view = bidView("9P 10P RP 8P 7P VC 8C 7C", dealer = Seat.WEST)
        assertEquals(parseBid("90P"), player.bid(view))
    }

    @Test
    fun `without 3 trumps holding the Jack or the Nine, it passes`() {
        assertEquals(Bid.Pass, player.bid(bidView("VC 9C AP AT AK 10P 10T RP", dealer = Seat.WEST)))
        assertEquals(Bid.Pass, player.bid(bidView("AC 10C RC DC AP AT AK 7P", dealer = Seat.WEST)))
    }

    @Test
    fun `it raises its partner by 10 per outside Ace and per Jack or Nine of trumps`() {
        // North opened 80 hearts; South holds two outside Aces and the Nine of hearts.
        val view = bidView("9C 7K AP AT 8K 7T 8T 9T", dealer = Seat.EAST, "N:80C", "O:-")
        assertEquals(parseBid("110C"), player.bid(view))
    }

    @Test
    fun `it announces once only, and passes a bid it cannot top`() {
        val strong = "VC 9C 7C AP AT 8K 7K 9P" // worth 110 in hearts
        assertEquals(Bid.Pass, player.bid(bidView(strong, Seat.WEST, "S:80C", "E:90P", "N:-", "O:-")))
        assertEquals(Bid.Pass, player.bid(bidView(strong, Seat.EAST, "N:-", "O:120P")))
    }

    @Test
    fun `the attack draws trumps with the master trump, the defence leads its Aces instead`() {
        val cards = "VC 7C AP 7P 8K 7K 8T 7T"
        assertEquals(parseCard("VC"), player.play(playView(cards, declarer = Seat.SOUTH, leader = Seat.SOUTH)))
        assertEquals(parseCard("AP"), player.play(playView(cards, declarer = Seat.EAST, leader = Seat.SOUTH)))
    }

    @Test
    fun `once the Ace has fallen, the Ten is master and the defence leads it`() {
        // West won the Ace of spades, then South won a club and leads the third trick.
        val tricks = listOf(
            Trick(Seat.WEST, listOf("AP", "8P", "9P", "DP").map { parseCard(it) }),
            Trick(Seat.WEST, listOf("7T", "AT", "8T", "9T").map { parseCard(it) }),
        )
        val view = playView("10P 7P 8K 7K 10C 8C", declarer = Seat.EAST, leader = Seat.SOUTH, tricks = tricks)
        assertEquals(parseCard("10P"), player.play(view))
    }

    @Test
    fun `with no master card it leads its cheapest card, never a trump`() {
        val view = playView("10C 8C RP 10K 9T 10T DT RT", declarer = Seat.EAST, leader = Seat.SOUTH)
        assertEquals(parseCard("9T"), player.play(view))
    }

    @Test
    fun `last to play behind a partner who is master, it gives points`() {
        // East leads, North wins with the Ace of spades; South has no spade and is free to discard.
        val view = playView("10K 7K 8T 7C VC 9T DT RT", declarer = Seat.NORTH, leader = Seat.EAST, played = "7P AP 8P")
        assertEquals(parseCard("10K"), player.play(view))
    }

    @Test
    fun `when its partner may still be overtaken, it plays its cheapest card`() {
        // North leads the King of spades, West follows; the Ace and the Ten are unseen and East plays after South.
        val view = playView("DP 7P 10K 8T 7C VC 9T RT", declarer = Seat.NORTH, leader = Seat.NORTH, played = "RP 8P")
        assertEquals(parseCard("7P"), player.play(view))
    }

    @Test
    fun `last to play behind an opponent, it wins with the weakest card that does it`() {
        val view = playView("AP 10P 7C 8K 7K 8T 7T 9T", declarer = Seat.EAST, leader = Seat.EAST, played = "9P 7P RP")
        assertEquals(parseCard("10P"), player.play(view))
    }

    @Test
    fun `void in the suit led, it trumps as low as possible`() {
        val view = playView("7C VC 8K 7K 8T 7T 9T 10T", declarer = Seat.EAST, leader = Seat.WEST, played = "AP")
        assertEquals(parseCard("7C"), player.play(view))
    }

    @Test
    fun `unable to win the trick, it plays its cheapest card`() {
        // East leads the Ace of diamonds; South, last, must follow and keeps the Ten.
        val view = playView("10K 7K 8T 7C VC 9T DT RT", declarer = Seat.EAST, leader = Seat.EAST, played = "AK 8K 9K")
        assertEquals(parseCard("7K"), player.play(view))
    }

    @Test
    fun `four rule players finish any deal, always bidding and playing legally`() {
        // playOut fails on the first illegal bid or card: finishing 500 deals is the proof.
        val random = Random(17)
        var contracts = 0
        repeat(500) {
            val players = Seat.entries.associateWith { RulePlayer() }
            val round = playOut(Round.start(Seat.entries.random(random), Deal.random(random), rules), players)
            if (round.phase == Round.Phase.OVER) contracts++
        }
        // A sanity bound, not a target: these opening rules find a contract on most deals.
        assertTrue(contracts > 250, "only $contracts contracts out of 500 deals")
    }

    @Test
    fun `rule players clearly beat random players in a duplicate match`() {
        val rule: PlayerFactory = { RulePlayer() }
        val random: PlayerFactory = { seat -> RandomPlayer(Random(seat.ordinal + 100)) }
        val result = duplicateMatch(rule, random, deals = 400, seed = 11, rules = rules)
        println("Rule players vs random players: $result")
        assertTrue(result.isSignificant && result.meanLead > 0, "no clear lead: $result")
    }
}
