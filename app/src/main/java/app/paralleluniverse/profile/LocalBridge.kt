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
 *
 * 흐름: 개인 공간이 [serve]로 일회용 서버를 열고 → CrossProfileApps로 평행 공간 앱을 깨우면 →
 * 평행 공간 앱이 [receive]로 접속해 명령(+ 필요한 데이터)을 받고 결과를 돌려준다.
 *
 * 형식(DataOutputStream): UTF 명령 줄 → 명령별 데이터 → (반대 방향) UTF 결과.
 *
 * 보안 메모: 다른 앱이 같은 포트를 먼저 차지해 가짜 명령을 보낼 수 있다. 그래서 프로필 쪽은
 * 피해가 없는 명령만 바로 실행한다. 앱 설치와 삭제는 시스템 확인 화면을 거치고,
 * 공간 삭제는 평행 공간 안에서 사용자가 다시 확인해야 한다.
 */
object LocalBridge {
    private const val TAG = "LocalBridge"
    private const val HOST = "127.0.0.1"
    private val PORTS = 47621..47625
    private const val ACCEPT_TIMEOUT_MS = 20_000
    private const val PAYLOAD_TIMEOUT_MS = 60_000

    /** 사용자가 설치 확인 창에서 고민하는 시간까지 기다린다. */
    private const val REPLY_TIMEOUT_MS = 10 * 60_000

    enum class Outcome { OK, FAILED, NO_ANSWER }

    /**
     * 개인 공간 쪽. 서버를 열고 [wake]로 평행 공간 앱을 깨운 뒤, 접속해 온 쪽에 [command]와
     * [payload]가 쓰는 데이터를 보내고 결과를 기다린다. 블로킹 함수이므로 IO 스레드에서 호출한다.
     */
    fun serve(
        command: BridgeCommand,
        payload: (DataOutputStream) -> Unit = {},
        wake: () -> Boolean,
    ): Outcome {
        val server = openServer() ?: return Outcome.FAILED
        server.use {
            it.soTimeout = ACCEPT_TIMEOUT_MS
            if (!wake()) return Outcome.FAILED
            return try {
                it.accept().use { socket ->
                    socket.soTimeout = REPLY_TIMEOUT_MS
                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    out.writeUTF(BridgeCommand.encode(command))
                    payload(out)
                    out.flush()
                    val reply = DataInputStream(BufferedInputStream(socket.getInputStream())).readUTF()
                    if (reply == BridgeCommand.REPLY_OK) Outcome.OK else Outcome.FAILED
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "no answer from parallel space", e)
                Outcome.NO_ANSWER
            } catch (e: IOException) {
                Log.w(TAG, "bridge failed", e)
                Outcome.FAILED
            }
        }
    }

    /**
     * 평행 공간 쪽. 개인 공간의 서버에 접속해 명령을 받고, [handle] 결과를 돌려준다.
     * [handle]은 명령별 데이터를 같은 입력 스트림에서 읽는다. 블로킹 함수다.
     * @return 받은 명령. 기다리는 서버가 없으면 null
     */
    fun receive(handle: (BridgeCommand, DataInputStream) -> Boolean): BridgeCommand? {
        for (port in PORTS) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(HOST, port), 1_000)
                socket.soTimeout = 3_000
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                // 우리 형식이 아니면(다른 프로그램의 포트) 다음 포트를 시도한다.
                val command = BridgeCommand.decode(input.readUTF()) ?: continue
                socket.soTimeout = PAYLOAD_TIMEOUT_MS
                val ok = try {
                    handle(command, input)
                } catch (e: Exception) {
                    Log.w(TAG, "command failed: ${command.action}", e)
                    false
                }
                DataOutputStream(socket.getOutputStream()).apply {
                    writeUTF(if (ok) BridgeCommand.REPLY_OK else BridgeCommand.REPLY_FAILED)
                    flush()
                }
                return command
            } catch (e: IOException) {
                // 이 포트에는 우리 서버가 없다.
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
                return ServerSocket(port, 1, loopback)
            } catch (e: IOException) {
                Log.w(TAG, "port $port busy")
            }
        }
        return null
    }
}
