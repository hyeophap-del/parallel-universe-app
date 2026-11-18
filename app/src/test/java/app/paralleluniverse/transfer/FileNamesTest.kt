package app.paralleluniverse.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test
    fun keepsNormalNames() {
        assertEquals("보고서 최종.pdf", FileNames.sanitize("보고서 최종.pdf"))
        assertEquals("photo.JPG", FileNames.sanitize("photo.JPG"))
    }

    @Test
    fun stripsPathTraversal() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd"))
        assertEquals("evil.txt", FileNames.sanitize("..\\..\\Windows\\evil.txt"))
        assertEquals("a.txt", FileNames.sanitize("C:\\fakepath\\a.txt"))
        assertEquals(FileNames.FALLBACK, FileNames.sanitize("a/.."))
        assertEquals(FileNames.FALLBACK, FileNames.sanitize(".."))
        assertEquals(FileNames.FALLBACK, FileNames.sanitize("/"))
    }

    @Test
    fun removesControlAndBidiCharacters() {
        assertEquals("ab.txt", FileNames.sanitize("a\u0000b\n.txt"))
        assertEquals("invoicefdp.exe", FileNames.sanitize("invoice\u202Efdp.exe"))
    }

    @Test
    fun replacesReservedCharacters() {
        assertEquals("a_b_c_.txt", FileNames.sanitize("a:b*c?.txt"))
        assertEquals("_x_", FileNames.sanitize("<x>"))
    }

    @Test
    fun trimsDotsAndSpaces() {
        assertEquals("bashrc", FileNames.sanitize(".bashrc"))
        assertEquals("name", FileNames.sanitize("  name.  "))
    }

    @Test
    fun emptyOrNullBecomesFallback() {
        assertEquals(FileNames.FALLBACK, FileNames.sanitize(null))
        assertEquals(FileNames.FALLBACK, FileNames.sanitize(""))
        assertEquals(FileNames.FALLBACK, FileNames.sanitize("   "))
    }

    @Test
    fun normalizesToNfc() {
        // macOS가 보내는 NFD 한글(ㅎ+ㅏ+ㄴ)을 NFC(한)로 합친다.
        assertEquals("한.txt", FileNames.sanitize("\u1112\u1161\u11AB.txt"))
    }

    @Test
    fun truncatesLongNamesKeepingExtension() {
        val long = "가".repeat(300) + ".mp4"
        val result = FileNames.sanitize(long)
        assertTrue(result.endsWith(".mp4"))
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= FileNames.MAX_BYTES)
        // 멀티바이트 문자를 중간에서 자르지 않는다.
        assertTrue(result.removeSuffix(".mp4").all { it == '가' })
    }

    @Test
    fun deduplicateAddsCounterBeforeExtension() {
        val existing = setOf("a.txt", "a (1).txt")
        assertEquals("a (2).txt", FileNames.deduplicate("a.txt") { it in existing })
        assertEquals("b.txt", FileNames.deduplicate("b.txt") { it in existing })
        assertEquals("README (1)", FileNames.deduplicate("README") { it == "README" })
    }

    @Test
    fun splitHandlesEdgeCases() {
        assertEquals("archive.tar" to "gz", FileNames.split("archive.tar.gz"))
        assertEquals("noext" to "", FileNames.split("noext"))
        assertEquals(".hidden" to "", FileNames.split(".hidden"))
        assertEquals("trailing." to "", FileNames.split("trailing."))
    }

    @Test
    fun decodesHeaderValues() {
        assertEquals("보고서 1+1.pdf", FileNames.decodeHeader("%EB%B3%B4%EA%B3%A0%EC%84%9C%201%2B1.pdf"))
        assertNull(FileNames.decodeHeader("%E"))
        assertNull(FileNames.decodeHeader(null))
    }

    @Test
    fun contentDispositionHasAsciiFallbackAndUtf8Name() {
        assertEquals(
            "attachment; filename=\"___ 1.pdf\"; filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C%201.pdf",
            FileNames.contentDisposition("보고서 1.pdf"),
        )
    }

    @Test
    fun contentDispositionCannotInjectHeaders() {
        val value = FileNames.contentDisposition("a\r\nSet-Cookie: x=1\".txt")
        assertFalse(value.contains('\r'))
        assertFalse(value.contains('\n'))
        // 따옴표 안의 이름에 따옴표가 남지 않는다.
        val quoted = value.substringAfter("filename=\"").substringBefore("\";")
        assertFalse(quoted.contains('"'))
    }
}
