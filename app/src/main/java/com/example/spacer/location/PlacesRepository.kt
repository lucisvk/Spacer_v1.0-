package com.example.spacer.location

import com.example.spacer.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Google Places API (New) — nearby + text search.
 * Dashboard: enable "Places API (New)", billing on; restrict API key to Android app + Places.
 *
 * **When Google Cloud / Places billing applies:** every successful HTTP call below counts against
 * your Places quota (and can incur cost once past free tier): [searchNearby], [searchText],
 * [fetchPlaceDetails], and each [photoMediaUrl] load (photo endpoint uses the same API key).
 * This does **not** run from calendar overlap code — that uses the on-device [android.provider.CalendarContract] API only.
 */
class PlacesRepository(
    private val apiKey: String = BuildConfig.PLACES_API_KEY
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    private val fieldMask = listOf(
        "places.id",
        "places.displayName",
        "places.formattedAddress",
        "places.location",
        "places.types",
        "places.currentOpeningHours",
        "places.regularOpeningHours",
        "places.businessStatus",
        "places.photos",
        "places.rating",
        "places.userRatingCount"
    ).joinToString(",")

    private val detailFieldMask = listOf(
        "id",
        "displayName",
        "formattedAddress",
        "location",
        "types",
        "currentOpeningHours",
        "regularOpeningHours",
        "businessStatus",
        "photos",
        "rating",
        "userRatingCount",
        "reviews"
    ).joinToString(",")

    /** Max 5 types per Places API (New) searchNearby. */
    private val nearbyEventTypes = listOf(
        "restaurant",
        "bar",
        "night_club",
        "park",
        "tourist_attraction"
    )

    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 2500.0
    ): Result<List<PlaceUi>> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Add PLACES_API_KEY to local.properties (Places API New enabled in Google Cloud)."))
        }
        return try {
            val body = SearchNearbyRequest(
                locationRestriction = LocationRestrictionCircle(
                    circle = CircleBody(
                        center = LatLngBody(latitude, longitude),
                        radius = radiusMeters.coerceIn(100.0, 50_000.0)
                    )
                ),
                includedTypes = nearbyEventTypes,
                maxResultCount = 20
            )
            val response = client.post(NEARBY_URL) {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", fieldMask)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Places nearby failed: ${response.status} ${response.bodyAsText()}"))
            }
            val parsed = response.body<SearchPlacesResponse>()
            val list = parsed.places.orEmpty().mapNotNull { it.toPlaceUi() }.distinctBy { it.id }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchText(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusMeters: Double = 25_000.0
    ): Result<List<PlaceUi>> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Add PLACES_API_KEY to local.properties."))
        }
        val q = query.trim()
        if (q.length < 2) return Result.success(emptyList())
        return try {
            val body = SearchTextRequest(
                textQuery = q,
                locationBias = if (latitude != null && longitude != null) {
                    LocationBiasCircle(
                        circle = CircleBody(
                            center = LatLngBody(latitude, longitude),
                            radius = radiusMeters.coerceIn(500.0, 50_000.0)
                        )
                    )
                } else null,
                maxResultCount = 20
            )
            val response = client.post(SEARCH_TEXT_URL) {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", fieldMask)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Places search failed: ${response.status} ${response.bodyAsText()}"))
            }
            val parsed = response.body<SearchPlacesResponse>()
            val list = parsed.places.orEmpty().mapNotNull { it.toPlaceUi() }.distinctBy { it.id }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GET place details (reviews, all photos). [placeId] may be `places/...` or raw id.
     */
    suspend fun fetchPlaceDetails(placeId: String): Result<PlaceDetailUi> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Missing PLACES_API_KEY"))
        }
        return try {
            val pid = placePathId(placeId)
            val response = client.get("https://places.googleapis.com/v1/places/$pid") {
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", detailFieldMask)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Place details failed: ${response.status} ${response.bodyAsText()}"))
            }
            val parsed = response.body<PlaceDetailJson>()
            val ui = parsed.toPlaceDetailUi()
                ?: return Result.failure(IllegalStateException("Invalid place payload"))
            Result.success(ui)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Public URL for Coil — Places Photo media (API key in query). */
    fun photoMediaUrl(photoResourceName: String, maxPx: Int = 900): String {
        val path = photoResourceName.trim().removePrefix("/")
        return buildString {
            append("https://places.googleapis.com/v1/")
            append(path)
            append("/media?maxHeightPx=")
            append(maxPx)
            append("&maxWidthPx=")
            append(maxPx)
            append("&key=")
            append(apiKey)
        }
    }

    companion object {
        private const val NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby"
        private const val SEARCH_TEXT_URL = "https://places.googleapis.com/v1/places:searchText"

        internal fun placePathId(raw: String): String = raw.trim().removePrefix("places/")

        /**
         * False when `PLACES_API_KEY` was not set at build time ([BuildConfig.PLACES_API_KEY] empty).
         * In that case all Places HTTP calls would fail — set the key in `local.properties` and rebuild.
         */
        fun isApiKeyConfigured(): Boolean = BuildConfig.PLACES_API_KEY.isNotBlank()
    }
}
