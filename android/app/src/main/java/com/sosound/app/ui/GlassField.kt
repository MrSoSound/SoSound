package com.sosound.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass

/**
 * Il campo di ricerca in vetro.
 *
 * BasicTextField invece di OutlinedTextField: quello di Material porta
 * con se' un contenitore opaco e un'etichetta fluttuante che qui
 * romperebbero l'effetto: si vedrebbe un rettangolo grigio sopra la
 * copertina sfocata.
 */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .glass(strong = true)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Search, null,
            tint = Vetro.InkFaint,
            modifier = Modifier.size(20.dp),
        )

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vetro.InkFaint,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyMedium
                ).copy(color = Vetro.Ink),
                cursorBrush = SolidColor(Vetro.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }

        // Il tasto per svuotare compare solo quando c'e' qualcosa da
        // svuotare: altrimenti sarebbe un bersaglio morto accanto al dito.
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close, "Cancella",
                    tint = Vetro.InkFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
