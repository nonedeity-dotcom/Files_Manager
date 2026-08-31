package com.filemanager.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileUtilsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `free name is returned unchanged`() {
        val destination = uniqueDestination(temp.root, "report.pdf")
        assertEquals(File(temp.root, "report.pdf"), destination)
    }

    /**
     * Regression test: pasting into the source folder used to resolve to the
     * file itself, and an overwriting copy deletes the target before reading
     * the source — destroying the file it was asked to duplicate.
     */
    @Test
    fun `taken name gets a numbered suffix instead of colliding`() {
        temp.newFile("report.pdf")
        assertEquals(File(temp.root, "report (1).pdf"), uniqueDestination(temp.root, "report.pdf"))
    }

    @Test
    fun `suffix increments past every existing copy`() {
        temp.newFile("report.pdf")
        temp.newFile("report (1).pdf")
        temp.newFile("report (2).pdf")
        assertEquals(File(temp.root, "report (3).pdf"), uniqueDestination(temp.root, "report.pdf"))
    }

    @Test
    fun `extensionless name keeps its whole name`() {
        temp.newFile("README")
        assertEquals(File(temp.root, "README (1)"), uniqueDestination(temp.root, "README"))
    }

    @Test
    fun `dotfile is not split into an empty base and an extension`() {
        temp.newFile(".bashrc")
        assertEquals(File(temp.root, ".bashrc (1)"), uniqueDestination(temp.root, ".bashrc"))
    }

    @Test
    fun `directory contains itself`() {
        assertTrue(isSameOrInside(temp.root, temp.root))
    }

    @Test
    fun `nested directory is reported as inside`() {
        val nested = temp.newFolder("outer", "inner")
        assertTrue(isSameOrInside(File(temp.root, "outer"), nested))
    }

    @Test
    fun `sibling directory is not reported as inside`() {
        val first = temp.newFolder("first")
        val second = temp.newFolder("second")
        assertFalse(isSameOrInside(first, second))
    }

    /** "/a/bc" must not count as living inside "/a/b". */
    @Test
    fun `name prefix alone does not count as containment`() {
        val shorter = temp.newFolder("data")
        temp.newFolder("data-backup")
        assertFalse(isSameOrInside(shorter, File(temp.root, "data-backup")))
    }

    @Test
    fun `regular file is not a symlink`() {
        assertFalse(temp.newFile("plain.txt").isSymlink())
    }
}
