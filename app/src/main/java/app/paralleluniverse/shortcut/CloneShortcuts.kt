package app.paralleluniverse.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import app.paralleluniverse.profile.AppEntry

/** 복제한 앱을 원하는 이름으로 홈 화면에 고정한다. */
object CloneShortcuts {
    const val ACTION_LAUNCH_CLONE = "app.paralleluniverse.action.LAUNCH_CLONE"
    const val EXTRA_PACKAGE = "app.paralleluniverse.extra.PACKAGE"
    private const val TAG = "CloneShortcuts"

    fun defaultLabel(appLabel: String, spaceName: String): String = "$appLabel ($spaceName)"

    /** @return 고정 요청을 보냈으면 true. 런처가 지원하지 않으면 false */
    fun pin(context: Context, entry: AppEntry, label: String): Boolean {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        if (!shortcutManager.isRequestPinShortcutSupported) return false

        val intent = Intent(context, ShortcutLaunchActivity::class.java)
            .setAction(ACTION_LAUNCH_CLONE)
            .putExtra(EXTRA_PACKAGE, entry.packageName)

        val info = ShortcutInfo.Builder(context, "clone:${entry.packageName}")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithBitmap(entry.icon.asAndroidBitmap()))
            .setIntent(intent)
            .build()

        return try {
            // 이미 고정된 바로가기가 있으면 이름과 아이콘을 갱신한다.
            shortcutManager.updateShortcuts(listOf(info))
            shortcutManager.requestPinShortcut(info, null)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "pin failed", e)
            false
        }
    }
}
