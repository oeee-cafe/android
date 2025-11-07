package cafe.oeee.data.model.search

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "users") val users: List<SearchUserResult>,
    @Json(name = "posts") val posts: List<SearchPostResult>
)
