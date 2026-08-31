package com.filemanager.app.data

import com.filemanager.app.domain.FileOperationResult
import com.filemanager.app.util.isSameOrInside
import com.filemanager.app.util.isSymlink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveRepository {

    suspend fun createZip(sources: List<File>, destination: File): FileOperationResult =
        withContext(Dispatchers.IO) {
            // Writing the archive into a directory it is archiving would make it
            // grow forever as it swallows its own output.
            val selfContaining = sources.any { it.isDirectory && isSameOrInside(it, destination) }
            if (selfContaining) {
                return@withContext FileOperationResult.Error(
                    "Архив нельзя создать внутри архивируемой папки"
                )
            }
            try {
                ZipOutputStream(destination.outputStream().buffered()).use { zipOut ->
                    for (source in sources) {
                        addToZip(source, source.name, zipOut)
                    }
                }
                FileOperationResult.Success
            } catch (e: IOException) {
                destination.delete()
                FileOperationResult.Error("Не удалось создать архив: ${e.message}")
            }
        }

    private fun addToZip(file: File, entryName: String, zipOut: ZipOutputStream) {
        // Symlinks are not followed: a link pointing back up the tree would
        // otherwise be archived recursively.
        if (file.isDirectory && !file.isSymlink()) {
            val children = file.listFiles()
            if (children.isNullOrEmpty()) {
                zipOut.putNextEntry(ZipEntry("$entryName/"))
                zipOut.closeEntry()
                return
            }
            for (child in children) {
                addToZip(child, "$entryName/${child.name}", zipOut)
            }
        } else if (file.isFile) {
            zipOut.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    suspend fun extractZip(archive: File, destinationDir: File): FileOperationResult =
        withContext(Dispatchers.IO) {
            if (!hasZipSignature(archive)) {
                return@withContext FileOperationResult.Error("Это не ZIP-архив")
            }
            try {
                val canonicalDestination = destinationDir.canonicalPath
                ZipInputStream(archive.inputStream().buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val outFile = File(destinationDir, entry.name)
                        // Zip Slip: an entry name like ../../evil.sh must not be
                        // allowed to write outside the chosen folder.
                        if (!outFile.canonicalPath.startsWith(canonicalDestination + File.separator)) {
                            return@withContext FileOperationResult.Error(
                                "Архив содержит небезопасный путь: ${entry.name}"
                            )
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zipIn.copyTo(it) }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
                FileOperationResult.Success
            } catch (e: IOException) {
                FileOperationResult.Error("Не удалось распаковать архив: ${e.message}")
            }
        }

    /**
     * ZipInputStream silently yields no entries for a file that isn't an
     * archive, which would look like a successful extraction of nothing.
     */
    private fun hasZipSignature(file: File): Boolean = try {
        file.inputStream().use { input ->
            val header = ByteArray(2)
            input.read(header) == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        }
    } catch (e: IOException) {
        false
    }
}
