package ru.slicoo.code_droid.core.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.slicoo.code_droid.core.model.ModelConfig

/** Fetches the live model list from an OpenAI-compatible /v1/models endpoint. */
class ModelCatalog(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchOpenAiCompatible(baseUrl: String, apiKey: String): List<ModelConfig> {
        val response = client.get("$baseUrl/models") {
            header("Authorization", "Bearer $apiKey")
        }
        if (!response.status.isSuccess()) {
            throw LlmHttpException(response.status.value, response.bodyAsText())
        }
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data
            .mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                ModelConfig(id = id)
            }
            .sortedBy { it.id }
    }
}
