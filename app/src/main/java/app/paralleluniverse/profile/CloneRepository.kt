package app.paralleluniverse.profile

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

data class AppsSnapshot(
    /** 기기에 설치되어 있지만 평행 공간에는 없는 앱(복제 후보). */
    val candidates: List<AppEntry>,
    /** 평행 공간에 설치된 앱. */
    val cloned: List<AppEntry>,
)

/**
 * 평행 공간(프로필) 안에서 실행된다. 개인 공간과 통신하지 않고 모든 정보를 직접 얻는다.
 *
 * MATCH_UNINSTALLED_PACKAGES를 쓰면 이 사용자 공간에는 없고 다른 공간(개인 공간)에만 설치된
 * 앱도 조회된다. 이 앱들은 installExistingPackage로 바로 복제할 수 있다.
 */
class CloneRepository(private val context: Context) {

    private val pm = context.packageManager

    fun load(): AppsSnapshot {
        val apps = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter { it.packageName != context.packageName && !it.isSystem() }

        val (installed, notInstalled) = apps.partition { it.isInstalledHere() }
        return AppsSnapshot(
            candidates = notInstalled.map { entry(it, badged = false) }
                .sortedWith(compareBy({ it.packageName != DISCORD }, { it.label.lowercase() })),
            cloned = installed.map { entry(it, badged = true) }
                .sortedBy { it.label.lowercase() },
        )
    }

    fun launchIntent(packageName: String): Intent? =
        pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun entry(info: ApplicationInfo, badged: Boolean): AppEntry {
        val raw = pm.getApplicationIcon(info)
        val icon = if (badged) pm.getUserBadgedIcon(raw, Process.myUserHandle()) else raw
        return AppEntry(
            packageName = info.packageName,
            label = pm.getApplicationLabel(info).toString(),
            icon = icon.toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
        )
    }

    private fun ApplicationInfo.isSystem() = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
    private fun ApplicationInfo.isInstalledHere() = (flags and ApplicationInfo.FLAG_INSTALLED) != 0

    companion object {
        const val DISCORD = "com.discord"
        private const val ICON_PX = 144
    }
}
