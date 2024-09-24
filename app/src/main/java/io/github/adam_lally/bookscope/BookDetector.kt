package io.github.adam_lally.bookscope

import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * Interface for detecting books in an image.
 */
interface BookDetector {
    /**
     * Detect books in the imaae at [imageUrl].
     */
    suspend fun detectBooksInImage(imageUrl : String): BookDetectorResult

    /**
     * Detect books in the image represented by [imageBytes].
     */
    suspend fun detectBooksInImage(imageBytes : ByteArray) : BookDetectorResult {
        val base64image = Base64.getEncoder().encodeToString(imageBytes)
        return detectBooksInImage("data:image/jpeg;base64,$base64image")
    }
}

@Serializable
data class BookDetectorResult(
    val bookInfo: List<BookInfo> = emptyList(),
    val message: String = ""
)
@Serializable
data class BookInfo(
    val title: String,
    val author_name: List<String>? = null,
    val ratings_average: Double? = null
)

@Serializable
data class BookInfos(
    val books: List<BookInfo>
)