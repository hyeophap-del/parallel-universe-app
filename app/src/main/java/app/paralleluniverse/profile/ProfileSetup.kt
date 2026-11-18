package app.paralleluniverse.profile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import app.paralleluniverse.admin.CloneAdminReceiver

/**
 * 프로필 안에서 실행하는 설정. 프로필 생성 직후와 관리 화면을 열 때마다 실행된다.
 * 여러 번 호출해도 안전하다.
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

        // 파일·사진 공유 통로. 일부 기기(예: One UI 8.5)는 이 필터를 받아들이고도 적용하지 않으므로,
        // 핵심 기능(복제·실행·관리)은 이 필터에 의존하지 않는다.
        dpm.clearCrossProfileIntentFilters(admin)
        for ((filter, direction) in crossProfileFilters()) {
            dpm.addCrossProfileIntentFilter(admin, filter, direction)
        }

        // 복제한 앱이 프로필 안에서 업데이트를 받을 수 있도록 Play 스토어를 켠다(있을 때만).
        try {
            dpm.enableSystemApp(admin, PLAY_STORE)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Play Store not available as a system app", e)
        }

        dpm.setProfileEnabled(admin)

        val pick = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        Log.i("DEBUG-pu7", "profile GET_CONTENT image/* -> ${pick.map { it.activityInfo.name }}")
    }

    fun rename(context: Context, name: String) {
        SpaceName.set(context, name)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        dpm.setProfileName(CloneAdminReceiver.component(context), name)
    }

    private fun crossProfileFilters(): List<Pair<IntentFilter, Int>> {
        val toProfile = DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        val toPersonal = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        return listOf(
            // 개인 앱의 "공유하기"에서 평행 공간 앱(업무 탭)을 고를 수 있게 한다.
            typedFilter(Intent.ACTION_SEND) to toProfile,
            typedFilter(Intent.ACTION_SEND_MULTIPLE) to toProfile,
            // 평행 공간 앱의 파일·사진 선택 화면에서 개인 공간 파일에 접근할 수 있게 한다.
            typedFilter(Intent.ACTION_GET_CONTENT, Intent.CATEGORY_OPENABLE) to toPersonal,
            typedFilter(Intent.ACTION_OPEN_DOCUMENT, Intent.CATEGORY_OPENABLE) to toPersonal,
            typedFilter(ACTION_PICK_IMAGES) to toPersonal,
            IntentFilter(ACTION_PICK_IMAGES).apply { addCategory(Intent.CATEGORY_DEFAULT) } to toPersonal,
        )
    }

    private fun typedFilter(action: String, vararg categories: String) = IntentFilter(action).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        categories.forEach(::addCategory)
        addDataType("*/*")
    }
}
