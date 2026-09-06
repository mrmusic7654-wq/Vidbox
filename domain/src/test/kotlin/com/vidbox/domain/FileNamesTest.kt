package com.vidbox.domain

import com.vidbox.domain.util.FileNames
import org.junit.Assert.*
import org.junit.Test

class FileNamesTest {
    @Test fun traversalAndControlsCannotEscapeDestination() {
        listOf("../../etc/passwd", "..\\..\\system\\file", "/etc/", "a\u0000b\u202ec", "<>:*?\"|").forEach {
            val safe = FileNames.sanitize(it)
            assertFalse(safe.contains('/')); assertFalse(safe.contains('\\'))
            assertFalse(safe.contains("..")); assertTrue(safe.isNotBlank())
        }
    }
    @Test fun preservesUnicodeWithinUtf8ByteBudget() {
        val name = FileNames.sanitize("旅🎬".repeat(100))
        assertTrue(name.toByteArray().size <= 160)
        assertFalse(name.contains('\uFFFD'))
    }
    @Test fun avoidsReservedNamesAndEmptyFileNames() {
        assertEquals("Media", FileNames.sanitize("..."))
        assertEquals("Media_CON", FileNames.sanitize("CON"))
        assertTrue(FileNames.sanitize("nul.mp4").startsWith("Media_"))
    }
    @Test fun outputExtensionIsAllowlisted() {
        val id = "00000000-0000-0000-0000-000000000000"
        assertTrue(FileNames.output("movie", id, "mp4").endsWith("-00000000.mp4"))
        assertThrows(IllegalArgumentException::class.java) { FileNames.output("movie", id, "../../x") }
    }
}
