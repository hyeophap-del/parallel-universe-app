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

    /** 삼성 '내 파일'이 있는 기기에서만 '개인 파일 첨부' 설정이 의미가 있다. */
    fun hasSamsungMyFiles(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(ProfileActions.SAMSUNG_MY_FILES, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
