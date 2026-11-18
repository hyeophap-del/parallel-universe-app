package app.paralleluniverse.transfer

import java.net.URLDecoder
import java.text.Normalizer

/**
 * 파일 이름 정리 규칙. Android 타입을 쓰지 않으므로 JVM 단위 테스트로 검증한다.
 *
 * - 경로 부분(`../`, `C:\fakepath\`)을 버리고 마지막 이름만 남긴다.
 * - 제어 문자와 방향 전환 문자는 지우고, 파일 시스템에서 문제가 되는 문자는 `_`로 바꾼다.
 * - 앞뒤 공백과 점을 지워 숨김 파일이나 `..` 같은 이름이 생기지 않게 한다.
 * - UTF-8 기준 [MAX_BYTES]바이트를 넘으면 확장자를 살려서 자른다.
 */
object FileNames {
    const val FALLBACK = "파일"
    const val MAX_BYTES = 200
    private const val MAX_EXTENSION = 16

    private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /** 글자 방향을 바꿔 확장자를 속일 수 있는 문자들(예: U+202E). */
    private val BIDI_CONTROLS = setOf(
        '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
        '\u2066', '\u2067', '\u2068', '\u2069',
    )

    private const val HEX = "0123456789ABCDEF"

    /** 브라우저가 보낸 이름을 저장해도 안전한 이름으로 바꾼다. 결과는 비어 있지 않다. */
    fun sanitize(raw: String?): String {
        if (raw == null) return FALLBACK
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val base = normalized.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString(base.length) {
            for (c in base) {
                when {
                    c.isISOControl() || c in BIDI_CONTROLS -> Unit
                    c in FORBIDDEN -> append('_')
                    else -> append(c)
                }
            }
        }
        val trimmed = cleaned.trim { it.isWhitespace() || it == '.' }
        if (trimmed.isEmpty()) return FALLBACK
        return truncate(trimmed)
    }

    /**
     * [exists]가 참인 동안 `이름 (1).확장자`, `이름 (2).확장자` … 순서로 새 이름을 찾는다.
     */
    fun deduplicate(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val (stem, ext) = split(name)
        val dotExt = if (ext.isEmpty()) "" else ".$ext"
        for (n in 1..9_999) {
            val candidate = "$stem ($n)$dotExt"
            if (!exists(candidate)) return candidate
        }
        return "$stem (${System.nanoTime()})$dotExt"
    }

    /** 헤더로 받은 퍼센트 인코딩 이름(`encodeURIComponent`)을 푼다. 잘못된 값이면 null. */
    fun decodeHeader(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * 다운로드 응답의 Content-Disposition 값. 오래된 브라우저용 ASCII 이름과
     * RFC 6266/5987 형식의 UTF-8 이름(`filename*`)을 함께 넣는다.
     */
    fun contentDisposition(rawName: String): String {
        val name = sanitize(rawName)
        val ascii = buildString(name.length) {
            for (c in name) {
                append(if (c.code in 0x20..0x7E && c != '"' && c != '\\' && c != '%') c else '_')
            }
        }
        return "attachment; filename=\"$ascii\"; filename*=UTF-8''${percentEncode(name)}"
    }

    /** 이름을 (확장자를 뺀 부분, 확장자)로 나눈다. 확장자가 없으면 빈 문자열. */
    fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name to ""
        val ext = name.substring(dot + 1)
        if (ext.isEmpty() || ext.length > MAX_EXTENSION) return name to ""
        return name.substring(0, dot) to ext
    }

    private fun truncate(name: String): String {
        if (utf8Length(name) <= MAX_BYTES) return name
        val (stem, ext) = split(name)
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        val budget = MAX_BYTES - utf8Length(suffix)
        val cut = cutToBytes(if (suffix.isEmpty()) name else stem, budget).trimEnd()
        return if (cut.isEmpty()) FALLBACK + suffix else cut + suffix
    }

    private fun cutToBytes(s: String, budget: Int): String {
        val sb = StringBuilder()
        var used = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = utf8Length(cp)
            if (used + len > budget) break
            sb.appendCodePoint(cp)
            used += len
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun utf8Length(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    /** RFC 5987 attr-char만 그대로 두고 나머지 UTF-8 바이트는 %XX로 바꾼다. */
    private fun percentEncode(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (isAttrChar(c)) {
                append(c.toChar())
            } else {
                append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
    }

    private fun isAttrChar(c: Int): Boolean =
        c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c.toChar() in "!#$&+-.^_`|~"
}
