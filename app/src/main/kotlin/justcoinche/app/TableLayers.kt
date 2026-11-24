package justcoinche.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Seat
import justcoinche.engine.Trick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The human's hand, fanned along the bottom. When a deal starts, the cards
 * rise from the bottom edge one after the other. On the human's turn the
 * cards they may play rise and can be tapped; the others are greyed. When a
 * card leaves, the others close the gap smoothly.
 */
@Composable
internal fun HandFan(
    cards: List<Card>,
    playable: CardSet,
    geometry: TableGeometry,
    timing: Timing,
    onPlay: (Card) -> Unit,
) {
    val myTurn = !playable.isEmpty()
    cards.forEachIndexed { index, card ->
        key(card) {
            val slot = geometry.handSlot(index, cards.size)
            val canPlay = card in playable
            val closeGap = tween<Dp>(timing.closeGapMs, easing = FastOutSlowInEasing)
            val x by animateDpAsState(slot.left, closeGap, label = "hand x")
            val top = if (canPlay) slot.top - geometry.lift else slot.top
            // A card starts below the screen: it rises when dealt, then follows its lift.
            val y = remember { Animatable(geometry.height, Dp.VectorConverter) }
            LaunchedEffect(top) {
                if (y.value >= geometry.height) delay(index * timing.dealStaggerMs)
                y.animateTo(top, tween(timing.riseMs, easing = FastOutSlowInEasing))
            }
            // A very gentle fan: the cards turn a little around their bottom edge.
            val angle = (index - (cards.size - 1) / 2f) * FAN_DEGREES
            CardFace(
                card,
                geometry.handCard,
                Modifier.offset { IntOffset(x.roundToPx(), y.value.roundToPx()) }
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

/**
 * Where a card is on its way, and how: its centre, its size compared with a
 * trick card, its turn in degrees.
 */
private data class Pose(val centre: DpOffset, val scale: Float, val turn: Float)

/**
 * The trick in the middle. Each card glides from its player to its place,
 * growing or shrinking to the size of the trick and turning a little as it
 * goes; an opponent's card fades in on the way, as if laid down. Once the
 * trick is complete, the winning card is ringed in gold, then the whole trick
 * slides to the winner, shrinking and fading away.
 */
@Composable
internal fun TrickLayer(
    trick: Trick,
    type: ContractType,
    humanHand: CardSet,
    geometry: TableGeometry,
    timing: Timing,
) {
    val winner = if (trick.isComplete) trick.winner(type) else null
    // A new state for every trick: the next trick's cards must not start out gathered.
    var gathered by remember(trick) { mutableStateOf(false) }
    LaunchedEffect(trick) {
        if (trick.isComplete) {
            delay(timing.gatherDelayMs)
            gathered = true
        }
    }
    trick.cards.forEachIndexed { index, card ->
        val seat = Seat.entries[(trick.leader.ordinal + index) % Seat.entries.size]
        key(card) {
            val start = remember { origin(card, seat, humanHand, type, geometry) }
            val target = if (gathered && winner != null) {
                Pose(geometry.anchor(winner), GATHERED_SCALE, TILT.getValue(seat))
            } else {
                Pose(geometry.trickSlot(seat).centre, 1f, TILT.getValue(seat))
            }
            val centre = remember { Animatable(start.centre, DpOffset.VectorConverter) }
            val scale = remember { Animatable(start.scale) }
            val turn = remember { Animatable(start.turn) }
            val alpha = remember { Animatable(if (seat == HUMAN) 1f else 0f) }
            LaunchedEffect(target) {
                launch { centre.animateTo(target.centre, timing.motion(gathered)) }
                launch { scale.animateTo(target.scale, timing.motion(gathered)) }
                launch { turn.animateTo(target.turn, timing.motion(gathered)) }
                alpha.animateTo(if (gathered) 0f else 1f, tween(if (gathered) timing.gatherMs else timing.fadeInMs))
            }
            val size = geometry.trickCard
            CardFace(
                card,
                size,
                Modifier
                    .offset {
                        IntOffset(
                            (centre.value.x - size.width / 2).roundToPx(),
                            (centre.value.y - size.height / 2).roundToPx(),
                        )
                    }
                    .graphicsLayer {
                        this.alpha = alpha.value
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = turn.value
                    },
                highlighted = seat == winner && !gathered,
            )
        }
    }
}

/** A card played glides in, slowing down as it lands; a gathered trick speeds up as it leaves. */
private fun <T> Timing.motion(gathered: Boolean): AnimationSpec<T> =
    if (gathered) tween(gatherMs, easing = FastOutLinearInEasing) else tween(playMs, easing = FastOutSlowInEasing)

/** Where a card played by [seat] starts: its place in the human's hand, or small, at the opponent's name. */
private fun origin(card: Card, seat: Seat, humanHand: CardSet, type: ContractType, geometry: TableGeometry): Pose =
    if (seat == HUMAN) {
        val before = sortedForDisplay(humanHand + card, type)
        val slot = geometry.handSlot(before.indexOf(card), before.size)
        Pose(slot.centre, geometry.handCard.height / geometry.trickCard.height, 0f)
    } else {
        Pose(geometry.anchor(seat), OPPONENT_START_SCALE, 0f)
    }

/** Each seat's card lies slightly askew, as if tossed on the table. */
private val TILT = mapOf(Seat.SOUTH to 2f, Seat.WEST to -6f, Seat.NORTH to -2f, Seat.EAST to 6f)

private val BOTTOM_CENTRE = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
private const val OPPONENT_START_SCALE = 0.55f
private const val GATHERED_SCALE = 0.45f
private const val FAN_DEGREES = 1.2f
