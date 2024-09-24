package io.github.adam_lally.bookscope

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OpenLibrarySearchResult(
    val numFound: Int,
    val docs: List<BookInfo>
)

/**
 * Call the openlibrary API to search for books with the given title and author.
 */
suspend fun searchBooks(title: String, author: String?): OpenLibrarySearchResult {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val response: OpenLibrarySearchResult = client.get("https://openlibrary.org/search.json") {
        parameter("limit", 3)
        parameter("title", title)
        if (author != "Unknown") parameter("author", author)
    }.body()

    client.close()
    return response
}

/**
 * Get [BookInfo] for the first book found with the given title and author.
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