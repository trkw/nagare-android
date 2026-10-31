package dev.trkw.nagare

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.trkw.nagare.core.Entry
import dev.trkw.nagare.core.HtmlText
import dev.trkw.nagare.core.Translation
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(model: ReaderViewModel, entry: Entry) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var translation by remember(entry.id) { mutableStateOf<Translation?>(null) }
    var showTranslation by remember(entry.id) { mutableStateOf(false) }
    var busy by remember(entry.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var handoff by remember { mutableStateOf(false) }
    val speech = rememberSpeech()

    val title = if (showTranslation) translation?.title ?: entry.displayTitle else entry.displayTitle
    val content = if (showTranslation) translation?.content ?: "" else entry.content ?: ""

    fun launchBusy(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                model.report(e)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = model::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "一覧") } },
                actions = {
                    IconButton(onClick = { model.setStarred(entry, !entry.isStarred) }) {
                        Icon(if (entry.isStarred) Icons.Default.Star else Icons.Default.StarBorder, "あとで読む")
                    }
                    IconButton(enabled = !busy, onClick = {
                        when {
                            showTranslation -> showTranslation = false
                            translation != null -> showTranslation = true
                            else -> launchBusy {
                                translation = model.client?.translate(entry.id)
                                showTranslation = translation != null
                            }
                        }
                    }) { Icon(Icons.Default.Translate, if (showTranslation) "原文" else "翻訳") }
                    IconButton(onClick = {
                        if (speech.speaking) speech.stop() else speech.speak("$title。${HtmlText.plain(content)}")
                    }) {
                        Icon(if (speech.speaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp, "読み上げ")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "その他") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (entry.isRead) "未読にする" else "既読にする") }, onClick = {
                                menu = false
                                model.setRead(entry, !entry.isRead)
                            })
                            if (entry.url != null) {
                                DropdownMenuItem(text = { Text("全文を取得") }, enabled = !busy, onClick = {
                                    menu = false
                                    launchBusy {
                                        val full = model.client?.fullText(entry.id) ?: return@launchBusy
                                        model.patch(entry.id) { it.copy(content = full) }
                                        translation = null
                                        showTranslation = false
                                    }
                                })
                            }
                            DropdownMenuItem(text = { Text("エージェントに送る") }, onClick = { menu = false; handoff = true })
                            entry.url?.let { url ->
                                DropdownMenuItem(text = { Text("元の記事を開く") }, onClick = {
                                    menu = false
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                })
                                DropdownMenuItem(text = { Text("共有") }, onClick = {
                                    menu = false
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                                    context.startActivity(Intent.createChooser(send, null))
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.JAPAN).format(Date(entry.publishedAt))
                Text(
                    listOfNotNull(entry.feedTitle, entry.author?.takeIf { it.isNotEmpty() }, date).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            if (entry.content == null || busy) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            if (entry.content != null) HtmlView(content, entry.url, Modifier.fillMaxSize().padding(horizontal = 8.dp))
        }
    }

    if (handoff) HandoffDialog(model, entry) { handoff = false }
}

/** Feed HTML with JavaScript off; links open in the browser. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlView(html: String, baseUrl: String?, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val document = remember(html, dark) { document(html, dark) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL(baseUrl, document, "text/html", "utf-8", null)
            }
        },
    )
}

private fun document(body: String, dark: Boolean): String {
    val text = if (dark) "#e6e6e6" else "#1a1a1a"
    val muted = if (dark) "#9a9a9a" else "#666"
    val link = if (dark) "#8ab4f8" else "#1a5fb4"
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body { font-family: sans-serif; font-size: 17px; line-height: 1.8; color: $text; background: transparent;
                 margin: 0; padding: 0 8px 48px; overflow-wrap: anywhere; }
          img, video, iframe { max-width: 100%; height: auto; }
          pre { overflow-x: auto; padding: 12px; background: rgba(127,127,127,.12); border-radius: 8px; }
          blockquote { margin: 0; padding-left: 12px; border-left: 3px solid rgba(127,127,127,.35); color: $muted; }
          a { color: $link; }
        </style></head><body>$body</body></html>
    """.trimIndent()
}

/** Japanese text-to-speech, released when the article closes. */
class Speech(context: android.content.Context) {
    var speaking by mutableStateOf(false)
        private set
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            // Each chunk reports onDone; only the last one ends playback.
            override fun onDone(id: String?) { if (id == "last") speaking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { speaking = false }
        })
    }

    fun speak(text: String) {
        if (!ready) return
        tts.language = Locale.JAPANESE
        // TextToSpeech takes at most getMaxSpeechInputLength() characters per call, so queue chunks.
        val chunks = text.chunked(TextToSpeech.getMaxSpeechInputLength() - 1)
        chunks.forEachIndexed { i, chunk ->
            tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == chunks.lastIndex) "last" else "part$i")
        }
        speaking = true
    }

    fun stop() {
        tts.stop()
        speaking = false
    }

    fun shutdown() = tts.shutdown()
}

@Composable
private fun rememberSpeech(): Speech {
    val context = LocalContext.current
    val speech = remember { Speech(context) }
    DisposableEffect(Unit) { onDispose { speech.shutdown() } }
    return speech
}

@Composable
private fun HandoffDialog(model: ReaderViewModel, entry: Entry, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    var agent by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("エージェントに送る") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(note, { note = it }, label = { Text("メモ") }, minLines = 3)
                OutlinedTextField(agent, { agent = it }, label = { Text("送り先（空ならどのエージェントでも）") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = !sending, onClick = {
                sending = true
                scope.launch {
                    try {
                        model.client?.handoff(listOf(entry.id), note, agent)
                        onDismiss()
                    } catch (e: Exception) {
                        model.report(e)
                    } finally {
                        sending = false
                    }
                }
            }) { Text("送る") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
