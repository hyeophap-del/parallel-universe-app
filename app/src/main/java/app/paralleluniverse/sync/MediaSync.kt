package app.paralleluniverse.sync

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * "앱을 열 때 개인 사진·파일 복사": 개인 공간의 최근 사진·동영상(+ 허용 시 다운로드 폴더)을
 * 평행 공간의 MediaStore로 복사해, 복제한 앱의 자체 갤러리·파일 목록에서도 보이게 한다.
 * 개인 → 평행 한 방향이다. 평행 공간 쪽 복사본은 기간·용량 상한을 벗어나면 자동으로 지운다.
 *
 * 형식(LocalBridge SYNC 명령의 데이터):
 *  개인 → 평행: Int 후보 수, 후보마다 (UTF key, UTF 이름, Long 크기, UTF MIME, UTF 종류)
 *  평행 → 개인: Int 받을 개수, Int 후보 번호들
 *  개인 → 평행: 받을 파일마다 Boolean 열림 여부, (Int 길이, 바이트)… 0으로 끝
 *  평행 → 개인: Int 저장한 수, Int 지운 수
 */
object MediaSync {
    private const val TAG = "MediaSync"
    private const val CHUNK = 64 * 1024
    private const val MAX_ITEMS = 10_000
    const val MEDIA_DIR = "DCIM/평행 공간 동기화"
    const val DOWNLOAD_DIR = "Download/평행 공간 동기화"

    /** 개인 공간에서 보낼 원본. */
    class Source(val item: SyncItem, val open: () -> InputStream)

    data class Result(val saved: Int, val deleted: Int)

    // ---------------------------------------------------------------- 개인 공간

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 사진·동영상 읽기 권한(일부 사진만 허용한 경우 포함) */
    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasMediaPermission(context: Context): Boolean {
        val granted = { p: String -> ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }
        return mediaPermissions().any(granted) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    /** 다운로드 폴더 전체를 읽으려면 '모든 파일 접근'이 필요하다(Android 11+). */
    fun canReadDownloads(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** 개인 공간의 최근 항목을 기간·용량 상한에 맞춰 고른다. */
    fun scan(context: Context, nowMs: Long = System.currentTimeMillis()): List<Source> {
        val all = buildList {
            if (hasMediaPermission(context)) {
                addAll(queryMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, SyncItem.Kind.IMAGE, nowMs))
                addAll(queryMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, SyncItem.Kind.VIDEO, nowMs))
            }
            if (canReadDownloads()) addAll(scanDownloads(nowMs))
        }
        return SyncPlanner.select(all, { it.item.modifiedMs }, { it.item.size }, nowMs)
    }

