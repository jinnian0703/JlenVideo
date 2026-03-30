package top.jlen.vod.data

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

interface AppleCmsApi {
    @GET("api.php/video/categories")
    suspend fun getCategories(): VideoApiEnvelope<List<AppleCmsCategory>>

    @GET("api.php/video/recommends")
    suspend fun getRecommendations(
        @Query("limit") limit: Int = 12
    ): VideoApiEnvelope<VideoApiPagedRows<VodItem>>

    @GET("api.php/video/latest")
    suspend fun getLatest(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 36
    ): VideoApiEnvelope<VideoApiPagedRows<VodItem>>

    @GET("api.php/video/list")
    suspend fun getByType(
        @Query("type_id") typeId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 36
    ): VideoApiEnvelope<VideoApiPagedRows<VodItem>>

    @GET("api.php/video/search")
    suspend fun search(
        @Query("wd") keyword: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 60
    ): VideoApiEnvelope<VideoApiPagedRows<VodItem>>

    @GET("api.php/video/detail")
    suspend fun getDetail(
        @Query("vod_id") vodId: String
    ): VideoApiEnvelope<JsonElement>
}
