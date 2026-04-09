package com.example.reproductormusica.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.LruCache
import com.example.reproductormusica.R
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class FetchedTrackMetadata(
    val albumArtUriString: String?,
    val artistDisplayName: String?,
    val albumTitle: String?
)

/**
 * Cliente mejorado de MusicBrainz + Cover Art Archive.
 * - Limpia sufijos como " - Topic" o "VEVO" para mejorar la búsqueda.
 * - Realiza un segundo intento con consulta simplificada si no hay resultados.
 * - Verifica la existencia real de la portada antes de devolver la URL.
 * - Implementa caché en memoria y caché de archivos de portada.
 */
class MetadataFetcher private constructor(private val appContext: Context) {

    private val userAgent: String
        get() = appContext.getString(R.string.musicbrainz_user_agent)

    private val mbMutex = Mutex()
    @Volatile
    private var lastMusicBrainzRequestAt = 0L

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .build()
            chain.proceed(req)
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/ws/2/")
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val musicBrainzApi: MusicBrainzApi = retrofit.create(MusicBrainzApi::class.java)

    // Caché en memoria (resultado de la búsqueda)
    private val metadataCache = LruCache<String, CachedMeta>(200)

    // Directorio para guardar portadas descargadas (persistente)
    private val coverArtDir: File by lazy {
        File(appContext.filesDir, "covers").apply { mkdirs() }
    }

    private data class CachedMeta(
        val albumArtUriString: String?,
        val artistDisplayName: String?,
        val albumTitle: String?
    )

    /**
     * Obtiene metadatos y portada para una canción.
     * @param title  Título de la canción
     * @param artist Nombre del artista (puede venir de yt-dlp o de tags)
     * @param albumHint Nombre de álbum conocido (opcional)
     */
    suspend fun fetchTrackMetadata(
        title: String,
        artist: String,
        albumHint: String? = null
    ): FetchedTrackMetadata? {
        val t = cleanTitle(title)
        val a = cleanArtist(artist)
        if (t.isBlank() || a.isBlank()) return null
        if (!isNetworkAvailable()) return null

        val albumNorm = albumHint?.trim()?.takeIf { it.isNotBlank() }
        val cacheKey = cacheKey(t, a, albumNorm)
        metadataCache.get(cacheKey)?.let { c ->
            return FetchedTrackMetadata(
                albumArtUriString = c.albumArtUriString,
                artistDisplayName = c.artistDisplayName,
                albumTitle = c.albumTitle
            )
        }

        // Intento 1: consulta exacta con título y artista
        var response = trySearch(t, a, limit = 15)
        var recordings = response?.recordings.orEmpty()

        // Intento 2: si no hay resultados, relajar la búsqueda (solo título o artista)
        if (recordings.isEmpty()) {
            val relaxedQuery = buildRelaxedQuery(t, a)
            response = trySearchRaw(relaxedQuery, limit = 15)
            recordings = response?.recordings.orEmpty()
        }

        if (recordings.isEmpty()) return null

        // Elegir la mejor grabación (mayor score)
        val bestRecording = recordings.maxByOrNull { it.score ?: 0.0 } ?: return null

        val mbArtist = bestRecording.artistCredit?.firstOrNull()?.name?.trim()?.takeIf { it.isNotBlank() }
        val releases = bestRecording.releases.orEmpty()

        // Buscar portada entre los releases ordenados por relevancia
        val (artFile, chosenRelease) = findCoverArtAmongReleases(releases, albumNorm)

        val albumTitle = chosenRelease?.title?.trim()?.takeIf { it.isNotBlank() }
            ?: releases.firstOrNull()?.title?.trim()?.takeIf { it.isNotBlank() }

        val artUri = artFile?.let { Uri.fromFile(it).toString() }

        val result = FetchedTrackMetadata(
            albumArtUriString = artUri,
            artistDisplayName = mbArtist,
            albumTitle = albumTitle
        )
        metadataCache.put(
            cacheKey,
            CachedMeta(result.albumArtUriString, result.artistDisplayName, result.albumTitle)
        )
        return result
    }

