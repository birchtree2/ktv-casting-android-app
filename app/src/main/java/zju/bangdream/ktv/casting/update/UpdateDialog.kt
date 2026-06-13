package zju.bangdream.ktv.casting.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri

object UpdateDialog {

    fun showUpdateDialog(
        context: Context,
        releaseInfo: ReleaseInfo,
        onUpdate: () -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("发现新版本: ${releaseInfo.tagName}")

        // 截取前 300 个字符的更新日志
        val summary = releaseInfo.body
            .take(300)
            .replace("\n", " ")
            .trim()
        builder.setMessage(summary)

        // "现在更新" 按钮 - 直接打开浏览器下载
        builder.setPositiveButton("现在更新") { dialog, _ ->
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(releaseInfo.apkUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // 如果找不到浏览器，打开 release 页面
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(releaseInfo.htmlUrl)
                }
                context.startActivity(fallbackIntent)
            }
        }

        // "查看详情" 按钮
        builder.setNeutralButton("查看详情") { dialog, _ ->
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(releaseInfo.htmlUrl)
            }
            context.startActivity(intent)
        }

        // "稍后" 按钮
        builder.setNegativeButton("稍后", null)

        builder.setCancelable(true)
        builder.show()
    }
}
