package cafe.oeee.data.model.reaction

import cafe.oeee.data.model.ReactionCount
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReactionResponse(
    @Json(name = "reactions") val reactions: List<ReactionCount>
)
