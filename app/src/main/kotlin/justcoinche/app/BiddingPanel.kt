package justcoinche.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.engine.Bid
import justcoinche.engine.ContractType
import justcoinche.engine.Suit

/**
 * The human's turn to speak: the arrows choose the value, then a suit (or SA,
 * TA) makes the bid at that value. Only legal bids can be made: the engine
 * gives the list, the panel never decides what is allowed.
 */
@Composable
internal fun BiddingPanel(legalBids: List<Bid>, onBid: (Bid) -> Unit) {
    val levels = remember(legalBids) { bidLevels(legalBids) }
    var index by remember(legalBids) { mutableIntStateOf(0) }
    DarkPanel {
        if (levels.isNotEmpty()) {
            val level = levels[index]
            Row(verticalAlignment = Alignment.CenterVertically) {
                LightButton("◀", enabled = index > 0, width = 44.dp) { index-- }
                Text(
                    level.label,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp),
                )
                LightButton("▶", enabled = index < levels.lastIndex, width = 44.dp) { index++ }
            }
            Row(
                Modifier.height(46.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BID_TYPES.forEach { type ->
                    val bid = level.bid(type)
                    val red = type is ContractType.SuitTrump && type.trump in RED_SUITS
                    val size = if (type is ContractType.SuitTrump) SUIT_SIZE else KEY_SIZE
                    val look = KeyLook(size, if (red) RED_SUIT else Color.Black)
                    LightButton(type.label, enabled = bid in legalBids, width = 48.dp, look = look) { onBid(bid) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val red = KeyLook(color = RED_SUIT)
            if (Bid.Coinche in legalBids) {
                LightButton("Coincher", width = ACTION_WIDTH, look = red) { onBid(Bid.Coinche) }
            }
            if (Bid.Surcoinche in legalBids) {
                LightButton("Surcoincher", width = ACTION_WIDTH, look = red) { onBid(Bid.Surcoinche) }
            }
            if (Bid.Pass in legalBids) LightButton("Passer", width = ACTION_WIDTH) { onBid(Bid.Pass) }
        }
    }
}

/** The text size and colour of a key. */
private class KeyLook(val size: Int = KEY_SIZE, val color: Color = Color.Black)

/** A light grey pill on the dark panel, like a key. */
@Composable
private fun LightButton(
    text: String,
    enabled: Boolean = true,
    width: Dp,
    look: KeyLook = KeyLook(),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = width, height = 36.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KEY,
            contentColor = look.color,
            disabledContainerColor = KEY_DISABLED,
            disabledContentColor = KEY_DISABLED_TEXT,
        ),
    ) {
        Text(text, fontSize = look.size.sp, fontWeight = FontWeight.Medium)
    }
}

private val RED_SUITS = setOf(Suit.HEARTS, Suit.DIAMONDS)
private val KEY = Color(0xFFE6E6E6)
private val KEY_DISABLED = Color(0x2EE6E6E6)
private val KEY_DISABLED_TEXT = Color(0x66FFFFFF)
private val ACTION_WIDTH = 120.dp
private const val KEY_SIZE = 15
private const val SUIT_SIZE = 20
