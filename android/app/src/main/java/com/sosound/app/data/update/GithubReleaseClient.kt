package com.sosound.app.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Parla con l'unico endpoint che serve: l'ultima release pubblicata su
 * GitHub. Pubblico, nessun token — lo stesso che chiunque vede aprendo
 * la pagina delle Release nel browser.
 */
class GithubReleaseClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp) { expectSuccess = false }

    suspend fun latestRelease(): GithubRelease = withContext(Dispatchers.IO) {
        val response = http.get("$BASE/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GitHub ha risposto ${response.status.value}")
        }
        json.decodeFromString(GithubRelease.serializer(), response.bodyAsText())
    }

    fun close() = http.close()

    private companion object {
        const val BASE = "https://api.github.com/repos/MrSoSound/SoSound"
    }
}
