package app.paralleluniverse.transfer

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 휴대폰 Wi-Fi 주소에서만 열리는 작은 HTTP 서버. PC는 브라우저로 접속한다.
 *
 * 엔드포인트
 * - `GET /` : 전송 페이지(assets/transfer/index.html). 로그인 없이 열린다.
 * - `GET /api/session` : 로그인 여부.
 * - `POST /api/login` : 본문에 PIN. 맞으면 세션 쿠키를 준다.
 * - `GET /api/files` : 휴대폰에서 고른 파일 목록(세션 필요).
 * - `GET /api/files/{id}` : 파일 내려받기(세션 필요).
 * - `PUT /api/upload` : 본문 전체가 파일 내용, 이름은 `X-File-Name` 헤더(세션 필요).
 *
 * 업로드는 multipart를 쓰지 않는다. NanoHTTPD의 multipart 파서(parseBody)는 요청 전체를 임시 파일에 쓴 뒤
 * 다시 메모리 매핑하므로 2GB가 넘는 파일을 다룰 수 없고 디스크를 두 배로 쓴다. 대신 브라우저가 파일을 PUT 본문으로
 * 그대로 보내고, 서버는 소켓에서 읽는 대로 저장소에 쓴다. parseBody를 부르지 않으므로 임시 파일도 생기지 않는다.
 */
