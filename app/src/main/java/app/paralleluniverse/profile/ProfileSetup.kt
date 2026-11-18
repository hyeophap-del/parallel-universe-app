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

        // [DEBUG-pu7] probe (a): custom managed->parent filter
        try {
            dpm.addCrossProfileIntentFilter(
                admin,
                IntentFilter("app.paralleluniverse.action.PICK_APP").apply { addCategory(Intent.CATEGORY_DEFAULT) },
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT,
            )
        } catch (e: Exception) {
            Log.e("DEBUG-pu7", "probe a add failed", e)
        }
        val probeA = context.packageManager.queryIntentActivities(
            Intent("app.paralleluniverse.action.PICK_APP"), PackageManager.MATCH_DEFAULT_ONLY,
        )
        Log.i("DEBUG-pu7", "probe a PICK_APP -> ${probeA.map { it.activityInfo.name }}")

        // [DEBUG-pu7] probe (d): connect to the personal-space localhost server
        Thread {
            Thread.sleep(1500)
            try {
                java.net.Socket("127.0.0.1", 47621).use { s ->
                    val line = s.getInputStream().bufferedReader().readLine()
                    Log.i("DEBUG-pu7", "probe d client read: $line")
                }
            } catch (e: Exception) {
                Log.e("DEBUG-pu7", "probe d client failed: $e")
            }
        }.start()

        // [DEBUG-pu7] probe (c): LauncherApps from inside the profile
        try {
            val la = context.getSystemService(android.content.pm.LauncherApps::class.java)
            val cpa = context.getSystemService(android.content.pm.CrossProfileApps::class.java)
            val profiles = la.profiles
            Log.i("DEBUG-pu7", "probe c la.profiles=$profiles cpa.targets=${cpa.targetUserProfiles}")
            for (user in profiles + cpa.targetUserProfiles) {
                val list = try {
                    la.getActivityList(null, user).map { it.applicationInfo.packageName }
                } catch (e: Exception) {
                    listOf("ERR ${e.javaClass.simpleName}: ${e.message}")
                }
                Log.i("DEBUG-pu7", "probe c user=$user count=${list.size} discord=${"com.discord" in list} sample=${list.take(5)}")
            }
        } catch (e: Exception) {
            Log.e("DEBUG-pu7", "probe c failed", e)
        }

        // [DEBUG-pu7] probe (b): connected apps (INTERACT_ACROSS_PROFILES)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                dpm.setCrossProfilePackages(admin, setOf(context.packageName))
                val cpa = context.getSystemService(android.content.pm.CrossProfileApps::class.java)
                Log.i(
                    "DEBUG-pu7",
                    "probe b canRequest=${cpa.canRequestInteractAcrossProfiles()} canInteract=${cpa.canInteractAcrossProfiles()}",
                )
            } catch (e: Exception) {
                Log.e("DEBUG-pu7", "probe b failed", e)
            }
        }
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
