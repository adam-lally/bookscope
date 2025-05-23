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
        engine {
            // Set a 15-second request timeout for the OpenLibrary API calls
            requestTimeout = 15000
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
// Helper function to calculate Jaro-Winkler distance
// Implementation based on https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance
fun jaroWinklerDistance(s1: String, s2: String, p: Double = 0.1): Double {
    val s1Lower = s1.lowercase()
    val s2Lower = s2.lowercase()

    val s1Length = s1Lower.length
    val s2Length = s2Lower.length

    if (s1Length == 0) return if (s2Length == 0) 1.0 else 0.0
    if (s2Length == 0) return 0.0

    val matchDistance = maxOf(s1Length, s2Length) / 2 - 1
    val s1Matches = BooleanArray(s1Length)
    val s2Matches = BooleanArray(s2Length)
    var matches = 0

    for (i in 0 until s1Length) {
        val start = maxOf(0, i - matchDistance)
        val end = minOf(i + matchDistance + 1, s2Length)
        for (j in start until end) {
            if (!s2Matches[j] && s1Lower[i] == s2Lower[j]) {
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
    }

    if (matches == 0) return 0.0

    var transpositions = 0
    var k = 0
    for (i in 0 until s1Length) {
        if (s1Matches[i]) {
            while (!s2Matches[k]) k++
            if (s1Lower[i] != s2Lower[k]) transpositions++
            k++
        }
    }

    val jaroSimilarity = (matches.toDouble() / s1Length +
            matches.toDouble() / s2Length +
            (matches - transpositions / 2.0) / matches) / 3.0

    // Winkler modification
    var l = 0 // length of common prefix at the start of the string
    while (l < minOf(s1Length, s2Length, 4) && s1Lower[l] == s2Lower[l]) {
        l++
    }

    return jaroSimilarity + l * p * (1 - jaroSimilarity)
}


// This function is internal for testing purposes
internal fun findBestMatch(title: String, author: String, docs: List<BookInfo>): BookInfo? {
    if (docs.isEmpty()) {
        return null
    }

    val normalizedTitle = title.lowercase()
    // Treat "Unknown" author as if no author information is provided for matching.
    val normalizedAuthor = author.takeIf { !it.equals("Unknown", ignoreCase = true) }?.lowercase()

    // Heuristic for selecting the best match:
    // 1. Exact case-insensitive title match + author match (if author is provided and matches)
    docs.firstOrNull { doc ->
        doc.title.lowercase() == normalizedTitle &&
                (normalizedAuthor == null || doc.author_name?.any { it.lowercase() == normalizedAuthor } == true)
    }?.let { return it }

    // 2. Exact case-insensitive title match (if author was not provided or didn't match in step 1)
    docs.firstOrNull { doc ->
        doc.title.lowercase() == normalizedTitle
    }?.let { return it }

    // 3. Case-insensitive title `contains` match + author match (if author is provided and matches)
    docs.firstOrNull { doc ->
        doc.title.lowercase().contains(normalizedTitle) &&
                (normalizedAuthor == null || doc.author_name?.any { it.lowercase() == normalizedAuthor } == true)
    }?.let { return it }

    // 4. Case-insensitive title `contains` match
    docs.firstOrNull { doc ->
        doc.title.lowercase().contains(normalizedTitle)
    }?.let { return it }

    // 5. Jaro-Winkler distance for partial title match
    val jaroWinklerThreshold = 0.8
    var bestMatchBySimilarity: BookInfo? = null
    var maxSimilarity = 0.0

    for (doc in docs) {
        val similarity = jaroWinklerDistance(normalizedTitle, doc.title.lowercase())
        val authorMatches = normalizedAuthor == null || doc.author_name?.any { it.lowercase() == normalizedAuthor } == true

        if (similarity >= jaroWinklerThreshold) { // Check against threshold first
            if (similarity > maxSimilarity) { // New highest similarity
                maxSimilarity = similarity
                bestMatchBySimilarity = doc
            } else if (similarity == maxSimilarity) { // Same similarity, prioritize based on author
                // If current bestMatchBySimilarity doesn't have an author, and this doc does, prefer this doc.
                if (bestMatchBySimilarity?.author_name.isNullOrEmpty() && !doc.author_name.isNullOrEmpty() && authorMatches) {
                    bestMatchBySimilarity = doc
                }
                // If both have authors, but current bestMatchBySimilarity's author didn't match normalizedAuthor,
                // and this doc's author *does* match, prefer this doc.
                else if (normalizedAuthor != null &&
                         !bestMatchBySimilarity?.author_name?.any { it.lowercase() == normalizedAuthor }!! &&
                         doc.author_name?.any { it.lowercase() == normalizedAuthor } == true) {
                    bestMatchBySimilarity = doc
                }
            }
        }
    }
    bestMatchBySimilarity?.let { return it }

    // 6. Fallback to the first result if no better match is found by the above heuristics.
    return docs.firstOrNull()
}

suspend fun getBookInfo(title: String, author: String): BookInfo? {
    val searchResult = searchBooks(title, author)
    return findBestMatch(title, author, searchResult.docs)
}

suspend fun main() {
    val title = "Daisy Jones & The Six"
    val author = "Unknown" // "Taylor Jenkins Reid"
    val info = getBookInfo(title, author)
    println(info)
}