package app.paralleluniverse.profile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.paralleluniverse.admin.CloneAdminReceiver

/** 프로필 안(Profile Owner 권한)에서만 실행할 수 있는 작업들. */
object ProfileActions {
    private const val TAG = "ProfileActions"

    /**
     * 개인 공간에 설치된 앱을 프로필에도 설치한다. 다운로드 없이 같은 APK를 공유하며,
     * 데이터는 프로필 사용자 쪽에 따로 생긴다.
     */
    fun clone(context: Context, packageName: String): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = CloneAdminReceiver.component(context)
        return try {
            val installed = dpm.installExistingPackage(admin, packageName)
            if (installed) {
                // 이전에 숨겨 둔 앱이면 다시 보이게 한다.
                dpm.setApplicationHidden(admin, packageName, false)
            }
            installed
        } catch (e: SecurityException) {
            Log.w(TAG, "clone failed: $packageName", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "clone failed: $packageName", e)
            false
        }
    }

    /** 시스템 삭제 확인 화면을 띄우는 인텐트. 프로필 안의 복제본과 그 데이터만 삭제된다. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 평행 공간 전체(프로필과 그 안의 모든 데이터)를 삭제한다. */
    fun destroy(context: Context) {
        context.getSystemService(DevicePolicyManager::class.java).wipeData(0)
    }
}
