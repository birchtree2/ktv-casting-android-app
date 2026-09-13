package zju.bangdream.ktv.casting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface EnsureRoomResult {
    data object Success : EnsureRoomResult
    data class Failure(val message: String) : EnsureRoomResult
}

sealed interface RoomExistenceResult {
    data object Exists : RoomExistenceResult
    data object Available : RoomExistenceResult
    data class Failure(val message: String) : RoomExistenceResult
}

enum class RoomEntryMode {
    CREATE,
    JOIN
}

object RoomApi {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun enterRoom(
        baseUrl: String,
        roomId: String,
        mode: RoomEntryMode
    ): EnsureRoomResult =
        withContext(Dispatchers.IO) {
            try {
                enterRoomBlocking(baseUrl, roomId, mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RustEngine.logFromKotlin(
                    "RoomApi",
                    "准备房间失败: ${e.message}",
                    LogLevel.ERROR
                )
                EnsureRoomResult.Failure("无法连接服务器，请检查服务器网址和网络连接")
            }
        }

    suspend fun checkRoom(baseUrl: String, roomId: String): RoomExistenceResult =
        withContext(Dispatchers.IO) {
            try {
                val serverUrl = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
                    ?: return@withContext RoomExistenceResult.Failure("服务器网址格式不正确")
                checkRoomBlocking(serverUrl, roomId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RustEngine.logFromKotlin(
                    "RoomApi",
                    "检查房间失败: ${e.message}",
                    LogLevel.ERROR
                )
                RoomExistenceResult.Failure("无法连接服务器，请检查服务器网址和网络连接")
            }
        }

    private fun enterRoomBlocking(
        baseUrl: String,
        roomId: String,
        mode: RoomEntryMode
    ): EnsureRoomResult {
        val serverUrl = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: return EnsureRoomResult.Failure("服务器网址格式不正确")

        return when (mode) {
            RoomEntryMode.CREATE -> createRoom(serverUrl, roomId)
            RoomEntryMode.JOIN -> joinRoom(serverUrl, roomId)
        }
    }

    private fun joinRoom(serverUrl: HttpUrl, roomId: String): EnsureRoomResult {
        return when (val result = checkRoomBlocking(serverUrl, roomId)) {
            RoomExistenceResult.Exists -> EnsureRoomResult.Success
            RoomExistenceResult.Available -> EnsureRoomResult.Failure("房间不存在，请先创建房间")
            is RoomExistenceResult.Failure -> EnsureRoomResult.Failure(result.message)
        }
    }

    private fun checkRoomBlocking(serverUrl: HttpUrl, roomId: String): RoomExistenceResult {
        val existsRequest = Request.Builder()
            .url(serverUrl.apiUrl("roomExists", roomId))
            .get()
            .build()

        httpClient.newCall(existsRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return RoomExistenceResult.Failure("检查房间失败（HTTP ${response.code}）")
            }
            val json = response.body?.string()?.let(::JSONObject)
                ?: return RoomExistenceResult.Failure("服务器返回了无效的房间信息")
            if (!json.has("exists")) {
                return RoomExistenceResult.Failure("服务器不支持房间创建接口，请更新服务器")
            }
            return if (json.optBoolean("exists")) {
                RoomExistenceResult.Exists
            } else {
                RoomExistenceResult.Available
            }
        }
    }

    private fun createRoom(serverUrl: HttpUrl, roomId: String): EnsureRoomResult {
        val createRequest = Request.Builder()
            .url(serverUrl.apiUrl("createRoom", roomId))
            .post(ByteArray(0).toRequestBody())
            .build()

        httpClient.newCall(createRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return EnsureRoomResult.Failure("创建房间失败（HTTP ${response.code}）")
            }
            val json = response.body?.string()?.let(::JSONObject)
                ?: return EnsureRoomResult.Failure("服务器返回了无效的创建结果")
            if (json.optBoolean("success")) {
                return EnsureRoomResult.Success
            }
            val message = json.optString("msg", "创建房间失败")
            return if (message == "房间已存在") {
                EnsureRoomResult.Failure("房间号已被占用，请更换房间号或选择加入房间")
            } else {
                EnsureRoomResult.Failure(message)
            }
        }
    }

    private fun HttpUrl.apiUrl(endpoint: String, roomId: String): HttpUrl =
        newBuilder()
            .addPathSegments("api/$endpoint")
            .addQueryParameter("roomId", roomId)
            .build()
}
