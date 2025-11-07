package cafe.oeee.data.service

import android.content.Context
import cafe.oeee.data.model.search.SearchResponse
import cafe.oeee.data.remote.ApiClient

class SearchService private constructor(private val context: Context) {
    private val apiService = ApiClient.apiService

    companion object {
        @Volatile
        private var INSTANCE: SearchService? = null

        fun getInstance(context: Context): SearchService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun search(query: String, limit: Int? = null): Result<SearchResponse> {
        return try {
            val response = apiService.search(query, limit)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
