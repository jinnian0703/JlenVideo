package top.jlen.vod.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap

@Composable
internal fun LoginChallengeDialog(image: ByteArray, busy: Boolean, dismiss: () -> Unit, submit: (String) -> Unit, refresh: () -> Unit) {
    var answer by remember(image) { mutableStateOf("") }
    val bitmap = remember(image) { BitmapFactory.decodeByteArray(image, 0, image.size)?.asImageBitmap() }
    AlertDialog(onDismissRequest = dismiss, title = { Text("登录验证码") }, text = {
        Column {
            bitmap?.let { Image(it, "图形验证码") }
            TextButton(enabled = !busy, onClick = refresh) { Text("换一张") }
            OutlinedTextField(value = answer, onValueChange = { answer = it }, singleLine = true, label = { Text("输入图中的字符") })
        }
    }, confirmButton = { TextButton(enabled = answer.isNotBlank() && !busy, onClick = { submit(answer.trim()) }) { Text("登录") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
