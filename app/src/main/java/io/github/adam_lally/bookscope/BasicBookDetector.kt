package io.github.adam_lally.bookscope

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * BookDetector implementation that first calls the LLM to identify book titles and authors,
 * then queries OpenLibrary to get the BookInfo for each book detected.
 */
class BasicBookDetector : BookDetector {
    override suspend fun detectBooksInImage(imageUrl: String): BookDetectorResult = coroutineScope {
        try {
            // Call the LLM to find books in the image
            val books =
                findBooksInImage(imageUrl)

            if (books.isEmpty()) {
                BookDetectorResult(message = "No books found")
            } else {
                // For each book, get the book info from OpenLibrary
                val bookInfoFutures = books.map {
                    async {
                        getBookInfo(it.title, it.author)
                    }
                }
                BookDetectorResult(bookInfo = bookInfoFutures.awaitAll().filterNotNull())
            }
        } catch (e: Exception) {
            BookDetectorResult(message = "Error: $e")
        }
    }
}

fun main() {
    runBlocking {
        val detector = BasicBookDetector()
        //val url = "https://prh.imgix.net/articles/top10-fiction-1600x800.jpg"
        val image = File("myBooks.jpg").readBytes()
        val result = detector.detectBooksInImage(image)
        for (book in result.bookInfo) {
            println("Title: ${book.title}, Author: ${book.author_name}, Rating: ${book.ratings_average}")
        }
    }
}