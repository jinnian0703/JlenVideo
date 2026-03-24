package top.jlen.vod.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal fun openExternalUrl(
    context: Context,
    url: String,
    failureMessage: String = "无法打开更新链接，请稍后重试"
): Boolean {
    val targetUrl = url.trim()
    if (targetUrl.isBlank()) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        return false
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    return runCatching {
        context.startActivity(intent)
        true
    }.recoverCatching {
        val chooserIntent = Intent.createChooser(intent, null).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooserIntent)
        true
    }.getOrElse {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        false
    }
}
