package io.github.adam_lally.bookscope

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withTimeout
import io.github.adam_lally.bookscope.NetworkClients
import io.github.adam_lally.bookscope.retryWithBackoff

@Serializable
data class OpenLibrarySearchResult(
    val numFound: Int,
    val docs: List<BookInfo>
)

/**
 * Call the openlibrary API to search for books with the given title and author.
 */
suspend fun searchBooks(title: String, author: String?): OpenLibrarySearchResult {
    val client = NetworkClients.openLibraryHttpClient

    val response: OpenLibrarySearchResult = withTimeout(15_000) {
        retryWithBackoff {
            client.get("https://openlibrary.org/search.json") {
                parameter("limit", 3)
                parameter("title", title)
                if (author != "Unknown") parameter("author", author)
            }.body()
        }
    }

    return response
}

/**
 */
suspend fun getBookInfo(title: String, author: String): BookInfo? {
    val searchResult = searchBooks(title, author)
    // For now I'm assuming the first result is the correct one, but it might not always be.
    // Sometimes, the search will fail to find any result, and maybe we're losing good
    // information in that case, but maybe this catches some hallucinations as well.
    // I'm not sure what's best.
    return searchResult.docs.firstOrNull()
}

suspend fun main() {
    val title = "Daisy Jones & The Six"
    val author = "Unknown" // "Taylor Jenkins Reid"
    val info = getBookInfo(title, author)
    println(info)
}