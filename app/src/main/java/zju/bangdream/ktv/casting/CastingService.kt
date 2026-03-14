package zju.bangdream.ktv.casting

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CastingService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    companion object {
        private val _playbackProgress = MutableStateFlow(Pair(0L, 0L))
        val playbackProgress = _playbackProgress.asStateFlow()

        // 歌名状态流
        private val _currentSongTitle = MutableStateFlow("正在加载...")
        val currentSongTitle = _currentSongTitle.asStateFlow()

        fun resetProgress() {
            _playbackProgress.value = Pair(0L, 0L)
            _currentSongTitle.value = "已停止"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val baseUrl = intent?.getStringExtra("base_url") ?: ""
        val roomId = intent?.getLongExtra("room_id", 1111L) ?: 1111L
        val location = intent?.getStringExtra("location") ?: ""

        // 1. 准备通知栏
        val notification = createNotification("准备投屏...")

        // 根据 Android 版本启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        // 2. 初始化 Rust 引擎
    RustEngine.startEngine(baseUrl, roomId.toString(), location)

        // 3. 开启轮询逻辑
        startCommanderLoop()

        return START_STICKY
    }

    private fun startCommanderLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            delay(2000)
            while (isActive) {
                delay(500)
                val progress = RustEngine.queryProgress()
                if (progress.size < 2) continue

                val current = progress[0].toLong()
                val total = progress[1].toLong()


                val title = RustEngine.getCurrentSongTitle() // 获取 Rust 层存储的标题

                _playbackProgress.value = Pair(current, total)
                _currentSongTitle.value = title

                if (current >= 0 && total > 0) {
                    // 通知栏现在显示：[歌名] 进度
                    updateNotification("$title (${formatTime(current)} / ${formatTime(total)})")

                    if (total - current <= 2 && current > 5) {
                        RustEngine.nextSong()
                        delay(5000)
                    }
                } else {
                    updateNotification("当前播放: $title")
                }

            }
        }
    }

    // --- 新增：时间格式化辅助函数 ---
    private fun formatTime(seconds: Long): String {
        if (seconds < 0) return "00:00"
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    // --- 通知栏相关逻辑 ---
    private fun createNotification(content: String): Notification {
        val channelId = "CastingChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "KTV Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("KTV 投屏助手")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true) // 建议设置，防止通知被意外划掉
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(content))
    }

    override fun onDestroy() {
        // --- 修改：销毁时重置 UI 状态流并取消协程 ---
        resetProgress()
        serviceScope.cancel()
        super.onDestroy()
    }
}