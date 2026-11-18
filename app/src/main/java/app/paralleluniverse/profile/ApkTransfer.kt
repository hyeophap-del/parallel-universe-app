package app.paralleluniverse.profile

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 앱 복제: 개인 공간에 설치된 앱의 APK(본체 + 분할 APK)를 평행 공간으로 보내 설치한다.
 *
 * DevicePolicyManager.installExistingPackage는 기기 소유자나 "제휴된" 프로필 관리자만 쓸 수 있어,
 * 사용자가 직접 만든 업무 프로필에서는 SecurityException이 난다. 그래서 APK를 직접 설치한다.
 *
 * 형식: Int 파일 개수 → 파일마다 (UTF 이름, Long 크기, 바이트)
 */
object ApkTransfer {
    private const val TAG = "ApkTransfer"
    private const val MAX_FILES = 64
    private const val BUFFER = 64 * 1024

    /** 개인 공간 쪽: [packageName]의 APK 파일들을 [out]에 쓴다. */
    fun send(context: Context, packageName: String, out: DataOutputStream) {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val files = (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).map(::File)
        out.writeInt(files.size)
        files.forEachIndexed { index, file ->
            out.writeUTF("${index}_${file.name}")
            out.writeLong(file.length())
            file.inputStream().use { it.copyTo(out, BUFFER) }
        }
    }

    /**
     * 평행 공간 쪽: [input]에서 APK를 받아 설치한다. 시스템 설치 확인 창이 뜨면 사용자가 '설치'를
     * 눌러야 한다. 설치가 끝날 때까지 기다리는 블로킹 함수이므로 IO 스레드에서 호출한다.
     */
    fun receiveAndInstall(activity: Activity, packageName: String, input: DataInputStream): Boolean {
        // 먼저 캐시에 받아 둔다. 처음 설치할 때 사용자가 '이 출처 허용'을 켜러 설정에 다녀오면
        // 첫 세션이 중단(ABORTED)되므로, 받아 둔 파일로 한 번 더 시도한다.
        val dir = File(activity.cacheDir, "clone").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val count = input.readInt()
            require(count in 1..MAX_FILES) { "bad file count: $count" }
            val files = (0 until count).map {
                val name = File(input.readUTF()).name // 경로 구분자 제거
                val size = input.readLong()
                File(dir, name).also { file -> file.outputStream().use { copyExactly(input, it, size) } }
            }
            for (attempt in 1..2) {
                when (installOnce(activity, packageName, files)) {
                    PackageInstaller.STATUS_SUCCESS -> return true
                    PackageInstaller.STATUS_FAILURE_ABORTED ->
                        if (!activity.packageManager.canRequestPackageInstalls()) return false
                    else -> return false
                }
            }
            return false
        } finally {
            dir.deleteRecursively()
        }
    }

    /** @return PackageInstaller 최종 상태 */
    private fun installOnce(activity: Activity, packageName: String, files: List<File>): Int {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(packageName) }
        val sessionId = installer.createSession(params)

        val action = "${activity.packageName}.INSTALL_STATUS.$sessionId"
        val statuses = LinkedBlockingQueue<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                statuses.offer(intent)
            }
        }
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)

        try {
            installer.openSession(sessionId).use { session ->
                files.forEach { file ->
                    session.openWrite(file.name, 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out, BUFFER) }
                        session.fsync(out)
                    }
                }
                val callback = PendingIntent.getBroadcast(
                    activity,
                    sessionId,
                    Intent(action).setPackage(activity.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }

            while (true) {
                val result = statuses.poll(10, TimeUnit.MINUTES) ?: return PackageInstaller.STATUS_FAILURE
                when (val status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = IntentCompat.getParcelableExtra(result, Intent.EXTRA_INTENT, Intent::class.java)
                        if (confirm != null) {
                            activity.runOnUiThread { activity.startActivity(confirm) }
                        }
                    }
                    else -> {
                        if (status != PackageInstaller.STATUS_SUCCESS) {
                            Log.w(TAG, "install status=$status ${result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                        }
                        return status
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "install failed: $packageName", e)
            runCatching { installer.abandonSession(sessionId) }
            return PackageInstaller.STATUS_FAILURE
        } finally {
            activity.unregisterReceiver(receiver)
        }
    }

    private fun copyExactly(input: DataInputStream, out: OutputStream, size: Long) {
        val buffer = ByteArray(BUFFER)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("APK stream ended early")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }
}
