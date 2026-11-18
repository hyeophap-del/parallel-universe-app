package app.paralleluniverse.shortcut

import org.junit.Assert.assertEquals
import org.junit.Test

class CloneShortcutsTest {
    @Test
    fun defaultLabelAppendsSpaceName() {
        assertEquals("Discord (부계정)", CloneShortcuts.defaultLabel("Discord", "부계정"))
    }
}
