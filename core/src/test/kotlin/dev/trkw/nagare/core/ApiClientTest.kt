package dev.trkw.nagare.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ApiClientTest {
    private lateinit var server: HttpServer
    private val requests = mutableListOf<Triple<String, String, String>>()

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val body = ex.requestBody.readBytes().decodeToString()
            requests += Triple("${ex.requestMethod} ${ex.requestURI}", ex.requestHeaders.getFirst("Authorization") ?: "", body)
            val (status, response) = when (ex.requestURI.path) {
                "/api/feeds" -> 200 to """[{"id":1,"kind":"web","url":"https://e.com/news","title":"","folder":"見張り","added_by":"grokbot","notify":1,"unread":3,"extra":true}]"""
                "/api/entries" -> 200 to """[{"id":7,"feed_id":1,"feed_title":"F","url":null,"title":"","author":null,"published_at":1793332858894,"read":0,"starred":1}]"""
                "/api/entries/state" -> 200 to """{"updated":1}"""
                "/api/entries/7/translate" -> 200 to """{"title":"題","content":"<p>本文</p>","engine":"x","cached":false}"""
                else -> 403 to """{"error":"token lacks the \"write\" scope"}"""
            }
            val bytes = response.encodeToByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun client() = ApiClient("http://127.0.0.1:${server.address.port}/", "ngr_x")

    @Test
    fun decodesFeedsAndEntries() = runTest {
        val feeds = client().feeds()
        assertEquals("https://e.com/news", feeds[0].displayTitle)
        assertEquals("grokbot", feeds[0].addedBy)
        val entries = client().entries(EntryQuery.of(Selection.Folder("見張り"), unreadOnly = true))
        assertTrue(entries[0].isStarred)
        assertEquals("（タイトルなし）", entries[0].displayTitle)
        assertEquals("GET /api/entries?limit=50&folder=%E8%A6%8B%E5%BC%B5%E3%82%8A&unread=1", requests[1].first)
        assertEquals("Bearer ngr_x", requests[1].second)
    }

    @Test
    fun postsJsonAndQuery() = runTest {
        client().setState(listOf(7, 8), read = true)
        assertEquals("POST /api/entries/state", requests[0].first)
        assertEquals("""{"ids":[7,8],"read":true}""", requests[0].third)
        assertEquals("題", client().translate(7).title)
        assertEquals("POST /api/entries/7/translate?lang=ja", requests[1].first)
    }

    @Test
    fun surfacesServerErrors() = runTest {
        val e = assertFailsWith<ApiException> { client().handoff(listOf(1), "メモ") }
        assertEquals(403, e.status)
        assertEquals("token lacks the \"write\" scope", e.message)
    }

    @Test
    fun plainText() {
        assertEquals("こんにちは 世界\n次", HtmlText.plain("<p>こんにちは&nbsp;<b>世界</b></p><script>x()</script><p>次</p>"))
    }
}
