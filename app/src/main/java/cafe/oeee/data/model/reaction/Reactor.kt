package cafe.oeee.data.model.reaction

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

@JsonClass(generateAdapter = true)
data class Reactor(
    @Json(name = "iri")
    val iri: String,
    @Json(name = "post_id")
    val postId: String,
    @Json(name = "actor_id")
    val actorId: String,
    @Json(name = "emoji")
    val emoji: String,
    @Json(name = "created_at")
    val createdAt: Date,
    @Json(name = "actor_name")
    val actorName: String,
    @Json(name = "actor_handle")
    val actorHandle: String
)
