package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import justcoinche.engine.Card
import justcoinche.engine.Rank

/**
 * One card face, our own simple design: the rank and suit in two opposite
 * corners, as on a real card, so that a card half hidden in a hand still reads;
 * a large suit in the middle, or a framed letter for the Jack, Queen and King.
 */
@Composable
internal fun CardFace(
    card: Card,
    size: DpSize,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
) {
    val color = if (card.isRed) RED_SUIT else BLACK_SUIT
    val h = size.height
    val text = with(LocalDensity.current) {
        object {
            val index = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = (h * INDEX_SIZE).toSp())
            val pip = TextStyle(color = color, fontSize = (h * INDEX_SIZE * PIP_FACTOR).toSp())
            val centre = TextStyle(color = color, fontSize = (h * CENTRE_SIZE).toSp())
            val figure = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = (h * FIGURE_SIZE).toSp())
        }
    }
    val border = if (highlighted) BorderStroke(3.dp, GOLD) else BorderStroke(EDGE_WIDTH, EDGE)
    Surface(
        modifier.size(size),
        shape = RoundedCornerShape(h * CORNER),
        color = if (dimmed) DIMMED_CARD else Color.White,
        border = border,
        shadowElevation = 3.dp,
    ) {
        Box(Modifier.padding(h * INSET)) {
            CornerIndex(card, text.index, text.pip, Modifier.align(Alignment.TopStart))
            CornerIndex(card, text.index, text.pip, Modifier.align(Alignment.BottomEnd).rotate(UPSIDE_DOWN))
            val suit = SUIT_SYMBOLS.getValue(card.suit)
            if (card.rank in FIGURES) {
                Box(
                    Modifier.align(Alignment.Center)
                        .background(color.copy(alpha = FIGURE_TINT), RoundedCornerShape(h * CORNER))
                        .padding(horizontal = h * INSET * 2),
                ) {
                    Text(RANK_LABELS.getValue(card.rank) + suit, style = text.figure)
                }
            } else {
                Text(suit, style = text.centre, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun CornerIndex(card: Card, rank: TextStyle, suit: TextStyle, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(RANK_LABELS.getValue(card.rank), style = rank, softWrap = false)
        Text(SUIT_SYMBOLS.getValue(card.suit), style = suit, softWrap = false)
    }
}

/** The back of a card: a blue lattice in a white frame, the same for every card. */
@Composable
internal fun CardBack(size: DpSize, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val radius = CornerRadius(this.size.minDimension * CORNER * CARD_RATIO_HINT)
        drawRoundRect(Color.White, cornerRadius = radius)
        val inset = this.size.minDimension * BACK_FRAME
        val inner = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
        drawRoundRect(BACK_BLUE, topLeft = Offset(inset, inset), size = inner, cornerRadius = radius)
        // A diagonal lattice inside the frame.
        val step = this.size.minDimension / LATTICE_LINES
        clipRect(inset, inset, this.size.width - inset, this.size.height - inset) {
            var d = -this.size.height
            while (d < this.size.width) {
                drawLine(BACK_LINE, Offset(d, 0f), Offset(d + this.size.height, this.size.height), LINE_WIDTH)
                drawLine(BACK_LINE, Offset(d + this.size.height, 0f), Offset(d, this.size.height), LINE_WIDTH)
                d += step
            }
        }
        val frame = Stroke(LINE_WIDTH)
        drawRoundRect(Color.White, topLeft = Offset(inset, inset), size = inner, cornerRadius = radius, style = frame)
    }
}

private val FIGURES = setOf(Rank.JACK, Rank.QUEEN, Rank.KING)
internal val RED_SUIT = Color(0xFFD32F2F)
private val BLACK_SUIT = Color(0xFF1B1B1B)
private val DIMMED_CARD = Color(0xFFB8B8B8)
private val EDGE = Color(0xFF9E9E9E)
private val EDGE_WIDTH = 0.5.dp
internal val GOLD = Color(0xFFF5C542)
private val BACK_BLUE = Color(0xFF1F3A93)
private val BACK_LINE = Color(0x553F6FD8)
private const val INDEX_SIZE = 0.15f
private const val PIP_FACTOR = 0.85f
private const val CENTRE_SIZE = 0.40f
private const val FIGURE_SIZE = 0.24f
private const val FIGURE_TINT = 0.10f
private const val CORNER = 0.07f
private const val INSET = 0.035f
private const val UPSIDE_DOWN = 180f
private const val BACK_FRAME = 0.07f
private const val LATTICE_LINES = 6f
private const val CARD_RATIO_HINT = 1.4f
private const val LINE_WIDTH = 2f
