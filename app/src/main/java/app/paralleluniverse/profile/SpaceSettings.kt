package app.paralleluniverse.profile

import android.content.Context
import android.content.pm.PackageManager

/** 개인 공간 화면에 보여 줄 평행 공간 설정 상태. 실제 적용은 평행 공간 안에서 LocalBridge 명령으로 한다. */
object SpaceSettings {
    private const val PREFS = "space"
    private const val KEY_PERSONAL_FILES = "personal_files"

    fun personalFiles(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PERSONAL_FILES, false)

    fun setPersonalFiles(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PERSONAL_FILES, enabled).apply()
    }

    private const val KEY_MEDIA_SYNC = "media_sync"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val AUTO_SYNC_INTERVAL_MS = 2 * 60_000L

    /** "앱을 열 때 개인 사진·파일 복사" (기본 꺼짐) */
    fun mediaSync(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MEDIA_SYNC, false)

    fun setMediaSync(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MEDIA_SYNC, enabled).apply()
    }

    fun markSynced(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    /** 앱을 열 때마다 평행 공간을 깨우지 않도록, 최근 2분 안에 동기화했으면 건너뛴다. */
    fun shouldAutoSync(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0)
        return System.currentTimeMillis() - last > AUTO_SYNC_INTERVAL_MS
    }

    /** 삼성 '내 파일'이 있는 기기에서만 '개인 파일 첨부' 설정이 의미가 있다. */
    fun hasSamsungMyFiles(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(ProfileActions.SAMSUNG_MY_FILES, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
