package com.nobasedev.fileutil

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nobasedev.fileutil.worker.FileDownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DownloadUiState(
    val url: String = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
    val statusMessage: String = "상태: 대기 중",
    val isDownloading: Boolean = false
)

class MainViewModel : ViewModel() {

    var uiState by mutableStateOf(DownloadUiState())
        private set

    fun onUrlChange(newUrl: String) {
        uiState = uiState.copy(url = newUrl)
    }

    private val _workInfo = MutableStateFlow<WorkInfo?>(null)
    val workInfo: StateFlow<WorkInfo?> = _workInfo

    fun startDownload(context: Context) {
        if (!URLUtil.isValidUrl(uiState.url)) {
            // ... (기존 URL 유효성 검사 로직)
            return
        }

        val workManager = WorkManager.getInstance(context)

        // 제약 조건 설정 (예: 네트워크 연결 시)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 입력 데이터 설정
        val inputData = workDataOf(FileDownloadWorker.KEY_FILE_URL to uiState.url)

        // 작업 요청 생성
        val downloadWorkRequest = OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        // 작업 큐에 추가
        workManager.enqueue(downloadWorkRequest)

        // 작업 상태를 관찰하여 UI 업데이트
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(downloadWorkRequest.id).collect { info ->
                _workInfo.value = info
                // 작업 상태에 따라 UI 상태 메시지 업데이트
                val statusMessage = when (info.state) {
                    WorkInfo.State.ENQUEUED -> "상태: 다운로드 대기 중"
                    WorkInfo.State.RUNNING -> "상태: 다운로드 중..."
                    WorkInfo.State.SUCCEEDED -> {
                        val filePath = info.outputData.getString(FileDownloadWorker.KEY_OUTPUT_FILE_PATH)
                        openFile(context, filePath) // 파일 열기 함수 호출
                        "상태: 다운로드 완료!"
                    }
                    WorkInfo.State.FAILED -> "상태: 다운로드 실패"
                    WorkInfo.State.CANCELLED -> "상태: 다운로드 취소됨"
                    else -> "상태: 대기 중"
                }
                uiState = uiState.copy(statusMessage = statusMessage, isDownloading = info.state == WorkInfo.State.RUNNING)
            }
        }
    }

    private fun openFile(context: Context, filePath: String?) {
        filePath?.let {
            try {
                val file = File(it)
                // 1. AndroidManifest.xml에 정의한 authority와 일치시킴
                val authority = "${context.packageName}.provider"
                // 2. FileProvider를 통해 안전한 content:// URI 생성
                val contentUri = FileProvider.getUriForFile(context, authority, file)

                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"

                // 3. 생성된 content:// URI로 Intent 실행
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(openIntent)
            } catch (e: Exception) {
                // ActivityNotFoundException, IllegalArgumentException 등 처리
                // 예: Toast.makeText(context, "파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}