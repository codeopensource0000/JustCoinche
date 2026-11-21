package justcoinche.app

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Game
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Trick
import justcoinche.record.GameStore

// What the table needs from a round, kept apart from the drawing so that it
// can be unit tested without a phone.

/** The human always sits South; the three other seats are computer players. */
internal val HUMAN = Seat.SOUTH

/**
 * Opens the game left unfinished last time or, if there is none, starts a new
 * one, recording which computer [players] sit where. A record that cannot be
 * read is left on disk for analysis, and a new game starts rather than the
 * app failing at every launch.
 */
internal fun GameStore.openGame(players: Map<Seat, String> = emptyMap()): Game =
    runCatching { resume() }.getOrNull() ?: start(Rules(), Seat.entries.random(), players = players)

/** The deal on show: the one being played, or the last one finished while the next one waits. */
internal val Game.shownRound: Round?
    get() = currentRound ?: results.lastOrNull()?.round

/**
 * The trick shown in the middle of the table. Right after the fourth card the
 * engine already starts an empty trick: the completed one stays on show until
 * the next card is played, so that everyone can see how it ended.
 */
internal val Round.shownTrick: Trick?
    get() = cardPlay?.let { play ->
        if (play.currentTrick.cards.isEmpty()) play.tricks.lastOrNull() ?: play.currentTrick else play.currentTrick
    }

/** Whether the trick on show is complete: the table then pauses longer before the next card. */
internal val Round.showsCompletedTrick: Boolean
    get() = shownTrick?.isComplete == true

/** The last thing each seat said in the auction. */
internal val Round.lastCalls: Map<Seat, Bid>
    get() = auction.calls.associate { it.seat to it.bid }

/** The cards [seat] still holds. */
internal fun Round.handOf(seat: Seat): CardSet = cardPlay?.hand(seat) ?: deal.hand(seat)

/**
 * The human's hand in display order: suits alternating black and red
 * (spades, hearts, clubs, diamonds), each from its highest card down, in trump
 * order for a trump suit once the contract is known.
 */
internal fun sortedForDisplay(hand: CardSet, type: ContractType = ContractType.NoTrump): List<Card> =
    hand.toList().sortedWith(
        compareBy<Card> { DISPLAY_SUITS.indexOf(it.suit) }.thenByDescending { type.strength(it) },
    )

internal val DISPLAY_SUITS = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)

/** The contract types offered by the bidding panel, in the order of its buttons. */
internal val BID_TYPES: List<ContractType> =
    DISPLAY_SUITS.map { ContractType.SuitTrump(it) } + ContractType.NoTrump + ContractType.AllTrump

/** A rung of the bid ladder, chosen with the arrows before picking the suit. */
internal sealed interface BidLevel {
    data class Points(val value: Int) : BidLevel
    data object Capot : BidLevel
    data object CapotBelote : BidLevel

    fun bid(type: ContractType): Bid = when (this) {
        is Points -> Bid.Announce(value, type)
        Capot -> Bid.Capot(type)
        CapotBelote -> Bid.CapotBelote(type)
    }
}

/** The rungs at which at least one bid is legal, lowest first. */
internal fun bidLevels(legal: List<Bid>): List<BidLevel> {
    val points = legal.filterIsInstance<Bid.Announce>().map { it.value }.distinct().sorted()
    return points.map { BidLevel.Points(it) } +
        listOfNotNull(
            BidLevel.Capot.takeIf { legal.any { it is Bid.Capot } },
            BidLevel.CapotBelote.takeIf { legal.any { it is Bid.CapotBelote } },
        )
}
