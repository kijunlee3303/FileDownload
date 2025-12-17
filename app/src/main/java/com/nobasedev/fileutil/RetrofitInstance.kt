package com.nobasedev.fileutil

import retrofit2.Retrofit

object RetrofitInstance {
    // Retrofit 빌더. base URL은 동적으로 변경되므로 비워둠
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://placeholder.com/") // 기본 URL이 필요하지만 @Url로 대체됨
            .build()
    }

    val downloadService: FileDownloadService by lazy {
        retrofit.create(FileDownloadService::class.java)
    }
}