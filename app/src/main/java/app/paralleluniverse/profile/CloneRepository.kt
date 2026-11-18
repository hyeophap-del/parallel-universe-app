package app.paralleluniverse.profile

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val component: ComponentName,
    val user: UserHandle,
)

data class AppsSnapshot(
    /** 개인 공간에 설치된 앱(복제 후보). */
    val personal: List<AppEntry>,
    /** 평행 공간 안의 앱. */
    val cloned: List<AppEntry>,
)

/**
 * 개인 공간에서 두 공간의 앱 목록을 조회하고 복제본을 실행한다.
 * LauncherApps는 같은 프로필 그룹의 앱을 조회·실행할 수 있어 별도 통신이 필요 없다.
 */
class CloneRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun load(profile: UserHandle?): AppsSnapshot {
        val personal = entries(Process.myUserHandle())
            .sortedWith(compareBy({ it.packageName != DISCORD }, { it.label.lowercase() }))
        val cloned = profile?.let { entries(it) }.orEmpty()
            .sortedBy { it.label.lowercase() }
        return AppsSnapshot(personal, cloned)
    }

    fun launch(entry: AppEntry): Boolean = try {
        launcherApps.startMainActivity(entry.component, entry.user, null, null)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "launch failed: ${entry.packageName}", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "launch failed: ${entry.packageName}", e)
        false
    }

    /**
     * 복제한 앱의 '앱 정보' 화면(평행 공간 쪽)을 연다. 배터리를 '제한 없음'으로 바꾸면 삼성 기기가
     * 오래 쓰지 않은 앱을 재우지 않아 알림이 끊기지 않는다. 다른 사용자 공간의 설정을 여는 공식 API다.
     */
    fun openAppDetails(entry: AppEntry): Boolean = try {
        launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "app details failed: ${entry.packageName}", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "app details failed: ${entry.packageName}", e)
        false
    }

    private fun entries(user: UserHandle): List<AppEntry> {
        val activities: List<LauncherActivityInfo> = try {
            launcherApps.getActivityList(null, user)
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot list apps for $user", e)
            emptyList()
        }
        return activities
            .filter { it.applicationInfo.packageName != context.packageName }
            .distinctBy { it.applicationInfo.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.applicationInfo.packageName,
                    label = info.label.toString(),
                    icon = info.getBadgedIcon(0).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                    component = info.componentName,
                    user = info.user,
                )
            }
    }

    companion object {
        const val DISCORD = "com.discord"
        private const val TAG = "CloneRepository"
        private const val ICON_PX = 144
    }
}
