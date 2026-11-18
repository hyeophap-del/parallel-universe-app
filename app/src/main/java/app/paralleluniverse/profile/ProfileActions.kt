package app.paralleluniverse.profile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.paralleluniverse.admin.CloneAdminReceiver

/** 프로필 안(Profile Owner 권한)에서 실행하는 작업들. 앱 복제는 [ApkTransfer] 참고. */
object ProfileActions {
    const val SAMSUNG_MY_FILES = "com.sec.android.app.myfiles"
    private const val TAG = "ProfileActions"

    /** 시스템 삭제 확인 화면을 띄우는 인텐트. 프로필 안의 복제본과 그 데이터만 삭제된다. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 삼성 기기에서 앱이 파일을 고를 때(GET_CONTENT) 기본 파일 선택기가 삼성 '내 파일' 선택기로 넘기는데,
     * 그 선택기에는 '개인' 탭이 없다. 평행 공간 안의 '내 파일'만 숨기면 기본 선택기가 그대로 열려
     * 개인/업무 탭으로 개인 공간 파일을 고를 수 있다. 개인 공간의 '내 파일'에는 영향이 없다.
     * @return 적용했으면 true. '내 파일'이 없는 기기는 할 일이 없으므로 true
     */
    fun setPersonalFilesInPicker(context: Context, enabled: Boolean): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = CloneAdminReceiver.component(context)
        return try {
            dpm.setApplicationHidden(admin, SAMSUNG_MY_FILES, enabled)
            dpm.isApplicationHidden(admin, SAMSUNG_MY_FILES) == enabled
        } catch (e: Exception) {
            Log.w(TAG, "cannot toggle My Files", e)
            false
        }
    }

    /** 평행 공간 전체(프로필과 그 안의 모든 데이터)를 삭제한다. */
    fun destroy(context: Context) {
        context.getSystemService(DevicePolicyManager::class.java).wipeData(0)
    }
}
