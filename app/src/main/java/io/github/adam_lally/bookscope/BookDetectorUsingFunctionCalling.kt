package io.github.adam_lally.bookscope

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.ToolBuilder
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.bookscope.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * BookDetector implementation that uses the LLM function calling feature.
 *
 * First we call the LLM and provide it the getBookInfo tool that it can use to look up
 * book information.
 *
 * When it comes back we execute those tool calls and then call the LLM again including
 * the results.
 */
class BookDetectorUsingFunctionCalling: BookDetector {
    override suspend fun detectBooksInImage(imageUrl: String): BookDetectorResult = coroutineScope {
        try {
            //Call the LLM to get its tool calls
            val openAi = OpenAI(BuildConfig.OPENAI_API_KEY)
            val modelId = ModelId("gpt-4o-mini")
            val chatMessages =  mutableListOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are a helpful assistant who identifies all of the books in an image. " +
                            "Use the provided tool to get information about a book given the title and author. " +
                            "Only return results where there is a good match between the book in the image and the book info from the tool."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    messageContent = ListContent(
                        listOf(
                            ImagePart(
                                url = imageUrl,
                                detail = "high")
                        )
                    )
                )
            )
            val chatCompletionRequest = chatCompletionRequest {
                model = modelId
                messages = chatMessages
                tools(getBookInfoToolBuilder())
            }
            val completion: ChatCompletion = openAi.chatCompletion(chatCompletionRequest)
            val message =  completion.choices.first().message
            if (message.toolCalls.isNullOrEmpty()) {
                BookDetectorResult(message = message.content ?: "No books detected")
            } else {
                // Execute the tool calls to look up book info, and call the LLM again
                chatMessages.add(message)
                message.toolCalls?.let { toolCalls ->
                    val bookInfosFutures = toolCalls.map { toolCall ->
                        require(toolCall is ToolCall.Function)
                        val functionCall = toolCall.function
                        require(functionCall.name == "getBookInfo")
                        val functionArgs = functionCall.argumentsAsJson()
                        val title = functionArgs["title"]?.jsonPrimitive?.content
                        val author = functionArgs["author"]?.jsonPrimitive?.content
                        async {
                            if (title != null && author != null) {
                                getBookInfo(title, author)
                            } else {
                                BookInfos(emptyList())
                            }
                        }
                    }
                    val bookInfos = bookInfosFutures.awaitAll()
                    for ((toolCall,bookInfo) in toolCalls zip bookInfos) {
                        require(toolCall is ToolCall.Function)
                        chatMessages.add(
                            ChatMessage(
                                role = ChatRole.Tool,
                                toolCallId = toolCall.id,
                                name = "getBookInfo",
                                content = Json.encodeToString(bookInfo)
                            )
                        )
                    }
                }
                val secondChatCompletionRequest = chatCompletionRequest {
                    model = modelId
                    messages = chatMessages
                    tools(foundBooksToolBuilder())
                    toolChoice = ToolChoice.function("foundBooks")
                }
                val secondResponse = openAi.chatCompletion(secondChatCompletionRequest)
                val toolCall = secondResponse.choices.first().message.toolCalls?.firstOrNull()
                require(toolCall is ToolCall.Function)
                val bookInfos = Json.decodeFromString<BookInfos>(toolCall.function.arguments)
                BookDetectorResult(bookInfo = bookInfos.books)
            }
        } catch (e: Exception) {
            BookDetectorResult(message = "Error: $e")
        }
    }

    private fun foundBooksToolBuilder(): ToolBuilder.() -> Unit = {
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
                            putJsonObject("author_name") {
                                put("type", "array")
                                put("description", "The authors of the book")
                                putJsonObject("items") {
                                    put("type", "string")
                                }
                            }
                            putJsonObject("ratings_average") {
                                put("type", "number")
                                put("description", "The average rating of the book")
                            }
                        }
                        putJsonArray("required") {
                            add("title")
                            add("author_name")
                        }
                    }
                }
            }
            putJsonArray("required") {
                add("books")
            }
        }
    }

    private fun getBookInfoToolBuilder(): ToolBuilder.() -> Unit = {
        function(
            name = "getBookInfo",
            description = "Get information about a book given the title and author.  Call this whenever you see a book in the image.",
        ) {
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

    private suspend fun getBookInfo(title: String, author: String): BookInfos {
        val searchResult = searchBooks(title, author)
        return BookInfos(searchResult.docs)
    }
}

fun main() {
    runBlocking {
        val detector = BookDetectorUsingFunctionCalling()
        //val url = "https://prh.imgix.net/articles/top10-fiction-1600x800.jpg"
        val image = File("myBooks.jpg").readBytes()
        val result = detector.detectBooksInImage(image)
        for (book in result.bookInfo) {
            println("Title: ${book.title}, Author: ${book.author_name}, Rating: ${book.ratings_average}")
        }
    }
}