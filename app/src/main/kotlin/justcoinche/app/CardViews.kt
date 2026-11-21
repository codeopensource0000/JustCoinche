package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import justcoinche.engine.Card
import justcoinche.engine.Rank

/**
 * One card face, our own drawing in the classic French style: rank and suit
 * in two opposite corners, the pips laid out as on a real card for the 7 to
 * the 10, one large pip for the Ace, and a framed, mirrored letter for the
 * Jack, Queen and King, on a slightly grained paper.
 */
@Composable
internal fun CardFace(
    card: Card,
    size: DpSize,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
) {
    val grain = rememberGrain()
    val h = size.height
    val styles = cardStyles(card, h)
    Surface(
        modifier.size(size),
        shape = RoundedCornerShape(h * CORNER),
        color = Color.White,
        border = if (highlighted) BorderStroke(3.dp, GOLD) else BorderStroke(EDGE_WIDTH, EDGE),
        shadowElevation = 4.dp,
    ) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
                if (dimmed) drawRect(DIM)
            },
        ) {
            CornerIndex(card, styles, Modifier.align(Alignment.TopStart).padding(h * INSET))
            CornerIndex(card, styles, Modifier.align(Alignment.BottomEnd).padding(h * INSET).rotate(UPSIDE_DOWN))
            when (card.rank) {
                Rank.JACK, Rank.QUEEN, Rank.KING -> Figure(card, styles, size)
                Rank.ACE -> Text(suitOf(card), Modifier.align(Alignment.Center), style = styles.ace)
                else -> Pips(card, styles, size)
            }
        }
    }
}

/** The text styles of one card, all proportional to its height. */
private class CardStyles(
    val index: TextStyle,
    val corner: TextStyle,
    val pip: TextStyle,
    val ace: TextStyle,
    val figure: TextStyle,
)

@Composable
private fun cardStyles(card: Card, h: Dp): CardStyles = with(LocalDensity.current) {
    val color = if (card.isRed) RED_SUIT else BLACK_SUIT
    fun style(share: Float, bold: Boolean = false) = TextStyle(
        color = color,
        fontSize = (h * share).toSp(),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Serif,
        lineHeight = (h * share).toSp(),
    )
    CardStyles(style(INDEX, bold = true), style(CORNER_SUIT), style(PIP), style(ACE), style(FIGURE, bold = true))
}

@Composable
private fun CornerIndex(card: Card, styles: CardStyles, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(RANK_LABELS.getValue(card.rank), style = styles.index, softWrap = false)
        Text(suitOf(card), style = styles.corner, softWrap = false)
    }
}

/** The pips of a 7 to 10, where a real card has them; those in the lower half are upside down. */
@Composable
private fun Pips(card: Card, styles: CardStyles, size: DpSize) {
    val box = size.height * PIP_BOX
    PIP_LAYOUTS.getValue(card.rank).forEach { (x, y) ->
        Box(
            Modifier.offset(size.width * x - box / 2, size.height * y - box / 2).size(box)
                .rotate(if (y > HALF) UPSIDE_DOWN else 0f),
            contentAlignment = Alignment.Center,
        ) {
            Text(suitOf(card), style = styles.pip, softWrap = false)
        }
    }
}

/** A Jack, Queen or King: the letter and suit in a frame, mirrored in the lower half as on a court card. */
@Composable
private fun Figure(card: Card, styles: CardStyles, size: DpSize) {
    val color = if (card.isRed) RED_SUIT else BLACK_SUIT
    val frame = RoundedCornerShape(size.height * CORNER)
    Box(
        Modifier
            .offset(size.width * FRAME_X, size.height * FRAME_Y)
            .size(size.width * (1 - 2 * FRAME_X), size.height * (1 - 2 * FRAME_Y))
            .background(color.copy(alpha = FRAME_TINT), frame)
            .border(1.5.dp, color.copy(alpha = FRAME_LINE), frame),
    ) {
        val text = RANK_LABELS.getValue(card.rank) + suitOf(card)
        Text(text, Modifier.align(Alignment.TopCenter).padding(top = size.height * INSET), style = styles.figure)
        Text(
            text,
            Modifier.align(Alignment.BottomCenter).padding(bottom = size.height * INSET).rotate(UPSIDE_DOWN),
            style = styles.figure,
        )
    }
}

private fun suitOf(card: Card): String = SUIT_SYMBOLS.getValue(card.suit)

/** Pip centres as fractions of the card's width and height, for each rank from 7 to 10. */
@Suppress("MagicNumber") // a drawing table: each number is a position on the card
private val PIP_LAYOUTS: Map<Rank, List<Pair<Float, Float>>> = run {
    val left = 0.30f
    val right = 0.70f
    val middle = 0.5f
    val sides = listOf(0.21f, 0.5f, 0.79f).flatMap { y -> listOf(left to y, right to y) }
    val sides4 = listOf(0.21f, 0.403f, 0.597f, 0.79f).flatMap { y -> listOf(left to y, right to y) }
    mapOf(
        Rank.SEVEN to sides + (middle to 0.355f),
        Rank.EIGHT to sides + (middle to 0.355f) + (middle to 0.645f),
        Rank.NINE to sides4 + (middle to 0.5f),
        Rank.TEN to sides4 + (middle to 0.305f) + (middle to 0.695f),
    )
}

internal val RED_SUIT = Color(0xFFD0212A)
private val BLACK_SUIT = Color(0xFF151515)
private val EDGE = Color(0x33000000)
private val EDGE_WIDTH = 0.5.dp
private val DIM = Color(0x38000000)
private const val CORNER = 0.06f
private const val INSET = 0.03f
private const val INDEX = 0.15f
private const val CORNER_SUIT = 0.11f
private const val PIP = 0.15f
private const val PIP_BOX = 0.18f
private const val ACE = 0.42f
private const val FIGURE = 0.20f
private const val FRAME_X = 0.20f
private const val FRAME_Y = 0.10f
private const val FRAME_TINT = 0.07f
private const val FRAME_LINE = 0.55f
private const val HALF = 0.5f
private const val UPSIDE_DOWN = 180f
