package app.paralleluniverse.sync

/** 동기화 대상 한 개. [key]는 같은 파일이면 항상 같은 값이다(종류·경로·이름·크기·수정 시각). */
data class SyncItem(
    val key: String,
    val name: String,
    val size: Long,
    val mime: String,
    val kind: Kind,
    val modifiedMs: Long,
) {
    enum class Kind { IMAGE, VIDEO, DOWNLOAD }
}

/** 무엇을 복사하고 무엇을 지울지 정하는 순수 로직. */
object SyncPlanner {
    const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    const val CAP_BYTES = 1L * 1024 * 1024 * 1024

    /** 최근 [windowMs] 안의 항목을 최신순으로, 합계가 [capBytes]를 넘지 않을 때까지 고른다. */
    fun <T> select(
        items: List<T>,
        modifiedMs: (T) -> Long,
        size: (T) -> Long,
        nowMs: Long,
        windowMs: Long = WINDOW_MS,
        capBytes: Long = CAP_BYTES,
    ): List<T> {
        var total = 0L
        val chosen = ArrayList<T>()
        for (item in items.filter { modifiedMs(it) >= nowMs - windowMs }.sortedByDescending(modifiedMs)) {
            val s = size(item)
            if (s < 0 || total + s > capBytes) continue
            total += s
            chosen += item
        }
        return chosen
    }

    data class Diff(val wantedIndices: List<Int>, val expiredKeys: Set<String>)

    /** [synced]: 이미 복사해 둔 키. 후보에 없으면 지우고, 후보 중 없는 것만 받는다. */
    fun diff(synced: Set<String>, candidates: List<SyncItem>): Diff {
        val candidateKeys = candidates.mapTo(HashSet()) { it.key }
        return Diff(
            wantedIndices = candidates.indices.filter { candidates[it].key !in synced },
            expiredKeys = synced - candidateKeys,
        )
    }
}
