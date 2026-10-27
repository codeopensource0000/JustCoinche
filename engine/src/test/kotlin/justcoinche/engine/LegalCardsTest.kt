package justcoinche.engine

import justcoinche.engine.Rank.ACE
import justcoinche.engine.Rank.EIGHT
import justcoinche.engine.Rank.JACK
import justcoinche.engine.Rank.KING
import justcoinche.engine.Rank.NINE
import justcoinche.engine.Rank.SEVEN
import justcoinche.engine.Rank.TEN
import justcoinche.engine.Seat.NORTH
import justcoinche.engine.Seat.SOUTH
import justcoinche.engine.Seat.WEST
import justcoinche.engine.Suit.CLUBS
import justcoinche.engine.Suit.DIAMONDS
import justcoinche.engine.Suit.HEARTS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every scenario has South to play. North is South's partner; East and West
 * are opponents. Play order: SOUTH, EAST, NORTH, WEST.
 */
class LegalCardsTest {

    private val heartsTrump = ContractType.SuitTrump(HEARTS)
    private val pisser = Rules(mustUnderTrump = true)
    private val noPisser = Rules(mustUnderTrump = false)

    private fun cards(vararg cards: Pair<Rank, Suit>): CardSet =
        CardSet.of(cards.map { (rank, suit) -> Card.of(rank, suit) })

    private fun trick(leader: Seat, vararg cards: Pair<Rank, Suit>): Trick =
        cards.fold(Trick(leader)) { trick, (rank, suit) -> trick.play(Card.of(rank, suit)) }

    @Test
    fun `the leader may play any card`() {
        val hand = cards(SEVEN to CLUBS, JACK to HEARTS, ACE to DIAMONDS)
        assertEquals(hand, legalCards(hand, Trick(SOUTH), heartsTrump, pisser))
    }

