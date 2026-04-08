package cafe.oeee.data.repository

import cafe.oeee.data.model.ActiveCommunitiesResponse
import cafe.oeee.data.model.PostsResponse
import cafe.oeee.data.model.RecentCommentsResponse
import cafe.oeee.data.remote.ApiClient

class HomeRepository {
    private val apiService = ApiClient.apiService

    suspend fun getPublicPosts(offset: Int = 0, limit: Int = 18): Result<PostsResponse> {
        return try {
            val response = apiService.getPublicPosts(offset, limit)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveCommunities(): Result<ActiveCommunitiesResponse> {
        return try {
            val response = apiService.getActiveCommunities()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestComments(): Result<RecentCommentsResponse> {
        return try {
            val response = apiService.getLatestComments()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
