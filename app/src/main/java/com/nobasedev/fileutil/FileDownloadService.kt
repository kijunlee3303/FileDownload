package com.nobasedev.fileutil

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url


interface FileDownloadService {
    @GET
    @Streaming // 파일을 청크 단위로 받아 메모리 문제를 방지
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}