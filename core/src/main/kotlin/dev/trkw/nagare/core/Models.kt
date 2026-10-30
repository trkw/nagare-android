package dev.trkw.nagare.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Feed(
    val id: Int,
    val kind: String,
    val url: String,
    val title: String,
    @SerialName("site_url") val siteUrl: String? = null,
    val folder: String? = null,
    @SerialName("added_by") val addedBy: String? = null,
    val reason: String? = null,
    val selector: String? = null,
    val credential: String? = null,
    val notify: Int = 0,
    val unread: Int = 0,
    @SerialName("last_error") val lastError: String? = null,
) {
    val displayTitle: String get() = title.ifEmpty { url }
}

@Serializable
data class Entry(
    val id: Int,
    @SerialName("feed_id") val feedId: Int,
    @SerialName("feed_title") val feedTitle: String,
    val url: String? = null,
    val title: String,
    val author: String? = null,
    val content: String? = null,
    /** Unix milliseconds. */
    @SerialName("published_at") val publishedAt: Long,
    val read: Int = 0,
    val starred: Int = 0,
) {
    val isRead: Boolean get() = read != 0
    val isStarred: Boolean get() = starred != 0
    val displayTitle: String get() = title.ifEmpty { "（タイトルなし）" }
}

@Serializable
data class Me(val name: String, val kind: String, val scopes: List<String>)

@Serializable
data class Translation(val title: String, val content: String, val engine: String, val cached: Boolean)

/** What the entry list shows. */
sealed interface Selection {
    data object All : Selection
    data object Starred : Selection
    data class Folder(val name: String) : Selection
    data class OneFeed(val id: Int) : Selection
}

data class EntryQuery(
    val feed: Int? = null,
    val folder: String? = null,
    val unread: Boolean = false,
    val starred: Boolean = false,
    val search: String? = null,
    val before: Long? = null,
    val beforeId: Int? = null,
    val limit: Int = 50,
) {
    fun params(): List<Pair<String, String>> = buildList {
        add("limit" to limit.toString())
        feed?.let { add("feed" to it.toString()) }
        folder?.let { add("folder" to it) }
        if (unread) add("unread" to "1")
        if (starred) add("starred" to "1")
        search?.takeIf { it.isNotEmpty() }?.let { add("q" to it) }
        before?.let { add("before" to it.toString()) }
        beforeId?.let { add("before_id" to it.toString()) }
    }

    companion object {
        fun of(selection: Selection, unreadOnly: Boolean): EntryQuery = when (selection) {
            Selection.All -> EntryQuery(unread = unreadOnly)
            Selection.Starred -> EntryQuery(starred = true)
            is Selection.Folder -> EntryQuery(folder = selection.name, unread = unreadOnly)
            is Selection.OneFeed -> EntryQuery(feed = selection.id, unread = unreadOnly)
        }
    }
}
