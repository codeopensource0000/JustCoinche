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

    fun overlaps(other: Area): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun isInside(width: Dp, height: Dp): Boolean = left >= 0.dp && top >= 0.dp && right <= width && bottom <= height
}

/**
 * Where everything sits on a landscape table of [width] × [height]: all sizes
 * follow the screen's height, so that the table looks the same on any phone.
 * A plain calculation, unit tested on several screen sizes.
 */
internal class TableGeometry(val width: Dp, val height: Dp) {

    /** The human's cards: the largest, for comfortable reading and tapping. */
    val handCard = DpSize(height * HAND_CARD_HEIGHT / CARD_RATIO, height * HAND_CARD_HEIGHT)

    /** The cards in the middle of the table. */
    val trickCard = DpSize(height * TRICK_CARD_HEIGHT / CARD_RATIO, height * TRICK_CARD_HEIGHT)

    /** The backs of the opponents' cards, upright for North. West and East hold theirs sideways. */
    val backCard = DpSize(height * BACK_CARD_HEIGHT / CARD_RATIO, height * BACK_CARD_HEIGHT)

    /** How far a playable card rises when it is the human's turn. */
    val lift: Dp = handCard.height * LIFT

    val centerX: Dp = width / 2

    /** The top of the human's cards, before any lift. */
    private val handTop: Dp = height - handCard.height - MARGIN

    /** The middle of the trick, between North's cards and the human's hand. */
    val centerY: Dp = (MARGIN + backCard.height + handTop - lift) / 2

    /** Where each seat's card lies in the trick. */
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

    /** The [index]-th of [count] cards in the human's hand, before any lift. */
    fun handSlot(index: Int, count: Int): Area {
        val step = handStep(count)
        val total = step * (count - 1).coerceAtLeast(0) + handCard.width
        return Area(centerX - total / 2 + step * index, handTop, handCard.width, handCard.height)
    }

    /** The space taken by a full hand, lifted cards included. */
    val handArea: Area
        get() {
            val first = handSlot(0, FULL_HAND)
            val last = handSlot(FULL_HAND - 1, FULL_HAND)
            return Area(first.left, first.top - lift, last.right - first.left, first.height + lift)
        }

    private fun handStep(count: Int): Dp = if (count <= 1) {
        0.dp
    } else {
        minOf(handCard.width * HAND_OVERLAP, (width * HAND_MAX_WIDTH - handCard.width) / (count - 1))
    }

    /** The [index]-th of [count] backs in front of an opponent. */
    fun backSlot(seat: Seat, index: Int, count: Int): Area {
        val (w, h) = backCard
        return when (seat) {
            Seat.NORTH -> {
                val step = w * BACK_OVERLAP
                val total = step * (count - 1).coerceAtLeast(0) + w
                Area(centerX - total / 2 + step * index, MARGIN, w, h)
            }
            Seat.WEST, Seat.EAST -> {
                // Held sideways: the card is as wide as an upright one is tall.
                val step = w * BACK_OVERLAP
                val full = step * (FULL_HAND - 1) + w
                val total = step * (count - 1).coerceAtLeast(0) + w
                // Centred beside the trick, but always below the score board.
                val top = maxOf(centerY - full / 2, MARGIN + SCORE_BOARD.height + GAP) + (full - total) / 2
                val left = if (seat == Seat.WEST) MARGIN else width - MARGIN - h
                Area(left, top + step * index, h, w)
            }
            Seat.SOUTH -> handSlot(index, count)
        }
    }

    /** The space taken by a full set of backs in front of an opponent. */
    fun backArea(seat: Seat): Area {
        val first = backSlot(seat, 0, FULL_HAND)
        val last = backSlot(seat, FULL_HAND - 1, FULL_HAND)
        return Area(first.left, first.top, last.right - first.left, last.bottom - first.top)
    }

    /** Where a seat's cards come from and where the tricks it wins go: the middle of its hand. */
    fun anchor(seat: Seat): DpOffset {
        val area = if (seat == Seat.SOUTH) handArea else backArea(seat)
        return DpOffset(area.left + area.width / 2, area.top + area.height / 2)
    }

    /** A player's name tag: beside North's cards, under West's and East's, in the corner for the human. */
    fun nameTag(seat: Seat): Area = when (seat) {
        Seat.NORTH -> Area(backArea(Seat.NORTH).right + GAP, MARGIN, TAG.width, TAG.height)
        Seat.WEST -> Area(MARGIN, backArea(Seat.WEST).bottom + GAP, TAG.width, TAG.height)
        Seat.EAST -> Area(width - MARGIN - TAG.width, backArea(Seat.EAST).bottom + GAP, TAG.width, TAG.height)
        Seat.SOUTH -> Area(MARGIN, height - MARGIN - TAG.height, TAG.width, TAG.height)
    }

    /** The game score and the contract, top left. */
    val scoreBoard: Area get() = Area(MARGIN, MARGIN, SCORE_BOARD.width, SCORE_BOARD.height)

    /** The buttons (last trick, signal), top right. */
    val toolbar: Area get() = Area(width - MARGIN - TOOLBAR.width, MARGIN, TOOLBAR.width, TOOLBAR.height)

    /**
     * The free middle of the table, between the opponents' cards and above the
     * hand: the bidding panel and the deal's result are drawn there.
     */
    val middle: Area
        get() {
            val left = backArea(Seat.WEST).right + GAP
            val top = backArea(Seat.NORTH).bottom + GAP
            return Area(left, top, backArea(Seat.EAST).left - GAP - left, handArea.top - GAP - top)
        }

    /** The top-left corner that puts a card of [size] centred on [point]. */
    fun centredOn(point: DpOffset, size: DpSize): DpOffset =
        DpOffset(point.x - size.width / 2, point.y - size.height / 2)

    companion object {
        const val CARD_RATIO = 1.4f
        const val FULL_HAND = 8
        val MARGIN = 6.dp
        private val GAP = 4.dp
        val TAG = DpSize(104.dp, 50.dp)
        private val SCORE_BOARD = DpSize(176.dp, 88.dp)
        private val TOOLBAR = DpSize(116.dp, 80.dp)
        private const val HAND_CARD_HEIGHT = 0.29f
        private const val TRICK_CARD_HEIGHT = 0.21f
        private const val BACK_CARD_HEIGHT = 0.15f
        private const val LIFT = 0.12f
        private const val HAND_OVERLAP = 0.62f
        private const val HAND_MAX_WIDTH = 0.62f
        private const val BACK_OVERLAP = 0.28f
        private const val TRICK_SPREAD_X = 0.95f
        private const val TRICK_SPREAD_Y = 0.42f
    }
}
