package com.filemanager.app.data

import com.filemanager.app.domain.FileOperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repository = ArchiveRepository()

    @Test
    fun `zips files and directories and extracts them back`() = runBlocking {
        val source = temp.newFolder("source")
        File(source, "note.txt").writeText("hello")
        File(source, "nested").mkdirs()
        File(source, "nested/deep.txt").writeText("deep")

        val archive = File(temp.root, "out.zip")
        assertEquals(FileOperationResult.Success, repository.createZip(listOf(source), archive))
        assertTrue(archive.length() > 0)

        val target = temp.newFolder("target")
        assertEquals(FileOperationResult.Success, repository.extractZip(archive, target))
        assertEquals("hello", File(target, "source/note.txt").readText())
        assertEquals("deep", File(target, "source/nested/deep.txt").readText())
    }

    @Test
    fun `empty directories survive the round trip`() = runBlocking {
        val source = temp.newFolder("source")
        File(source, "empty").mkdirs()

        val archive = File(temp.root, "empty.zip")
        repository.createZip(listOf(source), archive)
        val target = temp.newFolder("target")
        repository.extractZip(archive, target)

        assertTrue(File(target, "source/empty").isDirectory)
    }

    /**
     * Zip Slip: an archive whose entry name escapes the destination with ../
     * must be rejected rather than writing outside the chosen folder.
     */
    @Test
    fun `rejects entries that escape the destination directory`() = runBlocking {
        val archive = File(temp.root, "evil.zip")
        ZipOutputStream(archive.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../escaped.txt"))
            out.write("pwned".toByteArray())
            out.closeEntry()
        }

        val target = temp.newFolder("target")
        val result = repository.extractZip(archive, target)

        assertTrue(result is FileOperationResult.Error)
        assertFalse("entry escaped the destination", File(temp.root, "escaped.txt").exists())
    }

    @Test
    fun `reports an error for a file that is not a zip`() = runBlocking {
        val notAnArchive = temp.newFile("notes.txt").apply { writeText("just text") }
        val result = repository.extractZip(notAnArchive, temp.newFolder("target"))
        assertTrue(result is FileOperationResult.Error)
    }
}
