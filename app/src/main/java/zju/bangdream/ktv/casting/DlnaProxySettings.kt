package zju.bangdream.ktv.casting

import android.content.Context

object DlnaProxySettings {
    // Keep the old key so existing users retain their choice after the label is inverted.
    const val DIRECT_KEY = "dlna_direct"

    fun isEnabled(context: Context): Boolean =
        !context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE)
            .getBoolean(DIRECT_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        RustEngine.setDlnaDirect(!enabled)
        context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(DIRECT_KEY, !enabled).apply()
    }
}
