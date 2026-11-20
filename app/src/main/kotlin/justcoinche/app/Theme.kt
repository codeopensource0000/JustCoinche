package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The table's look: a green felt, dark translucent panels edged with gold.

internal val FELT: Brush = Brush.radialGradient(listOf(Color(0xFF2F8F5B), Color(0xFF0E4A2B)))
internal val PANEL = Color(0xE60F1D16)
private val PANEL_EDGE = Color(0x59F5C542)

/** A dark rounded panel on the felt; [highlighted] edges it with bright gold, for the player to act. */
@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(14.dp),
        color = PANEL,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) GOLD else PANEL_EDGE),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** One line of white text on a panel or on the felt. */
@Composable
internal fun FeltText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 13.sp,
    bold: Boolean = false,
    color: Color = Color.White,
) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = size,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Places an element exactly on an [Area] of the table. */
internal fun Modifier.place(area: Area): Modifier = offset(area.left, area.top).size(area.width, area.height)

internal val DIM_TEXT = Color(0xB3FFFFFF)
