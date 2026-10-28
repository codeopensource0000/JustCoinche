package justcoinche.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardPlayTest {

    private val rules = Rules()

    /** South holds every club, East every diamond, North every heart, West every spade. */
    private val bySuit = Deal(Suit.entries.map { CardSet.FULL.ofSuit(it) })

    private val allContracts: List<ContractType> =
        Suit.entries.map { ContractType.SuitTrump(it) } + ContractType.NoTrump + ContractType.AllTrump

    /** Plays the whole deal, each player choosing a random legal card. */
    private fun playOut(start: CardPlay, random: Random): CardPlay {
        var play = start
        while (!play.isOver) play = play.play(play.playableCards().toList().random(random))
        return play
    }

    @Test
    fun `the leader plays first and the turn follows the seats`() {
        val start = CardPlay.start(bySuit, ContractType.SuitTrump(Suit.CLUBS), rules, Seat.WEST)
        assertEquals(Seat.WEST, start.toPlay)
        val afterOne = start.play(start.playableCards().toList().first())
        assertEquals(Seat.SOUTH, afterOne.toPlay)
    }

    @Test
    fun `a card that is not legal is rejected`() {
        // In a random deal, find a moment where the player to move holds a card
        // the rules forbid (typically: a card of another suit while able to follow).
        val random = Random(5)
        var play = CardPlay.start(Deal.random(random), ContractType.SuitTrump(Suit.HEARTS), rules, Seat.SOUTH)
        while (play.hand(play.toPlay) == play.playableCards()) {
            play = play.play(play.playableCards().toList().random(random))
        }
        val forbidden = (play.hand(play.toPlay).toList() - play.playableCards().toList().toSet()).first()
        val current = play
        assertFailsWith<IllegalArgumentException> { current.play(forbidden) }
    }

    @Test
    fun `a completed trick is set aside and its winner, not its leader, leads the next one`() {
        // Clubs are trumps. West leads a spade; South has no spade but every club,
        // so South must trump and wins: South, not West, leads the second trick.
        var play = CardPlay.start(bySuit, ContractType.SuitTrump(Suit.CLUBS), rules, Seat.WEST)
        repeat(4) { play = play.play(play.playableCards().toList().first()) }
        assertEquals(1, play.tricks.size)
        assertTrue(play.currentTrick.cards.isEmpty())
        assertEquals(Seat.SOUTH, play.toPlay)
    }

    @Test
    fun `holding every trump wins every trick, with the last trick and the belote`() {
        // Clubs are trumps and South holds them all: nobody else can follow or trump,
        // so South wins all 8 tricks whatever is played.
        val end = playOut(CardPlay.start(bySuit, ContractType.SuitTrump(Suit.CLUBS), rules, Seat.SOUTH), Random(1))
        assertTrue(end.tricks.all { it.winner(end.contract) == Seat.SOUTH })
        assertEquals(152 + 10 + 20, end.points(Team.NORTH_SOUTH))
        assertEquals(0, end.points(Team.EAST_WEST))
    }

    @Test
    fun `the outcome sums up points made, belote and capot for the scoring`() {
        val end = playOut(CardPlay.start(bySuit, ContractType.SuitTrump(Suit.CLUBS), rules, Seat.SOUTH), Random(1))
        assertEquals(PlayOutcome(TeamPoints(182, 0), Team.NORTH_SOUTH, Team.NORTH_SOUTH), end.outcome())

        // When each team wins at least one trick, there is no capot.
        val random = Random(21)
        val mixed = generateSequence {
            playOut(CardPlay.start(Deal.random(random), ContractType.NoTrump, rules, Seat.SOUTH), random)
        }.first { play -> play.tricks.map { it.winner(ContractType.NoTrump)?.team }.toSet().size == 2 }
        assertNull(mixed.outcome().capotTeam)
    }

    @Test
    fun `the belote belongs to the team holding king and queen of trumps, in suit contracts only`() {
        fun beloteTeam(contract: ContractType) = CardPlay.start(bySuit, contract, rules, Seat.SOUTH).beloteTeam
        assertEquals(Team.NORTH_SOUTH, beloteTeam(ContractType.SuitTrump(Suit.CLUBS))) // South
        assertEquals(Team.EAST_WEST, beloteTeam(ContractType.SuitTrump(Suit.DIAMONDS))) // East
        assertNull(beloteTeam(ContractType.NoTrump))
        assertNull(beloteTeam(ContractType.AllTrump))
    }

    @Test
    fun `there is no belote when king and queen of trumps are in different hands, even partners'`() {
        // From the by-suit deal, South gives the queen of clubs to North (her
        // partner) for the 7 of hearts. South keeps king and jack of clubs.
        val queenOfClubs = Card.of(Rank.QUEEN, Suit.CLUBS)
        val sevenOfHearts = Card.of(Rank.SEVEN, Suit.HEARTS)
        val swapped = Deal(
            Seat.entries.map { seat ->
                val hand = bySuit.hand(seat)
                when (seat) {
                    Seat.SOUTH -> hand - queenOfClubs + sevenOfHearts
                    Seat.NORTH -> hand - sevenOfHearts + queenOfClubs
                    else -> hand
                }
            },
        )
        assertNull(CardPlay.start(swapped, ContractType.SuitTrump(Suit.CLUBS), rules, Seat.SOUTH).beloteTeam)
    }

    @Test
    fun `a whole deal shares out 162 points, or 182 with a belote, and the last trick is worth 10`() {
        val random = Random(13)
        repeat(2_000) {
            val contract = allContracts.random(random)
            val end = playOut(CardPlay.start(Deal.random(random), contract, rules, Seat.entries.random(random)), random)

            assertEquals(Deal.HAND_SIZE, end.tricks.size)
            assertTrue(Seat.entries.all { end.hand(it).isEmpty() })
            end.tricks.zipWithNext { previous, next ->
                assertEquals(previous.winner(contract), next.leader, "each trick is led by the previous winner")
            }
            val expectedTotal = if (end.beloteTeam == null) 162 else 182
            assertEquals(expectedTotal, end.points(Team.NORTH_SOUTH) + end.points(Team.EAST_WEST))

            // The team that won the eighth trick gets its card points plus exactly 10.
            val lastTeam = checkNotNull(end.tricks.last().winner(contract)).team
            val lastTeamCardPoints = end.tricks
                .filter { it.winner(contract)?.team == lastTeam }
                .sumOf { it.points(contract) }
            val lastTeamBelote = if (end.beloteTeam == lastTeam) 20 else 0
            assertEquals(lastTeamCardPoints + 10 + lastTeamBelote, end.points(lastTeam))
        }
    }
}
