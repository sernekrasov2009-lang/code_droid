package ru.slicoo.code_droid.core.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import ru.slicoo.code_droid.core.model.WorkspaceInfo
import ru.slicoo.code_droid.core.workspace.WorkspaceManager
import java.io.File

/**
 * Workspace management for Android. App-private storage is the only place
 * we can freely read and write without SAF round-trips, so the active
 * project lives in filesDir/projects/<name>. Import from and export to
 * arbitrary folders is done through SAF (documented in the UI).
 */
class WorkspaceRepository(private val context: Context, private val config: AppConfigRepository) {

    val projectsDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    fun activeWorkspaceInfo(): WorkspaceInfo? = config.load().workspace

    /** Creates a new empty workspace and makes it active. */
    fun createWorkspace(name: String): WorkspaceInfo {
        val safeName = name.trim()
            .replace(Regex("[^\\w\\-. ]+"), "-")
            .trim('-', ' ')
            .ifEmpty { "project" }
        val dir = uniqueDir(projectsDir, safeName)
        dir.mkdirs()
        val info = WorkspaceInfo(path = dir.absolutePath, displayName = safeName)
        config.save(config.load().copy(workspace = info))
        return info
    }

    fun setActiveWorkspace(info: WorkspaceInfo) {
        config.save(config.load().copy(workspace = info))
    }

    fun workspaceManager(info: WorkspaceInfo): WorkspaceManager =
        WorkspaceManager(File(info.path))

    fun workspaceManager(): WorkspaceManager? =
        activeWorkspaceInfo()?.let(::workspaceManager)

    fun listWorkspaces(): List<WorkspaceInfo> =
        projectsDir.listFiles { f -> f.isDirectory }
            ?.map { WorkspaceInfo(path = it.absolutePath, displayName = it.name) }
            ?.sortedBy { it.displayName.lowercase() }
            ?: emptyList()

    /** Copies a picked folder tree into a new workspace (SAF import). */
    fun importFromUri(uri: Uri, displayName: String): WorkspaceInfo {
        val info = createWorkspace(displayName)
        val target = File(info.path)
        copyTree(uri, DocumentsContract.getTreeDocumentId(uri), target)
        return info
    }

    private fun copyTree(treeUri: Uri, parentId: String, target: File) {
        val resolver = context.contentResolver
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        }.getOrNull() ?: return

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    val sub = File(target, name).apply { mkdirs() }
                    copyTree(treeUri, childDocId, sub)
                } else {
                    val out = File(target, name)
                    out.parentFile?.mkdirs()
                    runCatching {
                        resolver.openInputStream(childUri)?.use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            }
        }
    }

    private fun uniqueDir(parent: File, name: String): File {
        var candidate = File(parent, name)
        var i = 2
        while (candidate.exists()) {
            candidate = File(parent, "$name-$i")
            i++
        }
        return candidate
    }
}
