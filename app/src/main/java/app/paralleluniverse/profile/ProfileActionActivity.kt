package app.paralleluniverse.profile

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.paralleluniverse.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 프로필 안에서 실행되는 투명 Activity. 개인 공간에서 ProfileBridge로 보낸 요청을 처리한다.
 */
class ProfileActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isProfileOwnerApp(packageName)) {
            finish()
            return
        }

        val target = intent.getStringExtra(ProfileBridge.EXTRA_PACKAGE)
        when (intent.action) {
            ProfileBridge.ACTION_CLONE -> if (target != null) clone(target) else finish()
            ProfileBridge.ACTION_UNINSTALL -> if (target != null) uninstall(target) else finish()
            ProfileBridge.ACTION_DESTROY -> confirmDestroy()
            else -> finish()
        }
    }

    private fun clone(target: String) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { ProfileActions.clone(applicationContext, target) }
            val label = appLabel(target)
            val message = if (ok) R.string.clone_success else R.string.clone_failed
            Toast.makeText(applicationContext, getString(message, label), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun uninstall(target: String) {
        startActivity(ProfileActions.uninstallIntent(target))
        finish()
    }

    private fun confirmDestroy() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(R.string.destroy_confirm_title)
            .setMessage(R.string.destroy_confirm_body)
            .setPositiveButton(R.string.delete) { _, _ -> ProfileActions.destroy(applicationContext) }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }

    private fun appLabel(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
