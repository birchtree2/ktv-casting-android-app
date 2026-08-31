package zju.bangdream.ktv.casting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 线程安全的内存日志仓库，供 Rust / Kotlin 统一写入。
 */
object LogRepository {
    private const val MAX_LOG_COUNT = 800
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogItem>>(emptyList())
    val logs = _logs.asStateFlow()

    /** Rust 的 Tokio 工作线程可能并发回调 JNI；串行化可保护 dateFormat 并避免日志追加丢失。 */
    @Synchronized
    fun add(level: LogLevel, tag: String, message: String) {
        val time = dateFormat.format(Date())
        val newItem = LogItem(time, level, tag, message)
        _logs.value = (_logs.value + newItem).takeLast(MAX_LOG_COUNT)
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
    }
}

data class LogItem(
    val time: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel(val label: String) {
    VERBOSE("V"),
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E"),
    UNKNOWN("?")
}
