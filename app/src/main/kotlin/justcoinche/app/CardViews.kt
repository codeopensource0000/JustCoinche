package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import justcoinche.engine.Card

/**
 * One card face from the SVG-cards deck (see DeckArt.kt), on a slightly
 * grained paper, with its rank letters drawn in two opposite corners. A
 * [highlighted] card is ringed in gold; a [dimmed] one is greyed.
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
    Surface(
        modifier.size(size),
        shape = RoundedCornerShape(size.height * CORNER),
        color = Color.White,
        border = if (highlighted) BorderStroke(3.dp, GOLD) else null,
        shadowElevation = 4.dp,
    ) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
                if (dimmed) drawRect(DIM)
            },
        ) {
            Image(
                painterResource(faceOf(card)),
                contentDescription = card.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            CornerIndices(card, Modifier.fillMaxSize())
        }
    }
}

/** The rank letter in the top-left corner, as the source draws it, and the same upside down in the opposite one. */
@Composable
private fun CornerIndices(card: Card, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val index = CORNER_INDICES.getValue(card.rank)
    val style = TextStyle(
        color = if (card.isRed) DECK_RED else Color.Black,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        textGeometricTransform = TextGeometricTransform(scaleX = index.narrow),
    )
    Canvas(modifier) {
        // One unit of the drawing, in pixels.
        val unit = size.height / DECK_HEIGHT
        val text = measurer.measure(RANK_LABELS.getValue(card.rank), style.copy(fontSize = (index.size * unit).toSp()))
        val topLeft = Offset(index.x * unit, index.baseline * unit - text.firstBaseline)
        drawText(text, topLeft = topLeft)
        rotate(UPSIDE_DOWN) { drawText(text, topLeft = topLeft) }
    }
}

/**
 * A card too small for its drawing to be read, as in the summary of a deal:
 * only its rank and suit, large, on plain paper.
 */
@Composable
internal fun MiniCard(card: Card, size: DpSize, modifier: Modifier = Modifier) {
    val style = with(LocalDensity.current) {
        val text = (size.height * MINI_TEXT).toSp()
        TextStyle(
            color = if (card.isRed) DECK_RED else Color.Black,
            fontSize = text,
            lineHeight = text,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
        )
    }
    Surface(
        modifier.size(size),
        shape = RoundedCornerShape(size.height * CORNER),
        color = Color.White,
        border = BorderStroke(MINI_EDGE_WIDTH, MINI_EDGE),
        shadowElevation = 1.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(RANK_LABELS.getValue(card.rank), style = style, softWrap = false)
            Text(SUIT_SYMBOLS.getValue(card.suit), style = style, softWrap = false)
        }
    }
}

/** The red of the bidding panel's suits. */
internal val RED_SUIT = Color(0xFFD0212A)

/** The red of the deck's hearts and diamonds. */
private val DECK_RED = Color(0xFFE6180A)
private val DIM = Color(0x38000000)
private val MINI_EDGE = Color(0x55000000)
private val MINI_EDGE_WIDTH = 0.5.dp
private const val MINI_TEXT = 0.36f

/** The deck's corner radius, as a share of the card's height. */
private const val CORNER = 8.12f / DECK_HEIGHT
private const val UPSIDE_DOWN = 180f
