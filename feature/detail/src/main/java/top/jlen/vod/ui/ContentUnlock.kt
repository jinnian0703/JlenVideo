package top.jlen.vod.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ContentUnlock(label: String, verify: suspend (String) -> Unit) {
    var showing by remember { mutableStateOf(false) }
    TextButton(onClick = { showing = true }) { Text("输入${label}密码") }
    if (!showing) return
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) showing = false }, title = { Text("${label}密码") }, text = {
        Column {
            OutlinedTextField(value = answer, onValueChange = { answer = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text("密码") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = answer.isNotBlank() && !busy, onClick = {
        busy = true
        scope.launch {
            try { verify(answer); showing = false }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = failure.message ?: "验证失败，请重试" }
            finally { busy = false }
        }
    }) { Text("解锁") } }, dismissButton = { TextButton(enabled = !busy, onClick = { showing = false }) { Text("取消") } })
}
