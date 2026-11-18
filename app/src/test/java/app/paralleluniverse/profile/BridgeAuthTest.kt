package app.paralleluniverse.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeAuthTest {
    private val key = BridgeAuth.newKey()
    private val nS = BridgeAuth.newNonce()
    private val nC = BridgeAuth.newNonce()

    @Test
    fun sameKeyProducesMatchingProofs() {
        assertTrue(BridgeAuth.matches(BridgeAuth.clientProof(key, nS, nC), BridgeAuth.clientProof(key.copyOf(), nS, nC)))
        assertEquals(BridgeAuth.PROOF_BYTES, BridgeAuth.clientProof(key, nS, nC).size)
    }

    @Test
    fun wrongKeyIsRejected() {
        assertFalse(BridgeAuth.matches(BridgeAuth.clientProof(key, nS, nC), BridgeAuth.clientProof(BridgeAuth.newKey(), nS, nC)))
    }

    @Test
    fun proofIsBoundToNoncesAndRole() {
        val proof = BridgeAuth.clientProof(key, nS, nC)
        assertFalse(BridgeAuth.matches(proof, BridgeAuth.clientProof(key, BridgeAuth.newNonce(), nC)))
        assertFalse(BridgeAuth.matches(proof, BridgeAuth.clientProof(key, nS, BridgeAuth.newNonce())))
        // 클라이언트 증명을 서버 증명으로 되돌려 쓸 수 없다.
        assertFalse(BridgeAuth.matches(proof, BridgeAuth.serverProof(key, nS, nC, "")))
    }

    @Test
    fun serverProofCoversTheCommand() {
        val ok = BridgeAuth.serverProof(key, nS, nC, "PU1\tCLONE\tcom.discord")
        assertFalse(BridgeAuth.matches(ok, BridgeAuth.serverProof(key, nS, nC, "PU1\tDESTROY\t")))
    }
}
