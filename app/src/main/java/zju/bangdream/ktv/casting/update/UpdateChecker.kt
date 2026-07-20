package zju.bangdream.ktv.casting.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zju.bangdream.ktv.casting.BuildConfig
import zju.bangdream.ktv.casting.LogLevel
import zju.bangdream.ktv.casting.RustEngine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.ProxySelector
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
                .proxySelector(ProxySelector.getDefault())
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            RustEngine.logFromKotlin("UpdateChecker", "创建 OkHttpClient 失败: ${e.message}", LogLevel.ERROR)
            throw e
        }
    }

    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            fetchUpdateFromGitHubPages()
        } catch (e: Exception) {
            RustEngine.logFromKotlin("UpdateChecker", "获取更新信息失败: ${e.message}", LogLevel.DEBUG)
            null
        }
    }

    private fun fetchUpdateFromGitHubPages(): ReleaseInfo? {
        val updateUrl = "https://${BuildConfig.GITHUB_REPO_OWNER}.github.io/${BuildConfig.GITHUB_REPO_NAME}/release.json"
        RustEngine.logFromKotlin("UpdateChecker", "检查更新: 仓库=${BuildConfig.GITHUB_REPO_OWNER}/${BuildConfig.GITHUB_REPO_NAME} URL=$updateUrl", LogLevel.INFO)
        val request = Request.Builder()
            .url(updateUrl)
            .addHeader("User-Agent", "KTV-Casting-Android")
            .build()

        val response = httpClient.newCall(request).execute()
        RustEngine.logFromKotlin("UpdateChecker", "请求结果: code=${response.code} success=${response.isSuccessful}", LogLevel.INFO)
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        RustEngine.logFromKotlin("UpdateChecker", "响应内容: ${body.take(200)}", LogLevel.DEBUG)
        val json = JSONObject(body)

        val tagName = json.optString("tag_name", "unknown")
        val publishedAtStr = json.optString("published_at", "")
        val publishedAt = try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(publishedAtStr.replace("Z", "").substringBeforeLast("+").substringBeforeLast("-"))?.time ?: System.currentTimeMillis()
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
        // 如果 release tag 以当前安装版本号开头，则不是新版本
        val versionName = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        if (versionName.isNotEmpty() && releaseInfo.tagName.startsWith(versionName)) return false
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
