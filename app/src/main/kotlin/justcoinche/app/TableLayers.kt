package justcoinche.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Seat
import justcoinche.engine.Trick
import kotlinx.coroutines.delay

/**
 * The human's hand, fanned along the bottom. On their turn the cards they may
 * play rise and can be tapped; the others are greyed. When a card leaves, the
 * others close the gap smoothly.
 */
@Composable
internal fun HandFan(
    cards: List<Card>,
    playable: CardSet,
    geometry: TableGeometry,
    onPlay: (Card) -> Unit,
) {
    val myTurn = !playable.isEmpty()
    cards.forEachIndexed { index, card ->
        key(card) {
            val slot = geometry.handSlot(index, cards.size)
            val canPlay = card in playable
            val x by animateDpAsState(slot.left, tween(MOVE_MS), label = "hand card x")
            val top = if (canPlay) slot.top - geometry.lift else slot.top
            val y by animateDpAsState(top, tween(MOVE_MS), label = "lift")
            // A gentle fan: the cards turn a little around their bottom edge.
            val angle = (index - (cards.size - 1) / 2f) * FAN_DEGREES
            CardFace(
                card,
                geometry.handCard,
                Modifier.offset(x, y)
                    .graphicsLayer {
                        rotationZ = angle
                        transformOrigin = BOTTOM_CENTRE
                    }
                    .clickable(enabled = canPlay) { onPlay(card) },
                dimmed = myTurn && !canPlay,
            )
        }
    }
}

/** The backs of the cards an opponent still holds: upright for North, sideways for West and East. */
@Composable
internal fun OpponentBacks(seat: Seat, count: Int, geometry: TableGeometry) {
    repeat(count) { index ->
        val slot = geometry.backSlot(seat, index, count)
        CardBack(DpSize(slot.width, slot.height), Modifier.offset(slot.left, slot.top))
    }
}

/**
 * The trick in the middle. Each card glides from its player to its place;
 * once the trick is complete, the winning card is ringed in gold, then the
 * whole trick slides to the winner and fades away.
 */
@Composable
internal fun TrickLayer(trick: Trick, type: ContractType, humanHand: CardSet, geometry: TableGeometry) {
    val winner = if (trick.isComplete) trick.winner(type) else null
    // A new state for every trick: the next trick's cards must not start out gathered.
    var gathered by remember(trick) { mutableStateOf(false) }
    LaunchedEffect(trick) {
        if (trick.isComplete) {
            delay(GATHER_DELAY_MS)
            gathered = true
        }
    }
    trick.cards.forEachIndexed { index, card ->
        val seat = Seat.entries[(trick.leader.ordinal + index) % Seat.entries.size]
        key(card) {
            val start = remember { origin(card, seat, humanHand, type, geometry) }
            val target = if (gathered && winner != null) {
                geometry.centredOn(geometry.anchor(winner), geometry.trickCard)
            } else {
                geometry.trickSlot(seat).topLeft
            }
            val position = remember { Animatable(start, DpOffset.VectorConverter) }
            LaunchedEffect(target) { position.animateTo(target, tween(if (gathered) GATHER_MS else MOVE_MS)) }
            val alpha by animateFloatAsState(if (gathered) 0f else 1f, tween(GATHER_MS), label = "gather")
            CardFace(
                card,
                geometry.trickCard,
                Modifier.offset(position.value.x, position.value.y).graphicsLayer {
                    this.alpha = alpha
                    rotationZ = TILT.getValue(seat)
                },
                highlighted = seat == winner && !gathered,
            )
        }
    }
}

/** Where a card played by [seat] comes from: its place in the human's hand, or the opponent's cards. */
private fun origin(card: Card, seat: Seat, humanHand: CardSet, type: ContractType, geometry: TableGeometry): DpOffset =
    if (seat == HUMAN) {
        val before = sortedForDisplay(humanHand + card, type)
        geometry.handSlot(before.indexOf(card), before.size).topLeft
    } else {
        geometry.centredOn(geometry.anchor(seat), geometry.trickCard)
    }

/** Each seat's card lies slightly askew, as if tossed on the table. */
private val TILT = mapOf(Seat.SOUTH to 2f, Seat.WEST to -6f, Seat.NORTH to -2f, Seat.EAST to 6f)

private val BOTTOM_CENTRE = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
internal const val MOVE_MS = 300
private const val GATHER_MS = 450
private const val GATHER_DELAY_MS = 900L
private const val FAN_DEGREES = 2.5f
