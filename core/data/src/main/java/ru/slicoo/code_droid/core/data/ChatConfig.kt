package ru.slicoo.code_droid.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import ru.slicoo.code_droid.core.llm.LlmProvider
import ru.slicoo.code_droid.core.llm.ModelCatalog
import ru.slicoo.code_droid.core.llm.OpenAiCompatibleProvider
import ru.slicoo.code_droid.core.model.ApiType
import ru.slicoo.code_droid.core.model.ProviderAccount
import ru.slicoo.code_droid.core.model.ProviderConfig

/**
 * Builds the runtime chat stack from the saved configuration. The
 * OpenAI-compatible adapter serves every OPENAI-type provider; Anthropic
 * and Gemini adapters arrive in Stage 2.
 */
class ChatConfig(
    private val repository: AppConfigRepository,
) {

    val client: HttpClient = HttpClient(OkHttp)

    val modelCatalog: ModelCatalog = ModelCatalog(client)

    val repositoryRef: AppConfigRepository get() = repository

    /** Provider + account ready to chat, or null when setup is incomplete. */
    fun activeSetup(): Pair<ProviderConfig, ProviderAccount>? = repository.activeSetup()

    fun currentProvider(): LlmProvider? {
        val (provider, account) = activeSetup() ?: return null
        return build(provider, account)
    }

    fun providerFor(provider: ProviderConfig, key: String): LlmProvider =
        build(provider, ProviderAccount(provider.id, key, selectedModelDummy()))

    private fun selectedModelDummy(): String = ""

    private fun build(provider: ProviderConfig, account: ProviderAccount): LlmProvider =
        when (provider.apiType) {
            ApiType.OPENAI -> OpenAiCompatibleProvider(
                id = provider.id,
                baseUrl = provider.baseUrl,
                apiKey = account.apiKey,
                client = client,
            )
            // Anthropic/Gemini adapters are Stage 2; fall back is a clear error.
            ApiType.ANTHROPIC, ApiType.GEMINI -> throw UnsupportedOperationException(
                "Адаптер для ${provider.name} появится в следующем обновлении. " +
                    "Пока используйте Tokify или OpenRouter для доступа к этим моделям.",
            )
        }
}