class TransferServer(
    private val host: String,
    private val port: Int,
    private val page: ByteArray,
    private val auth: PinAuthenticator,
    private val idle: IdleTracker,
    private val storage: TransferStorage,
    private val shared: SharedFiles,
    private val onReceived: (name: String, size: Long) -> Unit,
) : NanoHTTPD(host, port) {

    val url: String get() = "http://$host:$port"

    override fun serve(session: IHTTPSession): Response =
        try {
            route(session)
        } catch (e: Exception) {
            closing(json(HttpStatus.INTERNAL_ERROR, errorBody("server_error")))
        }

    private fun route(s: IHTTPSession): Response {
        val headers = s.headers
        if (!Http.isAllowedHost(headers["host"], host, port)) {
            return closing(json(HttpStatus.FORBIDDEN, errorBody("host")))
        }
        val path = s.uri ?: "/"
        val method = s.method

        when {
            method == Method.GET && (path == "/" || path == "/index.html") -> return pageResponse()
            method == Method.GET && path == "/api/session" ->
                return json(HttpStatus.OK, "{\"authenticated\":${isAuthed(s)}}")
            method == Method.POST && path == "/api/login" -> return login(s)
        }

        if (!isAuthed(s)) return closing(json(HttpStatus.UNAUTHORIZED, errorBody("auth")))

        return when {
            method == Method.GET && path == "/api/files" -> listShared()
            method == Method.GET && path.startsWith(FILES_PREFIX) -> download(path.removePrefix(FILES_PREFIX))
            method == Method.PUT && path == "/api/upload" -> upload(s)
            else -> closing(json(HttpStatus.NOT_FOUND, errorBody("not_found")))
        }
    }

    private fun isAuthed(s: IHTTPSession): Boolean =
        auth.isValid(Http.readCookie(s.headers["cookie"], Http.SESSION_COOKIE))

    private fun pageResponse(): Response =
        NanoHTTPD.newFixedLengthResponse(
            HttpStatus.OK,
            "text/html; charset=utf-8",
            ByteArrayInputStream(page),
            page.size.toLong(),
        ).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Frame-Options", "DENY")
            addHeader("Referrer-Policy", "no-referrer")
            addHeader(
                "Content-Security-Policy",
                "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
                    "connect-src 'self'; img-src 'self' data:; form-action 'self'; base-uri 'none'; " +
                    "frame-ancestors 'none'",
            )
        }

    private fun login(s: IHTTPSession): Response {
        val length = s.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length < 0 || length > MAX_LOGIN_BODY) {
            return closing(json(HttpStatus.BAD_REQUEST, errorBody("bad_request")))
        }
        val body = readSmall(s.inputStream, length.toInt())
        return when (val result = auth.login(body)) {
            is PinAuthenticator.Result.Success -> {
                idle.touch()
                json(HttpStatus.OK, "{\"ok\":true}").apply {
                    addHeader("Set-Cookie", Http.sessionCookieHeader(result.token))
                }
            }
            is PinAuthenticator.Result.Wrong ->
                json(HttpStatus.UNAUTHORIZED, "{\"ok\":false,\"error\":\"wrong_pin\",\"remaining\":${result.remaining}}")
            PinAuthenticator.Result.PinReset ->
                json(HttpStatus.UNAUTHORIZED, errorBody("pin_reset"))
            is PinAuthenticator.Result.Locked -> {
                val seconds = (result.retryAfterMs + 999) / 1000
                json(HttpStatus.TOO_MANY_REQUESTS, "{\"ok\":false,\"error\":\"locked\",\"retryAfterSec\":$seconds}")
            }
        }
    }

    private fun listShared(): Response {
        val items = shared.list().joinToString(",") {
            "{\"id\":${Http.quote(it.id)},\"name\":${Http.quote(it.name)},\"size\":${it.size}}"
        }
        return json(HttpStatus.OK, "{\"files\":[$items]}")
    }

    private fun download(id: String): Response {
        val info = shared.list().firstOrNull { it.id == id }
            ?: return json(HttpStatus.NOT_FOUND, errorBody("not_found"))
        val stream = shared.open(id) ?: return json(HttpStatus.NOT_FOUND, errorBody("unreadable"))
        idle.touch()
        val body = TouchingInputStream(stream, idle)
        val response = if (info.size >= 0) {
            NanoHTTPD.newFixedLengthResponse(HttpStatus.OK, OCTET_STREAM, body, info.size)
        } else {
            NanoHTTPD.newChunkedResponse(HttpStatus.OK, OCTET_STREAM, body)
        }
        return response.apply {
            addHeader("Content-Disposition", FileNames.contentDisposition(info.name))
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    }

    private fun upload(s: IHTTPSession): Response {
        val headers = s.headers
        // 다른 사이트의 폼이 보낼 수 없는 사용자 정의 헤더로 CSRF를 막는다(SameSite 쿠키와 함께).
        if (headers["x-pu"] != "1") return closing(json(HttpStatus.FORBIDDEN, errorBody("csrf")))
        val length = headers["content-length"]?.toLongOrNull()
            ?: return closing(json(HttpStatus.LENGTH_REQUIRED, errorBody("length_required")))
        if (length < 0) return closing(json(HttpStatus.BAD_REQUEST, errorBody("bad_request")))
        if (length > MAX_UPLOAD_BYTES) return closing(json(HttpStatus.PAYLOAD_TOO_LARGE, errorBody("too_large")))
        if (!storage.hasSpaceFor(length)) {
            return closing(json(HttpStatus.INSUFFICIENT_STORAGE, errorBody("storage_full")))
        }

        val name = FileNames.sanitize(FileNames.decodeHeader(headers["x-file-name"]))
        idle.touch()
        return try {
            val saved = storage.save(name, length, TouchingInputStream(s.inputStream, idle))
            onReceived(saved, length)
            json(HttpStatus.OK, "{\"ok\":true,\"name\":${Http.quote(saved)}}")
        } catch (e: IOException) {
            val status = if (StreamCopy.isNoSpace(e)) HttpStatus.INSUFFICIENT_STORAGE else HttpStatus.INTERNAL_ERROR
            val code = if (status == HttpStatus.INSUFFICIENT_STORAGE) "storage_full" else "upload_failed"
            closing(json(status, errorBody(code)))
        }
    }

    private fun readSmall(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var off = 0
        while (off < length) {
            val read = input.read(bytes, off, length - off)
            if (read < 0) break
            off += read
        }
        return String(bytes, 0, off, Charsets.UTF_8)
    }

    private fun json(status: HttpStatus, body: String): Response {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        ).apply { addHeader("Cache-Control", "no-store") }
    }

    /** 읽지 않은 요청 본문이 남아 있을 수 있으므로 연결을 재사용하지 않게 한다. */
    private fun closing(response: Response): Response = response.apply { addHeader("Connection", "close") }

    private fun errorBody(code: String) = "{\"ok\":false,\"error\":${Http.quote(code)}}"

    /** 바이트가 오갈 때마다 유휴 타이머를 늦춘다. */
    private class TouchingInputStream(input: InputStream, private val idle: IdleTracker) : FilterInputStream(input) {
        override fun read(): Int = super.read().also { idle.touch() }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { idle.touch() }
    }

    /** NanoHTTPD 버전에 상관없이 쓰려고 직접 정의한 상태 코드. */
    enum class HttpStatus(private val code: Int, private val reason: String) : Response.IStatus {
        OK(200, "OK"),
        BAD_REQUEST(400, "Bad Request"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        NOT_FOUND(404, "Not Found"),
        LENGTH_REQUIRED(411, "Length Required"),
        PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
        TOO_MANY_REQUESTS(429, "Too Many Requests"),
        INTERNAL_ERROR(500, "Internal Server Error"),
        INSUFFICIENT_STORAGE(507, "Insufficient Storage"),
        ;

        override fun getDescription(): String = "$code $reason"

        override fun getRequestStatus(): Int = code
    }

    companion object {
        /** 파일 하나의 최대 크기: 4GB. */
        const val MAX_UPLOAD_BYTES: Long = 4L * 1024 * 1024 * 1024

        /** 소켓에서 이 시간 동안 아무것도 오지 않으면 연결을 끊는다. */
        const val SOCKET_READ_TIMEOUT_MS = 30_000

        private const val MAX_LOGIN_BODY = 64L
        private const val FILES_PREFIX = "/api/files/"
        private const val OCTET_STREAM = "application/octet-stream"
    }
}
