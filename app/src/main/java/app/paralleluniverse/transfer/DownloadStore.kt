package app.paralleluniverse.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * PC에서 받은 파일을 휴대폰에 저장한다.
 *
 * - Android 10(API 29) 이상: MediaStore로 `Download/평행파일/`에 저장한다. 권한이 필요 없고,
 *   같은 이름이 있으면 MediaStore가 `이름 (1).확장자`처럼 바꿔 준다.
 * - Android 9(API 28): 공용 Download 폴더에 쓰려면 WRITE_EXTERNAL_STORAGE 런타임 권한이 필요하다.
 *   권한 요청을 피하려고 앱 전용 외부 폴더(`Android/data/app.paralleluniverse/files/Download/평행파일`)에 저장한다.
 *   이 폴더는 앱을 삭제하면 함께 지워진다. 같은 이름은 [FileNames.deduplicate]로 피한다.
 *
 * 업로드는 버퍼 하나로 흘려 쓰며 파일 전체를 메모리에 올리지 않는다.
 * 평행 공간(업무 프로필) 안에서 실행하면 그 공간의 저장소에 저장된다.
 */
class DownloadStore(context: Context) : TransferStorage {
    private val appContext = context.applicationContext
    private val lock = Any()

    /** 화면에 보여 줄 저장 위치. */
    val locationLabel: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
        } else {
            legacyDir()?.absolutePath ?: "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
        }

    override fun hasSpaceFor(length: Long): Boolean {
        // 앱 전용 외부 폴더는 공용 Download와 같은 기본 저장소에 있고, 권한 없이 남은 공간을 읽을 수 있다.
        val dir = appContext.getExternalFilesDir(null) ?: return true
        val free = dir.usableSpace
        if (free <= 0L) return true // 알 수 없으면 쓰기 오류로 판단한다.
        return free - length >= SAFETY_MARGIN_BYTES
    }

    override fun save(name: String, length: Long, input: InputStream): String =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(name, length, input)
            } else {
                saveToAppDir(name, length, input)
            }
        } catch (e: IOException) {
            if (StreamCopy.isNoSpace(e)) throw StorageFullException(e) else throw e
        } catch (e: RuntimeException) {
            // MediaStore는 IllegalStateException 등을 던질 수 있다.
            if (StreamCopy.isNoSpace(e)) throw StorageFullException(e) else throw IOException(e)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(name: String, length: Long, input: InputStream): String {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert failed")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("cannot open $uri")
            output.use { StreamCopy.copyExactly(input, it, length) }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return displayName(uri) ?: name
    }

    private fun saveToAppDir(name: String, length: Long, input: InputStream): String {
        val dir = legacyDir() ?: throw IOException("external storage unavailable")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val file = reserve(dir, name)
        try {
            FileOutputStream(file).use { StreamCopy.copyExactly(input, it, length) }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        return file.name
    }

    /** 동시에 같은 이름이 올라와도 겹치지 않도록 빈 파일을 먼저 만들어 이름을 잡아 둔다. */
    private fun reserve(dir: File, name: String): File = synchronized(lock) {
        val unique = FileNames.deduplicate(name) { File(dir, it).exists() }
        val file = File(dir, unique)
        if (file.parentFile?.canonicalPath != dir.canonicalPath) throw IOException("invalid name")
        if (!file.createNewFile()) throw IOException("cannot create $unique")
        file
    }

    private fun legacyDir(): File? =
        appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, FOLDER) }

    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /**
     * 확장자로 MIME 형식을 정한다. 브라우저가 보낸 형식을 쓰면 MediaStore가 확장자를 바꿔 버릴 수 있다.
     */
    private fun mimeTypeOf(name: String): String {
        val ext = FileNames.split(name).second.lowercase()
        if (ext.isEmpty()) return OCTET_STREAM
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: OCTET_STREAM
    }

    companion object {
        const val FOLDER = "평행파일"
        private const val OCTET_STREAM = "application/octet-stream"

        /** 휴대폰이 꽉 차지 않도록 남겨 둘 공간. */
        private const val SAFETY_MARGIN_BYTES = 200L * 1024 * 1024
    }
}
