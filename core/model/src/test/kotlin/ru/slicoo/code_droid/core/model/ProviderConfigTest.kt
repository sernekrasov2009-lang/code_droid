package ru.slicoo.code_droid.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesRoundTrip() {
        val config = AppConfig(
            providers = listOf(
                ProviderConfig(
                    id = "custom:my-gateway",
                    name = "My Gateway",
                    baseUrl = "https://example.com/v1",
                    models = listOf(ModelConfig("gpt-x", "GPT X", 128_000)),
                ),
            ),
            accounts = listOf(ProviderAccount("custom:my-gateway", "", "gpt-x")),
            activeProviderId = "custom:my-gateway",
        )
        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString(AppConfig.serializer(), encoded)
        assertEquals(config, decoded)
    }

    @Test
    fun customFlagDetectsCustomProviders() {
        assertTrue(
            ProviderConfig("custom:x", "X", baseUrl = "https://x.example/v1").isCustom,
        )
        assertFalse(ProviderConfig("openai", "OpenAI", baseUrl = "u").isCustom)
    }

    @Test
    fun activeAccountResolves() {
        val config = AppConfig(
            providers = listOf(ProviderConfig("p", "P", baseUrl = "u")),
            accounts = listOf(ProviderAccount("p", "k", "m")),
            activeProviderId = "p",
        )
        assertEquals("m", config.activeAccount()?.selectedModel)
    }

    @Test
    fun nullWhenProviderMissing() {
        val config = AppConfig(activeProviderId = "nope")
        assertNull(config.providerById("nope"))
        assertNull(config.activeAccount())
    }
}
