package ru.slicoo.code_droid.core.llm

import ru.slicoo.code_droid.core.model.ApiType
import ru.slicoo.code_droid.core.model.ModelConfig
import ru.slicoo.code_droid.core.model.ProviderConfig

/**
 * Built-in provider catalog. Every entry speaks a protocol covered by an
 * adapter in core:llm; users can add more via the JSON config or the
 * settings screen (custom providers).
 */
object BuiltinProviders {

    val all: List<ProviderConfig> = listOf(
        ProviderConfig(
            id = "openai",
            name = "OpenAI",
            apiType = ApiType.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            models = listOf(
                ModelConfig("gpt-4o-mini"),
                ModelConfig("gpt-4o"),
                ModelConfig("gpt-4.1-mini"),
                ModelConfig("gpt-4.1"),
                ModelConfig("o3-mini"),
            ),
            apiKeyEnv = "OPENAI_API_KEY",
        ),
        ProviderConfig(
            id = "tokify",
            name = "Tokify",
            apiType = ApiType.OPENAI,
            baseUrl = "https://tokify.sale/v1",
            models = listOf(
                ModelConfig("gpt-6-astra"),
            ),
            notes = "Профили Tokify с каталога; модели подтягиваются по кнопке «Обновить».",
        ),
        ProviderConfig(
            id = "openrouter",
            name = "OpenRouter",
            apiType = ApiType.OPENAI,
            baseUrl = "https://openrouter.ai/api/v1",
            models = listOf(
                ModelConfig("openai/gpt-4o-mini"),
                ModelConfig("anthropic/claude-sonnet-4.5"),
                ModelConfig("google/gemini-2.5-flash"),
                ModelConfig("deepseek/deepseek-chat"),
            ),
            apiKeyEnv = "OPENROUTER_API_KEY",
        ),
        ProviderConfig(
            id = "groq",
            name = "Groq",
            apiType = ApiType.OPENAI,
            baseUrl = "https://api.groq.com/openai/v1",
            models = listOf(
                ModelConfig("llama-3.3-70b-versatile"),
            ),
            apiKeyEnv = "GROQ_API_KEY",
        ),
        ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            apiType = ApiType.OPENAI,
            baseUrl = "https://api.deepseek.com",
            models = listOf(
                ModelConfig("deepseek-chat"),
                ModelConfig("deepseek-reasoner"),
            ),
            apiKeyEnv = "DEEPSEEK_API_KEY",
        ),
        ProviderConfig(
            id = "mistral",
            name = "Mistral",
            apiType = ApiType.OPENAI,
            baseUrl = "https://api.mistral.ai/v1",
            models = listOf(
                ModelConfig("mistral-large-latest"),
                ModelConfig("mistral-small-latest"),
            ),
            apiKeyEnv = "MISTRAL_API_KEY",
        ),
        ProviderConfig(
            id = "xai",
            name = "xAI (Grok)",
            apiType = ApiType.OPENAI,
            baseUrl = "https://api.x.ai/v1",
            models = listOf(
                ModelConfig("grok-3"),
                ModelConfig("grok-3-mini"),
            ),
            apiKeyEnv = "XAI_API_KEY",
        ),
        ProviderConfig(
            id = "ollama",
            name = "Ollama (локальный)",
            apiType = ApiType.OPENAI,
            baseUrl = "http://127.0.0.1:11434/v1",
            models = emptyList(),
            notes = "Локальный сервер Ollama. Ключ не нужен; запустите ollama serve.",
        ),
        ProviderConfig(
            id = "lmstudio",
            name = "LM Studio (локальный)",
            apiType = ApiType.OPENAI,
            baseUrl = "http://127.0.0.1:1234/v1",
            models = emptyList(),
            notes = "Локальный LM Studio. Ключ не нужен; включите Local Server.",
        ),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic",
            apiType = ApiType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            models = listOf(
                ModelConfig("claude-sonnet-4-5"),
                ModelConfig("claude-haiku-4-5"),
            ),
            apiKeyEnv = "ANTHROPIC_API_KEY",
            notes = "Адаптер Messages API — Этап 2. Пока используйте Tokify/OpenRouter.",
        ),
        ProviderConfig(
            id = "gemini",
            name = "Google Gemini",
            apiType = ApiType.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com",
            models = listOf(
                ModelConfig("gemini-2.5-flash"),
                ModelConfig("gemini-2.5-pro"),
            ),
            apiKeyEnv = "GEMINI_API_KEY",
            notes = "Адаптер Gemini — Этап 2. Пока используйте Tokify/OpenRouter.",
        ),
    )

    fun byId(id: String): ProviderConfig? = all.firstOrNull { it.id == id }
}
