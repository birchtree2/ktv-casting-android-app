package zju.bangdream.ktv.casting.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    private val notificationManager by lazy {
        ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: throw IllegalStateException("NotificationManager not available")
    }

    private val httpClient: OkHttpClient by lazy {
        try {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Failed to create OkHttpClient: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "apk_download"
        private const val NOTIFICATION_ID = 10086
    }

    override fun doWork(): Result {
        val apkUrl = inputData.getString("apk_url") ?: return Result.failure()
        val tagName = inputData.getString("tag_name") ?: return Result.failure()

        return try {
            createNotificationChannel()

            // 1. 下载 APK 文件
            val apkFile = File(applicationContext.cacheDir, "$tagName.apk")
            downloadFile(apkUrl, apkFile)

            // 2. 保存文件路径
            val prefs = applicationContext.getSharedPreferences("update_check", Context.MODE_PRIVATE)
            prefs.edit().putString("pending_apk_path", apkFile.absolutePath).apply()

            // 3. 在主线程触发安装
            Handler(Looper.getMainLooper()).post {
                installApk(apkFile)
            }

            // 显示下载完成的通知
            showNotification("更新完成", "点击安装")

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Download failed: ${e.message}", e)
            showNotification("下载失败", e.message ?: "Unknown error")
            Result.retry()
        }
    }

    private fun downloadFile(url: String, file: File) {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "KTV-Casting-Android")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()

        body.byteStream().use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloadedBytes = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    // 更新通知进度
                    if (contentLength > 0) {
                        val progress = (downloadedBytes * 100 / contentLength).toInt()
                        updateNotification(progress)
                    }
                }
            }
        }

        android.util.Log.d("DownloadWorker", "Downloaded APK to ${file.absolutePath}")
    }

    private fun installApk(apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                // API 31+ 需要 EXTRA_INSTALLER_PACKAGE_NAME
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, applicationContext.packageName)
            }

            applicationContext.startActivity(intent)
            android.util.Log.d("DownloadWorker", "APK install intent sent")
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Failed to install APK: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "APK Download",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(progress: Int) {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("下载更新中")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
