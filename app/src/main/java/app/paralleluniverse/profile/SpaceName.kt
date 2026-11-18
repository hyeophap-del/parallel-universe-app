package app.paralleluniverse.profile

import android.content.Context
import app.paralleluniverse.R

/**
 * 사용자가 정한 평행 공간 이름. 사용자 공간마다 저장소가 따로 있으므로
 * 개인 공간(화면 표시용)과 프로필(setProfileName용)에 각각 저장된다.
 */
object SpaceName {
    private const val PREFS = "space"
    private const val KEY_NAME = "name"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)
            ?: context.getString(R.string.profile_name)

    fun set(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, name).apply()
    }
}