    /** Limpia títulos de sufijos como (Official Video), [HD], etc. */
    private fun cleanTitle(raw: String): String {
        return raw.replace(Regex("\\(.*?(Official|Audio|Video|Lyrics).*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?(Official|Audio|Video|Lyrics).*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(Official|Audio|Video|Lyrics).*$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /** Elimina sufijos como " - Topic", "VEVO", etc. */
    private fun cleanArtist(raw: String): String {
        return raw.replace(Regex("\\s*-\\s*Topic\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*VEVO\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*$"), "")
            .trim()
    }

    private suspend fun trySearch(title: String, artist: String, limit: Int): MbRecordingSearchResponse? {
        val query = buildRecordingQuery(title, artist)
        return trySearchRaw(query, limit)
    }

    private suspend fun trySearchRaw(query: String, limit: Int): MbRecordingSearchResponse? {
        return try {
            throttleMusicBrainz()
            musicBrainzApi.searchRecording(query = query, fmt = "json", limit = limit)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Itera sobre los releases (ordenados por preferencia) y descarga la primera portada
     * que realmente exista en Cover Art Archive.
     */
    private suspend fun findCoverArtAmongReleases(
        releases: List<MbReleaseJson>,
        albumHint: String?
    ): Pair<File?, MbReleaseJson?> {
        val ordered = orderReleasesForCover(releases, albumHint)
        for (rel in ordered) {
            val releaseId = rel.id ?: continue
            val coverFile = downloadCoverIfExists(releaseId)
            if (coverFile != null) {
                return Pair(coverFile, rel)
            }
        }
        return Pair(null, null)
    }

    private suspend fun downloadCoverIfExists(releaseId: String): File? {
        val cacheFile = File(coverArtDir, "mb_$releaseId.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }
        val url = "https://coverartarchive.org/release/$releaseId/front"
        val bytes = fetchUrlBytes(url) ?: return null
        if (bytes.isEmpty()) return null
        try {
            cacheFile.writeBytes(bytes)
            return cacheFile
        } catch (e: Exception) {
            return null
        }
    }

    private fun orderReleasesForCover(
        releases: List<MbReleaseJson>,
        albumHint: String?
    ): List<MbReleaseJson> {
        if (releases.isEmpty()) return releases
        val hint = albumHint?.lowercase()?.trim()?.takeIf { it.isNotBlank() }
        return releases.sortedWith(
            compareBy(
                { rel ->
                    when {
                        rel.status?.equals("Official", ignoreCase = true) == true -> 0
                        rel.status?.equals("Promotion", ignoreCase = true) == true -> 1
                        else -> 2
                    }
                },
                { rel ->
                    val titleLower = rel.title?.lowercase().orEmpty()
                    when {
                        hint == null -> 1
                        titleLower == hint -> 0
                        titleLower.contains(hint) || hint.contains(titleLower) -> 1
                        else -> 2
                    }
                },
                { rel -> rel.id?.isNotBlank() == true } // Preferir los que tienen ID
            )
        )
    }

    private suspend fun throttleMusicBrainz() {
        mbMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastMusicBrainzRequestAt
            if (lastMusicBrainzRequestAt > 0L && elapsed < MB_MIN_INTERVAL_MS) {
                delay(MB_MIN_INTERVAL_MS - elapsed)
            }
            lastMusicBrainzRequestAt = System.currentTimeMillis()
        }
    }

    private suspend fun fetchUrlBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun cacheKey(title: String, artist: String, album: String?): String =
        "${title.lowercase()}|${artist.lowercase()}|${album?.lowercase().orEmpty()}"

    private fun buildRecordingQuery(title: String, artist: String): String {
        val et = escapeLucene(title)
        val ea = escapeLucene(artist)
        return "recording:\"$et\" AND artist:\"$ea\""
    }

    private fun buildRelaxedQuery(title: String, artist: String): String {
        // Si el título contiene guion, extraer solo la parte izquierda (a menudo el título real)
        val simpleTitle = title.split(" - ").first().trim()
        return "recording:\"${escapeLucene(simpleTitle)}\" OR artist:\"${escapeLucene(artist)}\""
    }

    private fun escapeLucene(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val MB_MIN_INTERVAL_MS = 1_200L

        @Volatile
        private var instance: MetadataFetcher? = null

        fun getInstance(context: Context): MetadataFetcher {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: MetadataFetcher(app).also { instance = it }
            }
        }
    }
}

// Interfaces Retrofit (igual que antes)
private interface MusicBrainzApi {
    @GET("recording")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") fmt: String,
        @Query("limit") limit: Int
    ): MbRecordingSearchResponse
}

private data class MbRecordingSearchResponse(
    val recordings: List<MbRecordingJson>?
)

private data class MbRecordingJson(
    val id: String?,
    val title: String?,
    val score: Double?,
    val releases: List<MbReleaseJson>?,
    @SerializedName("artist-credit") val artistCredit: List<MbArtistCreditJson>?
)

private data class MbReleaseJson(
    val id: String?,
    val title: String?,
    val status: String? = null
)

private data class MbArtistCreditJson(
    val name: String?
)