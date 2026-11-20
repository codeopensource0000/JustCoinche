package justcoinche.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.engine.Bid
import justcoinche.engine.ContractType
import justcoinche.engine.Suit

/**
 * The human's turn to speak: the arrows choose the value, then a suit tile
 * (or SA, TA) makes the bid at that value. Only legal bids can be made: the
 * engine gives the list, the panel never decides what is allowed.
 */
@Composable
internal fun BiddingPanel(legalBids: List<Bid>, onBid: (Bid) -> Unit) {
    val levels = remember(legalBids) { bidLevels(legalBids) }
    var index by remember(legalBids) { mutableIntStateOf(0) }
    Panel(highlighted = true) {
        if (levels.isNotEmpty()) {
            val level = levels[index]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Arrow("◀", enabled = index > 0) { index-- }
                Text(
                    level.label,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(140.dp),
                )
                Arrow("▶", enabled = index < levels.lastIndex) { index++ }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BID_TYPES.forEach { type -> TypeTile(type, level.bid(type), legalBids, onBid) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Bid.Coinche in legalBids) ActionButton("Coincher", COINCHE) { onBid(Bid.Coinche) }
            if (Bid.Surcoinche in legalBids) ActionButton("Surcoincher", COINCHE) { onBid(Bid.Surcoinche) }
            if (Bid.Pass in legalBids) ActionButton("Passer", PASS) { onBid(Bid.Pass) }
        }
    }
}

@Composable
private fun Arrow(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, fontSize = 20.sp, color = if (enabled) GOLD else DIM_TEXT)
    }
}

/** A white tile with the suit, as on a card; greyed when the bid is not allowed. */
@Composable
private fun TypeTile(type: ContractType, bid: Bid, legalBids: List<Bid>, onBid: (Bid) -> Unit) {
    val red = type is ContractType.SuitTrump && type.trump in RED_SUITS
    Button(
        onClick = { onBid(bid) },
        enabled = bid in legalBids,
        modifier = Modifier.size(width = 56.dp, height = 40.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, disabledContainerColor = DISABLED_TILE),
    ) {
        Text(
            type.label,
            fontSize = if (type is ContractType.SuitTrump) 24.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (red) RED_SUIT else Color.Black,
        )
    }
}

@Composable
private fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(text) }
}

private val RED_SUITS = setOf(Suit.HEARTS, Suit.DIAMONDS)
private val COINCHE = Color(0xFFC62828)
private val PASS = Color(0xFF455A64)
private val DISABLED_TILE = Color(0x33FFFFFF)
