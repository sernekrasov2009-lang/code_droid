package ru.slicoo.code_droid.core.workspace

import java.io.File

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
}
