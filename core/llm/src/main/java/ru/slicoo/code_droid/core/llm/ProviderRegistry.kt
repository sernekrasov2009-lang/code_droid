package ru.slicoo.code_droid.core.llm

/** Registry of available providers; adapters are added in Stage 1. */
class ProviderRegistry {
    private val providers = mutableMapOf<String, LlmProvider>()

    fun register(provider: LlmProvider) {
        providers[provider.id] = provider
    }

    fun byId(id: String): LlmProvider? = providers[id]

    fun all(): List<LlmProvider> = providers.values.toList()
}