    /** 개인 공간 쪽: 후보 목록을 보내고, 평행 공간이 원하는 파일만 보낸다. */
    fun send(sources: List<Source>, out: DataOutputStream, input: DataInputStream): Result {
        out.writeInt(sources.size)
        sources.forEach { s ->
            out.writeUTF(s.item.key)
            out.writeUTF(s.item.name)
            out.writeLong(s.item.size)
            out.writeUTF(s.item.mime)
            out.writeUTF(s.item.kind.name)
        }
        out.flush()

        val wantedCount = input.readInt()
        require(wantedCount in 0..sources.size) { "bad wanted count" }
        val wanted = List(wantedCount) { input.readInt() }
        val buffer = ByteArray(CHUNK)
        for (index in wanted) {
            val stream = try {
                sources[index].open()
            } catch (e: Exception) {
                Log.w(TAG, "cannot open ${sources[index].item.name}", e)
                null
            }
            out.writeBoolean(stream != null)
            stream?.use {
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    out.writeInt(read)
                    out.write(buffer, 0, read)
                }
                out.writeInt(0)
            }
        }
        out.flush()
        return Result(saved = input.readInt(), deleted = input.readInt())
    }

    private fun queryMedia(context: Context, base: Uri, kind: SyncItem.Kind, nowMs: Long): List<Source> {
        val sinceSec = (nowMs - SyncPlanner.WINDOW_MS) / 1000
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val result = ArrayList<Source>()
        try {
            context.contentResolver.query(
                base, projection, "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?", arrayOf(sinceSec.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val size = c.getLong(2)
                    val modifiedMs = c.getLong(3) * 1000
                    val mime = c.getString(4) ?: continue
                    val uri = ContentUris.withAppendedId(base, id)
                    val item = SyncItem("${kind.name}|$id|$name|$size|$modifiedMs", name, size, mime, kind, modifiedMs)
                    result += Source(item) { context.contentResolver.openInputStream(uri) ?: throw IOException("null stream") }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission for $base", e)
        }
        return result
    }

    private fun scanDownloads(nowMs: Long): List<Source> {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return root.walkTopDown().maxDepth(3)
            .filter { it.isFile && !it.name.startsWith(".") && it.lastModified() >= nowMs - SyncPlanner.WINDOW_MS }
            .map { file ->
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "application/octet-stream"
                val item = SyncItem(
                    "${SyncItem.Kind.DOWNLOAD.name}|${file.absolutePath}|${file.length()}|${file.lastModified()}",
                    file.name, file.length(), mime, SyncItem.Kind.DOWNLOAD, file.lastModified(),
                )
                Source(item) { file.inputStream() }
            }
            .toList()
    }

    // ---------------------------------------------------------------- 평행 공간

    /** 평행 공간 쪽: 필요한 파일만 받아 MediaStore에 저장하고, 범위를 벗어난 복사본을 지운다. */
    fun receive(context: Context, input: DataInputStream, out: DataOutputStream): Boolean {
        if (!isSupported()) return false
        val count = input.readInt()
        require(count in 0..MAX_ITEMS) { "bad item count" }
        val items = List(count) {
            SyncItem(
                key = input.readUTF(),
                name = File(input.readUTF()).name, // 경로 구분자 제거
                size = input.readLong(),
                mime = input.readUTF(),
                kind = SyncItem.Kind.valueOf(input.readUTF()),
                modifiedMs = 0,
            )
        }
        val store = SyncStore(context)
        val diff = SyncPlanner.diff(store.keys(), items)
        out.writeInt(diff.wantedIndices.size)
        diff.wantedIndices.forEach(out::writeInt)
        out.flush()

        var saved = 0
        for (index in diff.wantedIndices) {
            if (!input.readBoolean()) continue
            val uri = save(context, items[index], input)
            if (uri != null) {
                store.put(items[index].key, uri)
                saved++
            }
        }

        var deleted = 0
        for (key in diff.expiredKeys) {
            store.get(key)?.let { uri ->
                runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
            }
            store.remove(key)
            deleted++
        }
        out.writeInt(saved)
        out.writeInt(deleted)
        out.flush()
        return true
    }

    /** 청크 스트림을 MediaStore에 쓴다. 실패해도 스트림은 끝까지 읽어 다음 파일과 어긋나지 않게 한다. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun save(context: Context, item: SyncItem, input: DataInputStream): String? {
        val resolver = context.contentResolver
        val (collection, dir) = when (item.kind) {
            SyncItem.Kind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to MEDIA_DIR
            SyncItem.Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to MEDIA_DIR
            SyncItem.Kind.DOWNLOAD -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to DOWNLOAD_DIR
        }
        val uri = try {
            resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })
        } catch (e: Exception) {
            Log.w(TAG, "insert failed: ${item.name}", e)
            null
        }
        val stream = uri?.let { runCatching { resolver.openOutputStream(it) }.getOrNull() }
        val buffer = ByteArray(CHUNK)
        var ok = stream != null
        try {
            while (true) {
                val length = input.readInt()
                if (length == 0) break
                require(length in 1..CHUNK) { "bad chunk" }
                input.readFully(buffer, 0, length)
                if (ok) {
                    try {
                        stream!!.write(buffer, 0, length)
                    } catch (e: IOException) {
                        Log.w(TAG, "write failed: ${item.name}", e)
                        ok = false
                    }
                }
            }
        } finally {
            runCatching { stream?.close() }
        }
        if (uri == null) return null
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri.toString()
    }
}

/** 평행 공간 쪽: 복사해 둔 항목의 key → MediaStore Uri */
private class SyncStore(context: Context) {
    private val prefs = context.getSharedPreferences("media_sync", Context.MODE_PRIVATE)
    fun keys(): Set<String> = prefs.all.keys.toSet()
    fun get(key: String): String? = prefs.getString(key, null)
    fun put(key: String, uri: String) = prefs.edit().putString(key, uri).commit()
    fun remove(key: String) = prefs.edit().remove(key).commit()
}
