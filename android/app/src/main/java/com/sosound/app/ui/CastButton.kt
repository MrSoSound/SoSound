package com.sosound.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.sosound.app.cast.StatoCast
import com.sosound.app.ui.theme.Vetro

/**
 * Il tasto per trasmettere.
 *
 * È quello del Cast SDK e non uno nostro: porta con se' il selettore dei
 * dispositivi, la ricerca sulla rete e l'animazione di connessione —
 * tutte cose che rifatte a mano si comporterebbero in modo leggermente
 * diverso da quello che la gente ha imparato su ogni altra app.
 *
 * Compare solo quando c'e' qualcosa a cui trasmettere: un tasto che non
 * fa niente e' peggio di un tasto che non c'e'.
 */
@Composable
fun CastButton(vm: MainViewModel, tinta: Color = Vetro.InkSoft) {
    val stato by vm.castStato.collectAsState()
    if (stato == StatoCast.NON_DISPONIBILE || stato == StatoCast.NESSUN_DISPOSITIVO) return

    val connesso = stato == StatoCast.CONNESSO
    val colore = (if (connesso) Vetro.Accent else tinta).toArgb()

    AndroidView(
        modifier = Modifier.size(48.dp),
        factory = { ctx ->
            MediaRouteButton(ctx).also { bottone ->
                CastButtonFactory.setUpMediaRouteButton(ctx, bottone)
            }
        },
        update = { bottone ->
            bottone.setRemoteIndicatorDrawable(
                bottone.context.getDrawable(
                    androidx.mediarouter.R.drawable.mr_button_light
                )?.apply { setTint(colore) }
            )
        },
    )
}
