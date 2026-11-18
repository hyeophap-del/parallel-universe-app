package app.paralleluniverse.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeCommandTest {

    @Test
    fun roundTripsEveryAction() {
        listOf(
            BridgeCommand.clone("com.discord"),
            BridgeCommand.uninstall("com.discord"),
            BridgeCommand.rename("부계정"),
            BridgeCommand.destroy(),
        ).forEach { assertEquals(it, BridgeCommand.decode(BridgeCommand.encode(it))) }
    }

    @Test
    fun separatorsInArgumentCannotInjectFields() {
        val decoded = BridgeCommand.decode(BridgeCommand.encode(BridgeCommand.rename("a\tDESTROY\nb")))
        assertEquals(BridgeCommand.rename("a DESTROY b"), decoded)
    }

    @Test
    fun rejectsForeignOrMalformedLines() {
        assertNull(BridgeCommand.decode("HTTP/1.1 200 OK"))
        assertNull(BridgeCommand.decode("PU1\tUNKNOWN\tx"))
        assertNull(BridgeCommand.decode("PU1\tCLONE\t"))
        assertNull(BridgeCommand.decode("PU1\tCLONE"))
    }
}
