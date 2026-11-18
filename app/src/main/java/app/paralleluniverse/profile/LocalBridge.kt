package app.paralleluniverse.profile

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * 개인 공간 앱 ↔ 평행 공간 앱 사이의 기기 내부 통로(127.0.0.1 TCP).
 *
 * 일부 기기(예: Galaxy · Android 16)는 관리자 앱이 추가한 cross-profile intent filter를 오류 없이 받아 놓고도
 * 적용하지 않는다. 반면 두 사용자 공간은 같은 네트워크 스택을 쓰므로 localhost 연결은 동작한다.
 * (Unix 도메인 소켓은 사용자 공간이 다르면 SELinux가 막는다: Permission denied)
 *
 * 흐름: 개인 공간이 [serve]로 일회용 서버를 열고 → CrossProfileApps로 평행 공간 앱을 깨우면 →
 * 평행 공간 앱이 [receive]로 접속해 [BridgeAuth] 상호 인증을 거친 뒤 명령(+ 데이터)을 받고 결과를 돌려준다.
 * 같은 포트를 가로챈 다른 앱은 비밀 키가 없으므로 인증을 통과하지 못한다.
 */
object LocalBridge {
    private const val TAG = "LocalBridge"
    private const val HOST = "127.0.0.1"
    private const val MAGIC = "PU2"
    private val PORTS = 47621..47625
    private const val ACCEPT_TIMEOUT_MS = 20_000L
    private const val AUTH_TIMEOUT_MS = 3_000
    private const val PAYLOAD_TIMEOUT_MS = 60_000

    /** 사용자가 설치 확인 창에서 고민하는 시간까지 기다린다. */
    private const val REPLY_TIMEOUT_MS = 10 * 60_000

    enum class Outcome {
        OK,
        FAILED,
        NO_ANSWER,

        /** 접속은 왔지만 인증에 실패했다(키가 다르거나 없는 평행 공간). */
        UNPAIRED,
    }

    /**
     * 개인 공간 쪽. 서버를 열고 [wake]로 평행 공간 앱을 깨운 뒤, 인증을 통과한 쪽에 [command]와
     * [payload]가 쓰는 데이터를 보내고 결과를 기다린다. 블로킹 함수이므로 IO 스레드에서 호출한다.
     */
    fun serve(
        key: ByteArray,
        command: BridgeCommand,
        payload: (DataOutputStream) -> Unit = {},
        wake: () -> Boolean,
    ): Outcome {
        val server = openServer() ?: return Outcome.FAILED
        server.use {
            if (!wake()) return Outcome.FAILED
            val deadline = System.currentTimeMillis() + ACCEPT_TIMEOUT_MS
            var authFailed = false
            // 인증에 실패한 접속(다른 앱)은 끊고, 진짜 평행 공간 앱이 올 때까지 계속 기다린다.
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return if (authFailed) Outcome.UNPAIRED else Outcome.NO_ANSWER
                it.soTimeout = remaining.toInt()
                val socket = try {
                    it.accept()
                } catch (e: SocketTimeoutException) {
                    return if (authFailed) Outcome.UNPAIRED else Outcome.NO_ANSWER
                } catch (e: IOException) {
                    Log.w(TAG, "accept failed", e)
                    return Outcome.FAILED
                }
                val result = try {
                    serveAuthenticated(socket, key, command, payload)
                } finally {
                    socket.close()
                }
                if (result != null) return result
                authFailed = true
            }
        }
    }

    /** @return 결과. 상대가 인증에 실패하면(또는 인증 중 끊기면) null */
    private fun serveAuthenticated(
        socket: Socket,
        key: ByteArray,
        command: BridgeCommand,
        payload: (DataOutputStream) -> Unit,
    ): Outcome? {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val serverNonce = BridgeAuth.newNonce()
        val clientNonce = ByteArray(BridgeAuth.NONCE_BYTES)

        // 인증 단계: 짧은 타임아웃. 접속만 붙잡고 있는 다른 앱이 통로를 오래 막지 못하게 한다.
        try {
            socket.soTimeout = AUTH_TIMEOUT_MS
            out.writeUTF(MAGIC)
            out.write(serverNonce)
            out.flush()
            input.readFully(clientNonce)
            val clientProof = ByteArray(BridgeAuth.PROOF_BYTES).also(input::readFully)
            if (!BridgeAuth.matches(BridgeAuth.clientProof(key, serverNonce, clientNonce), clientProof)) {
                Log.w(TAG, "client failed authentication")
                return null
            }
        } catch (e: IOException) {
            Log.w(TAG, "authentication interrupted", e)
            return null
        }

        // 인증된 상대: 명령과 데이터를 보내고 결과를 기다린다.
        return try {
            socket.soTimeout = PAYLOAD_TIMEOUT_MS
            val line = BridgeCommand.encode(command)
            out.write(BridgeAuth.serverProof(key, serverNonce, clientNonce, line))
            out.writeUTF(line)
            payload(out)
            out.flush()
            socket.soTimeout = REPLY_TIMEOUT_MS
            if (input.readUTF() == BridgeCommand.REPLY_OK) Outcome.OK else Outcome.FAILED
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "no answer from parallel space", e)
            Outcome.NO_ANSWER
        } catch (e: IOException) {
            Log.w(TAG, "bridge failed", e)
            Outcome.FAILED
        }
    }

    /**
     * 평행 공간 쪽. 개인 공간의 서버에 접속해 상호 인증 후 명령을 받고, [handle] 결과를 돌려준다.
     * [handle]은 명령별 데이터를 같은 입력 스트림에서 읽는다. 블로킹 함수다.
     * @return 받은 명령. 인증된 서버가 없으면 null
     */
    fun receive(key: ByteArray, handle: (BridgeCommand, DataInputStream) -> Boolean): BridgeCommand? {
        for (port in PORTS) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(HOST, port), 1_000)
                socket.soTimeout = 3_000
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

                if (input.readUTF() != MAGIC) continue
                val serverNonce = ByteArray(BridgeAuth.NONCE_BYTES).also(input::readFully)
                val clientNonce = BridgeAuth.newNonce()
                out.write(clientNonce)
                out.write(BridgeAuth.clientProof(key, serverNonce, clientNonce))
                out.flush()

                val serverProof = ByteArray(BridgeAuth.PROOF_BYTES).also(input::readFully)
                val line = input.readUTF()
                if (!BridgeAuth.matches(BridgeAuth.serverProof(key, serverNonce, clientNonce, line), serverProof)) {
                    Log.w(TAG, "server failed authentication on port $port")
                    continue
                }
                val command = BridgeCommand.decode(line) ?: continue

                socket.soTimeout = PAYLOAD_TIMEOUT_MS
                val ok = try {
                    handle(command, input)
                } catch (e: Exception) {
                    Log.w(TAG, "command failed: ${command.action}", e)
                    false
                }
                out.writeUTF(if (ok) BridgeCommand.REPLY_OK else BridgeCommand.REPLY_FAILED)
                out.flush()
                return command
            } catch (e: IOException) {
                // 이 포트에는 우리 서버가 없거나 연결이 끊겼다.
            } finally {
                socket.close()
            }
        }
        return null
    }

    private fun openServer(): ServerSocket? {
        val loopback = InetAddress.getByName(HOST)
        for (port in PORTS) {
            try {
                return ServerSocket(port, 4, loopback)
            } catch (e: IOException) {
                Log.w(TAG, "port $port busy")
            }
        }
        return null
    }
}
