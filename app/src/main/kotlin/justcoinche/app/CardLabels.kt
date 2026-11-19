package justcoinche.app

import justcoinche.engine.Card
import justcoinche.engine.Rank
import justcoinche.engine.Suit

// How cards are shown on screen: French ranks and the usual suit symbols.
// (The game record uses plain letters instead; see the engine's Notation.kt.)

private val RANK_LABELS = mapOf(
    Rank.SEVEN to "7", Rank.EIGHT to "8", Rank.NINE to "9", Rank.TEN to "10",
    Rank.JACK to "V", Rank.QUEEN to "D", Rank.KING to "R", Rank.ACE to "A",
)

private val SUIT_SYMBOLS = mapOf(
    Suit.SPADES to "♠", Suit.HEARTS to "♥", Suit.DIAMONDS to "♦", Suit.CLUBS to "♣",
)

/** For example "10♥" or "V♣". */
val Card.label: String get() = RANK_LABELS.getValue(rank) + SUIT_SYMBOLS.getValue(suit)

/** Hearts and diamonds are drawn in red. */
val Card.isRed: Boolean get() = suit == Suit.HEARTS || suit == Suit.DIAMONDS
