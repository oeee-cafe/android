package cafe.oeee.data.model.search

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchUserResult(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String
)
