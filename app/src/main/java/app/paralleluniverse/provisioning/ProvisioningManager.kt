package app.paralleluniverse.provisioning

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import app.paralleluniverse.admin.CloneAdminReceiver
import app.paralleluniverse.ui.MainActivity

/** 평행 공간 생성과 상태 조회를 담당한다. 개인 공간에서 사용한다. */
class ProvisioningManager(private val context: Context) {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val crossProfileApps = context.getSystemService(CrossProfileApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /**
     * 이 앱이 설치된 다른 프로필을 반환한다. 프로필 안의 이 앱은 Profile Owner이므로,
     * 결과가 있으면 그것이 우리가 만든 평행 공간이다.
     */
    fun ourProfile(): UserHandle? = crossProfileApps.targetUserProfiles.firstOrNull()

    fun status(): SpaceStatus = resolveSpaceStatus(
        isProfileOwner = dpm.isProfileOwnerApp(context.packageName),
        hasOurProfile = ourProfile() != null,
        provisioningAllowed = dpm.isProvisioningAllowed(
            DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
        ),
        profileCount = userManager.userProfiles.size,
    )

    fun isPaused(profile: UserHandle): Boolean = userManager.isQuietModeEnabled(profile)

    /**
     * 평행 공간 안의 이 앱(관리 화면)을 연다. cross-profile 필터 없이 동작하는 공식 API다.
     * @return 열었으면 true. 업무 앱이 일시중지된 경우 등에는 false
     */
    fun openSpace(profile: UserHandle): Boolean = try {
        crossProfileApps.startMainActivity(ComponentName(context, MainActivity::class.java), profile)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "openSpace failed", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "openSpace failed", e)
        false
    }

    private companion object {
        const val TAG = "ProvisioningManager"
    }

    /** 시스템 프로필 생성 마법사를 여는 인텐트. startActivityForResult로 실행한다. */
    fun createProfileIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                CloneAdminReceiver.component(context),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
            }
        }
}
