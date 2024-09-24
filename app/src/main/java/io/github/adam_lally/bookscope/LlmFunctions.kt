package io.github.adam_lally.bookscope

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.ToolBuilder
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.bookscope.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

@Serializable
data class Book(
    val title: String,
    val author: String
)

@Serializable
data class Books (
    val books: List<Book>
)

/**
 * Call the OpenAI API to identify book titles and authors in the given image.
 * Provide a [url] for the image, or specify it using base64 encoding by using the url
 * "data:image/jpeg;base64,$base64image"
 */
suspend fun findBooksInImage(url: String) : List<Book> {
    val openAi = OpenAI(BuildConfig.OPENAI_API_KEY)
    val chatMessages = listOf(
            ChatMessage(
                role = ChatRole.System,
                content = "You are a helpful assistant who identifies all of the books in an image. Identify the titles and authors. " +
                          "Check carefully to make sure the book is actually there.  If you aren't sure about the author, report it as \"Unknown\"."
            ),
            ChatMessage(
                role = ChatRole.User,
                messageContent = ListContent(
                    listOf(
//                        TextPart("What books are in this image?"),
                        ImagePart(
                            url = url,
                            detail = "high")
                    )
                )
            ),
        )

    //The kotlin OpenAI API doesn't support structured responses yet, so we use the older way
    //Of providing a function with a json schema for the response.
    //This is currently not handling the case where the response is invalid or doesn't
    //conform to the schema.
    val chatCompletionRequest = chatCompletionRequest {
        model = ModelId("gpt-4o-mini")
        messages = chatMessages
        tools {
            function(
                name = "foundBooks",
                description = "Report all the books that were found in the image",
            ) {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("books") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("title") {
                                    put("type", "string")
                                    put("description", "The title of the book")
                                }
                                putJsonObject("author") {
                                    put("type", "string")
                                    put("description", "The author of the book")
                                }
                            }
                            putJsonArray("required") {
                                add("title")
                                add("author")
                            }
                        }
                    }
                }
                putJsonArray("required") {
                    add("books")
                }
            }
        }
        toolChoice = ToolChoice.function("foundBooks")
    }
    val completion: ChatCompletion = openAi.chatCompletion(chatCompletionRequest)
    val toolCall = completion.choices.first().message.toolCalls?.firstOrNull()
    require(toolCall is ToolCall.Function)
    val books = Json.decodeFromString<Books>(toolCall.function.arguments)
    return books.books
}

/**
 * Call the OpenAI API to describe an image.
 */
suspend fun describeImage(imageBytes: ByteArray) : String {
    val base64image = Base64.getEncoder().encodeToString(imageBytes)
    val openAi = OpenAI(BuildConfig.OPENAI_API_KEY)
    val chatMessages = listOf(
        ChatMessage(
            role = ChatRole.User,
            messageContent = ListContent(
                listOf(
                    ImagePart(
                        url = "data:image/jpeg;base64,$base64image",
                        detail = "high")
                )
            )
        )
    )
    val chatCompletionRequest = chatCompletionRequest {
        model = ModelId("gpt-4o-mini")
        messages = chatMessages
    }
    val completion: ChatCompletion = openAi.chatCompletion(chatCompletionRequest)
    return completion.choices.first().message.content?: "I'm not sure what is in the image."
}

fun main() {
    runBlocking {
        val books = findBooksInImage("https://prh.imgix.net/articles/top10-fiction-1600x800.jpg")
        for (book in books) {
            val info = getBookInfo(book.title, book.author)
            if (info != null)
                println("Title: ${info.title}, Author: ${info.author_name}, Rating: ${info.ratings_average}")
            else
                println("Lookup failed for detected book.  Title: ${book.title}, Author: ${book.author}")
        }
    }
}