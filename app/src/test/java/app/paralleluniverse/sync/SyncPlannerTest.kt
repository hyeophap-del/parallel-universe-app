package app.paralleluniverse.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlannerTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun item(key: String, ageDays: Long, size: Long = 10) =
        SyncItem(key, "$key.jpg", size, "image/jpeg", SyncItem.Kind.IMAGE, now - ageDays * day)

    private fun select(items: List<SyncItem>, cap: Long = SyncPlanner.CAP_BYTES) =
        SyncPlanner.select(items, { it.modifiedMs }, { it.size }, now, capBytes = cap).map { it.key }

    @Test
    fun keepsOnlyRecentItemsNewestFirst() {
        assertEquals(listOf("a", "b"), select(listOf(item("b", 3), item("old", 8), item("a", 1))))
    }

    @Test
    fun respectsSizeCapButKeepsSmallerItemsThatFit() {
        val items = listOf(item("a", 1, 60), item("big", 2, 50), item("c", 3, 30))
        assertEquals(listOf("a", "c"), select(items, cap = 100))
    }

    @Test
    fun diffAddsMissingAndExpiresStale() {
        val candidates = listOf(item("a", 1), item("b", 2))
        val diff = SyncPlanner.diff(setOf("b", "gone"), candidates)
        assertEquals(listOf(0), diff.wantedIndices)
        assertEquals(setOf("gone"), diff.expiredKeys)
    }

    @Test
    fun emptyCandidatesClearEverything() {
        val diff = SyncPlanner.diff(setOf("a", "b"), emptyList())
        assertEquals(emptyList<Int>(), diff.wantedIndices)
        assertEquals(setOf("a", "b"), diff.expiredKeys)
    }
}
