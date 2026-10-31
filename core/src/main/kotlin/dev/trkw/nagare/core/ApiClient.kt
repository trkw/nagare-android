package dev.trkw.nagare.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(val status: Int, message: String) : Exception(message)

/** Thin client for the Nagare REST API (trkw/nagare apps/api). */
class ApiClient(
    val baseUrl: String,
    val token: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    internal fun url(path: String, params: List<Pair<String, String>> = emptyList()): HttpUrl =
        base.newBuilder().apply {
            addPathSegment("api")
            path.trim('/').split('/').forEach { addPathSegment(it) }
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private suspend fun send(method: String, url: HttpUrl, body: JsonElement? = null): String = withContext(Dispatchers.IO) {
        val requestBody = body?.toString()?.toRequestBody(JSON_TYPE)
            ?: if (method == "POST") ByteArray(0).toRequestBody(null) else null
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, requestBody)
            .build()
        http.newCall(request).execute().use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) {
                val message = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                throw ApiException(res.code, message ?: "HTTP ${res.code}")
            }
            text
        }
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>, params: List<Pair<String, String>> = emptyList()): T =
        json.decodeFromString(serializer, send("GET", url(path, params)))

    private suspend fun <T> post(
        path: String,
        serializer: KSerializer<T>,
        body: JsonObject? = null,
        params: List<Pair<String, String>> = emptyList(),
        method: String = "POST",
    ): T = json.decodeFromString(serializer, send(method, url(path, params), body))

    @Serializable private data class FeedResult(val feed: Feed)
    @Serializable private data class Content(val content: String)
    @Serializable private data class Updated(val updated: Int)
    @Serializable private data class Created(val id: Int)

    suspend fun me(): Me = get("/me", Me.serializer())

    suspend fun feeds(): List<Feed> = get("/feeds", ListSerializer(Feed.serializer()))

    suspend fun subscribe(url: String, folder: String? = null): Feed =
        post("/feeds", FeedResult.serializer(), buildJsonObject {
            put("url", url)
            folder?.let { put("folder", it) }
        }).feed

    suspend fun watchPage(url: String): Feed =
        post("/web-feeds", FeedResult.serializer(), buildJsonObject { put("url", url) }).feed

    suspend fun unsubscribe(feedId: Int) {
        send("DELETE", url("/feeds/$feedId"))
    }

    suspend fun entries(query: EntryQuery): List<Entry> = get("/entries", ListSerializer(Entry.serializer()), query.params())

    suspend fun entry(id: Int): Entry = get("/entries/$id", Entry.serializer())

    suspend fun fullText(entryId: Int): String = post("/entries/$entryId/fulltext", Content.serializer()).content

    suspend fun translate(entryId: Int, lang: String = "ja"): Translation =
        post("/entries/$entryId/translate", Translation.serializer(), params = listOf("lang" to lang))

    suspend fun setState(ids: List<Int>, read: Boolean? = null, starred: Boolean? = null) {
        post("/entries/state", Updated.serializer(), buildJsonObject {
            putJsonArray("ids") { ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            read?.let { put("read", it) }
            starred?.let { put("starred", it) }
        })
    }

    suspend fun handoff(entryIds: List<Int>, note: String, toAgent: String? = null) {
        post("/handoffs", Created.serializer(), buildJsonObject {
            putJsonArray("entry_ids") { entryIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            put("note", note)
            toAgent?.takeIf { it.isNotBlank() }?.let { put("to_agent", it) }
        })
    }

    /** Registers this device for FCM push notifications. */
    suspend fun registerPush(fcmToken: String, name: String) {
        post("/push/targets", Created.serializer(), buildJsonObject {
            put("platform", "fcm")
            put("token", fcmToken)
            put("name", name)
        })
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
        internal val json = Json { ignoreUnknownKeys = true }
    }
}
