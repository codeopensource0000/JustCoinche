package justcoinche.app

import androidx.annotation.DrawableRes
import code.opensource0000.justcoinche.R
import justcoinche.engine.Card
import justcoinche.engine.Rank

// The card faces: SVG-cards 2.0.1 by David Bellot (GNU LGPL 2.1 or later), with
// the court figures of the Paris pattern, cut out card by card and converted to
// Android vector drawables (see third_party/svg-cards/README.md).

/** A card's size in the drawings' own units, its outline included. */
internal const val DECK_WIDTH = 169.07f
internal const val DECK_HEIGHT = 244.64f

/** The drawing of each card, in the engine's card order: clubs, diamonds, hearts, spades, each from 7 to Ace. */
private val FACES = intArrayOf(
    R.drawable.card_clubs_7, R.drawable.card_clubs_8, R.drawable.card_clubs_9, R.drawable.card_clubs_10,
    R.drawable.card_clubs_jack, R.drawable.card_clubs_queen, R.drawable.card_clubs_king, R.drawable.card_clubs_ace,
    R.drawable.card_diamonds_7, R.drawable.card_diamonds_8, R.drawable.card_diamonds_9, R.drawable.card_diamonds_10,
    R.drawable.card_diamonds_jack, R.drawable.card_diamonds_queen, R.drawable.card_diamonds_king,
    R.drawable.card_diamonds_ace,
    R.drawable.card_hearts_7, R.drawable.card_hearts_8, R.drawable.card_hearts_9, R.drawable.card_hearts_10,
    R.drawable.card_hearts_jack, R.drawable.card_hearts_queen, R.drawable.card_hearts_king, R.drawable.card_hearts_ace,
    R.drawable.card_spades_7, R.drawable.card_spades_8, R.drawable.card_spades_9, R.drawable.card_spades_10,
    R.drawable.card_spades_jack, R.drawable.card_spades_queen, R.drawable.card_spades_king, R.drawable.card_spades_ace,
)

@DrawableRes
internal fun faceOf(card: Card): Int = FACES[card.index]

/**
 * Where the source draws a corner letter: the start of its baseline at
 * ([x], [baseline]), a font [size] high, and the letters narrowed to [narrow]
 * of their usual width, all in the drawing's units. The opposite corner holds
 * the same letter upside down. These letters were text in the source, which a
 * vector drawable cannot hold, so the app draws them itself; the source has
 * the French letters V, D and R for French readers.
 */
internal data class CornerIndex(val size: Float, val narrow: Float, val x: Float, val baseline: Float)

/** Measured in the source drawing. */
@Suppress("MagicNumber")
private val DIGIT = CornerIndex(size = 45.47f, narrow = 0.534f, x = 5.25f, baseline = 37.4f)

@Suppress("MagicNumber")
internal val CORNER_INDICES: Map<Rank, CornerIndex> = mapOf(
    Rank.SEVEN to DIGIT,
    Rank.EIGHT to DIGIT,
    Rank.NINE to DIGIT,
    Rank.TEN to DIGIT.copy(narrow = 0.305f), // two figures in the room of one
    Rank.JACK to CornerIndex(size = 40f, narrow = 0.565f, x = 10.28f, baseline = 33.17f),
    Rank.QUEEN to CornerIndex(size = 41f, narrow = 0.565f, x = 5.25f, baseline = 33.78f),
    Rank.KING to DIGIT,
    Rank.ACE to DIGIT,
)
