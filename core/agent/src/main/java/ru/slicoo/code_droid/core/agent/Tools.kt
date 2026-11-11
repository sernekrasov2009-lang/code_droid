package ru.slicoo.code_droid.core.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.slicoo.code_droid.core.llm.ToolSpec
import ru.slicoo.code_droid.core.workspace.WorkspaceManager

/**
 * Workspace tools the agent can use, mirroring OpenCode's built-ins:
 * read, write, edit, list, glob, grep. Every operation goes through
 * WorkspaceManager; paths outside the root are rejected.
 */
class WorkspaceTools(private val workspace: WorkspaceManager) {

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = "read",
            description = "Read a file inside the workspace. Returns up to 200k characters.",
            parameters = schema(
                properties = { put("path", stringProp("File path relative to the workspace root")) },
                required = listOf("path"),
            ),
        ),
        ToolSpec(
            name = "write",
            description = "Create a file or completely replace its content.",
            parameters = schema(
                properties = {
                    put("path", stringProp("File path relative to the workspace root"))
                    put("content", stringProp("Full new content of the file"))
                },
                required = listOf("path", "content"),
            ),
        ),
        ToolSpec(
            name = "edit",
            description = "Replace exact text in an existing file. By default oldString must occur exactly once.",
            parameters = schema(
                required = listOf("path", "oldString", "newString"),
            ) {
                put("path", stringProp("File to edit"))
                put("oldString", stringProp("Exact text to find"))
                put("newString", stringProp("Replacement text"))
                putJsonObject("replaceAll") {
                    put("type", "boolean")
                    put("description", "Replace every occurrence; defaults to false")
                }
            },
        ),
        ToolSpec(
            name = "list",
            description = "List entries of a directory in the workspace.",
            parameters = schema(required = listOf("path")) {
                put("path", stringProp("Directory path, defaults to workspace root"))
            },
        ),
        ToolSpec(
            name = "glob",
            description = "Find files by glob pattern, e.g. **/*.kt. Returns matching paths.",
            parameters = schema(required = listOf("pattern")) {
                put("pattern", stringProp("Glob pattern with ** and * segments"))
            },
        ),
        ToolSpec(
            name = "grep",
            description = "Search file contents with a regular expression. Returns path, line number and text.",
            parameters = schema(required = listOf("pattern")) {
                put("pattern", stringProp("Regular expression to search for"))
                put("glob", stringProp("Optional glob pattern limiting the files searched"))
            },
        ),
    )

    /**
     * Executes one tool call. Returns the content to send back to the model.
     * Write and edit are subject to caller-side approval ([pendingApprovals]).
     */
    suspend fun execute(name: String, argumentsJson: String): String {
        val args: JsonObject = try {
            Json.parseToJsonElement(argumentsJson) as? JsonObject
                ?: return error("arguments must be a JSON object")
        } catch (e: Exception) {
            return error("invalid arguments JSON: ${e.message}")
        }
        return try {
            when (name) {
                "read" -> {
                    val path = args.str("path") ?: return error("missing path")
                    val text = workspace.read(path)
                    text.ifEmpty { "(empty file)" }
                }
                "write" -> {
                    val path = args.str("path") ?: return error("missing path")
                    val content = args.str("content") ?: return error("missing content")
                    workspace.write(path, content)
                    "wrote $path (${content.length} chars)"
                }
                "edit" -> {
                    val path = args.str("path") ?: return error("missing path")
                    val old = args.str("oldString") ?: return error("missing oldString")
                    val new = args.str("newString") ?: return error("missing newString")
                    val all = args.boolean("replaceAll") ?: false
                    val count = workspace.edit(path, old, new, all)
                    "edited $path ($count replacement(s))"
                }
                "list" -> {
                    val path = args.str("path") ?: "."
                    val entries = workspace.list(path)
                    if (entries.isEmpty()) "(empty)" else entries.joinToString("\n") {
                        (if (it.isDirectory) "[dir] " else "") + it.path
                    }
                }
                "glob" -> {
                    val pattern = args.str("pattern") ?: return error("missing pattern")
                    val matches = workspace.glob(pattern)
                    if (matches.isEmpty()) "(no matches)" else matches.joinToString("\n")
                }
                "grep" -> {
                    val pattern = args.str("pattern") ?: return error("missing pattern")
                    val glob = args.str("glob")
                    val matches = workspace.grep(pattern, glob)
                    if (matches.isEmpty()) "(no matches)" else matches.joinToString("\n") {
                        "${it.path}:${it.line}: ${it.text}"
                    }
                }
                else -> error("unknown tool: $name")
            }
        } catch (e: IllegalArgumentException) {
            error(e.message ?: "tool failed")
        }
    }

    private fun schema(
        required: List<String>,
        properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties() }
        if (required.isNotEmpty()) {
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun stringProp(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullSafe()

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let {
            if (it.content == "true") true else if (it.content == "false") false else null
        }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else null

    private fun error(message: String): String = "ERROR: $message"

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
