package top.jlen.vod.data

import retrofit2.http.GET
import retrofit2.http.Query

interface AppleCmsApi {
    @GET("api.php/provide/vod/")
    suspend fun getCategories(
        @Query("ac") action: String = "list"
    ): AppleCmsResponse

    @GET("api.php/provide/vod/")
    suspend fun getLatest(
        @Query("ac") action: String = "detail",
        @Query("pg") page: Int = 1
    ): AppleCmsResponse

    @GET("api.php/provide/vod/")
    suspend fun getListPage(
        @Query("ac") action: String = "list",
        @Query("pg") page: Int = 1
    ): AppleCmsResponse

    @GET("api.php/provide/vod/")
    suspend fun getByType(
        @Query("ac") action: String = "detail",
        @Query("t") typeId: String,
        @Query("pg") page: Int = 1
    ): AppleCmsResponse

    @GET("api.php/provide/vod/")
    suspend fun search(
        @Query("ac") action: String = "detail",
        @Query("wd") keyword: String,
        @Query("pg") page: Int = 1
    ): AppleCmsResponse

    @GET("api.php/provide/vod/")
    suspend fun getDetail(
        @Query("ac") action: String = "detail",
        @Query("ids") vodId: String
    ): AppleCmsResponse

    @GET("api.php/provide/vod/")
    suspend fun getByLevel(
        @Query("ac") action: String = "detail",
        @Query("h") level: String,
        @Query("pg") page: Int = 1
    ): AppleCmsResponse
}
