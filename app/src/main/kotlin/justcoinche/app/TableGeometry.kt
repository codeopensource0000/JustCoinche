package justcoinche.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import justcoinche.engine.Seat

/** A rectangle on the table, in dp from the top-left corner. */
internal data class Area(val left: Dp, val top: Dp, val width: Dp, val height: Dp) {
    val right: Dp get() = left + width
    val bottom: Dp get() = top + height
    val topLeft: DpOffset get() = DpOffset(left, top)
    val centre: DpOffset get() = DpOffset(left + width / 2, top + height / 2)

    fun overlaps(other: Area): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun isInside(width: Dp, height: Dp): Boolean = left >= 0.dp && top >= 0.dp && right <= width && bottom <= height
}

/**
 * Where everything sits on a landscape table of [width] × [height]. The cards
 * take most of the screen: the human's hand runs off the bottom edge, as if
 * held, and the trick fills the middle. Names are engraved in the felt around.
 * Sizes follow the screen's height, so that the table looks the same on any
 * phone. A plain calculation, unit tested on several screen sizes.
 */
internal class TableGeometry(val width: Dp, val height: Dp) {

    /** The human's cards: only their upper part shows above the bottom edge. */
    val handCard = DpSize(height * HAND_CARD_HEIGHT / CARD_RATIO, height * HAND_CARD_HEIGHT)

    /** The cards in the middle of the table; a little smaller on narrower screens, to leave room for the score. */
    val trickCard: DpSize = minOf(height * TRICK_CARD_HEIGHT, width * TRICK_CARD_MAX_WIDTH_SHARE)
        .let { h -> DpSize(h / CARD_RATIO, h) }

    /** How far a playable card rises when it is the human's turn. */
    val lift: Dp = handCard.height * LIFT

    val centerX: Dp = width / 2

    /** Where the hand's cards start, before any lift: the rest of each card is below the screen. */
    private val handTop: Dp = height - handCard.height * HAND_SHOWN

    /** The middle of the trick, between the top of the table and the lifted hand. */
    val centerY: Dp = (MARGIN + handTop - lift - GAP) / 2

    /** Where each seat's card lies in the trick: a cross of overlapping cards. */
    fun trickSlot(seat: Seat): Area {
        val (w, h) = trickCard
        val left = when (seat) {
            Seat.NORTH, Seat.SOUTH -> centerX - w / 2
            Seat.WEST -> centerX - w / 2 - w * TRICK_SPREAD_X
            Seat.EAST -> centerX - w / 2 + w * TRICK_SPREAD_X
        }
        val top = when (seat) {
            Seat.NORTH -> centerY - h / 2 - h * TRICK_SPREAD_Y
            Seat.SOUTH -> centerY - h / 2 + h * TRICK_SPREAD_Y
            Seat.WEST, Seat.EAST -> centerY - h / 2
        }
        return Area(left, top, w, h)
    }

    /** The space taken by the whole trick. */
    val trickArea: Area
        get() {
            val slots = Seat.entries.map { trickSlot(it) }
            val left = slots.minOf { it.left }
            val top = slots.minOf { it.top }
            return Area(left, top, slots.maxOf { it.right } - left, slots.maxOf { it.bottom } - top)
        }

    /** The [index]-th of [count] cards in the human's hand, before any lift (it runs off the screen). */
    fun handSlot(index: Int, count: Int): Area {
        val step = handStep(count)
        val total = step * (count - 1).coerceAtLeast(0) + handCard.width
        return Area(centerX - total / 2 + step * index, handTop, handCard.width, handCard.height)
    }

    /** The visible part of a full hand, lifted cards included. */
    val handArea: Area
        get() {
            val first = handSlot(0, FULL_HAND)
            val last = handSlot(FULL_HAND - 1, FULL_HAND)
            return Area(first.left, handTop - lift, last.right - first.left, height - handTop + lift)
        }

    private fun handStep(count: Int): Dp = if (count <= 1) {
        0.dp
    } else {
        minOf(handCard.width * HAND_OVERLAP, (width * HAND_MAX_WIDTH - handCard.width) / (count - 1))
    }

    /**
     * A player's name engraved in the felt, with the pill of their last bid
     * under it: North beside the trick, West and East on the sides, the human
     * left of their hand.
     */
    fun nameTag(seat: Seat): Area {
        val (x, y) = when (seat) {
            Seat.NORTH -> trickArea.right + TAG_SPACE + TAG.width / 2 to MARGIN + TAG.height / 2
            Seat.WEST -> width * SIDE_X to centerY
            Seat.EAST -> width * (1 - SIDE_X) to centerY
            Seat.SOUTH -> southTagCentre()
        }
        return Area(x - TAG.width / 2, y - TAG.height / 2, TAG.width, TAG.height)
    }

    /** Left of the hand when there is room for it, else above the hand's left end. */
    private fun southTagCentre(): Pair<Dp, Dp> {
        val besideHand = handArea.left - TAG_SPACE - TAG.width / 2
        return if (besideHand - TAG.width / 2 >= MARGIN) {
            besideHand to height - TAG.height / 2 - MARGIN
        } else {
            MARGIN + TAG.width / 2 to handArea.top - GAP - TAG.height / 2
        }
    }

    /** Where a seat's cards come from and where the tricks it wins go. */
    fun anchor(seat: Seat): DpOffset =
        if (seat == Seat.SOUTH) DpOffset(centerX, height) else nameTag(seat).centre

    /** The menu button, top left. */
    val menuButton: Area get() = Area(MARGIN, MARGIN, MENU.width, MENU.height)

    /** The game score, beside the menu button. */
    val scoreLine: Area get() = Area(menuButton.right + GAP, MARGIN, SCORE.width, SCORE.height)

    /**
     * The free middle of the table, between the side players and above the
     * hand: the bidding panel and the deal's result are drawn there.
     */
    val middle: Area
        get() {
            val left = nameTag(Seat.WEST).right + GAP
            val top = scoreLine.bottom + GAP
            return Area(left, top, nameTag(Seat.EAST).left - GAP - left, handArea.top - GAP - top)
        }

    /** The top-left corner that puts a card of [size] centred on [point]. */
    fun centredOn(point: DpOffset, size: DpSize): DpOffset =
        DpOffset(point.x - size.width / 2, point.y - size.height / 2)

    companion object {
        const val CARD_RATIO = 1.42f
        const val FULL_HAND = 8
        val MARGIN = 8.dp
        private val GAP = 4.dp
        private val TAG_SPACE = 16.dp
        val TAG = DpSize(112.dp, 64.dp)
        private val MENU = DpSize(48.dp, 48.dp)
        private val SCORE = DpSize(150.dp, 48.dp)
        private const val HAND_CARD_HEIGHT = 0.44f
        private const val HAND_SHOWN = 0.70f
        private const val TRICK_CARD_HEIGHT = 0.39f
        private const val TRICK_CARD_MAX_WIDTH_SHARE = 0.20f
        private const val LIFT = 0.10f
        private const val HAND_OVERLAP = 0.56f
        private const val HAND_MAX_WIDTH = 0.70f
        private const val TRICK_SPREAD_X = 0.72f
        private const val TRICK_SPREAD_Y = 0.22f
        private const val SIDE_X = 0.10f
    }
}
