package com.nobasedev.fileutil.worker

import android.content.Context
import android.webkit.URLUtil
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nobasedev.fileutil.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // 1. ViewModel로부터 전달받은 파일 URL과 파일 이름 가져오기
        val fileUrl = inputData.getString(KEY_FILE_URL) ?: return Result.failure()
        val fileName = URLUtil.guessFileName(fileUrl, null, null)

        return try {
            // 2. Retrofit을 통해 파일 다운로드 요청
            val response = RetrofitInstance.downloadService.downloadFile(fileUrl)

            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure()

                // 3. 파일을 앱의 안전한 내부 저장소(context.filesDir)에 저장
                val outputFile = File(context.filesDir, fileName)

                withContext(Dispatchers.IO) {
                    outputFile.outputStream().use { outputStream ->
                        body.byteStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }

                // 4. 성공 시, 저장된 파일의 절대 경로를 결과 데이터로 반환
                Result.success(workDataOf(KEY_OUTPUT_FILE_PATH to outputFile.absolutePath))

            } else {
                // 다운로드 실패 처리 (예: 404 Not Found)
                Result.failure()
            }
        } catch (e: Exception) {
            // 네트워크 오류 등 예외 발생 시 실패 처리
            Result.failure()
        }
    }

    companion object {
        const val KEY_FILE_URL = "key_file_url"
        const val KEY_OUTPUT_FILE_PATH = "key_output_file_path"
        // NOTIFICATION_ID는 더 이상 필요 없으므로 제거
    }
}
