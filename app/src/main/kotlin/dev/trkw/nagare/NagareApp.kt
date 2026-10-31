package dev.trkw.nagare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import dev.trkw.nagare.core.Entry
import dev.trkw.nagare.core.Feed
import dev.trkw.nagare.core.Selection
import kotlinx.coroutines.launch

@Composable
fun NagareApp(model: ReaderViewModel, sharedUrl: String?, onSharedUrlUsed: () -> Unit) {
    if (model.client == null) LoginScreen(model) else ReaderScreen(model, sharedUrl, onSharedUrlUsed)

    model.error?.let { message ->
        AlertDialog(
            onDismissRequest = { model.error = null },
            confirmButton = { TextButton(onClick = { model.error = null }) { Text("OK") } },
            title = { Text("エラー") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun LoginScreen(model: ReaderViewModel) {
    var server by remember { mutableStateOf("nagare.trkw.dev") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Nagare", style = MaterialTheme.typography.headlineLarge)
        Text(
            "サーバーで作ったアプリ用トークン（bun run token:create --kind app）を入力してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            server, { server = it },
            label = { Text("サーバー") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            token, { token = it },
            label = { Text("トークン（ngr_...）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                busy = true
                model.login(server, token) { busy = false }
            },
            enabled = !busy && token.isNotBlank() && server.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "確認中…" else "ログイン") }
    }
}

@Composable
private fun ReaderScreen(model: ReaderViewModel, sharedUrl: String?, onSharedUrlUsed: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var subscribing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            subscribing = sharedUrl
            onSharedUrlUsed()
        }
    }

    val entry = model.openEntry
    if (entry != null) {
        BackHandler { model.close() }
        ArticleScreen(model, entry)
    } else {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                FeedDrawer(model) { selection ->
                    model.select(selection)
                    scope.launch { drawer.close() }
                }
            },
        ) {
            EntryListScreen(
                model,
                onMenu = { scope.launch { drawer.open() } },
                onAdd = { subscribing = "" },
            )
        }
    }

    subscribing?.let { initial -> SubscribeDialog(model, initial) { subscribing = null } }
}

@Composable
private fun FeedDrawer(model: ReaderViewModel, onSelect: (Selection) -> Unit) {
    var confirm by remember { mutableStateOf<Feed?>(null) }
    ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            item {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Nagare", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = model::logout) { Text("ログアウト") }
                }
                DrawerRow("すべて", model.totalUnread, model.selection == Selection.All) { onSelect(Selection.All) }
                DrawerRow("あとで読む", 0, model.selection == Selection.Starred) { onSelect(Selection.Starred) }
            }
            model.folders.forEach { (folder, list) ->
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    if (folder != null) {
                        DrawerRow(folder, list.sumOf { it.unread }, model.selection == Selection.Folder(folder), bold = true) {
                            onSelect(Selection.Folder(folder))
                        }
                    } else {
                        Text("フォルダなし", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp, 4.dp))
                    }
                }
                items(list, key = { it.id }) { feed ->
                    val by = feed.addedBy?.takeIf { it != model.me?.name }
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(
                                    feed.displayTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (feed.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                if (by != null) Text("$by が追加", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        badge = { if (feed.unread > 0) Text("${feed.unread}") },
                        selected = model.selection == Selection.OneFeed(feed.id),
                        onClick = { onSelect(Selection.OneFeed(feed.id)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item {
                TextButton(onClick = { model.selection.let { s -> if (s is Selection.OneFeed) confirm = model.feeds.firstOrNull { it.id == s.id } } }) {
                    Text("選択中のフィードの購読をやめる")
                }
            }
        }
    }
    confirm?.let { feed ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            confirmButton = { TextButton(onClick = { model.unsubscribe(feed); confirm = null }) { Text("やめる") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("キャンセル") } },
            text = { Text("「${feed.displayTitle}」の購読をやめますか？") },
        )
    }
}

@Composable
private fun DrawerRow(label: String, count: Int, selected: Boolean, bold: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (bold) FontWeight.SemiBold else null) },
        badge = { if (count > 0) Text("$count") },
        selected = selected,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryListScreen(model: ReaderViewModel, onMenu: () -> Unit, onAdd: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "フィード") } },
                actions = {
                    if (model.selection != Selection.Starred) {
                        IconToggleButton(checked = model.unreadOnly, onCheckedChange = { model.toggleUnreadOnly() }) {
                            Icon(Icons.Default.FilterList, if (model.unreadOnly) "未読のみ" else "すべて表示")
                        }
                    }
                    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "購読") }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = model.loading && model.entries.isEmpty(),
            onRefresh = model::reload,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                val reason = (model.selection as? Selection.OneFeed)?.let { s -> model.feeds.firstOrNull { it.id == s.id }?.reason }
                if (reason != null) {
                    item { Text(reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 8.dp)) }
                }
                items(model.entries, key = { it.id }) { entry ->
                    EntryRow(entry, onClick = { model.open(entry) })
                    HorizontalDivider()
                }
                if (model.hasMore) {
                    item {
                        TextButton(onClick = { model.loadEntries(more = true) }, enabled = !model.loading, modifier = Modifier.fillMaxWidth()) {
                            Text("さらに読み込む")
                        }
                    }
                }
                if (!model.loading && model.entries.isEmpty()) {
                    item { Text("記事はありません", modifier = Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 12.dp)) {
        Row {
            Text(
                entry.feedTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateUtils.getRelativeTimeSpanString(entry.publishedAt).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.Top) {
            if (entry.isStarred) {
                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                entry.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.isRead) FontWeight.Normal else FontWeight.SemiBold,
                color = if (entry.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SubscribeDialog(model: ReaderViewModel, initial: String, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var noFeed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("購読") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    url, { url = it; noFeed = false },
                    label = { Text("サイトかフィードのURL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (noFeed) {
                    Text("フィードが見つかりませんでした。ページの更新を見張ることもできます。", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            if (model.watchPage(url)) onDismiss()
                            busy = false
                        }
                    }) { Text("このページを見張る") }
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && url.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    when (model.subscribe(url)) {
                        SubscribeOutcome.Subscribed -> onDismiss()
                        SubscribeOutcome.NoFeed -> noFeed = true
                        SubscribeOutcome.Failed -> Unit
                    }
                    busy = false
                }
            }) { Text("購読") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
