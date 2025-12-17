package com.nobasedev.fileutil

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.vector.path
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException

class DownloadCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            return
        }

        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) {
            Log.e("Receiver", "Download ID not found.")
            return
        }

        Log.d("Receiver", "Download complete for ID: $id")
        Toast.makeText(context, "다운로드 완료!", Toast.LENGTH_SHORT).show()

        if (context == null) return

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(id)

        if (uri == null) {
            Log.e("Receiver", "Cannot get URI for downloaded file.")
            Toast.makeText(context, "파일을 열 수 없습니다. (URI null)", Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = downloadManager.getMimeTypeForDownloadedFile(id)

        // FileProvider를 사용하기 위해 다운로드된 파일의 실제 File 객체를 얻어야 합니다.
        val parcelFileDescriptor = try {
            downloadManager.openDownloadedFile(id)
        } catch (e: FileNotFoundException) {
            Log.e("Receiver", "Downloaded file not found.", e)
            Toast.makeText(context, "다운로드된 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(parcelFileDescriptor.fileDescriptor.toString()) // 이 방법은 신뢰할 수 없음
        // 따라서, 우리는 다운로드 시 파일 경로를 알고 있거나 쿼리해야 합니다.
        // 더 안정적인 방법은 DownloadManager.Query를 사용하는 것입니다.
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)
        if (!cursor.moveToFirst()) {
            Log.e("Receiver", "Cannot move cursor to first row for downloaded file.")
            cursor.close()
            return
        }

        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        if (localUriIndex == -1) {
            Log.e("Receiver", "COLUMN_LOCAL_URI not found.")
            cursor.close()
            return
        }

        val localUriString = cursor.getString(localUriIndex)
        cursor.close()

        val downloadedFile = File(Uri.parse(localUriString).path!!)

        val contentUri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".provider",
            downloadedFile
        )

        val openFileIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(openFileIntent)
        } catch (e: Exception) {
            Log.e("Receiver", "No application can handle this file.", e)
            Toast.makeText(context, "이 파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}