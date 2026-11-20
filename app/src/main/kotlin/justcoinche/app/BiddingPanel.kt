package justcoinche.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import justcoinche.engine.Bid
import justcoinche.engine.ContractType
import justcoinche.engine.Suit

/**
 * The human's turn to speak: arrows choose the value, then a suit button (or
 * SA, TA) makes the bid at that value. Only legal bids can be made: the engine
 * gives the list, the panel never decides what is allowed.
 */
@Composable
internal fun BiddingPanel(legalBids: List<Bid>, onBid: (Bid) -> Unit) {
    val levels = remember(legalBids) { bidLevels(legalBids) }
    var index by remember(legalBids) { mutableIntStateOf(0) }
    Surface(shape = RoundedCornerShape(12.dp), color = PANEL) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (levels.isNotEmpty()) {
                val level = levels[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { index-- }, enabled = index > 0) { Text("◀") }
                    Text(
                        level.label,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(110.dp),
                    )
                    TextButton(onClick = { index++ }, enabled = index < levels.lastIndex) { Text("▶") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BID_TYPES.forEach { type -> TypeButton(type, level.bid(type), legalBids, onBid) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Bid.Coinche to "Coincher", Bid.Surcoinche to "Surcoincher", Bid.Pass to "Passer")
                    .filter { (bid, _) -> bid in legalBids }
                    .forEach { (bid, text) -> Button(onClick = { onBid(bid) }) { Text(text) } }
            }
        }
    }
}

@Composable
private fun TypeButton(type: ContractType, bid: Bid, legalBids: List<Bid>, onBid: (Bid) -> Unit) {
    val red = type is ContractType.SuitTrump && (type.trump in RED_SUITS)
    FilledTonalButton(
        onClick = { onBid(bid) },
        enabled = bid in legalBids,
        modifier = Modifier.width(58.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(type.label, color = if (red) RED_SUIT else Color.Unspecified)
    }
}

private val RED_SUITS = setOf(Suit.HEARTS, Suit.DIAMONDS)
internal val PANEL = Color(0xE6202020)
