package ru.slicoo.code_droid.core.model

import kotlinx.serialization.Serializable

/** Wire protocol an endpoint speaks. Determines which adapter to use. */
enum class ApiType { OPENAI, ANTHROPIC, GEMINI }

/**
 * A configured provider entry, either built-in (fixed id) or custom.
 * Serialized into the JSON config file (spec section 4).
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val apiType: ApiType = ApiType.OPENAI,
    val baseUrl: String,
    val models: List<ModelConfig> = emptyList(),
    val apiKeyEnv: String? = null,
    val notes: String? = null,
) {
    val isCustom: Boolean get() = id.startsWith("custom:")

    fun modelById(modelId: String): ModelConfig? = models.firstOrNull { it.id == modelId }
}

/** A model (or Tokify-style profile) offered by a provider. */
@Serializable
data class ModelConfig(
    val id: String,
    val name: String? = null,
    /** Free-form limits from the provider catalog, informational only. */
    val contextLimit: Long? = null,
)

/** User's per-provider credentials and the currently selected model. */
@Serializable
data class ProviderAccount(
    val providerId: String,
    val apiKey: String,
    val selectedModel: String,
)

/** Whole persisted app configuration, one JSON file. */
@Serializable
data class AppConfig(
    val version: Int = 1,
    val providers: List<ProviderConfig> = emptyList(),
    val accounts: List<ProviderAccount> = emptyList(),
    val activeProviderId: String? = null,
) {
    fun accountFor(providerId: String): ProviderAccount? =
        accounts.firstOrNull { it.providerId == providerId }

    fun activeAccount(): ProviderAccount? =
        activeProviderId?.let { accountFor(it) }

    fun providerById(id: String): ProviderConfig? = providers.firstOrNull { it.id == id }
}
