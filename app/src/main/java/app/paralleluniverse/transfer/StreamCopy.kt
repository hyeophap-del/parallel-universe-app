package app.paralleluniverse.transfer

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 받은 데이터가 Content-Length보다 짧게 끊겼을 때. */
class IncompleteUploadException(expected: Long, actual: Long) :
    IOException("expected $expected bytes but got $actual")

/** 저장 공간이 부족할 때. */
class StorageFullException(cause: Throwable? = null) : IOException("no space left", cause)

object StreamCopy {
    private const val BUFFER_SIZE = 64 * 1024

    /**
     * [input]에서 정확히 [length]바이트를 [output]으로 복사한다. 메모리에는 버퍼 하나만 쓴다.
     * [input]은 닫지 않는다(업로드에서는 소켓 스트림이므로).
     */
    fun copyExactly(input: InputStream, output: OutputStream, length: Long): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (copied < length) {
            val want = minOf(buffer.size.toLong(), length - copied).toInt()
            val read = input.read(buffer, 0, want)
            if (read < 0) throw IncompleteUploadException(length, copied)
            output.write(buffer, 0, read)
            copied += read
        }
        output.flush()
        return copied
    }

    /** 디스크가 가득 찬 오류(ENOSPC)인지. Android에서는 ErrnoException이 원인으로 붙는다. */
    fun isNoSpace(error: Throwable): Boolean {
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth < 8) {
            if (e is StorageFullException) return true
            val msg = e.message.orEmpty()
            if ("ENOSPC" in msg || "No space left" in msg) return true
            e = e.cause
            depth++
        }
        return false
    }
}
