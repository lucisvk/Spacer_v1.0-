package com.example.spacer.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Places API (New) — request/response models (subset). */

@Serializable
internal data class SearchNearbyRequest(
    @SerialName("locationRestriction") val locationRestriction: LocationRestrictionCircle,
    @SerialName("includedTypes") val includedTypes: List<String>,
    @SerialName("maxResultCount") val maxResultCount: Int = 20,
    @SerialName("languageCode") val languageCode: String = "en"
)

@Serializable
internal data class LocationRestrictionCircle(
    val circle: CircleBody
)

@Serializable
internal data class CircleBody(
    val center: LatLngBody,
    val radius: Double
)

@Serializable
internal data class LatLngBody(
    val latitude: Double,
    val longitude: Double
)

@Serializable
internal data class SearchTextRequest(
    @SerialName("textQuery") val textQuery: String,
    @SerialName("locationBias") val locationBias: LocationBiasCircle? = null,
    @SerialName("maxResultCount") val maxResultCount: Int = 20,
    @SerialName("languageCode") val languageCode: String = "en"
)

@Serializable
internal data class LocationBiasCircle(
    val circle: CircleBody
)

@Serializable
internal data class SearchPlacesResponse(
    val places: List<PlaceJson>? = null
)

@Serializable
internal data class PhotoJson(
    val name: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null
)

@Serializable
internal data class PlaceJson(
    val id: String? = null,
    val displayName: LocalizedText? = null,
    val formattedAddress: String? = null,
    val location: LatLngBody? = null,
    val types: List<String>? = null,
    val currentOpeningHours: OpeningHoursJson? = null,
    val regularOpeningHours: OpeningHoursJson? = null,
    val businessStatus: String? = null,
    val photos: List<PhotoJson>? = null,
    val rating: Float? = null,
    @SerialName("userRatingCount") val userRatingCount: Int? = null
)

/** Single-place GET response (Places API New). */
@Serializable
internal data class PlaceDetailJson(
    val id: String? = null,
    val displayName: LocalizedText? = null,
    val formattedAddress: String? = null,
    val location: LatLngBody? = null,
    val types: List<String>? = null,
    val currentOpeningHours: OpeningHoursJson? = null,
    val regularOpeningHours: OpeningHoursJson? = null,
    val businessStatus: String? = null,
    val photos: List<PhotoJson>? = null,
    val rating: Float? = null,
    @SerialName("userRatingCount") val userRatingCount: Int? = null,
    val reviews: List<ReviewJson>? = null
)

@Serializable
internal data class ReviewJson(
    val rating: Double? = null,
    val text: LocalizedText? = null,
    @SerialName("relativePublishTimeDescription") val relativePublishTimeDescription: String? = null,
    @SerialName("authorAttribution") val authorAttribution: AuthorAttributionJson? = null
)

@Serializable
internal data class AuthorAttributionJson(
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("photoUri") val photoUri: String? = null
)

@Serializable
internal data class LocalizedText(
    val text: String? = null
)

@Serializable
internal data class OpeningHoursJson(
    val openNow: Boolean? = null,
    val weekdayDescriptions: List<String>? = null
)

@Serializable
data class PlaceUi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val types: List<String>,
    val openNow: Boolean?,
    val weekdayHours: List<String>,
    val businessStatus: String?,
    /** First photo resource name for Places photo media URL. */
    val primaryPhotoName: String? = null,
    val rating: Float? = null,
    val userRatingCount: Int? = null
)

data class ReviewUi(
    val author: String,
    val rating: Float,
    val text: String,
    val relativeTime: String
)

data class PlaceDetailUi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val types: List<String>,
    val openNow: Boolean?,
    val weekdayHours: List<String>,
    val businessStatus: String?,
    val rating: Float?,
    val userRatingCount: Int?,
    val photoResourceNames: List<String>,
    val reviews: List<ReviewUi>
)

internal fun PlaceJson.toPlaceUi(): PlaceUi? {
    val id = id ?: return null
    val name = displayName?.text?.trim().orEmpty().ifBlank { return null }
    val lat = location?.latitude ?: return null
    val lng = location?.longitude ?: return null
    val openNow = currentOpeningHours?.openNow ?: regularOpeningHours?.openNow
    val hours = (currentOpeningHours?.weekdayDescriptions ?: regularOpeningHours?.weekdayDescriptions)
        .orEmpty()
    val firstPhoto = photos?.firstOrNull()?.name
    return PlaceUi(
        id = id,
        name = name,
        address = formattedAddress.orEmpty(),
        latitude = lat,
        longitude = lng,
        types = types.orEmpty(),
        openNow = openNow,
        weekdayHours = hours,
        businessStatus = businessStatus,
        primaryPhotoName = firstPhoto,
        rating = rating,
        userRatingCount = userRatingCount
    )
}

internal fun PlaceDetailJson.toPlaceDetailUi(): PlaceDetailUi? {
    val id = id ?: return null
    val name = displayName?.text?.trim().orEmpty().ifBlank { return null }
    val lat = location?.latitude ?: return null
    val lng = location?.longitude ?: return null
    val openNow = currentOpeningHours?.openNow ?: regularOpeningHours?.openNow
    val hours = (currentOpeningHours?.weekdayDescriptions ?: regularOpeningHours?.weekdayDescriptions)
        .orEmpty()
    val photoNames = photos.orEmpty().mapNotNull { it.name }
    val revs = reviews.orEmpty().mapNotNull { r ->
        val t = r.text?.text?.trim().orEmpty()
        if (t.isEmpty()) return@mapNotNull null
        ReviewUi(
            author = r.authorAttribution?.displayName?.trim().orEmpty().ifBlank { "Visitor" },
            rating = (r.rating ?: 0.0).toFloat(),
            text = t,
            relativeTime = r.relativePublishTimeDescription.orEmpty()
        )
    }
    return PlaceDetailUi(
        id = id,
        name = name,
        address = formattedAddress.orEmpty(),
        latitude = lat,
        longitude = lng,
        types = types.orEmpty(),
        openNow = openNow,
        weekdayHours = hours,
        businessStatus = businessStatus,
        rating = rating,
        userRatingCount = userRatingCount,
        photoResourceNames = photoNames,
        reviews = revs
    )
}

fun PlaceDetailUi.toPlaceUi(): PlaceUi =
    PlaceUi(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        types = types,
        openNow = openNow,
        weekdayHours = weekdayHours,
        businessStatus = businessStatus,
        primaryPhotoName = photoResourceNames.firstOrNull(),
        rating = rating,
        userRatingCount = userRatingCount
    )

fun PlaceDetailUi.categoryTags(max: Int = 8): List<String> {
    return types
        .asSequence()
        .filter { it.isNotBlank() && it != "point_of_interest" && it != "establishment" }
        .distinct()
        .take(max)
        .map { formatPlaceType(it) }
        .toList()
}

fun PlaceUi.categoryTags(max: Int = 4): List<String> {
    return types
        .asSequence()
        .filter { it.isNotBlank() && it != "point_of_interest" && it != "establishment" }
        .distinct()
        .take(max)
        .map { formatPlaceType(it) }
        .toList()
}

fun formatPlaceType(raw: String): String {
    return raw
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
}
