package app.paralleluniverse.transfer

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.Random

/**
 * 6자리 PIN 확인과 세션 토큰 발급.
 *
 * PIN을 [maxAttempts]번 틀리면 PIN을 새로 만들고 [lockoutMs] 동안 로그인을 막는다.
 * 그래서 무작위 대입은 PIN 하나당 [maxAttempts]번밖에 시도할 수 없고, 바뀐 PIN은 휴대폰 화면에만 보인다.
 * 토큰은 서버가 살아 있는 동안만 유효하다(메모리에만 보관).
 */
class PinAuthenticator(
    private val random: Random = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAttempts: Int = 5,
    private val lockoutMs: Long = 10_000,
) {
    sealed interface Result {
        data class Success(val token: String) : Result
        data class Wrong(val remaining: Int) : Result
        data object PinReset : Result
        data class Locked(val retryAfterMs: Long) : Result
    }

    /** PIN이 바뀌면(틀린 횟수 초과) 새 PIN으로 호출된다. 서버 스레드에서 호출될 수 있다. */
    @Volatile
    var onPinChanged: ((String) -> Unit)? = null

    @Volatile
    var pin: String = newPin()
        private set

    private var failures = 0
    private var lockedUntil = 0L
    private val sessions: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @Synchronized
    fun login(input: String): Result {
        val now = clock()
        if (now < lockedUntil) return Result.Locked(lockedUntil - now)

        val candidate = input.filter { it.isDigit() }
        if (MessageDigest.isEqual(candidate.toByteArray(), pin.toByteArray())) {
            failures = 0
            val token = newToken()
            sessions += token
            return Result.Success(token)
        }

        failures++
        if (failures >= maxAttempts) {
            failures = 0
            pin = newPin()
            lockedUntil = now + lockoutMs
            onPinChanged?.invoke(pin)
            return Result.PinReset
        }
        return Result.Wrong(maxAttempts - failures)
    }

    fun isValid(token: String?): Boolean = token != null && token in sessions

    private fun newPin(): String = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }
    }
}
