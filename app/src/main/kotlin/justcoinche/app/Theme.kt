package justcoinche.app

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// The table's look, all drawn in code: a green felt with a fine grain and
// darker edges, names engraved in it, dark pills for what is said, and a
// wooden side panel for the menu.

/** Fine random grain, tiled over the felt, the wood and the cards' paper. */
@Composable
internal fun rememberGrain(): ImageBitmap = remember {
    val random = Random(GRAIN_SEED)
    val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE) {
        val shade = random.nextInt(GRAIN_SHADES)
        // Grey grain with a low alpha: it darkens or lightens what is under it a little.
        (random.nextInt(GRAIN_ALPHA) shl ALPHA_SHIFT) or (shade shl RED_SHIFT) or (shade shl GREEN_SHIFT) or shade
    }
    Bitmap.createBitmap(pixels, GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Broad soft clouds, a little lighter or darker, stretched over the whole
 * felt: the uneven nap of a real cloth.
 */
@Composable
private fun rememberMottle(): ImageBitmap = remember {
    val random = Random(MOTTLE_SEED)
    val pixels = IntArray(MOTTLE_WIDTH * MOTTLE_HEIGHT) {
        val shade = if (random.nextBoolean()) LIGHT_SHADE else 0
        (random.nextInt(MOTTLE_ALPHA) shl ALPHA_SHIFT) or (shade shl RED_SHIFT) or (shade shl GREEN_SHIFT) or shade
    }
    Bitmap.createBitmap(pixels, MOTTLE_WIDTH, MOTTLE_HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The felt of a card room: deep green, lit from the middle and dark at the
 * edges, with soft clouds in the nap, a fine grain and a stitched border.
 */
@Composable
internal fun Felt(content: @Composable BoxScope.() -> Unit) {
    val grain = rememberGrain()
    val mottle = rememberMottle()
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(FELT_LIGHT, FELT_DARK), radius = size.maxDimension * 0.62f))
            // Stretched with smoothing, the few pixels of the mottle become broad clouds.
            drawImage(
                mottle,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
            // The grain twice: the cloth is coarser than the cards' paper, which has it once.
            val fibres = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
            repeat(2) { drawRect(fibres) }
            val inset = 14.dp.toPx()
            drawRoundRect(
                FELT_SEAM,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius(18.dp.toPx()),
                style = Stroke(1.5.dp.toPx()),
            )
        },
        content = content,
    )
}

/** A name engraved in the felt: darker than it, with a light edge below; gold when it is that player's turn. */
@Composable
internal fun Engraved(text: String, modifier: Modifier = Modifier, size: TextUnit = 17.sp, lit: Boolean = false) {
    Text(
        text,
        modifier,
        maxLines = 1,
        style = TextStyle(
            color = if (lit) GOLD else ENGRAVED,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(if (lit) LIT_SHADOW else ENGRAVED_EDGE, Offset(0f, 2f), 1f),
        ),
    )
}

/** A dark pill with white text, for a bid or a short message on the felt. */
@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, gold: Boolean = false) {
    Surface(modifier, shape = CircleShape, color = PILL) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            color = if (gold) GOLD else Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The dealer's token: a red and white casino chip marked D. */
@Composable
@Suppress("MagicNumber") // a drawing: each number is a proportion of the chip
internal fun DealerChip(modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(CHIP_RED, r)
            repeat(CHIP_NOTCHES) { i ->
                val angle = Math.toRadians(i * 360.0 / CHIP_NOTCHES)
                val rim = r * 0.84f
                val point = center + Offset((rim * cos(angle)).toFloat(), (rim * sin(angle)).toFloat())
                drawCircle(Color.White, r * 0.13f, point)
            }
            drawCircle(Color.White, r * 0.62f)
            drawCircle(CHIP_RED, r * 0.62f, style = Stroke(r * 0.08f))
        }
        Text("D", color = CHIP_RED, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

/** A dark rounded panel over the felt, for the bidding. */
@Composable
internal fun DarkPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = PILL, shadowElevation = 8.dp) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** A light panel, like a sheet of paper, for results and the menu's pages. */
@Composable
internal fun LightPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(18.dp),
        color = PAPER,
        border = BorderStroke(1.dp, PAPER_EDGE),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** A wooden board, for the side menu: warm stripes along its length and the same grain. */
@Composable
@Suppress("MagicNumber") // a drawing: each number is a shade or a stroke width of the grain
internal fun Wood(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val grain = rememberGrain()
    val stripes = remember {
        val random = Random(WOOD_SEED)
        List(WOOD_STRIPES) { random.nextFloat() to random.nextFloat() }
    }
    Box(
        modifier.drawBehind {
            drawRect(WOOD)
            stripes.forEach { (position, strength) ->
                val x = position * size.width
                val stripe = WOOD_DARK.copy(alpha = 0.15f + 0.35f * strength)
                drawLine(stripe, Offset(x, 0f), Offset(x, size.height), 1.5f + 5f * strength)
            }
            drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
        },
        content = content,
    )
}

/** Places an element exactly on an [Area] of the table. */
internal fun Modifier.place(area: Area): Modifier = offset(area.left, area.top).size(area.width, area.height)

internal val GOLD = Color(0xFFF5C542)
internal val PAPER = Color(0xFFF1EDE8)
internal val INK = Color(0xFF3B3632)
internal val PILL = Color(0xF0161A17)
private val FELT_LIGHT = Color(0xFF0D6532)
private val FELT_DARK = Color(0xFF032A14)
private val FELT_SEAM = Color(0x33000000)
private val ENGRAVED = Color(0xFF01180A)
private val ENGRAVED_EDGE = Color(0x40FFFFFF)
private val CHIP_RED = Color(0xFFD7262B)
private val LIT_SHADOW = Color(0x99000000)
private val PAPER_EDGE = Color(0x22000000)
private val WOOD = Color(0xFFA9622F)
private val WOOD_DARK = Color(0xFF5A2E12)
private const val GRAIN_SEED = 7
private const val WOOD_SEED = 11
private const val MOTTLE_SEED = 5
private const val MOTTLE_WIDTH = 40
private const val MOTTLE_HEIGHT = 20
private const val MOTTLE_ALPHA = 12
private const val LIGHT_SHADE = 255
private const val GRAIN_SIZE = 160
private const val GRAIN_SHADES = 256
private const val GRAIN_ALPHA = 22
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHIP_NOTCHES = 8
private const val WOOD_STRIPES = 70
