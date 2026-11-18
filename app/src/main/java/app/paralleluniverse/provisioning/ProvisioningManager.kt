package app.paralleluniverse.provisioning

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import app.paralleluniverse.admin.CloneAdminReceiver

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
