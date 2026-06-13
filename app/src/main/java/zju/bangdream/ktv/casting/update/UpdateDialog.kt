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

        // 清理 Markdown 符号并截取前 300 个字符
        val summary = releaseInfo.body
            .replace(Regex("#{1,6}\\s*"), "")
            .replace(Regex("\\*{1,3}"), "")
            .replace(Regex("_"), "")
            .replace(Regex("`"), "")
            .replace(Regex("\n{2,}"), "\n")
            .take(300)
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
