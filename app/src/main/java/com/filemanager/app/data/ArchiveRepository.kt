package com.filemanager.app.data

import com.filemanager.app.domain.FileOperationResult
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
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zipOut.putNextEntry(ZipEntry("$entryName/"))
                zipOut.closeEntry()
            }
            for (child in children) {
                addToZip(child, "$entryName/${child.name}", zipOut)
            }
        } else {
            zipOut.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    suspend fun extractZip(archive: File, destinationDir: File): FileOperationResult =
        withContext(Dispatchers.IO) {
            try {
                val canonicalDestination = destinationDir.canonicalPath
                ZipInputStream(archive.inputStream().buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val outFile = File(destinationDir, entry.name)
                        // Zip Slip protection: reject entries that escape the destination dir
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
}
