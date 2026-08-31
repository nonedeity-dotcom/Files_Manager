package com.filemanager.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuotingTest {

    @Test
    fun `wraps a plain path in single quotes`() {
        assertEquals("'/storage/emulated/0/Download'", shellQuote("/storage/emulated/0/Download"))
    }

    /**
     * Regression test: the original sanitizer stripped every non-ASCII
     * character, so this path collapsed to /storage/emulated/0/ and
     * `rm -rf` would have wiped the whole volume.
     */
    @Test
    fun `keeps non-ascii path segments intact`() {
        val path = "/storage/emulated/0/Документы/отчёт.pdf"
        assertEquals("'$path'", shellQuote(path))
    }

    @Test
    fun `keeps spaces and shell metacharacters literal`() {
        val path = "/sdcard/My Files/a&b;c\$d|e*f?g.txt"
        assertEquals("'$path'", shellQuote(path))
    }

    @Test
    fun `escapes embedded single quotes so the quoting cannot be broken out of`() {
        assertEquals("""'it'\''s.txt'""", shellQuote("it's.txt"))
    }

    @Test
    fun `escapes an injection attempt instead of executing it`() {
        val hostile = "/sdcard/x'; rm -rf /; echo '"
        val quoted = shellQuote(hostile)
        // Every quote in the payload is neutralised, so the shell sees one argument.
        assertEquals("""'/sdcard/x'\''; rm -rf /; echo '\'''""", quoted)
    }
}
