package app.paralleluniverse.profile

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * LocalBridge 상호 인증. 두 공간은 평행 공간을 만들 때 시스템 프로비저닝 경로로 나눠 가진 비밀 키를 쓴다.
 *
 * 1. 서버(개인) → 클라이언트(평행): 서버 nonce
 * 2. 클라이언트 → 서버: 클라이언트 nonce + clientProof = HMAC(K, "C" | nS | nC)
 * 3. 서버 → 클라이언트: serverProof = HMAC(K, "S" | nS | nC | 명령) + 명령
 *
 * 키가 없는 쪽은 상대를 속일 수 없다. 같은 포트를 가로챈 다른 앱은 증명을 만들지 못하고,
 * 서버 쪽 증명이 명령까지 덮으므로 명령을 바꿔치기할 수도 없다.
 */
object BridgeAuth {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 16
    const val PROOF_BYTES = 32

    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun newNonce(): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

    fun clientProof(key: ByteArray, serverNonce: ByteArray, clientNonce: ByteArray): ByteArray =
        hmac(key, byteArrayOf('C'.code.toByte()), serverNonce, clientNonce)

    fun serverProof(key: ByteArray, serverNonce: ByteArray, clientNonce: ByteArray, command: String): ByteArray =
        hmac(key, byteArrayOf('S'.code.toByte()), serverNonce, clientNonce, command.toByteArray(Charsets.UTF_8))

    /** 상수 시간 비교 */
    fun matches(expected: ByteArray, actual: ByteArray): Boolean = MessageDigest.isEqual(expected, actual)

    private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "bad key size" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach(mac::update)
        return mac.doFinal()
    }
}
