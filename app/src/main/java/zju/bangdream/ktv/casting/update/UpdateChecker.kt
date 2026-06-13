package zju.bangdream.ktv.casting.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val publishedAt: Long,  // 毫秒时间戳
    val body: String,
    val apkUrl: String,
    val htmlUrl: String
)

class UpdateChecker(private val context: Context) {
    private val prefs = context.getSharedPreferences("update_check", Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient by lazy {
        try {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "Failed to create OkHttpClient: ${e.message}")
            throw e
        }
    }

    private val REPO_API_ENDPOINTS = listOf(
        "https://api.github.com/repos/KARAOKE-MASTER-ZJU/ktv-casting-android-app/releases/latest",
        "https://gh-proxy.com/https://api.github.com/repos/KARAOKE-MASTER-ZJU/ktv-casting-android-app/releases/latest"
    )

    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        for (endpoint in REPO_API_ENDPOINTS) {
            try {
                return@withContext fetchFromEndpoint(endpoint)
            } catch (e: Exception) {
                android.util.Log.d("UpdateChecker", "Failed to fetch from $endpoint: ${e.message}")
                continue
            }
        }
        null
    }

    private fun fetchFromEndpoint(url: String): ReleaseInfo? {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "KTV-Casting-Android")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val json = JSONObject(body)

        val tagName = json.optString("tag_name", "unknown")
        val publishedAtStr = json.optString("published_at", "")
        val publishedAt = try {
            Instant.parse(publishedAtStr).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        val releaseBody = json.optString("body", "")
        val htmlUrl = json.optString("html_url", "")

        // 提取第一个 .apk 文件的下载链接
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url", "")
                break
            }
        }

        if (apkUrl.isEmpty()) return null

        return ReleaseInfo(
            tagName = tagName,
            publishedAt = publishedAt,
            body = releaseBody,
            apkUrl = apkUrl,
            htmlUrl = htmlUrl
        )
    }

    fun shouldUpdate(releaseInfo: ReleaseInfo): Boolean {
        val lastReleaseTime = prefs.getLong("last_release_time", 0L)
        return releaseInfo.publishedAt > lastReleaseTime
    }

    fun saveLastCheckTime(releaseInfo: ReleaseInfo) {
        prefs.edit()
            .putLong("last_release_time", releaseInfo.publishedAt)
            .apply()
    }

    fun getLastCheckTime(): Long {
        return prefs.getLong("last_release_time", 0L)
    }
}
