package zju.bangdream.ktv.casting

object RustEngine {
    init {
        System.loadLibrary("ktv_casting_lib")
    }

    private fun mapLevel(level: Int): LogLevel {
        return when (level) {
            0 -> LogLevel.VERBOSE
            1 -> LogLevel.DEBUG
            2 -> LogLevel.INFO
            3 -> LogLevel.WARN
            4 -> LogLevel.ERROR
            else -> LogLevel.UNKNOWN
        }
    }

    /**
     * Rust 侧可通过 JNI 直接调用，用于回传日志。
     * 签名示例（Rust/Java）：
     *   zju/bangdream/ktv/casting/RustEngine.onRustLog(int, String, String)
     */
    @JvmStatic
    fun onRustLog(level: Int, tag: String, message: String) {
        LogRepository.add(mapLevel(level), tag, message)
    }

    fun logFromKotlin(tag: String, message: String, level: LogLevel = LogLevel.DEBUG) {
        LogRepository.add(level, tag, message)
    }

    // 基础接口
    external fun initLogging(level: Int)
    external fun initSessionDir(dirPath: String)
    external fun searchDevices(): Array<DlnaDeviceItem>

    /**
     * 通过设备描述 XML 的 URL 直接获取设备（适用于 WiFi 不支持多播的场景）
     * 例如：http://192.168.1.x:9958/bilibili/description.xml
     *
     * @param url 设备的 XML 描述地址
     * @return 成功返回包含一个元素的设备数组，失败返回空数组
     */
    external fun searchDeviceByUrl(url: String): Array<DlnaDeviceItem>

    // 核心初始化：启动 Rust 内部的 HttpServer 和状态机
    external fun startEngine(baseUrl: String, roomId: String, targetLocation: String)

    // 控制接口：由 KT 决定何时调用
    external fun nextSong()

    external fun prevSong()

    // 查询接口：由 KT 轮询获取状态
    external fun queryProgress(): IntArray      // 返回当前秒数

    external fun resetEngine()
    /**
     * @return 1 为播放中，0 为暂停
     */
    external fun togglePause(): Int

    /**
     * @param target 目标值
     * @return 新音量，-1 为失败
     */
    external fun setVolume(target: Int): Int
    /**
     * @return 音量，-1 为失败
     */
    external fun getVolume(): Int

    /**
     * 相对调高音量。DLNA 内部按 get+set 模拟；B站投屏直接发送设备原生的“音量+”指令。
     * @return 1 为成功，-1 为失败
     */
    external fun volumeUp(step: Int): Int

    /**
     * 相对调低音量，详见 [volumeUp]。
     * @return 1 为成功，-1 为失败
     */
    external fun volumeDown(step: Int): Int

    /**
     * 切换弹幕开关（仅B站投屏支持）。
     * @return 1=已开启，0=已关闭，-1=失败（如当前是 DLNA 模式）
     */
    external fun toggleDanmaku(): Int

    /**
     * 直接设置弹幕开关（供开关类 UI 使用）。
     * @return 1=已开启，0=已关闭，-1=失败（如当前是 DLNA 模式）
     */
    external fun setDanmaku(on: Boolean): Int

    /** 本地跟踪的弹幕状态（设备侧无读回接口），仅B站投屏有意义 */
    external fun getDanmakuState(): Boolean

    /**
     * 设置清晰度（仅B站投屏支持）。qn 是 B 站协议常量，见 [BiliQuality]。
     * @return 设置后的 qn，失败（含非法 qn 或当前是 DLNA 模式）返回 -1
     */
    external fun setQuality(qn: Int): Int

    /** 本地跟踪的清晰度 qn（设备侧无读回接口），仅B站投屏有意义 */
    external fun getQuality(): Int

    /**
     * @return 音量，-1 为失败
     */
    external fun jumpToSecs(target: Int): Int

    /**
     * 获取歌曲标题
     */
    external fun getCurrentSongTitle(): String

    /**
     * 获取队列中还有多少首歌未播；-1 表示引擎未初始化
     */
    external fun getQueuedSongsCount(): Int
    /**
     * 获取队列中有多少首歌已播；-1 表示引擎未初始化
     */
    external fun getSungSongsCount(): Int

    // ---- 哔哩哔哩云投屏 ----

    /** 后台启动扫码登录流程，通过 getBilibiliLoginStatus() 轮询状态 */
    external fun startBilibiliQrLogin()

    /** 返回当前登录状态：-2=未开始, 0=等待扫码, 1=成功, -1=失败/过期 */
    external fun getBilibiliLoginStatus(): Int

    /** 返回二维码 URL（status==0 时有效），空字符串表示尚未就绪 */
    external fun getBilibiliQrUrl(): String

    /** 返回在线投屏设备列表的 JSON 字符串（需已登录） */
    external fun listBilibiliDevices(): String

    /** 启动哔哩哔哩云投屏引擎 */
    external fun startBilibiliEngine(baseUrl: String, roomId: String, deviceBuvid: String)

    /** 返回当前会话 JSON，供持久化存储；未登录时返回空字符串 */
    external fun getBilibiliSessionJson(): String

    /** 从 JSON 字符串恢复会话（应用启动时调用）；成功返回 1，失败返回 0 */
    external fun restoreBilibiliSession(json: String): Int

    /** 检查 B 站 token 是否已过期；true 表示过期 */
    external fun isBilibiliSessionExpired(): Boolean

    /** 清除保存的 B 站 session；true 表示成功 */
    external fun clearBilibiliSession(): Boolean
}

/**
 * 与 Rust 侧 cast::Quality 对应。qn 是 B 站 `/x/tv/stream/cmd` 接口本身的协议常量，
 * 不是我们自定义的格式；跨 JNI 边界仍传裸 Int，这个 enum 只在 Kotlin 这一侧做类型安全。
 */
enum class BiliQuality(val qn: Int, val label: String) {
    P360(16, "360P"),
    P480(32, "480P"),
    P720(64, "720P"),
    P1080(80, "1080P"),
    P1080H(112, "1080PH"),
    P1080P60(116, "1080P60"),
    P4K(120, "4K");

    companion object {
        val DEFAULT = P1080
        fun fromQn(qn: Int): BiliQuality? = entries.find { it.qn == qn }
    }
}
