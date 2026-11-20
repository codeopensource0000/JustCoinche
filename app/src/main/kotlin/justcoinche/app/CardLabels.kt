package justcoinche.app

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CoincheLevel
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Rank
import justcoinche.engine.Seat
import justcoinche.engine.Suit

// How cards, bids and seats are shown on screen: in French, with the usual suit symbols.
// (The game record uses plain letters instead; see the engine's Notation.kt.)

internal val RANK_LABELS = mapOf(
    Rank.SEVEN to "7", Rank.EIGHT to "8", Rank.NINE to "9", Rank.TEN to "10",
    Rank.JACK to "V", Rank.QUEEN to "D", Rank.KING to "R", Rank.ACE to "A",
)

internal val SUIT_SYMBOLS = mapOf(
    Suit.SPADES to "♠", Suit.HEARTS to "♥", Suit.DIAMONDS to "♦", Suit.CLUBS to "♣",
)

/** For example "10♥" or "V♣". */
val Card.label: String get() = RANK_LABELS.getValue(rank) + SUIT_SYMBOLS.getValue(suit)

/** Hearts and diamonds are drawn in red. */
val Card.isRed: Boolean get() = suit == Suit.HEARTS || suit == Suit.DIAMONDS

/** "♥", or "SA" for no-trump and "TA" for all-trump. */
val ContractType.label: String
    get() = when (this) {
        is ContractType.SuitTrump -> SUIT_SYMBOLS.getValue(trump)
        ContractType.NoTrump -> "SA"
        ContractType.AllTrump -> "TA"
    }

/** For example "Passe", "100 ♥" or "Capot SA". */
val Bid.label: String
    get() = when (this) {
        Bid.Pass -> "Passe"
        is Bid.Announce -> "$value ${type.label}"
        is Bid.Capot -> "Capot ${type.label}"
        is Bid.CapotBelote -> "Capot belote ${type.label}"
        Bid.Coinche -> "Coinche"
        Bid.Surcoinche -> "Surcoinche"
    }

/** The value part of a bid, shown between the arrows of the bidding panel. */
internal val BidLevel.label: String
    get() = when (this) {
        is BidLevel.Points -> value.toString()
        BidLevel.Capot -> "Capot"
        BidLevel.CapotBelote -> "Capot belote"
    }

/** For example "100 ♥ coinché". */
val Contract.label: String
    get() {
        val value = when {
            capotBelote -> "Capot belote"
            isCapot -> "Capot"
            else -> value.toString()
        }
        val coinched = when (coinche) {
            CoincheLevel.NONE -> ""
            CoincheLevel.COINCHED -> " coinché"
            CoincheLevel.SURCOINCHED -> " surcoinché"
        }
        return "$value ${type.label}$coinched"
    }

val Seat.label: String
    get() = when (this) {
        Seat.SOUTH -> "Sud"
        Seat.EAST -> "Est"
        Seat.NORTH -> "Nord"
        Seat.WEST -> "Ouest"
    }
