package justcoinche.engine

import justcoinche.engine.Bid.Pass
import justcoinche.engine.Round.Phase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HistoryTest {

    /** South holds every club, East every diamond, North every heart, West every spade. */
    private val bySuit = Deal(Suit.entries.map { CardSet.FULL.ofSuit(it) })

    @Test
    fun `cards are written rank then suit, hearts and diamonds never clash`() {
        assertEquals("10K", Card.of(Rank.TEN, Suit.DIAMONDS).notation)
        assertEquals("VC", Card.of(Rank.JACK, Suit.HEARTS).notation)
        assertEquals("AT", Card.of(Rank.ACE, Suit.CLUBS).notation)
        assertEquals("DP", Card.of(Rank.QUEEN, Suit.SPADES).notation)
        assertEquals(32, Card.deck.map { it.notation }.toSet().size)
        Card.deck.forEach { assertEquals(it, parseCard(it.notation)) }
    }

    @Test
    fun `every kind of bid is written and read back`() {
        val bids = listOf(
            Pass, Bid.Coinche, Bid.Surcoinche,
            Bid.Announce(80, ContractType.SuitTrump(Suit.CLUBS)),
            Bid.Announce(180, ContractType.NoTrump),
            Bid.Capot(ContractType.AllTrump),
            Bid.CapotBelote(ContractType.SuitTrump(Suit.DIAMONDS)),
        )
        assertEquals(
            listOf("-", "coinche", "surcoinche", "80T", "180SA", "capot-TA", "capotbelote-K"),
            bids.map { it.notation },
        )
        bids.forEach { assertEquals(it, parseBid(it.notation)) }
    }

    @Test
    fun `a played deal is written as one exact line`() {
        // West deals, South bids 80 clubs and, holding every club, wins every trick.
        // Each player plays their first legal card, so the line is fully predictable.
        val round = playedRound()
        val tricks = listOf("7", "8", "9", "10", "V", "D", "R", "A")
            .joinToString(" / ") { "S:${it}T ${it}K ${it}C ${it}P" }
        assertEquals(
            "v1 | donneur O | regles pts-contrat,sous-couper,coinche-tour,sa,ta,belote-perdue,cible-1000" +
                " | donne ffffaaaa55550000 | encheres S:80T E:- N:- O:- | jeu $tricks | score NS 80 EO 0",
            History.write(round),
        )
    }

    @Test
    fun `a redeal is written with no play and no score`() {
        var round = Round.start(Seat.WEST, bySuit, Rules())
        repeat(4) { round = round.call(round.auction.toSpeak, Pass) }
        assertTrue(History.write(round).endsWith("| encheres S:- E:- N:- O:- | jeu - | score -"))
    }

    @Test
    fun `random deals under random rules are read back exactly`() {
        val random = Random(29)
        repeat(2_000) {
            val line = History.write(randomRound(random))
            assertEquals(line, History.write(History.read(line)))
            assertTrue(line.length < 400, "line too long: ${line.length}")
        }
    }

    @Test
    fun `a deal in progress is written and read back at every single move`() {
        val random = Random(37)
        repeat(50) {
            randomRoundSteps(random).forEach { round ->
                val line = History.write(round)
                val read = History.read(line)
                assertEquals(line, History.write(read))
                assertEquals(round.phase, read.phase)
            }
        }
        val start = Round.start(Seat.WEST, bySuit, Rules())
        assertTrue(History.write(start).endsWith("| encheres - | jeu - | score en-cours"))
        val firstCard = start.call(Seat.SOUTH, Bid.Announce(80, ContractType.SuitTrump(Suit.CLUBS)))
            .call(Seat.EAST, Pass).call(Seat.NORTH, Pass).call(Seat.WEST, Pass)
            .play(Card.of(Rank.SEVEN, Suit.CLUBS))
        assertTrue(History.write(firstCard).endsWith("| jeu S:7T | score en-cours"))
    }

    @Test
    fun `a line whose score does not match the replay is rejected`() {
        val line = History.write(playedRound())
        val tampered = line.replace(Regex("score NS \\d+"), "score NS 999")
        assertFailsWith<IllegalArgumentException> { History.read(tampered) }
    }

    @Test
    fun `a line naming the wrong leader for a trick is rejected`() {
        // The cards would replay fine, but the record would misstate who led: it must not pass.
        val line = History.write(playedRound())
        assertFailsWith<IllegalArgumentException> { History.read(line.replaceFirst("jeu S:", "jeu E:")) }
    }

    @Test
    fun `a line with an illegal card or an unknown version is rejected`() {
        val line = History.write(playedRound())
        // South leads the 7 of clubs then East plays it again: not in East's hand.
        val firstTrick = line.substringAfter("jeu ").substringBefore(" /")
        val (leader, cards) = firstTrick.split(":")
        val broken = cards.split(" ").let { listOf(it[0], it[0], it[2], it[3]) }.joinToString(" ")
        assertFailsWith<IllegalArgumentException> { History.read(line.replace(firstTrick, "$leader:$broken")) }
        assertFailsWith<IllegalArgumentException> { History.read(line.replaceFirst("v1", "v9")) }
    }

    /** The by-suit deal: South bids 80 clubs, everyone passes, and each player plays their first legal card. */
    private fun playedRound(): Round {
        var round = Round.start(Seat.WEST, bySuit, Rules())
        listOf(Bid.Announce(80, ContractType.SuitTrump(Suit.CLUBS)), Pass, Pass, Pass)
            .forEach { round = round.call(round.auction.toSpeak, it) }
        while (round.phase == Phase.PLAYING) {
            round = round.play(checkNotNull(round.cardPlay).playableCards().toList().first())
        }
        return round
    }

    /** A whole deal played with random legal bids and cards, under random rules. */
    private fun randomRound(random: Random): Round = randomRoundSteps(random).last()

    /** Every state of such a deal, from the empty auction to the end. */
    private fun randomRoundSteps(random: Random): List<Round> {
        val steps = mutableListOf<Round>()
        val rules = Rules(
            mustUnderTrump = random.nextBoolean(),
            coincheTiming = CoincheTiming.entries.random(random),
            allowNoTrump = random.nextBoolean(),
            allowAllTrump = random.nextBoolean(),
            scoringMode = ScoringMode.entries.random(random),
            beloteKeptOnFailure = random.nextBoolean(),
        )
        var round = Round.start(Seat.entries.random(random), Deal.random(random), rules)
        steps += round
        while (round.phase == Phase.BIDDING) {
            val seat = Seat.entries.filter { round.auction.legalBids(it).isNotEmpty() }.random(random)
            val legal = round.auction.legalBids(seat)
            round = round.call(seat, if (Pass in legal && random.nextInt(3) > 0) Pass else legal.random(random))
            steps += round
        }
        while (round.phase == Phase.PLAYING) {
            round = round.play(checkNotNull(round.cardPlay).playableCards().toList().random(random))
            steps += round
        }
        return steps
    }
}
