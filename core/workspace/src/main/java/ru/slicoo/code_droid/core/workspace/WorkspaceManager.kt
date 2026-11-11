package ru.slicoo.code_droid.core.workspace

import java.io.File
import java.nio.file.Files
import kotlin.io.path.relativeTo

/**
 * Single gate for all file operations inside a workspace root (spec section 8).
 * Agent tools never touch the filesystem directly; they go through this class.
 */
class WorkspaceManager(private val root: File) {

    init {
        require(root.isDirectory) { "Workspace root must be a directory: $root" }
    }

    /** Resolves [relativePath] and verifies it stays inside the workspace root. */
    fun resolve(relativePath: String): File {
        // Reject absolute inputs up front: java.io.File on Unix would otherwise
        // concatenate root + absolute child, and normalize() would land back
        // inside the root instead of flagging the escape.
        require(!File(relativePath).toPath().isAbsolute) {
            "Path must be relative to the workspace root: $relativePath"
        }
        val canonicalRoot = root.canonicalFile.toPath()
        val target = File(root, relativePath).absoluteFile.toPath().normalize()
        require(target == canonicalRoot || target.startsWith(canonicalRoot)) {
            "Path escapes workspace root: $relativePath"
        }
        // Canonicalize only after the path is proven to stay inside the root,
        // so symlinks leading outside are still caught by this check.
        val canonicalTarget = target.toFile().canonicalFile.toPath()
        require(canonicalTarget == canonicalRoot || canonicalTarget.startsWith(canonicalRoot)) {
            "Path escapes workspace root: $relativePath"
        }
        return canonicalTarget.toFile()
    }

    val rootPath: File get() = root

    fun read(relativePath: String, offset: Int = 0, limit: Int = MAX_READ_CHARS): String {
        val file = resolve(relativePath)
        require(file.isFile) { "Not a file: $relativePath" }
        val text = String(Files.readAllBytes(file.toPath()), Charsets.UTF_8)
        val from = offset.coerceAtLeast(0)
        if (from >= text.length) return ""
        return text.substring(from, minOf(from + limit, text.length))
    }

    fun write(relativePath: String, content: String) {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        Files.write(file.toPath(), content.toByteArray(Charsets.UTF_8))
    }

    /** OpenAI-style exact-match edit; returns the replacement count. */
    fun edit(relativePath: String, oldString: String, newString: String, replaceAll: Boolean = false): Int {
        val file = resolve(relativePath)
        require(file.isFile) { "Not a file: $relativePath" }
        val text = String(Files.readAllBytes(file.toPath()), Charsets.UTF_8)
        require(oldString != newString) { "oldString and newString must differ" }
        val count = countOccurrences(text, oldString)
        if (!replaceAll) {
            require(count == 1) {
                "oldString must occur exactly once (found $count occurrences in $relativePath)"
            }
        }
        require(count > 0) { "oldString not found in $relativePath" }
        val updated = if (replaceAll) text.replace(oldString, newString) else
            text.replaceFirst(oldString, newString)
        Files.write(file.toPath(), updated.toByteArray(Charsets.UTF_8))
        return if (replaceAll) count else 1
    }

    fun list(relativePath: String = "."): List<FileEntry> {
        val dir = resolve(relativePath)
        require(dir.isDirectory) { "Not a directory: $relativePath" }
        return dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { FileEntry(displayPath(it), it.isDirectory) }
            ?: emptyList()
    }

    /** Recursive glob, OpenCode-style: ** and * segments. */
    fun glob(pattern: String): List<String> {
        val regex = globToRegex(pattern)
        val rootPath = root.toPath()
        Files.walk(rootPath).use { stream ->
            return stream
                .filter { it != rootPath }
                .filter { !it.fileName.toString().startsWith(".") }
                .filter { !it.toString().contains(File.separatorChar + ".") }
                .map { it.relativeTo(rootPath).toString().replace(File.separatorChar, '/') }
                .filter { regex.matches(it) }
                .sorted()
                .toList()
                .take(MAX_GLOB_RESULTS)
        }
    }

    /** Simple line-based grep over text files in the workspace. */
    fun grep(pattern: String, glob: String? = null): List<GrepMatch> {
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            throw IllegalArgumentException("Некорректный regex: $pattern")
        }
        val files = glob?.let { glob(it) } ?: glob("**/*")
        val results = mutableListOf<GrepMatch>()
        for (path in files) {
            val file = resolve(path)
            if (!file.isFile) continue
            val text = try {
                String(Files.readAllBytes(file.toPath()), Charsets.UTF_8)
            } catch (e: Exception) {
                continue // binary or unreadable
            }
            text.lineSequence().forEachIndexed { index, line ->
                if (regex.containsMatchIn(line)) {
                    results += GrepMatch(path, index + 1, line.trim().take(300))
                    if (results.size >= MAX_GREP_RESULTS) return results
                }
            }
        }
        return results
    }

    private fun displayPath(file: File): String =
        file.toPath().relativeTo(root.toPath()).toString().replace(File.separatorChar, '/')

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun globToRegex(pattern: String): Regex {
        // `**` matches across separators, `*`/`?` within one segment.
        // A trailing `**/` also matches zero directories, so `**/*.kt`
        // finds files in the root as well as in subdirectories.
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            when (val ch = pattern[i]) {
                '*' -> {
                    if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                        if (i + 2 < pattern.length && pattern[i + 2] == '/') {
                            sb.append("(?:.*/)?")
                            i += 2
                        } else {
                            sb.append(".*")
                            i++
                        }
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                else -> sb.append(Regex.escape(ch.toString()))
            }
            i++
        }
        return Regex("^${sb}$")
    }

    data class FileEntry(val path: String, val isDirectory: Boolean)
    data class GrepMatch(val path: String, val line: Int, val text: String)

    companion object {
        const val MAX_READ_CHARS = 200_000
        const val MAX_GLOB_RESULTS = 200
        const val MAX_GREP_RESULTS = 50
    }
}
