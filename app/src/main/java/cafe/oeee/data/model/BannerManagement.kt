package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BannerListItem(
    @Json(name = "id") val id: String,
    @Json(name = "image_url") val imageUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "is_active") val isActive: Boolean
)

@JsonClass(generateAdapter = true)
data class BannerListResponse(
    @Json(name = "banners") val banners: List<BannerListItem>
)
