package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PostsResponse(
    @Json(name = "posts")
    val posts: List<Post>,
    @Json(name = "pagination")
    val pagination: Pagination
)
