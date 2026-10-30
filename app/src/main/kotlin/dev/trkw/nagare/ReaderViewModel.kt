package dev.trkw.nagare

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.trkw.nagare.core.ApiClient
import dev.trkw.nagare.core.ApiException
import dev.trkw.nagare.core.Entry
import dev.trkw.nagare.core.EntryQuery
import dev.trkw.nagare.core.Feed
import dev.trkw.nagare.core.Me
import dev.trkw.nagare.core.Selection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class SubscribeOutcome { Subscribed, NoFeed, Failed }

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TokenStore(app)

    var client by mutableStateOf<ApiClient?>(null)
        private set
    var me by mutableStateOf<Me?>(null)
        private set
    var feeds by mutableStateOf<List<Feed>>(emptyList())
        private set
    var selection by mutableStateOf<Selection>(Selection.All)
        private set
    var unreadOnly by mutableStateOf(true)
        private set
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set
    var openEntryId by mutableStateOf<Int?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** Bumped on login and logout so responses from a previous session are dropped. */
    private var session = 0
    private var listJob: Job? = null
    private val pageSize = 50

    val openEntry: Entry? get() = openEntryId?.let { id -> entries.firstOrNull { it.id == id } }

    val folders: List<Pair<String?, List<Feed>>>
        get() = feeds.groupBy { it.folder }
            .toList()
            .sortedWith(compareBy<Pair<String?, List<Feed>>> { it.first == null }.thenBy { it.first })
            .map { (name, list) -> name to list.sortedBy { it.displayTitle } }

    val totalUnread: Int get() = feeds.sumOf { it.unread }

    val title: String
        get() = when (val s = selection) {
            Selection.All -> "すべて"
            Selection.Starred -> "あとで読む"
            is Selection.Folder -> s.name
            is Selection.OneFeed -> feeds.firstOrNull { it.id == s.id }?.displayTitle ?: ""
        }

    init {
        val server = store.server
        val token = store.token
        if (server != null && token != null) {
            client = ApiClient(server, token)
            reload()
        }
    }

    fun login(server: String, token: String, onDone: () -> Unit) {
        var url = server.trim()
        if (!url.contains("://")) url = "https://$url"
        val candidate = runCatching { ApiClient(url, token.trim()) }.getOrElse {
            error = "サーバーのURLが正しくありません"
            onDone()
            return
        }
        viewModelScope.launch {
            try {
                me = candidate.me()
                store.server = url
                store.token = candidate.token
                session++
                client = candidate
                reload()
            } catch (e: Exception) {
                report(e)
            } finally {
                onDone()
            }
        }
    }

    fun logout() {
        session++
        listJob?.cancel()
        store.token = null
        client = null
        me = null
        feeds = emptyList()
        entries = emptyList()
        openEntryId = null
    }

    fun reload() {
        refreshFeeds()
        loadEntries()
    }

    fun refreshFeeds() {
        val c = client ?: return
        val current = session
        viewModelScope.launch {
            try {
                val result = c.feeds()
                if (current == session) feeds = result
                if (me == null && current == session) me = c.me()
            } catch (e: Exception) {
                if (current == session) report(e)
            }
        }
    }

    fun select(s: Selection) {
        selection = s
        loadEntries()
    }

    fun toggleUnreadOnly() {
        unreadOnly = !unreadOnly
        loadEntries()
    }

    fun loadEntries(more: Boolean = false) {
        val c = client ?: return
        val current = session
        if (!more) listJob?.cancel()
        var query = EntryQuery.of(selection, unreadOnly).copy(limit = pageSize)
        if (more) entries.lastOrNull()?.let { query = query.copy(before = it.publishedAt, beforeId = it.id) }
        loading = true
        listJob = viewModelScope.launch {
            try {
                val page = c.entries(query)
                if (current != session) return@launch
                entries = if (more) entries + page else page
                hasMore = page.size == pageSize
            } catch (e: Exception) {
                if (current == session && e !is kotlinx.coroutines.CancellationException) report(e)
            } finally {
                if (current == session) loading = false
            }
        }
    }

    fun open(entry: Entry) {
        openEntryId = entry.id
        if (!entry.isRead) setRead(entry, true)
        if (entry.content != null) return
        val c = client ?: return
        val current = session
        viewModelScope.launch {
            try {
                val full = c.entry(entry.id)
                if (current == session) patch(entry.id) { it.copy(content = full.content ?: "") }
            } catch (e: Exception) {
                if (current == session) report(e)
            }
        }
    }

    fun close() {
        openEntryId = null
    }

    fun patch(id: Int, change: (Entry) -> Entry) {
        entries = entries.map { if (it.id == id) change(it) else it }
    }

    fun setRead(entry: Entry, read: Boolean) {
        patch(entry.id) { it.copy(read = if (read) 1 else 0) }
        adjustUnread(entry.feedId, if (read) -1 else 1)
        viewModelScope.launch {
            try {
                client?.setState(listOf(entry.id), read = read)
            } catch (e: Exception) {
                patch(entry.id) { it.copy(read = if (read) 0 else 1) }
                adjustUnread(entry.feedId, if (read) 1 else -1)
                report(e)
            }
        }
    }

    fun setStarred(entry: Entry, starred: Boolean) {
        patch(entry.id) { it.copy(starred = if (starred) 1 else 0) }
        viewModelScope.launch {
            try {
                client?.setState(listOf(entry.id), starred = starred)
            } catch (e: Exception) {
                patch(entry.id) { it.copy(starred = if (starred) 0 else 1) }
                report(e)
            }
        }
    }

    private fun adjustUnread(feedId: Int, delta: Int) {
        feeds = feeds.map { if (it.id == feedId) it.copy(unread = maxOf(0, it.unread + delta)) else it }
    }

    suspend fun subscribe(url: String): SubscribeOutcome {
        val c = client ?: return SubscribeOutcome.Failed
        return try {
            val feed = c.subscribe(url.trim())
            refreshFeeds()
            select(Selection.OneFeed(feed.id))
            SubscribeOutcome.Subscribed
        } catch (e: ApiException) {
            if (e.message?.startsWith("no feed found") == true) SubscribeOutcome.NoFeed else SubscribeOutcome.Failed.also { report(e) }
        } catch (e: Exception) {
            report(e)
            SubscribeOutcome.Failed
        }
    }

    suspend fun watchPage(url: String): Boolean {
        val c = client ?: return false
        return try {
            val feed = c.watchPage(url.trim())
            refreshFeeds()
            select(Selection.OneFeed(feed.id))
            true
        } catch (e: Exception) {
            report(e)
            false
        }
    }

    fun unsubscribe(feed: Feed) {
        viewModelScope.launch {
            try {
                client?.unsubscribe(feed.id)
                if (selection == Selection.OneFeed(feed.id)) select(Selection.All)
                refreshFeeds()
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun report(e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) return
        error = e.message ?: e.toString()
    }
}
