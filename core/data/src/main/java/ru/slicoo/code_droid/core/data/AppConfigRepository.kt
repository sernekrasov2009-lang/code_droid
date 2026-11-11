package ru.slicoo.code_droid.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.slicoo.code_droid.core.model.AppConfig
import ru.slicoo.code_droid.core.model.ModelConfig
import ru.slicoo.code_droid.core.model.ProviderAccount
import ru.slicoo.code_droid.core.model.ProviderConfig
import ru.slicoo.code_droid.core.llm.BuiltinProviders
import java.io.File

/**
 * Owns the app configuration: a JSON file (config.json in filesDir) for
 * providers/models, and SharedPreferences for secrets. API keys are
 * deliberately kept OUT of the JSON so the file can be exported safely.
 */
class AppConfigRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val file: File
        get() = File(context.filesDir, CONFIG_FILE)

    private val secrets: SharedPreferences =
        context.getSharedPreferences(SECRETS_PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var cache: AppConfig? = null

    fun load(): AppConfig {
        cache?.let { return it }
        val config = readFromDisk() ?: defaults()
        cache = config
        return config
    }

    fun save(config: AppConfig) {
        cache = config
        persist(config)
    }

    private fun readFromDisk(): AppConfig? = try {
        if (file.exists()) {
            json.decodeFromString(AppConfig.serializer(), file.readText())
        } else {
            null
        }
    } catch (e: Exception) {
        null // Corrupt config: fall back to defaults rather than crash.
    }

    private fun defaults(): AppConfig = AppConfig(
        version = 1,
        providers = BuiltinProviders.all,
    )

    private fun persist(config: AppConfig) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(config))
        } catch (e: Exception) {
            // Disk failures must not crash the app; next save retries.
        }
    }

    // --- Secrets (kept out of the JSON) ---

    fun apiKey(providerId: String): String? =
        secrets.getString("key_$providerId", null)?.takeIf { it.isNotBlank() }

    fun writeApiKey(providerId: String, key: String) {
        secrets.edit().putString("key_$providerId", key).apply()
    }

    fun removeApiKey(providerId: String) {
        secrets.edit().remove("key_$providerId").apply()
    }

    // --- Mutations ---

    fun setActiveProvider(providerId: String) {
        save(load().copy(activeProviderId = providerId))
    }

    fun setSelectedModel(providerId: String, modelId: String) {
        val config = load()
        val account = config.accountFor(providerId)
        val newAccount = (account ?: ProviderAccount(
            providerId = providerId,
            apiKey = "",
            selectedModel = modelId,
        )).copy(selectedModel = modelId)
        save(
            config.copy(
                accounts = config.accounts.filterNot { it.providerId == providerId } + newAccount,
            ),
        )
    }

    fun upsertCustomProvider(provider: ProviderConfig) {
        require(provider.isCustom) { "Only custom: providers can be added here" }
        val config = load()
        save(
            config.copy(
                providers = config.providers.filterNot { it.id == provider.id } + provider,
            ),
        )
    }

    fun removeProvider(providerId: String) {
        val config = load()
        save(
            config.copy(
                providers = config.providers.filterNot { it.id == providerId },
                accounts = config.accounts.filterNot { it.providerId == providerId },
                activeProviderId = if (config.activeProviderId == providerId) null
                else config.activeProviderId,
            ),
        )
        removeApiKey(providerId)
    }

    fun replaceModels(providerId: String, models: List<ModelConfig>) {
        val config = load()
        val provider = config.providerById(providerId) ?: return
        save(
            config.copy(
                providers = config.providers.map {
                    if (it.id == providerId) it.copy(models = models) else it
                },
            ),
        )
    }

    /** True when the app is fully ready to chat. */
    fun activeSetup(): Pair<ProviderConfig, ProviderAccount>? {
        val config = load()
        val providerId = config.activeProviderId ?: return null
        val provider = config.providerById(providerId) ?: return null
        val key = apiKey(providerId) ?: return null
        val model = config.accountFor(providerId)?.selectedModel
            ?: provider.models.firstOrNull()?.id
            ?: return null
        return provider to ProviderAccount(providerId, key, model)
    }

    private companion object {
        const val CONFIG_FILE = "config.json"
        const val SECRETS_PREFS = "code_droid_secrets"
    }
}
