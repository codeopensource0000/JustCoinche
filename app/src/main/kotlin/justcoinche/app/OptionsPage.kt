package justcoinche.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import justcoinche.ai.AiCatalog

/** The computer players: one for the partner, one for both opponents, from the honest ones of the catalog. */
@Composable
internal fun OptionsPage(choice: AiChoice, onChoice: (AiChoice) -> Unit) {
    Text("Le changement compte dès le coup suivant.", color = INK.copy(alpha = 0.6f))
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AiColumn("Partenaire", choice.partner, Modifier.weight(1f)) { onChoice(choice.copy(partner = it)) }
        AiColumn("Adversaires", choice.opponents, Modifier.weight(1f)) { onChoice(choice.copy(opponents = it)) }
    }
}

@Composable
private fun AiColumn(title: String, selected: String, modifier: Modifier, onSelect: (String) -> Unit) {
    Column(modifier) {
        Text(title, color = INK, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        val description = AiCatalog.playable.firstOrNull { it.id == selected }?.description.orEmpty()
        Text(description, color = INK.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 14.sp, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(AiCatalog.playable) { entry ->
                val chosen = entry.id == selected
                Surface(
                    onClick = { onSelect(entry.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (chosen) PILL else Color.Transparent,
                ) {
                    Text(
                        "${entry.id} · ${entry.name}",
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = if (chosen) Color.White else INK,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
