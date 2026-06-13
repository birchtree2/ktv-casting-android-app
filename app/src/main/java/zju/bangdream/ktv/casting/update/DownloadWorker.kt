package zju.bangdream.ktv.casting.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters

class DownloadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val apkUrl = inputData.getString("apk_url") ?: return Result.failure()

        return try {
            // 直接打开浏览器下载
            openDownloadInBrowser(apkUrl)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun openDownloadInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            applicationContext.startActivity(intent)
            android.util.Log.d("DownloadWorker", "Opened download URL in browser")
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Failed to open browser: ${e.message}", e)
            throw e
        }
    }
}
