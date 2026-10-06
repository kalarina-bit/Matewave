package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.whiteOutlinedTextFieldColors
import cc.skysparkle.matewave.viewmodel.ChatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatDialog(
    messages: List<ChatEntry>,
    onSendText: (String) -> Unit,
    onSendImage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sendingImage by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sendingImage = true
        scope.launch {
            val encoded = withContext(Dispatchers.IO) { encodeImageForChat(context, uri) }
            sendingImage = false
            if (encoded != null) onSendImage(encoded)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chat_title),
        actions = { DialogAction(stringResource(R.string.chat_close), onDismiss) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 320.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                DialogText(stringResource(R.string.chat_empty))
            }
            messages.forEach { entry -> ChatBubble(entry) }
        }

        if (sendingImage) {
            GlassProgress(stringResource(R.string.chat_sending_image))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassOutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                AppIcon(R.drawable.ic_add_photo, size = 22.dp)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint), color = ink.copy(alpha = 0.5f)) },
                colors = whiteOutlinedTextFieldColors()
            )
            GlassButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendText(text.trim())
                        text = ""
                    }
                },
                enabled = text.isNotBlank()
            ) {
                AppIcon(R.drawable.ic_arrow_forward, size = 20.dp)
            }
        }
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (entry.fromMe) Arrangement.End else Arrangement.Start
    ) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .background(if (entry.fromMe) ChessGreen.copy(alpha = 0.55f) else ink.copy(alpha = 0.15f), shape)
                .padding(10.dp)
        ) {
            entry.text?.let { Text(it, color = ink) }
            entry.imageBase64?.let { b64 ->
                val image = rememberChatImage(b64)
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(180.dp)
                    )
                }
            }
        }
    }
}
