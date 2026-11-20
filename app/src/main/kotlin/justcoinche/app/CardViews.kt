package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Seat
import justcoinche.engine.Trick

/**
 * One card, drawn plainly: the rank in the corner, the suit symbol in the
 * middle. Our own simple faces until real artwork comes.
 */
@Composable
internal fun CardFace(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = if (card.isRed) RED_SUIT else Color.Black
    val border = if (highlighted) BorderStroke(3.dp, HIGHLIGHT) else BorderStroke(1.dp, Color.Gray)
    val shape = RoundedCornerShape(6.dp)
    val content: @Composable () -> Unit = {
        Box(Modifier.padding(4.dp)) {
            Text(
                RANK_LABELS.getValue(card.rank),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = (width.value * 0.3f).sp,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                SUIT_SYMBOLS.getValue(card.suit),
                color = color,
                fontSize = (width.value * 0.5f).sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
    val sized = modifier.size(width, width * CARD_RATIO)
    if (onClick == null) {
        Surface(sized, shape = shape, color = Color.White, border = border, content = content)
    } else {
        Surface(onClick, sized, shape = shape, color = Color.White, border = border, content = content)
    }
}

/**
 * The human's hand along the bottom of the table. When it is their turn to
 * play, the playable cards can be tapped and the others are dimmed.
 */
@Composable
internal fun HandRow(hand: CardSet, type: ContractType, playable: CardSet, onPlay: (Card) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        sortedForDisplay(hand, type).forEach { card ->
            val canPlay = card in playable
            CardFace(
                card,
                width = HAND_CARD_WIDTH,
                modifier = Modifier.alpha(if (playable.isEmpty() || canPlay) 1f else DIMMED),
                onClick = if (canPlay) ({ onPlay(card) }) else null,
            )
        }
    }
}

/** The trick in the middle of the table, each card in front of the seat that played it. */
@Composable
internal fun TrickView(trick: Trick, type: ContractType) {
    val winner = if (trick.isComplete) trick.winner(type) else null
    // Wide enough for the West and East cards on either side of North's and
    // South's, tall enough for North's above South's with a gap between.
    Box(Modifier.size(TRICK_CARD_WIDTH * TRICK_WIDTH_IN_CARDS, TRICK_CARD_WIDTH * CARD_RATIO * TRICK_HEIGHT_IN_CARDS)) {
        trick.cards.forEachIndexed { index, card ->
            val seat = Seat.entries[(trick.leader.ordinal + index) % Seat.entries.size]
            val place = when (seat) {
                Seat.NORTH -> Alignment.TopCenter
                Seat.SOUTH -> Alignment.BottomCenter
                Seat.WEST -> Alignment.CenterStart
                Seat.EAST -> Alignment.CenterEnd
            }
            CardFace(card, TRICK_CARD_WIDTH, Modifier.align(place), highlighted = seat == winner)
        }
    }
}

/** A column of short white lines on the felt. */
@Composable
internal fun FeltText(vararg lines: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach { Text(it, color = Color.White) }
    }
}

private val HAND_CARD_WIDTH = 54.dp
private val TRICK_CARD_WIDTH = 46.dp
private const val CARD_RATIO = 1.4f
private const val DIMMED = 0.45f
private const val TRICK_WIDTH_IN_CARDS = 3.5f
private const val TRICK_HEIGHT_IN_CARDS = 2.3f
internal val RED_SUIT = Color(0xFFC62828)
internal val HIGHLIGHT = Color(0xFFFFC107)
