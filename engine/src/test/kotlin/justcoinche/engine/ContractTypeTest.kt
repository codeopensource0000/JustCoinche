package justcoinche.engine

import justcoinche.engine.Rank.ACE
import justcoinche.engine.Rank.EIGHT
import justcoinche.engine.Rank.JACK
import justcoinche.engine.Rank.KING
import justcoinche.engine.Rank.NINE
import justcoinche.engine.Rank.QUEEN
import justcoinche.engine.Rank.SEVEN
import justcoinche.engine.Rank.TEN
import kotlin.test.Test
import kotlin.test.assertEquals

class ContractTypeTest {

    private val allContracts: List<ContractType> =
        Suit.entries.map { ContractType.SuitTrump(it) } + ContractType.NoTrump + ContractType.AllTrump

    @Test
    fun `every contract type totals 152 card points`() {
        allContracts.forEach { contract ->
            assertEquals(152, Card.deck.sumOf { contract.points(it) }, "contract $contract")
        }
    }

    @Test
    fun `trump suit follows the trump scale and the others the plain scale`() {
        val hearts = ContractType.SuitTrump(Suit.HEARTS)
        assertEquals(
            listOf(20, 14, 11, 10, 4, 3, 0, 0),
            listOf(JACK, NINE, ACE, TEN, KING, QUEEN, EIGHT, SEVEN).map { hearts.points(Card.of(it, Suit.HEARTS)) },
        )
        assertEquals(
            listOf(11, 10, 4, 3, 2, 0, 0, 0),
            listOf(ACE, TEN, KING, QUEEN, JACK, NINE, EIGHT, SEVEN).map { hearts.points(Card.of(it, Suit.SPADES)) },
        )
    }

    @Test
    fun `no-trump raises the ace to 19 and all-trump uses its own scale`() {
        assertEquals(19, ContractType.NoTrump.points(Card.of(ACE, Suit.CLUBS)))
        assertEquals(
            listOf(14, 9, 6, 5, 3, 1, 0, 0),
            listOf(JACK, NINE, ACE, TEN, KING, QUEEN, EIGHT, SEVEN)
                .map { ContractType.AllTrump.points(Card.of(it, Suit.CLUBS)) },
        )
    }

    @Test
    fun `trump order is J 9 A 10 K Q 8 7 and plain order is A 10 K Q J 9 8 7`() {
        val hearts = ContractType.SuitTrump(Suit.HEARTS)
        assertEquals(TRUMP_ORDER, ranksByStrength(hearts, Suit.HEARTS))
        assertEquals(PLAIN_ORDER, ranksByStrength(hearts, Suit.CLUBS))
    }

    @Test
    fun `no-trump plays every suit in plain order and all-trump every suit in trump order`() {
        Suit.entries.forEach { suit ->
            assertEquals(PLAIN_ORDER, ranksByStrength(ContractType.NoTrump, suit))
            assertEquals(TRUMP_ORDER, ranksByStrength(ContractType.AllTrump, suit))
        }
    }

    private fun ranksByStrength(contract: ContractType, suit: Suit): List<Rank> =
        Rank.entries.sortedByDescending { contract.strength(Card.of(it, suit)) }

    private companion object {
        val TRUMP_ORDER = listOf(JACK, NINE, ACE, TEN, KING, QUEEN, EIGHT, SEVEN)
        val PLAIN_ORDER = listOf(ACE, TEN, KING, QUEEN, JACK, NINE, EIGHT, SEVEN)
    }
}
