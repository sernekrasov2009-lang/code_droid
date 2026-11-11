package ru.slicoo.code_droid.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import ru.slicoo.code_droid.core.llm.LlmProvider
import ru.slicoo.code_droid.core.llm.OpenAiCompatibleProvider

/**
 * Single place that knows how to build the provider stack for Stage 1.
 * Default endpoint: OpenAI. Change [defaultBaseUrl] to target OpenRouter,
 * Groq or a local server.
 */
class ChatConfig(private val keyStore: ApiKeyStore) {

    val defaultBaseUrl: String = "https://api.openai.com/v1"
    val defaultModel: String = "gpt-4o-mini"

    private val client: HttpClient = HttpClient(OkHttp)

    fun currentProvider(): LlmProvider? {
        val key = keyStore.read() ?: return null
        return OpenAiCompatibleProvider(
            id = PROVIDER_ID,
            baseUrl = defaultBaseUrl,
            apiKey = key,
            client = client,
        )
    }

    /** Builds a throwaway provider to validate a key before saving it. */
    fun providerFor(key: String): LlmProvider = OpenAiCompatibleProvider(
        id = PROVIDER_ID,
        baseUrl = defaultBaseUrl,
        apiKey = key,
        client = client,
    )

    companion object {
        const val PROVIDER_ID = "openai_compatible"
    }
}
