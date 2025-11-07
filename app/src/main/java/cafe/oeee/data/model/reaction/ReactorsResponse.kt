package cafe.oeee.data.model.reaction

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReactorsResponse(
    @Json(name = "reactions")
    val reactions: List<Reactor>
)
