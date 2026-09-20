package com.sosound.app.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** L'ultima release pubblica del progetto, cosi' come la restituisce
 *  l'API di GitHub (`/releases/latest`). */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    /** Le note di rilascio: quello che si scrive creando la release. */
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
)
