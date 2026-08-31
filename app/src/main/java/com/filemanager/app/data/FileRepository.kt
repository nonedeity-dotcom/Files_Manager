package com.filemanager.app.data

import com.filemanager.app.domain.FileItem
import com.filemanager.app.domain.FileOperationResult
import com.filemanager.app.domain.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileRepository {

    suspend fun listChildren(directory: File, sortOrder: SortOrder): List<FileItem> =
        withContext(Dispatchers.IO) {
            val children = directory.listFiles() ?: emptyArray()
            children.map { FileItem(it) }.sortedWith(comparator(sortOrder))
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

    suspend fun createFolder(parent: File, name: String): FileOperationResult =
        withContext(Dispatchers.IO) {
            val target = File(parent, name)
            if (target.exists()) return@withContext FileOperationResult.Error("Файл уже существует")
            if (target.mkdir()) FileOperationResult.Success
            else FileOperationResult.Error("Не удалось создать папку")
        }

    suspend fun rename(target: File, newName: String): FileOperationResult =
        withContext(Dispatchers.IO) {
            val destination = File(target.parentFile, newName)
            if (destination.exists()) return@withContext FileOperationResult.Error("Файл уже существует")
            if (target.renameTo(destination)) FileOperationResult.Success
            else FileOperationResult.Error("Не удалось переименовать")
        }

    suspend fun delete(targets: List<File>): FileOperationResult = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (target in targets) {
            if (!deleteRecursively(target)) failures += target.name
        }
        if (failures.isEmpty()) FileOperationResult.Success
        else FileOperationResult.Error("Не удалось удалить: ${failures.joinToString()}")
    }

    private fun deleteRecursively(target: File): Boolean {
        if (target.isDirectory) {
            target.listFiles()?.forEach { deleteRecursively(it) }
        }
        return target.delete()
    }

    suspend fun copy(sources: List<File>, destinationDir: File): FileOperationResult =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            for (source in sources) {
                val destination = File(destinationDir, source.name)
                try {
                    copyRecursively(source, destination)
                } catch (e: IOException) {
                    failures += source.name
                }
            }
            if (failures.isEmpty()) FileOperationResult.Success
            else FileOperationResult.Error("Не удалось скопировать: ${failures.joinToString()}")
        }

    private fun copyRecursively(source: File, destination: File) {
        if (source.isDirectory) {
            if (!destination.exists()) destination.mkdirs()
            source.listFiles()?.forEach { child ->
                copyRecursively(child, File(destination, child.name))
            }
        } else {
            source.copyTo(destination, overwrite = true)
        }
    }

    suspend fun move(sources: List<File>, destinationDir: File): FileOperationResult =
        withContext(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            for (source in sources) {
                val destination = File(destinationDir, source.name)
                val moved = source.renameTo(destination) || run {
                    try {
                        copyRecursively(source, destination)
                        deleteRecursively(source)
                    } catch (e: IOException) {
                        false
                    }
                }
                if (!moved) failures += source.name
            }
            if (failures.isEmpty()) FileOperationResult.Success
            else FileOperationResult.Error("Не удалось переместить: ${failures.joinToString()}")
        }

    fun search(root: File, query: String): Flow<FileItem> = flow {
        val lowerQuery = query.lowercase()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.name.lowercase().contains(lowerQuery)) {
                    emit(FileItem(child))
                }
                if (child.isDirectory && !child.isHidden) {
                    stack.addLast(child)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun readTextFile(file: File, maxBytes: Long = 2 * 1024 * 1024): String =
        withContext(Dispatchers.IO) {
            if (file.length() > maxBytes) {
                file.inputStream().bufferedReader().use { reader ->
                    val buffer = CharArray(maxBytes.toInt())
                    val read = reader.read(buffer)
                    String(buffer, 0, if (read > 0) read else 0)
                }
            } else {
                file.readText()
            }
        }
}
