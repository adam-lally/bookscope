package io.github.adam_lally.bookscope

import com.aallam.openai.client.OpenAI
import com.example.bookscope.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Lazily instantiated network clients reused across the app to
 * reduce overhead and enable centralized configuration.
 */
object NetworkClients {
    /** OpenAI client shared by all calls. */
    val openAI: OpenAI by lazy {
        OpenAI(BuildConfig.OPENAI_API_KEY)
    }

    /** HttpClient for all OpenLibrary requests. */
    val openLibraryHttpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
            }
        }
    }
}