    @Test
    fun `in a plain suit you must follow but need not go higher`() {
        val hand = cards(SEVEN to CLUBS, ACE to CLUBS, JACK to HEARTS)
        val trick = trick(WEST, KING to CLUBS)
        assertEquals(cards(SEVEN to CLUBS, ACE to CLUBS), legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `on a trump lead you must go higher if you can`() {
        val hand = cards(JACK to HEARTS, NINE to HEARTS, SEVEN to HEARTS, ACE to CLUBS)
        val trick = trick(WEST, ACE to HEARTS)
        assertEquals(cards(JACK to HEARTS, NINE to HEARTS), legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `on a trump lead you must go higher even over your partner`() {
        // North (partner) leads the 10, West plays the 7: South must still beat the 10.
        val hand = cards(ACE to HEARTS, EIGHT to HEARTS)
        val trick = trick(NORTH, TEN to HEARTS, SEVEN to HEARTS)
        assertEquals(cards(ACE to HEARTS), legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `on a trump lead you cannot beat, any trump will do but you must still follow`() {
        val hand = cards(EIGHT to HEARTS, SEVEN to HEARTS, ACE to CLUBS)
        val trick = trick(WEST, JACK to HEARTS)
        assertEquals(cards(EIGHT to HEARTS, SEVEN to HEARTS), legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `without the led suit and with your partner master, you may discard anything`() {
        val hand = cards(JACK to HEARTS, SEVEN to DIAMONDS)
        val trick = trick(NORTH, ACE to CLUBS, SEVEN to CLUBS)
        assertEquals(hand, legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `without the led suit and with an opponent master, you must trump`() {
        val hand = cards(JACK to HEARTS, SEVEN to HEARTS, SEVEN to DIAMONDS)
        val trick = trick(WEST, ACE to CLUBS)
        assertEquals(cards(JACK to HEARTS, SEVEN to HEARTS), legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `when an opponent has trumped you must overtrump if you can`() {
        // North (partner) leads clubs, West trumps with the 10.
        val hand = cards(ACE to HEARTS, EIGHT to HEARTS, SEVEN to DIAMONDS)
        val trick = trick(NORTH, SEVEN to CLUBS, TEN to HEARTS)
        assertEquals(cards(ACE to HEARTS), legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `unable to overtrump, you must play a lower trump when pisser is the rule`() {
        val hand = cards(EIGHT to HEARTS, SEVEN to DIAMONDS)
        val trick = trick(NORTH, SEVEN to CLUBS, JACK to HEARTS)
        assertEquals(cards(EIGHT to HEARTS), legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `unable to overtrump, you may discard anything when pisser is not the rule`() {
        val hand = cards(EIGHT to HEARTS, SEVEN to DIAMONDS)
        val trick = trick(NORTH, SEVEN to CLUBS, JACK to HEARTS)
        assertEquals(hand, legalCards(hand, trick, heartsTrump, noPisser))
    }

    @Test
    fun `without the led suit and without trumps, you may discard anything`() {
        val hand = cards(SEVEN to DIAMONDS, KING to DIAMONDS)
        val trick = trick(WEST, ACE to CLUBS)
        assertEquals(hand, legalCards(hand, trick, heartsTrump, pisser))
    }

    @Test
    fun `in no-trump you follow without going higher, and discard freely otherwise`() {
        val trick = trick(WEST, KING to CLUBS)
        val withClubs = cards(SEVEN to CLUBS, ACE to CLUBS, ACE to DIAMONDS)
        assertEquals(cards(SEVEN to CLUBS, ACE to CLUBS), legalCards(withClubs, trick, ContractType.NoTrump, pisser))
        val withoutClubs = cards(SEVEN to DIAMONDS, JACK to HEARTS)
        assertEquals(withoutClubs, legalCards(withoutClubs, trick, ContractType.NoTrump, pisser))
    }

    @Test
    fun `in all-trump you must go higher in the led suit, and discard freely otherwise`() {
        val trick = trick(WEST, ACE to CLUBS)
        val canBeat = cards(NINE to CLUBS, EIGHT to CLUBS, JACK to DIAMONDS)
        assertEquals(cards(NINE to CLUBS), legalCards(canBeat, trick, ContractType.AllTrump, pisser))
        val cannotBeat = cards(EIGHT to CLUBS, SEVEN to CLUBS, JACK to DIAMONDS)
        assertEquals(
            cards(EIGHT to CLUBS, SEVEN to CLUBS),
            legalCards(cannotBeat, trick, ContractType.AllTrump, pisser),
        )
        val noClubs = cards(JACK to DIAMONDS, SEVEN to HEARTS)
        assertEquals(noClubs, legalCards(noClubs, trick, ContractType.AllTrump, pisser))
    }

    @Test
    fun `random legal play never gets stuck and plays out the whole deck`() {
        // Plays 2,000 deals card by card, each player picking a random legal card,
        // under random contracts and rules. This cannot tell whether a rule is
        // right (the scenarios above do that); it checks that the rules always
        // leave a legal card inside the hand, and that a deal ends after
        // 8 tricks with every card played and 152 card points shared out.
        val random = Random(11)
        val contracts = Suit.entries.map { ContractType.SuitTrump(it) } + ContractType.NoTrump + ContractType.AllTrump
        repeat(2_000) {
            val deal = Deal.random(random)
            val contract = contracts.random(random)
            val rules = Rules(mustUnderTrump = random.nextBoolean())
            val hands = Seat.entries.associateWith { deal.hand(it) }.toMutableMap()
            var leader = Seat.entries.random(random)
            var cardPoints = 0
            repeat(Deal.HAND_SIZE) {
                var trick = Trick(leader)
                while (!trick.isComplete) {
                    val seat = trick.nextSeat
                    val hand = hands.getValue(seat)
                    val legal = legalCards(hand, trick, contract, rules)
                    assertFalse(legal.isEmpty())
                    assertEquals(legal, legal intersect hand, "a legal card outside the hand")
                    val card = legal.toList().random(random)
                    hands[seat] = hand - card
                    trick = trick.play(card)
                }
                cardPoints += trick.points(contract)
                leader = checkNotNull(trick.winner(contract))
            }
            assertTrue(hands.values.all { it.isEmpty() })
            assertEquals(152, cardPoints)
        }
    }
}
