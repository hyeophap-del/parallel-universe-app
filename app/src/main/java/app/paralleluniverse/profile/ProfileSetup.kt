package app.paralleluniverse.profile

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import app.paralleluniverse.admin.CloneAdminReceiver

/**
 * 프로필 안에서 실행하는 설정. 프로필을 만든 직후에 실행되고, 개인 공간과의 연결이 끊겼을 때
 * 복구용으로 다시 실행된다. 여러 번 호출해도 안전하다.
 */
object ProfileSetup {
    private const val TAG = "ProfileSetup"
    private const val PLAY_STORE = "com.android.vending"
    private const val ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES"

    fun complete(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isProfileOwnerApp(context.packageName)) return
        val admin = CloneAdminReceiver.component(context)

        dpm.setProfileName(admin, SpaceName.get(context))

        dpm.clearCrossProfileIntentFilters(admin)
        for ((filter, direction) in crossProfileFilters()) {
            try {
                dpm.addCrossProfileIntentFilter(admin, filter, direction)
                Log.i("DEBUG-pu7", "add ok dir=$direction actions=${filter.actionsIterator().asSequence().toList()}")
            } catch (e: Exception) {
                Log.e("DEBUG-pu7", "add FAILED dir=$direction", e)
            }
        }

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

        val self = context.packageManager.queryIntentActivities(
            Intent(ProfileBridge.ACTION_CLONE), PackageManager.MATCH_DEFAULT_ONLY,
        )
        Log.i("DEBUG-pu7", "profile-side CLONE resolves to: ${self.map { it.activityInfo.name }}")
        Log.i("DEBUG-pu7", "sdk=${android.os.Build.VERSION.SDK_INT} isPO=${dpm.isProfileOwnerApp(context.packageName)}")
    }

    fun rename(context: Context, name: String) {
        SpaceName.set(context, name)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        dpm.setProfileName(CloneAdminReceiver.component(context), name)
    }

    private fun crossProfileFilters(): List<Pair<IntentFilter, Int>> {
        val toProfile = DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        val toPersonal = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        return buildList {
            // 개인 공간 앱 → 평행 공간 앱 요청 통로
            add(ProfileBridge.intentFilter() to toProfile)

            // 개인 앱의 "공유하기"에서 평행 공간 앱(업무 탭)을 고를 수 있게 한다.
            add(typedFilter(Intent.ACTION_SEND) to toProfile)
            add(typedFilter(Intent.ACTION_SEND_MULTIPLE) to toProfile)

            // 평행 공간 앱의 파일·사진 선택 화면에서 개인 공간 파일에 접근할 수 있게 한다.
            add(typedFilter(Intent.ACTION_GET_CONTENT, Intent.CATEGORY_OPENABLE) to toPersonal)
            add(typedFilter(Intent.ACTION_OPEN_DOCUMENT, Intent.CATEGORY_OPENABLE) to toPersonal)
            add(typedFilter(ACTION_PICK_IMAGES) to toPersonal)
            add(IntentFilter(ACTION_PICK_IMAGES).apply { addCategory(Intent.CATEGORY_DEFAULT) } to toPersonal)
        }
    }

    private fun typedFilter(action: String, vararg categories: String) = IntentFilter(action).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        categories.forEach(::addCategory)
        addDataType("*/*")
    }
}
