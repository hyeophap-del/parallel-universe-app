package app.paralleluniverse.transfer

import java.util.Locale

/** HTTP 처리에 쓰는 작은 순수 함수 모음(쿠키, Host 확인, JSON 문자열). */
object Http {
    const val SESSION_COOKIE = "pu_session"

    /** `Cookie` 헤더에서 [name] 값을 꺼낸다. */
    fun readCookie(header: String?, name: String): String? =
        header?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotEmpty() }

    /** 같은 사이트 요청에만 붙고 스크립트에서 읽을 수 없는 세션 쿠키. */
    fun sessionCookieHeader(token: String): String =
        "$SESSION_COOKIE=$token; Path=/; HttpOnly; SameSite=Strict"

    /**
     * DNS 리바인딩을 막기 위해 Host 헤더가 휴대폰 주소와 정확히 같은지 확인한다.
     */
    fun isAllowedHost(header: String?, host: String, port: Int): Boolean =
        header != null && header.trim().equals("$host:$port", ignoreCase = true)

    /** JSON 문자열 리터럴(따옴표 포함). */
    fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ' || c == ' ' || c == ' ') {
                    append(String.format(Locale.ROOT, "\\u%04x", c.code))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }
}
