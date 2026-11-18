package app.paralleluniverse.shortcut

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import app.paralleluniverse.R
import app.paralleluniverse.provisioning.ProvisioningManager

/** 홈 화면 바로가기가 실행하는 투명 Activity. 평행 공간 안의 앱을 열고 바로 닫힌다. */
class ShortcutLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(CloneShortcuts.EXTRA_PACKAGE)
        val profile = ProvisioningManager(this).ourProfile()
        val launched = packageName != null && profile != null && launch(packageName, profile)
        if (!launched) {
            Toast.makeText(this, R.string.shortcut_launch_failed, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun launch(packageName: String, profile: UserHandle): Boolean {
        val launcherApps = getSystemService(LauncherApps::class.java)
        return try {
            val activity = launcherApps.getActivityList(packageName, profile).firstOrNull()
                ?: return false
            launcherApps.startMainActivity(activity.componentName, profile, null, null)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "launch failed: $packageName", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "launch failed: $packageName", e)
            false
        }
    }

    private companion object {
        const val TAG = "ShortcutLaunch"
    }
}
