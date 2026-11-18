package app.paralleluniverse.transfer

import app.paralleluniverse.transfer.AddressCandidate.Source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 포트 선택, 유휴 타이머, 주소 선택, 스트림 복사, HTTP 보조 함수. */
class TransferLogicTest {
    // ---- PortPicker ----

    @Test
    fun picksFirstPortThatBinds() {
        val tried = mutableListOf<Int>()
        val result = PortPicker.firstBound { port ->
            tried += port
            if (port >= 8082) "server@$port" else null
        }
        assertEquals("server@8082", result)
        assertEquals(listOf(8080, 8081, 8082), tried)
    }

    @Test
    fun returnsNullWhenNoPortBinds() {
        val tried = mutableListOf<Int>()
        assertNull(PortPicker.firstBound { port -> tried += port; null as String? })
        assertEquals((8080..8090).toList(), tried)
    }

    // ---- IdleTracker ----

    @Test
    fun idleAfterTimeoutAndTouchResets() {
        var now = 0L
        val idle = IdleTracker(timeoutMs = 600_000, clock = { now })
        now = 599_999
        assertFalse(idle.isIdle())
        idle.touch()
        now += 599_999
        assertFalse(idle.isIdle())
        now += 1
        assertTrue(idle.isIdle())
    }

    // ---- AddressPicker ----

    @Test
    fun prefersWifiOverInterfaceScan() {
        val picked = AddressPicker.pick(
            listOf(
                AddressCandidate("ap0", "192.168.43.1", Source.INTERFACE),
                AddressCandidate("wlan0", "192.168.0.12", Source.WIFI),
            ),
        )
        assertEquals("192.168.0.12", picked)
    }

    @Test
    fun fallsBackToHotspotInterface() {
        val picked = AddressPicker.pick(
            listOf(
                AddressCandidate("rmnet_data0", "10.20.30.40", Source.INTERFACE),
                AddressCandidate("tun0", "10.8.0.2", Source.INTERFACE),
                AddressCandidate("swlan0", "192.168.43.1", Source.INTERFACE),
            ),
        )
        assertEquals("192.168.43.1", picked)
    }

    @Test
    fun ignoresUnusableAddresses() {
        assertNull(
            AddressPicker.pick(
                listOf(
                    AddressCandidate("wlan0", "169.254.1.2", Source.WIFI),
                    AddressCandidate("lo", "127.0.0.1", Source.INTERFACE),
                    AddressCandidate("rmnet0", "100.64.1.1", Source.INTERFACE),
                ),
            ),
        )
        assertFalse(AddressPicker.isUsableIpv4("256.1.1.1"))
        assertFalse(AddressPicker.isUsableIpv4("1.2.3"))
        assertFalse(AddressPicker.isUsableIpv4("fe80::1"))
        assertTrue(AddressPicker.isPrivate("172.20.0.5"))
        assertFalse(AddressPicker.isPrivate("172.32.0.5"))
    }

    @Test
    fun prefersPrivateAddressWithinSameSource() {
        val picked = AddressPicker.pick(
            listOf(
                AddressCandidate("wlan0", "100.70.1.2", Source.WIFI),
                AddressCandidate("wlan0", "10.0.0.7", Source.WIFI),
            ),
        )
        assertEquals("10.0.0.7", picked)
    }

    // ---- StreamCopy ----

    @Test
    fun copiesExactlyLengthBytes() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        val input = ByteArrayInputStream(data + byteArrayOf(1, 2, 3)) // 뒤에 남는 바이트는 읽지 않는다
        assertEquals(200_000L, StreamCopy.copyExactly(input, out, 200_000))
        assertTrue(data.contentEquals(out.toByteArray()))
        assertEquals(3, input.available())
    }

    @Test
    fun shortStreamThrowsIncomplete() {
        try {
            StreamCopy.copyExactly(ByteArrayInputStream(ByteArray(10)), ByteArrayOutputStream(), 11)
            fail("expected IncompleteUploadException")
        } catch (e: IncompleteUploadException) {
            // ok
        }
    }

    @Test
    fun zeroLengthCopiesNothing() {
        assertEquals(0L, StreamCopy.copyExactly(ByteArrayInputStream(ByteArray(5)), ByteArrayOutputStream(), 0))
    }

    @Test
    fun detectsNoSpaceErrors() {
        assertTrue(StreamCopy.isNoSpace(IOException("write failed: ENOSPC (No space left on device)")))
        assertTrue(StreamCopy.isNoSpace(IOException("wrapped", IOException("No space left on device"))))
        assertTrue(StreamCopy.isNoSpace(StorageFullException()))
        assertFalse(StreamCopy.isNoSpace(IOException("Connection reset")))
    }

    // ---- Http ----

    @Test
    fun readsSessionCookie() {
        assertEquals("abc", Http.readCookie("theme=dark; pu_session=abc; x=1", Http.SESSION_COOKIE))
        assertNull(Http.readCookie("pu_session_other=abc", Http.SESSION_COOKIE))
        assertNull(Http.readCookie("pu_session=", Http.SESSION_COOKIE))
        assertNull(Http.readCookie(null, Http.SESSION_COOKIE))
    }

    @Test
    fun sessionCookieIsHttpOnlyAndStrict() {
        val header = Http.sessionCookieHeader("t0k")
        assertTrue(header.startsWith("pu_session=t0k;"))
        assertTrue(header.contains("HttpOnly"))
        assertTrue(header.contains("SameSite=Strict"))
    }

    @Test
    fun hostMustMatchExactly() {
        assertTrue(Http.isAllowedHost("192.168.0.12:8080", "192.168.0.12", 8080))
        assertFalse(Http.isAllowedHost("evil.example:8080", "192.168.0.12", 8080))
        assertFalse(Http.isAllowedHost("192.168.0.12:8081", "192.168.0.12", 8080))
        assertFalse(Http.isAllowedHost(null, "192.168.0.12", 8080))
    }

    @Test
    fun quotesJsonStrings() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\"", Http.quote("a\"b\\c\n\u0001"))
        assertEquals("\"한글\"", Http.quote("한글"))
    }
}
