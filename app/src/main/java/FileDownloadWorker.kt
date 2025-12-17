package com.nobasedev.fileutil.worker

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.webkit.URLUtil
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nobasedev.fileutil.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class FileDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val fileUrl = inputData.getString(KEY_FILE_URL) ?: return Result.failure()
        val fileName = URLUtil.guessFileName(fileUrl, null, null)

        return try {
            val response = RetrofitInstance.downloadService.downloadFile(fileUrl)

            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure()

                // MediaStore를 사용하여 공용 Download 폴더에 저장
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, body.contentType()?.toString())
                    // 파일을 Download 폴더에 저장
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                        put(MediaStore.MediaColumns.IS_PENDING, 1) // 저장 시작을 알림
                    }
                }

                // 저장할 파일의 URI 생성
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return Result.failure()

                // 생성된 URI에 파일 스트림을 씀
                resolver.openOutputStream(uri)?.use { outputStream ->
                    withContext(Dispatchers.IO) {
                        body.byteStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }

                // 파일 저장이 완료되었음을 시스템에 알림
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                // 성공 시 파일 URI를 문자열로 반환
                Result.success(workDataOf(KEY_OUTPUT_FILE_URI to uri.toString()))

            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_FILE_URL = "key_file_url"
        const val KEY_OUTPUT_FILE_PATH = "key_output_file_path"
        // NOTIFICATION_ID는 더 이상 필요 없으므로 제거
    }
}
