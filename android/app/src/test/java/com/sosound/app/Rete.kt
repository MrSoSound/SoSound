package com.sosound.app

import org.junit.Assume

/**
 * I test che chiamano davvero YouTube Music e Spotify.
 *
 * Sono utili — prendono i cambiamenti di un'API che nessuno ci
 * annuncia, ed e' il modo in cui si sono scoperti quasi tutti i
 * difetti di parsing — ma non possono essere il cancello di una build.
 * Dipendono da un servizio che non controlliamo, e il catalogo cambia
 * da un paese all'altro: «supernova» fra i podcast di qui porta il
 * programma di Ale Cattelan, da un runner americano puo' non portare
 * niente. Una build rossa per quel motivo non dice niente sul codice,
 * e insegna solo a non fidarsi del rosso.
 *
 * Quindi: di serie si SALTANO, e si chiedono apposta.
 *
 *     ./gradlew test            solo i test deterministici
 *     ./gradlew test -Prete     anche quelli che escono in rete
 *
 * Saltati vuol dire «skipped», non «passati»: nel resoconto si vede
 * che non hanno girato.
 */
object Rete {

    val attiva: Boolean = System.getProperty("sosound.rete") == "si"

    /** Da chiamare in un `@Before`: ferma la classe se la rete non e' stata chiesta. */
    fun richiesta() = Assume.assumeTrue(
        "test di rete: si lanciano con ./gradlew test -Prete",
        attiva,
    )
}
