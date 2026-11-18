package app.paralleluniverse.transfer

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAuthenticatorTest {
    /** nextInt는 정해 둔 PIN을 차례로 돌려주고, nextBytes는 매번 다른 값을 채운다. */
    private class ScriptedRandom(vararg pins: Int) : Random(42) {
        private val queue = ArrayDeque(pins.toList())
        override fun nextInt(bound: Int): Int = queue.removeFirstOrNull() ?: 0
    }

    private var now = 1_000L

    private fun auth(vararg pins: Int) =
        PinAuthenticator(random = ScriptedRandom(*pins), clock = { now }, maxAttempts = 5, lockoutMs = 10_000)

    @Test
    fun pinIsSixDigitsWithLeadingZeros() {
        assertEquals("000042", auth(42).pin)
        assertEquals(6, PinAuthenticator().pin.length)
        assertTrue(PinAuthenticator().pin.all { it.isDigit() })
    }

    @Test
    fun correctPinIssuesValidToken() {
        val a = auth(123456)
        val result = a.login("123 456")
        assertTrue(result is PinAuthenticator.Result.Success)
        val token = (result as PinAuthenticator.Result.Success).token
        assertEquals(64, token.length)
        assertTrue(a.isValid(token))
        assertFalse(a.isValid("nope"))
        assertFalse(a.isValid(null))
    }

    @Test
    fun tokensAreUnique() {
        val a = auth(111111)
        val t1 = (a.login("111111") as PinAuthenticator.Result.Success).token
        val t2 = (a.login("111111") as PinAuthenticator.Result.Success).token
        assertNotEquals(t1, t2)
        assertTrue(a.isValid(t1) && a.isValid(t2))
    }

    @Test
    fun wrongPinCountsDownThenResets() {
        val changed = mutableListOf<String>()
        val a = auth(123456, 654321)
        a.onPinChanged = { changed += it }

        for (remaining in 4 downTo 1) {
            assertEquals(PinAuthenticator.Result.Wrong(remaining), a.login("000000"))
        }
        assertEquals(PinAuthenticator.Result.PinReset, a.login("000000"))
        assertEquals("654321", a.pin)
        assertEquals(listOf("654321"), changed)
    }

    @Test
    fun locksBrieflyAfterReset() {
        val a = auth(123456, 654321)
        repeat(5) { a.login("000000") }

        val locked = a.login("654321")
        assertTrue(locked is PinAuthenticator.Result.Locked)
        assertEquals(10_000L, (locked as PinAuthenticator.Result.Locked).retryAfterMs)

        now += 10_000
        assertTrue(a.login("654321") is PinAuthenticator.Result.Success)
    }

    @Test
    fun oldPinStopsWorkingAfterReset() {
        val a = auth(123456, 654321)
        repeat(5) { a.login("000000") }
        now += 10_000
        assertEquals(PinAuthenticator.Result.Wrong(4), a.login("123456"))
    }

    @Test
    fun successResetsFailureCount() {
        val a = auth(123456)
        repeat(4) { a.login("000000") }
        assertTrue(a.login("123456") is PinAuthenticator.Result.Success)
        assertEquals(PinAuthenticator.Result.Wrong(4), a.login("000000"))
    }
}
