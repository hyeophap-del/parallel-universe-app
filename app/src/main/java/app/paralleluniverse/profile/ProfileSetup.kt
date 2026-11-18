package app.paralleluniverse.profile

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.paralleluniverse.R
import app.paralleluniverse.admin.CloneAdminReceiver

/** 프로필 생성 직후 프로필 안에서 한 번 실행하는 초기 설정. 여러 번 호출해도 안전하다. */
object ProfileSetup {
    private const val TAG = "ProfileSetup"
    private const val PLAY_STORE = "com.android.vending"

    fun complete(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isProfileOwnerApp(context.packageName)) return
        val admin = CloneAdminReceiver.component(context)

        dpm.setProfileName(admin, context.getString(R.string.profile_name))

        // 개인 공간 → 프로필 방향으로 우리 요청 인텐트만 열어 둔다.
        dpm.clearCrossProfileIntentFilters(admin)
        dpm.addCrossProfileIntentFilter(
            admin,
            ProfileBridge.intentFilter(),
            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED,
        )

        // 요청을 처리하는 Activity는 프로필 쪽에서만 켠다.
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, ProfileActionActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        // 복제한 앱이 프로필 안에서 업데이트를 받을 수 있도록 Play 스토어를 켠다(있을 때만).
        try {
            dpm.enableSystemApp(admin, PLAY_STORE)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Play Store not available as a system app", e)
        }

        dpm.setProfileEnabled(admin)
    }
}
