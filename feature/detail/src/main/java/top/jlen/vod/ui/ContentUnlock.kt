package top.jlen.vod.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ContentUnlock(label: String, inline: Boolean = false, verify: suspend (String) -> Unit) {
    var showing by remember { mutableStateOf(false) }
    if (!showing || !inline) TextButton(onClick = { showing = true }) { Text("输入${label}密码") }
    if (showing) ContentUnlockPrompt(label, inline, onDismiss = { showing = false }, verify = verify)
}

@Composable
internal fun ContentUnlockPrompt(label: String, inline: Boolean = false, onDismiss: () -> Unit,
                                onVerified: () -> Unit = onDismiss, verify: suspend (String) -> Unit) {
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val input: @Composable () -> Unit = {
        Column {
            OutlinedTextField(value = answer, onValueChange = { answer = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text("${label}密码") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    val submit: @Composable () -> Unit = { TextButton(enabled = answer.isNotBlank() && !busy, onClick = {
        busy = true
        scope.launch {
            try { verify(answer); onVerified() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = failure.message ?: "验证失败，请重试" }
            finally { busy = false }
        }
    }) { Text("解锁") } }
    val cancel: @Composable () -> Unit = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } }
    if (inline) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        input()
        Row { submit(); cancel() }
    } else AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("${label}密码") },
        text = input, confirmButton = submit, dismissButton = cancel)
}
