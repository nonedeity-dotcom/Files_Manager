package com.filemanager.app.data

import com.filemanager.app.domain.FileItem
import com.filemanager.app.domain.FileOperationResult
import com.filemanager.app.domain.SortOrder
import com.filemanager.app.util.isSameOrInside
import com.filemanager.app.util.isSymlink
import com.filemanager.app.util.safeCanonicalPath
import com.filemanager.app.util.uniqueDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileRepository {

    suspend fun listChildren(
        directory: File,
        sortOrder: SortOrder,
        showHidden: Boolean = false,
        allowRoot: Boolean = false
    ): List<FileItem> =
        withContext(Dispatchers.IO) {
            val children = directory.listFiles()
            val items = when {
                children != null -> children.map { FileItem(it) }
                // Unreadable by the app (a system directory, say) — retry via root.
                allowRoot -> RootFileOperations.listDirectory(directory)
                    ?.map { entry ->
                        val file = File(directory, entry.name)
                        FileItem(file = file, isDirectory = entry.isDirectory)
                    }
                    ?: emptyList()

                else -> emptyList()
            }
            items
                .filter { showHidden || !it.name.startsWith(".") }
                .sortedWith(comparator(sortOrder))
        }

    private fun comparator(sortOrder: SortOrder): Comparator<FileItem> {
        val byFolderFirst = compareByDescending<FileItem> { it.isDirectory }
        val secondary = when (sortOrder) {
            SortOrder.NAME_ASC -> compareBy<FileItem> { it.name.lowercase() }
            SortOrder.NAME_DESC -> compareByDescending<FileItem> { it.name.lowercase() }
            SortOrder.DATE_DESC -> compareByDescending<FileItem> { it.lastModified }
            SortOrder.DATE_ASC -> compareBy<FileItem> { it.lastModified }
            SortOrder.SIZE_DESC -> compareByDescending<FileItem> { it.size }
            SortOrder.SIZE_ASC -> compareBy<FileItem> { it.size }
        }
        return byFolderFirst.then(secondary)
    }

    suspend fun createFolder(
        parent: File,
        name: String,
        allowRoot: Boolean = false
    ): FileOperationResult =
        withContext(Dispatchers.IO) {
            val target = File(parent, name)
            if (target.exists()) return@withContext FileOperationResult.Error("Файл уже существует")
            if (target.mkdir() || (allowRoot && RootFileOperations.mkdir(target))) {
                FileOperationResult.Success
            } else {
                FileOperationResult.Error("Не удалось создать папку")
            }
        }

    suspend fun rename(
        target: File,
        newName: String,
        allowRoot: Boolean = false
    ): FileOperationResult =
        withContext(Dispatchers.IO) {
            if (newName.isBlank() || newName.contains('/')) {
                return@withContext FileOperationResult.Error("Недопустимое имя")
            }
            val destination = File(target.parentFile, newName)
            if (destination.exists()) return@withContext FileOperationResult.Error("Файл уже существует")
            if (target.renameTo(destination) ||
                (allowRoot && RootFileOperations.rename(target, destination))
            ) {
                FileOperationResult.Success
            } else {
                FileOperationResult.Error("Не удалось переименовать")
            }
        }

    suspend fun delete(targets: List<File>, allowRoot: Boolean = false): FileOperationResult =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            for (target in targets) {
                val deleted = deleteRecursively(target) ||
                    (allowRoot && RootFileOperations.deleteRecursively(target))
                if (!deleted) failures += target.name
            }
            if (failures.isEmpty()) FileOperationResult.Success
            else FileOperationResult.Error("Не удалось удалить: ${failures.joinToString()}")
        }

    /**
     * Deletes [target], descending into real directories only. A symlink is
     * unlinked as-is — following one would delete the contents it points at.
     */
    private fun deleteRecursively(target: File): Boolean {
        if (target.isDirectory && !target.isSymlink()) {
            target.listFiles()?.forEach { deleteRecursively(it) }
        }
        return target.delete()
    }

    suspend fun copy(
        sources: List<File>,
        destinationDir: File,
        allowRoot: Boolean = false
    ): FileOperationResult =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            for (source in sources) {
                // Copying a directory into its own subtree would recurse until
                // the disk fills up.
                if (source.isDirectory && isSameOrInside(source, destinationDir)) {
                    failures += source.name
                    continue
                }
                // Never resolve to the source itself: an overwriting copy deletes
                // the target first, which would destroy the original.
                val destination = uniqueDestination(destinationDir, source.name)
                val copied = try {
                    copyRecursively(source, destination)
                    true
                } catch (e: IOException) {
                    allowRoot && RootFileOperations.copy(source, destination)
                }
                if (!copied) failures += source.name
            }
            if (failures.isEmpty()) FileOperationResult.Success
            else FileOperationResult.Error("Не удалось скопировать: ${failures.joinToString()}")
        }

    private fun copyRecursively(source: File, destination: File) {
        if (source.isDirectory && !source.isSymlink()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw IOException("Не удалось создать ${destination.absolutePath}")
            }
            source.listFiles()?.forEach { child ->
                copyRecursively(child, File(destination, child.name))
            }
        } else {
            source.copyTo(destination, overwrite = true)
        }
    }

    suspend fun move(
        sources: List<File>,
        destinationDir: File,
        allowRoot: Boolean = false
    ): FileOperationResult =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            for (source in sources) {
                if (source.isDirectory && isSameOrInside(source, destinationDir)) {
                    failures += source.name
                    continue
                }
                // Already in place: nothing to do, and copying would duplicate it.
                if (source.parentFile?.safeCanonicalPath() == destinationDir.safeCanonicalPath()) {
                    continue
                }
                val destination = uniqueDestination(destinationDir, source.name)
                val moved = source.renameTo(destination) || run {
                    try {
                        copyRecursively(source, destination)
                        deleteRecursively(source)
                    } catch (e: IOException) {
                        false
                    }
                } || (allowRoot && RootFileOperations.move(source, destination))
                if (!moved) failures += source.name
            }
            if (failures.isEmpty()) FileOperationResult.Success
            else FileOperationResult.Error("Не удалось переместить: ${failures.joinToString()}")
        }

    /**
     * Walks [root] breadth-first emitting name matches as they are found.
     *
     * Symlinked directories are not descended into: /storage/emulated/0 and
     * friends contain links back to their own parents, which would loop forever.
     */
    fun search(
        root: File,
        query: String,
        showHidden: Boolean = false,
        maxResults: Int = MAX_SEARCH_RESULTS
    ): Flow<FileItem> = flow {
        val lowerQuery = query.lowercase()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var emitted = 0
        while (stack.isNotEmpty() && emitted < maxResults) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (child.name.lowercase().contains(lowerQuery)) {
                    emit(FileItem(child))
                    if (++emitted >= maxResults) return@flow
                }
                if (child.isDirectory && !child.isSymlink()) {
                    stack.addLast(child)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun readTextFile(file: File, maxBytes: Long = MAX_TEXT_PREVIEW_BYTES): String =
        withContext(Dispatchers.IO) {
            file.inputStream().bufferedReader().use { reader ->
                val buffer = CharArray(maxBytes.toInt())
                // read() can return short of the buffer while more data remains,
                // so keep filling until the preview is full or the file ends.
                var filled = 0
                while (filled < buffer.size) {
                    val read = reader.read(buffer, filled, buffer.size - filled)
                    if (read < 0) break
                    filled += read
                }
                String(buffer, 0, filled)
            }
        }

    companion object {
        private const val MAX_SEARCH_RESULTS = 500
        private const val MAX_TEXT_PREVIEW_BYTES = 1L * 1024 * 1024
    }
}
