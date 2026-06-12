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
    external fun searchDevices(): Array<DlnaDeviceItem>

    /**
     * 通过设备描述XML的URL直接获取设备，适用于WiFi不支持多播的场景。
     * 例如 B站小电视默认地址：http://192.168.x.x:9958/bilibili/description.xml
     * @param url 设备描述XML完整地址
     * @return 成功返回含一个元素的数组，失败返回空数组
     */
    external fun searchDeviceByUrl(url: String): Array<DlnaDeviceItem>

    // 核心初始化：启动 Rust 内部的 HttpServer 和状态机
    external fun startEngine(baseUrl: String, roomId: String, targetLocation: String)

    // 控制接口：由 KT 决定何时调用
    external fun nextSong()

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
     * @return 音量，-1 为失败
     */
    external fun jumpToSecs(target: Int): Int

    /**
     * 获取歌曲标题
     */
    external fun getCurrentSongTitle(): String

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
}