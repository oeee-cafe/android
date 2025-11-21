package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReportPostRequest(
    @Json(name = "description") val description: String
)

@JsonClass(generateAdapter = true)
data class ReportPostResponse(
    @Json(name = "message") val message: String
)
