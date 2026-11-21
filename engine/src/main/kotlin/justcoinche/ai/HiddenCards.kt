package justcoinche.ai

import justcoinche.engine.Card
import justcoinche.engine.CardPlay
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.PlayerView
import justcoinche.engine.Seat
import justcoinche.engine.Team
import justcoinche.engine.Trick
import justcoinche.engine.legalCards
import kotlin.random.Random

/**
 * What a player knows of the hidden hands during the card play, and random
 * deals that agree with it, as the bridge programs draw them.
 *
 * Known: the cards nobody has shown yet, how many each other player still
 * holds, and the cards each of them cannot hold because of how they played:
 * a suit not followed, a trump not played or not raised when the rules
 * demanded it. A played card that was legal stays legal when the hand held
 * fewer cards, and the rules' demands each come from a single card held (one
 * of the suit led, a trump, a higher trump), so a card is excluded exactly
 * when holding it, beside the card played, would have made that play illegal.
 */
internal class HiddenCards(private val view: PlayerView) {
    private val me = view.seat
    private val type: ContractType = checkNotNull(view.contract).type
    private val allTricks: List<Trick> = view.tricks + listOfNotNull(view.currentTrick)

    /** The cards each seat has played so far. */
    val played: List<CardSet> = Seat.entries.map { seat ->
        CardSet.of(allTricks.flatMap { trick -> trick.cards.filterIndexed { i, _ -> seatAt(trick, i) == seat } })
    }

    /** Cards held by the other three players, unknown to [me]. */
    val unseen: CardSet = CardSet(
        (CardSet.FULL.bits and view.hand.bits.inv()) and played.fold(0) { bits, cards -> bits or cards.bits }.inv(),
    )

    /** How many cards each seat still holds. */
    private val counts = IntArray(Seat.entries.size) { Deal.HAND_SIZE - played[it].size }

    /** For each seat, the unseen cards it cannot hold. */
    val excluded: List<CardSet> = Seat.entries.map { seat -> if (seat == me) CardSet.EMPTY else exclusions(seat) }

    private fun exclusions(seat: Seat): CardSet {
        var excluded = 0
        allTricks.forEach { trick ->
            trick.cards.forEachIndexed { i, card ->
                if (i > 0 && seatAt(trick, i) == seat) {
                    val before = Trick(trick.leader, trick.cards.take(i))
                    unseen.forEach { other ->
                        if (card !in legalCards(CardSet.of(listOf(card, other)), before, type, view.rules)) {
                            excluded = excluded or (1 shl other.index)
                        }
                    }
                }
            }
        }
        return CardSet(excluded)
    }

    /**
     * A random deal of the unseen cards that agrees with what is known: the
     * remaining hands by seat, this player's included. The most constrained
     * cards are placed first, each with a chance in proportion to the room
     * left in the hands that may hold it; a draw that gets stuck starts over.
     * Null if no deal was found, which only happens if the knowledge is wrong.
     */
    fun sample(random: Random): List<CardSet>? {
        val cards = unseen.toList().shuffled(random)
            .sortedBy { card -> Seat.entries.count { it != me && card !in excluded[it.ordinal] } }
        repeat(MAX_TRIES) {
            val room = counts.copyOf()
            room[me.ordinal] = 0
            val hands = IntArray(Seat.entries.size)
            hands[me.ordinal] = view.hand.bits
            val placed = cards.all { card ->
                val seat = pickSeat(card, room, random)
                if (seat != null) {
                    hands[seat.ordinal] = hands[seat.ordinal] or (1 shl card.index)
                    room[seat.ordinal]--
                }
                seat != null
            }
            if (placed) return hands.map { CardSet(it) }
        }
        return null
    }

    private fun pickSeat(card: Card, room: IntArray, random: Random): Seat? {
        val allowed = Seat.entries.filter { room[it.ordinal] > 0 && card !in excluded[it.ordinal] }
        val total = allowed.sumOf { room[it.ordinal] }
        if (total == 0) return null
        var draw = random.nextInt(total)
        return allowed.first { seat -> (draw < room[seat.ordinal]).also { draw -= room[seat.ordinal] } }
    }

    /** The hands of a drawn deal as they were dealt: the hands now plus the cards played. */
    fun initialHands(hands: List<CardSet>): List<CardSet> =
        Seat.entries.map { hands[it.ordinal] union played[it.ordinal] }

    /** The team holding king and queen of trumps in a drawn deal. */
    fun beloteTeam(hands: List<CardSet>): Team? = CardPlay.beloteTeam(Deal(initialHands(hands)), type)

    private fun seatAt(trick: Trick, position: Int): Seat =
        Seat.entries[(trick.leader.ordinal + position) % Seat.entries.size]

    private companion object {
        const val MAX_TRIES = 50
    }
}
